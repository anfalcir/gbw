# GBW Android — Plano Mestre de Migração

**Projeto:** Guitar Backing Wizard (GBW)  
**Baseline funcional congelado:** GBW Linux v5.23.0  
**Baseline SHA-256:** `ca4e0b1b95e9f308deb9ae8bccce673a091631105cb4fbd020909f6fef64ce4a`  
**Plataforma-alvo:** Android  
**Linha de versão proposta:** Android 6.x  
**Status deste documento:** Fonte de verdade para a migração Android  
**Data de consolidação:** 2026-09-17

---

## 1. Objetivo

Transformar o **GBW Linux v5.23** em um aplicativo Android instalável diretamente por APK, mantendo a lógica, qualidade, robustez, fluxo e resultados do baseline desktop, sem depender de Termux, Python externo, comandos de terminal ou componentes que o usuário precise instalar manualmente.

A migração deve ser feita de forma **ordenada, seriada, validável e conservadora**, evitando uma simples “conversão” da interface Python/Tkinter para Android.

A implementação Android será tratada como uma **reimplementação nativa compatível**, tendo a v5.23 como referência funcional e de qualidade.

---

## 2. Princípios obrigatórios

1. **A v5.23 Linux é o baseline congelado.**
   - Não deve ser alterada durante a migração Android.
   - Mudanças futuras devem entrar em linha separada e explicitamente versionada.

2. **Uma única verdade funcional.**
   - Regras de afinação, pitch, workflow, projetos, backups, exportação e validações devem produzir comportamento equivalente no Linux e Android.

3. **Android nativo.**
   - Kotlin + Jetpack Compose para aplicação e interface.
   - C++/NDK/JNI quando necessário para DSP/áudio nativo.

4. **Sem dependências externas para o usuário final.**
   - Nada de Termux.
   - Nada de Python externo.
   - Nada de FFmpeg/Rubber Band instalados pelo usuário.
   - O APK deve ser autossuficiente, exceto downloads opcionais de modelos pesados.

5. **Qualidade antes de velocidade, mas com adequação ao hardware móvel.**

6. **Processamento pesado nunca pode depender da tela/Activity.**

7. **Projetos e backups devem ser interoperáveis entre Linux e Android sempre que tecnicamente possível.**

8. **Toda etapa crítica deve possuir testes e gate de aprovação antes de avançar.**

---

# 3. Arquitetura-alvo

```text
GBW Android
│
├── UI
│   └── Kotlin + Jetpack Compose + Material 3
│
├── Domain
│   ├── Projetos
│   ├── Workflow
│   ├── Afinações
│   ├── Pitch
│   ├── Normalização de nomes
│   ├── Regras de qualidade
│   └── Backup / Restore
│
├── Services
│   ├── Fonte
│   ├── Download
│   ├── Separação
│   ├── Afinação
│   ├── Pitch
│   ├── Exportação
│   └── Sistema
│
├── Native Audio Engine
│   ├── FFmpeg
│   ├── Rubber Band R3
│   └── DSP auxiliar
│
├── ML Engine
│   ├── Demucs / htdemucs_6s
│   └── BS-RoFormer-SW
│
└── Background Processing
    ├── Foreground Service
    ├── WorkManager quando aplicável
    ├── Notificação persistente
    ├── Estado persistido
    └── Recuperação / cleanup
```

---

# 4. Estrutura visual do app Android

A organização funcional deve preservar a lógica da v5.23:

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

## Comportamento responsivo

### Tablet
- Navegação lateral persistente sempre que houver espaço.
- Layout otimizado para tela grande.

### Smartphone
- Drawer/navigation adaptativa.
- Mesmas funções, sem redução artificial do escopo.

---

# 5. Separação — decisão específica para Android

## Padrão do Android

A opção padrão no Android será:

> **Rápida — recomendada no Android**

Motor interno:

> **Demucs / htdemucs_6s**

A interface apresentará:

```text
Separação

● Rápida — recomendada no Android
  Mais eficiente para celulares e tablets.

○ Alta qualidade
  Processamento mais pesado e demorado.

○ Comparar as duas
  Executa as duas opções para comparação.
```

## Mapeamento interno

