# promo-server

Servidor que coleta as ofertas das lojas que você quer divulgar e as entrega ao app PromoApp.
Kotlin + Ktor + SQLite. Roda sozinho: de tempos em tempos busca as ofertas, descarta o que não é promoção de verdade, guarda o histórico de preço e expira o que acabou.

## O que tem aqui
- Coletores: feeds da Awin, Shopee, Amazon, Mercado Livre e leitura de páginas públicas (`sites.json`), mais modo demo.
- Normalização (só desconto real), histórico de preço, expiração e limpeza automática.
- API no contrato do app (feed, busca, detalhe, histórico, filtros, cupons, publicação).
- **Painel de administração** em `/admin`: status, coletar agora, pré-visualizar páginas e cadastrar cupons.
- **Redirecionamento com contagem de cliques** (`/go/{id}`).
- Histórico de cada coleta (quem funcionou, quantas ofertas, qual erro).
- Limite de requisições por IP, cabeçalhos de segurança no painel e publicação desativada por padrão.
- Docker, docker-compose com HTTPS automático (Caddy) e CI.

## Rodar agora (modo demo)
```bash
gradle run          # ou abra a pasta no IntelliJ e rode ApplicationKt
curl http://localhost:8080/deals
```
Sem nenhuma credencial, o servidor gera ofertas de exemplo para as 5 lojas (Mercado Livre, Amazon, Shopee, Natura e O Boticário). Serve para testar o app.

## Ligar o app Android
No `app/build.gradle.kts` do app: `USE_FAKE_API = "false"` e `API_BASE_URL = "http://10.0.2.2:8080/"` (10.0.2.2 é o computador visto pelo emulador). O app já tem um manifesto de debug que libera HTTP sem TLS. Em produção, use HTTPS.

## Como cada loja entra

| Loja | Como coleta | O que você precisa | Observações |
|---|---|---|---|
| Natura | Feed de produtos da Awin (CSV) | `AWIN_FEED_NATURA_URL` | Programa na Awin. Na checagem que fiz, a página do programa avisava que não aceita pessoa física: provavelmente precisa de CNPJ. Confirme. |
| O Boticário | Feed de produtos da Awin (CSV) | `AWIN_FEED_BOTICARIO_URL` | Mesmo caso da Natura. |
| Amazon | Creators API (OAuth 2.0), `searchItems` | `AMAZON_CREDENTIAL_ID`, `AMAZON_CREDENTIAL_SECRET`, `AMAZON_PARTNER_TAG`, `AMAZON_KEYWORDS` | A PA-API 5 foi substituída pela Creators API. Exige conta de Associados com vendas qualificadas. |
| Shopee | Open API de Afiliados (GraphQL), `productOfferV2` | `SHOPEE_APP_ID`, `SHOPEE_APP_SECRET`, `SHOPEE_KEYWORDS` | A assinatura da requisição e os campos seguem a documentação como eu a conheço. Confira na sua conta antes de produção. |
| Mercado Livre | API oficial com token de app: anúncios que você escolhe + busca por termos | `ML_CLIENT_ID`, `ML_CLIENT_SECRET`, `ML_ITEM_IDS`, `ML_SEARCH_TERMS`, `ML_AFFILIATE_URL_TEMPLATE` | Não existe API pública de "ofertas do dia" para afiliados (a de ofertas é para vendedores). O modelo do link de afiliado você copia do painel do Mercado Livre Afiliados. |

### Natura e O Boticário (Awin)
No painel da Awin, crie um feed (Create-a-Feed) em CSV com as colunas `aw_product_id`, `product_name`, `aw_deep_link`, `aw_image_url`, `search_price`, `product_price_old`, `category_name`, `in_stock` e cole a URL gerada na variável. O servidor lê CSV com vírgula ou ponto e vírgula, com ou sem gzip.
O que vira "oferta do dia": só entra produto com preço antigo maior que o atual e desconto de pelo menos `MIN_DISCOUNT_PERCENT` (padrão 10%). Produto sem preço antigo é ignorado.

## Ler as páginas públicas das lojas (sem afiliado)

Para começar sem conta de afiliado, o servidor também sabe ler as páginas públicas de ofertas. Funciona por um arquivo `sites.json` (copie de `sites.example.json`): você diz a loja e a URL da página de ofertas, e o servidor extrai os produtos.

