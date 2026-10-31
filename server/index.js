'use strict';
const express = require('express');
const cors = require('cors');
const multer = require('multer');
const jwt = require('jsonwebtoken');
const bcrypt = require('bcryptjs');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const PORT = Number(process.env.PORT) || 80;
const DATA_DIR = process.env.DATA_DIR || path.join(__dirname, 'data');
const UPLOAD_DIR = path.join(DATA_DIR, 'uploads');
const DB_FILE = path.join(DATA_DIR, 'db.json');
let fileCfg = {};
try { fileCfg = JSON.parse(fs.readFileSync(path.join(__dirname, 'config.json'), 'utf8')); } catch (e) {}
// Chave do admin: variável ADMIN_KEY ou config.json. Se ficar com o valor padrão, o painel admin fica desligado.
const ADMIN_KEY = [process.env.ADMIN_KEY, fileCfg.ADMIN_KEY].find(k => k && k !== 'TROQUE-ESTA-CHAVE') || '';
// E-mail (recuperação de senha). Preencha SMTP_* no config.json ou nas variáveis de ambiente.
const cfgv = k => process.env[k] || fileCfg[k] || '';
const SMTP = { host: cfgv('SMTP_HOST'), port: Number(cfgv('SMTP_PORT')) || 587, user: cfgv('SMTP_USER'), pass: cfgv('SMTP_PASS') };
SMTP.from = cfgv('MAIL_FROM') || ('Volta Pra Casa <' + SMTP.user + '>');
const mailReady = !!(SMTP.host && SMTP.user && SMTP.pass);
let transporter = null;
function sendMail(to, subject, text) {
  if (!transporter) transporter = require('nodemailer').createTransport({ host: SMTP.host, port: SMTP.port, secure: SMTP.port === 465, auth: { user: SMTP.user, pass: SMTP.pass } });
  return transporter.sendMail({ from: SMTP.from, to, subject, text });
}
// Níveis de confiança: anúncios aprovados necessários para o nível 2 e o nível 3 (a partir do 3 a publicação é automática)
const LEVEL2 = Number(fileCfg.LEVEL2_POSTS) || 1, LEVEL3 = Number(fileCfg.LEVEL3_POSTS) || 3;
fs.mkdirSync(UPLOAD_DIR, { recursive: true });

// ---------- "banco" em arquivo JSON (sem dependências nativas) ----------
let db = { users: [], posts: [], chats: [], cases: [], resets: [], secret: '' };
try { db = Object.assign(db, JSON.parse(fs.readFileSync(DB_FILE, 'utf8'))); } catch (e) {}
if (!db.secret) db.secret = crypto.randomBytes(32).toString('hex');
const JWT_SECRET = process.env.JWT_SECRET || db.secret;
let saveTimer = null;
function save() {
  if (saveTimer) return;
  saveTimer = setTimeout(() => {
    saveTimer = null;
    const tmp = DB_FILE + '.tmp';
    fs.writeFile(tmp, JSON.stringify(db), err => { if (!err) fs.rename(tmp, DB_FILE, () => {}); });
  }, 300);
}
save();

