"""Compare K-values for the column conditions across SRK variants & kij."""
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
# Feed at column reboiler: ~88 C, 5 bara, roughly equimolar iC5/nC5 with traces
z = [0.0, 0.0, 0.0, 0.002, 0.018, 0.42, 0.56]
T_K = 273.15 + 88.0
P_bara = 5.05


def kvals(eos_name, zero_kij):
    f = EOS_CLASSES[eos_name](T_K, P_bara)
    for c in comps:
        f.addComponent(c, 1.0)
    f.setMixingRule("classic")
    f.setMolarComposition(z)
    f.init(0)
    if zero_kij:
        ThermodynamicOperations(f).TPflash()
        mr0 = f.getPhases()[0].getMixingRule()
        for i in range(len(comps)):
            for j in range(len(comps)):
                if i != j:
                    try:
                        mr0.setBinaryInteractionParameter(i, j, 0.0)
                    except Exception:
                        pass
        try:
            mr1 = f.getPhases()[1].getMixingRule()
            for i in range(len(comps)):
                for j in range(len(comps)):
                    if i != j:
                        try:
                            mr1.setBinaryInteractionParameter(i, j, 0.0)
                        except Exception:
                            pass
        except Exception:
            pass
    ThermodynamicOperations(f).TPflash()
    nphases = int(f.getNumberOfPhases())
    if nphases < 2:
        return None, nphases
    # gas phase index
    gas = None
    liq = None
    for p in range(nphases):
        pt = str(f.getPhase(p).getPhaseTypeName())
        if pt == "gas":
            gas = p
        elif pt == "oil" or pt == "liquid":
            liq = p
    if gas is None or liq is None:
        return None, nphases
    K = []
    for i in range(len(comps)):
        y = float(f.getPhase(gas).getComponent(i).getx())
        x = float(f.getPhase(liq).getComponent(i).getx())
        K.append(y / x if x > 1e-30 else float('inf'))
    return K, nphases


print(f"Conditions: T = 88.0 C, P = 5.05 bara, "
      f"feed = iC5 0.42, nC5 0.56, nC4 0.018, iC4 0.002")
print(f"{'eos':<14} {'kij':<8} {'nph':<5} " + "".join(f"{c[:6]:>10}" for c in comps))
for name in EOS_CLASSES:
    for zk_label, zk in [("default", False), ("zero", True)]:
        K, nph = kvals(name, zk)
        prefix = f"{name:<14} {zk_label:<8} {nph:<5} "
        if K is None:
            print(prefix + "  -- SINGLE PHASE --")
        else:
            print(prefix + "".join(f"{k:>10.4f}" for k in K))
