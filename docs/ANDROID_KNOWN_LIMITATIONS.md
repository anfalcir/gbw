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
- aquisição online depende do provedor de origem e pode falhar quando o serviço remoto altera formato, autenticação, disponibilidade regional ou política;
- resultados individuais indisponíveis são descartados quando possível, mas indisponibilidade ampla do serviço ainda pode impedir a aquisição;
- WorkManager pode reagendar a preparação online quando conectividade/restrições do Android exigirem;
- o app exige espaço temporário relevante para separação/export; o RC faz preflight e falha antes de iniciar quando o espaço estimado é insuficiente;
- restore/backup em providers lentos depende da estabilidade do provider e da conectividade.

## Release final pessoal

- secrets de produção foram informados pelo usuário como configurados, mas só o workflow manual poderá validá-los;
- a campanha física final R6 ainda precisa passar no hardware alvo;
- o APK final será de uso pessoal, sem distribuição pública/comercial;
- qualquer futura distribuição a terceiros exige reabrir auditoria de licenças/direitos e política de publicação.

Veja:
- docs/ANDROID_THIRD_PARTY.md;
- docs/ANDROID_FINAL_VALIDATION.md.
