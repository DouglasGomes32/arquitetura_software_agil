# Matriz de verificação

Execute `mvnw.cmd verify` na raiz do repositório. Os testes criam bancos H2 em diretórios temporários e usam ActiveMQ real por TCP, com portas alocadas pelo sistema. Não usam banco externo nem segredos da aplicação que inspirou o domínio.

| Critério | Cenário automático | Resultado esperado |
|---|---|---|
| Serviços e bancos separados | `FluxoTest.publicaPelaFilaInvalidaCacheEDeduplicaMesmoDepoisDeReiniciar` | POST em Escalas gera avisos consultáveis em outra API, usando arquivos diferentes |
| Reentrega | Mesmo teste | Evento duplicado é consumido, métrica aumenta e quantidade permanece igual |
| Reinício do consumidor | Mesmo teste | Banco reconhece novamente o evento já processado |
| Produtor reiniciado com outbox | `FluxoTest.outboxSobreviveProdutorReiniciadoEFilaGuardaMensagemSemConsumidor` | Publica sem broker, reinicia produtor e transmite a pendência |
| Persistência da fila | Mesmo teste | Broker é encerrado sem consumidor e reiniciado; a mensagem volta do KahaDB |
| Contrato e DLQ | `FluxoTest.rejeitaEntradaInvalidaEMensagemEnvenenadaVaiParaDlq` | Entradas HTTP inválidas retornam 400; JSON inválido vai à DLQ; mensagem seguinte funciona |
| Publicação concorrente | `FluxoTest.publicacoesConcorrentesGeramUmUnicoEvento` | Oito publicações resultam no mesmo eventoId e uma pendência |
| TTL de 60 s e limite | `CacheTest.expiraInvalidaIsolaVoluntariosELimitaMemoria` | MISS/HIT, expiração no limite, remoção por uso e isolamento entre chaves |
| Invalidação real | Primeiro cenário de `FluxoTest` | Lista vazia antes da publicação é invalidada e nova consulta retorna MISS com aviso |
| Atomicidade no consumidor | `TransacaoTest.erroNoSegundoAvisoReverteTambemPrimeiroAvisoEMarcador` | Falha SQL no segundo aviso não deixa o primeiro nem o marcador; reenvio posterior funciona |
| Duplicatas concorrentes | `TransacaoTest.mensagensConcorrentesSoGravamUmConjuntoDeAvisos` | Oito chamadas com mesmo evento gravam um único conjunto de avisos |
| JARs e processos reais | `scripts/demonstrar.ps1` | Três JVMs separadas; cache, duplicação, consumidor indisponível e recuperação aprovados |
| Painel no navegador | Verificação manual em 16/11/2026 | Cadastro com dois voluntários, publicação, consulta vazia MISS/HIT, invalidação após publicação e reentrega mantendo um aviso |
| CORS do painel | Requisições com origens permitida e externa | Origem local recebe autorização de leitura e `X-Cache`; origem externa não recebe autorização CORS |

Para repetir a verificação visual, execute `scripts/abrir-painel.ps1` e siga a seção **Testar nas telas** do README. O painel permanece ativo para interação. Os comandos `scripts/fechar-painel.ps1` e `scripts/abrir-painel.ps1` também foram conferidos para encerrar e reabrir os processos com dados preservados.

Há **7 testes JUnit**: quatro cenários de fluxo, dois de transação e um de cache. Várias verificações fazem parte de um mesmo cenário. Os resultados reais da última execução estão em `verificacao/target/surefire-reports/`; os relatórios de build não são versionados.

Os testes verificam efeitos e falhas relevantes, sem prometer provar todas as possíveis interrupções de energia ou todas as combinações de concorrência. A demonstração usa dados e logs próprios em `data/demo-*`, excluídos do Git.
