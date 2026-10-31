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
2. Settings → Secrets and variables → Actions → **Variables** → crie `API_URL` = URL do servidor (ex.: https://voltapracasa.shardweb.app).
3. Aba Actions → Build APK → Run workflow. O APK sai em Artifacts (`VoltaPraCasa-debug-apk`).

## 3) Painel de administração
Abra `admin-volta-pra-casa.html` no navegador (fica fora do servidor, só você usa) e entre com a `ADMIN_KEY`.
- **Fotos para aprovar:** anúncios de usuários nível 1 e 2 ficam em análise até você aprovar ou recusar.
- **ONGs:** aprovar/recusar cadastros. ONG aprovada vale como nível 3.
- **Denúncias:** 3 denúncias (ou 2 de venda/cobrança/golpe) ocultam o anúncio. Restaurar limpa as denúncias.
- **Conversas:** só aparecem as sinalizadas pelo sistema e as denunciadas. Dá para guardar como caso e suspender contas.

## 4) Níveis de confiança
Nível 1 = conta nova, nível 2 = 1 anúncio aprovado, nível 3 = 3 anúncios aprovados (publica direto). Anúncio ocultado por denúncia derruba a conta para o nível 1. Para mudar os números, adicione `LEVEL2_POSTS` e `LEVEL3_POSTS` no `config.json`.

## 5) Chat e privacidade
O app não usa mais WhatsApp: a conversa é dentro do app. O servidor apaga as mensagens **7 dias** depois de enviadas e **todas** as mensagens de um anúncio assim que ele é marcado como resolvido/adotado. Conversas denunciadas ou guardadas pelo admin viram "casos" e ficam por até 60 dias.

## 6) Recuperação de senha (e-mail)
Preencha no `config.json` (ou como variáveis de ambiente): `SMTP_HOST`, `SMTP_PORT` (587 ou 465), `SMTP_USER`, `SMTP_PASS` e `MAIL_FROM` (ex.: `Volta Pra Casa <seu@email.com>`). Sem isso o app avisa que a recuperação não está configurada. Se o e-mail não chegar, veja o log do servidor na Shard Cloud: erros de SMTP aparecem lá.

## Próximos passos sugeridos
Notificações push (Firebase), comparação de fotos por IA, assinatura/keystore para publicar na Play Store.
