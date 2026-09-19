# GBW Android 6.0 — Roadmap Mestre e Source of Truth

**Status:** fonte de verdade autoritativa para a conclusão do GBW Android 6.0  
**Baseline de referência:** GBW Linux 5.23.0, congelado em `linux/` e somente leitura  
**Branch de desenvolvimento:** `dev/android-6.0`  
**Data desta revisão:** 2026-09-18

> Este documento substitui versões anteriores do roadmap Android quando houver conflito.
> O objetivo não é mais reproduzir integralmente todas as funções do Linux: o Android tem
> um escopo de produto próprio, deliberadamente mais simples e centrado no uso no tablet.

---

## 1. Produto final decidido

O GBW Android 6.0 será um aplicativo **original-only**.

Fluxo principal:

```
Fonte original
  ↓
Preparação
  ↓
Separação Demucs htdemucs_6s
  ↓
6 stems
  ↓
Export original
  ├── backing = drums + bass + other + vocals + piano
  └── guitar
  ↓
Projeto local
  ↓
Backup automático Android / Google Drive
```

O GBW **não altera a tonalidade da música**. Qualquer pitch necessário para tocar será feito
externamente pela pedaleira do usuário.

---

## 2. Decisões de escopo irrevogáveis para 6.0

### 2.1 Separação

O Android usa **exclusivamente Demucs `htdemucs_6s`**.

Não fazem parte do produto final:
- BS-RoFormer;
- modo Alta Qualidade;
- modo Comparar;
- múltiplos motores de separação.

Contrato dos seis stems:
- drums;
- bass;
- other;
- vocals;
- guitar;
- piano.

### 2.2 Pitch e afinação — removidos do produto Android

Não fazem parte do GBW Android 6.0:
- detecção de afinação;
- análise de tom/tonalidade;
- candidatos de afinação;
- tuning → tuning;
- pitch por semitons;
- modo Sem pitch como configuração;
- preservação de formantes ligada a pitch;
- `Afinação & Pitch` no workflow;
- `Pitch de Arquivo`;
- Rubber Band R3 no APK;
- export ajustado/pitched.

O conteúdo deve permanecer no **tom original da fonte**.

O código de pitch existente no alpha12 é legado temporário e deverá ser removido de forma
controlada no próximo bloco. Compatibilidade de leitura de projetos alpha anteriores deve ser
mantida apenas para permitir migração segura para estado neutro/original.

### 2.3 Backup e projetos — Android-first

O sistema de backup Android atual é suficiente como produto.

Não é requisito de conclusão:
- abrir backup Linux no Android;
- abrir backup Android no Linux;
- round-trip de projeto Linux ↔ Android;
- compatibilidade de ZIP/manifest entre plataformas.

O Linux continua sendo baseline funcional para comportamentos úteis de UX, nomenclatura,
workflow e qualidade, mas **não é protocolo de armazenamento do Android**.

Contrato final Android:
- UUID interno e imutável por projeto;
- apresentação `Artista - Música`;
- persistência local;
- backup incremental;
- Google Drive/SAF;
- organização humana;
- restore Android ↔ Android;
- deduplicação por conteúdo;
- conflito explícito;
- nenhuma mudança remota abre projeto silenciosamente.

---

## 3. Estado comprovado até alpha12

Checkpoint validado:
- versão: `6.0.0-alpha12`;
- versionCode: `17`;
- commit: `9631fd165c457adeb103912633e2cc59d3adb32e`;
- Android CI #108: **SUCCESS**;
- APK SHA-256: `cded2ef6bb8ea02c329434c5d58550c9c4cfd9b3b18c8b42cb4bf5f1dff76ae7`.

