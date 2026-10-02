import copy,tempfile,pathlib,unittest,numpy as np
from integrated_rules import ReviewLoop,digest
class IntegratedRulesTest(unittest.TestCase):
 def setUp(self):
  self.loop=ReviewLoop();self.x=np.linspace(-1,1,81);self.y=np.sin(3*self.x)+.3*self.x*self.x;self.v=np.random.default_rng(17).uniform(-1,1,61);self.vy=np.sin(3*self.v)+.3*self.v*self.v
 def candidate(self,name='a'):
  self.loop.propose(name,'learn',self.x,self.y,400);r=self.loop.review(name,self.v,self.vy);self.assertTrue(r['accepted'])
 def test_proposal_isolation_merge_revert_reload(self):
  before=copy.deepcopy(self.loop.state);self.candidate();self.assertEqual(self.loop.state,before);self.loop.merge('a');self.assertNotEqual(self.loop.state,before);spent=self.loop.spent_cost;self.loop.revert(0);self.assertEqual(self.loop.state,before);self.assertEqual(self.loop.spent_cost,spent)
  with tempfile.TemporaryDirectory() as d:
   path=pathlib.Path(d)/'state.json';self.loop.save(path);loaded=ReviewLoop.load(path);self.assertEqual(loaded.state,self.loop.state);self.assertEqual(loaded.history,self.loop.history);self.assertEqual(loaded.spent_cost,self.loop.spent_cost)
 def test_stale_candidates_require_new_review(self):
  self.loop.propose('b','learn',self.x,self.y,400);self.candidate();self.loop.merge('a')
  with self.assertRaisesRegex(ValueError,'stale'):self.loop.review('b',self.v,self.vy)
  with self.assertRaisesRegex(ValueError,'stale'):self.loop.merge('b')
 def test_tampered_or_unreviewed_candidate_blocked(self):
  self.loop.propose('u','learn',self.x,self.y,400)
  with self.assertRaisesRegex(ValueError,'approve'):self.loop.merge('u')
  self.candidate();self.loop.proposals['a']['candidate']['model']['output_bias']+=1
  with self.assertRaisesRegex(ValueError,'changed'):self.loop.merge('a')
 def test_bad_candidate_rejected(self):
  before=digest(self.loop.state);self.loop.propose('bad','learn',self.x,self.y,0);self.loop.proposals['bad']['candidate']['model']['output_bias']=100
  r=self.loop.review('bad',self.v,self.vy);self.assertFalse(r['accepted'])
  with self.assertRaisesRegex(ValueError,'approve'):self.loop.merge('bad')
  self.assertEqual(digest(self.loop.state),before)
 def test_generation_isolated_and_ids_preserved(self):
  p=copy.deepcopy(self.loop.state);grown=self.loop.propose('g','grow',self.x,self.y,0);self.assertEqual(self.loop.state,p);self.assertEqual(grown['model']['ids'][:8],p['model']['ids']);self.assertEqual(len(set(grown['model']['ids'])),16);self.assertTrue(np.allclose(self.loop.metrics(grown,self.x,self.y)['mse'],self.loop.metrics(p,self.x,self.y)['mse']))
if __name__=='__main__':unittest.main()
