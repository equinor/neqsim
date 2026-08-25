package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPitzer;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Scientific, continuation, and process tests for competing pure-mineral equilibrium. */
class MultiSaltPrecipitationTest extends neqsim.NeqSimTest {

  @Test
  void competingCalciumSulfatePolymorphsSelectLowerKspIndependentOfInputOrder() {
    SystemPitzer forwardSystem = createPitzerBrine(false);
    double initialCalcium = forwardSystem.getComponent("Ca++").getNumberOfmoles();
    double initialSulfate = forwardSystem.getComponent("SO4--").getNumberOfmoles();
    MultiSaltPrecipitationResult forward = new ThermodynamicOperations(forwardSystem).precipitateScales("CaSO4_A",
        "CaSO4_G");

    assertStableGypsumTopology(forward);
    assertEquals(initialCalcium,
        forwardSystem.getComponent("Ca++").getNumberOfmoles()
            + forward.getMineralResult("CaSO4_A").getPrecipitatedMoles()
            + forward.getMineralResult("CaSO4_G").getPrecipitatedMoles(),
        1.0e-10);
    assertEquals(initialSulfate,
        forwardSystem.getComponent("SO4--").getNumberOfmoles()
            + forward.getMineralResult("CaSO4_A").getPrecipitatedMoles()
            + forward.getMineralResult("CaSO4_G").getPrecipitatedMoles(),
        1.0e-10);
    assertAqueousChargeAndPhaseState(forwardSystem);

    SystemPitzer reverseSystem = createPitzerBrine(false);
    MultiSaltPrecipitationResult reverse = new ThermodynamicOperations(reverseSystem).precipitateScales("CaSO4_G",
        "CaSO4_A");
    assertStableGypsumTopology(reverse);
    assertEquals(forward.getMineralResult("CaSO4_G").getPrecipitatedMoles(),
        reverse.getMineralResult("CaSO4_G").getPrecipitatedMoles(), 1.0e-10);
    assertEquals(forwardSystem.getComponent("Ca++").getNumberOfmoles(),
        reverseSystem.getComponent("Ca++").getNumberOfmoles(), 1.0e-10);
  }

  @Test
  void existingSolidLedgerIsRepeatableAndRedissolvesAfterDilution() {
    SystemPitzer system = createPitzerBrine(false);
    ThermodynamicOperations operations = new ThermodynamicOperations(system);
    MultiSaltPrecipitationResult initial = operations.precipitateScales("CaSO4_A", "CaSO4_G");

    MultiSaltPrecipitationResult repeated = operations.equilibrateScales(initial);
    assertEquals(initial.getMineralResult("CaSO4_G").getPrecipitatedMoles(),
        repeated.getMineralResult("CaSO4_G").getPrecipitatedMoles(), 1.0e-10);
    assertTrue(repeated.getMaximumComplementarityViolation() <= 1.0e-6);

    system.addComponent("water", 55.508);
    system.init(0);
    MultiSaltPrecipitationResult diluted = operations.equilibrateScales(repeated);
    assertTrue(diluted.getMineralResult("CaSO4_G").getPrecipitatedMoles() < repeated.getMineralResult("CaSO4_G")
        .getPrecipitatedMoles());
    assertTrue(diluted.getMaximumComplementarityViolation() <= 1.0e-6);
    assertTrue(diluted.getMaximumComponentBalanceResidualMoles() <= 1.0e-10);
    assertAqueousChargeAndPhaseState(system);
  }

