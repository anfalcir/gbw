# GBW — Estado Atual

**Data:** 2026-09-18  
**Repositório:** `anfalcir/gbw`  
**Branch Android:** `dev/android-6.0`

## Regras de preservação

- `linux/` é o baseline congelado GBW Linux 5.23.0.
- Trabalho Android não modifica `linux/`.
- Separação Android usa exclusivamente Demucs `htdemucs_6s`.
- Otimizações são medidas com uma variável por benchmark físico.

## Checkpoint consolidado — Android 6.0.0-alpha10.1

- versionCode: `14`
- commit funcional: `0636575a85f163e6b79a0f6400993484d03b6d36`
- Android CI: **#94 / run 35392234622 — SUCCESS**
- APK: `55,617,794` bytes
- SHA-256: `cd865e34d35e61a5a27706f01a6c771dccb65dedfe8172369c2c54112cdd699a`
- certificado de homologação SHA-256: `6d60524d7817a0ef907f70922d30f129325282451049accfd221222b60ef6dd0`

O alpha10.1 consolida:
- Demucs-only;
- BLAS default 1 thread;
- política interna suportada 1/2 threads;
- 4 threads removido da política Kotlin e do gate JNI;
- UX de nome amigável do arquivo SAF;
- mensagem de início de separação transitória;
- tela Sistema mostrando threads reais;
- correção de cancelamento nativo em timeout;
- Pesquisa Online de Fontes com ranking portado do Linux 5.23;
- Bandcamp discovery provider;
- Apple Music/iTunes Search API como segundo provider de catálogo;
- busca ampla por links para YouTube/SoundCloud/Bandcamp;
- estado da pesquisa persistido em ViewModel + SavedStateHandle para sobreviver a rotação;
- URL manual;
- isolamento de falha entre providers.

## Benchmark físico de threads — Samsung Galaxy Tab A11+

Música de referência: 211,9 s, seis stems, 44,1 kHz, 39 chunks.

| Candidato | Threads | Tempo | PSS | Mediana | Máximo | Térmico |
|---|---:|---:|---:|---:|---:|---|
| alpha8 | pré-OpenBLAS | 2165 s | 2180 MiB | 53,5 s | 68,4 s | leve |
| alpha9.1 | 2 | 1977 s | 1714 MiB | 45,9 s | 87,3 s | leve |
| alpha9.2 | 4 | 2460 s | 1698 MiB | 58,8 s | 102,8 s | leve |
| alpha9.3 | **1** | **1895 s** | **1712 MiB** | 48,2 s | **51,0 s** | **normal** |

### Decisão

**1 thread é o default promovido.**

Contra 2 threads:
- tempo: 1977 → 1895 s = **-82 s / -4,1%**;
- PSS: 1714 → 1712 MiB = praticamente igual;
- mediana: 45,9 → 48,2 s = +5,0%;
- máximo: 87,3 → 51,0 s = **-41,6%**;
- térmico: leve → **normal**.

Contra alpha8:
- tempo: **-12,5%**;
- PSS: **-21,5%**;
- mediana: **-9,9%**;
- máximo: **-25,4%**.

4 threads está rejeitado. O app não o aceita mais como política válida.

O teste de 1 thread terminou SUCCESS 100%, com os seis WAVs estruturais esperados. Como o usuário não registrou nesta rodada uma nova confirmação auditiva explícita dos seis stems de 1t, manter um spot-check auditivo no alpha10 como gate físico final deste bloco.

## Runtime Demucs

- modelo: `htdemucs_6s`
- checkpoint: `ggml-model-htdemucs-6s-f16.bin`
- SHA-256: `09704f4ceae204e56e77d5eefd6ac71d7275be81fd507e6913371d59abcee856`
- demucs.cpp: `f1206e9adeea103aef4a636b9e62297cf1f8e34e`
- Eigen: `dd8c71e62852b2fe429edb6682ac91fd1c578a26`
- OpenBLAS 0.3.34: `e0166008be8e466242aa76b2ff75ce3f0fbf574a`
- ABI: arm64-v8a
- BLAS default: 1
- política interna: 1/2
- chunk concurrency: 1
- window: 343.980 frames
- core: 242.550 frames
- contexto: 50.715 frames por lado

## Pesquisa Online de Fontes

Implementada no alpha10.1:
- ranking independente de provider;
- regras portadas do Linux 5.23;
- Artista + Música;
- profundidade Robusta/Máxima;
- Bandcamp discovery provider;
- Apple Music/iTunes Search API provider;
- busca ampla de fallback;
- estado preservado entre retrato/paisagem;
- recomendado + score + motivo;
- URL manual;
- falha de um provider não derruba a pesquisa.

Aquisição automática do áudio continua separada por design. A tela abre a fonte e o arquivo obtido por meio permitido pelo serviço entra no GBW via SAF.

## Próximos gates físicos

No alpha10.1:
1. instalar por cima do alpha9.3 sem limpar dados;
2. confirmar que o nome da fonte agora é amigável;
3. pesquisar `Wolves At The Gate / Enemy` e confirmar resultado Apple Music e/ou Bandcamp;
4. preencher Artista/Música, girar retrato↔paisagem e confirmar que os campos/resultados permanecem;
5. testar URL manual;
6. confirmar tela Sistema com BLAS 1 thread;
7. fazer spot-check auditivo dos seis stems já gerados ou de uma nova separação;
8. confirmar ícone de notificação.

Depois disso, avançar M7: Fonte → Separação → Afinação/Pitch → Exportação.
