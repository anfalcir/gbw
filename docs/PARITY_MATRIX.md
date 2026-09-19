# Matriz de Contrato — Linux 5.23 ↔ Android 6.0

Source of truth: docs/ANDROID_MIGRATION_PLAN.md

A matriz registra referência útil do Linux e divergências deliberadas do Android.

| Área | Linux 5.23 | Android 6.0 | Estado |
|---|---|---|---|
| Normalização artista/música | referência | Artista - Música | ✅ |
| Busca case/accent insensitive | sim | sim | ✅ |
| Pesquisa/ranking de fonte | referência | portado | ✅ |
| Fonte local/URL | sim | sim | ✅ |
| Separação | caminhos históricos | somente Demucs htdemucs_6s | ✅ divergência intencional |
| Seis stems | sim | drums/bass/other/vocals/guitar/piano | ✅ |
| Background | desktop n/a | Foreground Service :media | ✅ |
| Cancelamento/cleanup | referência | cooperativo + staging | ✅ digital / ⏳ spot físico |
| Alteração de tom/afinação | existe no desktop | não existe | 🚫 fora de escopo |
| Export original | sim | backing + guitar | ✅ |
| Shared gain | referência | pico combinado + ganho comum | ✅ |
| Projetos | manifest desktop | UUID + project.json schema 2 | ✅ arquitetura própria |
| Fechar projeto | desmonta contexto | mesmo resultado | ✅ |
| Busca/agrupamento Projetos | sim | sim | ✅ |
| Logs | sessão | histórico persistente por job/projeto | ✅ |
| Backup | desktop | SAF/Google Drive v2 | ✅ Android-first |
| Restore | desktop | Android ↔ Android | ✅ digital / ⏳ campanha final |
| Backup Linux↔Android | formatos distintos | não requerido | 🚫 fora de escopo |
| Storage baixo | n/a | preflight Fonte/Demucs/Export | ✅ |
| Acessibilidade/lifecycle | desktop n/a | hardening digital + validação física | ✅ digital / ⏳ R6 |
| Release signing | n/a | private key de produção | ⏳ R5 |
| Compliance de distribuição | n/a | auditoria registrada | ⏳ R5 |

Legenda:
- ✅ concluído;
- ⏳ pendente de gate indicado;
- 🚫 fora de escopo por decisão de produto.

Itens 🚫 não voltam ao roadmap sem decisão explícita.