  @Test
  void gasOilAqueousResultRemainsProcessComposableAndSerializable() throws Exception {
    SystemPitzer system = createPitzerBrine(true);
    MultiSaltPrecipitationResult result = new ThermodynamicOperations(system).precipitateScales("CaSO4_A", "CaSO4_G");

    Stream feed = new Stream("scale-equilibrated feed", system);
    Heater heater = new Heater("electrolyte heater", feed);
    heater.setOutTemperature(308.15);
    ProcessSystem process = new ProcessSystem("multi-mineral electrolyte process smoke test");
    process.add(feed);
    process.add(heater);
    process.run();

    SystemInterface outlet = heater.getOutletStream().getThermoSystem();
    assertEquals(system.getTotalNumberOfMoles(), outlet.getTotalNumberOfMoles(), 1.0e-10);
    assertEquals(outlet.getEnthalpy() - feed.getFluid().getEnthalpy(), heater.getDuty(),
        Math.max(1.0e-6, Math.abs(heater.getDuty()) * 1.0e-12));
    assertTrue(outlet.hasPhaseType("gas"));
    assertTrue(outlet.hasPhaseType("oil"));
    assertTrue(outlet.hasPhaseType("aqueous"));
    assertFinitePropertiesAndIonConfinement(outlet);
    MultiSaltPrecipitationResult outletResult = new ThermodynamicOperations(outlet).equilibrateScales(result);
    assertTrue(outletResult.getMaximumComplementarityViolation() <= 1.0e-6);
    assertTrue(outletResult.getMaximumComponentBalanceResidualMoles() <= 1.0e-10);

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(outletResult);
    }
    MultiSaltPrecipitationResult restored;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      restored = (MultiSaltPrecipitationResult) input.readObject();
    }
    assertEquals(outletResult.getTotalPrecipitatedMassGrams(), restored.getTotalPrecipitatedMassGrams(), 0.0);
    Map<String, SaltPrecipitationResult> defensiveResults = restored.getMineralResults();
    assertThrows(UnsupportedOperationException.class,
        () -> defensiveResults.put("unexpected", restored.getMineralResult("CaSO4_G")));
  }

  @Test
  void clonesAreDeterministicAndIndependentSystemsCanRunInParallel() throws Exception {
    SystemPitzer baseSystem = createPitzerBrine(false);
    SystemInterface firstSystem = baseSystem.clone();
    SystemInterface secondSystem = baseSystem.clone();
    MultiSaltPrecipitationResult firstCloneResult = new ThermodynamicOperations(firstSystem)
        .precipitateScales("CaSO4_A", "CaSO4_G");
    MultiSaltPrecipitationResult secondCloneResult = new ThermodynamicOperations(secondSystem)
        .precipitateScales("CaSO4_G", "CaSO4_A");
    assertStableGypsumTopology(firstCloneResult);
    assertStableGypsumTopology(secondCloneResult);
    assertEquals(firstCloneResult.getMineralResult("CaSO4_G").getPrecipitatedMoles(),
        secondCloneResult.getMineralResult("CaSO4_G").getPrecipitatedMoles(), 1.0e-10);

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<MultiSaltPrecipitationResult> first = executor
          .submit(() -> new ThermodynamicOperations(createPitzerBrine(false)).precipitateScales("CaSO4_A", "CaSO4_G"));
      Future<MultiSaltPrecipitationResult> second = executor
          .submit(() -> new ThermodynamicOperations(createPitzerBrine(false)).precipitateScales("CaSO4_G", "CaSO4_A"));
      MultiSaltPrecipitationResult firstResult = first.get();
      MultiSaltPrecipitationResult secondResult = second.get();
      assertStableGypsumTopology(firstResult);
      assertStableGypsumTopology(secondResult);
      assertEquals(firstResult.getMineralResult("CaSO4_G").getPrecipitatedMoles(),
          secondResult.getMineralResult("CaSO4_G").getPrecipitatedMoles(), 1.0e-10);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void electrolyteEosUsesItsOwnActivitiesAndInvalidRequestsFailClosed() throws Exception {
    SystemInterface system = new SystemElectrolyteCPAstatoil(298.15, 1.01325);
    system.addComponent("water", 55.508);
    system.addComponent("Na+", 1.0);
    system.addComponent("Ca++", 0.2);
    system.addComponent("Cl-", 1.0);
    system.addComponent("SO4--", 0.2);
    system.chemicalReactionInit();
    system.createDatabase(true);
    system.setMixingRule(10);
    system.setMultiPhaseCheck(true);

    MultiSaltPrecipitationResult result = new ThermodynamicOperations(system).precipitateScales("CaSO4_A", "CaSO4_G");
    assertStableGypsumTopology(result);
    assertTrue(result.getMaximumComponentBalanceResidualMoles() <= 1.0e-10);

    ThermodynamicOperations operations = new ThermodynamicOperations(system);
    assertThrows(IllegalArgumentException.class, () -> operations.precipitateScales("CaSO4_A", "CaSO4_A"));
    assertThrows(IllegalArgumentException.class, () -> operations.precipitateScales("NOT_A_COMPSALT_MINERAL"));
  }

  private static void assertStableGypsumTopology(MultiSaltPrecipitationResult result) {
    SaltPrecipitationResult anhydrite = result.getMineralResult("CaSO4_A");
    SaltPrecipitationResult gypsum = result.getMineralResult("CaSO4_G");
    assertFalse(anhydrite.hasPrecipitatedSolid());
    assertTrue(anhydrite.getFinalSaturationRatio() < 1.0);
    assertTrue(gypsum.hasPrecipitatedSolid());
    assertEquals(1.0, gypsum.getFinalSaturationRatio(), 1.0e-6);
    assertTrue(result.getMaximumComplementarityViolation() <= 1.0e-6);
    assertTrue(result.getMaximumComponentBalanceResidualMoles() <= 1.0e-10);
  }

  private static SystemPitzer createPitzerBrine(boolean includeHydrocarbons) {
    SystemPitzer system = new SystemPitzer(298.15, includeHydrocarbons ? 50.0 : 1.01325);
    system.addComponent("water", 55.508);
    system.addComponent("Na+", 1.0);
    system.addComponent("Ca++", 0.2);
    system.addComponent("Mg++", 0.0);
    system.addComponent("Cl-", 1.0);
    system.addComponent("SO4--", 0.2);
    system.init(0);
    system.applyPhreeqcCalciumMagnesiumChlorideSulfateParameters();
    if (includeHydrocarbons) {
      system.addComponent("methane", 5.0);
      system.addComponent("n-heptane", 2.0);
    }
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(true);
    return system;
  }

  private static void assertAqueousChargeAndPhaseState(SystemPitzer system) {
    int aqueousPhaseNumber = system.getPhaseNumberOfPhase("aqueous");
    PhaseInterface aqueous = system.getPhase(aqueousPhaseNumber >= 0 ? aqueousPhaseNumber : 1);
    double moleFractionSum = 0.0;
    double chargeMolality = 0.0;
    for (int componentIndex = 0; componentIndex < aqueous.getNumberOfComponents(); componentIndex++) {
      ComponentInterface component = aqueous.getComponent(componentIndex);
      assertTrue(Double.isFinite(component.getx()) && component.getx() >= 0.0);
      moleFractionSum += component.getx();
      chargeMolality += component.getMolality(aqueous) * component.getIonicCharge();
    }
    assertEquals(1.0, moleFractionSum, 1.0e-12);
    assertEquals(0.0, chargeMolality, 1.0e-10);
  }

  private static void assertFinitePropertiesAndIonConfinement(SystemInterface system) {
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      PhaseInterface phase = system.getPhase(phaseIndex);
      assertTrue(Double.isFinite(phase.getDensity()) && phase.getDensity() > 0.0);
      assertTrue(Double.isFinite(phase.getEnthalpy()));
      assertTrue(Double.isFinite(phase.getCp()) && phase.getCp() > 0.0);
      for (int componentIndex = 0; componentIndex < phase.getNumberOfComponents(); componentIndex++) {
        ComponentInterface component = phase.getComponent(componentIndex);
        if ((component.isIsIon() || component.getIonicCharge() != 0.0) && phase.getType() != PhaseType.AQUEOUS) {
          assertTrue(component.getx() <= 1.0e-40, component.getComponentName() + " escaped the aqueous phase");
        }
      }
    }
  }
}
