# Handoff — GBW Android

Repositório: `anfalcir/gbw`  
Branch: `dev/android-6.0`

## Regras

- confirmar HEAD antes de escrever;
- não modificar `linux/`;
- Separação Android = Demucs `htdemucs_6s`;
- uma variável de performance por experimento;
- não trocar chunking antes de medir cada hipótese isoladamente.

## Checkpoint atual

`6.0.0-alpha10.1`, versionCode 14.

- commit funcional: `0636575a85f163e6b79a0f6400993484d03b6d36`
- CI #94 / run `35392234622`: **SUCCESS**
- APK: 55,617,794 bytes
- SHA-256: `cd865e34d35e61a5a27706f01a6c771dccb65dedfe8172369c2c54112cdd699a`
- assinatura de homologação: `6d60524d7817a0ef907f70922d30f129325282451049accfd221222b60ef6dd0`

## Performance física homologada

### 1 thread — promovido
- 1895 s
- 1712 MiB PSS
- 39 chunks
- mediana 48,2 s
- máximo 51,0 s
- térmico normal
- SUCCESS 100%

### 2 threads — referência anterior
- 1977 s
- 1714 MiB
- mediana 45,9 s
- máximo 87,3 s
- térmico leve
- áudio previamente aprovado

### 4 threads — rejeitado
- 2460 s
- 1698 MiB
- mediana 58,8 s
- máximo 102,8 s
- térmico leve

Decisão:
- default = 1 thread;
- política suportada = 1/2;
- JNI também rejeita 4;
- chunk concurrency = 1.

Falta apenas confirmação auditiva explícita do resultado 1t/alpha10 para fechar o gate físico de áudio dessa configuração.

## UX já consolidada

- nome amigável do arquivo SAF;
- identificadores opacos não são expostos;
- mensagem “Separação iniciada em segundo plano.” expira;
- Sistema mostra threads reais;
- timeout do FGS envia cancelamento ao Demucs corretamente.

## Pesquisa Online

Implementada e validada digitalmente:
- regras de matching/ranking do Linux 5.23 portadas;
- Bandcamp discovery provider;
- Apple Music/iTunes Search API provider;
- busca ampla YouTube/SoundCloud/Bandcamp;
- estado da pesquisa persistente em ViewModel + SavedStateHandle;
- Robusta/Máxima;
- recomendado/score/motivo;
- URL manual;
- isolamento de falha entre providers.

Detalhes: `docs/ANDROID_ONLINE_SOURCES.md`.

## Próxima ação

Homologar fisicamente o alpha10.1:
- atualização por cima;
- Pesquisa `Wolves At The Gate / Enemy`;
- rotação retrato↔paisagem sem perder os campos/resultados;
- URL manual;
- nome amigável SAF;
- BLAS 1 na tela Sistema;
- spot-check dos seis stems.

Depois, continuar M7 e conectar os stems internos à etapa Afinação/Pitch sem regressar o renderer Rubber Band.
