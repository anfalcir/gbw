# GBW Android — Pesquisa e Aquisição Online de Fontes

## Arquitetura
Pesquisa/ranking e aquisição/preparação são responsabilidades separadas. O ranking permanece independente de provider; a aquisição só é oferecida quando o candidato declara suporte automático.

## Ranking
SourceSearchRules porta o contrato Linux 5.23: normalização, gate de título, confirmação de artista, penalidades para cover/karaoke/reaction/tutorial/slowed/sped/nightcore/8D/live/remix, preview/truncamento, consenso de duração, deduplicação e ranking determinístico.

Providers de catálogo/discovery incluem Bandcamp e Apple Music/iTunes; Apple Music permanece catálogo e não é tratado como fonte baixável.

## Aquisição
YouTube/SoundCloud e URL manual compatível usam yt-dlp Android. A mídia é re-inspecionada no momento da aquisição; o formatId obtido durante ranking é apenas advisory e não é confiado cegamente depois.

## Homologação 2026-09-18 — defeito 403
O vídeo físico confirmou que pesquisa e ranking funcionaram, mas o candidato YouTube falhou na preparação com:
ERROR: unable to download video data: HTTP Error 403: Forbidden

O alpha11 corrige o caminho de aquisição:
- tenta atualizar o yt-dlp pelo UpdateChannel.NIGHTLY suportado pela biblioteca Android;
- limpa o diretório de tentativa antes de retry;
- usa retries e fragment-retries limitados, sem loop infinito;
- não reutiliza uma URL de mídia expirada como prova de formato válido;
- para YouTube usa tentativas isoladas, não uma cadeia multi-client: formato fresco/default; android_vr após atualização; web_embedded isolado;
- só erros classificados como transitórios/403/PO-token/SABR/signature/formato indisponível acionam fallback;
- cancelamento encerra todos os processIds das tentativas.

## Estado de UI
Artista, música, profundidade, URL manual e resultados permanecem no ViewModel/SavedStateHandle durante mudança de configuração.

## Testes
Cobertura inclui regras/ranking, parsers, fallback de providers, estado, links, isolamento de falhas e a nova matriz de download: formato fresco, fallbacks isolados, classificação de HTTP 403 e caminho não-YouTube de tentativa única.