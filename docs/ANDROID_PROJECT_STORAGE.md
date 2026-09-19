# GBW Android — Project Storage Contract

Status: Android 6.0.0-rc1 source candidate.

## Identidade e apresentação

Cada projeto recebe UUID RFC-4122 aleatório. projectId é canônico e imutável.

Apresentação:
- artista/banda normalizado;
- música normalizada;
- nome padrão Artista - Música.

Nome/pasta são apresentação; UUID é a identidade de persistência/backup.

## Schema

Schema atual: 2.

O writer atual não contém estado funcional de alteração de tom. O reader aceita schema 1 somente para migração segura:
- preserva identidade/fonte/separação válidas;
- normaliza o workflow;
- invalida export legado incompatível;
- regrava em schema 2 na próxima mutação;
- cleanup remove artifacts que deixaram de pertencer ao inventário atual.

Schema futuro desconhecido é rejeitado.

## Ciclo de sessão

Estados:
1. nenhum projeto aberto;
2. projeto aberto;
3. tarefa ativa ligada a um projectId.

Fechar projeto:
- não exclui dados;
- limpa active_project;
- desmonta contexto visual;
- volta à Fonte;
- não exibe fonte/stems/export de projeto anterior.

Restore/import em background nunca abre projeto automaticamente.

## Layout local

filesDir/
  projects/<projectId>/
    project.json
    source/
    stems/
    exports/
  state/
    active_project.txt
    project-locks/
    project-job-links/
    job_history.json
    current_job.json
    backup_dirty.json

Jobs/DSP usam staging/cache e só promovem artifacts validados.

## Inventário

Somente entram:
- source original/prepared atualmente referenciado;
- stems da separação atual;
- artifacts + manifest do export atual.

Isso impede histórico acidental no backup. Arquivos obsoletos são removidos somente após publicação segura do novo estado.

## Escrita

project.json usa temp + fsync + replace atômico quando suportado. Caminhos persistidos são relativos ao project root e path traversal é rejeitado.
