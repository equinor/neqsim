package neqsim.process.safety.pump;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for {@link PumpDeadheadAnalyzer}.
 *
 * @author ESOL
 * @version 1.0
 */
public class PumpDeadheadAnalyzerTest {

  /**
   * A deadhead pressure above the protected rating should produce an EXCEEDS_RATING verdict with a
   * negative margin.
   */
  @Test
  void deadheadPressureExceedsRating() {
    PumpDeadheadResult result = new PumpDeadheadAnalyzer().setSuctionPressure(2.0, "bara")
        .setNormalDischargePressure(12.0, "bara").setShutoffHeadRatio(1.2)
        .setProtectedPressureRating(13.0, "bara").analyze();

    assertEquals(PumpDeadheadAnalyzer.DeadheadVerdict.EXCEEDS_RATING, result.getVerdict());
    assertTrue(result.isPressureRatingExceeded(), "rating should be exceeded");
    assertEquals(14.0, result.getDeadheadPressureBara(), 1.0e-6, "deadhead pressure");
    assertTrue(result.getPressureMarginBara() < 0.0, "margin should be negative");
  }

  /**
   * A deadhead pressure within the protected rating should produce a WITHIN_RATING verdict with a
   * positive margin.
   */
  @Test
  void deadheadWithinRating() {
    PumpDeadheadResult result = new PumpDeadheadAnalyzer().setSuctionPressure(2.0, "bara")
        .setNormalDischargePressure(12.0, "bara").setShutoffHeadRatio(1.2)
        .setProtectedPressureRating(20.0, "bara").analyze();

    assertEquals(PumpDeadheadAnalyzer.DeadheadVerdict.WITHIN_RATING, result.getVerdict());
    assertFalse(result.isPressureRatingExceeded(), "rating should not be exceeded");
    assertTrue(result.getPressureMarginBara() > 0.0, "margin should be positive");
  }

  /**
   * When no protected rating is supplied the verdict is NO_RATING, the margin is NaN and the result
   * still serialises to JSON.
   */
  @Test
  void noRatingSerialisesWithNaNMargin() {
    PumpDeadheadResult result = new PumpDeadheadAnalyzer().setSuctionPressure(2.0, "bara")
        .setNormalDischargePressure(12.0, "bara").analyze();

    assertEquals(PumpDeadheadAnalyzer.DeadheadVerdict.NO_RATING, result.getVerdict());
    assertFalse(result.isRatingDataProvided(), "rating data should not be provided");
    assertTrue(Double.isNaN(result.getPressureMarginBara()), "margin should be NaN");
    assertTrue(result.toJson().contains("deadheadPressureBara"), "JSON should contain deadhead");
  }

  /**
   * Supplying liquid density and specific heat directly should produce a finite, positive
   * recirculation temperature rise and a positive shut-off head.
   */
  @Test
  void recirculationTempRiseFromDirectProperties() {
    PumpDeadheadResult result = new PumpDeadheadAnalyzer().setSuctionPressure(2.0, "bara")
        .setNormalDischargePressure(12.0, "bara").setShutoffHeadRatio(1.2)
        .setLiquidDensity(800.0, "kg/m3").setSpecificHeat(2000.0, "J/kgK")
        .setMinimumFlowEfficiency(0.3).setMaxAllowableTemperatureRise(1.0, "K").analyze();

    assertTrue(result.isTempRiseDataAvailable(), "temperature rise should be available");
    assertTrue(result.getRecirculationTempRiseK() > 0.0, "temperature rise should be positive");
    assertTrue(Double.isFinite(result.getRecirculationTempRiseK()),
        "temperature rise should be finite");
    assertTrue(result.getShutoffHeadM() > 0.0, "shut-off head should be positive");
    assertTrue(result.isTempRiseExceeded(), "1 K allowable should be exceeded by ~1.75 K rise");
  }

  /**
   * A NeqSim fluid should yield liquid density and specific heat and a finite recirculation
   * temperature rise when no properties are supplied directly.
   */
  @Test
  void fluidDerivesPropertiesAndProducesTempRise() {
    SystemInterface water = new SystemSrkEos(298.15, 10.0);
    water.addComponent("water", 1.0);
    water.setMixingRule("classic");

    PumpDeadheadResult result =
        new PumpDeadheadAnalyzer().setSuctionPressure(2.0, "bara").setSuctionTemperature(25.0, "C")
            .setNormalDischargePressure(12.0, "bara").setFluid(water).analyze();

    assertTrue(result.isTempRiseDataAvailable(), "temperature rise should be available from fluid");
    assertTrue(result.getLiquidDensityKgPerM3() > 0.0, "derived density should be positive");
    assertTrue(result.getSpecificHeatJPerKgK() > 0.0, "derived specific heat should be positive");
    assertTrue(result.getRecirculationTempRiseK() > 0.0, "temperature rise should be positive");
  }

  /**
   * Non-physical configuration (discharge pressure below suction) is rejected.
   */
  @Test
  void rejectsDischargeBelowSuction() {
    final PumpDeadheadAnalyzer analyzer = new PumpDeadheadAnalyzer()
        .setSuctionPressure(10.0, "bara").setNormalDischargePressure(5.0, "bara");
    assertThrows(IllegalStateException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        analyzer.analyze();
      }
    });
  }
}
