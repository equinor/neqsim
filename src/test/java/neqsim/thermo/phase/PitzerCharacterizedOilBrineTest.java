package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPitzer;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regression checks for native reactive Pitzer brines with actual petroleum characterization.
 *
 * @author esol
 * @version 1.0
 */
class PitzerCharacterizedOilBrineTest {
  /** Input mass of the C7/C8/C9 TBP cuts and C10+ fraction in kg. */
  private static final double HEAVY_MASS_KG = 0.5 * 0.0933 + 0.5 * 0.1066 + 0.3 * 0.1196 + 0.7 * 0.281;

  /** Direct TBP cuts and an unsplit plus fraction retain their EOS roles without a neutral Pitzer data request. */
  @Test
  void directTbpAndUnsplitPlusFractionFlashWithoutExternalParameters() {
    SystemPitzer fluid = createFluid(313.15, 50.0, false, false);
    assertEquals(4, pseudoInventory(fluid).size());
    assertTrue(fluid.getPhase(0).getComponent("C10_PC").isIsPlusFraction());
    flashAndCheck(fluid);
  }

  /** The standard characterization call also works without selecting a lumping model or lump count. */
  @Test
  void defaultCharacterizationWorksWithoutAdditionalConfiguration() {
    SystemPitzer fluid = new SystemPitzer(313.15, 50.0);
    addOil(fluid);
    fluid.getCharacterization().characterisePlusFraction();
    assertFalse(fluid.hasComponent("C10_PC"));
    assertTrue(pseudoInventory(fluid).size() > 1);
    addBrine(fluid);
    fluid.chemicalReactionInit();
    fluid.createDatabase(true);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    flashAndCheck(fluid);
  }

  /** The overload accepting measured critical properties also populates the initially inactive oil role. */
  @Test
  void explicitCriticalTbpPropertiesReachEveryPhaseRole() {
    SystemPitzer fluid = new SystemPitzer(313.15, 50.0);
    addOil(fluid);
    fluid.addTBPfraction("C7", 0.0, 0.0933, 0.73, 530.0, 32.5, 0.3);
    addBrine(fluid);
    fluid.chemicalReactionInit();
    fluid.createDatabase(true);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    flashAndCheck(fluid);
    for (int phaseIndex = 0; phaseIndex < fluid.getNumberOfPhases(); phaseIndex++) {
      ComponentInterface component = fluid.getPhase(phaseIndex).getComponent("C7_PC");
      assertEquals(530.0, component.getTC(), 0.0);
      assertEquals(32.5, component.getPC(), 0.0);
      assertEquals(0.3, component.getAcentricFactor(), 1.0e-12);
    }
  }

  /** Standard EOS systems receive the same fraction metadata when only one phase is initially active. */
  @Test
  void singlePhaseSrkSetupAlsoPopulatesInactivePhaseProperties() {
    SystemSrkEos fluid = new SystemSrkEos(313.15, 50.0);
    fluid.setNumberOfPhases(1);
    fluid.addComponent("methane", 5.0);
    fluid.addPlusFraction("C10", 0.7, 0.281, 0.882888);
    for (int phaseIndex = 0; phaseIndex < fluid.getMaxNumberOfPhases(); phaseIndex++) {
      ComponentInterface component = fluid.getPhase(phaseIndex).getComponent("C10_PC");
      assertEquals(0.281, component.getMolarMass(), 1.0e-12);
      assertEquals(0.882888, component.getNormalLiquidDensity(), 1.0e-12);
      assertFalse(component.isIsTBPfraction());
      assertTrue(component.isIsPlusFraction());
    }
  }

  /** Characterized oil closes phase, ion, reaction and inventory balances at three nearby operating conditions. */
  @Test
  void characterizedOilFlashesAcrossTemperatureAndPressure() {
    double[][] conditions = {{298.15, 10.0}, {313.15, 50.0}, {333.15, 100.0}};
    for (double[] condition : conditions) {
      SystemPitzer fluid = createFluid(condition[0], condition[1], true, false);
      assertEquals(7, pseudoInventory(fluid).size());
      assertFalse(fluid.hasComponent("C10_PC"));
      flashAndCheck(fluid);
    }
  }