```text
Rápida          → Demucs htdemucs_6s
Alta qualidade  → BS-RoFormer-SW
Comparar        → ambos
```

## Regras

- No primeiro uso do Android, **Rápida é o padrão**.
- O usuário continua podendo escolher Alta qualidade.
- A preferência escolhida pode ser persistida.
- Nenhum formato de projeto ou exportação muda por causa dessa preferência.
- A decisão deve ser confirmada por benchmark real durante o M1.
- Se BS-RoFormer se mostrar suficientemente eficiente no Android, o padrão poderá ser reavaliado somente com evidência técnica.

---

# 6. Execução resiliente de tarefas longas

Este é um requisito estrutural obrigatório.

## BG-01 — Processamento independente da interface

Toda tarefa longa deve continuar funcionando quando:

- o usuário muda de aplicativo;
- a tela é bloqueada;
- a tela apaga;
- o Android recria a Activity;
- o aparelho gira;
- o GBW fica em background.

Arquitetura:

```text
UI
 ↓
Foreground Service / Worker
 ↓
Engine
 ↓
Estado persistido
```

## Notificação persistente

Exemplo:

```text
GBW — Separação em andamento
Wolves At The Gate — Enemy
Rápida • 43%
Tempo decorrido: 28:14

[ Abrir GBW ]   [ Cancelar ]
```

## Regras

- A Activity nunca será proprietária da tarefa longa.
- Voltar ao app reconecta ao job existente.
- Não reinicia automaticamente a tarefa.
- Cancelamento deve ser explícito e seguro.
- Tela pode apagar normalmente.
- CPU pode permanecer ativa quando necessário usando mecanismos apropriados.
- Recursos de background devem ser liberados imediatamente ao concluir/cancelar.

## Persistência de estado

Toda tarefa longa deve registrar:

```text
tipo
projeto
estado
etapa
progresso
arquivos intermediários válidos
data/hora
parâmetros
erro anterior, se houver
```

## Recuperação

Se o Android interromper o processo por memória, temperatura, reinício ou outra condição:

- preservar etapas já concluídas;
- limpar apenas temporários inválidos;
- retomar do ponto tecnicamente possível;
- quando não houver checkpoint interno no modelo, reiniciar somente a unidade necessária, nunca todo o workflow sem necessidade.

## Cenários obrigatórios de teste

- Separação → Home → voltar.
- Separação → outro app → voltar.
- Separação → bloquear tela.
- Tela apagada por período longo.
- Rotação.
- Activity destruída/recriada.
- Baixa memória.
- Thermal throttling.
- Pouco espaço.
- Cancelamento pela notificação.
- Cancelamento pela tela do GBW.
- Processo morto e posterior recuperação.
- App removido dos recentes sem force-stop.
- Force-stop explícito deve ser tratado como interrupção externa não evitável.

---

# 7. Matriz de paridade Linux v5.23 ↔ Android

A migração só será considerada concluída quando os contratos principais estiverem equivalentes.

| Área | Linux v5.23 | Android |
|---|---|---|
| Afinações | referência | equivalente |
| Delta de semitons | referência | idêntico |
| Drop B → Drop D | +3 | +3 |
| Drop D → Drop B | -3 | -3 |
| Conversões impossíveis | bloqueadas | bloqueadas |
| Manifest | referência | compatível/versionado |
| Projetos | referência | equivalente |
| Backup | referência | interoperável |
| Pitch | Rubber Band R3 | mesmo motor/mesma lógica |
| Qualidade do input | referência | mesmos critérios |
| Exportação | referência | equivalente |
| Shared gain | referência | mesma regra |
| Pesquisa de projetos | referência | equivalente |
| Normalização de nomes | referência | equivalente |
| Cancelamento | referência | equivalente ou superior |
| Logs | referência | equivalente |
| Pitch de Arquivo | referência | equivalente |

---

# 8. Armazenamento e arquivos no Android

Usar o **Storage Access Framework (SAF)**.

Evitar dependência de caminhos absolutos Linux.

Internamente, o Android deve trabalhar com:

- URI;
- streams;
- file descriptors;
- caminhos relativos dentro do projeto quando aplicável.

## Objetivo

Permitir:

```text
Backup Linux → Android
Backup Android → Linux
```

