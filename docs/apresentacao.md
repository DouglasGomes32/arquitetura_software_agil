# Roteiro de apresentação — até 10 minutos

Reserve **7 minutos de explicação e demonstração** e **3 minutos para perguntas**. Tenha a compilação concluída antes da banca; a primeira execução baixa dependências.

## 0:00–0:45 — Problema e perfil

“Quando uma escala é publicada, precisamos preparar os avisos sem depender da disponibilidade imediata de quem os processa. Meu projeto usa o perfil B: Java 21, sem Docker. Há dois serviços de negócio e uma infraestrutura de fila.”

Explique que é um recorte independente do aplicativo Igreja Escalas e que o aviso é uma caixa de entrada local, sem envio de mensagens externas.

## 0:45–1:45 — Desenho e bancos

Mostre o diagrama no README. Aponte o serviço Escalas, seu H2 e a outbox; depois o ActiveMQ e o serviço Avisos, com outro H2 e cache. Nenhum serviço lê o banco do outro. O evento leva os dados necessários para preparar os avisos.

## 1:45–4:15 — Demonstração

Com as portas livres, execute na raiz do repositório:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/demonstrar.ps1
```

Acompanhe os passos do script:

1. Consulta vazia: `MISS` e `HIT`.
2. Publicação e consumo pela fila: primeiro aviso, invalidação e novo `MISS`.
3. Reentrega do mesmo evento: continua um aviso.
4. Consumidor desligado, outra escala publicada, reinício: mensagem chega e os dados anteriores permanecem.
5. Reentrega após reinício: continuam dois avisos, um por publicação.

Se optar pelo fluxo manual, mantenha três terminais abertos e prepare os comandos do README. Não rode o script automático sobre os mesmos serviços: ele requer as portas livres e cria seus próprios processos.

## 4:15–5:15 — Idempotência e falhas

Mostre `AvisosBanco.processar`: marcador e avisos na mesma transação. A chave primária de `eventoId` rejeita mensagens já processadas. O ACK só vem depois do banco.

Explique a outbox: publicação e evento são gravados juntos. Se a fila cair, o evento fica salvo. Se a queda ocorrer depois do envio e antes da marcação, poderá haver reentrega; o consumidor a reconhece.

## 5:15–6:00 — Cache

“Guardo a lista de avisos por voluntário, inclusive lista vazia. O prazo é 60 segundos desde a carga, sem renovar em HIT. Depois do commit de novos avisos, apago as entradas das pessoas envolvidas. Reiniciar esvazia o cache, mas preserva o banco. O limite é mil entradas.”

O teste de TTL usa um relógio controlado. Se quiser mostrar o prazo ao vivo, faça isso no fluxo manual com antecedência; esperar um minuto durante a banca consome o tempo.

## 6:00–7:00 — Escolhas, testes e IA

Mostre os ADRs e os resultados de `mvnw.cmd verify`. Explique uma alternativa rejeitada em cada ponto: Docker, banco compartilhado, HTTP síncrono, Redis e retry infinito. O ADR 0006 explica o painel para testar pelas telas.

Informe com honestidade: houve assistência de IA na elaboração. Diga apenas o que você efetivamente conferiu por conta própria. Antes da banca, execute o fluxo, leia as transações e faça a lista de revisão abaixo.

## 7:00–10:00 — Perguntas

| Pergunta possível | Resposta que você deve conseguir explicar |
|---|---|
| Por que dois bancos? | Cada serviço é dono dos seus dados e recebe os dados do outro pelo evento. |
| Como não duplica? | Identificador único do evento e transação que inclui marcador e avisos. |
| É exatamente uma vez? | A entrega é pelo menos uma vez; o efeito de gravar os avisos é idempotente. |
| E se cair antes do ACK? | A fila entrega novamente; a chave única impede uma segunda gravação. |
| E se cair antes de enviar? | A outbox persiste no mesmo commit da publicação e volta a ser tentada. |
| Por que não Redis? | Uma instância Java basta para o perfil B; várias réplicas exigiriam outra estratégia de invalidação. |
| O que vai para a DLQ? | Mensagem inválida ou que falha na primeira tentativa e nas três reentregas. |
| Por que avisos e não email real? | O foco é comunicação assíncrona e persistência; envio externo adicionaria credenciais e outro efeito a tornar idempotente. |
| O que falta para produção? | Autenticação, isolamento por igreja, observabilidade, retenção e implantação com dados e broker protegidos. |

## Revisão pessoal antes da entrega

- [ ] Executei `mvnw.cmd verify` e entendi os cenários.
- [ ] Executei a demonstração em três processos.
- [ ] Encontrei os dois arquivos H2 em diretórios diferentes.
- [ ] Conferi que `eventoId` permanece igual na reentrega.
- [ ] Entendi onde ficam o commit do banco, o commit JMS e a invalidação do cache.
- [ ] Li os ADRs e consigo defender as escolhas.
- [ ] Personalizei a explicação e registrei honestamente o uso de IA.
