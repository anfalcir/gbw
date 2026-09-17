# Workflow de Desenvolvimento e CI

## Regra principal

O desenvolvimento Android deve ser executável autonomamente por uma sessão do ChatGPT usando o conector GitHub:

```text
consultar HEAD remoto
→ editar arquivos
→ criar commit
→ push/atualizar ref
→ GitHub Actions dispara automaticamente
→ consultar workflow run
→ consultar jobs/logs
→ corrigir se necessário
→ repetir
→ baixar artifact APK quando verde
```

Não é necessário `workflow_dispatch` para o fluxo normal. Ele existe apenas como contingência.

## Antes de escrever

1. consultar `main`/branch ativa e confirmar HEAD remoto;
2. ler `docs/CURRENT_STATE.md`;
3. ler `docs/ANDROID_MIGRATION_PLAN.md`;
4. ler `docs/PARITY_MATRIX.md`;
5. verificar runs recentes para não sobrescrever trabalho concorrente.

## Política de commits

- commits pequenos e semanticamente fechados;
- mensagens claras (`android: ...`, `linux: ...`, `docs: ...`, `ci: ...`);
- não disparar commits artificiais apenas para CI se não houver mudança necessária;
- após cada commit Android, acompanhar a CI automaticamente até conclusão;
- em falha, ler logs do job antes de alterar código;
- não ocultar falhas com `continue-on-error` em gates obrigatórios.

## Android CI

Trigger automático: todo `push` em qualquer branch e todo Pull Request.

Gates obrigatórios:

- smoke/paridade de domínio;
- golden host Rubber Band R3;
- testes unitários;
- lint;
- assemble debug;
- verificação nativa do APK;
- hash/metadata SHA-256;
- upload do APK;
- upload de relatórios mesmo em falha, quando produzidos.

## Linux Baseline CI

Trigger automático: alterações em `linux/**`, `docs/PARITY_MATRIX.md` ou no workflow Linux.

Gates:

- identidade `5.23.0` e SHA do pacote de origem;
- `linux/MANIFEST.sha256` e verificação integral da árvore expandida;
- ausência do ZIP de staging em `linux/app/`;
- permissões executáveis de `install.sh`, `run.sh`, `criar_atalho.sh`, `validate_local.sh`, `guitar_backing_wizard.py` e `audio_intelligence.py`;
- sintaxe Bash;
- `compileall` Python;
- testes unitários/core;
- testes do pipeline de áudio com FFmpeg;
- testes GUI em Xvfb;
- self-test da aplicação em Xvfb.

O workflow instala apenas dependências mínimas necessárias à validação. Modelos ML pesados/venvs persistentes não entram no Git nem são tratados como parte da integridade byte-a-byte da distribuição.

## Segurança

Nunca versionar:

- keystore privado;
- passwords/tokens;
- músicas ou projetos de usuário;
- outputs de separação/exportação;
- modelos ML pesados baixados em runtime;
- venv/cache/build directories.
