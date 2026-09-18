# GBW Android — Plano Mestre de Migração

Baseline congelado: GBW Linux 5.23.0
Branch Android: dev/android-6.0

## Princípios
1. Nunca modificar linux/ durante o trabalho Android.
2. Android nativo Kotlin/Compose + NDK/JNI.
3. Processamento pesado fora da Activity.
4. Separação Android exclusivamente Demucs htdemucs_6s.
5. Artefatos duráveis entram no project root somente após validação.
6. projectId UUID é identidade canônica e imutável.
7. Interoperabilidade com GuitarLab é por arquivos/metadados, não por dependência de código.

## Roadmap
M0–M6: shell, domínio, SAF/inspeção, background, Rubber Band R3, Demucs-only e otimização 1-thread — concluídos.

M7 — Workflow completo: IMPLEMENTADO no alpha11.
Fonte gerenciada → Separação → Afinação/Pitch → Exportação.

M8 — Exportação final/shared gain: IMPLEMENTADO no alpha11.
Par backing+guitar, target -1 dBFS na recombinação, um único ganho compartilhado, WAV float32/WAV24/FLAC24.

M9 — Projetos/backup/restore: IMPLEMENTADO no alpha11.
UUID, project.json versionado, managed source, migração alpha, SAF backup, revisionId forte, incremental reuse, no-history retention, WorkManager coalescido, scan/reconcile/conflict.

M10 — Hardening: DIGITAL PASS depende da CI do candidato final; gates físicos ficam restritos a DocumentsProvider real, rede/YouTube e audição.

M11 — Release engineering: homologação alpha11, depois RC/produção.
M12 — 6.0.0 final: homologação final e release.

## Aquisição online
O ranking continua desacoplado da aquisição. Após a homologação física encontrar candidatos corretamente mas registrar HTTP 403 no download YouTube, o alpha11 adiciona atualização do yt-dlp no runtime, re-inspeção no momento da aquisição e fallbacks isolados de player_client para evitar reutilizar uma rota/URL de mídia inválida.