"""Characterized oil, sour gas and reactive calcium/sulfate brine.

Requires the native automatic Pitzer parameter-selection build. The petroleum
fractions use their existing EOS and insoluble-Henry treatment; this example
does not qualify hydrocarbon solubility in water or an experimental pH range.
"""

from neqsim import jneqsim


def create_fluid(temperature=313.15, pressure=50.0):
    """Build the feed with temperature in K, pressure in bara and amounts in mol."""
    fluid = jneqsim.thermo.system.SystemPitzer(temperature, pressure)
    for name, moles in {
        "methane": 5.0,
        "ethane": 0.5,
        "propane": 0.25,
        "CO2": 0.5,
        "H2S": 0.02,
    }.items():
        fluid.addComponent(name, moles)

    # TBP and plus-fraction molecular weights are kg/mol; density is g/cm3.
    fluid.addTBPfraction("C7", 0.5, 0.0933, 0.73)
    fluid.addTBPfraction("C8", 0.5, 0.1066, 0.7533)
    fluid.addTBPfraction("C9", 0.3, 0.1196, 0.7653)
    fluid.addPlusFraction("C10", 0.7, 0.281, 0.882888)

    # Keep the three TBP cuts and split C10+ into four additional pseudo components.
    characterization = fluid.getCharacterization()
    characterization.setLumpingModel("PVTlumpingModel")
    characterization.getLumpingModel().setNumberOfLumpedComponents(4)
    characterization.characterisePlusFraction()

    for name, moles in {
        "water": 55.5,
        "Na+": 0.5,
        "K+": 0.5,
        "Ca++": 0.05,
        "Cl-": 1.08,
        "SO4--": 0.01,
    }.items():
        fluid.addComponent(name, moles)

    # Finish characterization before initializing reactions, which capture topology.
    fluid.chemicalReactionInit()
    fluid.createDatabase(True)
    fluid.setMixingRule("classic")
    fluid.setMultiPhaseCheck(True)
    return fluid


def main():
    """Run the characterized-oil reactive flash and print its main results."""
    fluid = create_fluid()
    jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid).TPflash()
    fluid.init(1)
    aqueous = fluid.getPhase("aqueous")
    aqueous.requireCompletePitzerParameterCoverage()
    aqueous.requireCompletePitzerReactionParameterCoverage()
    aqueous.requireCompleteNeutralPitzerParameterCoverage()

    print("Dataset:", aqueous.getParameterDatasetId())
    print("Parameter coverage: complete")
    print("Qualification:", fluid.getPitzerParameterQualification().getLevel())
    print("Phases:", [str(fluid.getPhase(i).getType()) for i in range(fluid.getNumberOfPhases())])
    print(f"pH (unscaled molality convention): {aqueous.getPitzerPH():.6f}")
    print("Pseudo components: name, molecular weight (g/mol), total amount (mol)")
    heavy_mass = 0.0
    for index in range(fluid.getNumberOfComponents()):
        component = aqueous.getComponent(index)
        if component.isIsTBPfraction() or component.isIsPlusFraction():
            amount = sum(
                fluid.getPhase(phase).getComponent(index).getNumberOfMolesInPhase()
                for phase in range(fluid.getNumberOfPhases())
            )
            heavy_mass += sum(
                fluid.getPhase(phase).getComponent(index).getNumberOfMolesInPhase()
                * fluid.getPhase(phase).getComponent(index).getMolarMass()
                for phase in range(fluid.getNumberOfPhases())
            )
            print(f"  {component.getComponentName()}: {1000 * component.getMolarMass():.6f}, {amount:.9f}")
    print(f"Total pseudo-component mass: {heavy_mass:.9f} kg")
    for name in ("Ca++", "SO4--", "HSO4-", "HS-", "HCO3-"):
        component = aqueous.getComponent(name)
        index = component.getComponentNumber()
        print(
            f"{name:6s}: m={component.getMolality(aqueous):.8g} mol/kg, "
            f"gamma={aqueous.getActivityCoefficient(index):.8g}, "
            f"a={aqueous.getPitzerActivity(index):.8g}"
        )


if __name__ == "__main__":
    main()