Implementado e/ou homologado:
- shell Android nativo;
- SAF;
- fonte local;
- pesquisa/ranking;
- aquisição online;
- hardening YouTube;
- Demucs-only real no Android arm64;
- seis stems;
- OpenBLAS 1 thread como padrão homologado;
- execução pesada em processo `:media`;
- Foreground Service;
- cancelamento e cleanup;
- projeto com UUID imutável;
- nome automático `Artista - Música`;
- export backing + guitar com shared gain;
- backup SAF/Google Drive incremental;
- layout de backup v2 legível;
- organização automática por projeto/categoria;
- restore Android;
- backup automático via WorkManager.

### Evidência física alpha12

O backup automático foi homologado em Google Drive:
- funcionou sem intervenção manual;
- criou a organização esperada;
- separou arquivos por projeto/categoria;
- utilizou nome de banda e música corretamente;
- estado sincronizado foi refletido na UI.

Problemas UX observados no vídeo `132674.mp4`:
1. identidade banda/música duplicada no card de Projetos;
2. mensagem de projeto fechado permanece depois de reabrir;
3. stems continuam visíveis após fechar o projeto;
4. workflow ainda permite estado visual residual entre sessões;
5. alguns textos/diagnósticos ainda estão orientados à engenharia.

---

# 4. Arquitetura final obrigatória

## 4.1 Regra de sessão

A única verdade de workflow é:

`projectId ativo + project.json`

Fonte, stems e export exibidos nas páginas 1–3 devem pertencer exclusivamente ao projeto ativo.

Sem projeto aberto:
- Fonte começa limpa;
- Separação não mostra stems;
- Exportação não mostra export anterior;
- nenhum resultado global de outro projeto pode aparecer.

Fechar projeto:
- não exclui dados;
- limpa o active pointer;
- desmonta o contexto visual;
- volta à Fonte;
- restaura `Nenhum projeto aberto`.

## 4.2 Workflow final

A navegação principal de Processo será:

1. **Fonte**
2. **Separação**
3. **Exportação**

Gerenciamento:
- Projetos
- Logs

Aplicativo:
- Configurações
- Sistema

`Afinação & Pitch` e `Pitch de Arquivo` devem desaparecer completamente do produto final.

## 4.3 Export final

Somente original:

```
exports/
  original/
    backing.*
    guitar.*
```

Regras:
- backing = drums + bass + other + vocals + piano;
- guitar permanece separada;
- backing + guitar são avaliados em conjunto;
- um único ganho compartilhado é aplicado quando necessário;
- nunca normalizar backing e guitar separadamente;
- nenhum `pitch_+Nst` novo deve ser produzido.

Formatos finais:
- FLAC 24-bit;
- WAV 24-bit;
- WAV float32, quando mantido como opção.

## 4.4 Backup final

Layout humano:

```
Projetos/
  Artista - Música/
    Fonte/
    Separacao - Stems/
    Exports/
    Projeto/
```

O backup deve manter somente o estado atual referenciado pelo projeto, sem versionamento histórico
acidental.

---

# 5. Roadmap restante até 100%

## R1 — Alpha13: Session & Workflow UX Hardening

Objetivo: eliminar qualquer vazamento de estado entre projeto fechado/aberto e consolidar o fluxo
original-only antes de remover o stack de pitch.

### R1.A — sessão por projeto
- stems somente do projeto ativo;
- source somente do projeto ativo;
- export somente do projeto ativo;
- nenhum resultado global reaproveitado sem vínculo com `projectId`;
- fechar projeto limpa todas as páginas do workflow;
- abrir outro UUID reconstrói as telas apenas a partir dele;
- testes de troca Projeto A → fechar → Projeto B.

### R1.B — UX observada na homologação
- remover identidade duplicada no card;
- remover mensagem persistente de “Projeto fechado”;
- transformar mensagens transitórias em eventos/snackbar;
- projeto ativo não oferece ação redundante “Abrir”;
- estados vazios com CTA claro;
- textos coerentes com as ações reais.

