package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/** Checks that process screening cannot silently acquire well/composition semantics. */
class CapacityScreeningSemanticsTest {
  @Test
  void compositionScenariosFailBeforeFeedMutation() {
    Stream feed = feed();
    ProcessOptimizationEngine engine = engine(feed);
    assertThrows(UnsupportedOperationException.class, () -> engine.generateLiftCurve(new double[] { 20.0 },
        new double[] { 320.0 }, new double[] { 0.1 }, new double[] { 100.0 }));
    assertThrows(UnsupportedOperationException.class, () -> engine.generateLiftCurve(new double[] { 20.0 },
        new double[] { 320.0 }, new double[] { 0.0, 0.0 }, new double[] { 0.0 }));
    assertEquals(50.0, feed.getPressure("bara"), 1e-10);
    assertEquals(300.0, feed.getTemperature("K"), 1e-10);
  }

  @Test
  void screeningAppliesTemperaturePreservesCompositionAndRetainsInfeasiblePoints() {
    Stream feed = feed();
    ProcessOptimizationEngine engine = engine(feed);
    ProcessOptimizationEngine.LiftCurveData samples = engine.generateCapacityScreening(new double[] { 20.0, 40.0 },
        new double[] { 310.0, 330.0 }, 30.0, 100.0, 200.0);
    assertEquals(4, samples.size());
    for (int i = 0; i < 4; i++) {
      ProcessOptimizationEngine.LiftCurvePoint point = samples.getPoints().get(i);
      assertEquals(i % 2 == 0 ? 310.0 : 330.0, point.getTemperature(), 1e-10);
      assertTrue(Double.isNaN(point.getGOR()));
      assertTrue(Double.isNaN(point.getWaterCut()));
      if (i < 2) {
        assertTrue(Double.isNaN(point.getMaxFlowRate()), "20 bara cannot meet a 30 bara outlet minimum");
      } else {
        assertTrue(point.getMaxFlowRate() > 199.0 && point.getMaxFlowRate() <= 200.0);
      }
    }
    assertEquals(330.0, feed.getTemperature("K"), 1e-10);
    assertEquals(0.8, feed.getFluid().getComponent("methane").getz(), 1e-10);
    assertEquals(samples.getPoints().get(3).getMaxFlowRate(), feed.getFlowRate("kg/hr"), 1e-8);
  }

  @Test
  void invalidBoundsAreRejectedBeforeSimulation() {
    ProcessOptimizationEngine engine = engine(feed());
    assertThrows(IllegalArgumentException.class,
        () -> engine.generateCapacityScreening(new double[] { Double.NaN }, new double[] { 300.0 }));
    assertThrows(IllegalArgumentException.class,
        () -> engine.generateCapacityScreening(new double[] { 20.0 }, new double[] { 300.0 }, 1.0, 200.0, 100.0));
  }

  @Test
  void nonfiniteOutletPressureCannotPassTheScreeningPressureGate() {
    for (double pressure : new double[] { Double.NaN, Double.POSITIVE_INFINITY, 0.0 }) {
      ProcessSystem process = new ProcessSystem() {
        @Override
        public void run() {
          // Isolate the pressure acceptance gate from thermodynamic failure handling.
        }
      };
      process.add(feed());
      Stream outlet = feed();
      outlet.setName("outlet");
      outlet.getFluid().setPressure(pressure);
      process.add(outlet);
      ProcessOptimizationEngine engine = new ProcessOptimizationEngine(process);
      engine.setEnforceConstraints(false);
      ProcessOptimizationEngine.LiftCurveData samples = engine.generateCapacityScreening(new double[] { 40.0 },
          new double[] { 300.0 }, 20.0, 100.0, 200.0);
      assertEquals(1, samples.size());
      assertTrue(Double.isNaN(samples.getPoints().get(0).getMaxFlowRate()));
    }
  }

  @Test
  void processTablesHaveNoReservoirDeckExport() {
    FlowRateOptimizer.ProcessLiftCurveTable lift = new FlowRateOptimizer.ProcessLiftCurveTable("screening",
        new double[] { 100.0 }, new double[] { 30.0 }, java.util.Collections.<String>emptyList());
    FlowRateOptimizer.ProcessCapacityTable capacity = new FlowRateOptimizer.ProcessCapacityTable("screening",
        new double[] { 30.0 }, new double[] { 20.0 }, java.util.Collections.<String>emptyList());
    assertThrows(UnsupportedOperationException.class, lift::toEclipseFormat);
    assertThrows(UnsupportedOperationException.class, capacity::toEclipseFormat);
    assertFalse(lift.toFormattedString().contains("BHP"));
    assertFalse(capacity.toCsv().contains("BHP"));
  }

  private Stream feed() {
    SystemSrkEos fluid = new SystemSrkEos(300.0, 50.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("ethane", 0.2);
    fluid.setMixingRule("classic");
    fluid.setTotalFlowRate(100.0, "kg/hr");
    return new Stream("feed", fluid);
  }

  private ProcessOptimizationEngine engine(Stream feed) {
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    ProcessOptimizationEngine engine = new ProcessOptimizationEngine(process);
    engine.setEnforceConstraints(false);
    return engine;
  }
}
