# GBW Linux 5.23 — Baseline Congelado

**Versão:** `5.23.0`  
**Pacote de origem validado:** `Guitar_Backing_Wizard_v5.23_Linux.zip`  
**SHA-256 do pacote de origem:** `ca4e0b1b95e9f308deb9ae8bccce673a091631105cb4fbd020909f6fef64ce4a`

`linux/app/` contém a distribuição Linux 5.23 completa, expandida diretamente do pacote autoritativo cuja hash está registrada acima.

`linux/MANIFEST.sha256` fixa os hashes SHA-256 de cada arquivo expandido e é o contrato de integridade da árvore preservada.

## Contrato de preservação

O repositório é autossuficiente para a versão Linux congelada. Uma cópia da pasta `linux/` deve bastar para inspecionar o código, instalar dependências, executar o aplicativo, executar validações, recriar atalhos e preservar assets/testes.

A baseline é considerada íntegra quando:

1. a versão e a hash do pacote de origem permanecem registradas neste arquivo;
2. `sha256sum -c linux/MANIFEST.sha256` passa integralmente;
3. a Linux Baseline CI está verde para qualquer alteração que toque `linux/**`.

Não modificar `linux/app/` como efeito colateral do desenvolvimento Android. Uma nova baseline Linux exige decisão explícita, nova versão, nova identidade/hash de origem, novo manifesto e atualização coordenada da documentação de paridade.