  /** Adding brine on either side of characterization gives the same characterized oil and reactive solution. */
  @Test
  void characterizationBeforeOrAfterAddingBrineGivesTheSameSolution() {
    SystemPitzer first = createFluid(313.15, 50.0, true, false);
    SystemPitzer second = createFluid(313.15, 50.0, true, true);
    Map<String, Double> firstInventory = pseudoInventory(first);
    Map<String, Double> secondInventory = pseudoInventory(second);
    assertEquals(firstInventory.keySet(), secondInventory.keySet());
    for (String name : firstInventory.keySet()) {
      assertEquals(firstInventory.get(name), secondInventory.get(name), 1.0e-10);
    }
    flashAndCheck(first);
    flashAndCheck(second);
    assertEquals(((PhasePitzer) first.getPhase("aqueous")).getPitzerPH(),
        ((PhasePitzer) second.getPhase("aqueous")).getPitzerPH(), 5.0e-7);
    for (String role : new String[] {"gas", "oil", "aqueous"}) {
      assertEquals(first.getPhase(role).getNumberOfMolesInPhase(), second.getPhase(role).getNumberOfMolesInPhase(),
          1.0e-7);
    }
  }

  /** Characterization produces the standard SRK hydrocarbon critical properties and heavy-component inventory. */
  @Test
  void pseudoPropertiesMatchStandardSrkCharacterization() {
    SystemPitzer fluid = createFluid(313.15, 50.0, true, false);
    SystemSrkEos reference = new SystemSrkEos(313.15, 50.0);
    addOil(reference);
    characterize(reference);
    Map<String, Double> expected = pseudoInventory(reference);
    assertEquals(expected.keySet(), pseudoInventory(fluid).keySet());
    for (String name : expected.keySet()) {
      ComponentInterface actual = fluid.getPhase(0).getComponent(name);
      ComponentInterface original = reference.getPhase(0).getComponent(name);
      assertEquals(original.getNumberOfmoles(), actual.getNumberOfmoles(), 1.0e-10);
      assertEquals(original.getMolarMass(), actual.getMolarMass(), 1.0e-12);
      assertEquals(original.getTC(), actual.getTC(), 1.0e-8);
      assertEquals(original.getPC(), actual.getPC(), 1.0e-10);
      assertEquals(original.getAcentricFactor(), actual.getAcentricFactor(), 1.0e-10);
      assertEquals(original.getNormalLiquidDensity(), actual.getNormalLiquidDensity(), 1.0e-12);
    }
  }

  /** Repeated flashes and cloning preserve the characterized inventory, selected dataset and aqueous outputs. */
  @Test
  void repeatedFlashAndClonePreserveCharacterizedOilAndBrine() {
    SystemPitzer fluid = createFluid(313.15, 50.0, true, false);
    flashAndCheck(fluid);
    PhasePitzer original = (PhasePitzer) fluid.getPhase("aqueous");
    double originalPH = original.getPitzerPH();
    double originalCalciumActivity = original.getPitzerActivity(original.getComponent("Ca++").getComponentNumber());
    SystemPitzer clone = fluid.clone();
    flashAndCheck(clone);
    flashAndCheck(fluid);
    PhasePitzer aqueous = (PhasePitzer) clone.getPhase("aqueous");
    assertEquals(originalPH, aqueous.getPitzerPH(), 5.0e-7);
    assertEquals(originalPH, ((PhasePitzer) fluid.getPhase("aqueous")).getPitzerPH(), 5.0e-7);
    assertEquals(originalCalciumActivity, aqueous.getPitzerActivity(aqueous.getComponent("Ca++").getComponentNumber()),
        1.0e-8);
    assertEquals(original.getParameterDatasetId(), aqueous.getParameterDatasetId());
    assertEquals(original.getReactiveCarbonateSourceZeroDefinitions(),
        aqueous.getReactiveCarbonateSourceZeroDefinitions());
  }

