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
class DynamicHeatExchangerTest {
  @TempDir
  Path temporaryDirectory;

  /**
   * Creates a two-water-circuit fixture with temperature-dependent reference enthalpy.
   *
   * @return initialized conservative exchanger
   */
  private DynamicHeatExchanger exchanger() {
    SystemWaterIF97 first = new SystemWaterIF97(353.15, 5.0);
    first.setTotalFlowRate(1.0, "kg/sec");
    SystemWaterIF97 second = new SystemWaterIF97(293.15, 3.0);
    second.setTotalFlowRate(1.5, "kg/sec");
    Stream hot = new Stream("HOT", first);
    Stream cold = new Stream("COLD", second);
    hot.run();
    cold.run();
    DynamicHeatExchanger exchanger = new DynamicHeatExchanger("E-101", hot, cold);
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
    DynamicHeatExchanger exchanger = exchanger();
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
    DynamicHeatExchanger exchanger = exchanger();
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
    DynamicHeatExchanger exchanger = exchanger();
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
    DynamicHeatExchanger exchanger = exchanger();
    DynamicHeatExchanger reference = exchanger.clone();
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
    DynamicHeatExchanger exchanger = exchanger();
    StreamInterface outlet = exchanger.getOutStream(0);
    DynamicHeatExchanger.Snapshot checkpoint = exchanger.captureTransientState();
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
    DynamicHeatExchanger restored = (DynamicHeatExchanger) loaded.getUnit("E-101");
    assertNotSame(restored.getOutStream(0), outlet);
    exchanger.runTransient(4.0, UUID.randomUUID());
    restored.runTransient(4.0, UUID.randomUUID());
    assertEquals(exchanger.getThermalInventoryEnergyJ(), restored.getThermalInventoryEnergyJ(), 1e-7);
    assertNotEquals(exchanger.getTransientStateIdentity(), exchanger.clone().getTransientStateIdentity());
  }

  /** Checks first-order timestep refinement and the zero-volume algebraic-fluid limit. */
  @Test
  void refinesThermalTrajectoryAndSupportsAlgebraicFluidNodes() {
    double[] outlet = new double[3];
    for (int level = 0; level < 3; level++) {
      DynamicHeatExchanger exchanger = exchanger();
      exchanger.getInStream(0).setTemperature(100.0, "C");
      exchanger.getInStream(0).run();
      double dt = 4.0 / (1 << level);
      for (int step = 0; step < 40.0 / dt; step++) {
        exchanger.runTransient(dt, UUID.randomUUID());
      }
      outlet[level] = exchanger.getOutStream(0).getTemperature();
    }
    assertTrue(Math.abs(outlet[2] - outlet[1]) < 0.65 * Math.abs(outlet[1] - outlet[0]));
    DynamicHeatExchanger algebraic = exchanger();
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
    DynamicHeatExchanger exchanger = exchanger();
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
}
