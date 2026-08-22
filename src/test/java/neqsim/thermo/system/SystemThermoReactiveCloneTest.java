package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import neqsim.chemicalreactions.chemicalreaction.ChemicalReaction;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Regression tests for independent chemical-reaction state in thermodynamic-system clones. */
class SystemThermoReactiveCloneTest extends neqsim.NeqSimTest {
  private static final double BALANCE_TOLERANCE = 2.0e-8;
  private static final double PHASE_FRACTION_TOLERANCE = 2.0e-10;
  private static final double STATE_TOLERANCE = 2.0e-8;

  @Test
  void reactiveCloneOwnsChemicalReactionOperations() {
    SystemInterface original = createReactiveBrine(298.15, 10.0, 0.01);
    flash(original);

    SystemInterface clone = original.clone();

    assertTrue(original.isChemicalSystem());
    assertNotNull(original.getChemicalReactionOperations());
    assertNotNull(clone.getChemicalReactionOperations());
    assertNotSame(original.getChemicalReactionOperations(), clone.getChemicalReactionOperations(),
        "A reactive clone must not share mutable chemical-reaction operations with its source");
  }

  @Test
  void changedReactiveCloneLeavesSourceUnchangedAndMatchesFreshCalculation() {
    SystemInterface original = createReactiveBrine(298.15, 10.0, 0.01);
    flash(original);
    SystemState sourceBefore = SystemState.capture(original);

    SystemInterface changedClone = original.clone();
    changedClone.setTemperature(303.15);
    changedClone.setPressure(14.0);
    changedClone.addComponent("Na+", 0.002);
    changedClone.addComponent("Cl-", 0.002);
    changedClone.addComponent("water", -0.004);
    flash(changedClone);

    SystemInterface freshReference = createReactiveBrine(303.15, 14.0, 0.012);
    flash(freshReference);

    sourceBefore.assertMatches(original, 0.0);
    assertSamePhaseState(freshReference, changedClone, STATE_TOLERANCE);
    assertPhysicalAndChemicalGates(changedClone, expectedElements(0.012));

    SystemState firstResult = SystemState.capture(changedClone);
    flash(changedClone);
    firstResult.assertMatches(changedClone, STATE_TOLERANCE);
    sourceBefore.assertMatches(original, 0.0);
  }