**Como ele extrai (nesta ordem, com `"strategy": "auto"`)**
1. seus seletores CSS, se você configurou;
2. dados estruturados `schema.org` (JSON-LD), que muitas lojas publicam para o Google;
3. JSON embutido na página (por exemplo `__NEXT_DATA__`), procurando campos comuns como nome, preço e link.

**Regras de boa conduta (já embutidas)**
- Respeita o `robots.txt`. Se o site não permite a página, o robô não acessa. Se o `robots.txt` der erro de servidor, também não acessa.
- Identifica-se como `PromoAppBot/1.0` (você pode somar um contato em `SCRAPER_CONTACT`). Não finge ser navegador.
- Espera pelo menos 3 s entre acessos ao mesmo site e coleta só algumas vezes por dia.
- Se o site responder 403, 429 ou 503, para de acessá-lo por 6 horas. O robô não tenta contornar bloqueio, captcha ou login.
- Se qualquer página de uma loja falhar, a coleta daquela loja inteira falha e nada é expirado por engano.

**Teste antes de ligar: pré-visualização**
```bash
curl -X POST http://localhost:8080/admin/scrape-preview \
  -H "X-Admin-Token: SEU_TOKEN" -H "Content-Type: application/json" \
  -d '{"url":"https://www.mercadolivre.com.br/ofertas"}'
```
Mostra qual estratégia funcionou, quantos produtos achou, quantos têm desconto e uma amostra, sem salvar nada. Se vier `found: 0`, a página provavelmente carrega os produtos por JavaScript ou os seletores precisam de ajuste: troque a URL (uma página de categoria costuma funcionar melhor) ou configure o `css` e teste de novo.

**O que esperar de cada loja**

| Loja | Leitura de página | Observações |
|---|---|---|
| Mercado Livre | Boa chance. A página `mercadolivre.com.br/ofertas` é pública | Os seletores do exemplo (`.poly-card` etc.) são de memória e o site muda. Use a pré-visualização e ajuste. |
| Natura, O Boticário | Depende de como o site entrega os produtos | Cole no `sites.json` a URL da página de ofertas e teste. Eu não conferi a estrutura dessas páginas. |
| Amazon | Não recomendo | Os termos da Amazon proíbem coleta automatizada e o site bloqueia robôs. Use a Creators API quando puder. |
| Shopee | Não recomendo | A página depende de JavaScript e tem proteção contra robôs. Use a API de Afiliados. |

**Pontos de atenção**
- Ler página pública não é o mesmo que ter permissão: os termos de uso de várias lojas proíbem coleta automática. Para um app que você publica, o caminho seguro é migrar para API ou feed oficial assim que possível.
- O link sai sem comissão. Quando entrar no afiliado, troque `affiliateUrlTemplate` no `sites.json` (por exemplo `https://seu-link-de-afiliado?u={url}`), sem mexer em código.
- Nem toda oferta tem "preço de" na página. Sem preço antigo, o servidor ignora o produto, porque não dá para afirmar desconto.

## Painel de administração
Abra `http://localhost:8080/admin`, cole o `ADMIN_TOKEN` e use:
- **Status**: ofertas ativas por loja, cliques nas últimas 24 h e a última coleta de cada coletor (com o erro, se houver).
- **Coletar agora**: roda tudo ou uma loja só e mostra o relatório.
- **Pré-visualizar uma página**: o mesmo teste do `/admin/scrape-preview`, com formulário. É o jeito mais rápido de acertar o `sites.json`.
- **Novo cupom**.
A página é estática e só mostra dados depois de você informar o token. O token fica na sessão do navegador.

## Cliques e links de afiliado
Com `PUBLIC_BASE_URL` definido, o link que o app recebe é `https://seu-dominio/go/{id}`. O servidor registra o clique e redireciona para a loja. Vantagens: você sabe o que as pessoas clicam e, no futuro, pode trocar o destino (afiliado) sem publicar uma nova versão do app.

## Manutenção automática
- Depois de cada coleta, apaga ofertas expiradas há mais de `RETENTION_DAYS` (60) com o histórico de preço e os cliques delas, e coletas com mais de 30 dias.
- Ofertas publicadas por usuários nunca são apagadas por esta limpeza.
- Limite de `RATE_LIMIT_PER_MINUTE` (120) requisições por IP nas rotas públicas. `/admin` e `/health` não entram no limite. Atrás de proxy, use `TRUST_PROXY=true` para usar o IP real.

