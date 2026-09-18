# GBW Android — Plano Mestre de Migração

**Baseline congelado:** GBW Linux 5.23.0  
**Plataforma-alvo:** Android 6.x  
**Branch:** `dev/android-6.0`

## Princípios

1. `linux/` permanece imutável durante a migração Android.
2. Android é uma reimplementação nativa Kotlin/Compose + NDK/JNI quando necessário.
3. Processamento pesado não pertence à Activity.
4. Qualidade e estabilidade têm prioridade sobre velocidade.
5. Toda otimização precisa de A/B reproduzível.
6. No Android, Separação usa exclusivamente Demucs `htdemucs_6s`.
7. Modelos grandes permanecem fora do APK e são validados antes do uso.
8. Projetos/backups devem ser interoperáveis com o Linux no que for comum às plataformas.

## Arquitetura

```text
UI Compose
→ domínio/workflow
→ Foreground Service :media
→ FFmpeg / Rubber Band / Demucs
→ estado persistido
→ SAF
```

## Macroetapas

### M0 — Baseline e CI
Concluído.

### M1 — Shell, domínio e navegação
Concluído digitalmente e validado fisicamente nas telas principais.

### M2 — Fonte / SAF / inspeção
Concluído digitalmente; fluxos reais já usados em homologação.

### M3 — Background e lifecycle
Implementado com processo dedicado, estado persistido, cancelamento e recuperação. Continuar regressão física em execuções longas.

### M4 — Pitch de Arquivo
Implementado com Rubber Band R3. Homologação auditiva/física final ainda necessária antes do RC.

### M5 — Separação Demucs
Implementação e áudio real aprovados. Alpha9.1 é baseline físico atual.

### M6 — Performance e UX da Separação
Em andamento.
- alpha8: 2165 s / 2180 MiB;
- alpha9.1 2t: 1977 s / 1714 MiB, áudio aprovado;
- alpha9.2-4t: candidato A/B, CI #85 verde.
Concluir escolha 2t vs 4t antes de alterar chunking.

### M7 — Workflow completo
Fonte → Separação → Afinação/Pitch → Exportação. Pendente de consolidação ponta a ponta.

### M8 — Exportação final / shared gain
Pendente.

### M9 — Projetos / backup / restore
Pendente.

### M10 — Hardening
Stress, lifecycle, pouco espaço, cancelamento, arquivos inválidos, recuperação e long runs.

### M11 — Release engineering
Licenças, chave de produção privada, release build, documentação e RC.

### M12 — GBW Android 6.0.0
Homologação física final e release.

## Gate atual

Instalar `6.0.0-alpha9.2-4t` sobre alpha9.1 e repetir a mesma música. Avaliar lado a lado tempo, PSS, mediana, máximo, térmico e áudio. Manter 4 threads apenas se superar o baseline sem regressões relevantes.