// ---------- utilidades ----------
const TYPES = ['LOST', 'FOUND', 'SIGHTING', 'ADOPTION'];
const SPECIES = ['DOG', 'CAT', 'BIRD', 'RABBIT', 'OTHER'];
const newId = () => crypto.randomBytes(8).toString('hex');
const clean = (s, max) => String(s == null ? '' : s).trim().slice(0, max);
const norm = s => String(s || '').toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').trim();
const toRad = d => d * Math.PI / 180;
function km(a, b, c, d) {
  const x = Math.sin(toRad(c - a) / 2) ** 2 + Math.cos(toRad(a)) * Math.cos(toRad(c)) * Math.sin(toRad(d - b) / 2) ** 2;
  return 2 * 6371 * Math.asin(Math.sqrt(x));
}
const esc = s => String(s || '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

function userById(id) { return db.users.find(x => x.id === id); }
const isApproved = p => !p.review || p.review === 'APPROVED'; // anúncios antigos (sem campo) contam como aprovados
const isAdminReq = req => !!ADMIN_KEY && req.headers['x-admin-key'] === ADMIN_KEY;
// Quem pode ver o anúncio: todos veem os aprovados; em análise/recusado só o dono; ocultos só o admin
function canSee(p, req) {
  if (!p) return false;
  if (isAdminReq(req)) return true;
  if ((userById(p.userId) || {}).banned) return false;
  if (p.hidden) return false;
  if (!isApproved(p)) return !!(req.user && req.user.id === p.userId);
  return true;
}
// Nível do usuário: 1 = novo, 2 = já teve anúncio aprovado, 3 = confiável (anúncios entram direto). ONG aprovada pelo admin = 3.
function userStats(u) {
  const mine = db.posts.filter(p => p.userId === u.id);
  const approved = mine.filter(p => isApproved(p) && !p.hidden).length;
  const restricted = mine.some(p => p.hidden); // anúncio ocultado por denúncias derruba para o nível 1
  let level = approved >= LEVEL3 ? 3 : approved >= LEVEL2 ? 2 : 1;
  if (restricted) level = 1;
  if (u.role === 'ONG' && u.ongStatus === 'APPROVED') level = 3;
  const next = level >= 3 ? 0 : Math.max(1, (level === 1 ? LEVEL2 : LEVEL3) - approved);
  return { level, approved, next, restricted };
}

// Proibição de venda de animais (filtro de texto; denúncias da comunidade complementam)
const SALE = /(\bvend(o|e|em|a|er|ida|ido|emos)\b|\bvenda\b|\bpre[cç]o\b|r\$|\breais\b|\bpix\b|\bcompr(o|a|ar)\b|\bparcel|\bcobr(o|amos|ar)\b|\bfilhotes? a venda\b)/i;
// pedidos de dinheiro (taxa, frete, pagamento...) em anúncios: golpe comum contra quem perdeu o pet
const SCAM = /(\bpagamentos?\b|\bpag(ar|ue)\b|\btaxas?\b|\bfrete\b|\btransfer[eê]ncias?\b|\bboleto\b)/i;

function isImage(file) {
  try {
    const fd = fs.openSync(file, 'r'); const b = Buffer.alloc(12); fs.readSync(fd, b, 0, 12, 0); fs.closeSync(fd);
    const jpg = b[0] === 0xFF && b[1] === 0xD8;
    const png = b[0] === 0x89 && b.toString('ascii', 1, 4) === 'PNG';
    const webp = b.toString('ascii', 0, 4) === 'RIFF' && b.toString('ascii', 8, 12) === 'WEBP';
    return jpg || png || webp;
  } catch (e) { return false; }
}

function pub(p, req, ref) {
  const u = db.users.find(x => x.id === p.userId) || {};
  const o = {
    id: p.id, userId: p.userId, type: p.type, species: p.species, name: p.name, color: p.color,
    size: p.size, sex: p.sex, description: p.description, address: p.address, lat: p.lat, lng: p.lng,
    photo: p.photo, status: p.status, resolvedNote: p.resolvedNote || '', createdAt: p.createdAt,
    ownerName: (u.role === 'ONG' ? (u.ongName || u.name) : u.name) || '', isOng: u.role === 'ONG',
    contactPhone: '', review: p.review || 'APPROVED', reviewNote: p.reviewNote || ''
  };
  if (ref) o.distanceKm = +km(ref.lat, ref.lng, p.lat, p.lng).toFixed(1);
  return o;
}
const publicUser = u => {
  const st = userStats(u);
  return { id: u.id, name: u.name, email: u.email, role: u.role, ongName: u.ongName || null, ongStatus: u.ongStatus || null,
    level: st.level, approvedPosts: st.approved, nextLevelIn: st.next, restricted: st.restricted };
};
const live = p => !p.hidden && isApproved(p) && !(userById(p.userId) || {}).banned && p.status === 'ACTIVE' && (p.type === 'ADOPTION' || Date.now() - p.createdAt < 120 * 864e5);

// ---------- cruzamento de localização ----------
const MAX_KM = 10;
function score(a, b) {
  if (a.species !== b.species) return null;
  const d = km(a.lat, a.lng, b.lat, b.lng);
  if (d > MAX_KM) return null;
  let s = 55 * (1 - d / MAX_KM);
  const ca = norm(a.color), cb = norm(b.color);
  if (ca && cb) { if (ca === cb) s += 20; else if (ca.includes(cb) || cb.includes(ca)) s += 10; }
  if (a.size && b.size && a.size === b.size) s += 10;
  const days = Math.abs(a.createdAt - b.createdAt) / 864e5;
  s += days <= 3 ? 15 : days <= 10 ? 8 : 0;
  if (s < 30) return null;
  return { score: Math.min(100, Math.round(s)), distanceKm: +d.toFixed(1) };
}
function matchesFor(p, req) {
  const opp = p.type === 'LOST' ? ['FOUND', 'SIGHTING'] : p.type === 'ADOPTION' ? [] : ['LOST'];
  return db.posts.filter(x => x.id !== p.id && live(x) && opp.includes(x.type))
    .map(x => { const s = score(p, x); return s && { post: pub(x, req), score: s.score, distanceKm: s.distanceKm }; })
    .filter(Boolean).sort((a, b) => b.score - a.score).slice(0, 10);
}

// ---------- app ----------
const app = express();
app.set('trust proxy', 1);
app.use(cors());
app.use(express.json({ limit: '100kb' }));
app.use('/uploads', express.static(UPLOAD_DIR, { maxAge: '30d' }));

function readToken(req) {
  const h = req.headers.authorization || '';
  try {
    const p = jwt.verify(h.startsWith('Bearer ') ? h.slice(7) : '', JWT_SECRET);
    const u = db.users.find(x => x.id === p.sub);
    if (!u || u.banned || (p.iat || 0) * 1000 < (u.tokensValidAfter || 0)) return null; // conta suspensa ou senha redefinida
    return u;
  } catch (e) { return null; }
}
app.use((req, res, next) => { req.user = readToken(req); next(); });
const auth = (req, res, next) => req.user ? next() : res.status(401).json({ error: 'Sessão inválida. Entre novamente.' });
const sign = u => jwt.sign({ sub: u.id }, JWT_SECRET, { expiresIn: '90d' });

const tries = new Map();
function limited(req) {
  const k = req.ip, now = Date.now(); const t = tries.get(k) || { n: 0, t: now };
  if (now - t.t > 15 * 60e3) { t.n = 0; t.t = now; }
  t.n++; tries.set(k, t); return t.n > 20;
}

app.get('/', (req, res) => res.json({ app: 'Volta Pra Casa API', ok: true }));

app.post('/api/auth/register', async (req, res) => {
  if (limited(req)) return res.status(429).json({ error: 'Muitas tentativas. Aguarde alguns minutos.' });
  const b = req.body || {};
  const name = clean(b.name, 80), email = clean(b.email, 120).toLowerCase(), password = String(b.password || '');
  if (name.length < 2) return res.status(400).json({ error: 'Informe seu nome.' });
  if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) return res.status(400).json({ error: 'E-mail inválido.' });
  if (password.length < 6) return res.status(400).json({ error: 'A senha precisa ter 6 caracteres ou mais.' });
  if (db.users.some(u => u.email === email)) return res.status(409).json({ error: 'Este e-mail já está cadastrado.' });
  const isOng = b.role === 'ONG';
  const u = { id: newId(), name, email, hash: await bcrypt.hash(password, 10), role: isOng ? 'ONG' : 'USER',
    ongName: isOng ? clean(b.ongName, 100) || name : null, ongStatus: isOng ? 'PENDING' : null, createdAt: Date.now() };
  db.users.push(u); save();
  res.json({ token: sign(u), user: publicUser(u) });
});

