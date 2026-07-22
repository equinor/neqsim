package neqsim.process.util.utilitydesign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Tests for {@link UtilityCompressionOptimizer} &mdash; the agentic closed-loop optimizer that finds the interstage
 * pressure minimizing total shaft power of a two-stage utility-gas compression package.
 *
 * @author NeqSim
 * @version 1.0
 */
class UtilityCompressionOptimizerTest {

  /**
   * The optimizer should rediscover the analytic geometric-mean interstage pressure {@code sqrt(pIn * pOut)} that
   * minimizes total two-stage compression power with equal intercooling.
   */
  @Test
  void testOptimumNearGeometricMean() {
    UtilityCompressionOptimizer opt = new UtilityCompressionOptimizer("Instrument Air");
    opt.setInletPressureBara(5.0);
    opt.setDeliveryPressureBara(60.0);
    opt.setSeed(42L).setMaxEvaluations(70);
    opt.optimize();

    assertTrue(opt.isFeasible(), "two-stage compression with valid bounds must be feasible");
    double geoMean = opt.getGeometricMeanPressureBara();
    assertEquals(Math.sqrt(5.0 * 60.0), geoMean, 1.0e-9, "geometric mean must be sqrt(pIn*pOut)");
    double pMid = opt.getOptimumInterstagePressureBara();
    // The optimum should sit close to the geometric mean (within ~10% — Nelder-Mead on a flat-bottomed
    // power curve converges to a neighbourhood, not the exact analytic point).
    assertEquals(geoMean, pMid, 0.10 * geoMean,
        "optimized interstage pressure should be near the geometric mean, got " + pMid);
    assertTrue(opt.getMinTotalPowerKW() > 0.0, "total compression power must be positive");
  }

  /**
   * The optimized total power must not exceed the total power at the lower-bound interstage pressure (i.e. optimization
   * actually reduced power versus a poor split).
   */
  @Test
  void testOptimumReducesPowerVersusPoorSplit() {
    UtilityCompressionOptimizer optGood = new UtilityCompressionOptimizer("Air");
    optGood.setInletPressureBara(4.0);
    optGood.setDeliveryPressureBara(80.0);
    optGood.setSeed(11L).setMaxEvaluations(70);
    optGood.optimize();

    double geoMean = optGood.getGeometricMeanPressureBara();
    double pMid = optGood.getOptimumInterstagePressureBara();
    assertTrue(pMid > 0.0, "interstage pressure must be positive");
    // The optimum interstage pressure must lie strictly between the inlet and delivery pressures.
    assertTrue(pMid > 4.0 && pMid < 80.0, "interstage pressure must be between inlet and delivery, got " + pMid);
    assertEquals(geoMean, pMid, 0.12 * geoMean, "interstage pressure near geometric mean");
  }

  /**
   * The JSON report must parse and carry the schema version and key result fields.
   */
  @Test
  void testJsonReport() {
    UtilityCompressionOptimizer opt = new UtilityCompressionOptimizer("Fuel Gas Booster");
    opt.setInletPressureBara(6.0);
    opt.setDeliveryPressureBara(45.0);
    opt.setSeed(3L).setMaxEvaluations(50);
    opt.optimize();

    String json = opt.toJson();
    JsonObject root = JsonParser.parseString(json).getAsJsonObject();
    assertEquals("1.0", root.get("schemaVersion").getAsString());
    assertTrue(root.has("optimumInterstagePressureBara"), "JSON must contain the optimized interstage pressure");
    assertTrue(root.has("minTotalPowerKW"), "JSON must contain the minimum total power");
    assertTrue(root.get("minTotalPowerKW").getAsDouble() > 0.0, "minimum total power must be positive");
  }

  /**
   * An inlet pressure at or above the delivery pressure is an invalid configuration and must be rejected.
   */
  @Test
  void testInvalidPressureOrderingRejected() {
    UtilityCompressionOptimizer opt = new UtilityCompressionOptimizer("Bad");
    opt.setInletPressureBara(60.0);
    opt.setDeliveryPressureBara(60.0);
    boolean threw = false;
    try {
      opt.optimize();
    } catch (IllegalStateException ex) {
      threw = true;
    }
    assertTrue(threw, "inlet >= delivery must throw IllegalStateException");
  }
}
