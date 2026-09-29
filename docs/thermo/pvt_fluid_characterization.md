---
title: "PVT fluid characterization"
description: "Build, characterize, lump, and validate petroleum-fluid descriptions with explicit units and executable Java coverage."
---

Fluid characterization converts measured light components, true-boiling-point
(TBP) cuts, and an unresolved heavy-end or plus fraction into the
pseudo-components used by a compositional equation of state. The generated
fluid description is a model input: it still requires comparison with
representative PVT laboratory data before engineering use.

## Input contract and order of operations

Use relative mole amounts for every component. NeqSim normalizes composition
during initialization; the values do not have to sum to one.

| Input | Method | Required basis |
| --- | --- | --- |
| Identified component | \`addComponent(name, moles)\` | Component name from the NeqSim database and a non-negative relative mole amount |
| Pre-binned petroleum cut | \`addTBPfraction(name, moles, molarMass, density)\` | Molar mass in kg/mol; density argument as specific gravity/relative density, numerically equal to g/cm³ |
| Unresolved heavy end | \`addPlusFraction(name, moles, molarMass, density)\` | Molar mass in kg/mol; density argument as specific gravity/relative density, numerically equal to g/cm³ |
| Thermodynamic state | system constructor or setters | Temperature in K and absolute pressure in bara |

The four-argument density methods accept the usual petroleum-cut relative-density
range. Values above 1.5 are interpreted by the API as kg/m³ and converted.
Prefer the documented relative-density basis so an accidental unit change does
not silently change the characterization.

Apply the setup in this order:

1. create the equation-of-state system at the intended temperature and pressure;
2. select the TBP correlation before adding TBP or plus fractions;
3. add identified components, TBP cuts, and one unresolved plus fraction;
4. select the plus-fraction and lumping models;
5. call \`characterisePlusFraction()\`;
6. set the mixing rule, flash the characterized system, and inspect the result.

## Characterization and lumping routes

\`setPlusFractionModel("Pedersen")\` distributes the unresolved heavy end into
single-carbon-number components using the selected TBP correlation.
\`characterisePlusFraction()\` then applies the configured lumping model.

| Lumping route | Fluent configuration | Meaning |
| --- | --- | --- |
| Preserve the light TBP cuts and lump only the heavy end | \`.model("PVTlumpingModel").plusFractionGroups(n)\` | Keeps the explicit C6-C9 TBP cuts and creates \`n\` groups from C10+ |
| Lump all heavy fractions from C6 | \`.model("standard").totalPseudoComponents(n)\` | Creates \`n\` total heavy pseudo-components |
| Retain every generated SCN component | \`.noLumping()\` | Highest component count; useful for diagnosis rather than routine simulation |
| Match project-owned carbon-number bins | \`.customBoundaries(...)\` | Uses the supplied starting carbon number for each group |

Do not use \`plusFractionGroups\` and \`totalPseudoComponents\` interchangeably.
Their counts have different meanings. The final component slate also contains
the identified components and any TBP cuts preserved by the selected route.

## Executable characterized-fluid example

The program below uses the public fluent lumping API, performs a TP flash, and
checks the resulting component count and phase split. It is one complete Java 8
program and uses Log4j2 instead of console output. Run documentation examples
with assertions enabled (\`java -ea\`) so the engineering checks execute.

\`\`\`java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public final class PvtFluidCharacterizationExample {
  private static final Logger logger =
      LogManager.getLogger(PvtFluidCharacterizationExample.class);

  private PvtFluidCharacterizationExample() {}

  public static void main(String[] args) {
    double temperatureK = 298.0;
    double pressureBara = 10.0;

    SystemInterface fluid = new SystemSrkEos(temperatureK, pressureBara);
    fluid.getCharacterization().setTBPModel("PedersenSRK");

    fluid.addComponent("CO2", 1.0);
    fluid.addComponent("methane", 51.0);
    fluid.addComponent("ethane", 1.0);
    fluid.addComponent("propane", 1.0);

    // name, relative moles, molar mass [kg/mol], relative density [-]
    fluid.addTBPfraction("C6", 1.0, 0.090, 0.70);
    fluid.addTBPfraction("C7", 1.0, 0.110, 0.73);
    fluid.addTBPfraction("C8", 1.0, 0.120, 0.76);
    fluid.addTBPfraction("C9", 1.0, 0.140, 0.79);
    fluid.addPlusFraction("C10", 11.0, 0.290, 0.82);

    fluid.getCharacterization().setPlusFractionModel("Pedersen");
    fluid.getCharacterization().configureLumping()
        .model("PVTlumpingModel")
        .plusFractionGroups(9)
        .build();
    fluid.getCharacterization().characterisePlusFraction();
    fluid.setMixingRule("classic");

    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    operations.TPflash();

    double vaporMoleFraction = fluid.getBeta();
    assert fluid.getNumberOfComponents() == 17;
    assert Double.isFinite(vaporMoleFraction);
    assert vaporMoleFraction > 0.0 && vaporMoleFraction < 1.0;

    logger.info(
        "Characterized {} components at {} K and {} bara; vapor mole fraction={}",
        fluid.getNumberOfComponents(),
        temperatureK,
        pressureBara,
        vaporMoleFraction);
  }
}
\`\`\`

The 17-component assertion closes the intended slate: four identified
components, four preserved C6-C9 TBP cuts, and nine groups created from C10+.
It is a regression check for this stated example, not a universal component
count for other assays or lumping choices.

## Tuning, experiments, and reported properties

Characterization does not tune an equation of state. Match uncertain
plus-fraction properties, binary-interaction parameters, volume translation,
and viscosity parameters against project-owned measurements using a documented
objective function, bounds, weights, and holdout data. Preserve the raw assay,
unit conversions, model names, fitted parameters, software version, and
residuals with the fluid description.

Do not calculate oil formation-volume factor or gas-oil ratio from a phase
volume divided by total feed moles. Those quantities require named stock-tank
and separator conditions plus an explicit standard-volume basis. Use the
dedicated CCE, CVD, differential-liberation, separator, and viscosity workflows,
and record every pressure as absolute or gauge and every gas/oil standard
condition.

## Engineering boundaries

The Pedersen and Whitson-style characterization routes are correlations, not
measurements. Results can be sensitive to plus-fraction molar mass and density,
TBP correlation, number and boundaries of lumped groups, equation of state,
mixing rule, and volume translation.

Before relying on a characterized fluid:

1. validate the input assay, composition closure, units, and sample provenance;
2. compare saturation pressure, density, relative volume, liquid dropout,
   separator yields, and viscosity with representative laboratory data;
3. rerun sensitivity cases for plausible heavy-end and lumping choices;
4. freeze the exact component slate and fitted parameters used downstream; and
5. obtain independent PVT/model review for reserves, facilities, flow assurance,
   custody, or safety-critical decisions.

Asphaltene, wax, hydrate, electrolyte, and solid-phase behavior require their
own qualified models and data. A characterized hydrocarbon slate does not
establish those risks.

## Related documentation

- [Characterization package and route selection](characterization/README.md)
- [Fluid-characterization mathematics](../pvtsimulation/fluid_characterization_mathematics.md)
- [PVT laboratory simulations](../pvtsimulation/pvt_lab_tests.md)
- [Thermodynamic workflows](thermodynamic_workflows.md)
- [Fluid creation](fluid_creation_guide.md)
