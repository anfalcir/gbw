# CI Automatizada do GBW

## Objetivo

Permitir que uma sessão do ChatGPT faça o ciclo completo de desenvolvimento Android sem depender de ações manuais do usuário e manter a baseline Linux verificável automaticamente.

```text
confirmar HEAD
→ alterar código
→ commit/push
→ CI dispara automaticamente
→ consultar run/jobs/logs
→ corrigir
→ novo commit
→ CI novamente
→ baixar artifact APK quando verde
```

## Android CI

Workflow: `.github/workflows/android-ci.yml`

**Trigger principal:** todo `push` em qualquer branch do repositório.

Também roda em Pull Requests. `workflow_dispatch` é apenas contingência; não é o fluxo normal.

Gates obrigatórios:

1. smoke/paridade de domínio;
2. golden Rubber Band R3 host;
3. testes unitários Android;
4. Android Lint;
5. `assembleDebug`;
6. verificação da biblioteca nativa no APK;
7. SHA-256/metadata do APK;
8. artifact com APK;
9. relatórios de testes/lint quando disponíveis.

Não usar `continue-on-error` nos gates acima.

## Uso por agente/chat

Após um commit:

1. descobrir o SHA criado;
2. consultar o workflow run referente a esse SHA;
3. acompanhar jobs até conclusão;
4. se houver falha, abrir os logs do job exato;
5. corrigir no código, sem rerun cego;
6. criar novo commit, que disparará nova CI automaticamente;
7. quando verde, consultar/download do artifact APK;
8. registrar SHA-256 e atualizar `docs/CURRENT_STATE.md` se o gate mudou.

## Linux Baseline CI

Workflow: `.github/workflows/linux-ci.yml`.

Roda automaticamente quando `linux/**`, `docs/PARITY_MATRIX.md` ou o próprio workflow Linux muda.

Gates obrigatórios:

1. presença de `linux/README.md`, `linux/BASELINE.md`, `linux/MANIFEST.sha256` e do validador CI;
2. identidade `5.23.0` e SHA-256 do pacote de origem;
3. `sha256sum -c linux/MANIFEST.sha256` sobre toda a distribuição expandida;
4. ausência de ZIP de staging em `linux/app/`;
5. permissões executáveis dos scripts/entrypoints;
6. `bash -n` nos scripts shell;
7. `python -m compileall`;
8. testes `test_core` e `test_audio_pipeline` com FFmpeg;
9. GUI sob Xvfb: todos os testes congelados não acoplados à implementação interna de `CTkScrollableFrame` + equivalentes semânticos para os três contratos de geometria em `linux/ci/validate_gui_contract.py`;
10. self-test da aplicação em Xvfb.

A CI não edita nem adapta `linux/app/`: o manifesto continua cobrindo a distribuição v5.23 byte-a-byte. O adaptador de CI existe fora de `app/` apenas para evitar que detalhes internos do CustomTkinter (`Canvas` do scrollable frame) sejam confundidos com falhas funcionais de layout.

A Linux CI não instala Demucs ou BS-RoFormer completos apenas para validar o baseline: esses componentes pesados são testados por interfaces/mocks onde apropriado; o objetivo deste workflow é provar preservação, importabilidade e regressão funcional do pacote congelado sem transformar a CI em download de modelos/pesos.
