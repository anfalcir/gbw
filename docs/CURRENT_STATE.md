# GBW — Estado Atual

**Data:** 2026-09-18  
**Branch ativa:** `dev/android-6.0`  
**Baseline Linux:** GBW Linux 5.23.0, congelado e somente leitura  
**Roadmap autoritativo:** `docs/ANDROID_MIGRATION_PLAN.md`

## Checkpoint homologado

- versão: `6.0.0-alpha12`;
- versionCode: `17`;
- commit: `9631fd165c457adeb103912633e2cc59d3adb32e`;
- Android CI #108: **SUCCESS**;
- APK SHA-256: `cded2ef6bb8ea02c329434c5d58550c9c4cfd9b3b18c8b42cb4bf5f1dff76ae7`.

## Homologação física alpha12

Backup Android/Google Drive aprovado no cenário principal:
- backup automático executou sozinho;
- estrutura foi criada automaticamente;
- projeto foi organizado por banda/música;
- categorias ficaram separadas e legíveis;
- sincronização funcionou corretamente.

Vídeo `132674.mp4` revelou pequenos problemas de sessão/UX:
- banda/música duplicada no card;
- mensagem “Projeto fechado” permanece após reabrir;
- stems permanecem visíveis sem projeto aberto;
- parte do diagnóstico ainda está técnica demais.

Esses pontos formam o próximo gate **R1 / alpha13**.

## Decisões de produto de 2026-09-18

### Android-first backup

Não é mais requisito:
- backup Linux → Android;
- backup Android → Linux;
- projeto cross-platform;
- round-trip de manifests entre plataformas.

O backup SAF/Google Drive v2 Android é o sistema oficial do produto móvel.

### Original-only

O GBW Android passa a trabalhar sempre no tom original da fonte.

Devem ser removidos:
- Afinação & Pitch;
- detecção de afinação;
- pitch por tuning/semitons;
- Pitch de Arquivo;
- Rubber Band R3;
- export pitched/ajustado;
- formant preservation ligada a pitch.

A pedaleira externa é responsável por qualquer pitch necessário durante o uso musical.

### Separação

Permanece exclusivamente:
- Demucs `htdemucs_6s`;
- seis stems;
- OpenBLAS default 1 thread, política interna 1/2.

BS-RoFormer/Alta Qualidade/Comparar permanecem fora do produto.

## Workflow final alvo

1. Fonte
2. Separação
3. Exportação

Gerenciamento:
- Projetos
- Logs

Aplicativo:
- Configurações
- Sistema

## Próximo gate

**R1 — Alpha13: Session & Workflow UX Hardening**

Prioridades:
1. state scoping estrito por `projectId`;
2. fechar projeto elimina source/stems/export visuais;
3. corrigir card Projetos;
4. eliminar mensagem stale;
5. completar pesquisa/agrupamento de Projetos;
6. preparar o fluxo para a remoção total de pitch em R2.

Depois:
- R2 original-only simplification;
- R3 Logs/UX final;
- R4 hardening;
- R5 RC;
- R6 homologação física final;
- R7 6.0.0.

## Invariantes atuais

- `linux/` permanece intacto;
- UUID de projeto é imutável;
- backup remoto não abre projeto;
- source/stems/export devem pertencer ao projeto ativo;
- backup mantém somente o estado atual;
- shared gain continua obrigatório no export original;
- tarefas pesadas não pertencem à Activity.
