package neqsim.process.equipment.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.stream.EnergyBus;
import neqsim.process.equipment.stream.EnergyNetworkReport;
import neqsim.process.equipment.stream.EnergyPortDirection;
import neqsim.process.equipment.stream.EnergyPortMode;
import neqsim.process.equipment.stream.EnergyType;
import neqsim.process.processmodel.ProcessSystem;

class CoupledProcessEnergySolverTest {

  @Test
  void testRelaxationConvergesOscillatingPowerRequest() {
    TestNetwork testNetwork = oscillatingNetwork();
    CoupledProcessEnergySolver solver = new CoupledProcessEnergySolver(testNetwork.process);
    solver.setMaximumIterations(10);
    solver.setPowerTolerance(1.0e-9);
    solver.setProcessTolerance(1.0e-9);
    solver.setRelaxationFactor(0.5);

    CoupledProcessEnergyResult result = solver.solve();

    assertTrue(result.isConverged());
    assertEquals(CoupledProcessEnergyResult.TerminationReason.CONVERGED, result.getTerminationReason());
    assertEquals(3, result.getIterations());
    assertEquals(-50.0, testNetwork.bus.getAllocation(testNetwork.load.getParticipantId()), 1.0e-12);
    assertEquals(50.0, result.getEnergyReports().get(0).getServedDemand(), 1.0e-12);
    assertEquals(result.getIterations(), result.getIterationHistory().size());
    assertEquals(0.0, result.getMaximumPowerResidual(), 1.0e-12);
  }

  @Test
  void testUndampedOscillationStopsAtMaximumIterations() {
    TestNetwork testNetwork = oscillatingNetwork();
    CoupledProcessEnergySolver solver = new CoupledProcessEnergySolver(testNetwork.process);
    solver.setMaximumIterations(6);
    solver.setPowerTolerance(1.0e-9);
    solver.setProcessTolerance(1.0e-9);
    solver.setRelaxationFactor(1.0);

    CoupledProcessEnergyResult result = solver.solve();

    assertFalse(result.isConverged());
    assertEquals(CoupledProcessEnergyResult.TerminationReason.MAXIMUM_ITERATIONS, result.getTerminationReason());
    assertEquals(6, result.getIterations());
    assertEquals(100.0, result.getMaximumPowerResidual(), 1.0e-12);
  }

  @Test
  void testEnergyNetworkSolverReportsAreRunSnapshots() {
    EnergyBus bus = new EnergyBus("grid", EnergyType.ELECTRICAL);
    FixedSource source = new FixedSource("source", bus, 100.0);
    FixedDemand demand = new FixedDemand("demand", bus, 100.0);
    EnergyNetworkSolver networkSolver = new EnergyNetworkSolver("network", bus);

    source.run(UUID.randomUUID());
    demand.run(UUID.randomUUID());
    networkSolver.run(UUID.randomUUID());
    List<EnergyNetworkReport> firstRunReports = networkSolver.getReports();

    source.setAvailablePower(50.0);
    source.run(UUID.randomUUID());
    bus.solveBalance();

    assertEquals(100.0, firstRunReports.get(0).getAcceptedSupply(), 1.0e-12);
    assertEquals(100.0, networkSolver.getReports().get(0).getAcceptedSupply(), 1.0e-12);
    assertEquals(50.0, bus.getLastReport().getAcceptedSupply(), 1.0e-12);
    assertThrows(UnsupportedOperationException.class, () -> networkSolver.getReports().clear());
  }

  @Test
  void testProcessWithoutEnergyNetworkIsRejected() {
    CoupledProcessEnergySolver solver = new CoupledProcessEnergySolver(new ProcessSystem());

    assertThrows(IllegalStateException.class, solver::solve);
  }

  private static TestNetwork oscillatingNetwork() {
    EnergyBus bus = new EnergyBus("oscillating grid", EnergyType.ELECTRICAL);
    FixedSource source = new FixedSource("source", bus, 100.0);
    OscillatingLoad load = new OscillatingLoad("feedback load", bus, 100.0);
    EnergyNetworkSolver networkSolver = new EnergyNetworkSolver("network solver", bus);

    ProcessSystem process = new ProcessSystem();
    process.setUseOptimizedExecution(false);
    process.setUseGraphBasedExecution(true);
    process.add(load);
    process.add(networkSolver);
    process.add(source);
    return new TestNetwork(process, bus, load);
  }

  private static final class TestNetwork {
    private final ProcessSystem process;
    private final EnergyBus bus;
    private final OscillatingLoad load;

    private TestNetwork(ProcessSystem process, EnergyBus bus, OscillatingLoad load) {
      this.process = process;
      this.bus = bus;
      this.load = load;
    }
  }

  private static final class FixedSource extends ProcessEquipmentBaseClass {
    private static final long serialVersionUID = 1000L;
    private static final String POWER_PORT = "power";
    private double availablePower;

    private FixedSource(String name, EnergyBus bus, double availablePower) {
      super(name);
      this.availablePower = availablePower;
      registerEnergyPort(POWER_PORT, EnergyType.ELECTRICAL, EnergyPortDirection.OUTPUT, EnergyPortMode.CALCULATED);
      connectEnergyStream(POWER_PORT, bus, EnergyPortMode.CALCULATED);
    }

    private void setAvailablePower(double availablePower) {
      this.availablePower = availablePower;
    }

    @Override
    public void run(UUID id) {
      getEnergyPort(POWER_PORT).setDuty(availablePower);
      setCalculationIdentifier(id);
    }
  }

  private static final class FixedDemand extends ProcessEquipmentBaseClass {
    private static final long serialVersionUID = 1000L;
    private static final String POWER_PORT = "power";
    private final double requestedPower;

    private FixedDemand(String name, EnergyBus bus, double requestedPower) {
      super(name);
      this.requestedPower = requestedPower;
      registerEnergyPort(POWER_PORT, EnergyType.ELECTRICAL, EnergyPortDirection.INPUT, EnergyPortMode.SPECIFICATION);
      connectEnergyStream(POWER_PORT, bus, EnergyPortMode.SPECIFICATION);
    }

    @Override
    public void run(UUID id) {
      getEnergyPort(POWER_PORT).setRequestedPower(requestedPower);
      setCalculationIdentifier(id);
    }
  }

  private static final class OscillatingLoad extends ProcessEquipmentBaseClass {
    private static final long serialVersionUID = 1000L;
    private static final String POWER_PORT = "power";
    private final double targetPower;

    private OscillatingLoad(String name, EnergyBus bus, double targetPower) {
      super(name);
      this.targetPower = targetPower;
      registerEnergyPort(POWER_PORT, EnergyType.ELECTRICAL, EnergyPortDirection.INPUT, EnergyPortMode.SPECIFICATION);
      connectEnergyStream(POWER_PORT, bus, EnergyPortMode.SPECIFICATION);
      getEnergyPort(POWER_PORT).setRequestedPower(0.0);
    }

    private String getParticipantId() {
      return getEnergyPort(POWER_PORT).getParticipantId();
    }

    @Override
    public void run(UUID id) {
      double allocatedPower = getEnergyPort(POWER_PORT).getPowerMagnitude();
      getEnergyPort(POWER_PORT).setRequestedPower(Math.max(0.0, targetPower - allocatedPower));
      setCalculationIdentifier(id);
    }
  }
}
