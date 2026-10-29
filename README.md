# 🐾 Volta Pra Casa

Rede para encontrar pets perdidos: publicar pet perdido, fotografar pet avistado na rua (ping no mapa),
cruzamento automático por localização/espécie/cor/porte, área de adoção só para ONGs aprovadas e **venda de animais proibida**.

- `app/` – app Android (Kotlin + Jetpack Compose, mapa OpenStreetMap sem chave de API)
- `server/` – API Node.js para hospedar na **Shard Cloud**
- `.github/workflows/build.yml` – gera o APK no GitHub Actions

## 1) Servidor na Shard Cloud
1. Edite `server/config.json` e troque `ADMIN_KEY` por uma chave secreta sua.
2. Zip o **conteúdo** da pasta `server/` (index.js, package.json, .shardcloud, config.json na raiz do zip). **Sem** `node_modules` e `package-lock.json`.
3. No dashboard da Shard Cloud, envie o zip e faça o deploy. Anote a URL pública (https).
4. Se a plataforma informar outra porta, defina a variável `PORT`. Teste: abra a URL, deve responder `{"ok":true}`.
5. Os dados ficam em `data/` (db.json + fotos). Confirme na Shard Cloud se o disco persiste entre deploys e faça backup antes de reenviar o zip.

## 2) APK no GitHub
**Antes de tudo:** abra `gradle.properties` e troque `API_URL=` pela URL do servidor (com https://, sem barra no final). Sem isso o app não conecta.
1. Suba este projeto num repositório.
2. Settings → Secrets and variables → Actions → **Variables** → crie `API_URL` = URL do servidor (ex.: https://voltapracasa.shardcloud.app).
3. Aba Actions → Build APK → Run workflow. O APK sai em Artifacts (`VoltaPraCasa-debug-apk`).

## 3) Aprovar ONGs
```
curl -H "x-admin-key: SUA_CHAVE" https://SEU_SERVIDOR/api/admin/ongs
curl -X POST -H "x-admin-key: SUA_CHAVE" https://SEU_SERVIDOR/api/admin/ongs/ID/approve
```
Denúncias: 3 denúncias (ou 2 de "venda") ocultam o anúncio. Revisar: `GET /api/admin/reported`, restaurar: `POST /api/admin/posts/ID/restore`.

## Próximos passos sugeridos
Notificações push (Firebase), comparação de fotos por IA, assinatura/keystore para publicar na Play Store.
