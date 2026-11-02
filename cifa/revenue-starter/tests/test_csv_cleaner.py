import csv
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from csv_cleaner import run, main, read_csv, excel_safe, number

CFG = {
    'columns': {'sku': '商品番号', 'name': '商品名', 'price': '価格'},
    'required': ['sku','name','price'], 'numeric_nonnegative': ['price'], 'dedupe_by': ['sku']
}

class CleanerTests(unittest.TestCase):
    def prepare(self, root, text, *, encoding='utf-8-sig'):
        src = root/'raw.csv'
        src.write_text(text, encoding=encoding)
        cfg = root/'config.json'
        cfg.write_text(json.dumps(CFG, ensure_ascii=False), encoding='utf-8')
        return src, cfg, root/'out'

    def test_valid_reject_duplicate_missing_number_and_excel_injection(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            src, cfg, out = self.prepare(root, '商品番号,商品名,価格\n001, りんご ,120\n002,=HYPERLINK(1),"1,200"\n001,りんご,120\n003,バナナ,-10\n004,,30\n005,みかん,0\n')
            report = run(src, cfg, out)
            self.assertEqual((report['rows_in'],report['rows_accepted'],report['rows_rejected']), (6,3,3))
            accepted, _, _, _ = read_csv(out/'cleaned.csv')
            self.assertEqual(accepted[0]['sku'], '001')
            self.assertEqual(accepted[0]['name'], 'りんご')
            self.assertEqual(accepted[1]['name'], "'=HYPERLINK(1)")
            self.assertEqual(accepted[1]['price'], '1200')
            self.assertEqual(accepted[2]['price'], '0')
            rejects, _, _, _ = read_csv(out/'rejected.csv')
            self.assertIn('重複', rejects[0]['reason'])
            self.assertIn('不正な数値', rejects[1]['reason'])
            self.assertIn('必須', rejects[2]['reason'])
            self.assertEqual(json.loads((out/'report.json').read_text())['status'], 'needs_review')

    def test_cp932_input(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d); src,cfg,out=self.prepare(root, '商品番号,商品名,価格\nA,蜜柑,20\n', encoding='cp932')
            self.assertEqual(run(src,cfg,out)['input_encoding'], 'cp932')

    def test_missing_header_fails_closed(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);src,cfg,out=self.prepare(root,'商品番号,商品名\nA,蜜柑\n')
            with self.assertRaisesRegex(ValueError, '入力列'):
                run(src,cfg,out)
            self.assertFalse(out.exists())

    def test_duplicate_header_fails_closed(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);src,cfg,out=self.prepare(root,'商品番号,商品番号,商品名,価格\nA,A,蜜柑,20\n')
            with self.assertRaisesRegex(ValueError, '重複'):
                run(src,cfg,out)

    def test_bad_config_fails_closed(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);src,cfg,out=self.prepare(root,'商品番号,商品名,価格\nA,蜜柑,20\n')
            config = dict(CFG, dedupe_by=['nonexistent'])
            cfg.write_text(json.dumps(config,ensure_ascii=False),encoding='utf-8')
            with self.assertRaisesRegex(ValueError,'列ルール'):
                run(src,cfg,out)

    def test_non_numeric_rejection(self):
        self.assertEqual(number('1,234.50'), '1234.50')
        with self.assertRaises(ValueError): number('1e8')
        with self.assertRaises(ValueError): number('-1')

    def test_excel_injection(self):
        for text in ['=a','+b','-c','@d']:
            self.assertEqual(excel_safe(text), "'"+text)
        self.assertEqual(excel_safe('良品'), '良品')

    def test_cli_error_exit(self):
        self.assertEqual(main(['--input','/no-file','--config','/no-config','--output','/tmp/out']),2)

if __name__ == '__main__':
    unittest.main()
