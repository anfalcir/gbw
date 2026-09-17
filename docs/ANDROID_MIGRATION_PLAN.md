# GBW Android — Plano Mestre de Migração

**Projeto:** Guitar Backing Wizard (GBW)  
**Baseline funcional congelado:** GBW Linux v5.23.0  
**Baseline SHA-256:** `ca4e0b1b95e9f308deb9ae8bccce673a091631105cb4fbd020909f6fef64ce4a`  
**Plataforma-alvo:** Android  
**Linha de versão proposta:** Android 6.x  
**Status deste documento:** Fonte de verdade para a migração Android  
**Data de consolidação:** 2026-09-17

## 1. Objetivo

Transformar o GBW Linux v5.23 em um aplicativo Android instalável diretamente por APK, mantendo a lógica, qualidade, robustez, fluxo e resultados do baseline desktop, sem depender de Termux, Python externo, comandos de terminal ou componentes que o usuário precise instalar manualmente.

A migração deve ser feita de forma ordenada, seriada, validável e conservadora. O Android será uma reimplementação nativa compatível, tendo a v5.23 como referência funcional e de qualidade.

## 2. Princípios obrigatórios

1. A v5.23 Linux é o baseline congelado.
2. Uma única verdade funcional: regras de afinação, pitch, workflow, projetos, backups, exportação e validações devem manter comportamento equivalente.
3. Android nativo: Kotlin + Jetpack Compose; C++/NDK/JNI quando necessário para DSP.
4. Sem Termux/Python externo/FFmpeg externo/Rubber Band externo no produto final.
5. Qualidade antes de velocidade, com adequação ao hardware móvel.
6. Processamento pesado nunca pode depender da Activity permanecer viva.
7. Projetos e backups devem ser interoperáveis Linux ↔ Android quando tecnicamente possível.
8. Cada etapa crítica tem testes e gate antes de avançar.

## 3. Arquitetura-alvo

```text
GBW Android
├── UI — Kotlin + Jetpack Compose + Material 3
├── Domain — Projetos / Workflow / Afinações / Pitch / Qualidade / Backup
├── Services — Fonte / Download / Separação / Afinação / Pitch / Exportação
├── Native Audio Engine — FFmpeg / Rubber Band R3 / DSP
├── ML Engine — Demucs htdemucs_6s / BS-RoFormer-SW
└── Background Processing — Foreground Service / estado persistido / recovery
```

## 4. Estrutura visual

```text
PROCESSO
  1. Fonte
  2. Separação
  3. Afinação & Pitch
  4. Exportação

GERENCIAMENTO
  Projetos
  Logs

FERRAMENTAS
  Pitch de Arquivo

APLICATIVO
  Configurações
  Sistema
```

Tablet usa navegação lateral persistente quando houver espaço. Smartphone usa navegação adaptativa sem redução de funcionalidade.

## 5. Separação — decisão Android

Padrão Android:

> **Rápida — recomendada no Android**

Mapeamento:

```text
Rápida          → Demucs htdemucs_6s
Alta qualidade  → BS-RoFormer-SW
Comparar        → ambos
```

Regras:
- primeiro uso inicia em Rápida;
- usuário pode escolher Alta qualidade;
- preferência pode ser persistida;
- projeto/export não muda de formato por essa escolha;
- benchmark real no M1 pode reavaliar o padrão somente com evidência técnica.

## 6. Execução resiliente

**BG-01:** tarefas longas devem sobreviver a troca de app, bloqueio de tela, tela apagada, rotação e recriação da Activity.

```text
UI → Foreground Service / Worker → Engine → Estado persistido
```

Notificação deve mostrar operação, progresso, tempo e permitir Abrir/Cancelar.

Toda tarefa longa registra tipo, projeto, estado, etapa, progresso, temporários válidos, data/hora, parâmetros e último erro.

Se houver interrupção externa, preservar etapas concluídas e retomar do ponto tecnicamente possível; quando não houver checkpoint interno, reiniciar somente a menor unidade necessária.

