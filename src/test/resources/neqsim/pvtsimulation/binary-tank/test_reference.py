"""Independent oracle acceptance gates; run with unittest, including under -O."""
import json
import math
from pathlib import Path
import unittest
import reference as ref


class ReferenceTests(unittest.TestCase):
    """Check equations, invariants and bounded domain before native comparison."""

    def test_cubic_roots(self):
        for a,b,c,expected in [(0,-1,0,[-1,0,1]),(-6,11,-6,[1,2,3])]:
            actual = ref.roots(a,b,c)
            self.assertEqual(len(actual),len(expected))
            for x,y in zip(actual,expected):
                self.assertAlmostEqual(x,y,places=12)

    def test_tie_residual_and_gibbs(self):
        # Both extreme physical cubic roots are checked on a composition grid.
        for t in [320,323.15,330]:
            for p in [65e5,75e5,80e5,90e5]:
                x,y,vl,vg = ref.tie(t,p)
                self.assertGreater(vg,vl)
                fl = ref.phase(t,p,x,True)[1]
                fg = ref.phase(t,p,y,False)[1]
                self.assertLess(max(abs(a-b) for a,b in zip(fl,fg)),2e-9)
                for j in range(1,200):
                    z = j/200
                    tangent = z*fl[0]+(1-z)*fl[1]
                    for liquid in (True,False):
                        chemical = ref.phase(t,p,z,liquid)[1]
                        gibbs = z*chemical[0]+(1-z)*chemical[1]
                        self.assertGreaterEqual(gibbs-tangent,-1e-9)

    def test_conservation_and_retained_snapshot(self):
        actual = ref.generate()
        expected = json.loads(Path(__file__).with_name('reference.json').read_text())
        previous = actual['initial_moles']
        for row,snapshot in zip(actual['rows'],expected['rows']):
            for i in range(2):
                closure = row['retained_moles'][i]+row['produced_moles'][i]-previous[i]-(200,10)[i]
                self.assertLess(abs(closure)/sum(previous),1e-9)
            self.assertAlmostEqual(sum(row['produced_moles']),10000,places=8)
            for key in ['pressure_bara','gor','produced_methane_fraction']:
                self.assertLess(abs(row[key]/snapshot[key]-1),1e-10)
            previous = row['retained_moles']

    def test_phase_volume_initialization(self):
        state = ref.generate()
        volume,beta,x,y = ref.equilibrium(323.15,80e5,state['initial_moles'])
        self.assertLess(abs(volume/100-1),1e-9)
        _,_,vl,vg = ref.tie(323.15,80e5)
        gas_volume = sum(state['initial_moles'])*beta*vg
        self.assertAlmostEqual(gas_volume,70,places=8)
        self.assertAlmostEqual(sum(state['initial_moles'])*(1-beta)*vl,30,places=8)
        self.assertNotAlmostEqual(beta,0.7,places=2)

    def test_laboratory_identifiability(self):
        values = ref.laboratory()
        self.assertLess(abs(values['identified_kij']-0.02),1e-12)
        self.assertEqual(len(values['holdout']),4)
        self.assertNotEqual(values['temperature_k'],values['holdout_temperature_k'])

    def test_invalid_inputs(self):
        for count in (0,-1,1.5,True):
            with self.assertRaises(ValueError):
                ref.generate(count)
        for bad in (float('nan'),float('inf'),-1.0):
            with self.assertRaises(ValueError):
                ref.phase(bad,80e5,0.8,False)

    def test_explicit_domain_failures(self):
        with self.assertRaises(ValueError):
            ref.tie(600,80e5)
        with self.assertRaises(ValueError):
            ref.equilibrium(323.15,80e5,[1e6,1])
        with self.assertRaises(ValueError):
            ref.tv(323.15,[1e6,1e6],1e6,80e5)


if __name__ == '__main__':
    unittest.main()
