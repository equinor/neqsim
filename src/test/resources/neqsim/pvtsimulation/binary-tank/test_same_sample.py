"""Frozen same-sample invariants and independently rooted stability controls."""
import hashlib
import json
import math
from pathlib import Path
import unittest
import reference as ref
import same_sample
import test_stability as stability


def diagnose():
    """Qualify all tank endpoints, surface and withheld closed-cell states."""
    closed = [(323.15,p,[0.8,0.2]) for p in (80e5,70e5,60e5,50e5)]
    result = stability.diagnose(same_sample.initial_moles(),closed)
    result['schema'] = 'binary_pr_same_sample_stability.v1'
    result['initial_component_moles'] = same_sample.initial_moles()
    folder = Path(__file__).resolve().parent
    result['same_sample_source_sha256'] = hashlib.sha256((folder/'same_sample.py').read_bytes()).hexdigest()
    result['same_sample_test_sha256'] = hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
    return result


class SameSampleTests(unittest.TestCase):
    """Numerical bounds are frozen in SAME_SAMPLE_SCOPE.md before native comparison."""

    def test_initial_composition_and_volume(self):
        values = same_sample.generate()
        n = values['initial_moles']
        volume,beta,x,y = ref.equilibrium(323.15,80e5,n)
        self.assertLess(abs(n[0]/sum(n)-0.8),1e-10)
        self.assertLess(abs(volume/100-1),1e-9)
        fg,fo,fw = values['initial_phase_volume_fractions']
        _,_,vl,vg = ref.tie(323.15,80e5)
        self.assertAlmostEqual(fg*100/vg*y+fo*100/vl*x,n[0],places=7)
        self.assertAlmostEqual(fg*100/vg*(1-y)+fo*100/vl*(1-x),n[1],places=7)
        self.assertEqual(fw,0)
        self.assertGreater(abs(fg-beta),0.01)
        self.assertGreater(abs(fg-0.7),0.01)

    def test_closed_mass_whole_experiment_holdout(self):
        lab = same_sample.generate()['laboratory']
        self.assertLess(abs(lab['identified_kij']-0.02),1e-12)
        self.assertEqual(len(lab['two_phase_holdout']),4)
        previous_volume = 0
        for row in lab['two_phase_holdout']:
            beta = row['gas_mole_fraction']
            x,y = row['liquid_methane_fraction'],row['vapor_methane_fraction']
            self.assertTrue(0 < beta < 1)
            self.assertLess(abs((1-beta)*x+beta*y-0.8),1e-10)
            self.assertGreater(row['molar_volume_m3'],previous_volume)
            previous_volume = row['molar_volume_m3']

    def test_independent_all_root_tangent(self):
        result = diagnose()
        self.assertEqual(result['state_count'],22)
        self.assertGreater(result['physical_root_checks'],22000)
        self.assertGreaterEqual(result['minimum_gibbs_gap'],-1e-9)

    def test_schedule_conservation_and_retained_data(self):
        actual = same_sample.generate()
        expected = json.loads(Path(__file__).with_name('same-sample-reference.json').read_text())
        previous = actual['initial_moles']
        for row,snapshot in zip(actual['rows'],expected['rows']):
            for i in range(2):
                self.assertLess(abs(row['retained_moles'][i]+row['produced_moles'][i]
                                    -previous[i]-(200,10)[i])/sum(previous),1e-9)
            for key in ('pressure_bara','gor','produced_methane_fraction'):
                self.assertTrue(math.isfinite(row[key]))
                self.assertLess(abs(row[key]/snapshot[key]-1),1e-10)
            previous = row['retained_moles']

    def test_replay_initial_admission(self):
        for amounts in ([1,1],[0,1],[-1,1],[float('nan'),1],[float('inf'),1],[]):
            with self.subTest(amounts=amounts), self.assertRaises(ValueError):
                ref.replay(amounts)


if __name__ == '__main__':
    unittest.main()
