# Entrega e histórico

O enunciado recomenda commits ao longo das aulas. O projeto foi preparado em novembro de 2026 e publicado neste repositório em 23/11/2026; não há histórico fictício nem commits com data retroativa. Para futuras evoluções, uma divisão útil é:

1. Tema e esqueleto: README, módulos Java e ADRs 0001–0002.
2. Comunicação: fila, serviços, bancos, outbox e ADR 0003.
3. Cache: implementação, observação HIT/MISS e ADR 0004.
4. Fechamento: testes, retry/DLQ, scripts, roteiro e ADR 0005.

Faça commits que representem o trabalho realmente revisado. Não publique `.env`, diretórios `data/`, `target/`, `node_modules/` ou logs.

Para entregar pelo GitHub, use [arquitetura_software_agil](https://github.com/DouglasGomes32/arquitetura_software_agil), contendo o código e `docs/adr/`. Para entregar um arquivo independente, rode o empacotador na raiz do repositório:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/empacotar.ps1
```

O ZIP contém os módulos Java, Maven Wrapper, README, scripts, testes e ADRs em `docs/adr/`. Não depende do aplicativo web. O avaliador deve entrar na pasta extraída e executar `mvnw.cmd verify`.

Depois de extrair o ZIP, você já está na raiz do projeto Java. O nome `projeto-final/` no contexto histórico do ADR 0001 refere-se à organização anterior, no aplicativo que inspirou o tema.
