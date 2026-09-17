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
| Separador padrão Android | — | Rápida / Demucs | ✅ decisão + pipeline |
| Ideal/Adequado/Ressalva | sim | regras portadas | ✅ domínio/UI |
| WAV 32f / 48 kHz | Ideal | Ideal | ✅ domínio |
| FLAC 24-bit | Adequado | Adequado | ✅ domínio |
| MP3/AAC lossy | Ressalva | Ressalva | ✅ domínio |
| Pitch | Rubber Band R3 | R3 v4.0.0 NDK/JNI arm64 | ✅ implementação/build/golden; 🟡 aparelho |
| Pitch offline | duas passagens | `study` + `process` | ✅ implementação |
| Formantes em vocal | preservar | `OptionFormantPreserved` | ✅ implementação; 🟡 auditivo |
| Preservar duração | sim | valida + golden ≤20 ms | ✅ digital; 🟡 aparelho |
| Preservar sample rate no Pitch | sim | sem `-ar`, valida antes/depois | ✅ pipeline |
| Preservar canais no Pitch | sim | sem `-ac`, valida antes/depois | ✅ pipeline + golden estéreo |
| WAV 32-bit float saída | sim | padrão | ✅ pipeline |
| WAV 24-bit / FLAC 24-bit | sim | alternativas | ✅ pipeline |
| Saída sem parcial corrompido | sim | temporário → validação → SAF | ✅ pipeline; 🟡 providers reais |
| Tarefa longa em background | desktop N/A | FGS + JobStore + wake lock + redelivery | ✅ implementação; 🟡 lock-screen real |
| Cancelamento seguro | sim | UI/notificação + coroutine/native/FFmpeg cleanup | ✅ implementação; 🟡 aparelho |
| Pouco armazenamento | erro controlado | preflight conservador | ✅ digital |
| Demucs runtime | htdemucs_6s | `demucs.cpp` C++17/JNI arm64 | ✅ build/contrato; 🟡 aparelho |
| Demucs checkpoint | htdemucs_6s | revisão + bytes + SHA-256 + `dmc6` | ✅ gate digital |
| Demucs sample rate | 44,1 kHz | prepara 44,1 kHz estéreo | ✅ implementação; resampling obrigatório do modelo |
| Demucs 6 stems | drums/bass/other/vocals/guitar/piano | mesma ordem fixa | ✅ contrato/pipeline; 🟡 áudio real |
| Demucs chunking | engine desktop | 7,8 s window / 5,5 s core / 1,15 s contexto | ✅ unitário/estrutura; 🟡 seams reais |
| Demucs outputs | 6 stems | WAV float32 estéreo, frames alinhados | ✅ validação estrutural; 🟡 qualidade física |
| Demucs modelo fora do app | ambiente desktop | fora do APK, cache privado verificado | ✅ implementação |
| Demucs cancelamento | sim | coroutine + native + cleanup | ✅ implementação; 🟡 aparelho |
| Métricas Demucs | ambiente desktop | elapsed + pico PSS observado | ✅ instrumentado; 🟡 benchmark real |
| BS-RoFormer | Alta qualidade | Alta qualidade | ⏳ próximo gate |
| Comparar motores | ambos | Demucs + BS-RoFormer | ⏳ após BS-RoFormer |
| Shared gain backing/guitar | sim | mesma regra | ⏳ workflow/exportação |
| Backup de projeto | sim | interoperável | ⏳ M3/M8 |
| Projetos | manifest JSON | schema versionado | ⏳ M3 |

Legenda: ✅ implementado/validado no nível indicado · 🟡 depende de validação física/integração adicional · ⏳ pendente.
