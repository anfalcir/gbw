# GBW Android — Plano Mestre de Migração

Baseline congelado: GBW Linux 5.23.0  
Branch Android: `dev/android-6.0`

## Princípios

1. Não modificar `linux/` durante trabalho Android.
2. Android nativo Kotlin/Compose + NDK/JNI.
3. Processamento pesado fora da Activity.
4. Qualidade/estabilidade antes de velocidade.
5. Uma variável por benchmark.
6. Separação Android somente com Demucs `htdemucs_6s`.
7. Modelos grandes fora do APK e validados.
8. Interoperabilidade Linux/Android no escopo funcional comum.

## Roadmap

### M0 — Baseline e CI
Concluído.

### M1 — Shell/domínio/navegação
Concluído.

### M2 — Fonte/SAF/inspeção
Concluído no escopo atual.

### M3 — Background/lifecycle
Implementado; manter regressão física.

### M4 — Pitch de Arquivo
Implementado; fechamento físico/auditivo antes do RC.

### M5 — Separação Demucs
Concluído e auditivamente homologado.

### M6 — Performance/UX
Em andamento.

Resultados:
- alpha8: 2165 s / 2180 MiB;
- alpha9.1 2t: 1977 s / 1714 MiB — baseline;
- alpha9.2 4t: 2460 s / 1698 MiB — rejeitado;
- alpha9.3 1t: CI #86 verde — aguardando A/B físico.

Fechar 1t vs 2t antes de alterar chunking ou outra classe de otimização.

### M7 — Workflow completo
Fonte → Separação → Afinação/Pitch → Exportação.

### M8 — Exportação final/shared gain
Pendente.

### M9 — Projetos/backup/restore
Pendente.

### M10 — Hardening
Stress, lifecycle, armazenamento, cancelamento, arquivos inválidos, long runs.

### M11 — Release engineering
Licenças, chave privada de produção, release build e RC.

### M12 — 6.0.0 final
Homologação final e release.
