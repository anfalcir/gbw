# GBW Android — Backup SAF Contract v2

## Objetivo

Backup legível para o usuário, incremental e restaurável, sem expor UUIDs/hashes como estrutura principal de navegação.

## Destino

O usuário escolhe uma pasta pelo seletor nativo `ACTION_OPEN_DOCUMENT_TREE`. O app persiste a permissão SAF. A interface consulta `COLUMN_DISPLAY_NAME` e o nome do provider para mostrar algo humano, por exemplo:

`Google Drive • GBW`

A URI `content://...` nunca é usada como rótulo de interface.

Toda operação de DocumentsProvider, scan, hash, upload e reconcile roda fora da Main thread. Ao conectar uma pasta, a UI faz somente o probe/registro e agenda a sincronização inicial no WorkManager.

## Layout v2

```
<destino selecionado>/
  GBW_ROOT.json
  Projetos/
    Wolves At The Gate - Deadbolt/
      Fonte/
        original__<sha12>.m4a
        prepared_44100_f32__<sha12>.wav
      Separacao - Stems/
        drums__<sha12>.wav
        bass__<sha12>.wav
        other__<sha12>.wav
        vocals__<sha12>.wav
        guitar__<sha12>.wav
        piano__<sha12>.wav
      Exports/
        Original/
          backing__<sha12>.flac
          guitar__<sha12>.flac
        Ajustado -3st/
          backing__<sha12>.flac
          guitar__<sha12>.flac
        export_manifest__<sha12>.json
      Projeto/
        project-id.json
        Dados/
          project__<sha12>.json
        Revisoes/
          v_<revisionId>/
            manifest.json
            commit.json
        current.json
```

O hash curto no nome do arquivo preserva identidade de conteúdo para dedupe/retry, mas o caminho continua legível.

## Identidade

- `projectId` UUID é imutável e fica em metadata.
- Pasta humana é apresentação e pode ser renomeada.
- `revisionId` é determinístico e representa o estado lógico atual.
- SHA-256 é identidade forte de cada artefato.

## Publicação

1. snapshot local imutável;
2. reuse/upload dos artefatos atuais;
3. manifest;
4. commit marker por último;
5. leitura direta e verificação;
6. current pointer;
7. somente depois limpeza da revisão/artefatos obsoletos.

## Compatibilidade

O reader continua entendendo o layout alpha11 legado:

`projects/<UUID>/files + revisions + current.json`.

No próximo upload válido do mesmo projeto, o conteúdo é publicado no layout v2 e o diretório legado só é removido depois que o commit v2 foi verificado.

## Retenção

O inventário local passou a ser referencial: entram apenas source, stems e export apontados pelo `project.json` atual. Arquivos físicos antigos que não pertencem mais ao estado lógico são ignorados e limpos depois de uma publicação segura.

## Concorrência e UX

- WorkManager unique work;
- coalescência de dirty changes;
- reconcile inicial/periodic/manual em background;
- cancelamento cooperativo durante upload/restore;
- conflito persistido até decisão explícita;
- sync remoto nunca altera o projeto ativo da interface.
