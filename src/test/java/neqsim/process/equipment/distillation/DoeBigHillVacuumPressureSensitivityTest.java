package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import neqsim.process.equipment.distillation.DoeBigHillVacuumFractionationCase.OperatingInputs;
import neqsim.process.equipment.distillation.DoeBigHillVacuumPressureSensitivity.PointResult;
import neqsim.process.equipment.distillation.DoeBigHillVacuumFractionationResult.ProductResult;

/** Qualification tests for {@link DoeBigHillVacuumPressureSensitivity}. */
public class DoeBigHillVacuumPressureSensitivityTest {
  private static final double FEED_MASS_FLOW_KG_PER_HOUR = 1000.0;
  private static final double BALANCE_TOLERANCE = 5.0e-2;

  /**
   * Execute the documented three-point pressure screen with every other input held fixed.
   */
  @Test
  @Timeout(value = 300, unit = TimeUnit.SECONDS)
  public void pressureScreenReturnsQualifiedImmutablePoints() {
    OperatingInputs baseline = baselineInputs();
    double[] factors = {0.98, 1.00, 1.02};

    DoeBigHillVacuumPressureSensitivity sensitivity =
        DoeBigHillVacuumPressureSensitivity.run("Big Hill vacuum pressure screen",
            FEED_MASS_FLOW_KG_PER_HOUR, baseline, factors);

    factors[0] = 99.0;
    PointResult[] points = sensitivity.getPoints();
    assertEquals(3, points.length);
    assertNotSame(points, sensitivity.getPoints());
    assertEquals(0.98, points[0].getPressureScaleFactor(), 0.0);
    assertEquals(1.00, points[1].getPressureScaleFactor(), 0.0);
    assertEquals(1.02, points[2].getPressureScaleFactor(), 0.0);
    assertEquals(points[0], sensitivity.getPoint(0));
    assertEquals(points[2], sensitivity.getPoint(2));
    assertThrows(IndexOutOfBoundsException.class, () -> sensitivity.getPoint(-1));
    assertThrows(IndexOutOfBoundsException.class, () -> sensitivity.getPoint(3));

    double expectedMinimumOverhead = Double.POSITIVE_INFINITY;
    double expectedMaximumOverhead = Double.NEGATIVE_INFINITY;
    double expectedMaximumMassClosure = 0.0;
    double expectedMaximumComponentClosure = 0.0;
    double expectedMaximumEnergyError = 0.0;
    double expectedMaximumMeshResidual = 0.0;

    for (PointResult point : points) {
      double factor = point.getPressureScaleFactor();
      OperatingInputs applied = point.getOperatingInputs();
      assertEquals(baseline.getTopPressureBara() * factor, applied.getTopPressureBara(),
          1.0e-12);
      assertEquals(baseline.getFeedPressureBara() * factor, applied.getFeedPressureBara(),
          1.0e-12);
      assertEquals(baseline.getBottomPressureBara() * factor,
          applied.getBottomPressureBara(), 1.0e-12);
      assertEquals(baseline.getSimpleTrayCount(), applied.getSimpleTrayCount());
      assertEquals(baseline.getFeedTrayIndex(), applied.getFeedTrayIndex());
      assertEquals(baseline.getFeedTemperatureKelvin(), applied.getFeedTemperatureKelvin(),
          0.0);
      assertEquals(baseline.getReboilerTemperatureKelvin(),
          applied.getReboilerTemperatureKelvin(), 0.0);
      assertEquals(baseline.getCondenserRefluxRatio(),
          applied.getCondenserRefluxRatio(), 0.0);

      DoeBigHillVacuumFractionationResult result = point.getFractionationResult();
      ProductResult overhead = result.getProduct("Overhead");
      ProductResult bottoms = result.getProduct("Bottoms");
      assertTrue(overhead.getMassFractionOfFeed() > 0.0);
      assertTrue(bottoms.getMassFractionOfFeed() > 0.0);
      assertTrue(overhead.getMeanNormalBoilingPointKelvin()
          < bottoms.getMeanNormalBoilingPointKelvin());
      assertTrue(Double.isFinite(point.getOverheadBoilingPointQuantileKelvin(0.10)));
      assertTrue(Double.isFinite(point.getOverheadBoilingPointQuantileKelvin(0.50)));
      assertTrue(Double.isFinite(point.getOverheadBoilingPointQuantileKelvin(0.90)));
      assertThrows(IllegalArgumentException.class,
          () -> point.getOverheadBoilingPointQuantileKelvin(0.0));
      assertTrue(result.getMassClosureRelativeError() <= BALANCE_TOLERANCE);
      assertTrue(result.getMaximumComponentMolarClosureRelativeError()
          <= BALANCE_TOLERANCE);
      assertTrue(result.getColumnEnergyBalanceError() <= BALANCE_TOLERANCE);

      expectedMinimumOverhead =
          Math.min(expectedMinimumOverhead, point.getOverheadMassFraction());
      expectedMaximumOverhead =
          Math.max(expectedMaximumOverhead, point.getOverheadMassFraction());
      expectedMaximumMassClosure =
          Math.max(expectedMaximumMassClosure, result.getMassClosureRelativeError());
      expectedMaximumComponentClosure = Math.max(expectedMaximumComponentClosure,
          result.getMaximumComponentMolarClosureRelativeError());
      expectedMaximumEnergyError =
          Math.max(expectedMaximumEnergyError, result.getColumnEnergyBalanceError());
      expectedMaximumMeshResidual =
          Math.max(expectedMaximumMeshResidual, result.getMeshResidualNorm());
    }

    assertEquals(expectedMinimumOverhead, sensitivity.getMinimumOverheadMassFraction(), 0.0);
    assertEquals(expectedMaximumOverhead, sensitivity.getMaximumOverheadMassFraction(), 0.0);
    assertEquals(expectedMaximumMassClosure,
        sensitivity.getMaximumMassClosureRelativeError(), 0.0);
    assertEquals(expectedMaximumComponentClosure,
        sensitivity.getMaximumComponentMolarClosureRelativeError(), 0.0);
    assertEquals(expectedMaximumEnergyError,
        sensitivity.getMaximumColumnEnergyBalanceError(), 0.0);
    assertEquals(expectedMaximumMeshResidual, sensitivity.getMaximumMeshResidualNorm(), 0.0);
  }

