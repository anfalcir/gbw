# Matriz de Contrato — Linux 5.23 ↔ Android 6.0

**Source of truth de escopo:** `docs/ANDROID_MIGRATION_PLAN.md`

A matriz abaixo não exige paridade total. Ela registra quais comportamentos do Linux continuam sendo
referência e quais foram deliberadamente removidos do produto Android.

| Área | Linux 5.23 | Android 6.0 alvo | Estado / decisão |
|---|---|---|---|
| Normalização de nomes | referência | `Artista - Música` | ✅ manter |
| Busca case/accent insensitive | sim | sim | 🟡 completar UI Projetos |
| Pesquisa/ranking de fonte | referência | portado | ✅ |
| Fonte local/URL | sim | sim | ✅ |
| Separação | múltiplos caminhos históricos | somente Demucs `htdemucs_6s` | ✅ divergência intencional |
| Seis stems | sim | drums/bass/other/vocals/guitar/piano | ✅ |
| Background | desktop n/a | Foreground Service `:media` | ✅ |
| Cancelamento | referência | UI/notificação + cleanup | 🟡 regressão final |
| Pitch | Rubber Band | **não existe no produto final** | 🚫 fora de escopo |
| Detecção de afinação | sim | **não existe** | 🚫 fora de escopo |
| Pitch de Arquivo | sim | **remover** | 🚫 fora de escopo |
| Export original | sim | backing + guitar | ✅ |
| Shared gain | referência | mesma regra | ✅ implementação; 🟡 regressão final |
| Export pitched | sim | **não existe** | 🚫 fora de escopo |
| Projetos | manifest desktop | UUID + project.json Android | ✅ arquitetura própria |
| Fechar projeto | desmonta contexto | mesmo resultado | 🟡 corrigir vazamentos alpha12 |
| Busca/agrupamento Projetos | sim | alvo equivalente | 🟡 R1/R3 |
| Logs | histórico de sessão | alvo equivalente | ⏳ R3 |
| Backup | ZIP/desktop | SAF/Google Drive v2 | ✅ Android-first |
| Restore | desktop | Android ↔ Android | ✅ base; 🟡 stress final |
| Backup Linux↔Android | n/a/formatos distintos | **não requerido** | 🚫 fora de escopo |
| Release signing | n/a | chave privada de produção | ⏳ R5 |
| Acessibilidade/lifecycle | desktop n/a | requisito Android | ⏳ R4 |

Legenda:
- ✅ implementado/decidido;
- 🟡 implementado ou parcialmente pronto, ainda requer fechamento;
- ⏳ pendente;
- 🚫 deliberadamente fora de escopo.

## Regra

Diferenças marcadas como 🚫 **não podem ser reintroduzidas como pendências** sem nova decisão explícita
do usuário.
