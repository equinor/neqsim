package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import neqsim.process.equipment.distillation.DoeBigHillVacuumFractionationCase.OperatingInputs;
import neqsim.process.equipment.distillation.DoeBigHillVacuumFractionationResult.ProductResult;
import neqsim.process.equipment.distillation.DoeBigHillVacuumScenarioScreen.PointResult;
import neqsim.process.equipment.distillation.DoeBigHillVacuumScenarioScreen.Scenario;

/** Qualification tests for {@link DoeBigHillVacuumScenarioScreen}. */
public class DoeBigHillVacuumScenarioScreenTest {
  private static final double BALANCE_TOLERANCE = 5.0e-2;

  /** Execute documented combined scenarios without assuming a response trend. */
  @Test
  @Timeout(value = 300, unit = TimeUnit.SECONDS)
  public void combinedScenarioScreenReturnsQualifiedImmutablePoints() {
    Scenario[] scenarios = documentedScenarios();

    DoeBigHillVacuumScenarioScreen screen = DoeBigHillVacuumScenarioScreen.run("Big Hill vacuum combined screen",
        scenarios);

    scenarios[0] = scenarios[2];
    PointResult[] points = screen.getPoints();
    assertEquals(3, points.length);
    assertNotSame(points, screen.getPoints());
    assertEquals("low", points[0].getScenario().getName());
    assertEquals("base", points[1].getScenario().getName());
    assertEquals("high", points[2].getScenario().getName());
    assertEquals(points[0], screen.getPoint(0));
    assertEquals(points[2], screen.getPoint(2));
    assertThrows(IndexOutOfBoundsException.class, () -> screen.getPoint(-1));
    assertThrows(IndexOutOfBoundsException.class, () -> screen.getPoint(3));

    double expectedMinimumOverhead = Double.POSITIVE_INFINITY;
    double expectedMaximumOverhead = Double.NEGATIVE_INFINITY;
    double expectedMaximumMassClosure = 0.0;
    double expectedMaximumComponentClosure = 0.0;
    double expectedMaximumEnergyError = 0.0;
    double expectedMaximumMeshResidual = 0.0;

    for (PointResult point : points) {
      Scenario scenario = point.getScenario();
      OperatingInputs applied = scenario.getOperatingInputs();
      DoeBigHillVacuumFractionationResult result = point.getFractionationResult();

      assertEquals(12, applied.getSimpleTrayCount());
      assertEquals(4, applied.getFeedTrayIndex());
      assertEquals(scenario.getFeedMassFlowKgPerHour(), result.getFeedMassFlowKgPerHour(),
          scenario.getFeedMassFlowKgPerHour() * 1.0e-10);

      ProductResult overhead = result.getProduct("Overhead");
      ProductResult bottoms = result.getProduct("Bottoms");
      assertTrue(overhead.getMassFlowKgPerHour() > 0.0);
      assertTrue(bottoms.getMassFlowKgPerHour() > 0.0);
      assertTrue(overhead.getMeanNormalBoilingPointKelvin() < bottoms.getMeanNormalBoilingPointKelvin());
      assertTrue(Double.isFinite(point.getOverheadBoilingPointQuantileKelvin(0.10)));
      assertTrue(Double.isFinite(point.getOverheadBoilingPointQuantileKelvin(0.50)));
      assertTrue(Double.isFinite(point.getOverheadBoilingPointQuantileKelvin(0.90)));
      assertThrows(IllegalArgumentException.class, () -> point.getOverheadBoilingPointQuantileKelvin(0.0));
      assertTrue(result.getMassClosureRelativeError() <= BALANCE_TOLERANCE);
      assertTrue(result.getMaximumComponentMolarClosureRelativeError() <= BALANCE_TOLERANCE);
      assertTrue(result.getColumnEnergyBalanceError() <= BALANCE_TOLERANCE);

      expectedMinimumOverhead = Math.min(expectedMinimumOverhead, point.getOverheadMassFraction());
      expectedMaximumOverhead = Math.max(expectedMaximumOverhead, point.getOverheadMassFraction());
      expectedMaximumMassClosure = Math.max(expectedMaximumMassClosure, result.getMassClosureRelativeError());
      expectedMaximumComponentClosure = Math.max(expectedMaximumComponentClosure,
          result.getMaximumComponentMolarClosureRelativeError());
      expectedMaximumEnergyError = Math.max(expectedMaximumEnergyError, result.getColumnEnergyBalanceError());
      expectedMaximumMeshResidual = Math.max(expectedMaximumMeshResidual, result.getMeshResidualNorm());
    }

    assertEquals(expectedMinimumOverhead, screen.getMinimumOverheadMassFraction(), 0.0);
    assertEquals(expectedMaximumOverhead, screen.getMaximumOverheadMassFraction(), 0.0);
    assertEquals(expectedMaximumMassClosure, screen.getMaximumMassClosureRelativeError(), 0.0);
    assertEquals(expectedMaximumComponentClosure, screen.getMaximumComponentMolarClosureRelativeError(), 0.0);
    assertEquals(expectedMaximumEnergyError, screen.getMaximumColumnEnergyBalanceError(), 0.0);
    assertEquals(expectedMaximumMeshResidual, screen.getMaximumMeshResidualNorm(), 0.0);
  }

  /** Require malformed scenario definitions to fail before presenting results. */
  @Test
  public void invalidScenarioDefinitionsFailClosed() {
    Scenario[] scenarios = documentedScenarios();

    assertThrows(IllegalArgumentException.class, () -> DoeBigHillVacuumScenarioScreen.run(" ", scenarios));
    assertThrows(NullPointerException.class, () -> DoeBigHillVacuumScenarioScreen.run("screen", null));
    assertThrows(IllegalArgumentException.class,
        () -> DoeBigHillVacuumScenarioScreen.run("screen", new Scenario[] { scenarios[0] }));
    assertThrows(NullPointerException.class,
        () -> DoeBigHillVacuumScenarioScreen.run("screen", new Scenario[] { scenarios[0], null }));
    assertThrows(IllegalArgumentException.class, () -> DoeBigHillVacuumScenarioScreen.run("screen",
        new Scenario[] { scenarios[0], new Scenario("LOW", 1000.0, baselineInputs()) }));

    assertThrows(IllegalArgumentException.class, () -> new Scenario(" ", 1000.0, baselineInputs()));
    assertThrows(IllegalArgumentException.class, () -> new Scenario("invalid", 0.0, baselineInputs()));
    assertThrows(IllegalArgumentException.class, () -> new Scenario("invalid", Double.NaN, baselineInputs()));
    assertThrows(NullPointerException.class, () -> new Scenario("invalid", 1000.0, null));
  }

  private static Scenario[] documentedScenarios() {
    OperatingInputs low = new OperatingInputs(12, 4, 638.0, 0.1176, 0.0784, 0.1568, 698.0, 0.49);
    OperatingInputs base = baselineInputs();
    OperatingInputs high = new OperatingInputs(12, 4, 642.0, 0.1224, 0.0816, 0.1632, 702.0, 0.51);
    return new Scenario[] { new Scenario("low", 980.0, low), new Scenario("base", 1000.0, base),
        new Scenario("high", 1020.0, high) };
  }

  private static OperatingInputs baselineInputs() {
    return new OperatingInputs(12, 4, 640.0, 0.12, 0.08, 0.16, 700.0, 0.5);
  }
}