  /**
   * Defines the same oil and sour-gas feed for the hybrid Pitzer system and SRK control.
   *
   * @param fluid system receiving real and pseudo hydrocarbon components
   */
  private static void addOil(SystemInterface fluid) {
    fluid.addComponent("methane", 5.0);
    fluid.addComponent("ethane", 0.5);
    fluid.addComponent("propane", 0.25);
    fluid.addComponent("CO2", 0.5);
    fluid.addComponent("H2S", 0.02);
    fluid.addTBPfraction("C7", 0.5, 0.0933, 0.73);
    fluid.addTBPfraction("C8", 0.5, 0.1066, 0.7533);
    fluid.addTBPfraction("C9", 0.3, 0.1196, 0.7653);
    fluid.addPlusFraction("C10", 0.7, 0.281, 0.882888);
  }

  /**
   * Splits C10+ into four lumps while preserving the three explicit TBP cuts.
   *
   * @param fluid oil-containing system to characterize
   */
  private static void characterize(SystemInterface fluid) {
    fluid.getCharacterization().setLumpingModel("PVTlumpingModel");
    fluid.getCharacterization().getLumpingModel().setNumberOfLumpedComponents(4);
    fluid.getCharacterization().characterisePlusFraction();
  }

  /**
   * Adds a charge-balanced calcium/chloride/sulfate brine.
   *
   * @param fluid receiving fluid
   */
  private static void addBrine(SystemPitzer fluid) {
    fluid.addComponent("water", 55.5);
    fluid.addComponent("Na+", 0.5);
    fluid.addComponent("K+", 0.5);
    fluid.addComponent("Ca++", 0.05);
    fluid.addComponent("Cl-", 1.08);
    fluid.addComponent("SO4--", 0.01);
  }

