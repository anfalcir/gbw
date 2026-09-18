# GBW — Estado Atual

**Data:** 2026-09-18  
**Repositório:** `anfalcir/gbw`  
**Branch consolidada:** `main`  
**Branch oficial de desenvolvimento Android:** `dev/android-6.0`

## Organização

- `linux/` — distribuição operacional congelada do baseline Linux 5.23 e fonte de verdade funcional.
- `android/` — aplicação Android nativa ativa, linha 6.x.
- `docs/` — contratos, roadmap, paridade, CI e handoff.
- `.github/workflows/` — automação CI.

## Linux

- Baseline congelado: **GBW Linux 5.23.0**.
- SHA-256 do pacote de origem: `ca4e0b1b95e9f308deb9ae8bccce673a091631105cb4fbd020909f6fef64ce4a`.
- `linux/app/` preserva a distribuição completa expandida diretamente do pacote homologado.
- `linux/MANIFEST.sha256` fixa a integridade byte-a-byte da árvore preservada.
- A referência Linux permanece imutável durante a migração Android salvo decisão explícita de nova baseline.

## Android

Linha atual: **6.0.0-alpha6**.

### Domínio/UI já portados

- projeto nativo Kotlin + Jetpack Compose;
- shell escuro/imersivo para Android, com safe drawing insets e conteúdo centralizado em telas largas;
- Fonte local via SAF conectada diretamente à seleção usada pela tela de Separação;
- regras de afinação, delta global, bloqueios de conversão e normalização da v5.23;
- **Rápida / Demucs** como separação padrão Android;
- Alta qualidade / BS-RoFormer preservada como opção futura;
- estados Ideal / Adequado / Ressalva do Pitch de Arquivo;
- inspetor WAV nativo + inspeção complementar via FFmpeg;
- UI responsiva do Pitch de Arquivo e da Separação via SAF.

### Rubber Band R3 / Pitch de Arquivo — gate digital concluído

- Rubber Band Library `4.0.0` via NDK/JNI;
- source pin `1d95888bec3ae0a17c0c4af791810d5a63f6bc35`;
- ABI inicial `arm64-v8a`;
- R3/Finer, offline em duas passagens (`study` → `process`);
- PCM float32 em blocos, preservando canais;
- pitch positivo/negativo, time ratio `1.0` e formant preserved para Vocal;
- cancelamento cooperativo e cleanup;
- golden host sintético para +3/-3 semitons em estéreo;
- pipeline SAF → FFmpeg → R3 → validação → WAV32f/WAV24/FLAC24 → SAF final;
- preflight de armazenamento, Foreground Service, JobStore, wake lock limitado e redelivery seguro.

### Demucs `htdemucs_6s` — implementação digital concluída

Runtime Android:

- `demucs.cpp` C++17 integrado via NDK/JNI;
- source pin: `f1206e9adeea103aef4a636b9e62297cf1f8e34e`;
- Eigen pin: `dd8c71e62852b2fe429edb6682ac91fd1c578a26`;
- ABI inicial: `arm64-v8a`;
- biblioteca empacotada: `libgbw_demucs.so`;
- JNI rejeita modelo de quatro fontes e exige tensor de seis stems;
- identidade nativa registra runtime/modelo/sample-rate/canais/stems.

Checkpoint externo:

- contrato: `htdemucs_6s`;
- arquivo: `ggml-model-htdemucs-6s-f16.bin`;
- fonte: dataset `Retrobear/demucs.cpp` no Hugging Face;
- revisão imutável: `5f5daffffcf06ad7b27a7285da327e18ea62068a`;
- tamanho: `54,855,129` bytes;
- SHA-256: `09704f4ceae204e56e77d5eefd6ac71d7275be81fd507e6913371d59abcee856`;
- modelo permanece fora do APK;
- download usa `.part`, valida tamanho + SHA-256 e só então promove atomicamente para o cache privado;
- CI baixa/reutiliza o checkpoint apenas após revalidar integridade e exige magic `dmc6` + nomes de tensores de arquitetura esperados.

Pipeline Rápida:

```text
SAF input
→ FFmpeg prepara WAV float32 estéreo 44,1 kHz
→ modelo htdemucs_6s validado/carregado
→ janelas nativas de 343.980 frames (7,8 s)
→ core de 242.550 frames (5,5 s)
→ contexto de 50.715 frames (1,15 s) em cada lado
→ inferência 6 stems
→ crop do core
→ WAV float32 estéreo por stem
→ valida sample rate/canais/frames
→ mantém somente outputs completos
```