  /** Require malformed sensitivity definitions to fail before presenting an envelope. */
  @Test
  public void invalidSensitivityInputsFailClosed() {
    OperatingInputs baseline = baselineInputs();

    assertThrows(IllegalArgumentException.class,
        () -> DoeBigHillVacuumPressureSensitivity.run(" ", FEED_MASS_FLOW_KG_PER_HOUR,
            baseline, new double[] {0.98, 1.02}));
    assertThrows(IllegalArgumentException.class,
        () -> DoeBigHillVacuumPressureSensitivity.run("screen", 0.0, baseline,
            new double[] {0.98, 1.02}));
    assertThrows(NullPointerException.class,
        () -> DoeBigHillVacuumPressureSensitivity.run("screen",
            FEED_MASS_FLOW_KG_PER_HOUR, null, new double[] {0.98, 1.02}));
    assertThrows(NullPointerException.class,
        () -> DoeBigHillVacuumPressureSensitivity.run("screen",
            FEED_MASS_FLOW_KG_PER_HOUR, baseline, null));
    assertThrows(IllegalArgumentException.class,
        () -> DoeBigHillVacuumPressureSensitivity.run("screen",
            FEED_MASS_FLOW_KG_PER_HOUR, baseline, new double[] {1.0}));
    assertThrows(IllegalArgumentException.class,
        () -> DoeBigHillVacuumPressureSensitivity.run("screen",
            FEED_MASS_FLOW_KG_PER_HOUR, baseline, new double[] {1.0, 1.0}));
    assertThrows(IllegalArgumentException.class,
        () -> DoeBigHillVacuumPressureSensitivity.run("screen",
            FEED_MASS_FLOW_KG_PER_HOUR, baseline, new double[] {1.02, 0.98}));
    assertThrows(IllegalArgumentException.class,
        () -> DoeBigHillVacuumPressureSensitivity.run("screen",
            FEED_MASS_FLOW_KG_PER_HOUR, baseline,
            new double[] {Double.NaN, 1.0}));
    assertThrows(IllegalArgumentException.class,
        () -> DoeBigHillVacuumPressureSensitivity.run("screen",
            FEED_MASS_FLOW_KG_PER_HOUR, baseline, new double[] {10.0, 11.0}));
  }

  private static OperatingInputs baselineInputs() {
    return new OperatingInputs(12, 4, 640.0, 0.12, 0.08, 0.16, 700.0, 0.5);
  }
}
