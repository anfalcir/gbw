# Handoff — GBW Android alpha12

Repo: `anfalcir/gbw`
Branch: `dev/android-6.0`
Baseline: `linux/` = GBW Linux 5.23.0, somente leitura.

## Regras

- confirmar HEAD antes de escrever;
- não modificar `linux/`;
- preservar Demucs-only `htdemucs_6s`;
- OpenBLAS default 1, política 1/2;
- preservar FFmpeg e Rubber Band R3;
- projeto é UUID interno + apresentação automática `Artista - Música`;
- sync/restore background nunca muda projeto ativo;
- DocumentsProvider/Drive nunca pode executar operação pesada na Main thread.

## Alpha12

Motivado pelo vídeo físico `132666.mp4`:
- remove ANR ao escolher destino;
- label legível do provider/pasta;
- reconcile inicial via WorkManager;
- layout Drive v2 humano e categorizado;
- migração layout alpha11;
- close project/estado inicial;
- normalização automática de projeto;
- ordenação por artista/música;
- lifecycle terminal de jobs;
- inventário somente do estado atual;
- cancelamento cooperativo upload/restore.

## Layout Drive v2

`Projetos/Artista - Música/Fonte`
`Projetos/Artista - Música/Separacao - Stems`
`Projetos/Artista - Música/Exports`
`Projetos/Artista - Música/Projeto`

O UUID fica em `Projeto/project-id.json`.

## Homologação física prioritária

1. conectar a mesma pasta GBW no Google Drive sem ANR;
2. confirmar label humano;
3. aguardar sync inicial e confirmar migração do projeto alpha11;
4. confirmar pastas por banda/música e categorias;
5. fechar projeto e verificar retorno a “Nenhum projeto aberto”;
6. abrir novamente em Projetos;
7. gerar export e verificar que mensagem de processamento desaparece ao terminar;
8. backup agora e verificar status terminal;
9. repetir pesquisa/download YouTube já endurecido no alpha11.
