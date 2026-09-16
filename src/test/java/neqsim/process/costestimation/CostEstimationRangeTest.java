package neqsim.process.costestimation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests that the Turton capacity correlations stay inside their validity range.
 *
 * <p>
 * The correlations are fitted over a limited capacity band. Evaluating them far outside that band silently returns a
 * cost that is too low, because the negative second-order term flattens the curve. Large duties are instead costed as
 * parallel units sized inside the range.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class CostEstimationRangeTest {
  /** Compressor cost must grow at least in proportion to duty for large trains. */
  @Test
  void largeCompressorTrainScalesWithDuty() {
    CostEstimationCalculator calculator = new CostEstimationCalculator(850.0);

    double small = calculator.calcCentrifugalCompressorCost(3000.0);
    double tenTimes = calculator.calcCentrifugalCompressorCost(30000.0);
    double eightyTimes = calculator.calcCentrifugalCompressorCost(240000.0);

    assertTrue(tenTimes > small * 8.0,
        "a ten times larger duty must cost at least eight times as much, got " + tenTimes / small);
    assertTrue(eightyTimes > tenTimes * 6.0,
        "an eighty times larger duty must keep scaling, got " + eightyTimes / tenTimes);
  }

  /** A 240 MW refrigeration train must land in a credible purchased-cost band. */
  @Test
  void largeRefrigerationTrainIsCredible() {
    CostEstimationCalculator calculator = new CostEstimationCalculator(850.0);
    double cost = calculator.calcCentrifugalCompressorCost(240000.0);

    assertTrue(cost > 2.0e7, "a 240 MW compressor train cannot cost less than 20 MUSD, got " + cost);
    assertTrue(cost < 5.0e8, "a 240 MW compressor train cannot cost more than 500 MUSD purchased, got " + cost);
  }

  /** Inside the correlation range the result must be unchanged by the parallel-unit logic. */
  @Test
  void insideRangeIsUnchanged() {
    CostEstimationCalculator calculator = new CostEstimationCalculator(850.0);
    double power = 1500.0;

    double k1 = 2.2897;
    double k2 = 1.3604;
    double k3 = -0.1027;
    double logS = Math.log10(power);
    double expected = Math.pow(10, k1 + k2 * logS + k3 * logS * logS)
        * (850.0 / calculator.getCurrentCepci() * calculator.getCurrentCepci() / 850.0) * (850.0 / 607.5);

    assertEquals(expected, calculator.calcCentrifugalCompressorCost(power), expected * 0.02);
  }

  /** Reciprocating machines must stay more expensive than centrifugal for the same duty. */
  @Test
  void reciprocatingIsMoreExpensiveThanCentrifugal() {
    CostEstimationCalculator calculator = new CostEstimationCalculator(850.0);

    assertTrue(calculator.calcReciprocatingCompressorCost(2000.0) > calculator.calcCentrifugalCompressorCost(2000.0));
    assertTrue(calculator.calcReciprocatingCompressorCost(60000.0) > calculator.calcCentrifugalCompressorCost(60000.0));
  }

  /** Large pump duties must also scale rather than flatten. */
  @Test
  void largePumpDutyScales() {
    CostEstimationCalculator calculator = new CostEstimationCalculator(850.0);

    double small = calculator.calcCentrifugalPumpCost(300.0);
    double large = calculator.calcCentrifugalPumpCost(3000.0);

    assertTrue(large > small * 8.0, "pump cost must scale with duty, got " + large / small);
  }

  /**
   * The volume-based vessel correlation must stay in a credible band, unlike the deprecated weight-based entry point
   * that feeds a shell weight into volume coefficients.
   */
  @Test
  void volumeBasedVesselCostIsCredible() {
    CostEstimationCalculator calculator = new CostEstimationCalculator(850.0);

    double vessel = calculator.calcVerticalVesselCostByVolume(100.0);
    assertTrue(vessel > 2.0e4 && vessel < 1.0e6,
        "a 100 m3 vertical vessel must cost between 20 kUSD and 1 MUSD purchased, got " + vessel);

    @SuppressWarnings("deprecation")
    double legacy = calculator.calcVerticalVesselCost(95000.0);
    assertTrue(legacy > vessel * 100.0, "the deprecated weight-based entry point is expected to be far too high; "
        + "this test documents why it is deprecated");
  }
}