Ordem fixa dos seis stems:

1. `drums`
2. `bass`
3. `other`
4. `vocals`
5. `guitar`
6. `piano`

A implementação mede `elapsedMillis` e pico observado de PSS durante a execução, preserva progresso persistido e remove saída parcial em falha/cancelamento.

### UI de Separação

- seleção do áudio via SAF;
- **Rápida — Demucs `htdemucs_6s`** executa o pipeline real;
- primeiro uso informa que o modelo externo será baixado/verificado;
- progresso e estado vêm do mesmo Foreground Service dos jobs longos;
- cancelamento pela UI/notificação;
- **Alta qualidade / BS-RoFormer-SW** executa o pipeline real quando o PTE autoritativo está instalado;
- o PTE pode ser importado via SAF e é revalidado por bytes + SHA-256;
- Comparar permanece bloqueado até o gate runtime arm64 da Alta qualidade.

## BS-RoFormer-SW — gate digital de produção

O workflow **BS-RoFormer Production PTE #3** (run `35289168951`) terminou **SUCCESS** e fixou o artifact autoritativo:

- arquivo: `GBW-BS-RoFormer-SW-executorch-1.3.1-T1151.pte`;
- bytes: `700,284,960`;
- SHA-256: `8c3cc68404b7fadb2a41ec332b0493290d956f9490dc5c21c5120ee596807182`;
- backend: XNNPACK;
- ExecuTorch: `1.3.1`;
- torch de export: `2.12.1+cpu`;
- input: `[1,2,1025,1151,2]`;
- output: `[1,6,2050,1151,2]`;
- parâmetros: `174,656,564`.

Pipeline Android: SAF → float32 stereo 44,1 kHz → PTE privado validado → ExecuTorch mmap → reflect/chunking 588800 → PFFFT STFT → XNNPACK masks → PFFFT ISTFT → overlap-add streaming → 6 WAVs float32 stereo.

O manager implementa `.part`, Content-Length quando disponível, limite de bytes, cancelamento cooperativo, fsync, SHA-256, promoção atômica e cleanup. O PTE permanece fora do APK.

**Licença:** o código `bs-roformer-infer` é MIT, mas o model card atual dos pesos usados declara a licença do checkpoint como desconhecida. Por isso a URL pública automática do PTE fica deliberadamente vazia e o projeto não republica o PTE derivado como Release enquanto direitos de redistribuição não forem estabelecidos. Para desenvolvimento/homologação, o artifact exato da CI pode ser importado via SAF.

## Background/lifecycle

- homologação física do alpha2 confirmou tema escuro, modo imersivo e ausência das sobreposições superior/inferior do Android;
- o alpha2 isolou o self-test como `InvalidForegroundServiceTypeException`: em Android pré-15 o serviço era promovido com tipo `none`, proibido para targetSdk 36;
- a homologação física do alpha3 confirmou que o mesmo M4A deixou de causar crash e passou a ser classificado com ressalva;
- o alpha3 ainda apresentou `InvalidForegroundServiceTypeException` tanto na Separação Rápida quanto no self-test;
- o alpha4 padroniza o serviço em `dataSync` para API 29+, mantendo API 28 no caminho legado; `dataSync` cobre o processamento local de arquivos do GBW;
- seleção não-WAV, incluindo M4A/AAC, usa `MediaExtractor` para inspeção inicial e não executa FFprobe/FFmpeg na UI; a decodificação completa permanece no pipeline controlado;
- o self-test persiste mensagem amigável e registra a exceção completa no Logcat;
- no alpha5, `MediaProcessingService` roda no processo dedicado `:media`, isolando UI de falhas nativas/pressão de memória dos motores;
- `JobStore` usa arquivo JSON atômico com lock cross-process, substituindo SharedPreferences para progresso/estado compartilhado;
- Android 11+ reconcilia mortes do worker via `ApplicationExitInfo`, incluindo crash nativo, sinal, memória, PSS/RSS e fase registrada;
- homologação física do alpha5: UI sobreviveu, self-test chegou a `SUCCESS 100%`, e a Separação Rápida isolou `crash Java/Kotlin` em `demucs:audio-prep`;
- alpha6 remove SAF direto do FFmpeg no worker: os pipelines fazem `content:// → cópia privada local → FFmpeg/FFprobe local`, com streaming, `fsync`, cleanup e execução síncrona controlada;
- Demucs e BS-RoFormer marcam `audio-stage`, `audio-ffmpeg` e `audio-validate` separadamente para diagnóstico físico;
- `ForegroundService` é proprietário das tarefas pesadas;
- Activity não é proprietária do job;
- estado/progresso persistidos em `JobStore`;
- cancelamento pela UI e notificação;
- `PARTIAL_WAKE_LOCK` limitado durante processamento;
- `START_REDELIVER_INTENT` para reinício seguro quando o Android redeliver o Intent;
- temporários são isolados por `jobId` e saídas parciais são removidas em erro/cancelamento.

