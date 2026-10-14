package neqsim.chemicalreactions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.chemicalreactions.chemicalequilibrium.LinearProgrammingChemicalEquilibrium;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPitzer;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Numerical reaction seeds preserve the charge and existing species amounts of the physical feed. */
class ChemicalReactionSeedChargeTest extends neqsim.NeqSimTest {
  /** Carbonate discovery includes a divalent anion and must not inject a negative seed charge. */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void carbonateSeedsPreserveNeutralFeed(boolean pitzer) {
    SystemInterface system = createFluid(pitzer, "CO2");
    assertInitializationContract(system, false);
    assertTrue(system.getPhase(0).getComponent("CO3--").getNumberOfmoles() > 0.0);
  }

  /** Sulfide and water reaction discovery obeys the same charge contract without carbonate assumptions. */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void sulfideSeedsPreserveNeutralFeed(boolean pitzer) {
    SystemInterface system = createFluid(pitzer, "H2S");
    assertInitializationContract(system, false);
    assertTrue(system.getPhase(0).getComponent("HS-").getNumberOfmoles() > 0.0);
  }

  /** Newly discovered anions cannot use an existing cation amount to compensate numerical seeds. */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void oneSignSeedsPreservePositiveFeedCharge(boolean pitzer) {
    SystemInterface system = createFluid(pitzer, "CO2");
    system.addComponent("H3O+", 1.0e-6);
    assertInitializationContract(system, true);
  }

  /** Balanced new seeds must not repair or alter an already negatively charged feed. */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void seedsPreserveNegativeFeedCharge(boolean pitzer) {
    SystemInterface system = createFluid(pitzer, "CO2");
    system.addComponent("OH-", 1.0e-6);
    assertInitializationContract(system, false);
  }

  /** Existing neutral acid-base inventories remain unchanged when only new carbonate anions are missing. */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void oneSignSeedsPreserveExistingNeutralInventory(boolean pitzer) {
    SystemInterface system = createFluid(pitzer, "CO2");
    system.addComponent("H3O+", 1.0e-6);
    system.addComponent("OH-", 1.0e-6);
    assertInitializationContract(system, true);
  }

  /** Fixed numerical seeds never consume a small physical feed or grow on repeated initialization. */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void smallFeedComponentsRemainUnchanged(boolean pitzer) {
    SystemInterface system = pitzer ? new SystemPitzer(298.15, 1.0) : new SystemElectrolyteCPAstatoil(298.15, 1.0);
    system.addComponent("water", 2.0e-18);
    system.addComponent("CO2", 1.0e-18);
    assertInitializationContract(system, false);
  }

  /** Zero new carbonate inventories reach reaction equilibrium through the public phase-flash workflow. */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void oneSignZeroSeedsReachChemicalEquilibrium(boolean pitzer) {
    SystemInterface system = createFluid(pitzer, "CO2");
    system.addComponent("H3O+", 1.0e-6);
    system.addComponent("OH-", 1.0e-6);
    assertInitializationContract(system, true);
    system.setMixingRule(pitzer ? 1 : 10);
    system.setMultiPhaseCheck(true);
    ThermodynamicOperations operations = new ThermodynamicOperations(system);
    for (int repetition = 0; repetition < 2; repetition++) {
      operations.TPflash();
      ChemicalReactionOperations chemistry = system.getChemicalReactionOperations();
      assertTrue(chemistry.getMaximumAbsoluteReactionLogResidual() <= 2.0e-6);
      assertTrue(Math.abs(chemistry.getReactivePhaseChargeMoles()) <= 1.0e-8);
      assertTrue(chemistry.getMaximumAbsoluteElementBalanceResidual() <= 1.0e-8);
      assertTrue(system.getPhase(0).getComponent("HCO3-").getNumberOfmoles() > 0.0);
      assertEquals(0.01, system.getPhase(0).getComponent("Na+").getNumberOfmoles(), 1.0e-12);
      assertEquals(0.01, system.getPhase(0).getComponent("Cl-").getNumberOfmoles(), 1.0e-12);
    }
  }

