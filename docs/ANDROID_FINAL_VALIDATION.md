# GBW Android 6.0 — Campanha Final de Homologação Física (R6)

Use exclusivamente o RC gerado a partir do commit documentado em docs/CURRENT_STATE.md.
Registre PASS/FAIL e observação para cada linha.

## Identidade do build

- [ ] versão/commit conferem com build-info.txt;
- [ ] SHA-256 do APK confere;
- [ ] certificado confere com o perfil esperado (homologação para campanha; produção no artefato final).

## Instalação e migração

- [ ] instalação limpa;
- [ ] upgrade sobre RC/alpha anterior compatível com a mesma chave de homologação;
- [ ] projeto schema antigo abre sem crash;
- [ ] fonte/separação válidas são preservadas;
- [ ] export antigo incompatível não reaparece como atual.

## Projetos / sessão

- [ ] Projeto A abre corretamente;
- [ ] fechar projeto limpa Fonte/Separação/Export da sessão;
- [ ] Projeto B não herda dados do Projeto A;
- [ ] reabrir A restaura somente dados de A;
- [ ] busca e agrupamento por artista/música;
- [ ] duplicação cria UUID distinto;
- [ ] remover/encerrar o GBW pelo multitarefas e abrir novamente inicia sem projeto aberto;
- [ ] rotação/recriação normal não fecha o projeto;
- [ ] apenas colocar em background e retornar não fecha o projeto.

## Fonte local

- [ ] arquivo local;
- [ ] M4A;
- [ ] MP3;
- [ ] FLAC;
- [ ] WAV;
- [ ] erro de fonte aparece perto da ação e também em toast.

## Pesquisa online — regressão RC3 obrigatória

- [ ] iniciar pesquisa e trocar de tela; ao retornar, a pesquisa/resultado permanece coerente;
- [ ] repetir pesquisa que anteriormente encontrou YouTube;
- [ ] um resultado YouTube indisponível não encerra toda a busca;
- [ ] candidatos válidos continuam aparecendo quando outro candidato falha;
- [ ] Apple Music/iTunes não aparece como resultado;
- [ ] selecionar candidato recomendado;
- [ ] iniciar preparação da fonte sem erro de quota/limite de foreground service;
- [ ] trocar de tela durante a preparação e retornar; tarefa continua;
- [ ] conclusão publica a fonte no projeto correto;
- [ ] cancelamento;
- [ ] erro de rede e tentativa posterior;
- [ ] avisos/erros aparecem próximos da seção correta e também via toast temporário.

## Separação

- [ ] inicia em background;
- [ ] notificação correta;
- [ ] app pode ir para segundo plano;
- [ ] lock screen prolongado;
- [ ] retorno ao app preserva estado/progresso;
- [ ] cancelamento limpa parciais;
- [ ] conclusão gera exatamente seis stems;
- [ ] preview de cada stem;
- [ ] nenhum corte/falha audível;
- [ ] temperatura/RAM/bateria aceitáveis no hardware alvo.

## Export

- [ ] backing = drums+bass+other+vocals+piano;
- [ ] guitar separada;
- [ ] alinhamento/duração corretos;
- [ ] relação de nível backing↔guitar preservada;
- [ ] FLAC 24;
- [ ] WAV 24;
- [ ] WAV float32;
- [ ] export repetido;
- [ ] pouco espaço gera erro antecipado e compreensível.

## Backup / Restore

- [ ] selecionar pasta no Google Drive via SAF;
- [ ] backup manual;
- [ ] backup automático;
- [ ] pequenas mudanças são coalescidas;
- [ ] sem mudança não cria versão duplicada;
- [ ] estrutura Projetos/Artista - Música/... correta;
- [ ] offline → online;
- [ ] conflito local/remoto;
- [ ] Manter local;
- [ ] Usar Drive;
- [ ] restore em instalação limpa;
- [ ] arquivos restaurados conferem;
- [ ] mudança remota não abre projeto silenciosamente.

## Sistema / Histórico / UX

- [ ] não existe página principal separada de Logs;
- [ ] Sistema contém Histórico de atividades;
- [ ] histórico mostra timestamp/tarefa/projeto/erro quando aplicável;
- [ ] copiar registro;
- [ ] limpar histórico;
- [ ] fluxo principal não mostra referência ao Linux 5.23;
- [ ] fluxo principal não exibe score interno/provider técnico/estado bruto RUNNING/CANCELLING;
- [ ] diagnóstico técnico fica recolhido em Sistema;
- [ ] mensagens importantes aparecem em local contextual;
- [ ] mensagens importantes também aparecem temporariamente em toast;
- [ ] telefone;
- [ ] tablet;
- [ ] portrait/landscape;
- [ ] font scale elevado;
- [ ] TalkBack;
- [ ] touch targets e leitura dos botões;
- [ ] barras do sistema não sobrepõem conteúdo.

## Critério de fechamento

R6 fecha somente se não houver blocker funcional, corrupção, vazamento entre projetos, falha de
integridade de backup/export ou crash reproduzível.

Falhas cosméticas não bloqueadoras devem ser registradas em docs/ANDROID_KNOWN_LIMITATIONS.md
ou issue específica antes de R7.
