# GBW — Estado Atual

**Data:** 2026-09-17  
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
- Pacote de origem validado: `Guitar_Backing_Wizard_v5.23_Linux.zip`.
- SHA-256 do pacote de origem: `ca4e0b1b95e9f308deb9ae8bccce673a091631105cb4fbd020909f6fef64ce4a`.
- `linux/app/` preserva a distribuição completa expandida diretamente desse pacote.
- `linux/MANIFEST.sha256` fixa a integridade byte-a-byte de todos os arquivos preservados.
- Baixar `linux/` é suficiente para instalar, validar e executar a v5.23; o ZIP original não é necessário para uso.
- A referência Linux permanece imutável durante a migração Android salvo decisão explícita de nova baseline.

## Android

Linha atual: **6.0.0-alpha1**.

### Domínio/UI já portados

- projeto nativo Kotlin + Jetpack Compose;
- afinações, delta global, bloqueios de conversão e normalização textual da v5.23;
- **Rápida / Demucs** como separação padrão Android;
- Alta qualidade / BS-RoFormer preservada como opção;
- estados Ideal / Adequado / Ressalva do Pitch de Arquivo;
- inspetor WAV nativo + inspeção complementar via FFmpeg;
- UI responsiva do Pitch de Arquivo com SAF, escolha por afinação/semitons, Inverter, tipo de áudio e formatos de saída;
- box de orientação DAW preservado.

### Rubber Band R3 — implementação digital concluída

- Rubber Band Library `4.0.0` integrada via NDK/JNI;
- source pin exato: `1d95888bec3ae0a17c0c4af791810d5a63f6bc35`;
- ABI inicial: `arm64-v8a`;
- R3/Finer explicitamente solicitado e validado por `getEngineVersion() == 3`;
- processamento offline em duas passagens (`study` → `process`);
- PCM float32 em blocos, preservando canais;
- pitch positivo e negativo;
- time ratio `1.0`;
- preservação opcional de formantes para Vocal;
- cancelamento cooperativo e cleanup;
- teste golden host executa +3 e -3 semitons em estéreo, valida frequências esperadas e tolerância de duração;
- CI compila a mesma revisão upstream e o alvo Android NDK.

A licença e o pin estão registrados em `android/app/src/main/cpp/THIRD_PARTY.md`. Distribuição pública/RC continua condicionada à auditoria de licenças prevista no roadmap.

### Pitch de Arquivo — pipeline ponta a ponta implementado

Fluxo implementado:

```text
SAF input
→ inspeção/classificação
→ Ressalva exige aceitação explícita
→ preparação WAV float32 via FFmpeg sem -ar/-ac
→ duas passagens Rubber Band R3
→ validação de duração/sample rate/canais
→ WAV 32f direto ou encode final WAV24/FLAC24
→ FFprobe do arquivo final
→ somente então gravação no SAF de destino
→ cleanup dos temporários
```

Regras consolidadas:

- input WAV float32 compatível usa `-c:a copy` na preparação quando possível;
- demais entradas suportadas são decodificadas uma única vez para float32;
- nenhum resampling/downmix é solicitado pelo pipeline;
- sample rate e número de canais são comparados antes/depois e divergências abortam;
- duração de pitch-only usa tolerância objetiva de 20 ms;
- saída padrão WAV 32-bit float;
- alternativas WAV 24-bit e FLAC 24-bit;
- saída só é copiada para o URI final após passar nas validações;
- falha/cancelamento limpa temporários e tenta remover/truncar o destino incompleto;
- preflight estima espaço temporário e falha cedo quando o armazenamento interno é insuficiente.

### Background/lifecycle

- `ForegroundService` `mediaProcessing` é proprietário da tarefa; a Activity não é proprietária do job;
- estado/progresso persistidos em `JobStore`;
- cancelamento pela UI e pela notificação;
- `PARTIAL_WAKE_LOCK` com limite de 6 h durante processamento;
- `START_REDELIVER_INTENT` para permitir reinício seguro do pipeline após morte do processo quando o Android redeliver o Intent;
- pipeline é idempotente no diretório temporário do `jobId`: uma retomada reinicia do começo e não reaproveita render parcial;
- timeout de Foreground Service é persistido como interrupção.

### Toolchain fixado

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

## CI

A CI Android é automática em todo push/PR e mantém `workflow_dispatch` apenas como contingência.

Gates Android atuais:

1. paridade/smoke de domínio;
2. golden host Rubber Band R3 (+3/-3, estéreo, duração);
3. testes unitários Android;
4. Android Lint;
5. `assembleDebug` incluindo NDK/CMake arm64;
6. verificação da biblioteca nativa dentro do APK;
7. metadata/SHA-256;
8. upload do APK debug;
9. upload dos relatórios.

A Linux Baseline CI valida, quando o baseline/paridade muda:

1. identidade da baseline e manifesto;
2. integridade SHA-256 de toda a árvore `linux/app/`;
3. ausência de ZIP de staging na distribuição canônica;
4. permissões executáveis dos entrypoints/scripts;
5. sintaxe Bash;
6. `compileall` Python;
7. testes core e pipeline de áudio com FFmpeg;
8. testes GUI sob Xvfb;
9. self-test da aplicação.

Checkpoint consolidado do bloco R3/Pitch de Arquivo:

- commit funcional: `55f5e5e12becff31bc38028cceabca855348bf92`;
- Android CI em `main`: run `#14`, **SUCCESS**;
- artifact APK: `GBW-Android-debug-14`;
- digest do artifact: `sha256:e682578df12d3826fcc317cdf94868d52d85666effdc46cdd7a75903b53fbe76`.

Esse checkpoint é histórico do gate funcional Android; o HEAD de `main` pode avançar por documentação, preservação Linux ou housekeeping sem invalidá-lo.

## O que ainda NÃO está homologado

A implementação digital acima não equivale a homologação física completa. Permanecem para dispositivo Android real/percepção humana:

- execução JNI/R3 real no aparelho e avaliação auditiva final;
- estabilidade com Home/outro app/tela bloqueada por períodos longos;
- comportamento do provider SAF específico do aparelho/nuvem em cancelamento e falha;
- thermal throttling, consumo de bateria e RAM real;
- ergonomia e notificações no hardware alvo.

Também permanecem como gates de desenvolvimento:

- Demucs `htdemucs_6s` real no Android arm64 com seis stems;
- BS-RoFormer-SW real no Android;
- workflow completo Fonte → Separação → Afinação → Exportação;
- projetos/backup/restore cross-platform completos;
- release assinado e homologação final.

## Próximo gate

1. **Demucs `htdemucs_6s` real em Android arm64**, preservando seis stems `drums`, `bass`, `other`, `vocals`, `guitar`, `piano`;
2. definir e provar runtime, checkpoint/hash/licença, segmentação, RAM e cancelamento;
3. depois integrar BS-RoFormer-SW;
4. seguir o roadmap funcional sem regredir o Pitch de Arquivo.

## Continuidade

O prompt oficial para outro chat está em `docs/ANDROID_HANDOFF_PROMPT.md`. Toda nova sessão deve confirmar HEAD remoto e CI real antes de escrever; nenhum SHA de handoff é autoritativo se o repositório tiver avançado.
