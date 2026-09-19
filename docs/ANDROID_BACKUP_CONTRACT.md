# GBW Android — Backup SAF Contract v2

## Objetivo

Backup legível, incremental e restaurável, sem usar UUID/hash como estrutura principal de navegação.

## Destino

O usuário escolhe uma pasta com ACTION_OPEN_DOCUMENT_TREE. O app persiste a permissão SAF e usa o provider escolhido, incluindo Google Drive quando disponível.

Operações de DocumentsProvider, scan, hash, upload e reconcile ficam fora da Main thread. A sincronização automática usa WorkManager.

## Layout v2

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
        backing__<sha12>.<ext>
        guitar__<sha12>.<ext>
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

O hash curto no nome preserva identidade de conteúdo para dedupe/retry, mantendo o caminho legível.

## Identidade

- projectId UUID é imutável;
- pasta Artista - Música é apresentação;
- revisionId representa o estado lógico atual;
- SHA-256 identifica o conteúdo de cada artifact.

## Publicação transacional

1. snapshot local imutável;
2. reuse/upload dos artifacts atuais;
3. manifest;
4. commit marker por último;
5. leitura direta e verificação;
6. current pointer;
7. somente depois limpeza de revisão/artifacts obsoletos.

## Compatibilidade

O reader mantém compatibilidade com o layout legado alpha11:
projects/<UUID>/files + revisions + current.json

No primeiro upload v2 confirmado, o layout legado do mesmo projeto pode ser removido de forma segura.

## Retenção

O inventário é referencial. Entram apenas:
- fonte atual;
- separação atual;
- export atual;
- project.json atual.

Não há versionamento histórico acidental do conteúdo de usuário.

## Concorrência / coalescência

- WorkManager unique work;
- quiet window de mudanças;
- max latency para impedir adiamento infinito;
- uma alteração durante upload permanece dirty e recebe follow-up coalescido;
- conflito persiste até decisão explícita;
- sync/restore remoto nunca abre projeto silenciosamente.

## Integridade

Restore valida:
- projectId;
- manifest/commit;
- tamanho de cada arquivo;
- SHA-256 de cada arquivo.

Staging é descartado em erro/cancelamento antes da instalação local.
