#!/usr/bin/env python3
"""CSV mapping, validation and audit tool; standard library only."""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import re
import sys
import unicodedata
from decimal import Decimal, InvalidOperation
from pathlib import Path


def read_csv(path: Path):
    raw = path.read_bytes()
    encoding = 'utf-8-sig'
    try:
        content = raw.decode(encoding)
    except UnicodeDecodeError:
        encoding = 'cp932'
        content = raw.decode(encoding)
    reader = csv.DictReader(content.splitlines(keepends=True))
    if not reader.fieldnames:
        raise ValueError('CSVにヘッダーがありません')
    if None in reader.fieldnames or len(set(reader.fieldnames)) != len(reader.fieldnames):
        raise ValueError('空欄または重複したヘッダーがあります')
    return list(reader), reader.fieldnames, encoding, hashlib.sha256(raw).hexdigest()


def normalize(value: str, nfkc: bool):
    value = value.strip()
    if nfkc:
        value = unicodedata.normalize('NFKC', value)
    return re.sub(r'\s+', ' ', value)


def number(value: str):
    if not re.fullmatch(r'(?:\d+|\d{1,3}(?:,\d{3})+)(?:\.\d+)?', value):
        raise ValueError('不正な数値形式')
    try:
        n = Decimal(value.replace(',', ''))
    except InvalidOperation as exc:
        raise ValueError('数値を解釈できません') from exc
    if not n.is_finite() or n < 0:
        raise ValueError('数値が負数または非有限です')
    return format(n, 'f')


def excel_safe(value: str) -> str:
    # Spreadsheet clients may interpret these prefixes as formulas.
    return "'" + value if value.lstrip().startswith(('=', '+', '-', '@')) else value


def run(input_path: Path, config_path: Path, output_dir: Path):
    cfg = json.loads(config_path.read_text(encoding='utf-8'))
    columns = cfg['columns']  # {output: input}
    if not isinstance(columns, dict) or not columns or not all(isinstance(k, str) and isinstance(v, str) for k, v in columns.items()):
        raise ValueError('columnsは空でない出力列→入力列の辞書が必要です')
    outnames = list(columns)
    required = cfg.get('required', [])
    numeric = cfg.get('numeric_nonnegative', [])
    dedupe = cfg.get('dedupe_by', [])
    if not all(isinstance(group, list) and set(group) <= set(outnames) for group in (required, numeric, dedupe)):
        raise ValueError('列ルールは出力列名で指定してください')
    rows, headers, encoding, digest = read_csv(input_path)
    missing = sorted(set(columns.values()) - set(headers))
    if missing:
        raise ValueError('入力列がありません: ' + ', '.join(missing))
    if output_dir.resolve() == input_path.parent.resolve():
        raise ValueError('入力CSVと出力先は異なるディレクトリにしてください')
    passed, rejected, seen = [], [], set()
    for line_num, src in enumerate(rows, start=2):
        if None in src:
            rejected.append({'source_row': line_num, 'reason': 'ヘッダーより列数が多い', **{k: '' for k in outnames}})
            continue
        dst = {out: normalize(src.get(inn) or '', bool(cfg.get('nfkc', False))) for out, inn in columns.items()}
        errors = []
        for col in required:
            if not dst[col]:
                errors.append(f'{col}:必須項目が空欄')
        for col in numeric:
            if not dst[col] and col not in required:
                continue
            if dst[col]:
                try:
                    dst[col] = number(dst[col])
                except ValueError as exc:
                    errors.append(f'{col}:{exc}')
        if dedupe and not errors:
            key = tuple(dst[k] for k in dedupe)
            if key in seen:
                errors.append('重複キー:' + '/'.join(dedupe))
            else:
                seen.add(key)
        if errors:
            rejected.append({'source_row': line_num, 'reason': '; '.join(errors), **{k: excel_safe(v) if k not in numeric else v for k, v in dst.items()}})
        else:
            passed.append({k: excel_safe(v) if k not in numeric else v for k, v in dst.items()})
    output_dir.mkdir(parents=True, exist_ok=True)
    def write_csv(filename, fields, records):
        with (output_dir / filename).open('w', encoding='utf-8-sig', newline='') as f:
            writer = csv.DictWriter(f, fieldnames=fields, extrasaction='ignore')
            writer.writeheader()
            writer.writerows(records)
    write_csv('cleaned.csv', outnames, passed)
    write_csv('rejected.csv', ['source_row', 'reason'] + outnames, rejected)
    report = {
        'schema_version': '1.0', 'input_sha256': digest, 'input_encoding': encoding,
        'rows_in': len(rows), 'rows_accepted': len(passed), 'rows_rejected': len(rejected),
        'status': 'needs_review' if rejected else 'ok',
        'stages': ['read', 'map', 'validate', 'deduplicate', 'export', 'audit'],
        'manual_review_required': bool(rejected),
    }
    (output_dir / 'report.json').write_text(json.dumps(report, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    return report


def main(argv=None):
    p = argparse.ArgumentParser(description='CSV列変換・重複/必須/数値検査・監査結果出力')
    p.add_argument('--input', type=Path, required=True)
    p.add_argument('--config', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    args = p.parse_args(argv)
    try:
        result = run(args.input, args.config, args.output)
        print(json.dumps(result, ensure_ascii=False, indent=2))
    except (ValueError, OSError, KeyError, UnicodeError, json.JSONDecodeError) as exc:
        print(f'ERROR: {exc}', file=sys.stderr)
        return 2
    return 0


if __name__ == '__main__':
    sys.exit(main())
