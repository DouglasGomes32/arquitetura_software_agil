# ADR 0002 — Bancos H2 separados por serviço

- Status: aceita
- Data: 2026-11-13

## Contexto

Cada serviço deve ser proprietário dos seus dados. Compartilhar as tabelas do aplicativo existente impediria demonstrar esse limite.

## Decisão

- **Escalas:** arquivo H2 `data/escalas/banco.mv.db`, tabelas `escalas` e `outbox`.
- **Avisos:** arquivo H2 `data/avisos/banco.mv.db`, tabelas `eventos_processados` e `avisos`.

Cada serviço conhece apenas seu arquivo. Não há consultas, chaves estrangeiras ou transações entre os dois bancos. O evento leva o nome do evento, a data e os voluntários necessários para gerar os avisos sem consultar o produtor. Os arquivos ficam persistidos entre reinicializações.

## Alternativas consideradas

- Um banco e schemas diferentes: menor separação e dependência de um servidor comum.
- Dois PostgreSQL: adequado para produção, mas adiciona instalação e configuração ao perfil sem Docker.
- H2 em memória: testes rápidos, mas perderia dados ao encerrar.

## Consequências

H2 embarcado mantém o perfil Java e reduz a instalação. O banco é local ao processo e não suporta escalar múltiplas instâncias deste exemplo. Os testes usam diretórios temporários próprios, sem acessar o PostgreSQL ou o `.env` do aplicativo.

Referência: [modos de conexão e persistência do H2](https://h2database.github.io/html/features.html).