### R1.C — Projetos
- pesquisa instantânea;
- comparação case/accent-insensitive;
- agrupamento por artista;
- artista A→Z;
- música A→Z;
- mostrar estágio útil do workflow;
- definir apresentação correta de cópias/duplicações;
- garantir UUID distinto em duplicação;
- zero vazamento entre projetos.

### Gate R1
- Unit/Lint/Build verdes;
- testes de sessão multi-projeto;
- homologação física curta de fechar/reabrir/trocar projeto;
- nenhum stem/source/export residual.

---

## R2 — Alpha14: Original-Only Simplification

Objetivo: remover integralmente pitch/afinação do Android.

### R2.A — UI e navegação
Remover:
- página Afinação & Pitch;
- página Pitch de Arquivo;
- opções/telas/textos de pitch;
- variantes ORIGINAL/AJUSTADO;
- menções a tuning, semitons e formantes no fluxo principal;
- diagnósticos Rubber Band em Sistema.

### R2.B — domínio/projeto
- estado novo sempre neutro/original;
- migrar projetos alpha antigos para original-only;
- reader pode aceitar campos legados de pitch;
- writer novo não deve criar dependência funcional de pitch;
- nenhum projeto precisa de tuning para exportar.

### R2.C — export
- eliminar render pitched;
- eliminar pasta `pitch_*st`;
- exportar somente backing + guitar originais;
- preservar shared gain;
- backup subsequente limpa artifacts pitched obsoletos que não pertencem mais ao estado atual.

### R2.D — runtime/build
Remover quando não houver mais consumidores:
- Rubber Band JNI;
- biblioteca nativa Rubber Band;
- download/build source Rubber Band;
- testes/golden específicos de pitch;
- gates CI Rubber Band;
- documentação/licenciamento Rubber Band.

### Gate R2
- busca no repositório sem referências funcionais de pitch/tuning/Rubber Band no produto Android,
  exceto compatibilidade de leitura/migração explicitamente documentada;
- APK menor ou igual ao anterior sem regressão funcional;
- Fonte → Separação → Exportação funcionando ponta a ponta.

---

## R3 — Alpha15: Logs, Projetos e UX de Release

### R3.A — Logs
Implementar página real:
- histórico da sessão;
- jobs;
- erros;
- timestamps;
- projeto relacionado quando aplicável;
- copiar;
- limpar;
- diagnóstico avançado separado do texto normal do usuário.

### R3.B — Sistema
- estados traduzidos e amigáveis;
- esconder saída bruta de worker em seção avançada;
- remover controles exclusivamente de engenharia do caminho principal;
- manter informações úteis de versão, Demucs, ABI, storage e diagnóstico.

### R3.C — Separação
Mover detalhes como:
- SHA do modelo;
- BLAS;
- chunks;
- mediana/máximo;
- thermal bruto

para `Detalhes técnicos` ou Sistema.

Tela principal deve priorizar:
- fonte;
- ação;
- progresso;
- seis stems;
- preview;
- export.

### Gate R3
- UX consistente em tablet e telefone;
- nenhum placeholder restante;
- textos finais revisados;
- TalkBack/content descriptions/touch targets verificados.

---

## R4 — M9 Final: Hardening e Regressão

### Automação
Adicionar/fechar testes para:
- Activity recreation;
- rotação;
- background/foreground;
- lock screen onde automatizável;
- cancelamento;
- recovery;
- provider SAF lento;
- offline → online;
- conflito de backup;
- mídia removida;
- pouco espaço;
- arquivos corrompidos/truncados;
- lifecycle;
- font scale;
- tablet/phone;
- acessibilidade.

### Stress
- fontes curtas/médias/longas;
- separação prolongada;
- múltiplos projetos;
- sucessivos fechar/abrir;
- export repetido;
- backup repetido sem mudanças;
- alterações pequenas coalescidas;
- restore Android;
- Drive offline/retorno;
- cleanup após erro/cancelamento.

### Física
Validar no hardware alvo:
- Demucs;
- temperatura;
- bateria;
- RAM;
- tela bloqueada;
- troca de app;
- notificação/cancelamento;
- YouTube;
- export;
- backup/restore;
- ergonomia.

