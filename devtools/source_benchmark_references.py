"""Source independent reference values for the NeqSim agentic benchmark suite.

Run once to regenerate the numbers cited in AgentBenchmarkSuite.createStandardSuite().
Reference EOS come from CoolProp's HEOS backend (Setzmann-Wagner methane,
Span-Wagner CO2, IAPWS-95 water), which outranks the SRK cubic under test.

Requires CoolProp, which is NOT needed to run the benchmark itself - only to
regenerate its reference values:
    <python-executable> -m pip install CoolProp
    <python-executable> devtools/source_benchmark_references.py
"""
import CoolProp
from CoolProp.CoolProp import PropsSI


def report(label, value, unit):
    print("{:<46s} {:>14.5g} {}".format(label, value, unit))


print("CoolProp", CoolProp.__version__)
print()

# 1. Methane density at 300 K, 50 bar - Setzmann & Wagner (1991)
report("methane density 300 K / 50 bara",
       PropsSI("D", "T", 300.0, "P", 50e5, "Methane"), "kg/m3")

# 2. CO2 density at 310 K, 100 bar - Span & Wagner (1996)
report("CO2 density 310 K / 100 bara",
       PropsSI("D", "T", 310.0, "P", 100e5, "CO2"), "kg/m3")

# 3. Water normal boiling point at 1.01325 bar - IAPWS-95
report("water saturation T at 1.01325 bara",
       PropsSI("T", "P", 101325.0, "Q", 0.0, "Water"), "K")

# 4. Compressor duty: methane 100 kg/hr, 300 K, 30 -> 100 bara.
#    Isentropic enthalpy rise from CoolProp, converted to polytropic shaft work
#    with a declared polytropic efficiency and the Schultz-style ratio.
m_dot = 100.0 / 3600.0  # kg/s
p1, p2, t1 = 30e5, 100e5, 300.0
h1 = PropsSI("H", "T", t1, "P", p1, "Methane")
s1 = PropsSI("S", "T", t1, "P", p1, "Methane")
h2s = PropsSI("H", "P", p2, "S", s1, "Methane")
eta_s = 0.75
w_isentropic_kw = m_dot * (h2s - h1) / 1000.0
report("methane isentropic head 30->100 bara", (h2s - h1) / 1000.0, "kJ/kg")
report("shaft power at eta_s = 0.75", w_isentropic_kw / eta_s, "kW")

# 5. Cricondentherm of 85/10/5 methane/ethane/propane
try:
    state = CoolProp.AbstractState("HEOS", "Methane&Ethane&Propane")
    state.set_mole_fractions([0.85, 0.10, 0.05])
    state.build_phase_envelope("")
    env = state.get_phase_envelope_data()
    cct = max(env.T)
    cct_p = env.p[list(env.T).index(cct)] / 1e5
    ccb = max(env.p) / 1e5
    ccb_t = env.T[list(env.p).index(max(env.p))]
    report("cricondentherm (85/10/5 C1/C2/C3)", cct, "K")
    report("  at pressure", cct_p, "bara")
    report("cricondenbar", ccb, "bara")
    report("  at temperature", ccb_t, "K")
except Exception as exc:
    print("phase envelope failed:", exc)
