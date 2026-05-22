"""Print default kij matrices for SRK variants in NeqSim."""
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT / "devtools"))
from neqsim_dev_setup import neqsim_init  # noqa: E402

ns = neqsim_init(project_root=REPO_ROOT)
import jpype  # noqa: E402

EOS_CLASSES = {
    "srk":          jpype.JClass("neqsim.thermo.system.SystemSrkEos"),
    "srk-schwartz": jpype.JClass("neqsim.thermo.system.SystemSrkSchwartzentruberEos"),
    "srk-twu":      jpype.JClass("neqsim.thermo.system.SystemSrkTwuCoonEos"),
}
ThermodynamicOperations = jpype.JClass(
    "neqsim.thermodynamicoperations.ThermodynamicOperations")

comps = ['methane', 'ethane', 'propane', 'i-butane', 'n-butane',
         'i-pentane', 'n-pentane']

for name, cls in EOS_CLASSES.items():
    f = cls(298.15, 1.0)
    for c in comps:
        f.addComponent(c, 0.143)
    f.setMixingRule("classic")
    f.init(0)
    ThermodynamicOperations(f).TPflash()
    mr = f.getPhases()[0].getMixingRule()
    print(f"\n=== {name} ===")
    print('     ' + ''.join(f'{c[:7]:>10}' for c in comps))
    for i, ci in enumerate(comps):
        row = f'{ci[:5]:>5}'
        for j in range(len(comps)):
            v = float(mr.getBinaryInteractionParameter(i, j))
            row += f'{v:+10.5f}'
        print(row)
