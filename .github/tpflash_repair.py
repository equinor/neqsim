from pathlib import Path

# A/B 1: restore the pre-#3026 beta seed only in ordinary TPmultiflash stability.
tp = Path("src/main/java/neqsim/thermodynamicoperations/flashops/TPmultiflash.java")
text = tp.read_text()
old = "system.setBeta(newPhaseIdx, getIncipientWilsonPhaseFraction(dominantComp));"
first = text.find(old)
if first < 0:
    raise RuntimeError("ordinary Wilson beta seed not found")
text = text[:first] + "system.setBeta(newPhaseIdx, system.getPhase(0).getComponent(dominantComp).getz());" + text[first + len(old):]
tp.write_text(text)

# A/B 2: disable the #3026 deserialization reconciliation completely.
st = Path("src/main/java/neqsim/thermo/system/SystemThermo.java")
text = st.read_text()
old = "    reconcileTotalMolesWithComponentInventory();\n"
if text.count(old) != 1:
    raise RuntimeError(f"readObject reconciliation call count={text.count(old)}")
st.write_text(text.replace(old, "    // A/B diagnostic: leave serialized scalar/component inventory untouched.\n"))
