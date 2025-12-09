package neqsim.process.util.fielddevelopment;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.fielddevelopment.ProductionProfile.DeclineParameters;
import neqsim.process.util.fielddevelopment.ProductionProfile.DeclineType;
import neqsim.process.util.fielddevelopment.ProductionProfile.ProductionForecast;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for {@link ProductionProfile}.
 *
 * <p>
 * Tests production decline curve calculations including exponential, hyperbolic, and harmonic
 * decline models, as well as plateau rate handling.
 * </p>
 *
 * @author NeqSim Development Team
 */
public class ProductionProfileTest {

  @Test
  @DisplayName("Exponential decline calculates correct rate")
  void testExponentialDecline() {
    double initialRate = 1000.0;
    double declineRate = 0.10; // 10% per year
    double timeYears = 1.0;

    // Expected: q(t) = q0 * exp(-D*t) = 1000 * exp(-0.10 * 1) ≈ 904.84
    double expectedRate = initialRate * Math.exp(-declineRate * timeYears);

    DeclineParameters params =
        new DeclineParameters(DeclineType.EXPONENTIAL, initialRate, declineRate, 0.0, "bbl/day");

    double calculatedRate = ProductionProfile.calculateRate(params, timeYears);

    assertEquals(expectedRate, calculatedRate, 0.01, "Exponential decline rate mismatch");
    assertTrue(calculatedRate < initialRate, "Rate should decline over time");
  }

  @Test
  @DisplayName("Hyperbolic decline calculates correct rate")
  void testHyperbolicDecline() {
    double initialRate = 1000.0;
    double declineRate = 0.15;
    double bFactor = 0.5;
    double timeYears = 2.0;

    // Expected: q(t) = q0 / (1 + b*D*t)^(1/b)
    double expectedRate =
        initialRate / Math.pow(1 + bFactor * declineRate * timeYears, 1 / bFactor);

    DeclineParameters params =
        new DeclineParameters(DeclineType.HYPERBOLIC, initialRate, declineRate, bFactor, "bbl/day");

    double calculatedRate = ProductionProfile.calculateRate(params, timeYears);

    assertEquals(expectedRate, calculatedRate, 0.01, "Hyperbolic decline rate mismatch");
  }

  @Test
  @DisplayName("Harmonic decline calculates correct rate")
  void testHarmonicDecline() {
    double initialRate = 1000.0;
    double declineRate = 0.12;
    double timeYears = 3.0;

    // Expected: q(t) = q0 / (1 + D*t)
    double expectedRate = initialRate / (1 + declineRate * timeYears);

    DeclineParameters params =
        new DeclineParameters(DeclineType.HARMONIC, initialRate, declineRate, 0.0, "bbl/day");

    double calculatedRate = ProductionProfile.calculateRate(params, timeYears);

    assertEquals(expectedRate, calculatedRate, 0.01, "Harmonic decline rate mismatch");
  }

  @Test
  @DisplayName("Zero time returns initial rate")
  void testZeroTimeReturnsInitialRate() {
    double initialRate = 500.0;

    DeclineParameters params =
        new DeclineParameters(DeclineType.EXPONENTIAL, initialRate, 0.10, 0.0, "bbl/day");

    double rate = ProductionProfile.calculateRate(params, 0.0);

    assertEquals(initialRate, rate, 0.001, "Rate at time zero should equal initial rate");
  }

  @Test
  @DisplayName("Negative time throws exception")
  void testNegativeTimeThrowsException() {
    DeclineParameters params =
        new DeclineParameters(DeclineType.EXPONENTIAL, 1000.0, 0.10, 0.0, "bbl/day");

    assertThrows(IllegalArgumentException.class, () -> {
      ProductionProfile.calculateRate(params, -1.0);
    });
  }

  @Test
  @DisplayName("DeclineParameters stores values correctly")
  void testDeclineParametersValues() {
    DeclineParameters params =
        new DeclineParameters(DeclineType.HYPERBOLIC, 1000.0, 0.15, 0.5, "bbl/day");

    assertEquals(DeclineType.HYPERBOLIC, params.getType());
    assertEquals(1000.0, params.getInitialRate(), 0.001);
    assertEquals(0.15, params.getDeclineRate(), 0.001);
    assertEquals(0.5, params.getHyperbolicExponent(), 0.001);
    assertEquals("bbl/day", params.getRateUnit());
  }