sem depender de caminhos específicos da máquina/aparelho.

---

# 9. Modelos de separação

Modelos grandes não devem, por padrão, inflar o APK.

## Estratégia

```text
Instala APK
   ↓
Primeiro uso da Separação
   ↓
GBW verifica modelos
   ↓
Download opcional
   ↓
SHA-256
   ↓
Armazenamento privado do app
```

## Vantagens

- APK menor;
- atualização do app não reinstala modelos;
- modelos podem ser atualizados separadamente;
- menos espaço temporário;
- instalação direta mais confiável.

## Futuro opcional

Criar um pacote offline `GBW-Model-Pack` para preparação sem internet.

---

# 10. Pitch de Arquivo — primeiro fluxo DSP completo no Android

A ferramenta da v5.23 será usada como primeiro fluxo completo para validar o motor Android.

## Fluxo

```text
Selecionar arquivo
 ↓
Analisar input
 ↓
Ideal / Adequado / Ressalva
 ↓
Configurar pitch
 ↓
Rubber Band R3
 ↓
Validar duração / canais / sample rate
 ↓
Exportar
```

## Entrada

Suportar, quando o backend permitir:

- WAV
- FLAC
- AIFF
- MP3
- M4A/AAC
- OGG
- Opus

## Análise de qualidade

Verificar:

- formato/container;
- codec;
- lossless/lossy;
- bit depth;
- float/integer;
- sample rate;
- canais;
- duração;
- pico/headroom;
- clipping;
- decodificação/integridade.

## Classificação

### Ideal
Segue sem confirmação.

### Adequado
Mostra informação discreta. Não pede confirmação extra.

### Ressalva
Interrompe antes do processamento pesado e apresenta:

```text
[ Escolher outro arquivo ]
[ Continuar mesmo assim ]
```

Nada de `Sim/Não`.

## Conversão

Modos:

- Por afinação.
- Por semitons.

Inclui:

- Atual.
- Destino.
- Inverter.
- Conversão inversa do projeto aberto.
- Validação das conversões globais possíveis.

## Tipo de áudio

- Instrumento / Mix.
- Vocal com preservação de formantes.

## Saída

Padrão: **WAV 32-bit float**.

Alternativas:

- WAV 24-bit.
- FLAC 24-bit.

## Regras de qualidade

- Preservar sample rate do input por padrão.
- Preservar número de canais.
- Evitar resampling desnecessário.
- Time ratio 1:1.
- Validar duração final.
- Não entregar arquivo desalinhado.

---

# 11. Orientação DAW dentro do Pitch de Arquivo

Box recolhível: **Como exportar do seu DAW para o GBW?**

Fechado por padrão.

Conteúdo:

- WAV recomendado.
- 32-bit float recomendado.
- Manter a mesma taxa de amostragem da sessão.
- Sessão 44,1 kHz → manter 44,1 kHz.
- Sessão 48 kHz → manter 48 kHz.
- Sessão nova → 48 kHz é ótima escolha.
- Não fazer upsampling apenas para usar o GBW.
- Mono para guitarra mono.
- Preservar estéreo quando houver efeitos estéreo impressos.
- Não normalizar desnecessariamente.
- Evitar MP3/AAC quando houver fonte lossless disponível.
- Exportar desde o mesmo ponto inicial da backing/original para preservar sincronismo.

---

# 12. Fonte / download

Portar as capacidades da v5.23 sem acoplar o app inteiro a uma implementação específica de downloader.

Requisitos:

- pesquisa;
- ranking;
- validação de título/artista;
- rejeição de previews;
- consenso de duração;
- validação após download;
- preservação do arquivo fonte;
- preparação única em float32.

A estratégia Android para download deve ser provada no M1 antes de consolidar a arquitetura final.

---

# 13. Afinação & Pitch

Portar:

- análise;
- candidatos;
- probabilidade/confiança;
- seleção persistente;
- escolha alternativa persistente;
- original → destino;
- pitch automático;
- pitch manual;
- sem pitch;
- confirmação;
- inversão quando aplicável.

---

# 14. Exportação

Preservar a lógica atual:

```text
exports/original/backing.*
exports/original/guitar.*
exports/pitch_+Nst/backing.*
exports/pitch_+Nst/guitar.*
```

