package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.capacity.CompressorCapacityStrategy;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Regression coverage for the operating point retained after throughput sensitivity probes. */
class ProcessOptimizationEngineOperatingPointTest extends NeqSimTest {
  private ProcessSystem createProcess() {
    SystemInterface gas = new SystemSrkEos(288.15, 50.0);
    gas.addComponent("methane", 0.85);
    gas.addComponent("ethane", 0.10);
    gas.addComponent("propane", 0.05);
    gas.setMixingRule("classic");
    Stream feed = new Stream("feed", gas);
    feed.setFlowRate(50000.0, "kg/hr");
    Compressor compressor = new Compressor("compressor", feed);
    compressor.setOutletPressure(150.0);
    compressor.setUsePolytropicCalc(true);
    compressor.setPolytropicEfficiency(0.78);
    compressor.getMechanicalDesign().maxDesignPower = 4000.0;
    compressor.getCapacityConstraints().get("power").setMaxValue(100.0);
    Stream export = new Stream("export", compressor.getOutletStream());
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(compressor);
    process.add(export);
    process.run();
    return process;
  }

  @Test
  void optimizedProcessRetainsFeasibleReturnedFlowAndPower() {
    ProcessSystem process = createProcess();
    Compressor compressor = (Compressor) process.getUnit("compressor");
    double expectedFlow = 50000.0 * 4000.0 / compressor.getPower("kW");
    ProcessOptimizationEngine engine = new ProcessOptimizationEngine(process);
    engine.setFeedStreamName("feed");
    engine.setOutletStreamName("export");
    engine.setSearchAlgorithm(ProcessOptimizationEngine.SearchAlgorithm.BINARY_SEARCH);
    engine.setTolerance(1.0);
    ProcessOptimizationEngine.OptimizationResult result = engine.findMaximumThroughput(50.0, 150.0, 10000.0, 200000.0);
    assertTrue(result.isConverged(), result.getErrorMessage());
    assertEquals(expectedFlow, result.getOptimalValue(), 1.0);
    assertEquals(result.getOptimalValue(), process.getUnit("feed").getFluid().getFlowRate("kg/hr"), 1.0e-6);
    assertEquals(result.getOptimalValue(), process.getUnit("export").getFluid().getFlowRate("kg/hr"), 1.0e-6);
    assertEquals(150.0, process.getUnit("export").getFluid().getPressure("bara"), 1.0e-8);
    assertTrue(compressor.getPower("kW") <= 4000.0);
    assertTrue(engine.evaluateAllConstraints().getEquipmentStatuses().stream().allMatch(s -> s.isWithinLimits()));
    assertNotNull(result.getSensitivity());
    assertEquals(0.0, result.getSensitivity().getFlowBuffer(), 1.0e-8,
        "An infeasible 1% probe is not available throughput margin");
  }

  @Test
  void directSensitivityRestoresBaseFlowAfterMultipleFeasibleProbes() {
    ProcessSystem process = createProcess();
    ProcessOptimizationEngine engine = new ProcessOptimizationEngine(process);
    engine.setFeedStreamName("feed");
    engine.setOutletStreamName("export");
    ProcessOptimizationEngine.SensitivityResult result = engine.analyzeSensitivity(20000.0, 55.0, 150.0);
    assertTrue(result.getFlowBuffer() > 0.0);
    assertEquals(20000.0, process.getUnit("feed").getFluid().getFlowRate("kg/hr"), 1.0e-6);
    assertEquals(20000.0, process.getUnit("export").getFluid().getFlowRate("kg/hr"), 1.0e-6);
    assertEquals(55.0, process.getUnit("feed").getFluid().getPressure("bara"), 1.0e-8);
    assertEquals(150.0, process.getUnit("export").getFluid().getPressure("bara"), 1.0e-8);
  }

  @Test
  void capacityStrategyIgnoresDisabledConstraintsButEnforcesEnabledOnes() {
    Compressor compressor = (Compressor) createProcess().getUnit("compressor");
    CompressorCapacityStrategy strategy = new CompressorCapacityStrategy();
    CapacityConstraint limit = new CapacityConstraint("testMinimum", "rpm", CapacityConstraint.ConstraintType.HARD)
        .setDesignValue(Double.MAX_VALUE).setMinValue(10000.0).setValueSupplier(() -> 1.0).setEnabled(false);
    compressor.addCapacityConstraint(limit);
    assertTrue(strategy.isWithinHardLimits(compressor));
    assertTrue(strategy.isWithinSoftLimits(compressor));
    assertTrue(strategy.getViolations(compressor).isEmpty());
    assertEquals("power", strategy.getBottleneckConstraint(compressor).getName());

    limit.setEnabled(true);
    assertFalse(strategy.isWithinHardLimits(compressor));
    assertFalse(strategy.isWithinSoftLimits(compressor));
    assertEquals(1, strategy.getViolations(compressor).size());
    assertEquals("testMinimum", strategy.getBottleneckConstraint(compressor).getName());
  }
}
