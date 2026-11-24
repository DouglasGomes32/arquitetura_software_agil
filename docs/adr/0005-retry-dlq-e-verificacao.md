# ADR 0005 — Retry, DLQ e verificação automática

- Status: aceita
- Data: 2026-11-13

## Contexto

Falhas transitórias não devem perder mensagens, e uma mensagem inválida não pode travar a fila indefinidamente. Também é necessário apresentar evidências dos comportamentos obrigatórios.

## Decisão

O consumidor usa sessão JMS transacionada. Falhas causam `rollback`, com espera de 250 ms e no máximo três reentregas, além da primeira tentativa. Ao esgotar o limite, o ActiveMQ encaminha a mensagem para `ActiveMQ.DLQ`. O consumidor registra o erro e continua processando eventos válidos. O produtor mantém sua outbox e repete a tentativa enquanto houver pendência, sem limite de tentativas.

Testar com JUnit, HTTP real, H2 em arquivos temporários e um broker real via TCP. Os cenários cobrem publicação, duplicação, concorrência, cache, reinício, indisponibilidade e DLQ. O cache recebe um relógio controlável para testar 60 segundos sem aguardar esse tempo real. O script PowerShell de demonstração também executa os JARs em três processos distintos.

## Alternativas consideradas

- Descartar mensagens inválidas: dificulta diagnóstico e recuperação.
- Retry infinito no consumidor: uma mensagem envenenada pode bloquear as seguintes.
- Testar só com mocks: não prova persistência, ACK, restrições SQL ou transporte.
- Kafka, SAGA e contratos entre equipes: sem necessidade para esse fluxo de publicação única.

## Consequências

A DLQ exige análise e eventual reenvio manual após correção; não há painel operacional nem reprocessamento automático da DLQ. Logs e métricas de `/health` bastam para a banca local. As métricas de duplicados e conexão são locais ao processo e reiniciam; o total de eventos processados vem do banco e persiste.

Referência: [reentregas e DLQ no ActiveMQ](https://activemq.apache.org/components/classic/documentation/message-redelivery-and-dlq-handling).
