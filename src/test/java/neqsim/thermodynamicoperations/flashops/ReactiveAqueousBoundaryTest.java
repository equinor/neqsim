package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.chemicalreactions.chemicalreaction.ChemicalReaction;
import neqsim.chemicalreactions.chemicalreaction.ChemicalReactionConcentrationBasis;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.thermodynamicoperations.flashops.reactiveflash.FormulaMatrix;

/**
 * Conserved reactive phase appearance and disappearance, including an independent unstable aqueous trial.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
@Tag("slow")
class ReactiveAqueousBoundaryTest extends neqsim.NeqSimTest {
  /**
   * A separated gas can repeatedly condense, dissociate and recombine without losing the reaction network.
   *
   * @param scale overall material scale
   */
  @ParameterizedTest
  @ValueSource(doubles = {0.01, 1.0, 100.0})
  void aqueousPhaseCanDisappearAndReappearConservatively(double scale) {
    SystemInterface gas = condensedGas(scale);
    FormulaMatrix formula = new FormulaMatrix(gas);
    double[] elements = formula.computeElementVector(FluidEquilibriumChecks.inventory(gas));
    int components = gas.getNumberOfComponents();
    for (int cycle = 0; cycle < 2; cycle++) {
      gas.setTemperature(320.0);
      ReactiveAqueousBoundary.Result qualified = ReactiveAqueousBoundary.trySolve(gas);
      assertNotNull(qualified, "warm neutral endpoint must be qualified against reactive aqueous starts");
      assertTrue(qualified.getTrialDistances().length >= 3);
      for (double distance : qualified.getTrialDistances()) {
        assertTrue(Double.isFinite(distance) && distance >= -1.0e-8);
      }
      new ThermodynamicOperations(gas).TPflash();
      assertEquals(1, gas.getNumberOfPhases());
      assertEquals(PhaseType.GAS, gas.getPhase(0).getType());
      assertFalse(FlashPhaseConstraints.hasIonicInventory(gas));
      assertEquals(components, gas.getNumberOfComponents());
      assertTrue(gas.isChemicalSystem());
      assertArrayEquals(elements, formula.computeElementVector(FluidEquilibriumChecks.inventory(gas)), scale * 1.0e-11);
      FluidEquilibriumChecks.validate(gas, FluidEquilibriumChecks.inventory(gas));
      gas.setTemperature(280.0);
      new ThermodynamicOperations(gas).TPflash();
      assertTrue(gas.hasPhaseType(PhaseType.AQUEOUS));
      assertTrue(FlashPhaseConstraints.hasIonicInventory(gas));
      assertTrue(gas.getChemicalReactionOperations().getMaximumAbsoluteReactionLogResidual() <= 2.0e-6);
      assertEquals(0.0, gas.getComponent("Na+").getNumberOfmoles(), 0.0);
      assertEquals(0.0, gas.getComponent("Cl-").getNumberOfmoles(), 0.0);
      assertArrayEquals(elements, formula.computeElementVector(FluidEquilibriumChecks.inventory(gas)), scale * 1.0e-11);
      FluidEquilibriumChecks.validate(gas, FluidEquilibriumChecks.inventory(gas));
    }
  }

  /** An absent molecular spectator cannot be introduced merely to seed a stability calculation. */
  @Test
  void absentMolecularRegistrationsRemainExactlyAbsent() {
    SystemInterface gas = condensedGas(1.0);
    gas.addComponent("nitrogen", 0.0);
    gas.chemicalReactionInit();
    gas.setMixingRule(10);
    gas.setMixingRule(10);
    gas.setTemperature(320.0);
    ReactiveAqueousBoundary.Result result = ReactiveAqueousBoundary.trySolve(gas);
    assertNotNull(result);
    assertEquals(0.0, gas.getComponent("nitrogen").getNumberOfmoles(), 0.0);
    assertEquals(0.0, result.getFluid().getComponent("nitrogen").getNumberOfmoles(), 0.0);
    assertEquals(gas.getNumberOfComponents(), result.getFluid().getNumberOfComponents());
  }

  /** A positive spectator inventory must block neutral recombination even far below conventional trace floors. */
  @Test
  void traceSpectatorsAreNeverDeletedToPermitDisappearance() {
    SystemInterface gas = condensedGas(1.0);
    gas.setTemperature(320.0);
    gas.addComponent("Na+", 1.0e-30);
    gas.addComponent("Cl-", 1.0e-30);
    double[] before = FluidEquilibriumChecks.inventory(gas);
    assertNull(ReactiveAqueousBoundary.trySolve(gas));
    assertArrayEquals(before, FluidEquilibriumChecks.inventory(gas), 0.0);
  }

  /**
   * Reactive aqueous minimization must detect the cold gas instability even if the neutral endpoint gate is bypassed.
   *
   * @throws ReflectiveOperationException if the independent internal stability calculation cannot be accessed
   */
  @Test
  void reactiveAqueousTrialRejectsMetastableColdGas() throws ReflectiveOperationException {
    SystemInterface cold = condensedGas(1.0);
    Constructor<ReactiveAqueousBoundary> constructor = ReactiveAqueousBoundary.class
        .getDeclaredConstructor(SystemInterface.class);
    constructor.setAccessible(true);
    ReactiveAqueousBoundary boundary = constructor.newInstance(cold);
    Method recombine = ReactiveAqueousBoundary.class.getDeclaredMethod("recombine");
    Method distances = ReactiveAqueousBoundary.class.getDeclaredMethod("trialDistances", SystemInterface.class);
    recombine.setAccessible(true);
    distances.setAccessible(true);
    double[] neutral = (double[]) recombine.invoke(boundary);
    SystemInterface metastable = cold.clone();
    metastable.setMolarFlowRates(neutral);
    metastable.isChemicalSystem(false);
    metastable.setNumberOfPhases(1);
    metastable.setPhaseType(0, PhaseType.GAS);
    metastable.setBeta(0, 1.0);
    double total = 0.0;
    for (double amount : neutral) {
      total += amount;
    }
    for (int i = 0; i < neutral.length; i++) {
      metastable.getPhase(0).getComponent(i).setx(neutral[i] / total);
      metastable.getPhase(0).getComponent(i).setNumberOfMolesInPhase(neutral[i]);
    }
    metastable.init(1);
    double[] trialDistances = (double[]) distances.invoke(boundary, metastable);
    double minimum = Double.POSITIVE_INFINITY;
    for (double distance : trialDistances) {
      minimum = Math.min(minimum, distance);
    }
    assertTrue(minimum < -0.1, "reaction-aware aqueous trial must find a clearly unstable cold gas");
    assertNull(ReactiveAqueousBoundary.trySolve(cold), "an existing stable aqueous phase must not be discarded");
  }

  /**
   * Neighboring pressure and temperature states retain the expected phase transition.
   *
   * @param pressure pressure in bara
   * @param warm warm temperature in kelvin
   * @param cold cold temperature in kelvin
   */
  @ParameterizedTest
  @CsvSource({"9.5,318.0,282.0", "10.5,322.0,278.0"})
  void neighboringConditionsRetainReactivePhaseTransition(double pressure, double warm, double cold) {
    SystemInterface gas = condensedGas(1.0);
    FormulaMatrix formula = new FormulaMatrix(gas);
    double[] elements = formula.computeElementVector(FluidEquilibriumChecks.inventory(gas));
    gas.setPressure(pressure);
    gas.setTemperature(warm);
    new ThermodynamicOperations(gas).TPflash();
    assertEquals(1, gas.getNumberOfPhases());
    assertEquals(PhaseType.GAS, gas.getPhase(0).getType());
    assertFalse(FlashPhaseConstraints.hasIonicInventory(gas));
    gas.setTemperature(cold);
    new ThermodynamicOperations(gas).TPflash();
    assertTrue(gas.hasPhaseType(PhaseType.AQUEOUS));
    assertTrue(gas.getChemicalReactionOperations().getMaximumAbsoluteReactionLogResidual() <= 2.0e-6);
    assertArrayEquals(elements, formula.computeElementVector(FluidEquilibriumChecks.inventory(gas)), 1.0e-11);
    FluidEquilibriumChecks.validate(gas, FluidEquilibriumChecks.inventory(gas));
  }

  /** Custom equilibrium and kinetics survive a boundary without rediscovering removed registered reactions. */
  @Test
  void boundaryPreservesRestrictedConfiguredReactionNetwork() {
    SystemInterface gas = condensedGas(1.0);
    gas.addComponent("CO2", 0.0);
    gas.chemicalReactionInit();
    gas.setMixingRule(10);
    gas.getChemicalReactionOperations().getReactionList().getChemicalReactionList()
        .removeIf(reaction -> !"waterreac".equals(reaction.getName()));
    double[] amounts = FluidEquilibriumChecks.inventory(gas);
    for (String name : new String[] {"CO2", "HCO3-", "CO3--"}) {
      amounts[gas.getComponent(name).getComponentNumber()] = 0.0;
    }
    gas.setMolarFlowRates(amounts);
    gas.getChemicalReactionOperations().reinitialize();
    ChemicalReaction configured = gas.getChemicalReactionOperations().getReactionList().getChemicalReactionList()
        .get(0);
    configured.setK(0, configured.getK()[0] + 0.4);
    configured.setReferenceKinetics(0.123, 50200.0, 310.0);
    double[] coefficients = configured.getEquilibriumConstantCoefficients().clone();
    int components = gas.getNumberOfComponents();
    gas.setTemperature(320.0);
    SystemInterface withoutRecombination = gas.clone();
    withoutRecombination.getChemicalReactionOperations().getReactionList().getChemicalReactionList().clear();
    assertNull(ReactiveAqueousBoundary.trySolve(withoutRecombination),
        "a removed reaction cannot be rediscovered to eliminate its ionic products");
    new ThermodynamicOperations(gas).TPflash();
    assertEquals(1, gas.getNumberOfPhases());
    assertEquals(1, gas.getChemicalReactionOperations().getReactionList().getChemicalReactionList().size());
    ChemicalReaction retained = gas.getChemicalReactionOperations().getReactionList().getChemicalReactionList().get(0);
    assertEquals("waterreac", retained.getName());
    assertArrayEquals(coefficients, retained.getEquilibriumConstantCoefficients(), 0.0);
    assertEquals(configured.getRateFactor(), retained.getRateFactor(), 0.0);
    assertEquals(configured.getActivationEnergy(), retained.getActivationEnergy(), 0.0);
    assertEquals(configured.getReferenceTemperature(), retained.getReferenceTemperature(), 0.0);
    assertEquals(components, gas.getNumberOfComponents());
    assertEquals(0.0, gas.getComponent("CO2").getNumberOfmoles(), 0.0);
    assertEquals(0.0, gas.getComponent("HCO3-").getNumberOfmoles(), 0.0);
    assertEquals(0.0, gas.getComponent("CO3--").getNumberOfmoles(), 0.0);
  }

  /** Fixed phase, solid, wax and other reaction concentration conventions retain their separate solver scope. */
  @Test
  void unsupportedRoutesDoNotEnterBoundarySearch() {
    SystemInterface gas = condensedGas(1.0);
    gas.setTemperature(320.0);
    double[] original = FluidEquilibriumChecks.inventory(gas);
    gas.setForcePhaseTypes(true);
    assertNull(ReactiveAqueousBoundary.trySolve(gas));
    gas.setForcePhaseTypes(false);
    gas.setSolidPhaseCheck(true);
    assertNull(ReactiveAqueousBoundary.trySolve(gas));
    gas.setSolidPhaseCheck(false);
    gas.setMultiphaseWaxCheck(true);
    assertNull(ReactiveAqueousBoundary.trySolve(gas));
    assertArrayEquals(original, FluidEquilibriumChecks.inventory(gas), 0.0);
    SystemInterface molality = new SystemElectrolyteCPAstatoil(320.0, 10.0) {
      /** Serialization version. */
      private static final long serialVersionUID = 1L;

      /** {@inheritDoc} */
      @Override
      public ChemicalReactionConcentrationBasis getChemicalReactionConcentrationBasis() {
        return ChemicalReactionConcentrationBasis.SOLUTE_MOLALITY;
      }
    };
    molality.addComponent("water", 1.0);
    molality.addComponent("methane", 0.1);
    molality.chemicalReactionInit();
    molality.setMixingRule(10);
    assertTrue(molality.hasIons());
    assertNotNull(molality.getChemicalReactionOperations());
    assertNull(ReactiveAqueousBoundary.trySolve(molality));
  }

  /** A legacy aqueous-only salt tag remains a spectator even though its stored charge is zero. */
  @Test
  void zeroChargeSaltTagStillBlocksNeutralization() {
    SystemInterface gas = condensedGas(1.0);
    gas.setTemperature(320.0);
    gas.addComponent("NaCl", 1.0e-30);
    gas.chemicalReactionInit();
    gas.setMixingRule(10);
    assertTrue(FlashPhaseConstraints.isIon(gas.getComponent("NaCl")));
    assertEquals(0.0, gas.getComponent("NaCl").getIonicCharge(), 0.0);
    double[] before = FluidEquilibriumChecks.inventory(gas);
    assertNull(ReactiveAqueousBoundary.trySolve(gas));
    assertArrayEquals(before, FluidEquilibriumChecks.inventory(gas), 0.0);
  }

  /**
   * Creates the separated stream from the reported brine cooling/heating cycle.
   *
   * @param scale overall material scale
   * @return condensed and chemically equilibrated stream at 280 K and 10 bara
   */
  private static SystemInterface condensedGas(double scale) {
    SystemInterface feed = new SystemElectrolyteCPAstatoil(298.15, 10.0);
    feed.addComponent("water", scale);
    feed.addComponent("methane", 0.1 * scale);
    feed.addComponent("Na+", 0.001 * scale);
    feed.addComponent("Cl-", 0.001 * scale);
    feed.setMixingRule(10);
    feed.setMultiPhaseCheck(true);
    feed.chemicalReactionInit();
    feed.setMixingRule(10);
    new ThermodynamicOperations(feed).TPflash();
    SystemInterface gas = feed.phaseToSystem("gas");
    new ThermodynamicOperations(gas).TPflash();
    gas.setTemperature(320.0);
    new ThermodynamicOperations(gas).TPflash();
    gas.setTemperature(280.0);
    new ThermodynamicOperations(gas).TPflash();
    assertTrue(gas.hasPhaseType(PhaseType.AQUEOUS));
    return gas;
  }
}