app.post('/api/auth/login', async (req, res) => {
  if (limited(req)) return res.status(429).json({ error: 'Muitas tentativas. Aguarde alguns minutos.' });
  const b = req.body || {};
  const u = db.users.find(x => x.email === clean(b.email, 120).toLowerCase());
  if (!u || !(await bcrypt.compare(String(b.password || ''), u.hash))) return res.status(401).json({ error: 'E-mail ou senha incorretos.' });
  if (u.banned) return res.status(403).json({ error: 'Conta suspensa por violar as regras. Fale com o suporte.' });
  res.json({ token: sign(u), user: publicUser(u) });
});

app.get('/api/me', auth, (req, res) => res.json(publicUser(req.user)));

app.get('/api/posts', (req, res) => {
  const q = req.query;
  const lat = parseFloat(q.lat), lng = parseFloat(q.lng), r = parseFloat(q.radiusKm);
  const ref = isFinite(lat) && isFinite(lng) ? { lat, lng } : null;
  let list = db.posts.filter(live);
  if (TYPES.includes(q.type)) list = list.filter(p => p.type === q.type);
  if (SPECIES.includes(q.species)) list = list.filter(p => p.species === q.species);
  if (q.q) { const s = norm(q.q); list = list.filter(p => norm([p.name, p.color, p.description, p.address].join(' ')).includes(s)); }
  let out = list.map(p => pub(p, req, ref));
  if (ref && r > 0) out = out.filter(p => p.distanceKm <= r);
  out.sort(ref ? (a, b) => a.distanceKm - b.distanceKm : (a, b) => b.createdAt - a.createdAt);
  res.json(out.slice(0, 300));
});

app.get('/api/posts/:id', (req, res) => {
  const p = db.posts.find(x => x.id === req.params.id);
  if (!canSee(p, req)) return res.status(404).json({ error: 'Anúncio não encontrado.' });
  res.json(pub(p, req));
});

const upload = multer({
  storage: multer.diskStorage({
    destination: UPLOAD_DIR,
    filename: (req, file, cb) => cb(null, newId() + Date.now().toString(36) + ({ 'image/png': '.png', 'image/webp': '.webp' }[file.mimetype] || '.jpg'))
  }),
  limits: { fileSize: 8 * 1024 * 1024, files: 1 },
  fileFilter: (req, file, cb) => cb(null, ['image/jpeg', 'image/png', 'image/webp'].includes(file.mimetype))
});

