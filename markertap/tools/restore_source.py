#!/usr/bin/env python3
"""Reassemble the key-free MarkerTap source archive using SHA-256 verification."""
import argparse, hashlib, json, pathlib, shutil, sys, zipfile

def digest(path):
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default="MarkerTap-v1.117-restored.zip")
    parser.add_argument("--extract-to")
    args = parser.parse_args()
    base = pathlib.Path(__file__).resolve().parent.parent / "archive"
    manifest = json.loads((base / "manifest.json").read_text())
    output = pathlib.Path(args.output).resolve()
    if output.exists():
        raise SystemExit("Output already exists; choose another --output path.")
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = output.with_name(output.name + ".partial")
    if temporary.exists():
        raise SystemExit("Partial output already exists.")
    try:
        with temporary.open("xb") as writer:
            for part in manifest["parts"]:
                name = part["name"]
                if pathlib.PurePath(name).name != name:
                    raise RuntimeError("Unsafe part name")
                source = base / name
                if source.stat().st_size != part["size_bytes"] or digest(source) != part["sha256"]:
                    raise RuntimeError("Part checksum mismatch: " + name)
                with source.open("rb") as reader:
                    shutil.copyfileobj(reader, writer, 1024 * 1024)
        if temporary.stat().st_size != manifest["archive_size_bytes"] or digest(temporary) != manifest["archive_sha256"]:
            raise RuntimeError("Archive checksum mismatch")
        temporary.rename(output)
    except Exception:
        temporary.unlink(missing_ok=True)
        raise
    if args.extract_to:
        target = pathlib.Path(args.extract_to).resolve()
        if target.exists() and any(target.iterdir()):
            raise RuntimeError("Extraction directory must be empty")
        target.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(output) as archive:
            for member in archive.infolist():
                path = pathlib.PurePosixPath(member.filename)
                if path.is_absolute() or ".." in path.parts or "\\" in member.filename:
                    raise RuntimeError("Unsafe archive path")
                archive.extract(member, target)
    print("Verified source archive: " + str(output))

if __name__ == "__main__":
    main()
