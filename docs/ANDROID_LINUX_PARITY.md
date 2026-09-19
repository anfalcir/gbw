# GBW Android — Matriz de Paridade com Linux 5.23

Data da revisão: 2026-09-18
Baseline: `linux/` em modo somente leitura.

Esta matriz separa comportamento de produto de detalhes de implementação. O Android não deve copiar APIs do desktop; deve preservar o resultado e o modelo mental do GBW quando isso fizer sentido.

| Área | Linux 5.23 | Android alpha12 | Decisão |
|---|---|---|---|
| Estado inicial | Nenhum projeto aberto; começa em Fonte | Nenhum projeto aberto | PARIDADE |
| Projeto ativo | Cabeçalho identifica artista/música | Drawer/sidebar e top bar identificam projeto | PARIDADE |
| Fechar projeto | Salva, desmonta contexto e volta à Fonte | Limpa active pointer/source/UI e volta à Fonte sem apagar arquivos | PARIDADE |
| Fechar durante DSP | Bloqueado enquanto tarefa ativa | Botão desabilitado enquanto job de mídia está RUNNING/CANCELLING | PARIDADE |
| Nome do projeto | Artista e música normalizados com iniciais maiúsculas | `Artista - Música` automático usando a mesma normalização conceitual | PARIDADE |
| Ordenação | Artista → Música | Artista → Música | PARIDADE |
| Pesquisa | Artista/música normalizados antes da busca | Mesma normalização e projeto criado/atualizado no início da busca | PARIDADE |
| Status de tarefa | `set_busy` durante execução e `set_ready` ao terminar | Cards de job só existem enquanto ativos; conclusão substitui mensagem de início | PARIDADE |
| Separação | Implementação desktop histórica | Demucs-only htdemucs_6s por decisão explícita do projeto Android | DIVERGÊNCIA INTENCIONAL |
| Pitch | Rubber Band; projeto e ferramenta independente | Rubber Band R3; projeto e ferramenta independente | PARIDADE DE MOTOR/RESULTADO |
| Export final | backing + guitar com shared gain, opções de variante | backing + guitar com shared gain, original/ajustado | PARIDADE DO CONTRATO FINAL |
| Projetos | Abrir/fechar/listar | Abrir/fechar/listar/duplicar/excluir | PARIDADE + Android |
| Backup | Desktop filesystem/arquivo | SAF + WorkManager + provider Google Drive | IMPLEMENTAÇÃO NATIVA ANDROID |
| Organização de backup | Nomes humanos | `Projetos/Artista - Música/Fonte/Separacao - Stems/Exports/Projeto` | PARIDADE DE UX |
| Identidade de backup | Caminho/metadata desktop | UUID interno imutável + SHA-256 | HARDENING ANDROID |
| Logs | Histórico de sessão visível | Página ainda não é histórico completo de sessão | GAP NÃO BLOQUEANTE DESTA RODADA |
| Afinação detectada | Linux possui análise/detecção | Android atual prioriza configuração de pitch do projeto | DIFERENÇA FUNCIONAL CONHECIDA |

## Regras estabelecidas nesta revisão

1. Nome de pasta nunca substitui `projectId`.
2. Renomear metadados não pode reuploadar fonte/stems grandes quando os hashes são iguais.
3. Restore/sync em segundo plano nunca pode abrir um projeto por conta própria.
4. Nenhuma chamada pesada de DocumentsProvider/Drive pode executar na thread de UI.
5. Mensagem "em segundo plano" só pode existir enquanto a operação está ativa; estados terminais devem substituí-la.
6. Fechar projeto significa fechar a sessão, não excluir o projeto.
7. O backup contém somente os artefatos atualmente referenciados pelo projeto, sem histórico acidental.
