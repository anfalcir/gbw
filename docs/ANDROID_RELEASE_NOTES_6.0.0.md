# GBW Android 6.0.0 — Release Notes (RC)

## Escopo final

GBW Android 6.0 é Android-first e original-only.

Fluxo:
Fonte → Separação Demucs → Export backing+guitar → Projeto/Backup Android

## Principais entregas

- fontes locais via SAF;
- pesquisa/ranking e aquisição online;
- separação exclusiva Demucs htdemucs_6s;
- seis stems: drums, bass, other, vocals, guitar e piano;
- processamento pesado de separação/export no processo :media;
- preparo online persistente via WorkManager, desacoplado da tela;
- foreground mediaProcessing para tarefas pesadas compatíveis, notificação, cancelamento e cleanup;
- export original em backing + guitar com shared gain;
- FLAC 24-bit, WAV 24-bit e WAV float32;
- projetos com UUID imutável;
- sessão estritamente vinculada ao projeto ativo;
- busca/agrupamento de projetos e duplicação com UUID novo;
- histórico persistente por tarefa/projeto dentro de Sistema;
- Sistema com diagnóstico avançado recolhível;
- backup Android via SAF/Google Drive, manual e automático;
- deduplicação, conflito explícito, restore Android e coalescência;
- preflight de espaço para fonte, separação e export;
- gates de arquitetura Demucs-only/original-only e hardening em CI.

## Removido do produto Android

Não fazem parte de 6.0:
- afinação/detecção de afinação;
- alteração de tom por semitons;
- processamento de formantes;
- ferramenta de alteração de arquivo;
- Rubber Band;
- BS-RoFormer;
- modos Alta Qualidade/Comparar;
- export ajustado;
- interoperabilidade de projeto/backup Linux↔Android.

## Migração

Projetos Android alpha antigos:
- preservam identidade, fonte e separação quando válidas;
- são normalizados para schema v2;
- export antigo incompatível é invalidado e pode ser regenerado no fluxo original-only;
- artifacts obsoletos deixam de pertencer ao estado atual e são elegíveis a cleanup.

## RC3

O RC3 corrige:
- pesquisa online cancelada ao mudar de tela;
- falha global causada por um único vídeo YouTube indisponível;
- resultados Apple Music sem utilidade para aquisição;
- preparo de fonte bloqueado por quota Android de foreground dataSync;
- mensagens de erro distantes da ação;
- reabertura automática do último projeto após encerrar o app;
- textos técnicos excessivos no fluxo principal;
- Logs como página separada.

O código RC3 pode ser homologado com a assinatura pública de teste.

A release final é destinada exclusivamente ao uso pessoal do proprietário. Antes de qualquer
distribuição futura a terceiros, requisitos de licenciamento/publicação devem ser reavaliados.
A campanha física R6 permanece obrigatória antes de gerar o APK final de produção.
