# Guitar Backing Wizard 5.23

Aplicativo desktop para Linux que prepara músicas para estudo de guitarra: encontra uma fonte, separa a guitarra, permite ajustar afinação/pitch e exporta arquivos prontos para usar no REAPER ou em outro DAW.

## Fluxo

1. **Fonte** — pesquise a música, escolha a melhor fonte ou use um arquivo local.
2. **Separação** — separe a guitarra do restante da banda.
3. **Afinação & Pitch** — detecte/confirme a afinação, escolha o destino e confirme o ajuste.
4. **Exportação** — gere `backing + guitar` no tom original e/ou com pitch ajustado.

## Instalação

No terminal, dentro da pasta do aplicativo:

```bash
chmod +x install.sh run.sh criar_atalho.sh validate_local.sh
./install.sh
```

Depois execute:

```bash
./run.sh
```

Crie o atalho no menu de aplicativos e na Área de Trabalho:

```bash
./criar_atalho.sh
```

O atalho usa o ícone do GBW e aponta para a cópia instalada em `~/.local/share/guitar-backing-wizard/app`, portanto continua funcionando mesmo se a pasta extraída for movida.

## Uso em TV

Em **Configurações → Escala da interface**, há opções de 100% a 250%. Para TV, normalmente 175%–250% oferece melhor leitura. A velocidade da roda do mouse acompanha a escala da interface para manter a navegação confortável.

## Separação

- **A — Alta qualidade:** opção recomendada para isolar guitarra.
- **B — Alternativa:** outra opção de separação.
- **A + B — Comparar:** gera as duas opções para você decidir qual ficou melhor.

Durante a separação, o aplicativo mostra o tempo decorrido e uma estimativa de tempo restante. A separação A pode ser demorada em CPU.

## Afinação & Pitch

A detecção automática sugere a afinação e o tom provável. Você pode corrigir a afinação original, escolher a afinação de destino e usar:

- **Automático pela afinação** — calcula os semitons necessários.
- **Manual** — permite informar os semitons diretamente.
- **Sem pitch** — mantém o áudio no tom original.

Use **Confirmar e continuar** para concluir a etapa. O pitch é aplicado durante a exportação dos arquivos finais.

## Exportação

O padrão é gerar dois arquivos sincronizados:

- `backing` — restante da banda;
- `guitar` — guitarra isolada.

No REAPER, você pode importar os dois e mutar a faixa `guitar` quando quiser tocar no lugar da guitarra original.

## Ferramenta: Pitch de Arquivo

A seção **FERRAMENTAS → Pitch de Arquivo** funciona mesmo sem projeto aberto. Ela permite carregar um arquivo de áudio e aplicar pitch usando o mesmo motor Rubber Band R3 do workflow.

- conversão por **afinação atual → afinação destino** ou por **semitons**;
- botão **Inverter** e, quando houver projeto aberto, **Usar conversão inversa do projeto**;
- tipo **Instrumento / Mix** ou **Vocal** (com preservação de formantes);
- saída recomendada **WAV 32-bit float**, além de WAV 24-bit e FLAC 24-bit;
- preserva a taxa de amostragem e os canais do arquivo de entrada;
- valida duração/sincronismo antes de entregar o arquivo final;
- cancelamento local e limpeza de temporários.

Ao selecionar o input, o GBW inspeciona formato/codec, lossless/lossy, profundidade, taxa de amostragem, canais, duração e pico/headroom. O resultado é classificado como **Ideal**, **Adequado** ou **Ressalva**. Apenas **Ressalva** exige uma decisão adicional antes do processamento; arquivos adequados seguem sem confirmação irritante.

A orientação **Como exportar do seu DAW para o GBW?** fica recolhida por padrão e recomenda WAV 32-bit float na mesma taxa da sessão (44,1 kHz deve permanecer 44,1 kHz; 48 kHz é uma boa escolha para uma sessão criada do zero).

## Projetos

A página **Projetos** organiza automaticamente as bandas/artistas em ordem alfabética, sem diferenciar maiúsculas/minúsculas ou acentos, e dentro de cada banda as músicas também ficam em ordem A–Z. Artista e música são padronizados com iniciais maiúsculas ao criar, salvar, carregar e restaurar projetos. A barra **Pesquisar** filtra a lista instantaneamente conforme você digita o nome da banda ou da música.

## Logs e diagnóstico

A página **Logs** mostra apenas eventos importantes da sessão. Informações técnicas completas continuam registradas nos arquivos de log para diagnóstico, sem poluir a interface normal.

A página **Sistema** mostra se os componentes necessários estão prontos e oferece um botão para executar o instalador quando necessário.

## Validação local

Para verificar a instalação:

```bash
./validate_local.sh
```


## Backup e restauração

### Backup do projeto
Na etapa **4. Exportação**, use **Salvar backup do projeto…** e escolha uma pasta local ou sincronizada com a nuvem. O pacote preserva configurações, fonte original, WAV preparado, análise de afinação e arquivos finais da pasta `exports`. Stems intermediários não são incluídos para manter o backup compacto.

Para restaurar, abra **Projetos > Restaurar backup…**. O GBW recria o projeto e preserva os exports; como os stems não fazem parte do backup compacto, o fluxo seguro de retomada é a Separação.

### Backup do GBW
Em **Sistema > Backup do GBW**, escolha uma pasta de destino. O ZIP gerado contém o programa, ícone, scripts, instalador, requisitos e testes necessários para reinstalação. Projetos, músicas, logs, caches, ambiente virtual e configurações pessoais ficam de fora.

Para usar em outra máquina Linux, extraia o ZIP e execute `./install.sh`.

## Fechar projeto e cancelamentos
O rodapé da barra lateral usa **Fechar projeto**. Ele salva o estado atual, fecha o projeto e retorna ao início do fluxo. Cancelamentos ficam junto da ação correspondente: busca/preparação da fonte, separação, análise de afinação, exportação, backup e restauração.


## 5.23

- Nova seção **FERRAMENTAS → Pitch de Arquivo**, independente do workflow/projeto.
- Análise automática de qualidade do input com estados Ideal/Adequado/Ressalva.
- Confirmação adicional somente para ressalvas, com **Escolher outro arquivo** / **Continuar mesmo assim**.
- Conversão por afinação ou semitons, inversão e reutilização inversa do pitch do projeto.
- Processamento pelo mesmo Rubber Band R3 do workflow, preservando sample rate, canais e sincronismo.
- Box recolhível com orientação de exportação DAW → GBW.
