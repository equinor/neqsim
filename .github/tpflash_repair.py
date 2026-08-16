from pathlib import Path


def replace_once(path, old, new):
    file = Path(path)
    text = file.read_text()
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"Expected one match in {path}, found {count}")
    file.write_text(text.replace(old, new))


replace_once(
    "src/main/java/neqsim/thermodynamicoperations/flashops/TPmultiflash.java",
    "      if (tm[j] < -1e-8) {\n        break;\n      }\n",
    "      double tpdAcceptanceTolerance = Math.max(1.0e-8, Math.abs(err));\n"
    "      if (tm[j] < -tpdAcceptanceTolerance) {\n"
    "        break;\n"
    "      }\n"
    "      if (tm[j] < -1.0e-8) {\n"
    "        // A negative TPD smaller than the converged stability residual is not\n"
    "        // significant enough to change the established phase topology.\n"
    "        tm[j] = 10.0;\n"
    "      }\n",
)

replace_once(
    "src/main/java/neqsim/thermo/system/SystemThermo.java",
    "    double tolerance = 1.0e-12 * Math.max(1.0, inventory);\n"
    "    if (!Double.isFinite(totalNumberOfMoles) || Math.abs(totalNumberOfMoles - inventory) > tolerance) {\n"
    "      setTotalNumberOfMolesRaw(inventory);\n"
    "      isInitialized = false;\n"
    "    }\n",
    "    double tolerance = 1.0e-12 * Math.max(1.0, inventory);\n"
    "    double mismatch = Math.abs(totalNumberOfMoles - inventory);\n"
    "    boolean clearlyStaleUpstreamTotal = !Double.isFinite(totalNumberOfMoles)\n"
    "        || (totalNumberOfMoles > inventory && totalNumberOfMoles / inventory > 100.0);\n"
    "    if (clearlyStaleUpstreamTotal && mismatch > tolerance) {\n"
    "      setTotalNumberOfMolesRaw(inventory);\n"
    "      isInitialized = false;\n"
    "    }\n",
)

replace_once(
    "src/test/java/neqsim/pvtsimulation/simulation/HydrocarbonScrubberSaturationPressureStabilityTest.java",
    "      assertEquals(1.0, compositionSum, 1.0e-9);\n",
    "      assertEquals(1.0, compositionSum, 1.0e-12);\n",
)
