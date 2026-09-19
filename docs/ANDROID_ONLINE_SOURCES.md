# GBW Android — Pesquisa e Aquisição Online de Fontes

## Arquitetura
Pesquisa/ranking e aquisição/preparação são responsabilidades separadas. A lista principal mostra somente fontes que o GBW consegue preparar automaticamente.

## Providers ativos
A pesquisa automática usa Bandcamp, SoundCloud e YouTube. Apple Music/iTunes foi removido do fluxo porque fornece metadados/catálogo, mas não uma fonte que o GBW possa adquirir e preparar.

## Resiliência da pesquisa
Resultados individuais podem desaparecer, ficar privados, sofrer bloqueio regional ou deixar de estar disponíveis entre a pesquisa e a inspeção. Essas falhas são tratadas por candidato: um vídeo indisponível é descartado e os demais resultados continuam sendo avaliados. A pesquisa só falha como um todo quando nenhum provider consegue executar.

## Aquisição
YouTube/SoundCloud e URL manual compatível usam yt-dlp Android. A mídia é re-inspecionada no momento da aquisição; o formato observado durante ranking é apenas uma pista e não é confiado cegamente depois.

A preparação online é persistida pelo WorkManager e não usa um foreground service `dataSync`. Isso evita que a ação do usuário seja recusada por quota acumulada de foreground services em Android recente. Mudança de tela não cancela a pesquisa nem a preparação.

## Falhas transitórias
O caminho de aquisição mantém retries limitados e fallbacks isolados para problemas como HTTP 403, formato expirado, assinatura/PO-token e rotas de cliente incompatíveis. Não há loop infinito.

## Estado de UI
Artista, música, profundidade, URL manual, resultados e seleção pertencem ao ViewModel e permanecem vivos ao navegar entre telas. Ao trocar de projeto, o estado de resultados é invalidado para impedir vazamento entre projetos.

Erros e alertas são apresentados junto à ação relacionada e também por mensagem temporária para facilitar a percepção.

## Testes
A cobertura inclui ranking, isolamento de providers, descarte de candidato indisponível sem abortar a busca, filtro de fontes não baixáveis, persistência de estado, scoping por projeto, fallback de download e contrato WorkManager sem foreground `dataSync`.
