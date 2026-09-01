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
 * Unit tests for {@link neqsim.process.measurementdevice.ConeFlowMeter}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class ConeFlowMeterTest {
  private Stream stream;

  /** Creates a gas stream at 20 bara and 25 degC upstream of the cone meter. */
  @BeforeEach
  void setUp() {
    SystemInterface gas = new SystemSrkEos(273.15 + 25.0, 20.0);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.07);
    gas.addComponent("propane", 0.03);
    gas.setMixingRule("classic");
    stream = new Stream("cone feed", gas);
    stream.setFlowRate(50000.0, "kg/hr");
    stream.run();
  }

  /** beta = sqrt(1 - dc^2/D^2), the ISO 5167-5 Formula (2) mapping, at the standard's own worked example h/D value. */
  @Test
  void testBetaFromConeDiameter() {
    ConeFlowMeter meter = new ConeFlowMeter(stream);
    meter.setGeometry(200.0, 120.0, "mm");

    double expectedBeta = Math.sqrt(1.0 - (120.0 * 120.0) / (200.0 * 200.0));
    assertEquals(expectedBeta, meter.getBetaRatio(), 1e-10);
    assertEquals(120.0, meter.getConeDiameter("mm"), 1e-9);
  }

  /** A cone diameter at or above the pipe diameter is not physical: beta would be zero or imaginary. */
  @Test
  void testInvalidGeometryYieldsNaN() {
    ConeFlowMeter meter = new ConeFlowMeter(stream);
    meter.setGeometry(200.0, 200.0, "mm");
    assertTrue(Double.isNaN(meter.getThroatDiameter("mm")));

    ConeFlowMeter oversizedCone = new ConeFlowMeter(stream);
    oversizedCone.setGeometry(200.0, 250.0, "mm");
    assertTrue(Double.isNaN(oversizedCone.getThroatDiameter("mm")));
  }

  /**
   * ISO 5167-5 Annex A tabulates epsilon = 0.9431 for beta = 0.6000, kappa = 1.3 and p2/p1 = 0.9 (dP/p1 = 0.1);
   * verified against the ISO 5167-5:2022 Annex A table image.
   */
  @Test
  void testExpansibilityMatchesIso5167_5AnnexA() {
    ConeFlowMeter meter = new ConeFlowMeter(stream);
    // beta = 0.6 => dc = D * sqrt(1 - beta^2)
    double beta = 0.6;
    double coneDiameterMm = 200.0 * Math.sqrt(1.0 - beta * beta);
    meter.setGeometry(200.0, coneDiameterMm, "mm");
    meter.setIsentropicExponent(1.3);
    meter.setDifferentialPressure(0.1 * 20.0, "bar"); // dP/p1 = 0.1 at p1 = 20 bara

    assertEquals(beta, meter.getBetaRatio(), 1e-9);
    assertEquals(0.9431, meter.getExpansibilityFactor(), 1e-4);
  }

  /**
   * The discharge coefficient is the constant 0.82 of an uncalibrated cone meter, independent of the Reynolds number.
   */
  @Test
  void testDischargeCoefficientIsConstant() {
    ConeFlowMeter meter = new ConeFlowMeter(stream);
    meter.setGeometry(200.0, 120.0, "mm");

    assertEquals(0.82, meter.calcDischargeCoefficient(meter.getBetaRatio(), 1.0e5), 1e-12);
    assertEquals(0.82, meter.calcDischargeCoefficient(meter.getBetaRatio(), 1.0e7), 1e-12);
  }

  /** The mass flow must match the ISO 5167-1 general equation using the equivalent throat diameter D * beta. */
  @Test
  void testMassFlowMatchesIso5167GeneralEquation() {
    ConeFlowMeter meter = new ConeFlowMeter("FT-500", stream);
    meter.setGeometry(200.0, 120.0, "mm");
    meter.setDifferentialPressure(300.0, "mbar");

    double beta = meter.getBetaRatio();
    double dp = 30000.0;
    double p1 = stream.getPressure("Pa");
    double kappa = meter.getIsentropicExponent();
    double epsilon = ExpansibilityModel.CONE.calculate(dp, p1, beta, kappa);
    double throatDiameterMeters = 0.2 * beta;
    double density = stream.getThermoSystem().getDensity("kg/m3");
    double expectedMassFlow = 0.82 / Math.sqrt(1.0 - Math.pow(beta, 4.0)) * epsilon * Math.PI / 4.0
        * throatDiameterMeters * throatDiameterMeters * Math.sqrt(2.0 * dp * density) * 3600.0;

    assertEquals(expectedMassFlow, meter.getMassFlowRate("kg/hr"), 1e-6);
  }

  /** A geometry and pressure point inside every ISO 5167-5 limit of use must report no violations. */
  @Test
  void testValidityInRange() {
    ConeFlowMeter meter = new ConeFlowMeter(stream);
    meter.setGeometry(200.0, 160.0, "mm"); // beta = sqrt(1 - 0.8^2) = 0.6, inside 0.45 to 0.75
    meter.setDifferentialPressure(300.0, "mbar");
    meter.getMassFlowRate("kg/hr");

    List<String> violations = meter.getValidityViolations();
    assertTrue(violations.isEmpty(), violations.toString());
  }

  /** A diameter ratio outside 0.45 to 0.75 must be flagged. */
  @Test
  void testValidityFlagsBetaOutOfRange() {
    ConeFlowMeter meter = new ConeFlowMeter(stream);
    meter.setGeometry(200.0, 190.0, "mm"); // small cone => beta close to 1
    meter.setDifferentialPressure(300.0, "mbar");

    List<String> violations = meter.getValidityViolations();
    assertTrue(violations.stream().anyMatch(v -> v.contains("beta")), violations.toString());
    assertFalse(meter.isWithinValidityRange());
  }

  /** A zero differential pressure means no measurable flow. */
  @Test
  void testZeroDifferentialPressureGivesZeroFlow() {
    ConeFlowMeter meter = new ConeFlowMeter(stream);
    meter.setGeometry(200.0, 120.0, "mm");
    meter.setDifferentialPressure(0.0, "mbar");

    assertEquals(0.0, meter.getMassFlowRate("kg/hr"), 0.0);
  }
}