  @Test
  void reactiveClonesCanBeSolvedConcurrentlyAndSurviveSerialization() throws Exception {
    SystemInterface original = createReactiveBrine(298.15, 10.0, 0.01);
    flash(original);
    SystemState sourceBefore = SystemState.capture(original);

    final SystemInterface warmerClone = roundTrip(original.clone());
    final SystemInterface coolerClone = original.clone();
    assertNotSame(warmerClone.getChemicalReactionOperations(), coolerClone.getChemicalReactionOperations());

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<SystemInterface> warmer = executor.submit(() -> solveChanged(warmerClone, 306.15, 18.0, 0.002));
      Future<SystemInterface> cooler = executor.submit(() -> solveChanged(coolerClone, 293.15, 7.0, -0.002));

      SystemInterface warmResult = warmer.get();
      SystemInterface coolResult = cooler.get();
      SystemInterface warmReference = createReactiveBrine(306.15, 18.0, 0.012);
      SystemInterface coolReference = createReactiveBrine(293.15, 7.0, 0.008);
      flash(warmReference);
      flash(coolReference);

      assertSamePhaseState(warmReference, warmResult, STATE_TOLERANCE);
      assertSamePhaseState(coolReference, coolResult, STATE_TOLERANCE);
      assertPhysicalAndChemicalGates(warmResult, expectedElements(0.012));
      assertPhysicalAndChemicalGates(coolResult, expectedElements(0.008));
      sourceBefore.assertMatches(original, 0.0);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void neutralCloneDoesNotInitializeChemicalReactionState() {
    SystemInterface neutral = new SystemSrkEos(298.15, 10.0);
    neutral.addComponent("methane", 0.9);
    neutral.addComponent("CO2", 0.1);

    SystemInterface clone = neutral.clone();

    assertNull(neutral.getChemicalReactionOperations());
    assertNull(clone.getChemicalReactionOperations());
    assertEquals(neutral.getTotalNumberOfMoles(), clone.getTotalNumberOfMoles(), 0.0);
  }

  private static SystemInterface solveChanged(SystemInterface fluid, double temperature, double pressure,
      double sodiumChlorideDelta) {
    fluid.setTemperature(temperature);
    fluid.setPressure(pressure);
    fluid.addComponent("Na+", sodiumChlorideDelta);
    fluid.addComponent("Cl-", sodiumChlorideDelta);
    fluid.addComponent("water", -2.0 * sodiumChlorideDelta);
    flash(fluid);
    return fluid;
  }

  private static void flash(SystemInterface fluid) {
    new ThermodynamicOperations(fluid).TPflash();
  }

  private static void assertPhysicalAndChemicalGates(SystemInterface fluid, Map<String, Double> expectedElements) {
    assertEquals(1.0, fluid.getTotalNumberOfMoles(), BALANCE_TOLERANCE,
        "Reactive brine must retain its total molar inventory");
    double betaSum = 0.0;
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      PhaseInterface phaseState = fluid.getPhase(phase);
      betaSum += fluid.getBeta(phase);
      assertTrue(fluid.getBeta(phase) >= 0.0, "Phase fractions must be non-negative");
      double moleFractionSum = 0.0;
      for (int component = 0; component < phaseState.getNumberOfComponents(); component++) {
        ComponentInterface componentState = phaseState.getComponent(component);
        assertTrue(componentState.getNumberOfMolesInPhase() >= -1.0e-14,
            "Phase component amounts must be non-negative");
        assertTrue(componentState.getx() >= -1.0e-14, "Phase mole fractions must be non-negative");
        moleFractionSum += componentState.getx();
      }
      assertEquals(1.0, moleFractionSum, 2.0e-10, "Each active phase must be normalized");
    }
    assertEquals(1.0, betaSum, PHASE_FRACTION_TOLERANCE, "Active phase fractions must be normalized");

    Map<String, Double> actualElements = calculateElementInventory(fluid);
    for (Map.Entry<String, Double> expected : expectedElements.entrySet()) {
      assertEquals(expected.getValue(), actualElements.get(expected.getKey()), BALANCE_TOLERANCE,
          "Element balance failed for " + expected.getKey());
    }

    int aqueousPhase = fluid.getPhaseNumberOfPhase("aqueous");
    PhaseInterface aqueous = fluid.getPhase(aqueousPhase);
    double charge = 0.0;
    for (int component = 0; component < aqueous.getNumberOfComponents(); component++) {
      ComponentInterface species = aqueous.getComponent(component);
      charge += species.getNumberOfMolesInPhase() * species.getIonicCharge();
    }
    assertEquals(0.0, charge, BALANCE_TOLERANCE, "Aqueous phase must be electroneutral");

    double maximumLogReactionResidual = 0.0;
    for (ChemicalReaction reaction : fluid.getChemicalReactionOperations().getReactionList()
        .getChemicalReactionList()) {
      double reactionQuotient = reaction.calcK(fluid, aqueousPhase);
      double equilibriumConstant = reaction.getK(aqueous);
      maximumLogReactionResidual = Math.max(maximumLogReactionResidual,
          Math.abs(Math.log(reactionQuotient / equilibriumConstant)));
    }
    assertTrue(maximumLogReactionResidual <= 2.0e-6, "Maximum absolute ln(Q/K) was " + maximumLogReactionResidual);
  }

