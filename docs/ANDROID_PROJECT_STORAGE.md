# GBW Android — Project Storage Contract

Status: Android 6.0.0-alpha12 source candidate.

## Identidade e apresentação

Cada projeto recebe um UUID RFC-4122 aleatório. `projectId` é canônico e imutável.

A identidade exibida segue o comportamento musical do GBW:
- artista/banda normalizado;
- música normalizada;
- nome padrão: `Artista - Música`.

Nome e pasta são apresentação. UUID continua sendo a identidade usada por persistência/backup.

## Ciclo de sessão

Estados possíveis da interface:
1. nenhum projeto aberto;
2. projeto aberto;
3. tarefa ativa ligada ao projeto.

`Fechar projeto`:
- não exclui o projeto;
- não apaga source/stems/exports;
- limpa o active pointer;
- limpa a fonte/contexto visual da sessão;
- retorna à página Fonte;
- restaura o estado “Nenhum projeto aberto”.

Fechamento fica indisponível durante job de mídia RUNNING/CANCELLING.

Restore/import de backup em background nunca abre projeto automaticamente.

## Layout local

```
filesDir/
  projects/<projectId>/
    project.json
    source/
    stems/
    exports/
  state/
    active_project.txt
    project-locks/
    backup_dirty.json
```

Jobs/DSP usam staging/cache e só promovem artefatos validados.

## Inventário

O inventário é referencial, não uma varredura cega da pasta.

Somente entram:
- source original/prepared atualmente referenciado;
- stems da separação atual;
- artifacts + manifest do export atual.

Isso evita histórico acidental no backup. Arquivos físicos antigos são removidos somente depois da publicação segura do novo estado.

## Escrita

`project.json` usa temp + fsync + replace atômico quando suportado. Caminhos portáveis são sempre relativos ao project root.

## Migração

O migrador alpha continua idempotente. Na inicialização alpha12, projetos existentes também têm artista/música/nome normalizados quando os metadados necessários já existem.