  @Test
  @DisplayName("Cumulative production calculation for exponential decline")
  void testCumulativeProductionExponential() {
    double initialRate = 1000.0; // per year
    double declineRate = 0.10;
    double timeYears = 5.0;

    DeclineParameters params =
        new DeclineParameters(DeclineType.EXPONENTIAL, initialRate, declineRate, 0.0, "units/year");

    double cumulative = ProductionProfile.calculateCumulativeProduction(params, timeYears);

    // For exponential: Np = (q0 / D) * (1 - exp(-D*t))
    double expectedCumulative =
        (initialRate / declineRate) * (1 - Math.exp(-declineRate * timeYears));

    assertEquals(expectedCumulative, cumulative, expectedCumulative * 0.01,
        "Cumulative production calculation error");
  }

  @Test
  @DisplayName("Cumulative production at zero time is zero")
  void testCumulativeProductionAtZero() {
    DeclineParameters params =
        new DeclineParameters(DeclineType.EXPONENTIAL, 1000.0, 0.10, 0.0, "bbl/day");

    double cumulative = ProductionProfile.calculateCumulativeProduction(params, 0.0);

    assertEquals(0.0, cumulative, 0.001, "Cumulative at t=0 should be zero");
  }

  @Test
  @DisplayName("Economic limit calculation for exponential decline")
  void testEconomicLimitExponential() {
    double initialRate = 1000.0;
    double declineRate = 0.20;
    double economicLimit = 50.0;

    DeclineParameters params =
        new DeclineParameters(DeclineType.EXPONENTIAL, initialRate, declineRate, 0.0, "bbl/day");

    double timeToLimit = ProductionProfile.calculateTimeToEconomicLimit(params, economicLimit);

    // For exponential: t = -ln(qlimit/q0) / D
    double expectedTime = -Math.log(economicLimit / initialRate) / declineRate;

    assertEquals(expectedTime, timeToLimit, 0.01, "Time to economic limit calculation error");
  }

  @Test
  @DisplayName("Economic limit at initial rate returns zero time")
  void testEconomicLimitAtInitialRate() {
    DeclineParameters params =
        new DeclineParameters(DeclineType.EXPONENTIAL, 1000.0, 0.10, 0.0, "bbl/day");

    double timeToLimit = ProductionProfile.calculateTimeToEconomicLimit(params, 1000.0);

    assertEquals(0.0, timeToLimit, 0.001, "Time should be zero when limit equals initial rate");
  }

  @Test
  @DisplayName("ProductionProfile with no facility constructs correctly")
  void testProductionProfileNoFacility() {
    ProductionProfile profile = new ProductionProfile();
    assertNotNull(profile);
  }

