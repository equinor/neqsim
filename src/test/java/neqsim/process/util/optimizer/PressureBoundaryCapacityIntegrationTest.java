package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationConstraint;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Verifies that pressure-boundary optimization consumes compressor capacity constraints.
 *
 * @author NeqSim
 * @version 1.0
 */
public class PressureBoundaryCapacityIntegrationTest {

  /** Verifies reuse of map, driver, temperature, and custom capacity constraints. */
  @SuppressWarnings("unchecked")
  @Test
  public void testOptimizerUsesEnabledCompressorCapacityConstraints() throws Exception {
    SystemInterface gas = new SystemSrkEos(288.15, 50.0);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.07);
    gas.addComponent("propane", 0.03);
    gas.setMixingRule("classic");

    Stream feed = new Stream("Feed", gas);
    feed.setFlowRate(30000.0, "kg/hr");
    feed.run();

    Compressor compressor = new Compressor("ExportCompressor", feed);
    compressor.setOutletPressure(100.0, "bara");
    compressor.setUsePolytropicCalc(true);
    compressor.setPolytropicEfficiency(0.75);
    Stream export = new Stream("Export", compressor.getOutletStream());

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(compressor);
    process.add(export);
    process.run();

    PressureBoundaryOptimizer optimizer = new PressureBoundaryOptimizer(process, feed, export);
    assertThrows(IllegalArgumentException.class, () -> optimizer.setMinSurgeMargin(0.0));
    assertThrows(IllegalArgumentException.class, () -> optimizer.setMinSurgeMargin(-0.10));
    assertThrows(IllegalArgumentException.class, () -> optimizer.setMinSurgeMargin(Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> optimizer.setMinSurgeMargin(Double.POSITIVE_INFINITY));
    assertThrows(IllegalArgumentException.class, () -> optimizer.setMinSurgeMargin(Double.NEGATIVE_INFINITY));
    optimizer.setMinSurgeMargin(0.15);
    compressor.addCapacityConstraint(new CapacityConstraint("vendorLimit", "-", CapacityConstraint.ConstraintType.SOFT)
        .setDesignValue(1.0).setCurrentValue(0.8).setSeverity(CapacityConstraint.ConstraintSeverity.SOFT));
    optimizer.configureCompressorCharts();

    Method factory = PressureBoundaryOptimizer.class.getDeclaredMethod("createCompressorConstraints");
    factory.setAccessible(true);
    List<OptimizationConstraint> constraints = (List<OptimizationConstraint>) factory.invoke(optimizer);

    assertConstraintPresent(constraints, "ExportCompressor_speed");
    assertConstraintPresent(constraints, "ExportCompressor_power");
    assertConstraintPresent(constraints, "ExportCompressor_surgeMargin");
    assertConstraintPresent(constraints, "ExportCompressor_stonewallMargin");
    assertConstraintPresent(constraints, "ExportCompressor_vendorLimit");
    assertFalse(hasConstraint(constraints, "ExportCompressor_ratedPower"),
        "DESIGN constraints should remain reporting-only");

    CapacityConstraint surge = compressor.getCapacityConstraints().get("surgeMargin");
    assertNotNull(surge);
    assertEquals(15.0, surge.getMinValue(), 1.0e-12);
    OptimizationConstraint surgeOptimization = getConstraint(constraints, "ExportCompressor_surgeMargin");
    assertEquals(1.0 - surge.getUtilization(), surgeOptimization.margin(process), 1.0e-12);
  }

  private static void assertConstraintPresent(List<OptimizationConstraint> constraints, String name) {
    assertTrue(hasConstraint(constraints, name), "Expected optimizer constraint " + name);
  }

  private static boolean hasConstraint(List<OptimizationConstraint> constraints, String name) {
    return getConstraint(constraints, name) != null;
  }

  private static OptimizationConstraint getConstraint(List<OptimizationConstraint> constraints, String name) {
    for (OptimizationConstraint constraint : constraints) {
      if (name.equals(constraint.getName())) {
        return constraint;
      }
    }
    return null;
  }
}
