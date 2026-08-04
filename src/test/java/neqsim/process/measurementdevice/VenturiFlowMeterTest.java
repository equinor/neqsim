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
 * Unit tests for {@link neqsim.process.measurementdevice.VenturiFlowMeter}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class VenturiFlowMeterTest {
  private Stream stream;

  /** Creates a gas stream at 20 bara and 25 degC upstream of the Venturi. */
  @BeforeEach
  void setUp() {
    SystemInterface gas = new SystemSrkEos(273.15 + 25.0, 20.0);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.07);
    gas.addComponent("propane", 0.03);
    gas.setMixingRule("classic");
    stream = new Stream("venturi feed", gas);
    stream.setFlowRate(50000.0, "kg/hr");
    stream.run();
  }

  /**
   * The mass flow must match the ISO 5167-1 general equation evaluated with the ISO 5167-4 Venturi expansibility.
   */
  @Test
  void testMassFlowMatchesIso5167Equation() {
    VenturiFlowMeter meter = new VenturiFlowMeter("FT-001", stream);
    meter.setGeometry(205.1, 138.1, "mm");
    meter.setDischargeCoefficient(0.985);
    meter.setDifferentialPressure(300.0, "mbar");

    double beta = 138.1 / 205.1;
    double dp = 30000.0;
    double p1 = stream.getPressure("Pa");
    double kappa = meter.getIsentropicExponent();
    double tau = (p1 - dp) / p1;
    double beta4 = Math.pow(beta, 4.0);
    double tauPow = Math.pow(tau, 2.0 / kappa);
    double expected = Math.sqrt(kappa * tauPow / (kappa - 1.0) * (1.0 - beta4) / (1.0 - beta4 * tauPow)
        * (1.0 - Math.pow(tau, (kappa - 1.0) / kappa)) / (1.0 - tau));

    assertEquals(beta, meter.getBetaRatio(), 1e-12);
    assertEquals(expected, meter.getExpansibilityFactor(), 1e-10);

    double d = 0.1381;
    double density = stream.getThermoSystem().getDensity("kg/m3");
    double expectedMassFlow = 0.985 / Math.sqrt(1.0 - beta4) * expected * Math.PI / 4.0 * d * d
        * Math.sqrt(2.0 * dp * density) * 3600.0;
    assertEquals(expectedMassFlow, meter.getMassFlowRate("kg/hr"), 1e-8);
    assertEquals(expectedMassFlow, meter.getMeasuredValue("kg/hr"), 1e-8);
  }

  /** Volume flows must be consistent with the mass flow and the flowing / standard densities. */
  @Test
  void testVolumeFlowsAreConsistentWithMassFlow() {
    VenturiFlowMeter meter = new VenturiFlowMeter(stream);
    meter.setGeometry(0.2051, 0.1381, "m");
    meter.setDifferentialPressure(0.3, "bar");

    double massFlow = meter.getMassFlowRate("kg/hr");
    double density = stream.getThermoSystem().getDensity("kg/m3");
    double standardDensity = stream.getThermoSystem().getDensity("kg/Sm3");

    assertEquals(massFlow / density, meter.getVolumeFlowRate("m3/hr"), 1e-8);
    assertEquals(massFlow / standardDensity, meter.getStandardVolumeFlowRate("Sm3/hr"), 1e-6);
    assertEquals(meter.getStandardVolumeFlowRate("Sm3/hr") * 24.0 / 1.0e6, meter.getStandardVolumeFlowRate("MSm3/day"),
        1e-10);
    assertEquals(meter.getVolumeFlowRate("m3/hr"), meter.getMeasuredValue("m3/hr"), 1e-8);
    assertEquals(meter.getStandardVolumeFlowRate("Sm3/hr"), meter.getMeasuredValue("Sm3/hr"), 1e-6);
  }

  /** Geometry and differential pressure setters must round-trip through the supported units. */
  @Test
  void testUnitConversions() {
    VenturiFlowMeter meter = new VenturiFlowMeter(stream);
    meter.setGeometry(205.1, 138.1, "mm");
    meter.setDifferentialPressure(300.0, "mbar");

    assertEquals(0.2051, meter.getPipeDiameter("m"), 1e-12);
    assertEquals(205.1, meter.getPipeDiameter("mm"), 1e-9);
    assertEquals(0.1381, meter.getThroatDiameter("m"), 1e-12);
    assertEquals(30000.0, meter.getDifferentialPressure("Pa"), 1e-9);
    assertEquals(0.3, meter.getDifferentialPressure("bar"), 1e-12);
  }

  /** A non-positive differential pressure means no measurable flow. */
  @Test
  void testZeroDifferentialPressureGivesZeroFlow() {
    VenturiFlowMeter meter = new VenturiFlowMeter(stream);
    meter.setGeometry(205.1, 138.1, "mm");
    meter.setDifferentialPressure(0.0, "mbar");

    assertEquals(0.0, meter.getMassFlowRate("kg/hr"), 0.0);
    assertEquals(0.0, meter.getVolumeFlowRate("m3/hr"), 0.0);
    assertEquals(0.0, meter.getStandardVolumeFlowRate("Sm3/hr"), 0.0);
  }

  /** A linked differential-pressure transmitter must override the explicitly set value. */
  @Test
  void testDifferentialPressureTransmitterTakesPrecedence() {
    SystemInterface downstreamFluid = stream.getThermoSystem().clone();
    downstreamFluid.setPressure(19.7, "bara");
    Stream downstream = new Stream("venturi throat", downstreamFluid);
    downstream.run();

    VenturiFlowMeter meter = new VenturiFlowMeter(stream);
    meter.setGeometry(205.1, 138.1, "mm");
    meter.setDifferentialPressure(1000.0, "mbar");
    meter.setDifferentialPressureTransmitter(new DifferentialPressureTransmitter("PDT-001", stream, downstream));

    assertEquals(0.3, meter.getDifferentialPressure("bar"), 1e-9);

    VenturiFlowMeter reference = new VenturiFlowMeter(stream);
    reference.setGeometry(205.1, 138.1, "mm");
    reference.setDifferentialPressure(300.0, "mbar");
    assertEquals(reference.getMassFlowRate("kg/hr"), meter.getMassFlowRate("kg/hr"), 1e-6);
  }

  /** The ISO 5167-4 expansibility is only defined for a pressure-drop ratio up to 0.25. */
  @Test
  void testIso5167ValidityRange() {
    VenturiFlowMeter meter = new VenturiFlowMeter(stream);
    meter.setGeometry(205.1, 138.1, "mm");

    meter.setDifferentialPressure(300.0, "mbar");
    assertTrue(meter.isWithinIso5167ValidityRange());

    meter.setDifferentialPressure(8.0, "bar");
    assertFalse(meter.isWithinIso5167ValidityRange());
  }

  /**
   * Builds the ISO/TR 11583 Annex A reference meter: d = 60 mm, D = 100 mm, dP = 0.5 bar, p1 = 60 bar, kappa = 1.3,
   * rho1,gas = 50 kg/m3. Gas density and isentropic exponent are imposed so the case is exactly the standard's.
   *
   * @param liquidDensity liquid density in kg/m3 (800 for Example 1, 1000 for Example 2)
   * @return the configured meter
   */
  private VenturiFlowMeter buildAnnexAMeter(double liquidDensity) {
    SystemInterface gas = new SystemSrkEos(273.15 + 20.0, 60.0);
    gas.addComponent("methane", 1.0);
    gas.setMixingRule("classic");
    Stream annexStream = new Stream("annex A", gas);
    annexStream.setFlowRate(10000.0, "kg/hr");
    annexStream.run();

    VenturiFlowMeter meter = new VenturiFlowMeter("annex A meter", annexStream);
    meter.setGeometry(100.0, 60.0, "mm");
    meter.setDifferentialPressure(0.5, "bar");
    meter.setGasDensity(50.0, "kg/m3");
    meter.setIsentropicExponent(1.3);
    meter.setGravitationalAcceleration(9.81);
    meter.setLiquidDensity(liquidDensity, "kg/m3");
    meter.setWetGasCorrelation(VenturiFlowMeter.WetGasCorrelation.ISO_TR_11583);
    return meter;
  }

  /**
   * ISO 5167-4 expansibility must reproduce the value quoted in ISO/TR 11583 Annex A (0.994236) for kappa = 1.3, beta =
   * 0.6, dP = 0.5 bar and p1 = 60 bar.
   */
  @Test
  void testExpansibilityMatchesIsoTr11583AnnexA() {
    VenturiFlowMeter meter = buildAnnexAMeter(800.0);
    assertEquals(0.994236, meter.getExpansibilityFactor(), 2.0e-5);
  }

  /**
   * ISO/TR 11583 Annex A Example 1: hydrocarbon liquid, liquid-to-gas mass ratio 0.5 known from a separator test. The
   * standard reports the converged values after six iterations.
   */
  @Test
  void testIsoTr11583AnnexAExample1() {
    VenturiFlowMeter meter = buildAnnexAMeter(800.0);
    meter.setSurfaceTensionFactor(VenturiFlowMeter.H_HYDROCARBON);
    meter.setLiquidToGasMassRatio(0.5);

    assertEquals(0.125, meter.getLockhartMartinelliParameter(), 1.0e-12);
    assertEquals(3.53111, meter.getGasDensiometricFroudeNumber(), 1.0e-5);
    assertEquals(12.6629, meter.getThroatFroudeNumber(), 1.0e-4);
    assertEquals(0.975418, meter.getEffectiveDischargeCoefficient(), 1.0e-6);
    assertEquals(0.483916, meter.getChisholmExponent(), 1.0e-6);
    assertEquals(4.08694, meter.getChisholmCoefficient(), 1.0e-5);
    assertEquals(1.235513, meter.getOverReadingFactor(), 1.0e-6);
    assertEquals(5.31926, meter.getMassFlowRate("kg/sec"), 1.0e-5);
  }

  /**
   * ISO/TR 11583 Annex A Example 2: water, X derived from the permanent pressure loss of 0.125 bar via clause 6.4.5.
   * The standard only prints the first two iterations for this example, so the converged values are pinned here as a
   * regression baseline; the standard's second-iteration gas rate is 6.365804 kg/s.
   */
  @Test
  void testIsoTr11583AnnexAExample2PressureLossRoute() {
    VenturiFlowMeter meter = buildAnnexAMeter(1000.0);
    meter.setSurfaceTensionFactor(VenturiFlowMeter.H_WATER_AMBIENT);
    meter.setPressureLoss(0.125, "bar");

    assertEquals(0.015239, meter.getLockhartMartinelliParameter(), 1.0e-6);
    assertEquals(3.76429, meter.getGasDensiometricFroudeNumber(), 1.0e-5);
    assertEquals(13.4991, meter.getThroatFroudeNumber(), 1.0e-4);
    assertEquals(0.976992, meter.getEffectiveDischargeCoefficient(), 1.0e-6);
    assertEquals(4.17597, meter.getChisholmCoefficient(), 1.0e-5);
    assertEquals(1.031441, meter.getOverReadingFactor(), 1.0e-6);
    assertEquals(6.381965, meter.getMassFlowRate("kg/sec"), 1.0e-6);
    assertTrue(meter.getValidityViolations().isEmpty(), meter.getValidityViolations().toString());
  }

  /** Uncertainty of C/Phi per ISO/TR 11583 6.5 Table 2. */
  @Test
  void testWetGasUncertainty() {
    VenturiFlowMeter meter = buildAnnexAMeter(800.0);
    meter.setLiquidToGasMassRatio(0.5);
    assertEquals(0.03, meter.getRelativeUncertaintyOfCOverPhi(), 1.0e-12);

    meter.setLiquidToGasMassRatio(1.0);
    assertEquals(0.025, meter.getRelativeUncertaintyOfCOverPhi(), 1.0e-12);
  }

  /** The Annex A point sits inside every ISO/TR 11583 limit of use. */
  @Test
  void testIsoTr11583ValidityRange() {
    VenturiFlowMeter meter = buildAnnexAMeter(800.0);
    meter.setLiquidToGasMassRatio(0.5);
    assertTrue(meter.getValidityViolations().isEmpty(), meter.getValidityViolations().toString());

    meter.setLiquidToGasMassRatio(3.0);
    assertFalse(meter.isWithinIso11583ValidityRange());
  }

  /** With no liquid information the wet-gas mode must fall back to the dry-gas result. */
  @Test
  void testWetGasWithoutLiquidFallsBackToDryGas() {
    VenturiFlowMeter dry = new VenturiFlowMeter(stream);
    dry.setGeometry(205.1, 138.1, "mm");
    dry.setDifferentialPressure(300.0, "mbar");

    VenturiFlowMeter wet = new VenturiFlowMeter(stream);
    wet.setGeometry(205.1, 138.1, "mm");
    wet.setDifferentialPressure(300.0, "mbar");
    wet.setWetGasCorrelation(VenturiFlowMeter.WetGasCorrelation.ISO_TR_11583);

    assertEquals(dry.getMassFlowRate("kg/hr"), wet.getMassFlowRate("kg/hr"), 1.0e-9);
    assertEquals(1.0, wet.getOverReadingFactor(), 1.0e-12);
  }

  /**
   * Keeping a calibrated discharge coefficient must apply the over-reading factor alone, so the result is the dry-gas
   * flow divided by Phi.
   */
  @Test
  void testCalibratedDischargeCoefficientAppliesOverReadingOnly() {
    VenturiFlowMeter meter = buildAnnexAMeter(800.0);
    meter.setDischargeCoefficient(0.9527);
    meter.setLiquidToGasMassRatio(0.5);
    meter.setUseWetGasDischargeCoefficient(false);

    assertEquals(0.9527, meter.getEffectiveDischargeCoefficient(), 1.0e-12);

    double phi = meter.getOverReadingFactor();
    VenturiFlowMeter dry = buildAnnexAMeter(800.0);
    dry.setDischargeCoefficient(0.9527);
    dry.setWetGasCorrelation(VenturiFlowMeter.WetGasCorrelation.NONE);

    assertEquals(dry.getMassFlowRate("kg/sec") / phi, meter.getMassFlowRate("kg/sec"), 1.0e-9);
  }

  /**
   * With no liquid information the de Leeuw wet-gas mode must fall back to the dry-gas result, same as ISO/TR 11583.
   */
  @Test
  void testDeLeeuwWithoutLiquidFallsBackToDryGas() {
    VenturiFlowMeter dry = new VenturiFlowMeter(stream);
    dry.setGeometry(205.1, 138.1, "mm");
    dry.setDifferentialPressure(300.0, "mbar");

    VenturiFlowMeter wet = new VenturiFlowMeter(stream);
    wet.setGeometry(205.1, 138.1, "mm");
    wet.setDifferentialPressure(300.0, "mbar");
    wet.setWetGasCorrelation(VenturiFlowMeter.WetGasCorrelation.DE_LEEUW);

    assertEquals(dry.getMassFlowRate("kg/hr"), wet.getMassFlowRate("kg/hr"), 1.0e-9);
    assertEquals(1.0, wet.getOverReadingFactor(), 1.0e-12);
  }

  /**
   * The de Leeuw (1997) exponent n (Steven, 2002, Eqs. (13a)-(13b)) must match the two-branch formula evaluated
   * independently here from the meter's own solved gas densiometric Froude number, at an operating point where Fr,gas
   * &gt;= 1.5.
   */
  @Test
  void testDeLeeuwExponentMatchesFormulaAboveFroudeBoundary() {
    VenturiFlowMeter meter = buildAnnexAMeter(800.0);
    meter.setWetGasCorrelation(VenturiFlowMeter.WetGasCorrelation.DE_LEEUW);
    meter.setLiquidToGasMassRatio(0.5);

    double froude = meter.getGasDensiometricFroudeNumber();
    assertTrue(froude >= 1.5, "expected Fr,gas >= 1.5, was " + froude);
    double expectedN = 0.606 * (1.0 - Math.exp(-0.746 * froude));
    assertEquals(expectedN, meter.getChisholmExponent(), 1.0e-9);
  }

  /**
   * Below Fr,gas = 1.5 (Steven, 2002, Eq. (13a)) the exponent is the 0.41 plateau; below the correlation's 0.5 lower
   * bound this class still extrapolates the plateau rather than returning NaN, but flags it via
   * {@link VenturiFlowMeter#getValidityViolations()}.
   */
  @Test
  void testDeLeeuwLowFroudeExtrapolatesPlateauAndFlagsViolation() {
    VenturiFlowMeter meter = buildAnnexAMeter(800.0);
    meter.setDifferentialPressure(0.001, "bar");
    meter.setWetGasCorrelation(VenturiFlowMeter.WetGasCorrelation.DE_LEEUW);
    meter.setLiquidToGasMassRatio(0.5);

    double froude = meter.getGasDensiometricFroudeNumber();
    assertTrue(froude < VenturiFlowMeter.DE_LEEUW_MIN_FROUDE_NUMBER, "expected Fr,gas below 0.5, was " + froude);
    assertEquals(0.41, meter.getChisholmExponent(), 1.0e-12);

    List<String> violations = meter.getValidityViolations();
    assertTrue(violations.stream().anyMatch(v -> v.contains("Fr,gas") && v.contains("0.5")), violations.toString());
    assertFalse(meter.isWithinDeLeeuwValidityRange());
  }

  /**
   * The de Leeuw over-reading factor must match the Chisholm-form equation evaluated from its own reported X and CCh.
   */
  @Test
  void testDeLeeuwOverReadingFactorFormula() {
    VenturiFlowMeter meter = buildAnnexAMeter(800.0);
    meter.setWetGasCorrelation(VenturiFlowMeter.WetGasCorrelation.DE_LEEUW);
    meter.setLiquidToGasMassRatio(0.5);

    double x = meter.getLockhartMartinelliParameter();
    double chisholm = meter.getChisholmCoefficient();
    double expectedPhi = Math.sqrt(1.0 + chisholm * x + x * x);
    assertEquals(expectedPhi, meter.getOverReadingFactor(), 1.0e-9);
    assertTrue(meter.getOverReadingFactor() > 1.0);
  }

  /**
   * Unlike ISO/TR 11583 Equation (4), de Leeuw (1997) never replaces the discharge coefficient, regardless of
   * {@link VenturiFlowMeter#setUseWetGasDischargeCoefficient(boolean)}. This guards the calibration trap found for
   * ISO/TR 11583 (see the field-reported in-service discharge coefficients) from ever affecting de Leeuw.
   */
  @Test
  void testDeLeeuwNeverReplacesDischargeCoefficient() {
    VenturiFlowMeter meter = buildAnnexAMeter(800.0);
    meter.setWetGasCorrelation(VenturiFlowMeter.WetGasCorrelation.DE_LEEUW);
    meter.setDischargeCoefficient(0.95270504);
    meter.setLiquidToGasMassRatio(0.5);
    meter.setUseWetGasDischargeCoefficient(true);

    assertEquals(0.95270504, meter.getEffectiveDischargeCoefficient(), 1.0e-12);
  }

  /**
   * de Leeuw (1997) was fitted to a 4 in Venturi with beta = 0.401 (Steven, 2002, Sec. 4) and its exponent has no beta
   * term; the Annex A geometry (beta = 0.6) must be flagged as a geometry extrapolation.
   */
  @Test
  void testDeLeeuwValidityFlagsBetaDeparture() {
    VenturiFlowMeter meter = buildAnnexAMeter(800.0);
    meter.setWetGasCorrelation(VenturiFlowMeter.WetGasCorrelation.DE_LEEUW);
    meter.setLiquidToGasMassRatio(0.5);

    List<String> violations = meter.getValidityViolations();
    assertTrue(violations.stream().anyMatch(v -> v.contains("beta")), violations.toString());
    assertFalse(meter.isWithinDeLeeuwValidityRange());
  }

  /**
   * de Leeuw (1997) and ISO/TR 11583 are independent correlations and must not be expected to agree; at the same
   * operating point both over-read (Phi &gt; 1) but by different amounts.
   */
  @Test
  void testDeLeeuwAndIsoTr11583DisagreeAtSameOperatingPoint() {
    VenturiFlowMeter deLeeuw = buildAnnexAMeter(800.0);
    deLeeuw.setWetGasCorrelation(VenturiFlowMeter.WetGasCorrelation.DE_LEEUW);
    deLeeuw.setLiquidToGasMassRatio(0.5);

    VenturiFlowMeter isoTr = buildAnnexAMeter(800.0);
    isoTr.setLiquidToGasMassRatio(0.5);

    assertTrue(deLeeuw.getOverReadingFactor() > 1.0);
    assertTrue(isoTr.getOverReadingFactor() > 1.0);
    assertTrue(Math.abs(deLeeuw.getOverReadingFactor() - isoTr.getOverReadingFactor()) > 1.0e-6,
        "expected the two correlations to disagree at the same operating point");
  }
}
