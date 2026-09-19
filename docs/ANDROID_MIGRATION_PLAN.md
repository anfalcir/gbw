# GBW Android — Plano Mestre de Migração

Baseline congelado: Linux 5.23.0.

## Concluído até alpha11

- shell Android;
- SAF local;
- pesquisa/ranking;
- aquisição online;
- Demucs-only;
- OpenBLAS 1 thread default;
- Rubber Band R3;
- projeto UUID;
- export backing+guitar/shared gain;
- backup SAF incremental.

## Alpha12 — hardening de produto

Bloco dedicado a paridade comportamental e UX após homologação real:
- sessão de projeto abrir/fechar;
- naming automático;
- status terminal correto;
- ANR/IO de backup;
- destination display name;
- Drive layout v2;
- migração v1→v2;
- inventário current-only;
- reconcile sem side effect no projeto ativo.

## Próximos blocos após homologar alpha12

- avaliar gap de histórico de Logs no Android;
- decidir se detecção automática de afinação do Linux deve ser portada ou permanecer fora do escopo móvel;
- stress de backup com provider lento/offline;
- RC/release hardening.