app.post('/api/posts', auth, (req, res, next) =>
  upload.single('photo')(req, res, err => err ? res.status(400).json({ error: 'Foto inválida (JPG, PNG ou WEBP de até 8 MB).' }) : next()),
  (req, res) => {
    const b = req.body || {}, u = req.user;
    const fail = (m, c = 400) => { if (req.file) fs.unlink(req.file.path, () => {}); return res.status(c).json({ error: m }); };
    if (!req.file || !isImage(req.file.path)) return fail('Envie uma foto do animal.');
    const type = String(b.type);
    if (!TYPES.includes(type)) return fail('Tipo de anúncio inválido.');
    if (type === 'ADOPTION' && !(u.role === 'ONG' && u.ongStatus === 'APPROVED'))
      return fail('Apenas ONGs aprovadas podem publicar animais para adoção.', 403);
    const lat = parseFloat(b.lat), lng = parseFloat(b.lng);
    if (!isFinite(lat) || !isFinite(lng) || Math.abs(lat) > 90 || Math.abs(lng) > 180) return fail('Localização inválida.');
    if (SALE.test([b.name, b.description, b.color, b.address, b.contactPhone].join(' ')))
      return fail('Venda de animais é proibida no Volta Pra Casa. Remova valores ou ofertas de venda do texto.');
    if (SCAM.test([b.name, b.description, b.color, b.address, b.contactPhone].join(' ')))
      return fail('Anúncios não podem pedir dinheiro, taxa, frete ou pagamento. Nunca se cobra por informação sobre um pet perdido.');
    if (db.posts.filter(x => x.userId === u.id && x.hidden).length >= 3)
      return fail('Sua conta está em análise por denúncias de outros usuários. Fale com o suporte.', 403);
    if (db.posts.filter(p => p.userId === u.id && p.createdAt > Date.now() - 864e5).length >= 20)
      return fail('Limite diário de publicações atingido.', 429);
    // Verificação de foto: nível 3 publica direto; níveis 1 e 2 entram numa fila para a equipe aprovar
    const review = userStats(u).level >= 3 ? 'APPROVED' : 'PENDING';
    if (review === 'PENDING' && db.posts.filter(x => x.userId === u.id && x.review === 'PENDING').length >= 5)
      return fail('Você já tem 5 anúncios aguardando verificação. Aguarde a análise antes de publicar mais.', 429);
    let photoHash = '';
    try { photoHash = crypto.createHash('sha256').update(fs.readFileSync(req.file.path)).digest('hex'); } catch (e) {}
    const post = {
      id: newId(), userId: u.id, type, species: SPECIES.includes(b.species) ? b.species : 'OTHER',
      name: clean(b.name, 60), color: clean(b.color, 40), size: ['SMALL', 'MEDIUM', 'LARGE'].includes(b.size) ? b.size : '',
      sex: ['M', 'F'].includes(b.sex) ? b.sex : '', description: clean(b.description, 1000),
      contactPhone: '', address: clean(b.address, 160),
      lat, lng, photo: '/uploads/' + req.file.filename, status: 'ACTIVE', createdAt: Date.now(), reports: [], review, photoHash
    };
    db.posts.push(post); save();
    res.json(pub(post, req));
  });

