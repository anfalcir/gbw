# GBW Android 6.0 — Campanha Final de Homologação Física (R6)

Use exclusivamente o RC gerado a partir do HEAD documentado em docs/CURRENT_STATE.md.
Registre PASS/FAIL e observação para cada linha.

## Identidade do build

- [ ] versão/commit conferem com build-info.txt;
- [ ] SHA-256 do APK confere;
- [ ] certificado confere com o perfil esperado (homologação para campanha; produção no artefato final).

## Instalação e migração

- [ ] instalação limpa;
- [ ] upgrade sobre alpha anterior;
- [ ] projeto schema antigo abre sem crash;
- [ ] fonte/separação válidas são preservadas;
- [ ] export antigo incompatível não reaparece como atual.

## Projetos/sessão

- [ ] Projeto A abre corretamente;
- [ ] fechar projeto limpa Fonte/Separação/Export da sessão;
- [ ] Projeto B não herda dados do Projeto A;
- [ ] reabrir A restaura somente dados de A;
- [ ] busca e agrupamento por artista/música;
- [ ] duplicação cria UUID distinto.

## Fonte

- [ ] arquivo local;
- [ ] M4A;
- [ ] MP3;
- [ ] FLAC;
- [ ] WAV;
- [ ] pesquisa online;
- [ ] YouTube/download;
- [ ] cancelamento;
- [ ] erro de rede e tentativa posterior.

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

## Logs / Sistema / UX

- [ ] logs mostram timestamp/job/projeto/erro;
- [ ] copiar log;
- [ ] limpar histórico;
- [ ] diagnóstico avançado fica fora do caminho principal;
- [ ] rotação/recriação;
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
