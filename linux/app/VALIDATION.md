# Validação da versão 5.23

Esta build passou por validação automatizada de núcleo, pipeline e interface gráfica.

Cobertura principal:

- busca e filtragem de fontes;
- bloqueio de trechos curtos/incompletos;
- preparação do áudio;
- separadores A e B;
- cancelamento seguro da separação;
- estimativa de tempo da separação A;
- detecção e confirmação de afinação;
- pitch automático, manual e sem pitch;
- exportação de `backing + guitar`;
- navegação linear 1→4;
- escala de interface até 250%;
- layout responsivo e sidebar rolável;
- scroll do mouse proporcional à escala;
- ícone personalizado da janela/atalho;
- criação de atalho no menu e na Área de Trabalho.

Execute `./validate_local.sh` para validar a instalação no computador de destino.


## Validação adicional 5.19
- Backup/restauração compacta de projeto com verificação SHA-256.
- Backup portátil do aplicativo sem dados pessoais/projetos.
- Exclusão segura de projetos dentro da pasta configurada.
- Fechar projeto retorna ao estado inicial.
- Cancelamentos locais nas ações demoradas.

## Validação adicional 5.20
- Projetos ordenados por banda/artista A–Z e música A–Z.
- Pesquisa instantânea por banda e/ou música sem releitura dos manifests a cada tecla.
- Agrupamento visual por banda/artista na página Projetos.

## Validação adicional 5.21
- Ordenação e agrupamento de projetos sem sensibilidade a maiúsculas/minúsculas ou acentos.
- Artista e música normalizados no padrão de iniciais maiúsculas ao criar, salvar, carregar e restaurar projetos.
- Projetos legados com caixa inconsistente são exibidos de forma canônica sem renomear os diretórios técnicos.


## Validação adicional 5.23

- Ferramenta Pitch de Arquivo independente do projeto.
- Classificação real de WAV float32/48 kHz como Ideal, FLAC 24-bit como Adequado e MP3 como Ressalva.
- Taxa de amostragem e canais preservados no render avulso.
- Conversão por afinação/semitons e inversão de projeto.
- Confirmação agressiva reservada exclusivamente ao estado Ressalva.
- Box DAW recolhível e GUI validada em 200%/250%.
- Processamento delegado ao mesmo motor central do workflow.
