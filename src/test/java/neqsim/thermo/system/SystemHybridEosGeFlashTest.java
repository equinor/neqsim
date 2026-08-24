package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import neqsim.chemicalreactions.ChemicalReactionOperations;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseEos;
import neqsim.thermo.phase.PhaseGEInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhasePitzer;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.thermodynamicoperations.flashops.TPHybridEosGeFlash;

/** Regression tests for fixed-role EOS-gas / EOS-oil / GE-aqueous flashes. */
class SystemHybridEosGeFlashTest extends neqsim.NeqSimTest {
  /** Material-balance tolerance used by the public hybrid acceptance contract. */
  private static final double MATERIAL_BALANCE_TOLERANCE = 1.0e-7;

  /** Cross-phase logarithmic fugacity tolerance used by the public hybrid acceptance contract. */
  private static final double LOG_FUGACITY_TOLERANCE = 1.0e-5;

  /** Phase fraction below which a role is considered absent. */
  private static final double MATERIAL_PHASE_FRACTION = 1.0e-10;

  /**
   * Build the synthetic hydrocarbon/brine system from issue 2862.
   *
   * @return configured unflashed system
   */
  private SystemPitzer createGasOilAqueousSystem() {
    SystemPitzer system = new SystemPitzer(313.15, 50.0);
    system.addComponent("methane", 5.0);
    system.addComponent("n-heptane", 2.0);
    system.addComponent("water", 55.5);
    system.addComponent("Na+", 1.0);
    system.addComponent("Cl-", 1.0);
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(true);
    return system;
  }

  /**
   * Build a gas-forming system using the qualified PHREEQC CO2-Na2SO4 subset.
   *
   * @return configured unflashed system
   */
  private SystemPitzer createQualifiedCarbonDioxideSodiumSulfateSystem() {
    SystemPitzer system = new SystemPitzer(373.15, 150.0);
    system.addComponent("CO2", 100.0);
    system.addComponent("water", 55.508);
    system.addComponent("Na+", 2.0);
    system.addComponent("SO4--", 1.0);
    system.init(0);
    system.applyPhreeqcCo2SodiumSulfateParameters();
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(true);
    return system;
  }

  /**
   * Build a reactive carbonate-scale system with optional EOS oil.
   *
   * @param includeOil whether to add an oil-forming hydrocarbon
   * @return configured unflashed reactive Pitzer system
   */
  private SystemPitzer createReactiveScaleSystem(boolean includeOil) {
    SystemPitzer system = new SystemPitzer(313.15, 50.0);
    system.addComponent("methane", 5.0);
    system.addComponent("CO2", 0.05);
    if (includeOil) {
      system.addComponent("n-heptane", 2.0);
    }
    system.addComponent("water", 55.5);
    // The feed is electrically neutral, while the primary-salt coverage topology remains
    // the qualified binary Ca++/Cl- pair.
    system.addComponent("Ca++", 6.0e-4);
    system.addComponent("Cl-", 2.0e-4);
    system.addComponent("HCO3-", 1.0e-3);
    system.chemicalReactionInit();
    system.createDatabase(true);
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(true);
    return system;
  }

