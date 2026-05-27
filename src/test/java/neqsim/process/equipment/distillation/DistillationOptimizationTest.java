
package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.mechanicaldesign.distillation.DistillationColumnMechanicalDesign;
import neqsim.thermo.system.SystemSrkEos;

public class DistillationOptimizationTest {
  @Test
  public void testPerStageMurphreeEfficiencyApi() {
    DistillationColumn column = new DistillationColumn("MurphreeApiColumn", 5, true, true);

    assertEquals(1.0, column.getMurphreeEfficiency(), 1.0e-12);
    assertEquals(1.0, column.getMurphreeEfficiency(0), 1.0e-12);

    column.setMurphreeEfficiency(0.75);
    assertEquals(0.75, column.getMurphreeEfficiency(), 1.0e-12);
    assertEquals(0.75, column.getMurphreeEfficiency(3), 1.0e-12);

    column.setMurphreeEfficiency(3, 0.62);
    assertEquals(0.62, column.getMurphreeEfficiency(3), 1.0e-12);
    assertEquals(0.75, column.getMurphreeEfficiency(2), 1.0e-12);

    column.setMurphreeEfficiencies(new double[] {1.0, 0.95, Double.NaN, 0.70, 1.2, 0.10, 0.0});
    assertEquals(1.0, column.getMurphreeEfficiency(0), 1.0e-12);
    assertEquals(0.95, column.getMurphreeEfficiency(1), 1.0e-12);
    assertEquals(0.75, column.getMurphreeEfficiency(2), 1.0e-12);
    assertEquals(0.70, column.getMurphreeEfficiency(3), 1.0e-12);
    assertEquals(1.0, column.getMurphreeEfficiency(4), 1.0e-12);

    column.clearPerStageMurphreeEfficiency();
    assertEquals(0.75, column.getMurphreeEfficiency(3), 1.0e-12);
    assertThrows(IllegalArgumentException.class,
        () -> column.setMurphreeEfficiencies(new double[] {0.8, 0.8}));
    assertThrows(IndexOutOfBoundsException.class, () -> column.getMurphreeEfficiency(7));
  }

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

    if (!column.solved()) {
      assertEquals(DistillationColumn.SolveStatus.FALLBACK_PRODUCTS, column.getLastSolveStatus(),
          "Non-rigorous auto-feed products should be reported explicitly: "
              + column.getConvergenceDiagnostics());
      assertTrue(column.wasFeedFlashFallbackApplied(),
          "Fallback products should be visible when the auto-feed case is not rigorously solved");
    }
    assertEquals(0.0, column.getMassBalance("kg/hr"), 1.0e-6);

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
    column.setMaxNumberOfIterations(25);

    double targetPropaneMoleFraction = 0.60;
    DistillationColumn.TrayOptimizationResult result =
        column.findOptimalTrayConfiguration(targetPropaneMoleFraction, "propane", true, 8);

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
    column.setMaxNumberOfIterations(25);
    column.setSolverType(DistillationColumn.SolverType.INSIDE_OUT);
    column.setMaxTrayOptimizationCandidates(8);
    column.setMaxTrayOptimizationTimeSeconds(20.0);

    DistillationColumnMechanicalDesign design =
        (DistillationColumnMechanicalDesign) column.getMechanicalDesign();
    design.setTrayEfficiency(0.70);

    DistillationColumn.EconomicTrayOptimizationResult result =
        design.optimizeEconomicTrayConfiguration(0.60, "propane", true, 8);

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
  public void testTrayOptimizationCandidateBudgetStopsSearch() {
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

    DistillationColumn column = new DistillationColumn("BudgetedDePropanizer", 5, true, true);
    column.addFeedStream(feed);
    column.getReboiler().setOutTemperature(273.15 + 75.0);
    column.getCondenser().setOutTemperature(273.15 + 25.0);
    column.getCondenser().setRefluxRatio(2.0);
    column.getReboiler().setRefluxRatio(2.0);
    column.setTopPressure(10.0);
    column.setBottomPressure(10.0);
    column.setTemperatureTolerance(1.0e-1);
    column.setMassBalanceTolerance(1.0e-1);

    assertTrue(column.getMaxTrayOptimizationCandidates() > 2);
    column.setMaxTrayOptimizationCandidates(2);
    column.setMaxTrayOptimizationTimeSeconds(30.0);
    assertEquals(2, column.getMaxTrayOptimizationCandidates());
    assertEquals(30.0, column.getMaxTrayOptimizationTimeSeconds(), 1.0e-12);

    DistillationColumn.TrayOptimizationResult result =
        column.findOptimalTrayConfiguration(0.99, "propane", true, 20);

    assertTrue(!result.isFeasible(), "Budget-limited search should not report feasibility");
    assertEquals(2, result.getEvaluatedCases());
    assertTrue(result.getMessage().contains("search budget"), result.getMessage());
    assertThrows(IllegalArgumentException.class, () -> column.setMaxTrayOptimizationCandidates(0));
    assertThrows(IllegalArgumentException.class,
        () -> column.setMaxTrayOptimizationTimeSeconds(Double.NaN));
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
    column.setMaxNumberOfIterations(25);

    // Feature 2: Use a light target so this unit test does not run a full design study.
    double targetPropaneMoleFraction = 0.60;
    int optimalTrays =
        column.findOptimalNumberOfTrays(targetPropaneMoleFraction, "propane", true, 8);
    System.out.println("Optimal trays found: " + optimalTrays);

    assertTrue(optimalTrays > 0, "Should find a solution");
    assertTrue(column.getGasOutStream().getFluid().getComponent("propane")
        .getz() >= targetPropaneMoleFraction, "Top product should meet spec");

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
    column.setMaxNumberOfIterations(25);

    // Feature 2: Use a light target so the Inside-Out smoke test does not run a full design study.
    double targetPropaneMoleFraction = 0.60;
    int optimalTrays =
        column.findOptimalNumberOfTrays(targetPropaneMoleFraction, "propane", true, 8);

    System.out.println("Optimal trays found (Inside-Out): " + optimalTrays);

    assertTrue(optimalTrays > 0, "Should find a solution with Inside-Out solver");
    assertTrue(column.getGasOutStream().getFluid().getComponent("propane")
        .getz() >= targetPropaneMoleFraction, "Top product should meet spec");
  }
}
