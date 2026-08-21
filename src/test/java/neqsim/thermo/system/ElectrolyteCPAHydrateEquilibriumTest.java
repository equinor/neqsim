package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Regression tests for hydrate equilibrium with non-reactive and reactive electrolyte CPA fluids. */
@Tag("slow")
public class ElectrolyteCPAHydrateEquilibriumTest extends neqsim.NeqSimTest {
  private static final double MATERIAL_BALANCE_TOLERANCE = 1.0e-9;
  private static final double CHARGE_BALANCE_TOLERANCE = 2.0e-9;

  /**
   * Verifies gas-water-brine hydrate equilibrium and salt inhibition.
   *
   * @throws Exception if the hydrate calculation fails
   */
  @Test
  @DisplayName("electrolyte CPA hydrate equilibrium for gas, water, and salt")
  public void testGasWaterSaltHydrateEquilibrium() throws Exception {
    SystemInterface freshWater = createBaseFluid();
    double freshWaterHydrateTemperature = calculateHydrateTemperature(freshWater);

    SystemInterface brine = createBaseFluid();
    addSalt(brine);
    double brineHydrateTemperature = calculateHydrateTemperature(brine);

    assertTrue(brineHydrateTemperature < freshWaterHydrateTemperature,
        "NaCl must depress the hydrate equilibrium temperature");
    assertTrue(brine.hasPhaseType(PhaseType.GAS));
    assertTrue(brine.hasPhaseType(PhaseType.AQUEOUS));
    assertElectrolytePhaseInventory(brine);
  }

  /**
   * Verifies a material gas-oil-water-ion split at hydrate equilibrium.
   *
   * @throws Exception if the hydrate calculation fails
   */
  @Test
  @DisplayName("electrolyte CPA hydrate equilibrium for gas, oil, water, and ions")
  public void testGasOilWaterIonHydrateEquilibrium() throws Exception {
    SystemInterface fluid = createBaseFluid();
    fluid.addComponent("n-hexane", 0.02);
    fluid.addComponent("n-heptane", 0.02);
    fluid.addComponent("n-octane", 0.01);
    addSalt(fluid);

    double hydrateTemperature = calculateHydrateTemperature(fluid);

    assertTrue(Double.isFinite(hydrateTemperature));
    assertTrue(fluid.hasPhaseType(PhaseType.GAS));
    assertTrue(fluid.hasPhaseType(PhaseType.OIL));
    assertTrue(fluid.hasPhaseType(PhaseType.AQUEOUS));
    assertTrue(fluid.getPhase(PhaseType.OIL).getBeta() > 1.0e-4,
        "The hydrocarbon liquid phase must have a material phase fraction");
    assertElectrolytePhaseInventory(fluid);
  }

  /**
   * Verifies combined MEG and salt inhibition in a gas-aqueous hydrate calculation.
   *
   * @throws Exception if the hydrate calculation fails
   */
  @Test
  @DisplayName("electrolyte CPA hydrate equilibrium for gas, aqueous inhibitor, and salt")
  public void testGasWaterInhibitorSaltHydrateEquilibrium() throws Exception {
    SystemInterface megOnly = createBaseFluid();
    megOnly.addComponent("MEG", 0.03);
    double megOnlyHydrateTemperature = calculateHydrateTemperature(megOnly);

    SystemInterface inhibitedBrine = createBaseFluid();
    inhibitedBrine.addComponent("MEG", 0.03);
    addSalt(inhibitedBrine);
    double inhibitedBrineHydrateTemperature = calculateHydrateTemperature(inhibitedBrine);

    assertTrue(inhibitedBrineHydrateTemperature < megOnlyHydrateTemperature,
        "NaCl and MEG together must inhibit hydrate more than MEG alone");
    assertTrue(inhibitedBrine.hasPhaseType(PhaseType.GAS));
    assertTrue(inhibitedBrine.hasPhaseType(PhaseType.AQUEOUS));
    assertElectrolytePhaseInventory(inhibitedBrine);
  }