  /** Gas, oil and electrolyte-GE aqueous phases satisfy topology, balance and fugacity acceptance. */
  @Test
  void gasOilAqueousFlashMeetsAcceptanceContract() {
    SystemPitzer system = createGasOilAqueousSystem();
    new ThermodynamicOperations(system).TPflash();

    assertEquals(3, system.getNumberOfPhases());
    assertTrue(system.getEquationOfStatePhase() instanceof PhaseEos);
    assertTrue(system.getEosOilPhase() instanceof PhaseEos);
    assertTrue(system.getGeLiquidPhase() instanceof PhasePitzer);
    assertTrue(system.getGeLiquidPhase() instanceof PhaseGEInterface);
    assertEquals(0, system.getEosGasPhaseSlot());
    assertEquals(2, system.getEosOilPhaseSlot());
    assertEquals(1, system.getGeLiquidPhaseSlot());
    assertTrue(hasPhaseType(system, PhaseType.GAS));
    assertTrue(hasPhaseType(system, PhaseType.OIL));
    assertTrue(hasPhaseType(system, PhaseType.AQUEOUS));

    assertBalancedAndAtEquilibrium(system);
    PhaseInterface aqueous = findPhase(system, PhaseType.AQUEOUS);
    assertTrue(aqueous.getComponent("n-heptane").getFugacityCoefficient() > 1.0e6,
        "A non-water database solvent must use an aqueous Henry reference in Pitzer");
    for (int componentIndex = 0; componentIndex < aqueous.getNumberOfComponents(); componentIndex++) {
      ComponentInterface component = aqueous.getComponent(componentIndex);
      if (component.getIonicCharge() != 0 || component.isIsIon()) {
        assertTrue(component.getx() > 0.0);
        for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
          if (system.getPhase(phaseIndex) != aqueous) {
            assertTrue(system.getPhase(phaseIndex).getComponent(componentIndex).getx() <= 1.0e-40);
          }
        }
      }
    }
  }

  /** An infeasible beta iterate is projected above the aqueous phase's fixed ionic inventory. */
  @Test
  void hybridBetaIterationRetainsAqueousIonCapacity() {
    SystemPitzer system = createQualifiedCarbonDioxideSodiumSulfateSystem();
    system.prepareHybridEosGeFlash();
    system.init(1);

    int aqueousPhaseIndex = -1;
    double ionOverallFraction = 0.0;
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      if (system.isHybridEosGeAqueousPhase(phaseIndex)) {
        aqueousPhaseIndex = phaseIndex;
      }
    }
    for (int componentIndex = 0; componentIndex < system.getPhase(0).getNumberOfComponents(); componentIndex++) {
      ComponentInterface component = system.getPhase(0).getComponent(componentIndex);
      if (component.getIonicCharge() != 0 || component.isIsIon()) {
        ionOverallFraction += component.getz();
      }
    }
    assertTrue(aqueousPhaseIndex >= 0);
    assertTrue(system.getNumberOfPhases() >= 2);
    system.setBeta(aqueousPhaseIndex, 0.5 * ionOverallFraction);
    double nonAqueousBeta = (1.0 - system.getBeta(aqueousPhaseIndex)) / (system.getNumberOfPhases() - 1.0);
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      if (phaseIndex != aqueousPhaseIndex) {
        system.setBeta(phaseIndex, nonAqueousBeta);
      }
    }

    TPHybridEosGeFlash solver = new TPHybridEosGeFlash(system, system);
    solver.calcE();
    solver.setXY();
    system.init(1);

    assertTrue(system.getBeta(aqueousPhaseIndex) > ionOverallFraction);
    assertEquals(1.0, betaSum(system), 1.0e-12);
    PhaseInterface aqueous = system.getPhase(aqueousPhaseIndex);
    for (int componentIndex = 0; componentIndex < aqueous.getNumberOfComponents(); componentIndex++) {
      ComponentInterface component = aqueous.getComponent(componentIndex);
      if (component.getIonicCharge() != 0 || component.isIsIon()) {
        assertEquals(component.getz(), system.getBeta(aqueousPhaseIndex) * component.getx(), 1.0e-12,
            component.getComponentName());
      }
    }
  }

  /** Qualified gas-forming CO2/Na2SO4 flashes remain closed across repeats and nearby pressure. */
  @Test
  void qualifiedCarbonDioxideSodiumSulfateGasAqueousFlashConverges() {
    SystemPitzer system = createQualifiedCarbonDioxideSodiumSulfateSystem();
    ThermodynamicOperations operations = new ThermodynamicOperations(system);

    operations.TPflash();
    assertEquals(2, system.getNumberOfPhases(), phaseDiagnostics(system));
    assertTrue(hasPhaseType(system, PhaseType.GAS), phaseDiagnostics(system));
    assertTrue(hasPhaseType(system, PhaseType.AQUEOUS), phaseDiagnostics(system));
    assertBalancedAndAtEquilibrium(system);
    double firstGasBeta = findPhaseBeta(system, PhaseType.GAS);

    operations.TPflash();
    assertEquals(firstGasBeta, findPhaseBeta(system, PhaseType.GAS), 1.0e-10);
    assertBalancedAndAtEquilibrium(system);

    system.setPressure(140.0);
    operations.TPflash();
    assertEquals(2, system.getNumberOfPhases(), phaseDiagnostics(system));
    assertTrue(hasPhaseType(system, PhaseType.GAS), phaseDiagnostics(system));
    assertTrue(hasPhaseType(system, PhaseType.AQUEOUS), phaseDiagnostics(system));
    assertBalancedAndAtEquilibrium(system);
  }

  /** Every SystemEosGE subclass can explicitly promote its GE liquid to the hybrid aqueous role. */
  @Test
  void genericNrtlSystemCanEnableHybridGasOilAqueousFlash() {
    SystemNRTL system = new SystemNRTL(313.15, 50.0);
    system.addComponent("methane", 5.0);
    system.addComponent("n-heptane", 2.0);
    system.addComponent("water", 55.5);
    system.createDatabase(true);
    system.setMixingRule("classic");

    assertFalse(system.isHybridEosGeTopologyConfigured());
    system.enableHybridEosGeFlash();
    assertTrue(system.isHybridEosGeTopologyConfigured());
    assertTrue(system.requiresHybridEosGeFlash());

    new ThermodynamicOperations(system).TPflash();

    assertTrue(system.getEquationOfStatePhase() instanceof PhaseEos);
    assertTrue(system.getEosOilPhase() instanceof PhaseEos);
    assertTrue(system.getGeLiquidPhase() instanceof PhaseGEInterface);
    assertTrue(hasPhaseType(system, PhaseType.GAS));
    assertTrue(hasPhaseType(system, PhaseType.OIL));
    assertTrue(hasPhaseType(system, PhaseType.AQUEOUS));
    assertBalancedAndAtEquilibrium(system);
  }

  /** Desmukh-Mather is registered as a reactive electrolyte-GE hybrid rather than a Pitzer special case. */
  @Test
  void reactiveDesmukhMatherSystemUsesSharedHybridCoupling() throws Exception {
    SystemDesmukhMather system = new SystemDesmukhMather(313.15, 5.0);
    system.addComponent("methane", 5.0);
    system.addComponent("CO2", 0.2);
    system.addComponent("n-heptane", 2.0);
    system.addComponent("MDEA", 1.0);
    system.addComponent("water", 9.0);
    system.addComponent("Ca++", 1.0e-4);
    system.addComponent("Na+", 1.0e-3);
    system.addComponent("Cl-", 2.0e-4);
    system.addComponent("HCO3-", 1.0e-3);
    system.chemicalReactionInit();
    system.createDatabase(true);
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(true);
    double[] conservedQuantities = getReactiveConservedQuantities(system, true);

    assertTrue(system.isHybridEosGeTopologyConfigured());
    assertTrue(system.requiresHybridEosGeFlash());

    ThermodynamicOperations operations = new ThermodynamicOperations(system);
    operations.TPflash();

    assertTrue(hasPhaseType(system, PhaseType.GAS), phaseDiagnostics(system));
    assertTrue(hasPhaseType(system, PhaseType.OIL), phaseDiagnostics(system));
    assertTrue(hasPhaseType(system, PhaseType.AQUEOUS), phaseDiagnostics(system));
    assertTrue(findPhase(system, PhaseType.AQUEOUS) instanceof neqsim.thermo.phase.PhaseDesmukhMather);
    assertReactiveConservedQuantities(system, conservedQuantities);
    double calciteSaturationRatio = operations.getRelativeScalePotential("CaCO3");
    assertTrue(Double.isFinite(calciteSaturationRatio) && calciteSaturationRatio > 0.0,
        "Scale potential must use the selected electrolyte-GE model's aqueous activities");
  }

  /** Built-in electrolyte GE systems expose the same fixed-role contract without Pitzer type checks. */
  @Test
  void electrolyteGeSystemsRegisterSharedHybridTopology() {
    SystemEosGE[] systems = new SystemEosGE[] { new SystemDesmukhMather(313.15, 5.0),
        new SystemKentEisenberg(313.15, 5.0) };
    for (SystemEosGE system : systems) {
      assertTrue(system.isHybridEosGeTopologyConfigured());
      assertTrue(system.getEquationOfStatePhase() instanceof PhaseEos);
      assertTrue(system.getEosOilPhase() instanceof PhaseEos);
      assertTrue(system.getGeLiquidPhase() instanceof PhaseGEInterface);
      system.setMultiPhaseCheck(true);
      assertTrue(system.requiresHybridEosGeFlash());
    }
  }

  /** Reactive gas-aqueous Pitzer flash retains carbonate chemistry and exposes calcite scale potential. */
  @Test
  void reactiveGasAqueousFlashSupportsCalciteScalePotential() throws Exception {
    SystemPitzer system = createReactiveScaleSystem(false);
    double[] conservedQuantities = getReactiveConservedQuantities(system, true);
    double initialCarbonateMoles = system.getPhase(0).getComponent("CO3--").getNumberOfmoles();
    assertTrue(system.requiresHybridEosGeFlash(), "Reactive Pitzer systems must select the fixed-role hybrid strategy");

    ThermodynamicOperations operations = new ThermodynamicOperations(system);
    operations.TPflash();

    assertEquals(2, system.getNumberOfPhases(), phaseDiagnostics(system));
    assertTrue(hasPhaseType(system, PhaseType.GAS));
    assertFalse(hasPhaseType(system, PhaseType.OIL));
    assertTrue(hasPhaseType(system, PhaseType.AQUEOUS));
    assertReactiveCarbonateScaleResult(system, operations, conservedQuantities, initialCarbonateMoles);
  }

  /** Reactive gas-oil-aqueous Pitzer flash retains all roles and exposes calcite scale potential. */
  @Test
  void reactiveGasOilAqueousFlashSupportsCalciteScalePotential() throws Exception {
    SystemPitzer system = createReactiveScaleSystem(true);
    double[] conservedQuantities = getReactiveConservedQuantities(system, true);
    double initialCarbonateMoles = system.getPhase(0).getComponent("CO3--").getNumberOfmoles();
    PhaseInterface gasRole = system.getEquationOfStatePhase();
    PhaseInterface oilRole = system.getEosOilPhase();
    PhaseInterface aqueousRole = system.getGeLiquidPhase();
    assertTrue(system.requiresHybridEosGeFlash(), "Reactive Pitzer systems must select the fixed-role hybrid strategy");

    ThermodynamicOperations operations = new ThermodynamicOperations(system);
    operations.TPflash();

    assertEquals(3, system.getNumberOfPhases(), phaseDiagnostics(system));
    assertTrue(hasPhaseType(system, PhaseType.GAS));
    assertTrue(hasPhaseType(system, PhaseType.OIL));
    assertTrue(hasPhaseType(system, PhaseType.AQUEOUS));
    assertSame(gasRole, system.getEquationOfStatePhase());
    assertSame(oilRole, system.getEosOilPhase());
    assertSame(aqueousRole, system.getGeLiquidPhase());
    assertReactiveCarbonateScaleResult(system, operations, conservedQuantities, initialCarbonateMoles);
  }

  /** A trace oil role disappears from the active mapping and the same object is restored on a later flash. */
  @Test
  void disappearingOilRoleIsRestoredWithoutReplacement() {
    SystemPitzer system = new SystemPitzer(313.15, 50.0);
    system.addComponent("methane", 5.0);
    system.addComponent("n-heptane", 0.0);
    system.addComponent("water", 55.5);
    system.addComponent("Na+", 1.0);
    system.addComponent("Cl-", 1.0);
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(true);
    PhaseInterface oilRole = system.getEosOilPhase();

    ThermodynamicOperations operations = new ThermodynamicOperations(system);
    operations.TPflash();
    assertEquals(2, system.getNumberOfPhases(), phaseDiagnostics(system));
    assertFalse(hasPhaseType(system, PhaseType.OIL));
    assertSame(oilRole, system.getEosOilPhase());
    assertBalancedAndAtEquilibrium(system);

    system.addComponent("n-heptane", 2.0);
    operations.TPflash();
    assertEquals(3, system.getNumberOfPhases());
    assertTrue(hasPhaseType(system, PhaseType.OIL));
    assertSame(oilRole, system.getEosOilPhase());
    assertBalancedAndAtEquilibrium(system);
  }

  /** Gas-oil and oil-aqueous limits retain only their material creation-order roles. */
  @Test
  void twoPhaseRoleCombinationsConverge() {
    SystemPitzer gasOil = new SystemPitzer(313.15, 50.0);
    gasOil.addComponent("methane", 5.0);
    gasOil.addComponent("n-heptane", 2.0);
    gasOil.addComponent("water", 0.0);
    gasOil.setMixingRule("classic");
    gasOil.setMultiPhaseCheck(true);
    new ThermodynamicOperations(gasOil).TPflash();
    assertEquals(2, gasOil.getNumberOfPhases(), phaseDiagnostics(gasOil));
    assertTrue(hasPhaseType(gasOil, PhaseType.GAS));
    assertTrue(hasPhaseType(gasOil, PhaseType.OIL));
    assertFalse(hasPhaseType(gasOil, PhaseType.AQUEOUS));
    assertBalancedAndAtEquilibrium(gasOil);

    SystemPitzer oilAqueous = new SystemPitzer(298.15, 100.0);
    oilAqueous.addComponent("methane", 0.0);
    oilAqueous.addComponent("n-heptane", 2.0);
    oilAqueous.addComponent("water", 55.5);
    oilAqueous.addComponent("Na+", 1.0);
    oilAqueous.addComponent("Cl-", 1.0);
    oilAqueous.setMixingRule("classic");
    oilAqueous.setMultiPhaseCheck(true);
    new ThermodynamicOperations(oilAqueous).TPflash();
    assertEquals(2, oilAqueous.getNumberOfPhases(), phaseDiagnostics(oilAqueous));
    assertFalse(hasPhaseType(oilAqueous, PhaseType.GAS));
    assertTrue(hasPhaseType(oilAqueous, PhaseType.OIL));
    assertTrue(hasPhaseType(oilAqueous, PhaseType.AQUEOUS));
    assertBalancedAndAtEquilibrium(oilAqueous);
  }

  /** Nearby water-rich gas-condensate states retain finite balanced hybrid solutions. */
  @Test
  void waterRichGasCondensateNearbyStatesConverge() {
    double[][] conditions = new double[][] { { 303.15, 40.0 }, { 323.15, 60.0 } };
    for (double[] condition : conditions) {
      SystemPitzer system = createGasOilAqueousSystem();
      system.setTemperature(condition[0]);
      system.setPressure(condition[1]);
      new ThermodynamicOperations(system).TPflash();
      assertTrue(system.getNumberOfPhases() >= 2, phaseDiagnostics(system));
      assertTrue(hasPhaseType(system, PhaseType.AQUEOUS));
      assertBalancedAndAtEquilibrium(system);
    }
  }

  /** Density reordering, cloning and Java serialization preserve creation-order role ownership. */
  @Test
  void rolesSurviveReorderingCloneSerializationAndRepeatedFlash() throws Exception {
    SystemPitzer system = createGasOilAqueousSystem();
    PhaseInterface gasRole = system.getEquationOfStatePhase();
    PhaseInterface oilRole = system.getEosOilPhase();
    PhaseInterface aqueousRole = system.getGeLiquidPhase();
    ThermodynamicOperations operations = new ThermodynamicOperations(system);

    operations.TPflash();
    system.orderByDensity();
    operations.TPflash();
    assertSame(gasRole, system.getEquationOfStatePhase());
    assertSame(oilRole, system.getEosOilPhase());
    assertSame(aqueousRole, system.getGeLiquidPhase());
    assertBalancedAndAtEquilibrium(system);

    SystemPitzer clone = system.clone();
    assertNotSame(gasRole, clone.getEquationOfStatePhase());
    assertNotSame(oilRole, clone.getEosOilPhase());
    assertNotSame(aqueousRole, clone.getGeLiquidPhase());
    new ThermodynamicOperations(clone).TPflash();
    assertBalancedAndAtEquilibrium(clone);

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(system);
    }
    SystemPitzer restored;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      restored = (SystemPitzer) input.readObject();
    }
    assertEquals(0, restored.getEosGasPhaseSlot());
    assertEquals(2, restored.getEosOilPhaseSlot());
    assertEquals(1, restored.getGeLiquidPhaseSlot());
    new ThermodynamicOperations(restored).TPflash();
    assertBalancedAndAtEquilibrium(restored);
  }

  /** PH, PS and TV flashes recover the extensive state of a hybrid electrolyte-GE reference. */
  @Test
  void phPsAndTvFlashesRecoverReferenceState() {
    SystemPitzer reference = createGasOilAqueousSystem();
    new ThermodynamicOperations(reference).TPflash();
    reference.initProperties();
    double targetTemperature = reference.getTemperature();
    double targetPressure = reference.getPressure();
    double targetEnthalpy = reference.getEnthalpy();
    double targetEntropy = reference.getEntropy();
    double targetVolume = reference.getVolume();

    assertTrue(Double.isFinite(targetEnthalpy));
    assertTrue(Double.isFinite(targetEntropy));
    assertTrue(Double.isFinite(targetVolume) && targetVolume > 0.0);

    SystemPitzer phSystem = reference.clone();
    phSystem.setTemperature(targetTemperature + 2.0);
    new ThermodynamicOperations(phSystem).PHflash(targetEnthalpy);
    phSystem.initProperties();
    assertEquals(targetEnthalpy, phSystem.getEnthalpy(), Math.max(1.0e-6, Math.abs(targetEnthalpy) * 1.0e-5));
    assertEquals(targetTemperature, phSystem.getTemperature(), 0.1);

    SystemPitzer psSystem = reference.clone();
    psSystem.setTemperature(targetTemperature + 2.0);
    new ThermodynamicOperations(psSystem).PSflash(targetEntropy);
    psSystem.initProperties();
    assertEquals(targetEntropy, psSystem.getEntropy(), Math.max(1.0e-6, Math.abs(targetEntropy) * 1.0e-5));
    assertEquals(targetTemperature, psSystem.getTemperature(), 0.1);

    SystemPitzer tvSystem = reference.clone();
    tvSystem.setPressure(targetPressure * 1.05);
    new ThermodynamicOperations(tvSystem).TVflash(targetVolume);
    tvSystem.initProperties();
    assertEquals(targetVolume, tvSystem.getVolume(), Math.abs(targetVolume) * 1.0e-5);
    assertEquals(targetPressure, tvSystem.getPressure(), 1.0e-3);
  }

  /**
   * Assert normalization, component material balance and neutral-component fugacity equality.
   *
   * @param system flashed system
   */
  private void assertBalancedAndAtEquilibrium(SystemInterface system) {
    double betaSum = 0.0;
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      betaSum += system.getBeta(phaseIndex);
      double compositionSum = 0.0;
      for (int componentIndex = 0; componentIndex < system.getPhase(phaseIndex)
          .getNumberOfComponents(); componentIndex++) {
        ComponentInterface component = system.getPhase(phaseIndex).getComponent(componentIndex);
        assertTrue(Double.isFinite(component.getx()) && component.getx() > 0.0);
        compositionSum += component.getx();
      }
      assertEquals(1.0, compositionSum, 1.0e-8);
    }
    assertEquals(1.0, betaSum, 1.0e-8);

    for (int componentIndex = 0; componentIndex < system.getPhase(0).getNumberOfComponents(); componentIndex++) {
      ComponentInterface referenceComponent = system.getPhase(0).getComponent(componentIndex);
      double calculatedFeedFraction = 0.0;
      for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
        calculatedFeedFraction += system.getBeta(phaseIndex)
            * system.getPhase(phaseIndex).getComponent(componentIndex).getx();
      }
      assertEquals(referenceComponent.getz(), calculatedFeedFraction, MATERIAL_BALANCE_TOLERANCE,
          referenceComponent.getComponentName());

      if (referenceComponent.getz() <= 1.0e-30 || referenceComponent.getIonicCharge() != 0
          || referenceComponent.isIsIon()) {
        continue;
      }
      double referenceLogFugacity = Double.NaN;
      for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
        if (system.getBeta(phaseIndex) <= MATERIAL_PHASE_FRACTION) {
          continue;
        }
        ComponentInterface phaseComponent = system.getPhase(phaseIndex).getComponent(componentIndex);
        double logFugacity = Math.log(phaseComponent.getx() * phaseComponent.getFugacityCoefficient()
            * system.getPhase(phaseIndex).getPressure());
        assertTrue(Double.isFinite(logFugacity), referenceComponent.getComponentName());
        if (Double.isNaN(referenceLogFugacity)) {
          referenceLogFugacity = logFugacity;
        } else {
          assertEquals(referenceLogFugacity, logFugacity, LOG_FUGACITY_TOLERANCE,
              referenceComponent.getComponentName());
        }
      }
    }
  }

  /**
   * Assert reactive carbonate formation and a finite, repeatable Pitzer calcite saturation ratio.
   *
   * @param system flashed reactive Pitzer system
   * @param operations operations bound to the flashed system
   * @param conservedQuantities element and charge quantities before the flash
   * @param initialCarbonateMoles carbonate trace amount before chemical equilibrium
   * @throws Exception if scale-potential evaluation fails
   */
  private void assertReactiveCarbonateScaleResult(SystemPitzer system, ThermodynamicOperations operations,
      double[] conservedQuantities, double initialCarbonateMoles) throws Exception {
    PhaseInterface aqueous = findPhase(system, PhaseType.AQUEOUS);
    assertTrue(aqueous instanceof PhasePitzer);
    assertTrue(system.isChemicalSystem());
    assertTrue(aqueous.hasComponent("HCO3-"));
    assertTrue(aqueous.hasComponent("CO3--"));
    double bicarbonateMoles = aqueous.getComponent("HCO3-").getNumberOfMolesInPhase();
    double carbonateMoles = aqueous.getComponent("CO3--").getNumberOfMolesInPhase();
    assertTrue(bicarbonateMoles > 0.0);
    assertTrue(carbonateMoles > 0.0, "Aqueous bicarbonate reactions must retain carbonate: " + carbonateMoles);
    assertTrue(Math.abs(carbonateMoles - initialCarbonateMoles) > initialCarbonateMoles * 1.0e-3,
        "Aqueous chemical equilibrium must change carbonate from its trace seed");
    assertReactiveConservedQuantities(system, conservedQuantities);

    double firstScalePotential = operations.getRelativeScalePotential("CaCO3");
    assertTrue(Double.isFinite(firstScalePotential) && firstScalePotential > 0.0,
        "Pitzer calcite scale potential must be finite and positive");

    operations.TPflash();
    double repeatedScalePotential = operations.getRelativeScalePotential("CaCO3");
    assertReactiveConservedQuantities(system, conservedQuantities);
    assertEquals(firstScalePotential, repeatedScalePotential, Math.max(1.0e-8, Math.abs(firstScalePotential) * 1.0e-4),
        "Repeated reactive hybrid flashes must retain the same calcite scale potential");
  }

  /**
   * Calculate the chemical subsystem's conserved element and charge quantities.
   *
   * @param system reactive system
   * @param useTotalComponentMoles whether to read the feed totals instead of summing active phases
   * @return element quantities followed by net ionic charge
   */
  private double[] getReactiveConservedQuantities(SystemInterface system, boolean useTotalComponentMoles) {
    ChemicalReactionOperations reactions = system.getChemicalReactionOperations();
    ComponentInterface[] reactiveComponents = reactions.getComponents();
    double[][] conservationMatrix = reactions.getAmatrix();
    double[] quantities = new double[conservationMatrix.length];

    for (int reactiveIndex = 0; reactiveIndex < reactiveComponents.length; reactiveIndex++) {
      int componentIndex = reactiveComponents[reactiveIndex].getComponentNumber();
      double componentMoles = 0.0;
      if (useTotalComponentMoles) {
        componentMoles = system.getPhase(0).getComponent(componentIndex).getNumberOfmoles();
      } else {
        for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
          componentMoles += system.getPhase(phaseIndex).getComponent(componentIndex).getNumberOfMolesInPhase();
        }
      }
      for (int quantityIndex = 0; quantityIndex < conservationMatrix.length - 1; quantityIndex++) {
        quantities[quantityIndex] += conservationMatrix[quantityIndex][reactiveIndex] * componentMoles;
      }
    }
    for (int componentIndex = 0; componentIndex < system.getPhase(0).getNumberOfComponents(); componentIndex++) {
      double componentMoles = 0.0;
      if (useTotalComponentMoles) {
        componentMoles = system.getPhase(0).getComponent(componentIndex).getNumberOfmoles();
      } else {
        for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
          componentMoles += system.getPhase(phaseIndex).getComponent(componentIndex).getNumberOfMolesInPhase();
        }
      }
      quantities[quantities.length - 1] += system.getPhase(0).getComponent(componentIndex).getIonicCharge()
          * componentMoles;
    }
    return quantities;
  }

  /**
   * Assert conservation of every chemical element and net ionic charge through the coupled flash.
   *
   * @param system flashed reactive system
   * @param expectedQuantities element and charge quantities before the flash
   */
  private void assertReactiveConservedQuantities(SystemInterface system, double[] expectedQuantities) {
    double[] actualQuantities = getReactiveConservedQuantities(system, false);
    assertEquals(expectedQuantities.length, actualQuantities.length);
    for (int quantityIndex = 0; quantityIndex < expectedQuantities.length; quantityIndex++) {
      double tolerance = quantityIndex == expectedQuantities.length - 1 ? 1.0e-8
          : Math.max(1.0e-6, Math.abs(expectedQuantities[quantityIndex]) * 1.0e-7);
      assertEquals(expectedQuantities[quantityIndex], actualQuantities[quantityIndex], tolerance,
          "Reactive element/charge conservation row " + quantityIndex);
    }
    for (int componentIndex = 0; componentIndex < system.getPhase(0).getNumberOfComponents(); componentIndex++) {
      ComponentInterface component = system.getPhase(0).getComponent(componentIndex);
      if (component.getIonicCharge() == 0 && !component.isIsIon()) {
        continue;
      }
      double actualMoles = 0.0;
      for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
        actualMoles += system.getPhase(phaseIndex).getComponent(componentIndex).getNumberOfMolesInPhase();
      }
      assertEquals(component.getNumberOfmoles(), actualMoles,
          Math.max(1.0e-12, Math.abs(component.getNumberOfmoles()) * 1.0e-8),
          "Reactive ionic component balance for " + component.getComponentName());
    }
  }

  /**
   * Check whether an active phase type is present.
   *
   * @param system system to inspect
   * @param phaseType phase type
   * @return true when present
   */
  private boolean hasPhaseType(SystemInterface system, PhaseType phaseType) {
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      if (system.getPhase(phaseIndex).getType() == phaseType) {
        return true;
      }
    }
    return false;
  }

  /**
   * Find an active phase by type.
   *
   * @param system system to inspect
   * @param phaseType phase type
   * @return matching phase
   */
  private PhaseInterface findPhase(SystemInterface system, PhaseType phaseType) {
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      if (system.getPhase(phaseIndex).getType() == phaseType) {
        return system.getPhase(phaseIndex);
      }
    }
    throw new AssertionError("Missing phase type " + phaseType);
  }

  /**
   * Sum active phase fractions.
   *
   * @param system system to inspect
   * @return active phase-fraction sum
   */
  private double betaSum(SystemInterface system) {
    double total = 0.0;
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      total += system.getBeta(phaseIndex);
    }
    return total;
  }

  /**
   * Find the active fraction of a phase type.
   *
   * @param system system to inspect
   * @param phaseType requested phase type
   * @return matching phase fraction
   */
  private double findPhaseBeta(SystemInterface system, PhaseType phaseType) {
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      if (system.getPhase(phaseIndex).getType() == phaseType) {
        return system.getBeta(phaseIndex);
      }
    }
    throw new AssertionError("Missing phase type " + phaseType);
  }

  /**
   * Format active phase fractions for assertion diagnostics.
   *
   * @param system system to inspect
   * @return phase diagnostics
   */
  private String phaseDiagnostics(SystemInterface system) {
    StringBuilder diagnostics = new StringBuilder();
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      if (phaseIndex > 0) {
        diagnostics.append(", ");
      }
      diagnostics.append(system.getPhase(phaseIndex).getType()).append('[').append(system.getPhaseIndex(phaseIndex))
          .append(':').append(system.getPhase(phaseIndex).getClass().getSimpleName()).append("]=")
          .append(system.getBeta(phaseIndex));
    }
    return diagnostics.toString();
  }
}
