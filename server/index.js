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
fs.mkdirSync(UPLOAD_DIR, { recursive: true });

// ---------- "banco" em arquivo JSON (sem dependências nativas) ----------
let db = { users: [], posts: [], secret: '' };
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

// Proibição de venda de animais (filtro de texto; denúncias da comunidade complementam)
const SALE = /(\bvend(o|e|em|a|er|ida|ido|emos)\b|\bvenda\b|\bpre[cç]o\b|r\$|\breais\b|\bpix\b|\bcompr(o|a|ar)\b|\bparcel|\bcobr(o|amos|ar)\b|\bfilhotes? a venda\b)/i;

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
    contactPhone: req.user ? p.contactPhone : ''
  };
  if (ref) o.distanceKm = +km(ref.lat, ref.lng, p.lat, p.lng).toFixed(1);
  return o;
}
const publicUser = u => ({ id: u.id, name: u.name, email: u.email, role: u.role, ongName: u.ongName || null, ongStatus: u.ongStatus || null });
const live = p => !p.hidden && p.status === 'ACTIVE' && (p.type === 'ADOPTION' || Date.now() - p.createdAt < 120 * 864e5);

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
  try { const p = jwt.verify(h.startsWith('Bearer ') ? h.slice(7) : '', JWT_SECRET); return db.users.find(x => x.id === p.sub) || null; } catch (e) { return null; }
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
  const p = db.posts.find(x => x.id === req.params.id && !x.hidden);
  if (!p) return res.status(404).json({ error: 'Anúncio não encontrado.' });
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
    if (db.posts.filter(p => p.userId === u.id && p.createdAt > Date.now() - 864e5).length >= 20)
      return fail('Limite diário de publicações atingido.', 429);
    const post = {
      id: newId(), userId: u.id, type, species: SPECIES.includes(b.species) ? b.species : 'OTHER',
      name: clean(b.name, 60), color: clean(b.color, 40), size: ['SMALL', 'MEDIUM', 'LARGE'].includes(b.size) ? b.size : '',
      sex: ['M', 'F'].includes(b.sex) ? b.sex : '', description: clean(b.description, 1000),
      contactPhone: clean(b.contactPhone, 20).replace(/[^\d+]/g, ''), address: clean(b.address, 160),
      lat, lng, photo: '/uploads/' + req.file.filename, status: 'ACTIVE', createdAt: Date.now(), reports: []
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
  res.json(pub(p, req));
});
app.delete('/api/posts/:id', auth, (req, res) => {
  const p = ownPost(req, res); if (!p) return;
  db.posts = db.posts.filter(x => x.id !== p.id);
  fs.unlink(path.join(DATA_DIR, p.photo.replace(/^\//, '')), () => {}); save();
  res.json({ ok: true });
});
app.post('/api/posts/:id/report', auth, (req, res) => {
  const p = db.posts.find(x => x.id === req.params.id);
  if (!p) return res.status(404).json({ error: 'Anúncio não encontrado.' });
  const reason = clean((req.body || {}).reason, 40) || 'OUTRO';
  p.reports = (p.reports || []).filter(r => r.by !== req.user.id);
  p.reports.push({ by: req.user.id, reason, at: Date.now() });
  const sale = p.reports.filter(r => /venda/i.test(r.reason)).length;
  if (p.reports.length >= 3 || sale >= 2) p.hidden = true; // oculta até revisão
  save(); res.json({ ok: true });
});
app.get('/api/posts/:id/matches', (req, res) => {
  const p = db.posts.find(x => x.id === req.params.id && !x.hidden);
  if (!p) return res.status(404).json({ error: 'Anúncio não encontrado.' });
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

// ---------- páginas públicas (compartilhamento + termos) ----------
app.get('/p/:id', (req, res) => {
  const p = db.posts.find(x => x.id === req.params.id && !x.hidden);
  if (!p) return res.status(404).send('Anúncio não encontrado');
  const base = `${req.protocol}://${req.get('host')}`;
  const label = { LOST: 'PERDIDO', FOUND: 'ENCONTRADO', SIGHTING: 'AVISTADO', ADOPTION: 'PARA ADOÇÃO' }[p.type];
  const title = `${label}: ${p.name || 'Pet'} ${p.address ? '- ' + p.address : ''}`;
  res.send(`<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>${esc(title)}</title><meta property="og:title" content="${esc(title)}"><meta property="og:image" content="${base}${p.photo}">
<meta property="og:description" content="${esc(p.description)}"></head>
<body style="font-family:sans-serif;max-width:520px;margin:auto;padding:16px"><h2>${esc(title)}</h2>
<img src="${base}${p.photo}" style="width:100%;border-radius:12px"><p>${esc(p.description)}</p>
<p><b>Baixe o app Volta Pra Casa</b> para ver o contato e o mapa.</p></body></html>`);
});
app.get('/termos', (req, res) => res.send(`<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<body style="font-family:sans-serif;max-width:600px;margin:auto;padding:16px"><h1>Termos - Volta Pra Casa</h1>
<p>O Volta Pra Casa é uma rede gratuita para reunir pets perdidos e suas famílias.</p>
<ul><li>É <b>proibida a venda</b> ou oferta paga de animais. Anúncios assim são removidos e a conta pode ser banida.</li>
<li>Adoção só pode ser divulgada por ONGs/protetores aprovados.</li>
<li>Publique apenas informações verdadeiras. Seu telefone fica visível só para usuários logados.</li></ul></body>`));

app.use((err, req, res, next) => res.status(500).json({ error: 'Erro interno do servidor.' }));
app.listen(PORT, '0.0.0.0', () => console.log('Volta Pra Casa API na porta ' + PORT));
