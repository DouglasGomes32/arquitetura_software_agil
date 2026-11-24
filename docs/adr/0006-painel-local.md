# ADR 0006 — Painel web local para testar nas telas

- Status: aceita
- Data: 2026-11-16

## Contexto

O usuário quer testar o projeto por telas. O recorte Java inicialmente oferecia apenas APIs e um script de demonstração. O perfil B deve continuar funcionando sem Docker ou Node.

## Decisão

Servir HTML, CSS e JavaScript estáticos no JAR do serviço Escalas, em `/` e `/painel`. O painel permite criar rascunhos, listar até 100 escalas, publicar, reentregar o evento original e consultar avisos por voluntário. Estados vazios, mensagens de erro, indicadores de saúde e o resultado real de `X-Cache` ajudam na demonstração.

O navegador faz as escritas na API Escalas, na mesma origem, e leituras diretamente na API Avisos. O consumidor permite CORS de leitura apenas para a origem configurada em `painel.origin` (padrão `http://127.0.0.1:8081`, aceitando também o equivalente com `localhost`) e expõe `X-Cache`. O endereço de Avisos vem de `/painel/config`, configurável por `avisos.url` no produtor, com padrão `http://127.0.0.1:8082`.

A comunicação de negócio entre os dois serviços continua exclusivamente pelo evento na fila. Não há acesso compartilhado aos bancos. A interface usa `textContent` para inserir nomes, eventos e avisos, evitando interpretar esses conteúdos como HTML. As respostas da interface não são armazenadas no cache do navegador.

Os scripts `abrir-painel.ps1` e `fechar-painel.ps1` gerenciam três processos Java locais com dados em `data/painel/`. O encerramento verifica PID e instante de início para não atingir outro processo que reutilizou o identificador. O script de abertura verifica as portas e a saúde antes de anunciar que o painel está pronto.

## Alternativas consideradas

- Integrar as telas do Next.js: exigiria outro runtime e misturaria o recorte didático com o aplicativo existente.
- Criar um terceiro serviço de negócio para a interface: desnecessário para recursos estáticos e dois formulários.
- Liberar CORS para qualquer origem: desnecessário para o painel local.
- Usar apenas documentação com comandos: não atende ao pedido de testar nas telas.

## Consequências

A interface é incluída no JAR e no ZIP sem instalação adicional. A API de listagem é limitada a 100 escalas; não oferece paginação, edição ou exclusão. Alterar as portas exige ajustar `avisos.url` no produtor e `painel.origin` no consumidor. O painel continua sendo uma demonstração local de uma igreja, sem autenticação.
