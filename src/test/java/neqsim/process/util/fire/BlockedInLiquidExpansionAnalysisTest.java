package neqsim.process.util.fire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for BlockedInLiquidExpansionAnalysis.
 *
 * @author ESOL
 */
public class BlockedInLiquidExpansionAnalysisTest {

  private SystemInterface subcooledPropaneLiquid(double temperatureK, double pressureBara) {
    SystemInterface fluid = new SystemSrkEos(temperatureK, pressureBara);
    fluid.addComponent("propane", 1.0);
    fluid.setMixingRule("classic");
    return fluid;
  }

  @Test
  void testIsochoricPressureProfileIsMonotonicAndMatchesInitialState() {
    double t0 = 293.15;
    double p0Bara = 15.0;
    SystemInterface fluid = subcooledPropaneLiquid(t0, p0Bara);

    double[] temperaturesK = { t0, t0 + 2.0, t0 + 4.0, t0 + 6.0, t0 + 8.0, t0 + 10.0 };
    double[] pressuresPa = BlockedInLiquidExpansionAnalysis.computeIsochoricPressureProfile(fluid, temperaturesK);

    assertEquals(temperaturesK.length, pressuresPa.length);
    assertEquals(p0Bara * 1.0e5, pressuresPa[0], p0Bara * 1.0e5 * 1.0e-3,
        "First profile point should match the initial blocked-in pressure");
    for (int i = 1; i < pressuresPa.length; i++) {
      assertTrue(pressuresPa[i] > pressuresPa[i - 1], "Isochoric pressure must rise monotonically with temperature");
    }
  }

  @Test
  void testIsochoricMarchMatchesSimplifiedBetaOverKappaEstimateNearReferenceState() {
    double t0 = 293.15;
    double p0Bara = 15.0;
    SystemInterface fluid = subcooledPropaneLiquid(t0, p0Bara);

    double beta = BlockedInLiquidExpansionAnalysis.estimateThermalExpansionCoefficient(fluid, 0.5);
    double kappa = BlockedInLiquidExpansionAnalysis.estimateIsothermalCompressibility(fluid, 2.0e5);
    assertTrue(beta > 0.0, "Thermal expansion coefficient of a liquid should be positive");
    assertTrue(kappa > 0.0, "Isothermal compressibility should be positive");

    double deltaTK = 5.0;
    double[] temperaturesK = { t0, t0 + deltaTK };
    double[] pressuresPa = BlockedInLiquidExpansionAnalysis.computeIsochoricPressureProfile(fluid, temperaturesK);
    double actualDeltaPPa = pressuresPa[1] - pressuresPa[0];

    double simplifiedDeltaPPa = BlockedInLiquidExpansionAnalysis.simplifiedPressureRise(beta, kappa, deltaTK);

    assertTrue(actualDeltaPPa > 0.0, "Pressure rise should be positive when warming a blocked-in liquid");
    double relativeDifference = Math.abs(actualDeltaPPa - simplifiedDeltaPPa) / actualDeltaPPa;
    assertTrue(relativeDifference < 0.3, "EOS isochoric march should be within 30% of the constant-property "
        + "beta/kappa screening estimate near the reference state, was " + relativeDifference);
  }

  @Test
  void testSimplifiedPressureRiseFormula() {
    double beta = 1.5e-3;
    double kappa = 1.0e-9;
    double deltaT = 10.0;
    double deltaP = BlockedInLiquidExpansionAnalysis.simplifiedPressureRise(beta, kappa, deltaT);
    assertEquals(beta / kappa * deltaT, deltaP, 1.0e-6);
  }

  @Test
  void testInvalidInputsThrow() {
    SystemInterface fluid = subcooledPropaneLiquid(293.15, 15.0);
    assertThrows(IllegalArgumentException.class,
        () -> BlockedInLiquidExpansionAnalysis.computeIsochoricPressureProfile(fluid, new double[0]));
    assertThrows(IllegalArgumentException.class,
        () -> BlockedInLiquidExpansionAnalysis.computeIsochoricPressureProfile(null, new double[] { 300.0 }));
    assertThrows(IllegalArgumentException.class,
        () -> BlockedInLiquidExpansionAnalysis.simplifiedPressureRise(1.0e-3, 0.0, 10.0));
  }
}
