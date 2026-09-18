# GBW Android — Plano Mestre de Migração

Baseline congelado: GBW Linux 5.23.0  
Branch Android: `dev/android-6.0`

## Princípios

1. Não modificar `linux/`.
2. Android nativo Kotlin/Compose + NDK/JNI.
3. Processamento pesado fora da Activity.
4. Qualidade/estabilidade antes de velocidade.
5. Uma variável por benchmark.
6. Separação Android somente com Demucs `htdemucs_6s`.
7. Modelos grandes fora do APK e validados.
8. Interoperabilidade no escopo comum.

## Roadmap

### M0 — Baseline e CI
Concluído.

### M1 — Shell/domínio/navegação
Concluído.

### M2 — Fonte/SAF/inspeção
Implementação local concluída.

Pesquisa online implementada:
- ranking Linux 5.23 portado;
- UI Artista/Música;
- Robusta/Máxima;
- Bandcamp discovery;
- URL manual;
- isolamento de falha;
- aquisição automática de mídia permanece separada.

Gate restante: homologação física da pesquisa no alpha10.

### M3 — Background/lifecycle
Implementado; regressão física contínua.

### M4 — Pitch de Arquivo
Implementado com Rubber Band R3; homologação auditiva final antes do RC.

### M5 — Separação Demucs
Concluído.

### M6 — Performance/UX
**Performance de threads concluída.**

Resultados:
- alpha8: 2165 s / 2180 MiB;
- 2t: 1977 s / 1714 MiB;
- 4t: 2460 s / 1698 MiB — rejeitado;
- **1t: 1895 s / 1712 MiB — promovido**.

Alpha10 consolida 1t default e política 1/2.

Restante M6:
- spot-check auditivo explícito no alpha10;
- homologar UX nova;
- só então considerar uma nova classe de otimização como redução adicional de cópias JNI/native output, isoladamente.

### M7 — Workflow completo
Próxima macroetapa:
Fonte → Separação → Afinação/Pitch → Exportação.

A primeira implementação deve conectar stems privados ao renderer de Pitch preservando o contrato Rubber Band e sem depender de SAF para arquivos internos.

### M8 — Exportação final/shared gain
Pendente.

### M9 — Projetos/backup/restore
Pendente.

### M10 — Hardening
Stress, lifecycle, armazenamento, cancelamento, arquivos inválidos e long runs.

### M11 — Release engineering
Licenças, chave privada de produção, release build e RC.

### M12 — 6.0.0 final
Homologação final e release.
