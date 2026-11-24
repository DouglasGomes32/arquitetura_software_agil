# ADR 0003 — ActiveMQ, outbox e consumidor idempotente

- Status: aceita
- Data: 2026-11-13

## Contexto

O serviço Escalas precisa enviar uma publicação ao serviço Avisos. Uma queda entre o commit no banco e o envio pode perder o evento; uma queda entre o processamento e o ACK pode provocar uma nova entrega.

## Decisão

Usar **ActiveMQ Classic 6.4.0**, executado como infraestrutura Java separada, com transporte TCP local e persistência KahaDB. A fila `escalas.publicadas.v1` transporta `TextMessage` JSON persistentes. O contrato tem versão, tipo, `eventoId` UUID, `escalaId`, instante, evento, data e voluntários.

O produtor altera a escala para `PUBLICADA` e insere a mensagem na outbox na mesma transação H2. Um dispatcher consulta até 50 pendências a cada 500 ms, envia em sessão JMS transacionada, confirma no broker e só então marca a outbox como enviada. Não remove mensagens pendentes quando há erro. Repetir o pedido de publicação retorna o mesmo `eventoId`, inclusive com requisições concorrentes, graças ao bloqueio da linha.

O consumidor valida o contrato e tenta inserir `eventoId` na chave primária de `eventos_processados`. Essa inserção e todos os avisos são uma única transação. Um conflito nessa chave significa evento já processado e não grava novos avisos. A restrição `UNIQUE(evento_id, voluntario_id)` reforça a unicidade de cada aviso. O ACK JMS vem depois do commit no banco e da invalidação do cache.

O endpoint didático `/escalas/{id}/reentregar` coloca o mesmo evento na outbox, preservando seu identificador e conteúdo. Não representa uma nova publicação.

## Alternativas consideradas

- HTTP síncrono: acopla a publicação à disponibilidade do consumidor e não atende à fila.
- Kafka: maior custo operacional para um único tipo de evento e um consumidor.
- Marcar a outbox antes do envio: pode perder evento se o processo cair.
- Gravar o identificador e os avisos em transações separadas: pode perder avisos ou duplicá-los.
- Transação distribuída XA: evita certas janelas, mas traz complexidade desnecessária.

## Consequências

A entrega é **pelo menos uma vez**, com efeito idempotente no banco do consumidor. Não prometemos entrega exatamente uma vez: queda depois do envio e antes da marcação da outbox gera nova entrega segura. Não há garantia de ordem entre escalas diferentes; cada escala só é publicada uma vez neste recorte.

A outbox enviada e os identificadores processados são mantidos para a demonstração e para reentrega. Uma política de retenção exigiria definir até quando o sistema aceita replays; apagar identificadores sem esse cuidado permitiria duplicações futuras.

Referências: [broker Java e transporte TCP](https://activemq.apache.org/components/classic/documentation/how-do-i-embed-a-broker-inside-a-connection), [persistência](https://activemq.apache.org/components/classic/documentation/persistence).
