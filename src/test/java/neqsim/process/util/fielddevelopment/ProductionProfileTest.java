package neqsim.process.util.fielddevelopment;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.util.fielddevelopment.ProductionProfile.DeclineParameters;
import neqsim.process.util.fielddevelopment.ProductionProfile.DeclineType;
import neqsim.process.util.fielddevelopment.ProductionProfile.ProductionForecast;
import neqsim.process.util.fielddevelopment.ProductionProfile.ProductionPoint;

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

  private ProductionProfile profile;

  @BeforeEach
  void setUp() {
    profile = new ProductionProfile("TestProfile");
  }

  @Test
  @DisplayName("Exponential decline calculates correct rate")
  void testExponentialDecline() {
    double initialRate = 1000.0;
    double declineRate = 0.10; // 10% per year
    double timeYears = 1.0;

    // Expected: q(t) = q0 * exp(-D*t) = 1000 * exp(-0.10 * 1) ≈ 904.84
    double expectedRate = initialRate * Math.exp(-declineRate * timeYears);

    DeclineParameters params =
        new DeclineParameters(DeclineType.EXPONENTIAL, initialRate, declineRate, 0.0, 0.0);
    profile.setDeclineParameters(params);

    double calculatedRate = profile.calculateRate(timeYears);

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
        new DeclineParameters(DeclineType.HYPERBOLIC, initialRate, declineRate, bFactor, 0.0);
    profile.setDeclineParameters(params);

    double calculatedRate = profile.calculateRate(timeYears);

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
        new DeclineParameters(DeclineType.HARMONIC, initialRate, declineRate, 0.0, 0.0);
    profile.setDeclineParameters(params);

    double calculatedRate = profile.calculateRate(timeYears);

    assertEquals(expectedRate, calculatedRate, 0.01, "Harmonic decline rate mismatch");
  }

  @Test
  @DisplayName("Plateau period maintains constant rate")
  void testPlateauRate() {
    double initialRate = 1000.0;
    double plateauRate = 800.0;
    double declineRate = 0.10;
    double plateauDuration = 2.0; // years

    DeclineParameters params =
        new DeclineParameters(DeclineType.EXPONENTIAL, initialRate, declineRate, 0.0, plateauRate);
    profile.setDeclineParameters(params);
    profile.setPlateauDuration(plateauDuration);

    // During plateau period, rate should be plateau rate
    double rateDuringPlateau = profile.calculateRate(1.0);
    assertEquals(plateauRate, rateDuringPlateau, 0.01, "Rate during plateau should be constant");

    // After plateau, decline should start
    double rateAfterPlateau = profile.calculateRate(3.0);
    assertTrue(rateAfterPlateau < plateauRate, "Rate should decline after plateau period");
  }

  @Test
  @DisplayName("Forecast generates correct number of points")
  void testForecastGeneration() {
    DeclineParameters params =
        new DeclineParameters(DeclineType.EXPONENTIAL, 1000.0, 0.10, 0.0, 0.0);
    profile.setDeclineParameters(params);

    LocalDate startDate = LocalDate.of(2024, 1, 1);
    int forecastYears = 10;

    ProductionForecast forecast = profile.generateForecast(startDate, forecastYears, 12);

    assertNotNull(forecast, "Forecast should not be null");
    List<ProductionPoint> points = forecast.getProductionPoints();
    assertNotNull(points, "Production points should not be null");

    // Monthly for 10 years = 120 points + initial = 121 or close
    assertTrue(points.size() >= forecastYears * 12, "Should have at least 120 monthly points");
  }

  @Test
  @DisplayName("Cumulative production calculation")
  void testCumulativeProduction() {
    double initialRate = 1000.0; // bbl/day
    double declineRate = 0.10;
    double timeYears = 5.0;

    DeclineParameters params =
        new DeclineParameters(DeclineType.EXPONENTIAL, initialRate, declineRate, 0.0, 0.0);
    profile.setDeclineParameters(params);

    double cumulative = profile.calculateCumulativeProduction(timeYears);

    // For exponential: Np = (q0 / D) * (1 - exp(-D*t))
    double expectedCumulative =
        (initialRate * 365) / declineRate * (1 - Math.exp(-declineRate * timeYears));

    assertTrue(cumulative > 0, "Cumulative production should be positive");
    assertEquals(expectedCumulative, cumulative, expectedCumulative * 0.01,
        "Cumulative production calculation error");
  }

  @Test
  @DisplayName("Economic limit detection")
  void testEconomicLimit() {
    double initialRate = 1000.0;
    double declineRate = 0.20;
    double economicLimit = 50.0; // bbl/day

    DeclineParameters params =
        new DeclineParameters(DeclineType.EXPONENTIAL, initialRate, declineRate, 0.0, 0.0);
    profile.setDeclineParameters(params);
    profile.setEconomicLimit(economicLimit);

    double timeToLimit = profile.calculateTimeToEconomicLimit();

    // For exponential: t = -ln(qlimit/q0) / D
    double expectedTime = -Math.log(economicLimit / initialRate) / declineRate;

    assertEquals(expectedTime, timeToLimit, 0.01, "Time to economic limit calculation error");
  }

  @Test
  @DisplayName("Profile name getter works correctly")
  void testProfileName() {
    assertEquals("TestProfile", profile.getName(), "Profile name should match");
  }

  @Test
  @DisplayName("Zero time returns initial rate")
  void testZeroTimeReturnsInitialRate() {
    double initialRate = 500.0;

    DeclineParameters params =
        new DeclineParameters(DeclineType.EXPONENTIAL, initialRate, 0.10, 0.0, 0.0);
    profile.setDeclineParameters(params);

    double rate = profile.calculateRate(0.0);

    assertEquals(initialRate, rate, 0.001, "Rate at time zero should equal initial rate");
  }

  @Test
  @DisplayName("Multiple profiles can be combined")
  void testMultipleProfilesCombination() {
    ProductionProfile profile1 = new ProductionProfile("Well1");
    ProductionProfile profile2 = new ProductionProfile("Well2");

    profile1.setDeclineParameters(
        new DeclineParameters(DeclineType.EXPONENTIAL, 500.0, 0.10, 0.0, 0.0));
    profile2.setDeclineParameters(
        new DeclineParameters(DeclineType.EXPONENTIAL, 300.0, 0.15, 0.0, 0.0));

    double time = 2.0;
    double combinedRate = profile1.calculateRate(time) + profile2.calculateRate(time);

    assertTrue(combinedRate > 0, "Combined rate should be positive");
    assertTrue(combinedRate < 800.0, "Combined rate should be less than sum of initial rates");
  }

  @Test
  @DisplayName("Decline parameters validation")
  void testDeclineParametersValidation() {
    // Negative decline rate should be handled
    DeclineParameters params =
        new DeclineParameters(DeclineType.EXPONENTIAL, 1000.0, -0.10, 0.0, 0.0);
    profile.setDeclineParameters(params);

    // Rate should increase (negative decline = growth)
    double rate = profile.calculateRate(1.0);
    assertTrue(rate > 1000.0, "Negative decline should result in rate increase");
  }
}
