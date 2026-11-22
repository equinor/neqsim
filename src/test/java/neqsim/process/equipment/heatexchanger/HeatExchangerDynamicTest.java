package neqsim.process.equipment.heatexchanger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.process.dynamics.DynamicActivationStatus;
import neqsim.process.dynamics.DynamicActivationResolver;
import neqsim.process.dynamics.DynamicCapability;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemWaterIF97;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemSeawater;

/**
 * Independent first-law, stiffness, restart and ownership checks for the lumped thermal model.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class HeatExchangerDynamicTest {
  @TempDir
  Path temporaryDirectory;

  /**
   * Creates a two-water-circuit fixture with temperature-dependent reference enthalpy.
   *
   * @return initialized conservative exchanger
   */
  private HeatExchanger exchanger() {
    SystemWaterIF97 first = new SystemWaterIF97(353.15, 5.0);
    first.setTotalFlowRate(1.0, "kg/sec");
    SystemWaterIF97 second = new SystemWaterIF97(293.15, 3.0);
    second.setTotalFlowRate(1.5, "kg/sec");
    Stream hot = new Stream("HOT", first);
    Stream cold = new Stream("COLD", second);
    hot.run();
    cold.run();
    HeatExchanger exchanger = new HeatExchanger("E-101", hot, cold);
    exchanger.setDynamicModelEnabled(true);
    exchanger.setWallMass(250.0);
    exchanger.setWallCp(500.0);
    exchanger.setShellHoldupVolume(0.20);
    exchanger.setTubeHoldupVolume(0.15);
    exchanger.setHeatTransferArea(10.0);
    exchanger.setShellSideHtc(700.0);
    exchanger.setTubeSideHtc(600.0);
    exchanger.run();
    return exchanger;
  }

  /** Verifies the actual enthalpy ledger at every interval, including both nonzero fluid inventories. */
  @Test
  void closesFluidAndMetalAccumulationAgainstActualBoundaryEnthalpy() {
    HeatExchanger exchanger = exchanger();
    exchanger.getInStream(0).setTemperature(90.0, "C");
    exchanger.getInStream(0).run();
    for (int step = 0; step < 40; step++) {
      double before = exchanger.getThermalInventoryEnergyJ();
      exchanger.runTransient(2.0, UUID.randomUUID());
      double boundary = 0.0;
      for (int side = 0; side < 2; side++) {
        boundary += 2.0 * (exchanger.getInStream(side).getFluid().getEnthalpy()
            - exchanger.getOutStream(side).getFluid().getEnthalpy());
        assertEquals(exchanger.getInStream(side).getFlowRate("kg/sec"),
            exchanger.getOutStream(side).getFlowRate("kg/sec"), 1e-12);
        assertEquals(exchanger.getInStream(side).getPressure(), exchanger.getOutStream(side).getPressure(), 1e-12);
      }
      assertEquals(boundary, exchanger.getThermalInventoryEnergyJ() - before, 1e-4);
      assertEquals(boundary, exchanger.getLastThermalBalance().getBoundaryEnergyJ(), 1e-6);
      assertEquals(0.0, exchanger.getLastThermalBalance().getResidualJ(), 1e-4);
    }
    assertEquals(DynamicCapability.DYNAMIC_LUMPED, exchanger.getDynamicCapability());
    assertEquals(DynamicActivationStatus.ACTIVE, DynamicActivationResolver.resolve(exchanger));
    assertTrue(Math.abs(exchanger.getDuty()) > 1000.0);
    assertEquals(exchanger.getDuty() / 1000.0, exchanger.getDuty("kW"), 1e-10);
  }

  /** Checks a locally independent constant-Cp backward-Euler solution using scalar Schur elimination. */
  @Test
  void agreesWithIndependentLinearizedThreeNodeBalance() {
    HeatExchanger exchanger = exchanger();
    double dt = 8.0;
    double[] previous = {exchanger.getOutStream(0).getTemperature(), exchanger.getOutStream(1).getTemperature(),
        exchanger.getWallTemperature()};
    double[] capacity = new double[2];
    double[] flowCapacity = new double[2];
    for (int side = 0; side < 2; side++) {
      double volume = side == 0 ? exchanger.getShellHoldupVolume() : exchanger.getTubeHoldupVolume();
      double cp = exchanger.getOutStream(side).getFluid().getCp("J/kgK");
      capacity[side] = volume * exchanger.getOutStream(side).getFluid().getDensity("kg/m3") * cp;
      flowCapacity[side] = exchanger.getInStream(side).getFlowRate("kg/sec") * cp;
    }
    exchanger.getInStream(0).setTemperature(exchanger.getInStream(0).getTemperature() + 0.01);
    exchanger.getInStream(0).run();
    double[] film = {7000.0, 6000.0};
    double[] diagonal = new double[2];
    double[] rhs = new double[2];
    double wallDiagonal = 250.0 * 500.0 / dt + film[0] + film[1];
    double wallRhs = 250.0 * 500.0 / dt * previous[2];
    for (int side = 0; side < 2; side++) {
      diagonal[side] = capacity[side] / dt + flowCapacity[side] + film[side];
      rhs[side] = capacity[side] / dt * previous[side]
          + flowCapacity[side] * exchanger.getInStream(side).getTemperature();
      wallDiagonal -= film[side] * film[side] / diagonal[side];
      wallRhs += film[side] * rhs[side] / diagonal[side];
    }
    double wallExpected = wallRhs / wallDiagonal;
    exchanger.runTransient(dt, UUID.randomUUID());
    assertEquals(wallExpected, exchanger.getWallTemperature(), 0.002);
    for (int side = 0; side < 2; side++) {
      assertEquals((rhs[side] + film[side] * wallExpected) / diagonal[side],
          exchanger.getOutStream(side).getTemperature(), 0.002);
    }
  }

  /** Tests passive temperature bounds over a stiff interval and rejects invalid requests before publication. */
  @Test
  void remainsBoundedForStiffStepsAndRejectsInvalidInputsAtomically() {
    HeatExchanger exchanger = exchanger();
    StreamInterface outlet = exchanger.getOutStream(0);
    double before = outlet.getTemperature();
    for (double dt : new double[] {-1.0, 0.0, Double.NaN, Double.POSITIVE_INFINITY}) {
      assertThrows(IllegalArgumentException.class, () -> exchanger.runTransient(dt, UUID.randomUUID()));
      assertEquals(before, outlet.getTemperature(), 0.0);
      assertEquals(0.0, exchanger.getTime(), 0.0);
    }
    exchanger.getInStream(0).setTemperature(100.0, "C");
    exchanger.getInStream(0).run();
    exchanger.runTransient(100000.0, UUID.randomUUID());
    assertTrue(exchanger.getWallTemperature() > 293.15 && exchanger.getWallTemperature() < 373.15);
    for (int side = 0; side < 2; side++) {
      assertTrue(exchanger.getOutStream(side).getTemperature() > 293.15);
      assertTrue(exchanger.getOutStream(side).getTemperature() < 373.15);
    }
    double energy = exchanger.getThermalInventoryEnergyJ();
    exchanger.getInStream(1).setPressure(4.0);
    assertThrows(IllegalArgumentException.class, () -> exchanger.runTransient(1.0, UUID.randomUUID()));
    assertEquals(energy, exchanger.getThermalInventoryEnergyJ(), 0.0);
    assertSame(outlet, exchanger.getOutStream(0));
  }

  /** Verifies semi-implicit re-evaluation recomputes from the step start while advancing time once. */
  @Test
  void repeatedPhysicalIdRefinesInsteadOfDoubleIntegrating() {
    HeatExchanger exchanger = exchanger();
    HeatExchanger reference = exchanger.copy();
    UUID id = UUID.randomUUID();
    exchanger.runTransient(3.0, id);
    exchanger.getInStream(0).setTemperature(90.0, "C");
    exchanger.getInStream(0).run();
    reference.getInStream(0).setTemperature(90.0, "C");
    reference.getInStream(0).run();
    exchanger.runTransient(3.0, id);
    reference.runTransient(3.0, UUID.randomUUID());
    assertEquals(3.0, exchanger.getTime(), 0.0);
    assertEquals(reference.getThermalInventoryEnergyJ(), exchanger.getThermalInventoryEnergyJ(), 1e-7);
    assertEquals(reference.getOutStream(0).getTemperature(), exchanger.getOutStream(0).getTemperature(), 1e-10);
    assertThrows(IllegalArgumentException.class, () -> exchanger.runTransient(2.0, id));
    exchanger.runTransient(3.0, UUID.randomUUID());
    assertEquals(6.0, exchanger.getTime(), 0.0);
  }

  /**
   * Checks live-reference rollback and serialized continuation without resetting the lumped model.
   *
   * @throws Exception if the model file cannot be inspected
   */
  @Test
  void snapshotAndRestartPreserveStateAndLiveReferences() throws Exception {
    HeatExchanger exchanger = exchanger();
    StreamInterface outlet = exchanger.getOutStream(0);
    HeatExchanger.Snapshot checkpoint = exchanger.captureTransientState();
    exchanger.getInStream(0).setTemperature(90.0, "C");
    exchanger.getInStream(0).run();
    exchanger.runTransient(4.0, UUID.randomUUID());
    double continued = exchanger.getThermalInventoryEnergyJ();
    exchanger.restoreTransientState(checkpoint);
    assertSame(outlet, exchanger.getOutStream(0));
    assertEquals(0.0, exchanger.getTime(), 0.0);
    exchanger.runTransient(4.0, UUID.randomUUID());
    assertEquals(continued, exchanger.getThermalInventoryEnergyJ(), 1e-7);
    ProcessSystem process = new ProcessSystem();
    process.add(exchanger.getInStream(0));
    process.add(exchanger.getInStream(1));
    process.add(exchanger);
    Path file = temporaryDirectory.resolve("dynamic-exchanger.neqsim");
    assertTrue(process.saveToNeqsim(file.toString()));
    ProcessSystem loaded = (ProcessSystem) neqsim.util.serialization.NeqSimXtream.openNeqsim(file.toString());
    HeatExchanger restored = (HeatExchanger) loaded.getUnit("E-101");
    assertNotSame(restored.getOutStream(0), outlet);
    exchanger.runTransient(4.0, UUID.randomUUID());
    restored.runTransient(4.0, UUID.randomUUID());
    assertEquals(exchanger.getThermalInventoryEnergyJ(), restored.getThermalInventoryEnergyJ(), 1e-7);
    assertNotEquals(exchanger.getTransientStateIdentity(), exchanger.copy().getTransientStateIdentity());
  }

  /** Checks first-order timestep refinement and the zero-volume algebraic-fluid limit. */
  @Test
  void refinesThermalTrajectoryAndSupportsAlgebraicFluidNodes() {
    double[] outlet = new double[3];
    for (int level = 0; level < 3; level++) {
      HeatExchanger exchanger = exchanger();
      exchanger.getInStream(0).setTemperature(100.0, "C");
      exchanger.getInStream(0).run();
      double dt = 4.0 / (1 << level);
      for (int step = 0; step < 40.0 / dt; step++) {
        exchanger.runTransient(dt, UUID.randomUUID());
      }
      outlet[level] = exchanger.getOutStream(0).getTemperature();
    }
    assertTrue(Math.abs(outlet[2] - outlet[1]) < 0.65 * Math.abs(outlet[1] - outlet[0]));
    HeatExchanger algebraic = exchanger();
    algebraic.setShellHoldupVolume(0.0);
    algebraic.setTubeHoldupVolume(0.0);
    algebraic.run();
    algebraic.getInStream(0).setTemperature(90.0, "C");
    algebraic.getInStream(0).run();
    algebraic.runTransient(2.0, UUID.randomUUID());
    assertEquals(0.0, algebraic.getLastThermalBalance().getResidualJ(), 1e-4);
  }

  /** Rejects changed thermodynamic inventory policy and multi-phase inputs before publishing either outlet. */
  @Test
  void rejectsCompositionMixingParametersSalinityAndTwoPhaseChanges() {
    HeatExchanger exchanger = exchanger();
    SystemSrkEos gas = new SystemSrkEos(353.15, 5.0);
    gas.addComponent("methane", 0.9);
    gas.addComponent("ethane", 0.1);
    gas.setMixingRule("classic");
    gas.setTotalFlowRate(1.0, "kg/sec");
    exchanger.getInStream(0).setThermoSystem(gas);
    exchanger.getInStream(0).run();
    exchanger.run();
    exchanger.getInStream(0).setFlowRate(1.234567, "kg/sec");
    exchanger.getInStream(0).run();
    exchanger.runTransient(1.0, UUID.randomUUID());
    assertEquals(0.0, exchanger.getLastThermalBalance().getResidualJ(), 1e-4);
    double energy = exchanger.getThermalInventoryEnergyJ();
    exchanger.getInStream(0).getFluid().setBinaryInteractionParameter(0, 1, 0.15);
    assertThrows(IllegalArgumentException.class, () -> exchanger.runTransient(1.0, UUID.randomUUID()));
    assertEquals(energy, exchanger.getThermalInventoryEnergyJ(), 0.0);
    exchanger.getInStream(0).getFluid().setMixingRule("classic");
    exchanger.getInStream(0).run();
    exchanger.run();
    energy = exchanger.getThermalInventoryEnergyJ();
    exchanger.getInStream(0).getFluid().setMixingRule(1);
    assertThrows(IllegalArgumentException.class, () -> exchanger.runTransient(1.0, UUID.randomUUID()));
    assertEquals(energy, exchanger.getThermalInventoryEnergyJ(), 0.0);
    exchanger.getInStream(0).getFluid().setMixingRule("classic");
    exchanger.getInStream(0).run();
    exchanger.run();
    energy = exchanger.getThermalInventoryEnergyJ();
    exchanger.getInStream(0).getFluid().addComponent("propane", 0.2);
    exchanger.getInStream(0).getFluid().setMixingRule("classic");
    assertThrows(IllegalArgumentException.class, () -> exchanger.runTransient(1.0, UUID.randomUUID()));
    assertEquals(energy, exchanger.getThermalInventoryEnergyJ(), 0.0);
    SystemSeawater seawater = new SystemSeawater(293.15, 3.0, 35.0);
    seawater.setTotalFlowRate(1.5, "kg/sec");
    exchanger.getInStream(0).setThermoSystem(new SystemWaterIF97(353.15, 5.0));
    exchanger.getInStream(0).setFlowRate(1.0, "kg/sec");
    exchanger.getInStream(1).setThermoSystem(seawater);
    exchanger.getInStream(0).run();
    exchanger.getInStream(1).run();
    exchanger.run();
    energy = exchanger.getThermalInventoryEnergyJ();
    ((SystemSeawater) exchanger.getInStream(1).getFluid()).setSalinity(40.0);
    assertThrows(IllegalArgumentException.class, () -> exchanger.runTransient(1.0, UUID.randomUUID()));
    assertEquals(energy, exchanger.getThermalInventoryEnergyJ(), 0.0);
    exchanger.run();
    energy = exchanger.getThermalInventoryEnergyJ();
    SystemSrkEos twoPhase = new SystemSrkEos(293.15, 5.0);
    twoPhase.addComponent("methane", 0.5);
    twoPhase.addComponent("n-decane", 0.5);
    twoPhase.setMixingRule("classic");
    twoPhase.setTotalFlowRate(1.0, "kg/sec");
    new neqsim.thermodynamicoperations.ThermodynamicOperations(twoPhase).TPflash();
    assertEquals(2, twoPhase.getNumberOfPhases());
    exchanger.getInStream(0).setThermoSystem(twoPhase);
    assertThrows(IllegalArgumentException.class, () -> exchanger.runTransient(1.0, UUID.randomUUID()));
    assertEquals(energy, exchanger.getThermalInventoryEnergyJ(), 0.0);
  }

  /** Verifies default NTU execution, stable identities and inventory initialization at a mode boundary. */
  @Test
  void preservesDefaultSteadyBehaviorAndInitializesFromPublishedOutlets() {
    HeatExchanger exchanger = exchanger();
    exchanger.setDynamicModelEnabled(false);
    exchanger.setUAvalue(3000.0);
    exchanger.run();
    assertTrue(Double.isNaN(exchanger.getThermalInventoryEnergyJ()));
    assertTrue(Double.isFinite(exchanger.getThermalEffectiveness()));
    StreamInterface first = exchanger.getOutStream(0);
    StreamInterface second = exchanger.getOutStream(1);
    Object heatPort = exchanger.getEnergyPort("heatDuty");
    double firstTemperature = first.getTemperature();
    double secondTemperature = second.getTemperature();
    double steadyDuty = exchanger.getDuty();
    exchanger.runTransient(1.0, UUID.randomUUID());
    assertEquals(firstTemperature, first.getTemperature(), 1e-8);
    assertEquals(steadyDuty, exchanger.getDuty(), 1e-6);
    exchanger.setDynamicModelEnabled(true);
    assertSame(first, exchanger.getOutStream(0));
    assertSame(second, exchanger.getOutStream(1));
    assertSame(heatPort, exchanger.getEnergyPort("heatDuty"));
    assertTrue(exchanger.needRecalculation());
    exchanger.runTransient(0.001, UUID.randomUUID());
    assertEquals(firstTemperature, first.getTemperature(), 0.001);
    assertEquals(secondTemperature, second.getTemperature(), 0.001);
    assertEquals(0.0, exchanger.getLastThermalBalance().getResidualJ(), 1e-4);
    assertTrue(Double.isNaN(exchanger.getThermalEffectiveness()));
  }

  /** Verifies complete rollback after a steady-mode trial and clears repeated-step history across mode changes. */
  @Test
  void rollsBackModeChangesAndReinitializesAfterReturningToSteadyOperation() {
    HeatExchanger exchanger = exchanger();
    HeatExchanger reference = exchanger.copy();
    HeatExchanger.Snapshot snapshot = exchanger.captureTransientState();
    double energy = exchanger.getThermalInventoryEnergyJ();
    double ua = exchanger.getUAvalue();
    double portDuty = exchanger.getEnergyPort("heatDuty").getDuty();
    StreamInterface outlet = exchanger.getOutStream(0);
    exchanger.setDynamicModelEnabled(false);
    exchanger.setUAvalue(2500.0);
    exchanger.run();
    exchanger.restoreTransientState(snapshot);
    assertTrue(exchanger.isDynamicModelEnabled());
    assertEquals(energy, exchanger.getThermalInventoryEnergyJ(), 0.0);
    assertEquals(ua, exchanger.getUAvalue(), 0.0);
    assertEquals(portDuty, exchanger.getEnergyPort("heatDuty").getDuty(), 0.0);
    assertSame(outlet, exchanger.getOutStream(0));
    UUID interval = UUID.randomUUID();
    exchanger.runTransient(1.0, interval);
    reference.runTransient(1.0, interval);
    assertEquals(reference.getThermalInventoryEnergyJ(), exchanger.getThermalInventoryEnergyJ(), 1e-7);
    exchanger.setDynamicModelEnabled(false);
    exchanger.run();
    assertTrue(Double.isNaN(exchanger.getThermalInventoryEnergyJ()));
    exchanger.setDynamicModelEnabled(true);
    exchanger.runTransient(2.0, interval);
    assertEquals(3.0, exchanger.getTime(), 0.0);
    assertEquals(0.0, exchanger.getLastThermalBalance().getResidualJ(), 1e-4);
  }

  /** Rejects external heat boundaries atomically, even when another audit would first report a controller. */
  @Test
  void rejectsExternalHeatBoundariesAtomicallyAndFailsClosedOnIncompleteThermalConfiguration() {
    HeatExchanger exchanger = exchanger();
    exchanger.setDynamicModelEnabled(false);
    neqsim.process.equipment.stream.EnergyStream external = new neqsim.process.equipment.stream.EnergyStream(
        "EXTERNAL");
    exchanger.setEnergyStream(external);
    assertThrows(IllegalArgumentException.class, () -> exchanger.setDynamicModelEnabled(true));
    assertTrue(!exchanger.isDynamicModelEnabled());
    assertSame(external, exchanger.getEnergyStream());
    HeatExchanger specified = exchanger();
    specified.setDynamicModelEnabled(false);
    specified.getEnergyPort("heatDuty").setMode(neqsim.process.equipment.stream.EnergyPortMode.SPECIFICATION);
    assertThrows(IllegalArgumentException.class, () -> specified.setDynamicModelEnabled(true));
    HeatExchanger controlled = exchanger();
    controlled.setDynamicModelEnabled(false);
    controlled.setController(new neqsim.process.controllerdevice.ControllerDeviceBaseClass("ATTACHED"));
    controlled.connectEnergyStream("heatDuty", external, neqsim.process.equipment.stream.EnergyPortMode.CALCULATED);
    assertThrows(IllegalArgumentException.class, () -> controlled.setDynamicModelEnabled(true));
    assertTrue(!controlled.isDynamicModelEnabled());
    HeatExchanger incomplete = exchanger();
    incomplete.setWallMass(0.0);
    double before = incomplete.getOutStream(0).getTemperature();
    assertThrows(IllegalArgumentException.class, () -> incomplete.runTransient(1.0, UUID.randomUUID()));
    assertEquals(before, incomplete.getOutStream(0).getTemperature(), 0.0);
    assertEquals(0.0, incomplete.getTime(), 0.0);
  }

  /** Restores inherited heater settings, utility-data identity and port mode before resuming thermal integration. */
  @Test
  void restoresInheritedSpecificationsAndInternalPortAfterRejectedTrial() {
    HeatExchanger exchanger = exchanger();
    HeatExchanger.Snapshot snapshot = exchanger.captureTransientState();
    UtilityStreamSpecification utility = exchanger.getUtilitySpecification();
    exchanger.setPressureDrop(1.0);
    exchanger.setOutPressure(2.0);
    exchanger.setEnergyInput(8000.0);
    utility.setSupplyTemperature(313.15);
    exchanger.setDynamicModelEnabled(false);
    exchanger.getEnergyPort("heatDuty").setMode(neqsim.process.equipment.stream.EnergyPortMode.SPECIFICATION);
    exchanger.restoreTransientState(snapshot);
    assertTrue(exchanger.isDynamicModelEnabled());
    assertEquals(0.0, exchanger.getPressureDrop(), 0.0);
    assertTrue(!exchanger.hasOutletPressureSpecification());
    assertSame(utility, exchanger.getUtilitySpecification());
    assertTrue(Double.isNaN(utility.getSupplyTemperature()));
    assertEquals(neqsim.process.equipment.stream.EnergyPortMode.CALCULATED,
        exchanger.getEnergyPort("heatDuty").getMode());
    exchanger.runTransient(1.0, UUID.randomUUID());
    assertEquals(0.0, exchanger.getLastThermalBalance().getResidualJ(), 1e-4);
  }

  /**
   * Repairs identity fields absent from legacy Java serialization and emits strict JSON for active thermal monitoring.
   *
   * @throws Exception if reflection or JSON decoding fails
   */
  @Test
  void repairsLegacyParticipantIdentityAndReportsStrictDynamicJson() throws Exception {
    HeatExchanger first = exchanger();
    HeatExchanger second = exchanger();
    java.lang.reflect.Field identity = HeatExchanger.class.getDeclaredField("transientIdentity");
    identity.setAccessible(true);
    identity.set(first, null);
    identity.set(second, null);
    HeatExchanger restored = org.apache.commons.lang3.SerializationUtils.clone(first);
    HeatExchanger other = org.apache.commons.lang3.SerializationUtils.clone(second);
    String stable = restored.getTransientStateIdentity();
    assertEquals(stable, restored.getTransientStateIdentity());
    assertTrue(!stable.endsWith(":null"));
    assertNotEquals(stable, other.getTransientStateIdentity());
    restored.runTransient(1.0, UUID.randomUUID());
    com.fasterxml.jackson.databind.JsonNode response = new com.fasterxml.jackson.databind.ObjectMapper()
        .readTree(restored.toJson());
    assertEquals("SINGLE_PHASE_FIXED_PRESSURE_TWO_MIXED_FLUIDS_AND_WALL", response.get("thermalModel").asText());
    assertTrue(!response.has("HXthermalEfectiveness"));
    assertTrue(response.has("thermalBalance"));
    neqsim.process.util.monitor.HXResponse thermalResponse = new neqsim.process.util.monitor.HXResponse(restored);
    assertEquals(response.get("thermalModel").asText(), thermalResponse.getThermalModel());
    assertEquals(restored.getUAvalue(), thermalResponse.getConfiguredSteadyUAvalue(), 0.0);
    assertEquals(restored.getLastThermalBalance().toMap(), thermalResponse.getThermalBalance());
    thermalResponse.getThermalBalance().clear();
    assertTrue(!thermalResponse.getThermalBalance().isEmpty());
    String[] names = restored.getStateNames();
    for (double value : restored.getStateVector().toArray()) {
      assertTrue(Double.isFinite(value));
    }
    for (double value : restored.getStateVector().toNormalizedArray()) {
      assertTrue(Double.isFinite(value));
    }
    assertEquals(restored.getWallTemperature(), restored.getStateVector().getValue("wall_temperature"), 0.0);
    restored.runTransient(1.0, UUID.randomUUID());
    org.junit.jupiter.api.Assertions.assertArrayEquals(names, restored.getStateNames());
    restored.setDynamicModelEnabled(false);
    assertThrows(IllegalStateException.class, () -> restored.getStateVector());
    restored.run();
    for (double value : restored.getStateVector().toNormalizedArray()) {
      assertTrue(Double.isFinite(value));
    }
    restored.setDynamicModelEnabled(true);
    assertThrows(IllegalStateException.class, () -> restored.getStateVector());
  }

  /** Requires a concrete audited implementation before transactional coverage can be claimed. */
  @Test
  void rejectsUnauditedSubclassSnapshotCoverage() {
    HeatExchanger source = exchanger();
    HeatExchanger unaudited = new HeatExchanger("SPECIAL", source.getInStream(0), source.getInStream(1)) {
      private static final long serialVersionUID = 1000L;
    };
    unaudited.setDynamicModelEnabled(true);
    assertTrue(unaudited.getTransientStateCoverageIssue().contains("concrete HeatExchanger"));
    assertThrows(IllegalStateException.class, () -> unaudited.captureTransientState());
  }

  /** Restores the original closed energy connection before publishing snapshot duty after rejected rewiring. */
  @Test
  void restoresInternalEnergyConnectionAndBookkeepingAfterRejectedRewiring() {
    for (int variant = 0; variant < 4; variant++) {
      HeatExchanger exchanger = exchanger();
      HeatExchanger reference = exchanger.copy();
      HeatExchanger.Snapshot snapshot = exchanger.captureTransientState();
      neqsim.process.equipment.stream.EnergyStream original = exchanger.getEnergyStream();
      Object port = exchanger.getEnergyPort("heatDuty");
      neqsim.process.equipment.stream.EnergyStream external = new neqsim.process.equipment.stream.EnergyStream(
          "EXTERNAL");
      external.setDuty(1234.0);
      if (variant == 0) {
        exchanger.setEnergyStream(external);
      } else if (variant == 1) {
        exchanger.setDynamicModelEnabled(false);
        exchanger.connectEnergyStream("heatDuty", external, neqsim.process.equipment.stream.EnergyPortMode.CALCULATED);
      } else if (variant == 2) {
        exchanger.disconnectEnergyStream("heatDuty");
      } else {
        exchanger.getEnergyPort("heatDuty").connect(external);
      }
      exchanger.restoreTransientState(snapshot);
      assertSame(original, exchanger.getEnergyStream());
      assertSame(original, exchanger.getEnergyPort("heatDuty").getEnergyStream());
      assertSame(port, exchanger.getEnergyPort("heatDuty"));
      assertEquals(1234.0, external.getDuty(), 0.0);
      assertTrue(!exchanger.isSetEnergyStream());
      assertEquals(null, exchanger.getTransientStateCoverageIssue());
      exchanger.runTransient(1.0, UUID.randomUUID());
      reference.runTransient(1.0, UUID.randomUUID());
      assertEquals(reference.getThermalInventoryEnergyJ(), exchanger.getThermalInventoryEnergyJ(), 1e-7);
      assertEquals(0.0, exchanger.getLastThermalBalance().getResidualJ(), 1e-4);
    }
  }

  /** Allows finite observations after successful specified-outlet and approach-temperature steady transitions. */
  @Test
  void observesSpecifiedAndApproachSteadyModesAfterThermalOperation() {
    for (int specification = 0; specification < 2; specification++) {
      HeatExchanger exchanger = exchanger();
      exchanger.setDynamicModelEnabled(false);
      assertThrows(IllegalStateException.class, () -> exchanger.getStateVector());
      if (specification == 0) {
        exchanger.setOutTemperature(45.0, "C");
      } else {
        exchanger.setDeltaT(5.0);
      }
      exchanger.run();
      for (double value : exchanger.getStateVector().toArray()) {
        assertTrue(Double.isFinite(value));
      }
      for (double value : exchanger.getStateVector().toNormalizedArray()) {
        assertTrue(Double.isFinite(value));
      }
    }
  }

}
