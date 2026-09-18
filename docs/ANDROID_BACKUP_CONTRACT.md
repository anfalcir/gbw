# GBW Android — Backup SAF Contract

Status: Android 6.0.0-alpha11.

## Destination
The user selects a document tree with ACTION_OPEN_DOCUMENT_TREE. GBW persists scoped read/write access and uses provider-neutral SAF; Google Drive is consumed through its DocumentsProvider rather than a proprietary OAuth/API dependency.

A destination is accepted only after a create/write/read/byte-compare/delete probe. Changing or disconnecting a destination never deletes remote content automatically.

## Remote identity and layout
projectId is the immutable remote identity. Display folder names are not identity.

Selected tree:
- GBW_ROOT.json
- projects/<projectId>/files/
- projects/<projectId>/revisions/v_<revisionId>/manifest.json
- projects/<projectId>/revisions/v_<revisionId>/commit.json
- projects/<projectId>/current.json

revisionId is deterministic: r_<updatedAtEpochMs>_<stateDigest>. The digest covers canonical project state and durable artifact hashes.

## Publication and eventual consistency
1. Capture an immutable local snapshot under the project lock.
2. Stream/reuse content blobs by size + SHA-256.
3. Write revision manifest.
4. Write commit marker last.
5. Re-read direct document URIs and validate size/SHA-256.
6. Use targeted projectId/revisionId lookup with bounded settling.
7. Update the advisory current pointer.
8. Only after the new revision is proven valid, remove obsolete revision metadata and unreferenced blobs.

An immediate parent-folder relist is never the only proof of commit.

## No-history retention
revisionId is used for change detection, idempotent retries and conflict detection, not user history. Stable remote state keeps one logical current revision per projectId.

## Contents
Backup includes project metadata, managed original/prepared source, all six Demucs stems, final backing/guitar exports and export manifest. Audio remains as individual files; backup is not an opaque ZIP-only package.

## Incremental behavior
Unchanged large artifacts are reused by strong SHA-256 identity. A metadata-only rename therefore does not re-upload hundreds of MB of source/stems.

## Scheduling and coalescence
WorkManager uses unique work, network constraint and foreground execution.
Supported periods: Manual only, 15 min, 1 h, 6 h, 12 h, 24 h.
Durable changes update a persistent dirty state. A 30-second quiet window coalesces rapid edits, with a 5-minute maximum latency. Changes during an upload remain dirty and are handled by a bounded follow-up cycle rather than spawning one worker per click.

Backup bookkeeping does not mutate project updatedAt and cannot create a backup loop.

## Reconciliation
- remote-only: import preserving projectId;
- local-only: upload;
- same revision: no-op;
- local changed while remote equals lastSynced: upload;
- remote changed while local equals lastSynced: safe local replacement with rollback;
- both changed: explicit conflict.

Conflict UI offers Keep local or Use Drive. Local-only delete records an ignore/tombstone so scan does not immediately resurrect the same remote project.