## Shared gain

- recombinar logicamente backing + guitar;
- medir pico comum;
- aplicar a mesma atenuação às duas;
- nunca normalizar separadamente.

## Formatos

- FLAC 24-bit;
- WAV 24-bit;
- WAV float32.

## Backup

Continuar suportando:

- backup compacto do projeto;
- restauração;
- backup do próprio GBW quando aplicável;
- seleção de pasta de destino via Android.

---

# 15. Projetos

Preservar:

- lista de projetos;
- agrupamento por banda;
- banda A→Z;
- música A→Z;
- comparação sem case;
- busca instantânea;
- normalização para iniciais maiúsculas;
- exclusão segura;
- abrir/continuar;
- restore;
- backup;
- estado sem projeto;
- fechamento de projeto.

---

# 16. Normalização textual

Artista e música devem manter o comportamento consolidado no desktop.

```text
WOLVES AT THE GATE → Wolves At The Gate
wolves at the gate → Wolves At The Gate
enemy              → Enemy
ENEMY                → Enemy
```

Ordenação e busca:

- case-insensitive;
- accent-insensitive quando apropriado para comparação;
- apresentação normalizada.

---

# 17. Roadmap seriado

## M0 — Congelamento do baseline

- congelar v5.23;
- registrar ZIP e SHA-256;
- inventariar funcionalidades;
- documentar schemas;
- construir matriz de paridade;
- criar corpus de áudio;
- gerar golden outputs.

**Gate:** baseline documentado e reproduzível.

## M1 — Provas técnicas de maior risco

### M1.A — FFmpeg Android
Validar WAV, FLAC, MP3, AAC/M4A, OGG/Opus, mono/stereo, 44,1/48/96 kHz.

### M1.B — Rubber Band R3 Android
Validar +pitch, -pitch, Drop B→Drop D, Drop D→Drop B, duração, sincronismo, peak/RMS e comparação com Linux.

### M1.C — Separação
Benchmarkar Demucs htdemucs_6s e BS-RoFormer-SW em CPU/GPU quando aplicável, RAM, tempo, temperatura, bateria e armazenamento.

**Decisão provisória:** Demucs/Rápida é padrão Android.

### M1.D — Fonte/download
Validar busca, download, cancelamento e background.

### M1.E — Background processing
Provar Foreground Service, tela bloqueada, troca de app, recriação de Activity, cancelamento e recuperação.

**Gate:** todos os riscos arquiteturais centrais demonstrados.

## M2 — Esqueleto Android

Kotlin, Jetpack Compose, Material 3, navegação responsiva, arquitetura de camadas, logging, armazenamento base e serviço de jobs.

**Gate:** aplicativo base executável e estável.

## M3 — Projetos / Persistência / Backup

Schema versionado, manifest, migração v5.23, SAF, lista, busca, exclusão, backup, restore e Linux↔Android.

**Gate:** round-trip de projeto aprovado.

## M4 — Pitch de Arquivo

Implementar integralmente o recurso da v5.23.

**Gate:** golden tests Linux↔Android aprovados.

## M5 — Fonte

Arquivo local, URL, pesquisa, ranking, download, preparação, cancelamento e background.

## M6 — Separação

Rápida/Demucs, Alta qualidade/BS-RoFormer, Comparar, seis stems, previews, progresso, cancelamento, cleanup e jobs persistentes.

**Gate:** separação estável no dispositivo Android real.

## M7 — Afinação & Pitch

Análise, candidatos, persistência, tuning→tuning, semitons, sem pitch e confirmação.

## M8 — Exportação e Backup Cross-Platform

Backing/guitar, pitched/original, shared gain, WAV/FLAC, pasta escolhida e backup cross-platform.

## M9 — Hardening

Stress, memória, armazenamento, erros, corruptos, background, temperatura, cancelamento, lifecycle, acessibilidade, responsividade e regressão completa.

## M10 — Release Candidate

APK assinado, matriz final, hashes, certificado, release notes e limitações conhecidas.

## M11 — Homologação física

Áudio, performance, temperatura, bateria, ergonomia, bloqueio de tela, background, armazenamento e exportação real.

## M12 — GBW Android 6.0.0

Entrega final:

