"""Reactive gas/oil/NaCl-KCl brine with native automatic carbonate parameters.

Run with a NeqSim build containing the automatic reactive-carbonate selection.
The dataset reports partial experimental validation; this example does not
establish a fully qualified engineering pH or phase-equilibrium operating range.
"""

from neqsim import jneqsim

fluid = jneqsim.thermo.system.SystemPitzer(313.15, 50.0)
for name, moles in {
    "methane": 5.0,
    "n-heptane": 2.0,
    "CO2": 0.5,
    "water": 55.5,
    "Na+": 0.5,
    "K+": 0.5,
    "Cl-": 1.0,
}.items():
    fluid.addComponent(name, moles)

fluid.chemicalReactionInit()
fluid.setMixingRule("classic")
fluid.setMultiPhaseCheck(True)
jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid).TPflash()
fluid.init(1)

aqueous = fluid.getPhase("aqueous")
aqueous.requireCompletePitzerParameterCoverage()
aqueous.requireCompletePitzerReactionParameterCoverage()
aqueous.requireCompleteNeutralPitzerParameterCoverage()

# Native activity readouts use the 1 mol/kg solute standard state.
pH = float(aqueous.getPitzerPH())

print("Dataset:", aqueous.getParameterDatasetId())
print("Parameter coverage: complete")
print("Reviewed source-zero definitions:", len(aqueous.getReactiveCarbonateSourceZeroDefinitions()))
print("Qualification:", fluid.getPitzerParameterQualification().getLevel())
print("Phases:", [str(fluid.getPhase(i).getType()) for i in range(fluid.getNumberOfPhases())])
print(f"pH (unscaled molality convention): {pH:.6f}")