## 7. Paridade Linux ↔ Android

Contratos obrigatórios incluem:
- Drop B → Drop D = +3;
- Drop D → Drop B = -3;
- E Standard → Drop D inválido por pitch global;
- manifest compatível/versionado;
- mesma lógica de projeto, backup, qualidade do input, shared gain, exportação e Pitch de Arquivo;
- Rubber Band R3 como referência de pitch.

## 8. Armazenamento

Usar Storage Access Framework (SAF), URI, streams/file descriptors e caminhos relativos quando aplicável. Não depender de caminhos absolutos Linux.

Objetivo: backup Linux → Android e Android → Linux.

## 9. Modelos de separação

Modelos grandes não devem inflar o APK por padrão.

```text
Instala APK → primeiro uso da Separação → verifica modelo → download → SHA-256 → armazenamento privado
```

Futuro opcional: pacote offline `GBW-Model-Pack`.

## 10. Pitch de Arquivo — primeiro fluxo DSP completo

Fluxo:

```text
Selecionar arquivo → analisar input → Ideal/Adequado/Ressalva → configurar pitch → Rubber Band R3 → validar → exportar
```

Entrada suportada conforme backend: WAV, FLAC, AIFF, MP3, M4A/AAC, OGG, Opus.

Análise: formato/container, codec, lossless/lossy, bit depth, float/integer, sample rate, canais, duração, pico/headroom, clipping e integridade.

Classificação:
- **Ideal:** segue sem confirmação.
- **Adequado:** informação discreta; sem confirmação extra.
- **Ressalva:** antes do processamento apresenta `Escolher outro arquivo` / `Continuar mesmo assim`.

Conversão:
- por afinação;
- por semitons;
- inverter;
- conversão inversa do projeto;
- validar conversões globais possíveis.

Tipo de áudio: Instrumento/Mix; Vocal com preservação de formantes.

Saída padrão: WAV 32-bit float. Alternativas: WAV 24-bit, FLAC 24-bit.

Preservar sample rate e canais por padrão, evitar resampling desnecessário e validar duração final.

## 11. Orientação DAW

Box recolhível "Como exportar do seu DAW para o GBW?":
- WAV recomendado;
- 32-bit float recomendado;
- mesma taxa de amostragem da sessão;
- 44,1 kHz → manter 44,1;
- 48 kHz → manter 48;
- sessão nova → 48 kHz é ótima escolha;
- não fazer upsampling apenas para o GBW;
- mono para guitarra mono;
- preservar estéreo quando houver efeitos estéreo;
- não normalizar desnecessariamente;
- evitar MP3/AAC quando houver lossless;
- exportar desde o mesmo ponto inicial da backing/original para preservar sincronismo.

## 12. Fonte / download

Portar pesquisa, ranking, validação de título/artista, rejeição de previews, consenso de duração, validação pós-download, preservação da fonte e preparação única em float32. A estratégia Android de download deve ser provada no M1.

## 13. Afinação & Pitch

Portar análise, candidatos, confiança, escolha persistente, original → destino, pitch automático/manual/sem pitch e confirmação.

## 14. Exportação

Preservar:

```text
exports/original/backing.*
exports/original/guitar.*
exports/pitch_+Nst/backing.*
exports/pitch_+Nst/guitar.*
```

Shared gain: medir backing+guitar em conjunto e aplicar a mesma atenuação; nunca normalizar separadamente.

Formatos: FLAC 24-bit, WAV 24-bit e WAV float32.

## 15. Projetos

Preservar lista, agrupamento por banda, ordenação A→Z banda/música, comparação case-insensitive, busca instantânea, normalização visual, exclusão segura, abrir/continuar, restore, backup e fechamento de projeto.

## 16. Normalização textual

Exemplos:

```text
WOLVES AT THE GATE → Wolves At The Gate
wolves at the gate → Wolves At The Gate
enemy              → Enemy
```

Busca/ordenação devem ser case-insensitive e accent-insensitive quando apropriado.