  @Test
  @DisplayName("ProductionProfile with facility constructs correctly")
  void testProductionProfileWithFacility() {
    ProcessSystem process = new ProcessSystem();

    SystemInterface gasSystem = new SystemSrkEos(298.15, 50.0);
    gasSystem.addComponent("methane", 0.9);
    gasSystem.addComponent("ethane", 0.1);
    gasSystem.setMixingRule("classic");

    Stream feed = new Stream("Feed", gasSystem);
    feed.setFlowRate(10000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(50.0, "bara");

    process.add(feed);

    ProductionProfile profile = new ProductionProfile(process);
    assertNotNull(profile);
  }

  @Test
  @DisplayName("Forecast generates production points")
  void testForecastGeneration() {
    ProductionProfile profile = new ProductionProfile();

    DeclineParameters params =
        new DeclineParameters(DeclineType.EXPONENTIAL, 1000.0, 0.10, 0.0, "bbl/day");

    double plateauRate = 800.0;
    double plateauDuration = 2.0;
    double economicLimit = 50.0;
    double forecastYears = 10.0;
    double timeStepDays = 30.0;

    ProductionForecast forecast = profile.forecast(null, params, plateauRate, plateauDuration,
        economicLimit, forecastYears, timeStepDays);

    assertNotNull(forecast, "Forecast should not be null");
    assertTrue(forecast.getProfile().size() > 0, "Forecast should have production points");
  }

  @Test
  @DisplayName("Forecast plateau rate is respected")
  void testForecastPlateauRate() {
    ProductionProfile profile = new ProductionProfile();

    DeclineParameters params =
        new DeclineParameters(DeclineType.EXPONENTIAL, 1000.0, 0.10, 0.0, "bbl/day");

    double plateauRate = 800.0;
    double plateauDuration = 2.0;
    double economicLimit = 50.0;
    double forecastYears = 10.0;
    double timeStepDays = 30.0;

    ProductionForecast forecast = profile.forecast(null, params, plateauRate, plateauDuration,
        economicLimit, forecastYears, timeStepDays);

    // First point during plateau should be at plateau rate
    double firstRate = forecast.getProfile().get(0).getRate();
    assertTrue(firstRate <= plateauRate, "Initial rate should not exceed plateau rate");
  }

  @Test
  @DisplayName("Forecast cumulative production increases")
  void testForecastCumulativeIncreases() {
    ProductionProfile profile = new ProductionProfile();

    DeclineParameters params =
        new DeclineParameters(DeclineType.EXPONENTIAL, 1000.0, 0.10, 0.0, "bbl/day");

    ProductionForecast forecast = profile.forecast(null, params, 800.0, 2.0, 50.0, 10.0, 30.0);

    var points = forecast.getProfile();
    for (int i = 1; i < points.size(); i++) {
      assertTrue(
          points.get(i).getCumulativeProduction() >= points.get(i - 1).getCumulativeProduction(),
          "Cumulative production should increase");
    }
  }

  @Test
  @DisplayName("Forecast total cumulative is accessible")
  void testForecastTotalCumulative() {
    ProductionProfile profile = new ProductionProfile();

    DeclineParameters params =
        new DeclineParameters(DeclineType.EXPONENTIAL, 1000.0, 0.10, 0.0, "bbl/day");

    ProductionForecast forecast = profile.forecast(null, params, 800.0, 2.0, 50.0, 10.0, 30.0);

    double totalCumulative = forecast.getTotalCumulativeProduction();
    assertTrue(totalCumulative > 0, "Total cumulative should be positive");
  }

  @Test
  @DisplayName("Different decline types produce different rates")
  void testDifferentDeclineTypesProduceDifferentRates() {
    double qi = 1000.0;
    double d = 0.15;
    double t = 3.0;

    DeclineParameters expParams = new DeclineParameters(DeclineType.EXPONENTIAL, qi, d, 0.0, "u");
    DeclineParameters hypParams = new DeclineParameters(DeclineType.HYPERBOLIC, qi, d, 0.5, "u");
    DeclineParameters harParams = new DeclineParameters(DeclineType.HARMONIC, qi, d, 0.0, "u");

    double expRate = ProductionProfile.calculateRate(expParams, t);
    double hypRate = ProductionProfile.calculateRate(hypParams, t);
    double harRate = ProductionProfile.calculateRate(harParams, t);

    // All should decline from initial
    assertTrue(expRate < qi);
    assertTrue(hypRate < qi);
    assertTrue(harRate < qi);

    // Exponential should be lowest, harmonic should be highest
    assertTrue(expRate < hypRate, "Exponential should decline faster than hyperbolic");
    assertTrue(hypRate < harRate, "Hyperbolic should decline faster than harmonic");
  }

  @Test
  @DisplayName("DeclineParameters toString is readable")
  void testDeclineParametersToString() {
    DeclineParameters params =
        new DeclineParameters(DeclineType.EXPONENTIAL, 1000.0, 0.10, 0.0, "bbl/day");

    String str = params.toString();
    assertNotNull(str);
    assertTrue(str.contains("EXPONENTIAL") || str.contains("1000"), "Should contain key info");
  }
}
