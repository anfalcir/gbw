# GBW Android — Pesquisa Online de Fontes

## Escopo

A pesquisa online do Android é separada em duas responsabilidades:

1. **descoberta/ranking** de fontes;
2. **aquisição/preparação** do arquivo de áudio.

Essa separação evita acoplar o GBW a um único serviço ou método de download e preserva o contrato de qualidade mesmo quando providers mudam.

## Contrato portado do Linux 5.23

`SourceSearchRules` implementa em Kotlin:

- normalização de texto;
- gate eliminatório de título;
- confirmação de artista por título/uploader;
- penalização de versões cover, karaoke, reaction, tutorial, slowed, sped, nightcore, 8D e live quando não solicitadas;
- penalização de remix quando não solicitado;
- detecção de preview/truncamento;
- consenso de duração entre candidatos;
- deduplicação por URL;
- ranking determinístico;
- previews sempre ficam atrás de fontes completas e com score final limitado.

O ranking é independente da implementação dos providers.

## Providers atuais

### Bandcamp

`BandcampDiscoveryProvider`:
- consulta a página pública de pesquisa;
- extrai links de faixas e metadados disponíveis;
- possui fallback para mudanças simples de markup;
- não baixa nem extrai mídia;
- suporta profundidade Robusta e Máxima;
- falhas de rede são isoladas pelo coordinator.

### Apple Music / iTunes Search API

`AppleMusicDiscoveryProvider`:
- consulta o catálogo por artista+música;
- usa apenas metadados e link da faixa;
- não consome preview nem baixa mídia;
- tenta a storefront local e usa US como fallback quando necessário;
- fornece título, artista, álbum e duração para o ranking.

### Busca ampla

Quando nenhum provider direto resolve a consulta, a UI oferece links de pesquisa equivalentes para:
- YouTube;
- SoundCloud;
- Bandcamp.

Esses links são fallback de descoberta; não são tratados como arquivo de áudio interno.

## Persistência de estado

Artista, música, profundidade e URL manual são mantidos em `SourceSearchViewModel` + `SavedStateHandle`.
Os resultados permanecem no ViewModel durante mudança de configuração, evitando perda ao alternar retrato/paisagem.

## Coordinator

`SourceSearchCoordinator` executa providers independentemente.

Se um provider falhar:
- resultados dos demais continuam válidos;
- a falha vira warning;
- a pesquisa não cai inteira.

## UX atual — Fonte

A tela Fonte oferece:
- arquivo local via SAF;
- pesquisa por artista e música;
- profundidade Robusta/Máxima;
- resultados ranqueados;
- destaque do primeiro candidato seguro como recomendado;
- origem, qualidade, score e razão do ranking;
- botão **Abrir fonte**;
- URL manual validada para `http://`/`https://`;
- busca ampla por serviço quando necessário;
- estado preservado durante rotação.

A descoberta online ainda não promove um URL diretamente a áudio interno. O usuário abre a fonte no serviço e, quando possui um arquivo de áudio por um meio permitido pelo serviço, seleciona esse arquivo via SAF.

## Próximos passos

1. homologar fisicamente Bandcamp + Apple Music usando `Wolves At The Gate / Enemy`;
2. adicionar novos providers somente quando o método de descoberta for sustentável;
3. manter provider e ranking desacoplados;
4. projetar aquisição/preparação por serviço separadamente;
5. nunca degradar o fallback Arquivo local.

## Testes

Cobertura unitária inclui:
- títulos de uma palavra;
- títulos multi-palavra;
- match de artista;
- previews;
- cover/live;
- consenso de duração;
- deduplicação/limite;
- parser Bandcamp;
- parser Apple Music;
- fallback BR→US do catálogo Apple;
- persistência via SavedStateHandle;
- geração de links de busca ampla;
- limite Robusta/Máxima;
- isolamento de falha entre providers.
