# Handoff — GBW Android

Repository: anfalcir/gbw
Branch: dev/android-6.0
Baseline Linux: linux/ = GBW Linux 5.23.0, read-only.

## Regras
- confirmar HEAD remoto antes de escrever;
- não modificar linux/;
- não modificar anfalcir/guitarlab quando usado como referência;
- preservar Demucs-only htdemucs_6s, OpenBLAS default 1 thread, FFmpeg e Rubber Band R3;
- não reintroduzir BS-RoFormer/PTE/ExecuTorch/PFFFT.

## Arquitetura alpha11
- project/: UUID/schema/repository/managed source/migração/snapshot;
- export/: backing+guitar/shared gain/export manifest;
- backup/: SAF store, revision identity, coordinator, WorkManager e reconciliation;
- source/: pesquisa/ranking e aquisição yt-dlp endurecida contra HTTP 403.

## Invariantes
- rename mantém projectId;
- duplicate cria projectId novo;
- SAF local é copiado para source/ gerenciado;
- jobs e staging não entram no backup;
- backup inclui fonte + stems + exports;
- backup não mantém histórico de revisões;
- commit remoto só é válido após size/SHA-256 + manifest + commit marker;
- conflito local/Drive nunca sobrescreve silenciosamente;
- backing contém exatamente cinco stems sem guitar;
- shared gain é idêntico para backing e guitar;
- pitch preserva duração/alinhamento.

## Download YouTube
O vídeo de homologação confirmou ranking correto e falha de aquisição com: HTTP Error 403: Forbidden.
O alpha11 trata isso com formato re-inspecionado na aquisição, atualização NIGHTLY suportada pelo youtubedl-android, retries limitados e clientes isolados: tentativa fresca, android_vr, depois web_embedded. O cancelamento destrói todos os processIds de retry.

## Gates físicos restantes
1. retestar o download do candidato YouTube em rede real;
2. selecionar uma pasta Google Drive via SAF e confirmar permissão/visibilidade;
3. rename sem duplicação remota e coalescência de pequenas mudanças;
4. reinstalação + seleção da mesma pasta + descoberta/import;
5. audição final do par backing/guitar.