  private static Map<String, Double> calculateElementInventory(SystemInterface fluid) {
    Map<String, Double> inventory = new LinkedHashMap<String, Double>();
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      PhaseInterface phaseState = fluid.getPhase(phase);
      for (int component = 0; component < phaseState.getNumberOfComponents(); component++) {
        ComponentInterface species = phaseState.getComponent(component);
        String[] names = species.getElements().getElementNames();
        double[] coefficients = species.getElements().getElementCoefs();
        for (int element = 0; element < names.length; element++) {
          Double current = inventory.get(names[element]);
          inventory.put(names[element], (current == null ? 0.0 : current.doubleValue())
              + species.getNumberOfMolesInPhase() * coefficients[element]);
        }
      }
    }
    return inventory;
  }

  private static Map<String, Double> expectedElements(double sodiumChlorideMoles) {
    double waterMoles = 1.0 - 2.0 * sodiumChlorideMoles;
    Map<String, Double> expected = new LinkedHashMap<String, Double>();
    expected.put("H", 2.0 * waterMoles);
    expected.put("O", waterMoles);
    expected.put("Na", sodiumChlorideMoles);
    expected.put("Cl", sodiumChlorideMoles);
    return expected;
  }

  private static void assertSamePhaseState(SystemInterface expected, SystemInterface actual, double tolerance) {
    assertEquals(expected.getNumberOfPhases(), actual.getNumberOfPhases());
    for (int expectedPhase = 0; expectedPhase < expected.getNumberOfPhases(); expectedPhase++) {
      String phaseType = expected.getPhase(expectedPhase).getPhaseTypeName();
      int actualPhase = actual.getPhaseNumberOfPhase(phaseType);
      assertEquals(expected.getBeta(expectedPhase), actual.getBeta(actualPhase), tolerance,
          "Phase fraction mismatch for " + phaseType);
      PhaseInterface expectedState = expected.getPhase(expectedPhase);
      PhaseInterface actualState = actual.getPhase(actualPhase);
      for (int component = 0; component < expectedState.getNumberOfComponents(); component++) {
        String componentName = expectedState.getComponent(component).getComponentName();
        assertEquals(expectedState.getComponent(component).getNumberOfMolesInPhase(),
            actualState.getComponent(componentName).getNumberOfMolesInPhase(), tolerance,
            "Phase amount mismatch for " + phaseType + "/" + componentName);
      }
    }
  }

  private static SystemInterface createReactiveBrine(double temperature, double pressure, double sodiumChlorideMoles) {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(temperature, pressure);
    fluid.addComponent("water", 1.0 - 2.0 * sodiumChlorideMoles);
    fluid.addComponent("Na+", sodiumChlorideMoles);
    fluid.addComponent("Cl-", sodiumChlorideMoles);
    fluid.chemicalReactionInit();
    fluid.createDatabase(true);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  private static SystemInterface roundTrip(SystemInterface fluid) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(fluid);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return (SystemInterface) input.readObject();
    }
  }

  private static final class SystemState {
    private final double temperature;
    private final double pressure;
    private final double totalMoles;
    private final int numberOfPhases;
    private final Map<String, Double> componentMoles;
    private final Map<String, Double> phaseComponentMoles;

    private SystemState(SystemInterface fluid) {
      temperature = fluid.getTemperature();
      pressure = fluid.getPressure();
      totalMoles = fluid.getTotalNumberOfMoles();
      numberOfPhases = fluid.getNumberOfPhases();
      componentMoles = new LinkedHashMap<String, Double>();
      phaseComponentMoles = new LinkedHashMap<String, Double>();
      for (int component = 0; component < fluid.getPhase(0).getNumberOfComponents(); component++) {
        ComponentInterface species = fluid.getPhase(0).getComponent(component);
        componentMoles.put(species.getComponentName(), species.getNumberOfmoles());
      }
      for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
        PhaseInterface phaseState = fluid.getPhase(phase);
        for (int component = 0; component < phaseState.getNumberOfComponents(); component++) {
          ComponentInterface species = phaseState.getComponent(component);
          phaseComponentMoles.put(phaseState.getPhaseTypeName() + "/" + species.getComponentName(),
              species.getNumberOfMolesInPhase());
        }
      }
    }

    private static SystemState capture(SystemInterface fluid) {
      return new SystemState(fluid);
    }

    private void assertMatches(SystemInterface actual, double tolerance) {
      assertEquals(temperature, actual.getTemperature(), tolerance);
      assertEquals(pressure, actual.getPressure(), tolerance);
      assertEquals(totalMoles, actual.getTotalNumberOfMoles(), tolerance);
      assertEquals(numberOfPhases, actual.getNumberOfPhases());
      for (Map.Entry<String, Double> component : componentMoles.entrySet()) {
        assertEquals(component.getValue(), actual.getPhase(0).getComponent(component.getKey()).getNumberOfmoles(),
            tolerance, "Overall amount changed for " + component.getKey());
      }
      for (Map.Entry<String, Double> component : phaseComponentMoles.entrySet()) {
        String[] key = component.getKey().split("/", 2);
        PhaseInterface phase = actual.getPhase(actual.getPhaseNumberOfPhase(key[0]));
        assertEquals(component.getValue(), phase.getComponent(key[1]).getNumberOfMolesInPhase(), tolerance,
            "Phase amount changed for " + component.getKey());
      }
    }
  }
}
