# CI Automatizada do GBW

## Objetivo

Permitir que uma sessão do ChatGPT faça o ciclo completo de desenvolvimento Android sem depender de ações manuais do usuário:

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
2. testes unitários Android;
3. Android Lint;
4. `assembleDebug`;
5. SHA-256 do APK;
6. artifact com APK + metadata;
7. relatórios de testes/lint quando disponíveis.

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

## Linux CI

Workflow: `.github/workflows/linux-ci.yml`.

Roda automaticamente quando `linux/**` ou o próprio workflow Linux muda. O baseline Linux é congelado, portanto esse workflow normalmente será pouco acionado.
