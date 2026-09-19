# GBW Export Contract

Producer: GBW
Schema version: 2

## Produto final

Cada exportação contém exatamente dois arquivos de áudio:
- backing = drums + bass + other + vocals + piano;
- guitar = stem de guitarra separado.

O Android 6.0 é original-only. Não existe variante ajustada, alteração de tom, semitons ou processamento de formantes.

## Shared gain

O renderer:
1. mistura os cinco stems de backing em float32;
2. avalia o pico combinado backing + guitar;
3. usa -1 dBFS como teto de segurança;
4. calcula um único fator de ganho;
5. aplica exatamente o mesmo fator ao backing e à guitar.

Backing e guitar nunca são normalizados independentemente. A relação de nível entre os dois arquivos é preservada.

## Contrato de áudio

- sample rate: 44,1 kHz;
- canais: estéreo;
- alinhamento: backing e guitar têm o mesmo número de frames;
- formatos: WAV float32, WAV 24-bit, FLAC 24-bit.

## Layout local

exports/<exportId>/
  backing.<ext>
  guitar.<ext>
  export_manifest.json

O exportId é identidade interna da publicação. No backup remoto humano, o exportId não precisa aparecer na navegação.

## Manifest

export_manifest.json registra:
- schemaVersion 2;
- producer;
- projectId;
- artist;
- song;
- exportId;
- revision;
- targetPeakDbfs;
- sharedGainPolicy;
- sharedGainDb;
- artifacts com role, relativePath, format, sampleRate, channels, durationFrames, size e SHA-256.

Roles válidos do par final:
- backing;
- guitar.

## Invariantes

- guitar nunca é misturada no backing;
- shared gain é comum aos dois arquivos;
- nenhum artifact de estado anterior é mantido como export atual;
- falha/cancelamento não publica staging parcial;
- projetos schema v1 podem ser lidos para migração, mas export legado incompatível é invalidado;
- o writer atual produz somente schema v2 original-only.
