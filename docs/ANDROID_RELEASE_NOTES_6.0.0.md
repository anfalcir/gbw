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
- processamento pesado no processo :media;
- Foreground Service, notificação, cancelamento e cleanup;
- export original em backing + guitar com shared gain;
- FLAC 24-bit, WAV 24-bit e WAV float32;
- projetos com UUID imutável;
- sessão estritamente vinculada ao projeto ativo;
- busca/agrupamento de projetos e duplicação com UUID novo;
- Logs persistentes por job/projeto;
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

## Estado do RC

O código RC pode ser homologado com a assinatura pública de teste.

A distribuição de produção permanece condicionada a:
- chave privada de produção;
- licença do projeto;
- fechamento da auditoria de direitos do checkpoint Demucs;
- campanha física R6.