  /**
   * Verifies coupled CO2-water chemical equilibrium during a brine hydrate-temperature calculation.
   *
   * @throws Exception if reaction, phase, or hydrate equilibrium fails
   */
  @Test
  @DisplayName("electrolyte CPA hydrate equilibrium with CO2-water reactions")
  public void testReactiveCo2WaterSaltHydrateEquilibrium() throws Exception {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(283.15, 100.0);
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("CO2", 0.05);
    fluid.addComponent("water", 0.23);
    addSalt(fluid);
    fluid.chemicalReactionInit();
    fluid.createDatabase(true);
    fluid.setMultiPhaseCheck(true);

    double initialCarbonMoles = getOverallComponentMoles(fluid, "CO2")
        + getOverallComponentMoles(fluid, "HCO3-") + getOverallComponentMoles(fluid, "CO3--");
    double initialSodiumMoles = getOverallComponentMoles(fluid, "Na+");
    double initialChlorideMoles = getOverallComponentMoles(fluid, "Cl-");

    double hydrateTemperature = calculateHydrateTemperature(fluid);

    assertTrue(Double.isFinite(hydrateTemperature));
    assertTrue(fluid.hasPhaseType(PhaseType.GAS));
    assertTrue(fluid.hasPhaseType(PhaseType.AQUEOUS));
    assertTrue(fluid.isChemicalSystem());
    assertTrue(fluid.getChemicalReactionOperations().hasReactions());
    assertTrue(getRecoveredComponentMoles(fluid, "HCO3-") > 1.0e-10,
        "Dissolved CO2 chemistry must produce bicarbonate");
    assertEquals(initialCarbonMoles,
        getRecoveredComponentMoles(fluid, "CO2") + getRecoveredComponentMoles(fluid, "HCO3-")
            + getRecoveredComponentMoles(fluid, "CO3--"),
        1.0e-8, "Carbon atoms must be conserved across phase and chemical equilibrium");
    assertEquals(initialSodiumMoles, getRecoveredComponentMoles(fluid, "Na+"), 1.0e-9,
        "Sodium inventory must be conserved");
    assertEquals(initialChlorideMoles, getRecoveredComponentMoles(fluid, "Cl-"), 1.0e-9,
        "Chloride inventory must be conserved");
    assertElectrolytePhaseInventory(fluid);
  }

  private static SystemInterface createBaseFluid() {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(283.15, 100.0);
    fluid.addComponent("methane", 0.75);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("propane", 0.03);
    fluid.addComponent("water", 0.12);
    return fluid;
  }

  private static void addSalt(SystemInterface fluid) {
    fluid.addComponent("Na+", 0.01);
    fluid.addComponent("Cl-", 0.01);
  }

  private static double calculateHydrateTemperature(SystemInterface fluid) throws Exception {
    fluid.setMixingRule(10);
    fluid.setHydrateCheck(true);
    new ThermodynamicOperations(fluid).hydrateFormationTemperature();
    double hydrateTemperature = fluid.getTemperature("C");
    assertTrue(Double.isFinite(hydrateTemperature), "Hydrate equilibrium temperature must be finite");
    assertTrue(hydrateTemperature > -50.0 && hydrateTemperature < 40.0,
        "Hydrate equilibrium temperature is outside the supported engineering range");
    return hydrateTemperature;
  }

  private static double getOverallComponentMoles(SystemInterface fluid, String componentName) {
    return fluid.getPhase(0).hasComponent(componentName)
        ? fluid.getPhase(0).getComponent(componentName).getNumberOfmoles()
        : 0.0;
  }

  private static double getRecoveredComponentMoles(SystemInterface fluid, String componentName) {
    if (!fluid.getPhase(0).hasComponent(componentName)) {
      return 0.0;
    }
    double overallFraction = 0.0;
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      overallFraction += fluid.getBeta(phase) * fluid.getPhase(phase).getComponent(componentName).getx();
    }
    return fluid.getNumberOfMoles() * overallFraction;
  }

  private static void assertElectrolytePhaseInventory(SystemInterface fluid) {
    int aqueousPhase = fluid.getPhaseNumberOfPhase("aqueous");
    double aqueousCharge = 0.0;

    for (int component = 0; component < fluid.getPhase(0).getNumberOfComponents(); component++) {
      double recoveredComposition = 0.0;
      for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
        double phaseComposition = fluid.getPhase(phase).getComponent(component).getx();
        recoveredComposition += fluid.getBeta(phase) * phaseComposition;
        if (fluid.getPhase(phase).getType() != PhaseType.AQUEOUS
            && fluid.getPhase(phase).getComponent(component).getIonicCharge() != 0) {
          assertTrue(phaseComposition < 1.0e-20, "Ions must be confined to the aqueous phase");
        }
      }

      double overallComposition = fluid.getPhase(0).getComponent(component).getz();
      assertEquals(overallComposition, recoveredComposition, MATERIAL_BALANCE_TOLERANCE,
          "Component inventory must close for " + fluid.getPhase(0).getComponent(component).getComponentName());

      double charge = fluid.getPhase(aqueousPhase).getComponent(component).getIonicCharge();
      aqueousCharge += charge * fluid.getPhase(aqueousPhase).getComponent(component).getx();
    }

    assertEquals(0.0, aqueousCharge, CHARGE_BALANCE_TOLERANCE,
        "The aqueous electrolyte phase must remain charge neutral");
  }
}
