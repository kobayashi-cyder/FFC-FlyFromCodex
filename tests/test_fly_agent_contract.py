import importlib.util
import json
import subprocess
import sys
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location('fly_agent_toolbus', ROOT/'tools'/'fly_agent_toolbus.py')
mod = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = mod
SPEC.loader.exec_module(mod)

class SharedContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.contract=json.loads((ROOT/'contracts'/'fly_agent_toolbus_v0_2.json').read_text())
        cls.expected=cls.contract['tools']

    def normalized(self, rows):
        keys=('name','capability','side_effect','required_args','optional_args')
        return [{k:r[k] for k in keys} for r in sorted(rows,key=lambda x:x['name'])]

    def test_python_manifest_matches_contract(self):
        self.assertEqual(self.normalized(mod.FlyAgentToolBus().manifest()), self.normalized(self.expected))

    def test_js_manifest_matches_contract(self):
        raw=subprocess.check_output(['node', str(ROOT/'tools'/'fly_agent_toolbus_browser.js'), '--manifest'], text=True)
        self.assertEqual(self.normalized(json.loads(raw)), self.normalized(self.expected))

    def test_python_idempotency_and_delete(self):
        bus=mod.FlyAgentToolBus()
        first=bus.execute('memory.put',{'key':'x','value':1},idempotency_key='same')
        second=bus.execute('memory.put',{'key':'x','value':2},idempotency_key='same')
        self.assertTrue(second['replayed'])
        self.assertEqual(bus.execute('memory.get',{'key':'x'})['value'],1)
        deleted=bus.execute('memory.delete',{'key':'x'})['value']
        self.assertTrue(deleted['existed'])

if __name__=='__main__': unittest.main(verbosity=2)