## Regras de qualidade
- Só vira oferta o que tem desconto real (preço original maior que o preço, acima do mínimo configurado).
- Ofertas que somem da loja são desativadas na coleta seguinte. Se um coletor volta vazio, nada é expirado (pode ser falha da API).
- Cada preço novo gera um ponto no histórico. É o que alimenta o gráfico do app.
- Um coletor com erro não derruba os outros. O erro aparece no log e na resposta de `/admin/collect`.

## Variáveis de ambiente
Veja `.env.example`. As principais:

| Variável | Padrão | Para quê |
|---|---|---|
| `PORT` | 8080 | Porta HTTP (na ShardCloud, 80) |
| `DB_PATH` | promo.db | Arquivo SQLite |
| `ADMIN_TOKEN` | vazio | Libera `/admin/*` (cabeçalho `X-Admin-Token`). Mínimo de 16 caracteres; sem ele (ou com um token curto), o admin fica desligado. |
| `ENV_FILE` | promo.env | Arquivo com as variáveis, para hospedagens sem painel de variáveis |
| `ALLOW_PUBLIC_POSTS` | false | Permite `POST /deals` (publicação pelo app). Deixe desligado até haver login, senão qualquer um publica. |
| `COLLECT_INTERVAL_HOURS` | 6 | De quanto em quanto tempo coleta |
| `COLLECT_ON_START` | true | Coleta ao iniciar |
| `MIN_DISCOUNT_PERCENT` | 10 | Desconto mínimo para ser oferta |
| `DEMO_MODE` | liga sozinho sem credenciais | Ofertas de exemplo para lojas sem credencial |
| `SCRAPE_SITES_FILE` | sites.json | Arquivo com as páginas públicas a ler |
| `SCRAPER_CONTACT` | vazio | Contato que vai no User-Agent do robô |
| `SCRAPER_MIN_DELAY_MS` | 3000 | Intervalo mínimo entre acessos ao mesmo site |
| `PUBLIC_BASE_URL` | vazio | Faz o app abrir os links por `/go/{id}` (cliques) |
| `RETENTION_DAYS` | 60 | Dias até apagar ofertas expiradas |
| `RATE_LIMIT_PER_MINUTE` | 120 | Requisições por minuto por IP (0 desliga) |
| `TRUST_PROXY` | false | Usar X-Forwarded-For como IP do cliente |

## API (o contrato que o app já usa)

| Método | Rota | Descrição |
|---|---|---|
| GET | `/deals?tab=&page=&page_size=` | Feed. `tab`: for_you, trending, recent, lowest_price |
| GET | `/deals/search?q=&stores=&category=&min_price=&max_price=&sort=&page=&page_size=` | Busca. `sort`: relevance, lowest_price, biggest_discount, newest |
| GET | `/deals/{id}` | Detalhe |
| GET | `/deals/{id}/price-history` | Histórico de preço |
| GET | `/deals/mine` | Ofertas publicadas pelos usuários |
| GET | `/filters` | Lojas e categorias com ofertas ativas |
| GET | `/coupons` | Cupons |
| POST | `/deals` | Publicar oferta (só com `ALLOW_PUBLIC_POSTS=true`) |
| POST | `/admin/collect?store=` | Roda a coleta agora e devolve o relatório |
| POST | `/admin/coupons` | Cadastra cupom |
| POST | `/admin/scrape-preview` | Mostra o que o robô acharia em uma página, sem salvar |
| GET | `/go/{id}` | Conta o clique e redireciona para a loja |
| GET | `/admin` | Painel de administração (página) |
| GET | `/admin/backup` | Baixa uma cópia consistente do banco SQLite |
| GET | `/admin/status` | Totais, lojas e última coleta de cada coletor |
| GET | `/health` | Verificação de saúde |

Forçar uma coleta:
```bash
curl -X POST -H "X-Admin-Token: SEU_TOKEN" "http://localhost:8080/admin/collect?store=Natura"
```

## Publicar no ar
Com HTTPS automático (recomendado). Aponte o DNS do seu domínio para a VPS, copie `.env.example` para `.env`, preencha `DOMAIN`, `ADMIN_TOKEN` e as credenciais, copie `sites.example.json` para `sites.json` e rode:
```bash
docker compose up -d --build
```
Sem domínio, só o servidor: `docker build -t promo-server . && docker run -d -p 8080:8080 -v promo-data:/data -e ADMIN_TOKEN=... promo-server`.
Qualquer VPS pequena serve. Faça backup do volume `promo-data` (arquivo `promo.db`).

