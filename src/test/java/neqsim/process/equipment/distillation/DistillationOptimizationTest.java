
package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.mechanicaldesign.distillation.DistillationColumnMechanicalDesign;
import neqsim.thermo.system.SystemSrkEos;

public class DistillationOptimizationTest {
  @Test
  public void testAutoFeedOnly() {
    // Use a simple Propane/n-Butane system
    neqsim.thermo.system.SystemInterface fluid = new SystemSrkEos(273.15 + 50.0, 10.00);
    fluid.addComponent("propane", 0.5);
    fluid.addComponent("n-butane", 0.5);
    fluid.setMixingRule("classic");
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
    int feedTrayNumber = column.getFeedTrayNumber(feed);
    assertTrue(feedTrayNumber >= 0, "Feed tray should be available after the column is run");
    assertEquals(feedTrayNumber, column.getFeedTrayNumber("feed"));

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
  public void testFeedTrayLookupForAssignedFeeds() {
    neqsim.thermo.system.SystemInterface fluid = new SystemSrkEos(273.15 + 50.0, 10.00);
    fluid.addComponent("propane", 0.5);
    fluid.addComponent("n-butane", 0.5);
    fluid.setMixingRule("classic");
    fluid.init(0);

    Stream feedOne = new Stream("feedOne", fluid.clone());
    feedOne.setFlowRate(1000.0, "kg/hr");
    feedOne.setTemperature(273.15 + 50.0);
    feedOne.setPressure(10.0, "bara");
    feedOne.run();

    Stream feedTwo = new Stream("feedTwo", fluid.clone());
    feedTwo.setFlowRate(500.0, "kg/hr");
    feedTwo.setTemperature(273.15 + 55.0);
    feedTwo.setPressure(10.0, "bara");
    feedTwo.run();

    DistillationColumn column = new DistillationColumn("DePropanizer", 5, true, true);
    column.addFeedStream(feedOne, 2);
    column.addFeedStream(feedTwo, 4);

    assertEquals(2, column.getFeedTrayNumber(feedOne));
    assertEquals(2, column.getFeedTrayNumber("feedOne"));
    assertEquals(4, column.getFeedTrayNumber(feedTwo));
    assertEquals(4, column.getFeedTrayNumber("feedTwo"));
    assertEquals(-1, column.getFeedTrayNumber("missingFeed"));
  }

  @Test
  public void testAutoFeedTrayEstimateUsesReboilerTemperature() {
    neqsim.thermo.system.SystemInterface fluid = new SystemSrkEos(273.15 + 105.0, 14.00);
    fluid.addComponent("propane", 0.15);
    fluid.addComponent("n-butane", 0.45);
    fluid.addComponent("n-pentane", 0.25);
    fluid.addComponent("n-hexane", 0.15);
    fluid.setMixingRule("classic");
    fluid.init(0);

    Stream feed = new Stream("debutanizer feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.setTemperature(273.15 + 105.0);
    feed.setPressure(14.0, "bara");
    feed.run();

    DistillationColumn column = new DistillationColumn("Debutanizer", 10, true, true);
    column.getCondenser().setRefluxRatio(0.1);
    column.getCondenser().setTotalCondenser(true);
    column.getReboiler().setOutTemperature(446.15);
    column.setTopPressure(12.8);
    column.setBottomPressure(15.0);

    assertEquals(9, column.estimateFeedTrayNumber(feed));

    column.addFeedStream(feed);
    column.run();

    assertEquals(9, column.getFeedTrayNumber(feed));
    assertEquals(9, column.getFeedTrayNumber("debutanizer feed"));
  }

  @Test
  public void testFindOptimalTrayConfigurationAppliesSelectedCandidate() {
    neqsim.thermo.system.SystemInterface fluid = new SystemSrkEos(273.15 + 50.0, 10.00);
    fluid.addComponent("propane", 0.5);
    fluid.addComponent("n-butane", 0.5);
    fluid.setMixingRule("classic");
    fluid.init(0);

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.setTemperature(273.15 + 50.0);
    feed.setPressure(10.0, "bara");
    feed.run();

    DistillationColumn column = new DistillationColumn("DePropanizer", 5, true, true);
    column.addFeedStream(feed);
    column.getReboiler().setOutTemperature(273.15 + 75.0);
    column.getCondenser().setOutTemperature(273.15 + 25.0);
    column.getCondenser().setRefluxRatio(2.0);
    column.getReboiler().setRefluxRatio(2.0);
    column.setTopPressure(10.0);
    column.setBottomPressure(10.0);
    column.setTemperatureTolerance(1.0e-1);
    column.setMassBalanceTolerance(1.0e-1);

    DistillationColumn.TrayOptimizationResult result =
      column.findOptimalTrayConfiguration(0.80, "propane", true, 20);

    assertTrue(result.isFeasible(), result.getMessage());
    assertEquals(result.getNumberOfTrays(), column.getTrays().size());
    assertEquals(result.getFeedTrayNumber(), column.getFeedTrayNumber(feed));
    assertEquals(result.getFeedTrayNumber(), column.getFeedTrayNumber("feed"));
    assertTrue(result.getProductPurity() >= result.getTargetPurity());
    assertTrue(result.getEvaluatedCases() > 0);
    assertTrue(result.getConvergedCases() > 0);
    assertTrue(result.getTotalAbsoluteDuty() >= 0.0);
  }

  @Test
  public void testMechanicalDesignEconomicTrayOptimizationAppliesSelectedCandidate() {
    neqsim.thermo.system.SystemInterface fluid = new SystemSrkEos(273.15 + 50.0, 10.00);
    fluid.addComponent("propane", 0.5);
    fluid.addComponent("n-butane", 0.5);
    fluid.setMixingRule("classic");
    fluid.init(0);

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.setTemperature(273.15 + 50.0);
    feed.setPressure(10.0, "bara");
    feed.run();

    DistillationColumn column = new DistillationColumn("DePropanizer", 5, true, true);
    column.addFeedStream(feed);
    column.getReboiler().setOutTemperature(273.15 + 75.0);
    column.getCondenser().setOutTemperature(273.15 + 25.0);
    column.getCondenser().setRefluxRatio(2.0);
    column.getReboiler().setRefluxRatio(2.0);
    column.setTopPressure(10.0);
    column.setBottomPressure(10.0);
    column.setTemperatureTolerance(1.0e-1);
    column.setMassBalanceTolerance(1.0e-1);

    DistillationColumnMechanicalDesign design =
        (DistillationColumnMechanicalDesign) column.getMechanicalDesign();
    design.setTrayEfficiency(0.70);

    DistillationColumn.EconomicTrayOptimizationResult result =
        design.optimizeEconomicTrayConfiguration(0.80, "propane", true, 20);

    assertTrue(result.isFeasible(), result.getMessage());
    assertEquals(result.getNumberOfTrays(), column.getTrays().size());
    assertEquals(result.getFeedTrayNumber(), column.getFeedTrayNumber(feed));
    assertTrue(result.getProductPurity() >= result.getTargetPurity());
    assertTrue(result.getCapitalCost() > 0.0);
    assertTrue(result.getAnnualUtilityCost() >= 0.0);
    assertTrue(result.getTotalAnnualizedCost() > 0.0);
    assertTrue(result.getActualTrays() >= result.getNumberOfTrays());
    assertTrue(result.getColumnDiameter() > 0.0);
    assertTrue(result.getColumnHeight() > 0.0);
    assertEquals(0.70, result.getTrayEfficiency(), 1.0e-12);
  }

  @Test
  public void testAutoFeedAndOptimalTrays() {
    // ... existing test ...
    neqsim.thermo.system.SystemInterface fluid = new SystemSrkEos(273.15 + 50.0, 10.00);
    fluid.addComponent("propane", 0.5);
    fluid.addComponent("n-butane", 0.5);
    fluid.setMixingRule("classic");
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
    column.setTemperatureTolerance(1.0e-1);
    column.setMassBalanceTolerance(1.0e-1);

    // Feature 2: Find optimal trays for 80% propane in top
    int optimalTrays = column.findOptimalNumberOfTrays(0.80, "propane", true, 20);
    System.out.println("Optimal trays found: " + optimalTrays);

    assertTrue(optimalTrays > 0, "Should find a solution");
    assertTrue(column.getGasOutStream().getFluid().getComponent("propane").getz() >= 0.80,
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
    neqsim.thermo.system.SystemInterface fluid = new SystemSrkEos(273.15 + 50.0, 10.00);
    fluid.addComponent("propane", 0.5);
    fluid.addComponent("n-butane", 0.5);
    fluid.setMixingRule("classic");
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
    column.setTemperatureTolerance(1.0e-1);
    column.setMassBalanceTolerance(1.0e-1);

    // Feature 2: Find optimal trays for 80% propane in top
    int optimalTrays = column.findOptimalNumberOfTrays(0.80, "propane", true, 20);

    System.out.println("Optimal trays found (Inside-Out): " + optimalTrays);

    assertTrue(optimalTrays > 0, "Should find a solution with Inside-Out solver");
    assertTrue(column.getGasOutStream().getFluid().getComponent("propane").getz() >= 0.80,
        "Top product should meet spec");
  }
}