  /** The reused constrained initial estimate cannot create a material inventory of an absent element. */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void zeroCarbonInventoryRemainsAbsentInInitialEstimate(boolean pitzer) {
    SystemInterface system = pitzer ? new SystemPitzer(298.15, 1.0) : new SystemElectrolyteCPAstatoil(298.15, 1.0);
    system.addComponent("water", 1.0);
    system.addComponent("CO2", 0.0);
    system.addComponent("HCO3-", 0.0);
    system.addComponent("CO3--", 0.0);
    system.addComponent("H3O+", 1.0e-7);
    system.addComponent("OH-", 1.0e-7);
    system.setMixingRule(pitzer ? 1 : 10);
    system.setNumberOfPhases(1);
    system.setPhaseType(0, PhaseType.AQUEOUS);
    system.setBeta(0, 1.0);
    system.init(1);
    system.chemicalReactionInit();
    ChemicalReactionOperations chemistry = system.getChemicalReactionOperations();
    LinearProgrammingChemicalEquilibrium initializer = new LinearProgrammingChemicalEquilibrium(
        chemistry.calcChemRefPot(0), chemistry.getComponents(), chemistry.getAllElements(), chemistry, 0);
    double[] estimate = initializer.generateInitialEstimates(system, chemistry.calcBVector(),
        chemistry.calcInertMoles(0), 0);
    assertNotNull(estimate);
    for (int component = 0; component < estimate.length; component++) {
      ComponentInterface species = chemistry.getComponents()[component];
      String name = species.getName();
      if ("CO2".equals(name) || "HCO3-".equals(name) || "CO3--".equals(name)) {
        assertEquals(0.0, species.getNumberOfmoles(), 0.0, name);
        assertTrue(estimate[component] >= 0.0 && estimate[component] <= 1.0e-60,
            name + " must remain absent apart from the initializer's existing activity floor");
      }
    }
  }

  /**
   * Creates a neutral feed with spectator ions for either supported reaction-data family.
   *
   * @param pitzer whether to use the Pitzer reaction data
   * @param dissolvedGas neutral dissolved gas that selects the reaction set
   * @return uninitialized reactive feed
   */
  private static SystemInterface createFluid(boolean pitzer, String dissolvedGas) {
    SystemInterface system = pitzer ? new SystemPitzer(298.15, 10.0) : new SystemElectrolyteCPAstatoil(298.15, 10.0);
    system.addComponent("water", 1.0);
    system.addComponent(dissolvedGas, 0.1);
    system.addComponent("Na+", 0.01);
    system.addComponent("Cl-", 0.01);
    return system;
  }

  /**
   * Checks newly discovered species independently from existing physical inventory, then repeats initialization.
   *
   * @param system feed before reaction discovery
   * @param expectZeroIonicSeeds whether the missing ionic set contains only one charge sign
   */
  private static void assertInitializationContract(SystemInterface system, boolean expectZeroIonicSeeds) {
    Map<String, Double> before = inventory(system);
    double originalCharge = charge(system);
    system.chemicalReactionInit();
    assertTrue(system.getNumberOfComponents() > before.size());
    double seedCharge = 0.0;
    double seedChargeMagnitude = 0.0;
    double seedMoles = 0.0;
    for (int component = 0; component < system.getNumberOfComponents(); component++) {
      ComponentInterface species = system.getPhase(0).getComponent(component);
      double amount = species.getNumberOfmoles();
      if (before.containsKey(species.getName())) {
        assertEquals(before.get(species.getName()), amount, 0.0, species.getName());
      } else {
        assertTrue(Double.isFinite(amount) && amount >= 0.0 && amount <= 1.0e-10, species.getName());
        double addedCharge = amount * species.getIonicCharge();
        seedCharge += addedCharge;
        seedChargeMagnitude += Math.abs(addedCharge);
        seedMoles += amount;
        if (expectZeroIonicSeeds && species.getIonicCharge() != 0.0) {
          assertEquals(0.0, amount, 0.0, species.getName());
        }
      }
    }
    assertEquals(0.0, seedCharge, 8.0 * Math.ulp(seedChargeMagnitude));
    assertEquals(originalCharge, charge(system),
        8.0 * Math.ulp(Math.max(Math.abs(originalCharge), seedChargeMagnitude)));
    assertTrue(seedMoles <= (system.getNumberOfComponents() - before.size()) * 1.0e-10);
    Map<String, Double> initialized = inventory(system);
    system.chemicalReactionInit();
    assertEquals(initialized, inventory(system), "Repeated discovery must not add or rebalance existing species");
  }

  /**
   * Reads component amounts without using phase fractions or initialization caches.
   *
   * @param system source fluid
   * @return overall component mole amounts in component order
   */
  private static Map<String, Double> inventory(SystemInterface system) {
    Map<String, Double> result = new LinkedHashMap<String, Double>();
    for (int component = 0; component < system.getNumberOfComponents(); component++) {
      ComponentInterface species = system.getPhase(0).getComponent(component);
      result.put(species.getName(), species.getNumberOfmoles());
    }
    return result;
  }

  /**
   * Computes net overall ionic charge from the actual stored species amounts.
   *
   * @param system source fluid
   * @return charge in moles of elementary charges
   */
  private static double charge(SystemInterface system) {
    double result = 0.0;
    for (int component = 0; component < system.getNumberOfComponents(); component++) {
      ComponentInterface species = system.getPhase(0).getComponent(component);
      result += species.getNumberOfmoles() * species.getIonicCharge();
    }
    return result;
  }
}
