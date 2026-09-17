# Matriz de Paridade — Linux 5.23 ↔ Android 6.x

| Contrato | Linux 5.23 | Android | Estado |
|---|---|---|---|
| Drop B → Drop D | +3 st | +3 st | ✅ domínio |
| Drop D → Drop B | -3 st | -3 st | ✅ domínio |
| E Standard → Drop D | inválido globalmente | inválido | ✅ domínio |
| Afinações suportadas | 18 | 18 | ✅ domínio |
| Normalização de nomes | Title Case | equivalente | ✅ domínio |
| Busca case/accent insensitive | sim | contrato portado | 🟡 UI/projetos pendente |
| Separador padrão desktop | Alta qualidade | — | referência |
| Separador padrão Android | — | Rápida / Demucs | ✅ decisão |
| Qualidade Ideal/Adequado/Ressalva | sim | regras portadas | ✅ domínio |
| WAV 32f 48 kHz | Ideal | Ideal | ✅ domínio |
| FLAC 24-bit | Adequado | Adequado | ✅ domínio |
| MP3/AAC lossy | Ressalva | Ressalva | ✅ domínio |
| Pitch R3 | Rubber Band R3 | R3 NDK/JNI | ⏳ M1 |
| Preservar duração | sim | obrigatório | ⏳ M1/M4 |
| Preservar sample rate | sim | obrigatório | ⏳ M4 |
| Shared gain backing/guitar | sim | mesma regra | ⏳ M8 |
| Backup de projeto | sim | interoperável | ⏳ M3/M8 |
| Projetos | manifest JSON | schema versionado | ⏳ M3 |
| Tarefa longa com tela bloqueada | desktop N/A | Foreground Service | 🟡 infraestrutura pronta |
| Cancelamento seguro | sim | obrigatório | 🟡 infraestrutura pronta |
| Demucs 6 stems | htdemucs_6s | htdemucs_6s | ⏳ benchmark M1/M6 |
| BS-RoFormer | A | Alta qualidade | ⏳ benchmark M1/M6 |

Legenda: ✅ implementado/validado no nível indicado · 🟡 parcial · ⏳ pendente.