### Gate R4
Matriz digital e física sem blocker conhecido.

---

## R5 — M10: Release Candidate

Criar `6.0.0-rc1`.

Obrigatório:
- assinatura privada de produção;
- certificado registrado;
- APK reprodutível;
- SHA-256;
- release notes;
- known limitations;
- matriz final de funcionalidades;
- auditoria de licenças dos componentes realmente distribuídos;
- remover documentação obsoleta;
- consolidar `dev/android-6.0` em `main`.

A auditoria Rubber Band deixa de ser blocker depois de sua remoção total do APK.

---

## R6 — M11: Homologação física final do RC

Uma única campanha final focada em:
- instalação limpa;
- upgrade de alpha/projeto antigo;
- Fonte local;
- Pesquisa Online/YouTube;
- Separação;
- previews;
- Export backing+guitar original;
- fechar/reabrir/trocar projetos;
- backup automático;
- backup manual;
- restore Android;
- background;
- lock screen;
- cancelamento;
- offline/online;
- tablet/ergonomia.

Não reabrir escopo por diferenças deliberadamente removidas:
- pitch;
- afinação;
- BS-RoFormer;
- backup Linux↔Android.

---

## R7 — M12: GBW Android 6.0.0

Entregáveis:

```
Guitar_Backing_Wizard_6.0.0.apk
SHA-256
certificado de assinatura
relatório consolidado de testes
matriz final de funcionalidades
known limitations
release notes
```

Projeto é considerado 100% quando todos os gates R1–R6 estiverem fechados.

---

# 6. O que NÃO bloqueia 6.0

Explicitamente fora de escopo:
- detecção de afinação;
- qualquer pitch;
- Pitch de Arquivo;
- Rubber Band;
- BS-RoFormer;
- múltiplos separadores;
- export pitched;
- projeto/backup interoperável com Linux;
- backup ZIP Linux;
- backup do aplicativo Linux;
- reprodução do sistema de afinação do desktop.

Esses itens não devem voltar ao roadmap como “pendências”.

---

# 7. Uso do Linux daqui para frente

Linux 5.23 permanece congelado e útil para comparar:
- nomenclatura;
- pesquisa/ranking;
- organização do workflow;
- lifecycle visual;
- projetos/listagem;
- estados vazios;
- logs;
- princípios de export;
- qualidade de UX.

Não usar o Linux como requisito de paridade para:
- pitch/afinação;
- formato de projeto;
- formato de backup;
- motor de separação alternativo;
- detalhes de implementação desktop.

---

# 8. Definition of Done final

O GBW Android 6.0 está concluído quando:

1. instala por APK sem dependência externa;
2. Fonte funciona local e online;
3. Demucs gera seis stems de forma estável;
4. background/cancelamento/recovery são seguros;
5. exporta backing + guitar no tom original com shared gain correto;
6. projetos têm identidade imutável e sessão coerente;
7. fechar projeto não deixa estado residual;
8. backup Android automático/manual é robusto e restaurável;
9. Drive mantém estrutura humana organizada;
10. Logs e Sistema estão prontos para usuário final;
11. lifecycle, stress, erros e acessibilidade passam;
12. CI e regressão física final passam;
13. APK RC/final usa assinatura de produção;
14. licenças dos componentes distribuídos estão fechadas;
15. documentação final corresponde ao produto real.

---

# 9. Regra de continuidade

Toda nova sessão deve:

1. ler este arquivo primeiro;
2. confirmar HEAD remoto;
3. ler `docs/CURRENT_STATE.md`;
4. ler `docs/PARITY_MATRIX.md`;
5. identificar o próximo gate R1–R7 não concluído;
6. executar o trabalho, testar e acompanhar CI;
7. atualizar o estado ao fechar o gate.

Nenhum roadmap histórico substitui este documento.
