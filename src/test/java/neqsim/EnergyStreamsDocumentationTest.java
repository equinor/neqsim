package neqsim;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.energy.CoupledProcessEnergyResult;
import neqsim.process.equipment.energy.CoupledProcessEnergySolver;
import neqsim.process.equipment.energy.EnergyNetworkSolver;
import neqsim.process.equipment.stream.EnergyBus;
import neqsim.process.equipment.stream.EnergyPort;
import neqsim.process.equipment.stream.EnergyPortDirection;
import neqsim.process.equipment.stream.EnergyPortMode;
import neqsim.process.equipment.stream.EnergyType;
import neqsim.process.processmodel.ProcessSystem;

/** Compiles and executes coupled energy examples from docs/process/energy_streams.md. */
class EnergyStreamsDocumentationTest {

  @Test
  void testCoupledProcessEnergyDocumentationExample() {
    EnergyBus allocatedGrid = new EnergyBus("allocated grid", EnergyType.ELECTRICAL);

    EnergyPort generator = new EnergyPort("power", EnergyType.ELECTRICAL, EnergyPortDirection.OUTPUT,
        EnergyPortMode.CALCULATED);
    generator.setOwnerName("generator");
    generator.connect(allocatedGrid);
    generator.setDuty(100.0, "kW");

    EnergyPort essentialLoad = new EnergyPort("power", EnergyType.ELECTRICAL, EnergyPortDirection.INPUT,
        EnergyPortMode.SPECIFICATION);
    essentialLoad.setOwnerName("essential load");
    essentialLoad.setRequestedPower(80.0, "kW");
    essentialLoad.connect(allocatedGrid);

    EnergyNetworkSolver network = new EnergyNetworkSolver("electrical allocation", allocatedGrid);
    ProcessSystem process = new ProcessSystem();
    process.setUseOptimizedExecution(false);
    process.setUseGraphBasedExecution(true);
    process.add(network);

    CoupledProcessEnergySolver coupledSolver = new CoupledProcessEnergySolver(process);
    coupledSolver.setMaximumIterations(50);
    coupledSolver.setProcessTolerance(1.0e-6);
    coupledSolver.setPowerTolerance(1.0e3);
    coupledSolver.setRelaxationFactor(0.5);

    CoupledProcessEnergyResult result = coupledSolver.solve();
    String json = result.toJson();

    assertTrue(result.isConverged());
    assertFalse(json.isEmpty());
    assertTrue(json.contains("CONVERGED"));
  }
}
