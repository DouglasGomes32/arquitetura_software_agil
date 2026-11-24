# ADR 0004 — Cache local dos avisos por voluntário

- Status: aceita
- Data: 2026-11-13

## Contexto

A consulta de avisos tende a se repetir enquanto o voluntário aguarda uma publicação. O requisito pede explicar conteúdo, duração e invalidação do cache.

## Decisão

Manter um cache em memória no serviço Avisos:

| Aspecto | Regra |
|---|---|
| Chave | `voluntarioId` |
| Valor | Lista imutável de avisos desse voluntário, inclusive lista vazia |
| TTL | 60 segundos após carregar; HIT não renova o prazo |
| Limite | 1.000 voluntários; remove o menos recentemente consultado ao atingir o limite |
| Invalidação | Depois do commit de um evento novo, remove as chaves dos voluntários afetados, antes do ACK |
| Expiração | Na próxima consulta, remove entradas cujo prazo acabou |
| Reinício | Cache começa vazio; dados continuam no H2 |
| Observação | Cabeçalho `X-Cache: MISS` ou `X-Cache: HIT` |

Consulta/carregamento e commit/invalidação usam a mesma trava no processo. Isso impede que uma consulta carregada antes do commit seja armazenada depois da invalidação. Eventos duplicados não alteram o banco nem precisam invalidar a lista. O navegador recebe `Cache-Control: no-store`; o cache avaliado é o do serviço.

## Alternativas consideradas

- Redis: útil com várias réplicas, mas exigiria outro runtime no perfil Java.
- Só TTL, sem invalidação: um novo aviso poderia ficar oculto por até 60 segundos.
- Cache no produtor: não evita a consulta ao banco que contém os avisos.

## Consequências

O cache reduz leituras H2 e funciona sem infraestrutura adicional. A trava privilegia clareza e consistência local sobre throughput. A política só é correta para a instância única do exemplo. Antes de escalar horizontalmente, substituir por cache compartilhado ou propagar invalidações. `-Dcache.ttl.seconds` permite alterar o prazo para experimentação; o padrão da entrega é 60.
