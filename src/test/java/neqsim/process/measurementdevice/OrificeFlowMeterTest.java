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
 * Unit tests for {@link neqsim.process.measurementdevice.OrificeFlowMeter}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class OrificeFlowMeterTest {
  private Stream stream;

  /** Creates a gas stream at 20 bara and 25 degC upstream of the orifice. */
  @BeforeEach
  void setUp() {
    SystemInterface gas = new SystemSrkEos(273.15 + 25.0, 20.0);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.07);
    gas.addComponent("propane", 0.03);
    gas.setMixingRule("classic");
    stream = new Stream("orifice feed", gas);
    stream.setFlowRate(50000.0, "kg/hr");
    stream.run();
  }

  /**
   * Reader-Harris/Gallagher (1998) discharge coefficient, corner tappings, evaluated independently here from the ISO
   * 5167-2:2022 Formula (4) terms and compared with the production implementation at a fixed Reynolds number.
   */
  @Test
  void testDischargeCoefficientMatchesReaderHarrisGallagherCornerTappings() {
    OrificeFlowMeter meter = new OrificeFlowMeter("FT-100", stream);
    meter.setGeometry(200.0, 100.0, "mm");
    meter.setTappingArrangement(OrificeFlowMeter.TappingArrangement.CORNER);
    meter.setDifferentialPressure(300.0, "mbar");

    double beta = 0.5;
    double reynoldsD = 5.0e5;
    double l1 = 0.0;
    double l2Prime = 0.0;
    double a = Math.pow(19000.0 * beta / reynoldsD, 0.8);
    double m2Prime = 2.0 * l2Prime / (1.0 - beta);
    double expected = 0.5961 + 0.0261 * beta * beta - 0.216 * Math.pow(beta, 8.0)
        + 0.000521 * Math.pow(1.0e6 * beta / reynoldsD, 0.7)
        + (0.0188 + 0.0063 * a) * Math.pow(beta, 3.5) * Math.pow(1.0e6 / reynoldsD, 0.3)
        + (0.043 + 0.080 * Math.exp(-10.0 * l1) - 0.123 * Math.exp(-7.0 * l1)) * (1.0 - 0.11 * a) * Math.pow(beta, 4.0)
            / (1.0 - Math.pow(beta, 4.0))
        - 0.031 * (m2Prime - 0.8 * Math.pow(m2Prime, 1.1)) * Math.pow(beta, 1.3);
    // D = 200 mm >= 71.12 mm, so no small-pipe correction term applies here.

    assertEquals(0.5, meter.getBetaRatio(), 1e-12);
    assertEquals(expected, meter.calcDischargeCoefficient(beta, reynoldsD), 1e-9);
  }

  /** Corner, D-and-D/2 and flange tappings must give different discharge coefficients at the same operating point. */
  @Test
  void testTappingArrangementsDiffer() {
    OrificeFlowMeter corner = new OrificeFlowMeter(stream);
    corner.setGeometry(200.0, 100.0, "mm");
    corner.setTappingArrangement(OrificeFlowMeter.TappingArrangement.CORNER);
    corner.setDifferentialPressure(300.0, "mbar");

    OrificeFlowMeter dAndDHalf = new OrificeFlowMeter(stream);
    dAndDHalf.setGeometry(200.0, 100.0, "mm");
    dAndDHalf.setTappingArrangement(OrificeFlowMeter.TappingArrangement.D_AND_D_HALF);
    dAndDHalf.setDifferentialPressure(300.0, "mbar");

    OrificeFlowMeter flange = new OrificeFlowMeter(stream);
    flange.setGeometry(200.0, 100.0, "mm");
    flange.setTappingArrangement(OrificeFlowMeter.TappingArrangement.FLANGE);
    flange.setDifferentialPressure(300.0, "mbar");

    double massCorner = corner.getMassFlowRate("kg/hr");
    double massDAndDHalf = dAndDHalf.getMassFlowRate("kg/hr");
    double massFlange = flange.getMassFlowRate("kg/hr");

    assertTrue(Math.abs(massCorner - massDAndDHalf) > 1.0e-6);
    assertTrue(Math.abs(massCorner - massFlange) > 1.0e-6);
    assertTrue(Math.abs(massDAndDHalf - massFlange) > 1.0e-6);
  }

  /** The mass flow must match the ISO 5167-1 general equation evaluated with the converged discharge coefficient. */
  @Test
  void testMassFlowMatchesIso5167GeneralEquation() {
    OrificeFlowMeter meter = new OrificeFlowMeter("FT-100", stream);
    meter.setGeometry(200.0, 100.0, "mm");
    meter.setTappingArrangement(OrificeFlowMeter.TappingArrangement.FLANGE);
    meter.setDifferentialPressure(300.0, "mbar");
    double actualMassFlow = meter.getMassFlowRate("kg/hr"); // converges and populates the Reynolds number

    double beta = meter.getBetaRatio();
    double dp = 30000.0;
    double p1 = stream.getPressure("Pa");
    double kappa = meter.getIsentropicExponent();
    double epsilon = ExpansibilityModel.ORIFICE.calculate(dp, p1, beta, kappa);
    double reynoldsD = meter.getReynoldsNumberPipe();
    double c = meter.calcDischargeCoefficient(beta, reynoldsD);
    double d = 0.1;
    double density = stream.getThermoSystem().getDensity("kg/m3");
    double expectedMassFlow = c / Math.sqrt(1.0 - Math.pow(beta, 4.0)) * epsilon * Math.PI / 4.0 * d * d
        * Math.sqrt(2.0 * dp * density) * 3600.0;

    assertEquals(expectedMassFlow, actualMassFlow, 1e-6);
  }

  /** The orifice expansibility factor must tend to 1 as the differential pressure tends to zero. */
  @Test
  void testExpansibilityTendsToUnityAtLowDp() {
    OrificeFlowMeter meter = new OrificeFlowMeter(stream);
    meter.setGeometry(200.0, 100.0, "mm");
    meter.setDifferentialPressure(0.01, "mbar");

    assertEquals(1.0, meter.getExpansibilityFactor(), 1e-3);
  }

  /** A geometry and pressure point inside every ISO 5167-2 limit of use must report no violations. */
  @Test
  void testValidityInRange() {
    OrificeFlowMeter meter = new OrificeFlowMeter(stream);
    meter.setGeometry(200.0, 100.0, "mm");
    meter.setTappingArrangement(OrificeFlowMeter.TappingArrangement.FLANGE);
    meter.setDifferentialPressure(300.0, "mbar");
    meter.getMassFlowRate("kg/hr"); // solve once so the Reynolds number is available

    List<String> violations = meter.getValidityViolations();
    assertTrue(violations.isEmpty(), violations.toString());
    assertTrue(meter.isWithinValidityRange());
  }

  /** A diameter ratio outside 0.1 to 0.75 must be flagged. */
  @Test
  void testValidityFlagsBetaOutOfRange() {
    OrificeFlowMeter meter = new OrificeFlowMeter(stream);
    meter.setGeometry(200.0, 190.0, "mm");
    meter.setDifferentialPressure(300.0, "mbar");

    List<String> violations = meter.getValidityViolations();
    assertTrue(violations.stream().anyMatch(v -> v.contains("beta")), violations.toString());
    assertFalse(meter.isWithinValidityRange());
  }

  /** A pipe diameter below 50 mm must be flagged. */
  @Test
  void testValidityFlagsSmallPipeDiameter() {
    OrificeFlowMeter meter = new OrificeFlowMeter(stream);
    meter.setGeometry(30.0, 15.0, "mm");
    meter.setDifferentialPressure(300.0, "mbar");

    List<String> violations = meter.getValidityViolations();
    assertTrue(violations.stream().anyMatch(v -> v.contains("D =")), violations.toString());
  }

  /** A zero differential pressure means no measurable flow. */
  @Test
  void testZeroDifferentialPressureGivesZeroFlow() {
    OrificeFlowMeter meter = new OrificeFlowMeter(stream);
    meter.setGeometry(200.0, 100.0, "mm");
    meter.setDifferentialPressure(0.0, "mbar");

    assertEquals(0.0, meter.getMassFlowRate("kg/hr"), 0.0);
  }

  /**
   * Builds a fixed reference meter for the ISO/TR 11583 Clause 7 wet-gas tests: D = 200 mm, d = 100 mm (beta = 0.5), dP
   * = 0.5 bar, kappa = 1.3, rho,gas = 50 kg/m3. Upstream pressure comes from the shared {@code stream} fixture (20
   * bara, set up in {@link #setUp()}). Gas density and isentropic exponent are imposed so the case does not depend on
   * the fluid package.
   *
   * @param liquidDensity liquid density in kg/m3
   * @return the configured meter
   */
  private OrificeFlowMeter buildWetGasMeter(double liquidDensity) {
    OrificeFlowMeter meter = new OrificeFlowMeter("FT-100-wet", stream);
    meter.setGeometry(200.0, 100.0, "mm");
    meter.setDifferentialPressure(0.5, "bar");
    meter.setGasDensity(50.0, "kg/m3");
    meter.setIsentropicExponent(1.3);
    meter.setGravitationalAcceleration(9.81);
    meter.setLiquidDensity(liquidDensity, "kg/m3");
    meter.setWetGasCorrelation(OrificeFlowMeter.WetGasCorrelation.ISO_TR_11583);
    return meter;
  }

  /** With no liquid information the wet-gas mode must fall back to the dry-gas result. */
  @Test
  void testWetGasWithoutLiquidFallsBackToDryGas() {
    OrificeFlowMeter dry = new OrificeFlowMeter(stream);
    dry.setGeometry(200.0, 100.0, "mm");
    dry.setDifferentialPressure(300.0, "mbar");

    OrificeFlowMeter wet = new OrificeFlowMeter(stream);
    wet.setGeometry(200.0, 100.0, "mm");
    wet.setDifferentialPressure(300.0, "mbar");
    wet.setWetGasCorrelation(OrificeFlowMeter.WetGasCorrelation.ISO_TR_11583);

    assertEquals(dry.getMassFlowRate("kg/hr"), wet.getMassFlowRate("kg/hr"), 1.0e-6);
    assertEquals(1.0, wet.getOverReadingFactor(), 1.0e-12);
  }

  /**
   * The ISO/TR 11583 Clause 7 exponent n must match the two-branch formula evaluated independently here from the
   * meter's own solved gas densiometric Froude number, above the Fr,gas = 1.5 boundary.
   */
  @Test
  void testChisholmExponentMatchesFormulaAboveFroudeBoundary() {
    OrificeFlowMeter meter = buildWetGasMeter(800.0);
    meter.setDifferentialPressure(5.0, "bar"); // larger dp => larger gas rate => Fr,gas above 1.5
    meter.setLiquidToGasMassRatio(0.5);

    double froude = meter.getGasDensiometricFroudeNumber();
    assertTrue(froude > 1.5, "expected Fr,gas > 1.5, was " + froude);
    double term = 1.0 / Math.sqrt(2.0) - 0.3 / Math.sqrt(froude);
    double expectedN = term * term;
    assertEquals(expectedN, meter.getChisholmExponent(), 1.0e-9);
  }

  /**
   * Below Fr,gas = 1.5 the exponent is the 0.214 plateau; below the correlation's 0.2 lower bound this class still
   * extrapolates the plateau rather than returning NaN, but flags it via
   * {@link OrificeFlowMeter#getValidityViolations()}.
   */
  @Test
  void testChisholmExponentExtrapolatesBelowFroudeLowerBound() {
    OrificeFlowMeter meter = buildWetGasMeter(800.0);
    meter.setDifferentialPressure(0.001, "bar"); // tiny dp => tiny gas rate => small Fr,gas
    meter.setLiquidToGasMassRatio(0.5);

    double froude = meter.getGasDensiometricFroudeNumber();
    assertTrue(froude < OrificeFlowMeter.MIN_FROUDE_NUMBER, "expected Fr,gas below 0.2, was " + froude);
    assertEquals(0.214, meter.getChisholmExponent(), 1.0e-12);

    List<String> violations = meter.getValidityViolations();
    assertTrue(violations.stream().anyMatch(v -> v.contains("Fr,gas")), violations.toString());
  }

  /** The over-reading factor must match the Chisholm-form equation evaluated from its own reported X and CCh. */
  @Test
  void testOverReadingFactorFormula() {
    OrificeFlowMeter meter = buildWetGasMeter(800.0);
    meter.setLiquidToGasMassRatio(0.5);

    double x = meter.getLockhartMartinelliParameter();
    double chisholm = meter.getChisholmCoefficient();
    double expectedPhi = Math.sqrt(1.0 + chisholm * x + x * x);
    assertEquals(expectedPhi, meter.getOverReadingFactor(), 1.0e-9);
    assertTrue(meter.getOverReadingFactor() > 1.0);
  }

  /**
   * ISO/TR 11583 Clause 7 never replaces the discharge coefficient: the wet-gas gas mass flow rate must equal the plain
   * Reader-Harris/Gallagher dry-gas equation evaluated at the converged Reynolds number, divided by Phi.
   */
  @Test
  void testDischargeCoefficientNeverReplaced() {
    OrificeFlowMeter meter = buildWetGasMeter(800.0);
    meter.setLiquidToGasMassRatio(0.5);

    double gasFlow = meter.getMassFlowRate("kg/sec");
    double phi = meter.getOverReadingFactor();
    double beta = meter.getBetaRatio();
    double reynoldsD = meter.getReynoldsNumberPipe();
    double c = meter.calcDischargeCoefficient(beta, reynoldsD);

    double dp = 0.5e5;
    double p1 = stream.getPressure("Pa");
    double epsilon = ExpansibilityModel.ORIFICE.calculate(dp, p1, beta, 1.3);
    double d = 0.1;
    double expectedDryFlow = c / Math.sqrt(1.0 - Math.pow(beta, 4.0)) * epsilon * Math.PI / 4.0 * d * d
        * Math.sqrt(2.0 * dp * 50.0);

    assertEquals(expectedDryFlow / phi, gasFlow, 1.0e-6);
  }

  /** A liquid load derived from the stream's own phase split must over-read (Phi &gt; 1). */
  @Test
  void testLiquidFromStreamOverReads() {
    SystemInterface wetGas = new SystemSrkEos(273.15 + 25.0, 60.0);
    wetGas.addComponent("methane", 0.90);
    wetGas.addComponent("ethane", 0.05);
    wetGas.addComponent("n-heptane", 0.05);
    wetGas.setMixingRule("classic");
    Stream wetStream = new Stream("wet orifice feed", wetGas);
    wetStream.setFlowRate(50000.0, "kg/hr");
    wetStream.run();

    OrificeFlowMeter meter = new OrificeFlowMeter("FT-100-stream", wetStream);
    meter.setGeometry(200.0, 100.0, "mm");
    meter.setDifferentialPressure(300.0, "mbar");
    meter.setWetGasCorrelation(OrificeFlowMeter.WetGasCorrelation.ISO_TR_11583);
    meter.setLiquidFromStream(true);

    assertTrue(meter.getLockhartMartinelliParameter() > 0.0);
    assertTrue(meter.getOverReadingFactor() > 1.0);
  }

  /** A geometry and liquid load inside every ISO/TR 11583 Clause 7 limit of use must report no violations. */
  @Test
  void testWetGasValidityInRange() {
    OrificeFlowMeter meter = buildWetGasMeter(800.0);
    meter.setLiquidToGasMassRatio(0.5);

    List<String> violations = meter.getValidityViolations();
    assertTrue(violations.isEmpty(), violations.toString());
  }

  /** A diameter ratio outside the wet-gas 0.24 to 0.73 range must be flagged. */
  @Test
  void testWetGasValidityFlagsBetaOutOfRange() {
    OrificeFlowMeter meter = new OrificeFlowMeter(stream);
    meter.setGeometry(200.0, 190.0, "mm"); // beta = 0.95, outside 0.24 to 0.73
    meter.setDifferentialPressure(300.0, "mbar");
    meter.setWetGasCorrelation(OrificeFlowMeter.WetGasCorrelation.ISO_TR_11583);
    meter.setLiquidToGasMassRatio(0.1);

    List<String> violations = meter.getValidityViolations();
    assertTrue(violations.stream().anyMatch(v -> v.contains("beta")), violations.toString());
    assertFalse(meter.isWithinValidityRange());
  }

  /**
   * ISO/TR 11583 7.5.5: with no liquid ratio supplied but a measured pressure loss and a beta inside 0.5 to 0.68, X is
   * derived from the pressure-loss ratio and the meter still over-reads.
   */
  @Test
  void testPressureLossRouteDerivesLockhartMartinelli() {
    OrificeFlowMeter meter = buildWetGasMeter(800.0);
    // beta = 0.5 is inside the 7.5.5 window [0.5, 0.68] (reuse the D=200/d=100 mm geometry).
    meter.setPressureLoss(0.45, "bar"); // most of the 0.5 bar differential pressure recovers as permanent loss

    double x = meter.getLockhartMartinelliParameter();
    assertTrue(x > 0.0, "expected a positive X from the pressure-loss route, was " + x);
    assertTrue(meter.getOverReadingFactor() > 1.0);
  }
}
