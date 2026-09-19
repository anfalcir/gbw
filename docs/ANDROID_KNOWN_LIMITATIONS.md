# GBW Android 6.0 — Known Limitations

## Deliberadas

- somente arm64-v8a;
- separação somente com Demucs htdemucs_6s;
- modelo é baixado na primeira necessidade e exige rede nesse momento;
- fluxo trabalha apenas no tom original;
- não há interoperabilidade de projeto/backup com a versão Linux;
- backup depende de provider SAF escolhido pelo usuário; Google Drive é utilizado por meio desse provider.

## Operacionais

- separação Demucs é computacionalmente pesada e pode consumir bateria/gerar calor;
- desempenho varia com SoC, estado térmico, memória disponível e política do Android;
- aquisição online depende do provedor de origem e pode falhar quando o serviço remoto altera formato, autenticação ou política;
- o app exige espaço temporário relevante para separação/export; o RC faz preflight e falha antes de iniciar quando o espaço estimado é insuficiente;
- restore/backup em providers lentos depende da estabilidade do provider e da conectividade.

## Release blockers atuais

Estes itens não são limitações funcionais; bloqueiam a declaração de produção final:
- assinatura privada de produção ainda não provisionada no GitHub;
- ausência de LICENSE do projeto;
- licenciamento do checkpoint htdemucs_6s precisa de confirmação/decisão explícita;
- campanha física final R6 ainda precisa ser executada no hardware alvo.

Veja:
- docs/ANDROID_THIRD_PARTY.md;
- docs/ANDROID_FINAL_VALIDATION.md.
