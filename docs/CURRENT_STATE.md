# GBW — Estado Atual

Data: 2026-09-18
Repository: anfalcir/gbw
Branch Android: dev/android-6.0
Baseline Linux: linux/ = GBW Linux 5.23.0, congelado/somente leitura.

## Candidato Android
Version: 6.0.0-alpha11
versionCode: 16

O alpha11 fecha as bases de produto que faltavam depois do Demucs/pesquisa:
- repositório real de projetos com UUID imutável;
- managed source autocontida e migração idempotente dos alphas;
- stems publicados por projectId somente depois de validação;
- Afinação/Pitch vinculada ao projeto;
- export final backing + guitar com shared gain do Linux 5.23;
- WAV float32, WAV24 e FLAC24;
- export_manifest.json para interoperabilidade futura com GuitarLab;
- SAF backup manual/automático, destination probe e persistable tree URI;
- SHA-256 inventory e revisionId determinístico;
- incremental content reuse e retenção sem histórico;
- commit marker por último, direct-URI verification e settling lookup;
- WorkManager unique/coalesced;
- remote discovery/import/reconciliation/conflict;
- local-only delete tombstone;
- correção do HTTP 403 do download YouTube observado na homologação física.

## Homologação da pesquisa
O vídeo físico de 2026-09-18 confirmou que descoberta e ranking funcionaram e escolheram candidatos coerentes, mas a preparação do candidato YouTube terminou com:
ERROR: unable to download video data: HTTP Error 403: Forbidden

O alpha11 corrige a camada de aquisição sem mudar o ranking:
- re-inspeção do formato no momento do download;
- atualização yt-dlp NIGHTLY pela API suportada do youtubedl-android;
- retries limitados e diretório limpo por tentativa;
- fallback de player_client isolado: fresh/default, android_vr e web_embedded;
- classificação específica de 403/Forbidden/PO-token/SABR/signature/formato;
- cancelamento cobre todos os processIds de retry.

## Export contract
Backing = drums + bass + other + vocals + piano.
Guitar permanece separada.
O pico é medido em backing + guitar recombinados; target -1 dBFS; um único fator é aplicado igualmente aos dois arquivos. Normalização independente é proibida.

## Backup contract
projectId é a identidade local e remota. Rename preserva ID; duplicate gera novo ID.
Backup contém project metadata + managed source + seis stems + exports.
revisionId é forte/determinístico e serve para dedupe/retry/conflito, não histórico.
Somente a versão lógica corrente permanece no destino após o novo commit ser validado.

## Runtime preservado
- Demucs-only htdemucs_6s;
- OpenBLAS 0.3.34;
- default 1 thread; política suportada 1/2;
- chunk concurrency 1;
- FFmpeg;
- Rubber Band R3 4.0.0;
- ABI arm64-v8a;
- sem BS-RoFormer/PTE/ExecuTorch/PFFFT.

## Evidência digital
CI #102 / run 35403458254 passou integralmente sobre a base alpha11 antes do último hardening isolado dos clientes YouTube: Unit, Lint, assemble, assinatura, conteúdo nativo, Demucs/OpenBLAS, FFmpegKit e manifest worker.
O HEAD final deve ser promovido somente depois de repetir integralmente esses gates com o hardening/documentação consolidados.

## Gates físicos mínimos restantes
1. retestar a aquisição do candidato YouTube que antes retornava 403;
2. escolher Google Drive via SAF e confirmar permission/visibilidade;
3. confirmar rename sem duplicação e coalescência;
4. reinstalar/limpar app, apontar a mesma pasta e confirmar scan/import;
5. audição final de backing/guitar.

Documentos canônicos adicionais:
- docs/ANDROID_PROJECT_STORAGE.md
- docs/ANDROID_BACKUP_CONTRACT.md
- docs/GBW_EXPORT_CONTRACT.md
- docs/ANDROID_ONLINE_SOURCES.md