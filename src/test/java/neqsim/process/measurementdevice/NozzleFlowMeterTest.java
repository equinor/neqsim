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
 * Unit tests for {@link neqsim.process.measurementdevice.NozzleFlowMeter}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class NozzleFlowMeterTest {
  private Stream stream;

  /** Creates a gas stream at 20 bara and 25 degC upstream of the nozzle. */
  @BeforeEach
  void setUp() {
    SystemInterface gas = new SystemSrkEos(273.15 + 25.0, 20.0);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.07);
    gas.addComponent("propane", 0.03);
    gas.setMixingRule("classic");
    stream = new Stream("nozzle feed", gas);
    stream.setFlowRate(50000.0, "kg/hr");
    stream.run();
  }

  /** ISA 1932 nozzle discharge coefficient, ISO 5167-3:2022 Formula (5), evaluated independently here. */
  @Test
  void testIsa1932DischargeCoefficient() {
    NozzleFlowMeter meter = new NozzleFlowMeter(stream);
    meter.setNozzleType(NozzleFlowMeter.NozzleType.ISA_1932);
    double beta = 0.5;
    double reynoldsD = 5.0e5;
    double expected = 0.9900 - 0.2262 * Math.pow(beta, 4.1)
        - (0.00175 * beta * beta - 0.0033 * Math.pow(beta, 4.15)) * Math.pow(1.0e6 / reynoldsD, 1.15);

    assertEquals(expected, meter.calcDischargeCoefficient(beta, reynoldsD), 1e-12);
  }

  /** Long radius nozzle discharge coefficient, ISO 5167-3:2022 Formula (10). */
  @Test
  void testLongRadiusDischargeCoefficient() {
    NozzleFlowMeter meter = new NozzleFlowMeter(stream);
    meter.setNozzleType(NozzleFlowMeter.NozzleType.LONG_RADIUS);
    double beta = 0.5;
    double reynoldsD = 5.0e5;
    double expected = 0.9965 - 0.00653 * Math.sqrt(1.0e6 * beta / reynoldsD);

    assertEquals(expected, meter.calcDischargeCoefficient(beta, reynoldsD), 1e-12);
  }

  /**
   * Throat-tapped flow nozzle discharge coefficient, ISO 5167-3:2022 Formula (13), below the 3.0e6 Re,d branch point.
   */
  @Test
  void testThroatTappedDischargeCoefficientBelowBranchPoint() {
    NozzleFlowMeter meter = new NozzleFlowMeter(stream);
    meter.setNozzleType(NozzleFlowMeter.NozzleType.THROAT_TAPPED);
    double beta = 0.45;
    double reynoldsThroat = 1.0e6;
    double reynoldsD = reynoldsThroat * beta;
    double common = 0.255 / Math.pow(reynoldsThroat, 0.2) * Math.pow(1.0 - 400000.0 / reynoldsThroat, 0.8);
    double expected = 1.0090 - common;

    assertEquals(expected, meter.calcDischargeCoefficient(beta, reynoldsD), 1e-9);
  }

  /**
   * Throat-tapped flow nozzle discharge coefficient, ISO 5167-3:2022 Formula (14), above the 3.0e6 Re,d branch point.
   */
  @Test
  void testThroatTappedDischargeCoefficientAboveBranchPoint() {
    NozzleFlowMeter meter = new NozzleFlowMeter(stream);
    meter.setNozzleType(NozzleFlowMeter.NozzleType.THROAT_TAPPED);
    double beta = 0.45;
    double reynoldsThroat = 5.0e6;
    double reynoldsD = reynoldsThroat * beta;
    double common = 0.255 / Math.pow(reynoldsThroat, 0.2) * Math.pow(1.0 - 400000.0 / reynoldsThroat, 0.8);
    double expected = 0.9823 - common + 0.0018 * Math.log(reynoldsThroat);

    assertEquals(expected, meter.calcDischargeCoefficient(beta, reynoldsD), 1e-9);
  }

  /** Venturi nozzle discharge coefficient, ISO 5167-3:2022 Formula (19), independent of the Reynolds number. */
  @Test
  void testVenturiNozzleDischargeCoefficientIsReynoldsIndependent() {
    NozzleFlowMeter meter = new NozzleFlowMeter(stream);
    meter.setNozzleType(NozzleFlowMeter.NozzleType.VENTURI_NOZZLE);
    double beta = 0.5;
    double expected = 0.9858 - 0.196 * Math.pow(beta, 4.5);

    assertEquals(expected, meter.calcDischargeCoefficient(beta, 1.0e5), 1e-12);
    assertEquals(expected, meter.calcDischargeCoefficient(beta, 1.0e7), 1e-12);
  }

  /** The mass flow must match the ISO 5167-1 general equation for the ISA 1932 nozzle. */
  @Test
  void testMassFlowMatchesIso5167GeneralEquation() {
    NozzleFlowMeter meter = new NozzleFlowMeter("FT-700", stream);
    meter.setNozzleType(NozzleFlowMeter.NozzleType.ISA_1932);
    meter.setGeometry(200.0, 100.0, "mm");
    meter.setDifferentialPressure(300.0, "mbar");
    double actualMassFlow = meter.getMassFlowRate("kg/hr"); // converges and populates the Reynolds number

    double beta = meter.getBetaRatio();
    double dp = 30000.0;
    double p1 = stream.getPressure("Pa");
    double kappa = meter.getIsentropicExponent();
    double epsilon = ExpansibilityModel.ISENTROPIC.calculate(dp, p1, beta, kappa);
    double reynoldsD = meter.getReynoldsNumberPipe();
    double c = meter.calcDischargeCoefficient(beta, reynoldsD);
    double d = 0.1;
    double density = stream.getThermoSystem().getDensity("kg/m3");
    double expectedMassFlow = c / Math.sqrt(1.0 - Math.pow(beta, 4.0)) * epsilon * Math.PI / 4.0 * d * d
        * Math.sqrt(2.0 * dp * density) * 3600.0;

    assertEquals(expectedMassFlow, actualMassFlow, 1e-6);
  }

  /** A geometry and pressure point inside every ISA 1932 nozzle limit of use must report no violations. */
  @Test
  void testValidityInRangeIsa1932() {
    NozzleFlowMeter meter = new NozzleFlowMeter(stream);
    meter.setNozzleType(NozzleFlowMeter.NozzleType.ISA_1932);
    meter.setGeometry(200.0, 100.0, "mm");
    meter.setDifferentialPressure(300.0, "mbar");
    meter.getMassFlowRate("kg/hr");

    List<String> violations = meter.getValidityViolations();
    assertTrue(violations.isEmpty(), violations.toString());
  }

  /** A diameter ratio outside the ISA 1932 nozzle's 0.3 to 0.8 range must be flagged. */
  @Test
  void testValidityFlagsBetaOutOfRangeIsa1932() {
    NozzleFlowMeter meter = new NozzleFlowMeter(stream);
    meter.setNozzleType(NozzleFlowMeter.NozzleType.ISA_1932);
    meter.setGeometry(200.0, 190.0, "mm");
    meter.setDifferentialPressure(300.0, "mbar");

    List<String> violations = meter.getValidityViolations();
    assertTrue(violations.stream().anyMatch(v -> v.contains("beta")), violations.toString());
    assertFalse(meter.isWithinValidityRange());
  }

  /** A pipe diameter below the Venturi nozzle's 65 mm lower bound must be flagged. */
  @Test
  void testValidityFlagsSmallPipeDiameterVenturiNozzle() {
    NozzleFlowMeter meter = new NozzleFlowMeter(stream);
    meter.setNozzleType(NozzleFlowMeter.NozzleType.VENTURI_NOZZLE);
    meter.setGeometry(60.0, 30.0, "mm");
    meter.setDifferentialPressure(300.0, "mbar");

    List<String> violations = meter.getValidityViolations();
    assertTrue(violations.stream().anyMatch(v -> v.contains("D =")), violations.toString());
  }

  /** A zero differential pressure means no measurable flow. */
  @Test
  void testZeroDifferentialPressureGivesZeroFlow() {
    NozzleFlowMeter meter = new NozzleFlowMeter(stream);
    meter.setGeometry(200.0, 100.0, "mm");
    meter.setDifferentialPressure(0.0, "mbar");

    assertEquals(0.0, meter.getMassFlowRate("kg/hr"), 0.0);
  }
}
