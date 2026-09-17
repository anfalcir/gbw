# GBW Linux 5.23 — distribuição completa

Esta pasta contém a cópia operacional completa do **Guitar Backing Wizard 5.23.0 para Linux**.

## Objetivo

Baixar a pasta `linux/` é suficiente para preservar, instalar, validar e executar a versão Linux 5.23 sem depender de arquivos externos, conversas anteriores ou reconstrução por memória.

## Estrutura

- `app/` — distribuição Linux completa e pronta para uso.
- `BASELINE.md` — identidade e contrato do baseline congelado.

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

Para validar a distribuição:

```bash
./validate_local.sh
```

## Identidade

Baseline: `5.23.0`

SHA-256 do pacote original validado e usado para materializar esta árvore:

`ca4e0b1b95e9f308deb9ae8bccce673a091631105cb4fbd020909f6fef64ce4a`

A árvore expandida em `linux/app/` é a forma canônica de preservação no repositório. O ZIP original não é necessário para instalar ou executar o sistema.