  /**
   * Builds the ordinary reactive model after completing pseudo-component topology changes.
   *
   * @param temperature temperature in K
   * @param pressure pressure in bara
   * @param characterizePlus whether to split and lump the C10+ fraction
   * @param brineFirst whether to add brine before characterization
   * @return initialized feed with native automatic parameter selection
   */
  private static SystemPitzer createFluid(double temperature, double pressure, boolean characterizePlus,
      boolean brineFirst) {
    SystemPitzer fluid = new SystemPitzer(temperature, pressure);
    addOil(fluid);
    if (brineFirst) {
      addBrine(fluid);
    }
    if (characterizePlus) {
      characterize(fluid);
    }
    if (!brineFirst) {
      addBrine(fluid);
    }
    double heavyMass = 0.0;
    for (String name : pseudoInventory(fluid).keySet()) {
      ComponentInterface component = fluid.getPhase(0).getComponent(name);
      heavyMass += component.getNumberOfmoles() * component.getMolarMass();
      assertTrue(PitzerParameterDatasets.isHydrocarbonForAutomaticCatalog(component));
    }
    assertEquals(HEAVY_MASS_KG, heavyMass, 1.0e-10, "Characterization preserves heavy mass");
    fluid.chemicalReactionInit();
    fluid.createDatabase(true);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  /**
   * Records explicit pseudo-component amounts without inventing molecular formulas for petroleum fractions.
   *
   * @param fluid fluid with an existing characterized slate
   * @return overall moles by pseudo-component name
   */
  private static Map<String, Double> pseudoInventory(SystemInterface fluid) {
    Map<String, Double> inventory = new TreeMap<String, Double>();
    for (int index = 0; index < fluid.getNumberOfComponents(); index++) {
      ComponentInterface component = fluid.getPhase(0).getComponent(index);
      if (component.isIsTBPfraction() || component.isIsPlusFraction()) {
        inventory.put(component.getComponentName(), component.getNumberOfmoles());
      }
    }
    return inventory;
  }

  /**
   * Runs a flash and checks phase equilibrium, reactive closure and every pseudo-component inventory.
   *
   * @param fluid initialized reactive feed or flashed fluid to re-evaluate
   */
  private static void flashAndCheck(SystemPitzer fluid) {
    Map<String, Double> inventory = pseudoInventory(fluid);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.init(1);
    assertEquals(3, fluid.getNumberOfPhases());
    PhasePitzer aqueous = (PhasePitzer) fluid.getPhase("aqueous");
    assertEquals(PitzerParameterDatasets.PHREEQC_AUTOMATIC_REACTIVE_CARBONATE_ID, aqueous.getParameterDatasetId());
    aqueous.requireCompletePitzerParameterCoverage();
    aqueous.requireCompletePitzerReactionParameterCoverage();
    aqueous.requireCompleteNeutralPitzerParameterCoverage();
    assertEquals(225, aqueous.getReactiveCarbonateSourceZeroDefinitions().size());
    assertTrue(aqueous.getPitzerPH() > 3.0 && aqueous.getPitzerPH() < 5.0);
    for (String name : new String[] {"Ca++", "SO4--", "HSO4-", "H2S", "HS-", "HCO3-", "CO3--", "water"}) {
      double activity = aqueous.getPitzerActivity(aqueous.getComponent(name).getComponentNumber());
      assertTrue(Double.isFinite(activity) && activity > 0.0, name + " native activity");
    }
    double heavyMass = 0.0;
    double betaSum = 0.0;
    for (int phaseIndex = 0; phaseIndex < fluid.getNumberOfPhases(); phaseIndex++) {
      PhaseInterface phase = fluid.getPhase(phaseIndex);
      double beta = fluid.getBeta(phaseIndex);
      assertTrue(Double.isFinite(beta) && beta > 0.0 && beta < 1.0);
      betaSum += beta;
      for (int index = 0; index < phase.getNumberOfComponents(); index++) {
        ComponentInterface component = phase.getComponent(index);
        if (phase != aqueous && Math.abs(component.getIonicCharge()) > 0.5) {
          assertTrue(component.getNumberOfMolesInPhase() < 1.0e-15);
        }
      }
    }
    assertEquals(1.0, betaSum, 1.0e-12);
    for (String name : inventory.keySet()) {
      double amount = 0.0;
      for (int phaseIndex = 0; phaseIndex < fluid.getNumberOfPhases(); phaseIndex++) {
        ComponentInterface component = fluid.getPhase(phaseIndex).getComponent(name);
        ComponentInterface reference = aqueous.getComponent(name);
        assertEquals(reference.isIsTBPfraction(), component.isIsTBPfraction());
        assertEquals(reference.isIsPlusFraction(), component.isIsPlusFraction());
        assertEquals(reference.getMolarMass(), component.getMolarMass(), 1.0e-12, name + " molar mass");
        assertEquals(reference.getNormalLiquidDensity(), component.getNormalLiquidDensity(), 1.0e-12,
            name + " reference density");
        assertEquals(reference.getRacketZ(), component.getRacketZ(), 1.0e-12, name + " volume correction");
        double phaseMoles = component.getNumberOfMolesInPhase();
        assertTrue(Double.isFinite(phaseMoles) && phaseMoles >= 0.0);
        amount += phaseMoles;
        heavyMass += phaseMoles * component.getMolarMass();
      }
      assertEquals(inventory.get(name), amount, 1.0e-9, name + " inventory");
      ComponentInterface gas = fluid.getPhase("gas").getComponent(name);
      ComponentInterface oil = fluid.getPhase("oil").getComponent(name);
      if (gas.getNumberOfMolesInPhase() > 1.0e-15 && oil.getNumberOfMolesInPhase() > 1.0e-15) {
        assertEquals(Math.log(gas.getx() * gas.getFugacityCoefficient()),
            Math.log(oil.getx() * oil.getFugacityCoefficient()), 1.0e-6, name + " gas/oil fugacity equality");
      }
      assertTrue(aqueous.getComponent(name).getNumberOfMolesInPhase() < 1.0e-10,
          "Preserves the existing insoluble-Henry treatment for " + name);
    }
    assertEquals(HEAVY_MASS_KG, heavyMass, 1.0e-9);
    assertTrue(fluid.getPhase("oil").getNumberOfMolesInPhase() > 0.0);
    assertTrue(fluid.getPhase("gas").getNumberOfMolesInPhase() > 0.0);
    assertTrue(fluid.getChemicalReactionOperations().getMaximumAbsoluteReactionLogResidual() < 3.0e-6);
    assertTrue(fluid.getChemicalReactionOperations().getNormalizedReactivePhaseChargeResidual() < 1.0e-10);
    assertTrue(fluid.getChemicalReactionOperations().getMaximumAbsoluteElementBalanceResidual() < 1.0e-8);
  }
}
