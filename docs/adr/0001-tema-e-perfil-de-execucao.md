# ADR 0001 — Escalas ministeriais e perfil B

- Status: aceita
- Data: 2026-11-13

## Contexto

O projeto final exige dois serviços com bancos próprios, fila, cache e decisões documentadas. O tema vem do aplicativo Igreja Escalas, que trata de escalas ministeriais. Publicar uma escala e preparar os avisos são responsabilidades pequenas que podem ser demonstradas separadamente.

## Decisão

Criar o recorte **Escalas e Avisos**, usando **perfil B: Java 21, sem Docker**. Os dois serviços de negócio e a infraestrutura da fila executam em três processos Java. Maven Wrapper baixa as dependências e gera JARs executáveis. O código foi preparado inicialmente na pasta `projeto-final/` do aplicativo de origem e publicado como repositório Java independente em 23/11/2026; os módulos agora ficam na raiz.

O módulo é uma aplicação didática independente. O Next.js existente inspira o domínio; sua interface, autenticação e banco PostgreSQL não participam do fluxo Java. Uma escala é cadastrada pela API Java, com voluntários informados no pedido. O serviço de avisos mantém uma caixa de entrada consultável; não envia email nem WhatsApp.

## Alternativas consideradas

- Perfil A com Docker, PostgreSQL, RabbitMQ e Redis: mais próximo de uma implantação distribuída, mas exige Docker, ausente no ambiente verificado.
- Perfil C conceitual: permitiria explicar a arquitetura, mas não demonstrar o comportamento real.
- Converter o aplicativo Next.js inteiro: aumentaria o escopo e o risco para funcionalidades existentes.

## Consequências

O fluxo obrigatório roda localmente com Java. Há consistência eventual entre publicação e avisos. A integração com a interface existente fica para um trabalho futuro. As APIs e o broker escutam somente em `127.0.0.1`; o exemplo é de uma única igreja, sem autenticação ou isolamento de múltiplas igrejas.