## 17. Roadmap seriado

### M0 — Baseline
Congelar v5.23, SHA, inventário, schemas, matriz de paridade, corpus e golden outputs.

### M1 — Riscos técnicos
- M1.A FFmpeg Android.
- M1.B Rubber Band R3 Android.
- M1.C Demucs e BS-RoFormer benchmark.
- M1.D Fonte/download.
- M1.E Background processing.

### M2 — Esqueleto Android
Kotlin, Compose, Material 3, navegação, responsividade, logging, armazenamento e jobs.

### M3 — Projetos / Persistência / Backup
Schema versionado, SAF, lista, busca, exclusão, backup, restore, Linux ↔ Android.

### M4 — Pitch de Arquivo
Paridade completa com golden tests.

### M5 — Fonte
Arquivo local, URL, pesquisa, ranking, download, preparação, cancelamento/background.

### M6 — Separação
Rápida/Demucs, Alta/BS-RoFormer, Comparar, seis stems, progresso, cancelamento, cleanup.

### M7 — Afinação & Pitch
Análise, candidatos, persistência, tuning→tuning, semitons, sem pitch, confirmação.

### M8 — Exportação e Backup Cross-Platform
Backing/guitar, pitched/original, shared gain, WAV/FLAC e backup.

### M9 — Hardening
Stress, memória, armazenamento, erros, corruptos, background, temperatura, cancelamento, lifecycle, acessibilidade e regressão.

### M10 — Release Candidate
APK assinado, hashes, certificado, matriz final, release notes e limitações.

### M11 — Homologação física
Áudio, performance, temperatura, bateria, ergonomia, lock screen/background, armazenamento e exportação real.

### M12 — GBW Android 6.0.0
Entrega APK, SHA-256, certificado, relatório, matriz e known limitations.

## 18. Versionamento

```text
Desktop baseline: GBW 5.23.0
Android: 6.0.0-alpha1 → beta → rc → 6.0.0
```

6.0.0 só é promovida com matriz de paridade fechada.

## 19. QA obrigatório

Unitários: tuning, delta, projeto, normalização, workflow, qualidade, backup, filtros, persistência.

DSP/golden: duração, alinhamento, sample rate, canais, peak/RMS, pitch, stems e exports.

Instrumentação Android: navegação, SAF, permissões, rotação, recriação, background, lock screen, cancelamento e recovery.

Stress: 3/6/10+ min, 44,1/48/96 kHz, mono/stereo, WAV/FLAC/MP3, corrompidos, pouco armazenamento/memória e temperatura.

## 20. Homologação

Emulador: UI, lógica, persistência, navegação, lifecycle e testes automatizados.

Android arm64 real: DSP, ML, performance, temperatura, bateria e background. Separação não é homologada somente em emulador.

## 21. Eficiência

Preferir `SAF URI → stream/file descriptor → engine nativo`; evitar cópias, resamplings e intermediários redundantes. Estimar espaço e limpar temporários em sucesso/cancelamento/erro.

## 22. Licenciamento

Antes de distribuição pública, auditar Rubber Band, FFmpeg, Demucs, BS-RoFormer, checkpoints, runtime ML e componentes de download.

## 23. Critério final de sucesso

Concluído somente quando instala por APK, funciona sem ferramentas externas, mantém workflow/paridade da v5.23, projetos/backups interoperáveis, Rápida é padrão, Alta permanece disponível, tarefas longas sobrevivem a background/lock screen, cancelamento/recovery são seguros, resultados passam golden tests e somente validações estritamente físicas restam para homologação manual.

## 24. Continuidade entre chats

1. carregar este arquivo;
2. tratar GBW Linux v5.23 como baseline;
3. não alterar decisões consolidadas sem autorização explícita;
4. identificar milestone/gate atual;
5. continuar do último gate aprovado;
6. preservar funcionalidade homologada;
7. atualizar este documento quando uma decisão estrutural mudar.

Este arquivo é a fonte de referência da migração Android até ser explicitamente substituído.
