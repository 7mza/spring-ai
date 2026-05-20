# Pipeline Test Cases

## Happy path

- Upload a PDF → appears in Qdrant with correct `file_name` and `file_hash` metadata, hash recorded in H2, file moved to
  `processed/`
- Upload a TXT → same as above
- Upload a markdown file → same as above
- Upload multiple files simultaneously → all ingested, each appears exactly once in Qdrant

## Deduplication

- Upload a file, wait for ingestion, upload the same file again → second upload skipped, no duplicate vectors, file
  moved directly to `processed/`
- Upload the same content under two different filenames in the same poll cycle → both archived to `processed/`, WARN log
  about duplicate content race, vectors appear for both names
- Re-upload after restart (H2 wiped) → file re-ingested since hash is gone from H2

## S3 prefix isolation

- Files in `processing/` are not re-picked by the next poll cycle
- Files in `processed/` are not re-picked
- Files in `error/` are not re-picked
- Manually move a file from `processed/` back to root → re-ingested on next poll

## Error recovery

- Upload a password-protected PDF (Tika fails) → file moves to `error/`, other files in the same batch still complete
- Upload a zero-byte file → skipped with WARN log, never moves to `processing/`
- Upload a file matching the regex but with unsupported content (e.g. binary executable renamed to .txt) → Tika returns
  empty or throws → file moves to `error/`
- Corrupt the file mid-upload (partial content) → Tika fails → `error/`

## Concurrent polling

- Trigger two pipeline runs back-to-back before the first completes → second poll is skipped (`pipelineRunning` guard),
  no double processing
- Watchdog: set `custom.supplier.pipeline-timeout` to a very low value, inject a slow file → watchdog fires,
  `pipelineRunning` reset, next poll runs normally, slow file remains in `processing/`

## Large files

- Upload a large PDF (>100MB) → no OOM, completes successfully, memory usage stays bounded
- Upload a multi-page PDF → all pages indexed, single `file_hash` entry in H2, single archive operation

## moveAsync idempotency

- Manually delete a file from `processing/` then let the archiver try to move it → `NoSuchKeyException` swallowed, no
  error propagation, pipeline continues
- Call `moveAsync` twice with the same source/destination → second call skips copy+delete silently

## Regex filter

- Upload a file with a non-matching extension (e.g. `.xml`) → never enters pipeline, not moved anywhere
- Upload a file with a matching extension but in a subdirectory of the bucket → never appears (delimiter blocks it)

## Pipeline definition

- Misspell a function name in `spring.cloud.function.definition` → clear error log at poll time ("pipeline definition
  not found in catalog"), `pipelineRunning` reset correctly, next poll retries
