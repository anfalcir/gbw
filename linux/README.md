# GBW Linux 5.23 — distribuição completa

Esta pasta contém a cópia operacional completa do **Guitar Backing Wizard 5.23.0 para Linux**.

## Objetivo

Baixar a pasta `linux/` é suficiente para preservar, instalar, validar e executar a versão Linux 5.23 sem depender de arquivos externos, conversas anteriores ou reconstrução por memória.

## Estrutura

- `app/` — distribuição Linux completa e pronta para uso, preservada byte-a-byte.
- `BASELINE.md` — identidade e contrato do baseline congelado.
- `MANIFEST.sha256` — hashes SHA-256 de todos os arquivos preservados em `app/`.
- `ci/` — validadores de repositório; não altera a distribuição congelada em `app/`.

Dentro de `app/` ficam o código-fonte completo, assets, testes, documentação, `requirements.txt`, `install.sh`, `run.sh`, `criar_atalho.sh` e `validate_local.sh`.

## Uso

```bash
cd linux/app
./install.sh
```

Depois, para executar diretamente da pasta:

```bash
./run.sh
```

Para validar a distribuição local:

```bash
./validate_local.sh
```

Para validar a integridade byte-a-byte da cópia preservada:

```bash
cd linux
sha256sum -c MANIFEST.sha256
```

## Identidade

Baseline: `5.23.0`

SHA-256 do pacote original validado e usado para materializar esta árvore:

`ca4e0b1b95e9f308deb9ae8bccce673a091631105cb4fbd020909f6fef64ce4a`

A árvore expandida em `linux/app/` é a forma canônica de preservação no repositório. O ZIP original não é necessário para instalar ou executar o sistema. Qualquer divergência no manifesto significa que a cópia deixou de ser byte-a-byte equivalente à baseline materializada.

### Nota sobre a CI de GUI

A baseline v5.23 permanece intocada, inclusive seus testes. Três asserts históricos de `tests/test_gui_smoke.py` consultam `winfo_manager()` diretamente em `CTkScrollableFrame`; no CustomTkinter real, o frame interno pertence a um `Canvas`, enquanto a moldura pública é gerenciada por `grid`. A CI executa todos os demais testes congelados e usa `ci/validate_gui_contract.py` para validar de forma equivalente e mais estável esses três contratos contra a moldura pública real, sem modificar `app/`.