```text
Guitar_Backing_Wizard_6.0.0.apk
SHA-256
certificado de assinatura
relatório de testes
matriz v5.23 ↔ Android 6.0
known limitations
```

---

# 18. Estratégia de versionamento

Desktop congelado: `GBW 5.23.0`.

Android:

```text
6.0.0-alpha1
6.0.0-alpha2
...
6.0.0-beta1
...
6.0.0-rc1
6.0.0
```

A versão 6.0.0 só será promovida quando a matriz de paridade estiver fechada.

---

# 19. QA obrigatório

## Unitários

- tuning;
- pitch delta;
- projeto;
- normalização;
- workflow;
- qualidade;
- backup;
- filtros;
- persistência.

## DSP / Golden

Linux v5.23 × Android:

- duração;
- alinhamento;
- sample rate;
- canais;
- peak;
- RMS;
- pitch;
- stems;
- exportações.

## Instrumentação Android

- navegação;
- SAF;
- permissões;
- rotação;
- Activity recreation;
- background;
- lock screen;
- cancelamento;
- recuperação.

## Stress

- 3 min;
- 6 min;
- 10+ min;
- 44,1 kHz;
- 48 kHz;
- 96 kHz;
- mono;
- stereo;
- WAV;
- FLAC;
- MP3;
- arquivos truncados/corrompidos;
- pouco armazenamento;
- pouca memória;
- alta temperatura.

---

# 20. Dispositivos de homologação

## Emulador

Usar para UI, lógica, persistência, navegação, lifecycle e parte dos testes automatizados.

## Android arm64 real

Obrigatório para DSP, ML, performance, temperatura, consumo e background real.

A separação não pode ser considerada homologada apenas em emulador.

---

# 21. Eficiência

Sempre que possível:

```text
SAF URI
→ stream / file descriptor
→ engine nativo
```

Evitar cópias gigantes desnecessárias, múltiplos resamplings e conversões intermediárias redundantes.

Implementar estimativa de espaço e cleanup após sucesso/cancelamento/erro.

---

# 22. Licenciamento

Antes de qualquer distribuição pública do APK, auditar Rubber Band, FFmpeg, Demucs, BS-RoFormer, checkpoints/modelos, runtime ML, componentes de download e demais bibliotecas.

---

# 23. Critério final de sucesso

O projeto Android só está concluído quando:

1. instala por APK diretamente;
2. funciona sem terminal/Python externo;
3. mantém o workflow da v5.23;
4. Pitch de Arquivo possui paridade;
5. projetos e backups são compatíveis;
6. separação rápida é padrão;
7. alta qualidade continua disponível;
8. tarefas longas sobrevivem a troca de app e bloqueio de tela;
9. cancelamento e recuperação são seguros;
10. os resultados de áudio passam nos golden tests;
11. o app passa nos testes de lifecycle/background;
12. o APK final é assinado e reprodutível;
13. somente validações estritamente físicas ficam para homologação manual.

---

# 24. Regra de continuidade entre chats

Ao iniciar um novo chat/sessão para continuar a migração Android:

1. carregar este arquivo;
2. tratar **GBW Linux v5.23.0** como baseline;
3. não alterar decisões consolidadas sem nova autorização explícita;
4. consultar o roadmap e identificar o milestone/gate atual;
5. continuar do último gate aprovado;
6. preservar toda funcionalidade já homologada;
7. atualizar este documento sempre que uma decisão estrutural mudar.

Este arquivo é a **fonte de referência do projeto Android GBW** até ser substituído explicitamente por uma revisão posterior.

---

## 25. CI/CD consolidada no monorepo

A partir de 2026-09-17, a política operacional do repositório `anfalcir/gbw` é:

- `android/**` e `linux/**` são árvores independentes;
- **todo commit/push em qualquer branch dispara Android CI automaticamente**;
- Linux CI dispara automaticamente quando `linux/**` ou o workflow Linux mudam;
- `workflow_dispatch` é apenas contingência;
- Android CI deve produzir APK debug + SHA-256 + relatórios como artifacts;
- uma sessão do ChatGPT deve acompanhar autonomamente os runs após seus commits, ler logs e iterar até verde;
- a política manual-only anterior está revogada.
