# Matriz de Paridade — Linux 5.23 ↔ Android 6.x

| Contrato | Linux 5.23 | Android | Estado |
|---|---|---|---|
| Drop B → Drop D | +3 st | +3 st | ✅ domínio + golden R3 |
| Drop D → Drop B | -3 st | -3 st | ✅ domínio + golden R3 |
| E Standard → Drop D | inválido globalmente | inválido | ✅ domínio |
| Afinações suportadas | 18 | 18 | ✅ domínio |
| Normalização de nomes | Title Case | equivalente | ✅ domínio |
| Busca case/accent insensitive | sim | contrato portado | 🟡 UI/projetos pendente |
| Separador padrão Linux | Alta qualidade | — | referência |
| Separador padrão Android | — | Rápida / Demucs | ✅ decisão |
| Ideal/Adequado/Ressalva | sim | regras portadas | ✅ domínio/UI |
| WAV 32f / 48 kHz | Ideal | Ideal | ✅ domínio |
| FLAC 24-bit | Adequado | Adequado | ✅ domínio |
| MP3/AAC lossy | Ressalva | Ressalva | ✅ domínio |
| Pitch | Rubber Band R3 | R3 v4.0.0 NDK/JNI arm64 | ✅ implementação/build/golden; 🟡 aparelho |
| Pitch offline | duas passagens | `study` + `process` | ✅ implementação |
| Formantes em vocal | preservar | `OptionFormantPreserved` | ✅ implementação; 🟡 auditivo |
| Preservar duração | sim | valida + golden ≤20 ms | ✅ digital; 🟡 aparelho |
| Preservar sample rate | sim | sem `-ar`, valida antes/depois | ✅ pipeline |
| Preservar canais | sim | sem `-ac`, valida antes/depois | ✅ pipeline + golden estéreo |
| WAV 32-bit float saída | sim | padrão | ✅ pipeline |
| WAV 24-bit / FLAC 24-bit | sim | alternativas | ✅ pipeline |
| Saída sem parcial corrompido | sim | temporário → validação → SAF | ✅ pipeline; 🟡 providers reais |
| Tarefa longa em background | desktop N/A | FGS + JobStore + wake lock + redelivery | ✅ implementação; 🟡 lock-screen real |
| Cancelamento seguro | sim | UI/notificação + coroutine/native/FFmpeg cleanup | ✅ implementação; 🟡 aparelho |
| Pouco armazenamento | erro controlado | preflight conservador de cache | ✅ digital |
| Shared gain backing/guitar | sim | mesma regra | ⏳ M8 |
| Backup de projeto | sim | interoperável | ⏳ M3/M8 |
| Projetos | manifest JSON | schema versionado | ⏳ M3 |
| Demucs 6 stems | htdemucs_6s | htdemucs_6s | ⏳ próximo gate |
| BS-RoFormer | A | Alta qualidade | ⏳ M1/M6 |

Legenda: ✅ implementado/validado no nível indicado · 🟡 depende de validação física/integração adicional · ⏳ pendente.
