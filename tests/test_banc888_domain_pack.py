import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("banc888_domain_pack", ROOT/"tools"/"banc888_domain_pack.py")
mod = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(mod)

class DomainPackTests(unittest.TestCase):
    def test_domain_count(self):
        self.assertEqual(len(mod.DOMAINS), 9)

    def test_concept_count(self):
        self.assertEqual(sum(len(x["concepts"]) for x in mod.DOMAINS.values()), 45)

    def test_each_domain_has_eight_groups(self):
        for name, data in mod.DOMAINS.items():
            self.assertEqual(len(data["groups"]), 8, name)
            self.assertEqual(len(set(data["groups"])), 8, name)

    def test_default_job_count(self):
        jobs = mod.build_jobs()
        self.assertEqual(len(jobs), 2880)

    def test_unique_ids_and_filenames(self):
        jobs = mod.build_jobs()
        self.assertEqual(len(jobs), len({j["id"] for j in jobs}))
        self.assertEqual(len(jobs), len({j["filename"] for j in jobs}))

    def test_required_evaluation_fields(self):
        required = {
            "id","domain","subcategory","concept","group","prompt","negative_prompt",
            "focus_tags","compare_targets","repair_targets","seed","cfg_scale","steps",
            "sampler_name","scheduler","width","height","endpoint","filename","compare_against"
        }
        for job in mod.build_jobs():
            self.assertTrue(required.issubset(job), job["id"])
            self.assertTrue(job["focus_tags"], job["id"])
            self.assertTrue(job["compare_targets"], job["id"])
            self.assertTrue(job["repair_targets"], job["id"])

    def test_o2_match_exists_for_every_concept(self):
        jobs = mod.build_jobs()
        concepts={(j["domain"],j["subcategory"],j["concept"]) for j in jobs}
        matched={(j["domain"],j["subcategory"],j["concept"]) for j in jobs if j["group"]=="O2_MATCH"}
        self.assertEqual(concepts, matched)

    def test_validator_passes(self):
        self.assertEqual(mod.validate(mod.build_jobs()), [])

if __name__ == "__main__":
    unittest.main(verbosity=2)
