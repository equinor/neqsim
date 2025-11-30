
package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkCPAstatoil;

public class DistillationOptimizationTest {

  @Test
  public void testAutoFeedOnly() {
    // Use a simple Propane/n-Butane system
    neqsim.thermo.system.SystemInterface fluid = new SystemSrkCPAstatoil(273.15 + 50.0, 10.00);
    fluid.addComponent("propane", 0.5);
    fluid.addComponent("n-butane", 0.5);
    fluid.setMixingRule(1);
    fluid.init(0);

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.setTemperature(273.15 + 50.0);
    feed.setPressure(10.0, "bara");
    feed.run();

    DistillationColumn column = new DistillationColumn("DePropanizer", 5, true, true);
    column.addFeedStream(feed); // Auto-assign

    column.getReboiler().setOutTemperature(273.15 + 75.0);
    column.getCondenser().setOutTemperature(273.15 + 25.0);
    column.setTopPressure(10.0);
    column.setBottomPressure(10.0);

    column.run();

    assertTrue(column.solved(), "Column should solve");

    // Check feed assignment
    boolean feedAssigned = false;
    for (int i = 0; i < column.getTrays().size(); i++) {
      if (column.getTray(i).getNumberOfInputStreams() > 0) {
        if (Math.abs(column.getTray(i).getStream(0).getFlowRate("kg/hr") - 1000.0) < 1.0) {
          feedAssigned = true;
          System.out.println("Feed assigned to tray: " + i);
        }
      }
    }
    assertTrue(feedAssigned, "Feed should be assigned");
  }

  @Test
  public void testAutoFeedAndOptimalTrays() {
    // ... existing test ...
    neqsim.thermo.system.SystemInterface fluid = new SystemSrkCPAstatoil(273.15 + 50.0, 10.00);
    fluid.addComponent("propane", 0.5);
    fluid.addComponent("n-butane", 0.5);
    fluid.setMixingRule(1); // Classic SRK
    fluid.init(0);

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.setTemperature(273.15 + 50.0);
    feed.setPressure(10.0, "bara");
    feed.run();

    // Start with a small column
    DistillationColumn column = new DistillationColumn("DePropanizer", 5, true, true);

    // Feature 1: Add feed without specifying tray
    column.addFeedStream(feed);

    // Set reasonable estimates for reboiler/condenser
    // Propane sat T @ 10 bar is approx 27C (300K)
    // n-Butane sat T @ 10 bar is approx 75C (348K)
    column.getReboiler().setOutTemperature(273.15 + 75.0);
    column.getCondenser().setOutTemperature(273.15 + 25.0);

    // Set reflux ratio to something reasonable for separation
    column.getCondenser().setRefluxRatio(2.0);
    column.getReboiler().setRefluxRatio(2.0);

    column.setTopPressure(10.0);
    column.setBottomPressure(10.0);

    // Feature 2: Find optimal trays for 95% propane in top
    int optimalTrays = column.findOptimalNumberOfTrays(0.95, "propane", true, 20);
    System.out.println("Optimal trays found: " + optimalTrays);

    assertTrue(optimalTrays > 0, "Should find a solution");
    assertTrue(column.getGasOutStream().getFluid().getComponent("propane").getz() >= 0.95,
        "Top product should meet spec");

    // Verify feed was assigned
    boolean feedAssigned = false;
    for (int i = 0; i < column.getTrays().size(); i++) {
      if (column.getTray(i).getNumberOfInputStreams() > 0) {
        if (Math.abs(column.getTray(i).getStream(0).getFlowRate("kg/hr") - 1000.0) < 1.0) {
          feedAssigned = true;
          System.out.println("Feed assigned to tray: " + i);
        }
      }
    }
    assertTrue(feedAssigned, "Feed should be assigned to a tray");
  }

  @Test
  public void testAutoFeedAndOptimalTraysInsideOut() {
    // Use a simple Propane/n-Butane system which is numerically stable
    neqsim.thermo.system.SystemInterface fluid = new SystemSrkCPAstatoil(273.15 + 50.0, 10.00);
    fluid.addComponent("propane", 0.5);
    fluid.addComponent("n-butane", 0.5);
    fluid.setMixingRule(1); // Classic SRK
    fluid.init(0);

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.setTemperature(273.15 + 50.0);
    feed.setPressure(10.0, "bara");
    feed.run();

    // Start with a small column
    DistillationColumn column = new DistillationColumn("DePropanizer", 5, true, true);
    column.setSolverType(DistillationColumn.SolverType.INSIDE_OUT);

    // Feature 1: Add feed without specifying tray
    column.addFeedStream(feed);

    // Set reasonable estimates for reboiler/condenser
    column.getReboiler().setOutTemperature(273.15 + 75.0);
    column.getCondenser().setOutTemperature(273.15 + 25.0);

    // Set reflux ratio to something reasonable for separation
    column.getCondenser().setRefluxRatio(2.0);
    column.getReboiler().setRefluxRatio(2.0);

    column.setTopPressure(10.0);
    column.setBottomPressure(10.0);

    // Feature 2: Find optimal trays for 95% propane in top
    int optimalTrays = column.findOptimalNumberOfTrays(0.95, "propane", true, 20);

    System.out.println("Optimal trays found (Inside-Out): " + optimalTrays);

    assertTrue(optimalTrays > 0, "Should find a solution with Inside-Out solver");
    assertTrue(column.getGasOutStream().getFluid().getComponent("propane").getz() >= 0.95,
        "Top product should meet spec");
  }
}
