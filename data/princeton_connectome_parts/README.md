# Princeton connectome source data — split binary files

These are the original uploaded gzip-compressed source files split into binary parts.
They are **not QR-code images** and are not Base64 files in the repository.

Each part is <= 1 MiB, comfortably below the requested 20 MB limit.

## Reassemble

Run:

```bash
python reassemble.py
```

This recreates:

- `connections_princeton.csv.gz.csv`
- `neurons.csv.gz.csv`

The script verifies every part and the reconstructed file with SHA-256 against `manifest.json`.

The parts must be concatenated in the order recorded in `manifest.json`.
