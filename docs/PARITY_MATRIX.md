# Matriz de Paridade — Linux 5.23 ↔ Android 6.x

| Contrato | Linux 5.23 | Android | Estado |
|---|---|---|---|
| Afinações suportadas | referência | 18 | ✅ domínio |
| Delta de semitons | referência | equivalente | ✅ domínio |
| Conversões inválidas | bloqueadas | bloqueadas | ✅ domínio |
| Normalização de nomes | referência | equivalente | ✅ domínio |
| Busca case/accent insensitive | sim | contrato portado | 🟡 UI/projetos |
| Separação | baseline Linux | Demucs `htdemucs_6s` | ✅ decisão + runtime + áudio real |
| Seis stems | referência | drums/bass/other/vocals/guitar/piano | ✅ |
| Separação 44,1 kHz | referência | float32 estéreo 44,1 kHz | ✅ |
| Continuidade entre chunks | referência | window/core/contexto fixos | ✅ áudio real alpha9.1 |
| Métricas de separação | n/a | elapsed/PSS/chunks/thermal | ✅ |
| Background | desktop n/a | Foreground Service `:media` | ✅ digital + uso físico |
| Cancelamento | referência | UI/notificação + cleanup | ✅ implementação; 🟡 regressão final |
| Pitch | Rubber Band R3 | Rubber Band R3 NDK/JNI | ✅ digital; 🟡 auditivo final |
| Preservar duração | sim | validação + golden | ✅ digital |
| Preservar canais | sim | validado | ✅ digital |
| Exportação parcial segura | sim | temporário → valida → SAF | ✅ implementação |
| Shared gain | referência | mesma regra | ⏳ |
| Projetos | manifest | schema versionado | ⏳ |
| Backup/restore | referência | interoperável no escopo comum | ⏳ |
| Release signing | n/a | chave privada de produção | ⏳ |

Legenda: ✅ validado no nível indicado · 🟡 requer fechamento físico/integração · ⏳ pendente.
