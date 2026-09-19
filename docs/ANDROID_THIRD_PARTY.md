# GBW Android 6.0 — Third-Party / License Audit

Data da auditoria: 2026-09-19

Este arquivo é um inventário técnico de release. Não substitui aconselhamento jurídico.

## Estado de compliance

**Release blocker:** a distribuição pública/produção ainda não está liberada.

Motivos:
1. o repositório não possui LICENSE na raiz;
2. io.github.junkfood02.youtubedl-android:library:0.18.1 é publicado pelo upstream como GPL-3.0;
3. o modelo htdemucs_6s usado pelo produto vem de pesos pré-treinados cujo licenciamento é ambíguo entre fontes. O mirror exato usado pelo GBW é rotulado como MIT no Hugging Face atual, mas aponta para pesos originados do Demucs/Meta; o maintainer original registrou em facebookresearch/demucs#327 que os pesos pré-treinados não eram cobertos pela licença MIT do código e eram fornecidos para fins científicos.

Antes de distribuição pública/comercial, é obrigatório:
- escolher e registrar uma licença do projeto compatível com as dependências efetivamente distribuídas;
- confirmar o direito de uso/distribuição do checkpoint htdemucs_6s ou substituir por pesos com licença inequívoca adequada ao objetivo;
- manter os textos/avisos exigidos pelas bibliotecas distribuídas.

## Inventário direto

| Componente | Versão/revisão | Uso | Licença upstream / observação |
|---|---|---|---|
| AndroidX / Jetpack Compose / WorkManager / Lifecycle | versões pinadas em Gradle | UI, lifecycle, background | Apache-2.0 |
| kotlinx-coroutines-android | 1.10.2 | concorrência/cancelamento | Apache-2.0 |
| youtubedl-android | 0.18.1 | pesquisa/aquisição online via yt-dlp | GPL-3.0 upstream |
| FFmpegKit maintained audio | 8.1.7 | probe/conversão/encode de áudio | variante audio declarada LGPL-3.0 pelo maintainer |
| smart-exception-java/common | 0.2.1 | runtime FFmpegKit | BSD-3-Clause |
| demucs.cpp | f1206e9adeea103aef4a636b9e62297cf1f8e34e | inferência Demucs nativa | MIT |
| Eigen | dd8c71e62852b2fe429edb6682ac91fd1c578a26 | álgebra linear | primariamente MPL-2.0, com arquivos compatíveis adicionais |
| OpenBLAS | 0.3.34 / e0166008be8e466242aa76b2ff75ce3f0fbf574a | BLAS nativo | BSD-3-Clause |
| htdemucs_6s GGML | 5f5daffffcf06ad7b27a7285da327e18ea62068a | checkpoint baixado em runtime | ver seção específica abaixo |

## Checkpoint Demucs

Contrato do GBW:
- arquivo: ggml-model-htdemucs-6s-f16.bin;
- bytes: 54,855,129;
- SHA-256: 09704f4ceae204e56e77d5eefd6ac71d7275be81fd507e6913371d59abcee856;
- download: Retrobear/demucs.cpp, revisão 5f5daffffcf06ad7b27a7285da327e18ea62068a;
- o modelo não é empacotado no APK; é baixado e validado por tamanho + SHA-256.

Fontes de auditoria:
- mirror utilizado: https://huggingface.co/datasets/Retrobear/demucs.cpp
- revisão pinada: https://huggingface.co/datasets/Retrobear/demucs.cpp/tree/5f5daffffcf06ad7b27a7285da327e18ea62068a
- origem declarada pelo mirror: pesos oficiais de facebookresearch/demucs;
- discussão upstream sobre pesos: https://github.com/facebookresearch/demucs/issues/327

A etiqueta de licença do mirror não é tratada pelo GBW como prova suficiente para produção porque a origem dos pesos e a declaração do maintainer original entram em tensão. O produto pode continuar sendo testado/homologado, mas o gate de produção permanece aberto até decisão explícita sobre esse checkpoint.

## FFmpegKit

A variante atual é:
dev.ffmpegkit-maintained:ffmpeg-kit-audio:8.1.7

O upstream mantido declara a variante audio como LGPL-3.0 e inclui avisos de terceiros. A auditoria do APK Alpha16 confirmou a presença de arquivos de licença em res/raw/, incluindo a licença principal e licenças de codecs.

Fonte:
https://github.com/ffmpegkit-maintained/ffmpeg

## youtubedl-android

Dependência atual:
io.github.junkfood02.youtubedl-android:library:0.18.1

O repositório upstream declara GPL-3.0:
https://github.com/yausername/youtubedl-android

Como o GBW ainda não possui licença própria na raiz, esse ponto impede encerrar o gate de distribuição sem decisão de licenciamento do projeto.

## Native notices

Detalhes nativos adicionais permanecem em:
android/app/src/main/cpp/THIRD_PARTY.md

## Política

Não:
- versionar keystore privado;
- remover avisos/licenças de bibliotecas do APK;
- trocar checkpoint/modelo sem nova auditoria de origem, SHA e licença;
- declarar R5/R7 concluído enquanto os blockers desta página estiverem abertos.
