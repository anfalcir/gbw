# GBW — Estado Atual

Data: 2026-09-18
Branch: `dev/android-6.0`
Baseline Linux: GBW Linux 5.23.0, somente leitura.

## Candidato em preparação

- versão: `6.0.0-alpha12`
- versionCode: `17`
- objetivo: hardening pós-homologação alpha11 + paridade de sessão/backup/UX.

## Evidência física que motivou alpha12

O vídeo `132666.mp4` mostrou:
- ANR ao definir a pasta de backup;
- destino exibido como URI/código;
- Drive organizado por `projects/<UUID>/files` e hashes planos;
- ausência de ação clara para fechar o projeto;
- mensagens de processamento permanecendo depois do término.

## Correções do alpha12

- operações pesadas SAF/Drive saem da UI thread;
- destino passa a usar display name/provider;
- reconcile inicial é WorkManager;
- backup layout v2 legível por artista/música e por categoria;
- migração segura do layout alpha11;
- projeto automático `Artista - Música`, com normalização de iniciais;
- migração de metadata já persistida quando possível;
- lista de projetos por artista/música;
- ação Fechar projeto e retorno ao estado inicial;
- restore remoto não abre projeto silenciosamente;
- job cards desaparecem quando a operação termina;
- mensagens de início são substituídas por sucesso/erro/cancelamento;
- inventário contém somente artefatos referenciados pelo estado atual;
- cancelamento cooperativo de upload/restore.

## Invariantes preservados

- Demucs-only `htdemucs_6s`;
- OpenBLAS default 1 thread, política 1/2;
- FFmpeg;
- Rubber Band R3;
- backing = drums+bass+other+vocals+piano;
- guitar separada;
- shared gain sobre backing+guitar recombinados;
- UUID interno imutável;
- SHA-256;
- commit remoto antes de cleanup;
- `linux/` não é alterado.

## Gate

O source candidate alpha12 só deve ser entregue para homologação após Unit Tests, Lint, assemble, assinatura e gates nativos da Android CI.