function ownPost(req, res) {
  const p = db.posts.find(x => x.id === req.params.id);
  if (!p) { res.status(404).json({ error: 'Anúncio não encontrado.' }); return null; }
  if (p.userId !== req.user.id && !(ADMIN_KEY && req.headers['x-admin-key'] === ADMIN_KEY)) { res.status(403).json({ error: 'Sem permissão.' }); return null; }
  return p;
}
app.post('/api/posts/:id/resolve', auth, (req, res) => {
  const p = ownPost(req, res); if (!p) return;
  p.status = 'RESOLVED'; p.resolvedNote = clean((req.body || {}).note, 120); save();
  purgeChats(); // entrega concluída: apaga as mensagens deste anúncio
  res.json(pub(p, req));
});
app.delete('/api/posts/:id', auth, (req, res) => {
  const p = ownPost(req, res); if (!p) return;
  db.posts = db.posts.filter(x => x.id !== p.id);
  fs.unlink(path.join(DATA_DIR, p.photo.replace(/^\//, '')), () => {}); save();
  purgeChats();
  res.json({ ok: true });
});
app.post('/api/posts/:id/report', auth, (req, res) => {
  const p = db.posts.find(x => x.id === req.params.id);
  if (!p) return res.status(404).json({ error: 'Anúncio não encontrado.' });
  const reason = clean((req.body || {}).reason, 40) || 'OUTRO';
  p.reports = (p.reports || []).filter(r => r.by !== req.user.id);
  p.reports.push({ by: req.user.id, reason, at: Date.now() });
  const sale = p.reports.filter(r => /venda|cobran|golpe|dinheiro/i.test(r.reason)).length;
  if (p.reports.length >= 3 || sale >= 2) p.hidden = true; // oculta até revisão
  save(); res.json({ ok: true });
});
app.get('/api/posts/:id/matches', (req, res) => {
  const p = db.posts.find(x => x.id === req.params.id);
  if (!canSee(p, req)) return res.status(404).json({ error: 'Anúncio não encontrado.' });
  res.json(matchesFor(p, req));
});

app.get('/api/my/posts', auth, (req, res) =>
  res.json(db.posts.filter(p => p.userId === req.user.id).sort((a, b) => b.createdAt - a.createdAt).map(p => pub(p, req))));
app.get('/api/my/matches', auth, (req, res) => {
  const out = [];
  db.posts.filter(p => p.userId === req.user.id && live(p) && p.type !== 'ADOPTION').forEach(p =>
    matchesFor(p, req).filter(m => m.score >= 45).slice(0, 3).forEach(m => out.push({ mine: pub(p, req), match: m })));
  res.json(out.sort((a, b) => b.match.score - a.match.score).slice(0, 20));
});

// ---------- admin: aprovar ONGs (header x-admin-key = variável ADMIN_KEY) ----------
const admin = (req, res, next) => ADMIN_KEY && req.headers['x-admin-key'] === ADMIN_KEY ? next() : res.status(403).json({ error: 'Acesso negado.' });
app.get('/api/admin/ongs', admin, (req, res) =>
  res.json(db.users.filter(u => u.role === 'ONG').map(u => ({ id: u.id, name: u.name, ongName: u.ongName, email: u.email, status: u.ongStatus }))));
app.post('/api/admin/ongs/:id/:action', admin, (req, res) => {
  const u = db.users.find(x => x.id === req.params.id && x.role === 'ONG');
  if (!u) return res.status(404).json({ error: 'ONG não encontrada.' });
  u.ongStatus = req.params.action === 'approve' ? 'APPROVED' : 'REJECTED'; save();
  res.json({ ok: true, status: u.ongStatus });
});
app.get('/api/admin/reported', admin, (req, res) =>
  res.json(db.posts.filter(p => (p.reports || []).length).map(p => ({ id: p.id, type: p.type, hidden: !!p.hidden, reports: p.reports }))));
app.post('/api/admin/posts/:id/:action', admin, (req, res) => {
  const p = db.posts.find(x => x.id === req.params.id);
  if (!p) return res.status(404).json({ error: 'Anúncio não encontrado.' });
  if (req.params.action === 'restore') { p.hidden = false; p.reports = []; } else p.hidden = true;
  save(); res.json({ ok: true });
});

// ---------- recuperação de senha (código de 6 dígitos por e-mail, vale 15 min) ----------
const sha = v => crypto.createHash('sha256').update(String(v) + db.secret).digest('hex');
app.post('/api/auth/forgot', async (req, res) => {
  if (limited(req)) return res.status(429).json({ error: 'Muitas tentativas. Aguarde alguns minutos.' });
  if (!mailReady) return res.status(503).json({ error: 'A recuperação de senha por e-mail ainda não foi configurada neste servidor.' });
  const email = clean((req.body || {}).email, 120).toLowerCase();
  const u = db.users.find(x => x.email === email);
  if (u && !u.banned) {
    const old = db.resets.find(r => r.userId === u.id);
    if (!old || Date.now() - old.at > 60e3) { // no máximo 1 código por minuto
      const code = String(crypto.randomInt(0, 1000000)).padStart(6, '0');
      db.resets = db.resets.filter(r => r.userId !== u.id);
      db.resets.push({ userId: u.id, hash: sha(code), at: Date.now(), expires: Date.now() + 15 * 60e3, tries: 0 });
      save();
      try {
        await sendMail(u.email, 'Seu código do Volta Pra Casa',
          `Seu código para redefinir a senha é ${code}.\nEle vale por 15 minutos.\n\nSe não foi você que pediu, ignore este e-mail.\nNunca compartilhe este código com ninguém, nem com quem disser ser do Volta Pra Casa.`);
      } catch (e) { console.error('Falha ao enviar e-mail de recuperação:', e.message); }
    }
  }
  res.json({ ok: true }); // mesma resposta exista ou não a conta (não revela quem está cadastrado)
});
app.post('/api/auth/reset', async (req, res) => {
  if (limited(req)) return res.status(429).json({ error: 'Muitas tentativas. Aguarde alguns minutos.' });
  const b = req.body || {};
  const email = clean(b.email, 120).toLowerCase(), code = String(b.code || '').replace(/\D/g, ''), password = String(b.password || '');
  if (password.length < 6) return res.status(400).json({ error: 'A senha precisa ter 6 caracteres ou mais.' });
  const bad = () => res.status(400).json({ error: 'Código inválido ou expirado. Peça um novo código.' });
  const u = db.users.find(x => x.email === email);
  const r = u && !u.banned && db.resets.find(x => x.userId === u.id);
  if (!r || r.expires < Date.now()) return bad();
  if (r.tries >= 5) { db.resets = db.resets.filter(x => x !== r); save(); return bad(); }
  r.tries++;
  if (!crypto.timingSafeEqual(Buffer.from(sha(code)), Buffer.from(r.hash))) { save(); return bad(); }
  u.hash = await bcrypt.hash(password, 10);
  u.tokensValidAfter = Math.floor(Date.now() / 1000) * 1000; // desconecta sessões antigas
  db.resets = db.resets.filter(x => x !== r); save();
  res.json({ token: sign(u), user: publicUser(u) });
});

// ---------- chat interno: mensagens apagadas em 7 dias ou quando a entrega é concluída ----------
const WEEK = 7 * 864e5;
const FLAGS = [
  ['money', /(\bpix\b|r\$|\breais\b|\bdinheiro\b|\bpagamentos?\b|\bpag(ar|ue)\b|\btaxas?\b|\bfrete\b|\bdep[oó]sito\b|\btransfer[eê]ncias?\b|\bboleto\b)/i],
  ['code', /(c[oó]digo.{0,40}(sms|whats|zap|chegou|recebeu|verifica|confirma)|(sms|whats|zap|chegou|recebeu).{0,40}c[oó]digo)/i],
  ['contact', /(https?:\/\/|www\.|(?<!\d)\(?\d{2}\)?\s?9?\d{4}[-\s]?\d{4}(?!\d))/i]
];
const chatFlag = t => { const f = FLAGS.find(x => x[1].test(t)); return f ? f[0] : ''; };
function chatSummary(c, me) {
  const p = db.posts.find(x => x.id === c.postId) || {};
  const other = userById(c.a === me.id ? c.b : c.a) || {};
  const last = c.msgs[c.msgs.length - 1];
  const readAt = (c.read || {})[me.id] || 0;
  return { id: c.id, postId: c.postId, postName: p.name || '', postSpecies: p.species || 'OTHER', postType: p.type || 'LOST', postPhoto: p.photo || '',
    isOwner: c.a === me.id, otherName: (other.role === 'ONG' ? (other.ongName || other.name) : other.name) || 'Usuário',
    lastText: last ? last.text.slice(0, 80) : '', lastAt: last ? last.at : c.createdAt,
    unread: c.msgs.filter(m => m.from !== me.id && m.at > readAt).length };
}
function chatFor(req, res) {
  const c = db.chats.find(x => x.id === req.params.id);
  if (!c || (c.a !== req.user.id && c.b !== req.user.id)) { res.status(404).json({ error: 'Conversa não encontrada ou já apagada.' }); return null; }
  c.read = c.read || {};
  return c;
}
app.post('/api/chats', auth, (req, res) => {
  const p = db.posts.find(x => x.id === clean((req.body || {}).postId, 40));
  if (!p || !canSee(p, req) || p.status !== 'ACTIVE') return res.status(404).json({ error: 'Este anúncio não está disponível para conversa.' });
  if (p.userId === req.user.id) return res.status(400).json({ error: 'Este anúncio é seu. As conversas aparecem na aba Conversas.' });
  let c = db.chats.find(x => x.postId === p.id && x.b === req.user.id);
  if (!c) {
    if (db.chats.filter(x => x.b === req.user.id && x.createdAt > Date.now() - 864e5).length >= 15)
      return res.status(429).json({ error: 'Limite diário de novas conversas atingido.' });
    c = { id: newId(), postId: p.id, a: p.userId, b: req.user.id, createdAt: Date.now(), msgs: [], read: {} };
    db.chats.push(c); save();
  }
  res.json(chatSummary(c, req.user));
});
app.get('/api/chats', auth, (req, res) =>
  res.json(db.chats.filter(c => c.a === req.user.id || c.b === req.user.id).map(c => chatSummary(c, req.user)).sort((x, y) => y.lastAt - x.lastAt)));
app.get('/api/chats/:id/messages', auth, (req, res) => {
  const c = chatFor(req, res); if (!c) return;
  const last = c.msgs.length ? c.msgs[c.msgs.length - 1].at : 0;
  if ((c.read[req.user.id] || 0) < last) { c.read[req.user.id] = Date.now(); save(); }
  res.json({ chat: chatSummary(c, req.user), messages: c.msgs.map(m => ({ id: m.id, mine: m.from === req.user.id, text: m.text, at: m.at, flag: m.from === req.user.id ? '' : (m.flag || '') })) });
});
app.post('/api/chats/:id/messages', auth, (req, res) => {
  const c = chatFor(req, res); if (!c) return;
  const p = db.posts.find(x => x.id === c.postId);
  if (!p || p.status !== 'ACTIVE' || p.hidden) return res.status(410).json({ error: 'Esta conversa foi encerrada.' });
  const text = clean((req.body || {}).text, 1000);
  if (!text) return res.status(400).json({ error: 'Escreva uma mensagem.' });
  const now = Date.now();
  const recent = db.chats.reduce((n, x) => n + x.msgs.filter(m => m.from === req.user.id && now - m.at < 60e3).length, 0);
  if (recent >= 20) return res.status(429).json({ error: 'Você está enviando mensagens rápido demais. Aguarde um instante.' });
  const m = { id: newId(), from: req.user.id, text, at: now, flag: chatFlag(text) };
  c.msgs.push(m); if (c.msgs.length > 500) c.msgs.shift();
  save();
  res.json({ id: m.id, mine: true, text: m.text, at: m.at, flag: '' });
});
// Caso de moderação: cópia da conversa guardada só para investigar denúncia (some em 60 dias ou quando o admin encerra)
function makeCase(c, reason, by) {
  const who = id => { const u = userById(id) || {}; return { id, name: u.name || '', email: u.email || '' }; };
  const p = db.posts.find(x => x.id === c.postId) || {};
  return { id: newId(), chatId: c.id, postId: c.postId, postType: p.type || '', reason, by, createdAt: Date.now(),
    people: [who(c.a), who(c.b)], msgs: c.msgs.map(m => ({ from: m.from, text: m.text, at: m.at, flag: m.flag || '' })) };
}
app.post('/api/chats/:id/report', auth, (req, res) => {
  const c = chatFor(req, res); if (!c) return;
  const reason = clean((req.body || {}).reason, 60) || 'OUTRO';
  if (!db.cases.some(k => k.chatId === c.id && k.by === req.user.id)) db.cases.push(makeCase(c, reason, req.user.id));
  save(); res.json({ ok: true });
});
function purgeChats() {
  const now = Date.now(); let changed = false;
  db.chats = db.chats.filter(c => {
    const p = db.posts.find(x => x.id === c.postId);
    if (!p || p.status !== 'ACTIVE') { changed = true; return false; } // entrega concluída (anúncio resolvido) ou anúncio apagado
    const n = c.msgs.length;
    c.msgs = c.msgs.filter(m => now - m.at < WEEK);
    if (c.msgs.length !== n) changed = true;
    if (!c.msgs.length && now - c.createdAt >= WEEK) { changed = true; return false; }
    return true;
  });
  const nc = db.cases.length; db.cases = db.cases.filter(k => now - k.createdAt < 60 * 864e5); if (db.cases.length !== nc) changed = true;
  const nr = db.resets.length; db.resets = db.resets.filter(r => r.expires > now); if (db.resets.length !== nr) changed = true;
  if (changed) save();
}

// ---------- admin: fila de verificação de fotos, conversas sinalizadas e suspensão ----------
app.get('/api/admin/pending', admin, (req, res) =>
  res.json(db.posts.filter(p => p.review === 'PENDING').sort((a, b) => a.createdAt - b.createdAt).map(p => {
    const u = userById(p.userId) || {}; const st = u.id ? userStats(u) : { level: 1, approved: 0 };
    return { id: p.id, type: p.type, species: p.species, name: p.name, color: p.color, description: p.description, address: p.address,
      photo: p.photo, createdAt: p.createdAt, lat: p.lat, lng: p.lng,
      owner: { id: u.id, name: u.name, email: u.email, role: u.role, level: st.level, approved: st.approved },
      duplicates: p.photoHash ? db.posts.filter(x => x.id !== p.id && x.photoHash === p.photoHash).length : 0 };
  })));
app.post('/api/admin/posts/:id/review/:decision', admin, (req, res) => {
  const p = db.posts.find(x => x.id === req.params.id);
  if (!p) return res.status(404).json({ error: 'Anúncio não encontrado.' });
  if (req.params.decision === 'approve') { p.review = 'APPROVED'; p.reviewNote = ''; }
  else if (req.params.decision === 'reject') { p.review = 'REJECTED'; p.reviewNote = clean((req.body || {}).note, 120); }
  else return res.status(400).json({ error: 'Decisão inválida.' });
  p.reviewedAt = Date.now(); save();
  res.json({ ok: true, review: p.review });
});
const withBan = k => Object.assign({}, k, { people: k.people.map(x => Object.assign({}, x, { banned: !!(userById(x.id) || {}).banned })) });
app.get('/api/admin/chats', admin, (req, res) =>
  res.json(db.chats.filter(c => c.msgs.some(m => m.flag)).map(c => withBan(makeCase(c, 'mensagem sinalizada', 'sistema')))));
app.post('/api/admin/chats/:id/keep', admin, (req, res) => {
  const c = db.chats.find(x => x.id === req.params.id);
  if (!c) return res.status(404).json({ error: 'Conversa não encontrada (pode já ter sido apagada).' });
  if (!db.cases.some(k => k.chatId === c.id)) db.cases.push(makeCase(c, 'guardada pela moderação', 'admin'));
  save(); res.json({ ok: true });
});
app.get('/api/admin/cases', admin, (req, res) => res.json(db.cases.slice().sort((a, b) => b.createdAt - a.createdAt).map(withBan)));
app.post('/api/admin/cases/:id/close', admin, (req, res) => {
  const n = db.cases.length; db.cases = db.cases.filter(k => k.id !== req.params.id);
  if (db.cases.length === n) return res.status(404).json({ error: 'Caso não encontrado.' });
  save(); res.json({ ok: true });
});
app.post('/api/admin/users/:id/:action', admin, (req, res) => {
  const u = userById(req.params.id);
  if (!u) return res.status(404).json({ error: 'Usuário não encontrado.' });
  u.banned = req.params.action === 'ban'; save();
  res.json({ ok: true, banned: u.banned });
});

// ---------- páginas públicas (compartilhamento + termos) ----------
app.get('/p/:id', (req, res) => {
  const p = db.posts.find(x => x.id === req.params.id && !x.hidden && isApproved(x));
  if (!p) return res.status(404).send('Anúncio não encontrado');
  const base = `${req.protocol}://${req.get('host')}`;
  const label = { LOST: 'PERDIDO', FOUND: 'ENCONTRADO', SIGHTING: 'AVISTADO', ADOPTION: 'PARA ADOÇÃO' }[p.type];
  const title = `${label}: ${p.name || 'Pet'} ${p.address ? '- ' + p.address : ''}`;
  res.send(`<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>${esc(title)}</title><meta property="og:title" content="${esc(title)}"><meta property="og:image" content="${base}${p.photo}">
<meta property="og:description" content="${esc(p.description)}"></head>
<body style="font-family:sans-serif;max-width:520px;margin:auto;padding:16px"><h2>${esc(title)}</h2>
<img src="${base}${p.photo}" style="width:100%;border-radius:12px"><p>${esc(p.description)}</p>
<p style="background:#FFEFC7;padding:12px;border-radius:12px"><b>Nunca pague por informações sobre um pet perdido.</b> Quem cobra é golpista.</p>
<p><b>Baixe o app Volta Pra Casa</b> para conversar com quem publicou e ver o mapa.</p></body></html>`);
});
app.get('/termos', (req, res) => res.send(`<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<body style="font-family:sans-serif;max-width:600px;margin:auto;padding:16px"><h1>Termos - Volta Pra Casa</h1>
<p>O Volta Pra Casa é uma rede gratuita para reunir pets perdidos e suas famílias.</p>
<ul><li>É <b>proibida a venda</b> ou oferta paga de animais. Anúncios assim são removidos e a conta pode ser banida.</li>
<li><b>Nunca pague</b> para receber informações sobre um pet perdido. Quem cobra é golpista: denuncie o anúncio.</li>
<li>Adoção só pode ser divulgada por ONGs/protetores aprovados.</li>
<li>Publique apenas informações verdadeiras. Fotos de anúncios novos são conferidas pela equipe antes de irem ao ar, para evitar fotos falsas.</li>
<li>As conversas acontecem dentro do app e <b>são apagadas após 7 dias</b> ou assim que a entrega é concluída. Seu telefone não aparece para ninguém.</li>
<li>Para combater golpes, mensagens suspeitas podem ser analisadas pela moderação, e conversas denunciadas ficam guardadas somente para essa investigação.</li></ul></body>`));

purgeChats(); setInterval(purgeChats, 30 * 60e3); // apaga mensagens vencidas, conversas de entregas concluídas e códigos expirados
app.use((err, req, res, next) => res.status(500).json({ error: 'Erro interno do servidor.' }));
app.listen(PORT, '0.0.0.0', () => console.log('Volta Pra Casa API na porta ' + PORT));
