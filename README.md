# PromoApp

App Android de promoções e cupons, em Kotlin, com foco em transparência de preço (histórico e selo de "preço real").

![CI](https://img.shields.io/badge/CI-GitHub%20Actions-blue)

## Stack
Kotlin 2 · Jetpack Compose · Material 3 · Navigation type-safe · Hilt · Paging 3 · Room · Retrofit + kotlinx.serialization · Coil

## Como rodar
1. Abra a pasta no Android Studio (Ladybug ou mais novo, JDK 17) e aguarde o Sync (ele gera o `gradlew`).
2. Rode no emulador. Por padrão usa `FakeDealApi`, sem precisar de backend.
3. Na primeira vez, rode `gradle spotlessApply` (ou `./gradlew spotlessApply`) para padronizar a formatação.

Se o Sync reclamar de versões, atualize `gradle/libs.versions.toml` pelas sugestões do Android Studio.

## Comandos
| Objetivo | Comando |
|---|---|
| Testes unitários | `./gradlew testDebugUnitTest` |
| Verificar estilo | `./gradlew spotlessCheck` |
| Corrigir estilo | `./gradlew spotlessApply` |
| Build debug | `./gradlew assembleDebug` |
| Lint do Android | `./gradlew lintDebug` |

## Arquitetura
```
ui (Compose, ViewModels)  ->  domain (modelos, casos de uso, contratos)  <-  data (Retrofit, Room, Paging)
                                          ^
                                 di (Hilt liga as camadas)
```
- **domain** não depende de Android nem de bibliotecas de rede. Regras como o selo de preço (`PriceHistory.verdictFor`) vivem aqui e têm testes.
- **data** é offline-first: o feed vem do Room e o `DealRemoteMediator` o atualiza pela rede (cache de 15 min, pull to refresh e aviso de modo offline). O detalhe usa `Result` (`safeApiCall`).
- **ui** usa estado imutável (`UiState`), strings em `strings.xml` e navegação com rotas tipadas (`Routes.kt`).
- Módulo único, organizado por camadas, pronto para virar `core`, `data`, `domain` e `feature-*`.

## API real
Em `app/build.gradle.kts`, mude `USE_FAKE_API` para `false` e ajuste `API_BASE_URL`. Contrato esperado:

| Método | Rota | Retorno |
|---|---|---|
| GET | `/deals?tab=&page=&page_size=` | lista de `DealDto` |
| GET | `/deals/{id}` | `DealDto` |
| GET | `/deals/{id}/price-history` | lista de `PricePointDto` |
| GET | `/deals/search?q=&stores=&category=&min_price=&max_price=&sort=&page=&page_size=` | lista de `DealDto` (`stores` separado por vírgula; `sort`: relevance, lowest_price, biggest_discount, newest) |
| POST | `/deals` | cria uma oferta (`NewDealRequest`) e devolve o `DealDto` |
| GET | `/deals/mine` | ofertas publicadas pelo usuário logado |
| GET | `/coupons` | lista de `CouponDto` (código, loja, desconto, compra mínima, validade em ms) |
| GET | `/filters` | `FilterOptionsDto` (lojas e categorias disponíveis) |

## Qualidade
- Testes unitários: regras de desconto, veredito de preço, mapeadores do cache, filtros de busca, entrada de preço e `safeApiCall`.
- Banco com schema exportado em `app/schemas`. A partir do primeiro release, crie Migrations reais.
- Estilo: Spotless + ktlint (`.editorconfig`).
- CI: `.github/workflows/ci.yml` roda estilo, testes e build a cada push e pull request.
- Release: R8 e shrink de recursos ativados, com regras para kotlinx.serialization.

## Alertas de preço
- Na tela de detalhe, "Criar alerta de preço" define o preço alvo (sugestão: 10% abaixo do atual).
- Um `PriceAlertWorker` (WorkManager, a cada 6 h e com internet) confere os preços e notifica quando o alvo é atingido. A notificação abre a oferta por deep link (`promoapp://deal/{id}`).
- A aba Alertas lista os alertas e tem o botão "Verificar agora".
- Com a API falsa, o preço varia a cada hora (até -12%), então dá para ver um alerta ser atingido.
- Os alertas são locais. Push remoto (FCM) depende de backend e de um projeto Firebase (`google-services.json`).

## Publicação de ofertas
- Perfil > **Publicar oferta**: título, link, loja, preço, preço original, imagem, cupom, frete grátis e categoria.
- As regras de validação ficam no domínio (`DealDraft.validate`) e valem também no caso de uso. Os erros só aparecem depois da primeira tentativa de publicar.
- Depois de publicar, o app abre a oferta. A lista "Minhas ofertas" no Perfil mostra o que você já postou.
- O cache do feed é marcado como velho: a oferta nova aparece ao puxar para atualizar ou ao trocar de aba na Home.
- Por enquanto o autor é um nome salvo no aparelho (DataStore). Login real (Firebase Auth) e upload de imagem entram depois.

## Oferta do dia e compartilhamento
- Perfil > **Oferta do dia**: um aviso por dia, às 9h (fuso do aparelho), com a oferta de maior desconto ainda não avisada (mínimo de 15%).
- Funciona por consulta em segundo plano (WorkManager), sem Firebase. O botão só liga se a permissão de notificação for concedida.
- Na tela de detalhe, o botão **Compartilhar** envia título, preço e link.

## Servidor
O projeto `promo-server` (Kotlin + Ktor) coleta as ofertas das lojas e entrega neste contrato. Veja o README dele.
Para apontar o app para o servidor, sem editar código:
```bash
./gradlew assembleDebug -PapiBaseUrl=http://10.0.2.2:8080/ -PuseFakeApi=false     # servidor local no emulador
./gradlew assembleRelease -PapiBaseUrl=https://seu-servidor/ -PuseFakeApi=false   # produção (HTTPS)
```
O endereço precisa terminar com `/`. O manifesto de debug já libera HTTP para o emulador.

## Roadmap
1. Teste de integração do `DealRemoteMediator` (Room em memória)
2. Login (Firebase Auth) e upload de imagem pela câmera ou galeria
3. Votos e comentários nas ofertas
4. Push remoto (FCM) no lugar da consulta diária
5. Widget de home (Glance)
6. Cache offline dos cupons
7. Divisão em módulos
