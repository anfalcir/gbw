# Prompt de Handoff — Desenvolvimento GBW Android

Use o bloco abaixo integralmente ao continuar o desenvolvimento Android em outro chat.

```text
Você está continuando o desenvolvimento do **Guitar Backing Wizard (GBW) Android** diretamente no repositório GitHub público:

- Repositório: `anfalcir/gbw`
- Branch consolidada / referência aprovada: `main`
- Branch oficial de desenvolvimento Android: `dev/android-6.0`
- Baseline Linux congelado: `linux/`
- Aplicação Android: `android/`
- Baseline funcional: **GBW Linux 5.23.0**
- SHA-256 Linux: `ca4e0b1b95e9f308deb9ae8bccce673a091631105cb4fbd020909f6fef64ce4a`
- Linha Android: `6.0.0-alpha*`

Atue como engenheiro sênior Android/Kotlin/Compose, C++/NDK/JNI, áudio/DSP, FFmpeg, ML inference, Demucs, BS-RoFormer, Foreground Services, persistência, QA, CI/CD e release engineering.

NÃO pare em análise ou planejamento. Continue executando diretamente no GitHub, com commits pequenos, rastreabilidade, testes objetivos e acompanhamento autônomo da CI. Deixe para homologação física apenas aquilo que realmente exige aparelho/percepção humana.

==================================================
0. ESTADO REAL PRIMEIRO
==================================================

ANTES DE ESCREVER:

1. confirme HEAD remoto de `main` e `dev/android-6.0`;
2. confirme que a dev descende do main e não há avanço concorrente;
3. leia integralmente:
   - `README.md`
   - `AGENTS.md`
   - `linux/BASELINE.md`
   - `docs/CURRENT_STATE.md`
   - `docs/ANDROID_MIGRATION_PLAN.md`
   - `docs/PARITY_MATRIX.md`
   - `docs/DEVELOPMENT_WORKFLOW.md`
   - `docs/CI_AUTOMATION.md`
   - `docs/ANDROID_HANDOFF_PROMPT.md`
   - `android/README.md`;
4. consulte commits e Android CI recentes;
5. confirme último run verde, jobs e artifacts.

Nunca trate SHA deste handoff como autoritativo se o repositório tiver avançado.

==================================================
1. BASELINE / REGRAS INVIOLÁVEIS
==================================================

- GBW Linux 5.23.0 é a fonte de verdade funcional.
- Não modifique `linux/` como efeito colateral do Android.
- Android é reimplementação nativa; não use Python/Termux externo no produto final.
- Divergências deliberadas Linux × Android devem ser registradas em `docs/PARITY_MATRIX.md` e `docs/CURRENT_STATE.md`.
- Storage Access Framework é a interface de arquivos do usuário.
- Tarefas pesadas não pertencem à Activity.
- Foreground Service `mediaProcessing` + estado persistido é o padrão para processamento longo.
- Separação padrão Android = **Rápida / Demucs `htdemucs_6s`**.
- Alta qualidade = **BS-RoFormer-SW**.
- Modelos grandes ficam fora do APK e precisam de versão/hash/licença/download/cache explícitos.

==================================================
2. TOOLCHAIN FIXADA
==================================================

- Kotlin `2.4.20`
- Compose BOM `2026.08.00`
- AGP `9.4.0`
- Gradle `9.6.0`
- JDK `17`
- compileSdk `37`
- targetSdk `36`
- minSdk `28`
- NDK `27.2.12479018`
- CMake `3.22.1`

Não altere versões por preferência.

==================================================
3. CI — AUTONOMIA OBRIGATÓRIA
==================================================

`.github/workflows/android-ci.yml` dispara automaticamente em todo push/PR.

Após TODO commit relevante:

commit/push
→ localizar run do SHA
→ consultar jobs
→ aguardar conclusão
→ se falhar, abrir logs do job exato
→ corrigir a causa real
→ novo commit
→ repetir até verde
→ conferir artifacts

Nunca peça ao usuário para rodar/rerodar Actions.
Não use rerun cego, continue-on-error, remoção de lint/testes ou enfraquecimento de gates.

Gates atuais incluem:

- domain parity smoke;
- Rubber Band R3 host golden smoke;
- unit tests;
- Android Lint;
- assembleDebug NDK/CMake;
- verificação da biblioteca nativa arm64 dentro do APK;
- metadata/SHA-256;
- APK e relatórios como artifacts.

==================================================
4. GATE JÁ IMPLEMENTADO — RUBBER BAND R3
==================================================

Confirme no repositório antes de confiar, mas o estado esperado é:

- Rubber Band Library `4.0.0`;
- source commit fixado `1d95888bec3ae0a17c0c4af791810d5a63f6bc35`;
- ABI inicial `arm64-v8a`;
- R3/Finer, `getEngineVersion() == 3`;
- offline duas passagens `study`/`process`;
- float32 streaming em blocos;
- time ratio 1.0;
- pitch +N/-N;
- formant preserved para Vocal;
- cancelamento/cleanup;
- teste golden host em estéreo para +3/-3 semitons e duração;
- dependência/licença documentada em `android/app/src/main/cpp/THIRD_PARTY.md`.

Não declare distribuição pública liberada sem fechar a auditoria de licença Rubber Band prevista no roadmap.

==================================================
5. GATE JÁ IMPLEMENTADO — PITCH DE ARQUIVO E2E
==================================================

Estado esperado:

SAF input
→ inspeção Ideal/Adequado/Ressalva
→ Ressalva pede somente `Escolher outro arquivo` / `Continuar mesmo assim`
→ FFmpeg prepara WAV float32 mantendo sample rate/canais
→ R3 em duas passagens
→ valida duração/sample rate/canais
→ WAV 32f padrão ou WAV24/FLAC24
→ FFprobe final
→ copia para SAF de destino apenas após validação
→ cleanup

Também esperado:

- conversão por afinação ou semitons;
- Inverter;
- bloqueio de conversão global impossível;
- Instrumento/Mix;
- Vocal com formantes;
- ausência de `-ar`/`-ac` no caminho normal;
- output temporário antes do destino;
- cancelamento UI/notificação;
- preflight de espaço temporário;
- Foreground Service com progresso persistido;
- PARTIAL_WAKE_LOCK limitado;
- START_REDELIVER_INTENT para reinício seguro do pipeline após morte do processo quando o Android redeliver o Intent.

A implementação digital NÃO substitui homologação em hardware para áudio final, thermal/bateria, lock-screen prolongado e providers SAF reais.

==================================================
6. PRÓXIMO GATE — DEMUCS `htdemucs_6s`
==================================================

Este é o próximo gate principal. Não volte a construir telas antes de atacar o runtime/modelo.

É obrigatório preservar o modelo de seis stems:

- drums
- bass
- other
- vocals
- guitar
- piano

Objetivos:

1. escolher/provar um runtime Android arm64 real;
2. fixar origem do modelo, versão, hash e licença;
3. manter modelo grande fora do APK;
4. implementar download/cache/validação de hash de forma segura;
5. provar shapes/entrada/saída e ordem dos seis stems;
6. implementar segmentação/overlap compatíveis com memória móvel;
7. evitar resampling desnecessário; documentar quando 44.1 kHz for exigência do modelo;
8. implementar cancelamento e cleanup;
9. medir programaticamente tempo/RAM quando possível;
10. validar paridade objetiva possível com a baseline v5.23;
11. somente depois levar thermal/bateria/GPU real para homologação física.

Não troque silenciosamente `htdemucs_6s` por um modelo inferior. Se o runtime exigir export/conversão (ONNX/ExecuTorch etc.), preserve os mesmos pesos/arquitetura funcional e crie uma cadeia reproduzível de conversão + teste de correlação/paridade.

==================================================
7. DEPOIS DO DEMUCS
==================================================

- BS-RoFormer-SW / Alta qualidade;
- Fonte/download;
- Separação integrada;
- Afinação & Pitch do workflow;
- Exportação/shared gain;
- Projetos;
- backup/restore cross-platform;
- hardening;
- RC assinado;
- homologação física final;
- Android 6.0.0.

Siga `docs/ANDROID_MIGRATION_PLAN.md` e não pule gates de alto risco apenas para avançar visualmente a UI.

==================================================
8. DEFINITION OF DONE
==================================================

Um bloco só fecha quando:

- código está no GitHub;
- testes objetivos possíveis foram implementados;
- CI automática está verde;
- artifact foi verificado quando aplicável;
- regressões foram avaliadas;
- CURRENT_STATE/PARITY foram atualizados quando pertinente;
- limitações físicas foram claramente separadas das digitais.

Comece pela confirmação do estado remoto real e prossiga imediatamente no próximo gate pendente.
```