## Toolchain fixado

- AGP `9.4.0`;
- Gradle `9.6.0`;
- JDK `17`;
- Kotlin `2.4.20`;
- Compose BOM `2026.08.00`;
- compileSdk `37`;
- targetSdk `36`;
- minSdk `28`;
- NDK `27.2.12479018`;
- CMake `3.22.1`.

## CI Android

A CI dispara em push/PR e mantém `workflow_dispatch` como contingência.

Gates atuais:

1. paridade/smoke de domínio;
2. golden host Rubber Band R3;
3. checkpoint real `htdemucs_6s`: cache revalidado, tamanho, SHA-256, magic `dmc6` e tensores esperados;
4. testes unitários Android, incluindo contratos de chunking/modelo;
5. Android Lint;
6. `assembleDebug` NDK/CMake arm64;
7. verificação de `libgbw_rubberband.so` e `libgbw_demucs.so` dentro do APK;
8. metadata/SHA-256 do APK incluindo pins do runtime e modelo;
9. upload do APK debug e relatórios.

Checkpoint funcional anterior de UI + Demucs:

- commit: `724a3604cf42e7a610ee8c3e19076075444ca508`;
- Android CI run `#39`: **SUCCESS**.

O gate adicional de checkpoint externo foi acrescentado em `f4089aa5c338154f07cdda15d432f4bbe30dd837`; consulte a CI do próprio SHA como evidência autoritativa.

## Licenças / distribuição

- `demucs.cpp`: MIT no source pin usado;
- Eigen: família MPL-2.0 conforme upstream;
- dataset `Retrobear/demucs.cpp`: metadata pública declara MIT e documenta a origem dos pesos convertidos;
- Rubber Band continua sendo o principal gate de licença antes de RC/distribuição pública: GPL v2-or-later ou licença comercial apropriada.

Detalhes: `android/app/src/main/cpp/THIRD_PARTY.md`.

## O que ainda NÃO está homologado

A implementação digital não equivale a homologação física completa. Permanecem para dispositivo Android arm64 real/percepção humana:

- inferência Demucs completa no aparelho alvo com música real;
- avaliação auditiva dos seis stems e continuidade nas fronteiras de chunks;
- RAM/PSS real, thermal throttling, tempo, bateria e estabilidade prolongada;
- execução com Home/outro app/tela bloqueada;
- providers SAF reais em cancelamento/falha;
- execução JNI/R3 e percepção auditiva final do Pitch de Arquivo.

Também permanecem como gates de desenvolvimento:

- execução física arm64 do BS-RoFormer-SW, incluindo mmap/XNNPACK, PSS, tempo e thermal;
- resolução da licença/redistribuição dos pesos/PTE antes de hosting público;
- modo Comparar executando os dois motores;
- workflow completo Fonte → Separação → Afinação → Exportação;
- shared gain/exportação final;
- projetos/backup/restore cross-platform;
- hardening, auditoria de licenças, release assinado e homologação final.

## Próximo gate

1. executar a Separação Rápida no alpha6 com o mesmo M4A e confirmar avanço por `audio-stage` → `audio-ffmpeg` → `audio-validate`;
2. se houver nova falha, registrar a fase exata exibida; se a preparação passar, observar `demucs:model-load` e a primeira inferência;
3. validar Home/outro app/tela bloqueada com o serviço ativo;
4. executar **BS-RoFormer-SW / Alta qualidade** em Android arm64 real com o PTE autoritativo;
5. medir PSS/RAM, tempo, thermal, bateria, estabilidade, cancelamento e qualidade/seams;
6. resolver a licença de redistribuição do checkpoint/PTE antes de habilitar URL pública;
7. implementar Comparar sem duplicar desnecessariamente preparação/I/O;
8. depois seguir Fonte/download e o workflow completo.

## Continuidade

O prompt oficial para outro chat está em `docs/ANDROID_HANDOFF_PROMPT.md`. Toda nova sessão deve confirmar HEAD remoto e CI real antes de escrever; SHAs documentados são checkpoints, não substituem a leitura do estado remoto atual.
