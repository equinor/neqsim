"""Check the feed enthalpy & phase state across SRK variants."""
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

# Main feed: pure ethane, 77 C, 4.2 barG = 5.213 bara, 1059.4 kmol/hr
# Top  feed: equimolar C1..nC5, 32.14 C, 3.7 barG = 4.713 bara, 1000.0 kmol/hr

print("\n--- MAIN FEED: pure ethane @ 77 C, 5.213 bara, 1059.4 kmol/hr ---")
for name, cls in EOS_CLASSES.items():
    f = cls(273.15 + 77.0, 5.213)
    for i, c in enumerate(comps):
        f.addComponent(c, 1059.4 / 3600.0 if c == "ethane" else 1e-10)
    f.setMixingRule("classic")
    f.init(0)
    ThermodynamicOperations(f).TPflash()
    nph = int(f.getNumberOfPhases())
    f.initProperties()
    H = float(f.getEnthalpy())   # J/sec at mol/sec flow
    Hm = float(f.getEnthalpy()) / float(f.getTotalNumberOfMoles())  # J/mol
    T = float(f.getTemperature())
    P = float(f.getPressure())
    types = "+".join(str(f.getPhase(p).getPhaseTypeName()) for p in range(nph))
    print(f"  {name:<14} nph={nph} ({types})  T={T-273.15:.2f}C  P={P:.3f}bara"
          f"  H={H:+.3e} J/s  H_m={Hm:+.3e} J/mol")

print("\n--- TOP FEED: equimolar C1..nC5 @ 32.14 C, 4.713 bara, 1000 kmol/hr ---")
for name, cls in EOS_CLASSES.items():
    f = cls(273.15 + 32.14, 4.713)
    for i, c in enumerate(comps):
        f.addComponent(c, 1000.0 / 3600.0 / 7.0)
    f.setMixingRule("classic")
    f.init(0)
    ThermodynamicOperations(f).TPflash()
    nph = int(f.getNumberOfPhases())
    f.initProperties()
    H = float(f.getEnthalpy())
    Hm = float(f.getEnthalpy()) / float(f.getTotalNumberOfMoles())
    if nph >= 2:
        beta = float(f.getPhase(0).getNumberOfMolesInPhase()
                     ) / float(f.getTotalNumberOfMoles())
    else:
        beta = 1.0 if str(f.getPhase(0).getPhaseTypeName()) == "gas" else 0.0
    types = "+".join(str(f.getPhase(p).getPhaseTypeName()) for p in range(nph))
    print(f"  {name:<14} nph={nph} ({types})  beta_V={beta:.4f}"
          f"  H={H:+.3e} J/s  H_m={Hm:+.3e} J/mol")
