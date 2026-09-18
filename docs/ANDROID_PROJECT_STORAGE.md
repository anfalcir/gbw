# GBW Android — Project Storage Contract

Status: Android 6.0.0-alpha11.

## Identity
Every project receives a random RFC-4122 UUID at creation. projectId is canonical and immutable.
- Rename preserves projectId.
- Duplicate creates a new projectId.
- Restore of the same remote project preserves projectId.
- Name, artist, song and folder names are never identity.

## Local layout
filesDir/projects/<projectId>/ contains project.json, source/, stems/ and exports/.
Jobs and DSP write to jobs/cache staging first. Only validated artifacts are promoted to the project tree.
Locks live outside the published tree under state/project-locks/.

## project.json
Schema v1 records identity, metadata, timestamps, workflow stage, managed source provenance, separation metadata, stem relative paths, pitch configuration, export metadata, integrity inventory and lastSyncedRevisionId.
Portable references are relative to the project root. Writes use temp + fsync + atomic move where supported.

## Managed source
A SAF source is copied into source/. The external URI is provenance only. Online acquisition preserves the downloaded native file and the prepared 44.1 kHz float WAV when available.

## Integrity
Durable source/stem/export files use relative path, size, mtime and SHA-256. SHA-256 remains the strong final content identity.

## Legacy migration
PreparedSourceStore, SeparationResultStore and jobs/<jobId> remain compatibility inputs. Migration creates one UUID, copies validated artifacts, writes project.json and only then writes an idempotent migration marker.