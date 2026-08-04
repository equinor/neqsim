package neqsim.process.measurementdevice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for {@link neqsim.process.measurementdevice.WedgeFlowMeter}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class WedgeFlowMeterTest {
  private Stream stream;

  /** Creates a gas stream at 20 bara and 25 degC upstream of the wedge meter. */
  @BeforeEach
  void setUp() {
    SystemInterface gas = new SystemSrkEos(273.15 + 25.0, 20.0);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.07);
    gas.addComponent("propane", 0.03);
    gas.setMixingRule("classic");
    stream = new Stream("wedge feed", gas);
    stream.setFlowRate(50000.0, "kg/hr");
    stream.run();
  }

  /**
   * ISO 5167-6 Formula (3) states, as a worked example, that h/D = 0.5 corresponds to beta = sqrt(0.5) = 0.70711 (not
   * beta = 0.5).
   */
  @Test
  void testBetaFromWedgeRatioHalf() {
    WedgeFlowMeter meter = new WedgeFlowMeter(stream);
    meter.setPipeDiameter(200.0, "mm");
    meter.setWedgeRatio(0.5);

    assertEquals(Math.sqrt(0.5), meter.getBetaRatio(), 1e-4);
    assertEquals(0.5, meter.getWedgeRatio(), 1e-12);
  }

  /**
   * ISO 5167-6 Formula (3) also states that beta = 0.5 corresponds to h/D = 0.298; verified by sweeping the wedge ratio
   * to locate the beta = 0.5 crossing.
   */
  @Test
  void testWedgeRatioForBetaHalf() {
    WedgeFlowMeter meter = new WedgeFlowMeter(stream);
    meter.setPipeDiameter(200.0, "mm");

    double lo = 0.001;
    double hi = 0.999;
    for (int i = 0; i < 60; i++) {
      double mid = 0.5 * (lo + hi);
      meter.setWedgeRatio(mid);
      if (meter.getBetaRatio() < 0.5) {
        lo = mid;
      } else {
        hi = mid;
      }
    }
    assertEquals(0.298, 0.5 * (lo + hi), 1.0e-3);
  }

  /** The discharge coefficient is C = 0.77 - 0.09 beta, independent of the Reynolds number. */
  @Test
  void testDischargeCoefficientFormula() {
    WedgeFlowMeter meter = new WedgeFlowMeter(stream);
    meter.setPipeDiameter(200.0, "mm");
    meter.setWedgeRatio(0.5);
    double beta = meter.getBetaRatio();

    assertEquals(0.77 - 0.09 * beta, meter.calcDischargeCoefficient(beta, 1.0e5), 1e-12);
    assertEquals(0.77 - 0.09 * beta, meter.calcDischargeCoefficient(beta, 1.0e6), 1e-12);
  }

  /** The mass flow must match the ISO 5167-1 general equation using the equivalent throat diameter D * beta. */
  @Test
  void testMassFlowMatchesIso5167GeneralEquation() {
    WedgeFlowMeter meter = new WedgeFlowMeter("FT-600", stream);
    meter.setGeometry(200.0, 80.0, "mm");
    meter.setDifferentialPressure(300.0, "mbar");

    double beta = meter.getBetaRatio();
    double dp = 30000.0;
    double p1 = stream.getPressure("Pa");
    double kappa = meter.getIsentropicExponent();
    double epsilon = ExpansibilityModel.ISENTROPIC.calculate(dp, p1, beta, kappa);
    double c = 0.77 - 0.09 * beta;
    double throatDiameterMeters = 0.2 * beta;
    double density = stream.getThermoSystem().getDensity("kg/m3");
    double expectedMassFlow = c / Math.sqrt(1.0 - Math.pow(beta, 4.0)) * epsilon * Math.PI / 4.0 * throatDiameterMeters
        * throatDiameterMeters * Math.sqrt(2.0 * dp * density) * 3600.0;

    assertEquals(expectedMassFlow, meter.getMassFlowRate("kg/hr"), 1e-6);
  }

  /** A geometry and pressure point inside every ISO 5167-6 limit of use must report no violations. */
  @Test
  void testValidityInRange() {
    WedgeFlowMeter meter = new WedgeFlowMeter(stream);
    meter.setGeometry(200.0, 80.0, "mm");
    meter.setDifferentialPressure(300.0, "mbar");
    meter.getMassFlowRate("kg/hr");

    List<String> violations = meter.getValidityViolations();
    assertTrue(violations.isEmpty(), violations.toString());
  }

  /** A diameter ratio outside 0.377 to 0.791 must be flagged. */
  @Test
  void testValidityFlagsBetaOutOfRange() {
    WedgeFlowMeter meter = new WedgeFlowMeter(stream);
    meter.setGeometry(200.0, 5.0, "mm"); // tiny wedge => beta well below 0.377
    meter.setDifferentialPressure(300.0, "mbar");

    List<String> violations = meter.getValidityViolations();
    assertTrue(violations.stream().anyMatch(v -> v.contains("beta")), violations.toString());
    assertFalse(meter.isWithinValidityRange());
  }

  /** A zero differential pressure means no measurable flow. */
  @Test
  void testZeroDifferentialPressureGivesZeroFlow() {
    WedgeFlowMeter meter = new WedgeFlowMeter(stream);
    meter.setGeometry(200.0, 80.0, "mm");
    meter.setDifferentialPressure(0.0, "mbar");

    assertEquals(0.0, meter.getMassFlowRate("kg/hr"), 0.0);
  }
}
