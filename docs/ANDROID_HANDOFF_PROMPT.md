# Handoff — GBW Android 6.0

Repo: anfalcir/gbw
Branch: dev/android-6.0
Linux baseline: linux/ = 5.23.0 congelado e somente leitura.

## Leia primeiro

1. docs/ANDROID_MIGRATION_PLAN.md;
2. docs/CURRENT_STATE.md;
3. docs/PARITY_MATRIX.md;
4. docs/ANDROID_HARDENING_MATRIX.md;
5. docs/ANDROID_THIRD_PARTY.md;
6. confirmar HEAD/CI reais.

## Produto final

Fonte → Separação Demucs → Export original backing+guitar → Projeto/Backup Android

Não reintroduzir:
- afinação/detecção;
- alteração de tom;
- ferramenta de alteração de arquivo;
- Rubber Band;
- export ajustado;
- BS-RoFormer;
- Alta Qualidade/Comparar;
- interoperabilidade Linux↔Android de projeto/backup.

## Gates concluídos digitalmente

- R1 Alpha13: sessão/project scoping e Projetos;
- R2 Alpha14: original-only e remoção física do stack antigo;
- R3 Alpha15: Logs + UX de release;
- R4 Alpha16: hardening digital/stress/preflight.

Alpha16 de referência:
- commit c05068622d8e936cfdc5cbd139da296b7e9fc61e;
- CI #117 SUCCESS;
- APK SHA-256 8468b9ab6750cab9649425679a10ecf9e5e6f333d096dfa9d7c829ebc033af46.

## RC2

O candidato atual é 6.0.0-rc2 / versionCode 23 e ganhou pipeline separado de produção.

R5 permanece aberto enquanto faltar qualquer um:
- private key de produção;
- LICENSE do projeto;
- fechamento da auditoria do checkpoint Demucs;
- APK release assinado e verificado.

R6 é a campanha física única definida em docs/ANDROID_FINAL_VALIDATION.md.

R7 somente após R5 + R6.

## Regra

- não tocar linux/app;
- não enfraquecer CI;
- ler logs reais em qualquer falha;
- não confundir APK de homologação com release de produção;
- não declarar 100% antes de R1–R6 estarem fechados.