## Hospedar na ShardCloud
O que a documentação da ShardCloud diz para Java: roda um **JAR executável** (com `Main-Class`), o arquivo **`.shardcloud`** fica na raiz, o envio é por **ZIP/RAR (até 100 MB)** ou GitHub, o servidor web precisa **escutar na porta 80**, o mínimo é 256 MB (512 MB ou mais para API pública), cada aplicação tem **10 GB de disco persistente em `/app`** e ganha um endereço **HTTPS grátis em `*.shardweb.app`** (domínio próprio só a partir do plano Core).

O projeto já vem preparado para isso:

| Arquivo | Para quê |
|---|---|
| `deploy/shardcloud/.shardcloud` | Configuração da ShardCloud (`MAIN=promo-server.jar`, `MEMORY=512`, `VERSION=latest`, `SUBDOMAIN`) |
| `deploy/shardcloud/promo.env.example` | Modelo das configurações do servidor (porta 80, banco em `/app/data`, token, endereço) |
| `sites.example.json` | Modelo das páginas públicas a ler |

**Passo a passo**
1. Copie `deploy/shardcloud/promo.env.example` para `promo.env` (na raiz do projeto) e preencha:
   - `ADMIN_TOKEN`: um segredo aleatório de pelo menos 16 caracteres. Sem isso, o painel `/admin` fica desligado.
   - `PUBLIC_BASE_URL`: `https://SEU-SUBDOMINIO.shardweb.app`, sem barra no final.
   - as credenciais das lojas, se já tiver (sem nenhuma, roda em modo demo).
2. Em `deploy/shardcloud/.shardcloud`, troque `SUBDOMAIN` por um nome só com letras e números que ninguém mais use.
3. (Opcional) Copie `sites.example.json` para `sites.json` e ajuste as páginas.
4. Gere o pacote: `gradle shardcloudZip`. O arquivo `build/distributions/promo-server-shardcloud.zip` já traz `promo-server.jar`, `.shardcloud`, `promo.env` e `sites.json`.
5. No painel da ShardCloud, crie uma aplicação Java e envie esse ZIP.
6. Abra `https://SEU-SUBDOMINIO.shardweb.app/health` (deve responder ok) e `/admin`. Entre com o token e rode "Coletar tudo".
7. No app: `./gradlew assembleRelease -PapiBaseUrl=https://SEU-SUBDOMINIO.shardweb.app/ -PuseFakeApi=false`.

**Por que as configurações ficam em `promo.env`**
Na documentação que li, o `.shardcloud` não tem campo para variáveis de ambiente. Por isso o servidor também lê o arquivo `promo.env` (e as variáveis de ambiente reais, se o painel tiver essa opção, têm prioridade sobre ele). O `promo.env` contém segredos: ele já está no `.gitignore`. Não publique em repositório aberto.

**Cuidados**
- **Memória:** a JVM usa por padrão uma parte da RAM do container. Se os logs mostrarem `OutOfMemoryError`, aumente `MEMORY` no `.shardcloud`.
- **Banco:** fica em `/app/data/promo.db`, no disco persistente. Antes de reenviar uma versão nova, baixe um backup pelo painel (`/admin`, botão "Baixar backup do banco"). Eu não encontrei na documentação o que acontece com os arquivos em `/app` quando você reenvia um ZIP, então não confie nisso sem o backup.
- **Deploy pelo GitHub:** a ShardCloud espera o JAR já compilado. O CI deste projeto gera o `promo-server-shardcloud.zip` como artefato a cada push na `main`.
- **IP de datacenter:** algumas lojas bloqueiam esses IPs. Se aparecer 403 ou 429, o robô pausa aquela loja por 6 horas.

**Outras hospedagens:** `gradle buildFatJar` gera `build/libs/promo-server.jar` (Java 17+). Com Docker, use o `Dockerfile` ou o `docker-compose.yml`.

## Testes
```bash
gradle test
```
Cobrem CSV, cada coletor (com respostas simuladas), robots.txt, extração de páginas, normalização, banco (histórico, expiração, abas, busca), serviço de coleta, configuração e todas as rotas.
Os coletores foram testados contra respostas de exemplo, não contra as APIs reais: rode uma coleta com suas credenciais e confira o relatório de `/admin/collect`.

## Próximos passos
1. Login e moderação para liberar publicação de usuários
2. Votos e comentários
3. Push remoto (FCM) em vez do aviso diário por consulta do app
4. Aprovação manual de ofertas antes de publicar
