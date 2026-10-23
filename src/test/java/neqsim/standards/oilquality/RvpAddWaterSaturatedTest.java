package neqsim.standards.oilquality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.process.equipment.stream.Stream;
import neqsim.standards.oilquality.Standard_ASTM_D6377.RvpMethod;
import neqsim.standards.oilquality.Standard_ASTM_D6377.RvpResult;
import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link RvpMethod#RVP_ASTM_D6377_ADD_WATER_SATURATED}: RVPE = 0.834 * (VPCR4_dry + psat_water,IF97) at
 * 37.8 C, and for the shared water-free fluid used by {@link RvpMethod#VPCR4_NO_WATER}. All pressures are in bara.
 *
 * @author NeqSim
 * @version 1.0
 */
class RvpAddWaterSaturatedTest extends NeqSimTest {
  /** Dry VPCR4 of {@link #createOil(double, boolean)} at 37.8 C [bara] (regression baseline, SRK). */
  private static final double DRY_VPCR4_BARA = 1.601125442508;

  /** IAPWS-IF97 Region 4 water saturation pressure at 310.95 K (37.8 C) [bara]. */
  private static final double WATER_PSAT_378_BARA = 0.0656094352187;

  /** Verifies the water term against the IAPWS-IF97 verification values for Region 4 (Table 35 of the release). */
  @Test
  void waterSaturationPressureMatchesIf97VerificationValues() {
    // IF97 Table 35: psat(300 K) = 0.353658941e-2 MPa, psat(500 K) = 0.263889776e1 MPa,
    // psat(600 K) = 0.123443146e2 MPa.
    assertEquals(0.0353658941, Standard_ASTM_D6377.getWaterSaturationPressure(300.0), 1.0e-10);
    assertEquals(26.3889776, Standard_ASTM_D6377.getWaterSaturationPressure(500.0), 1.0e-7);
    assertEquals(123.443146, Standard_ASTM_D6377.getWaterSaturationPressure(600.0), 1.0e-6);
    assertEquals(WATER_PSAT_378_BARA, Standard_ASTM_D6377.getWaterSaturationPressure(310.95), 1.0e-12);
  }

  /** The IF97 Region 4 equation is only valid between the triple-point region and the critical point. */
  @Test
  void waterSaturationPressureRejectsTemperaturesOutsideIf97Range() {
    assertThrows(IllegalArgumentException.class, () -> Standard_ASTM_D6377.getWaterSaturationPressure(273.0));
    assertThrows(IllegalArgumentException.class, () -> Standard_ASTM_D6377.getWaterSaturationPressure(647.2));
    assertThrows(IllegalArgumentException.class, () -> Standard_ASTM_D6377.getWaterSaturationPressure(Double.NaN));
    assertEquals(0.00611212677, Standard_ASTM_D6377.getWaterSaturationPressure(273.15), 1.0e-9);
  }

  /** A water-free fluid still gets the water term: 0.834 * (VPCR4 + psat). */
  @Test
  void dryFluidAddsWaterSaturationPressureBeforeCorrelation() {
    Standard_ASTM_D6377 standard = new Standard_ASTM_D6377(createOil(0.0, false));
    standard.calculate();
    double vpcr4 = standard.getRvpResult(RvpMethod.VPCR4).getValue();
    RvpResult result = standard.getRvpResult(RvpMethod.RVP_ASTM_D6377_ADD_WATER_SATURATED);
    assertEquals(DRY_VPCR4_BARA, vpcr4, 1.0e-9);
    assertTrue(result.isValid());
    assertEquals(0.834 * (vpcr4 + WATER_PSAT_378_BARA), result.getValue(), 1.0e-9);
    assertEquals(1.390056888024, result.getValue(), 1.0e-9);
    // Not dry RVPE plus the full water pressure.
    assertNotEquals(0.834 * vpcr4 + WATER_PSAT_378_BARA, result.getValue(), 1.0e-3);
  }

  /** The result depends only on the hydrocarbons: neither the amount nor the position of water changes it. */
  @Test
  void resultIsIndependentOfWaterAmountAndPosition() {
    double expected = 0.834 * (DRY_VPCR4_BARA + WATER_PSAT_378_BARA);
    for (boolean middle : new boolean[] {false, true}) {
      for (double water : new double[] {0.001, 0.1, 0.25}) {
        Standard_ASTM_D6377 standard = new Standard_ASTM_D6377(createOil(water, middle));
        standard.calculate();
        String label = "water=" + water + (middle ? " (middle)" : " (last)");
        assertEquals(expected, standard.getRvpResult(RvpMethod.RVP_ASTM_D6377_ADD_WATER_SATURATED).getValue(), 1.0e-9,
            label);
        // The wet EOS result does change with water; the water-saturated result does not.
        assertNotEquals(DRY_VPCR4_BARA, standard.getRvpResult(RvpMethod.VPCR4).getValue(), 1.0e-3, label);
      }
    }
  }

  /**
   * Water in the middle of the component list is kept as a trace instead of removed, so retained BIPs stay on their
   * pairs. Removing it would shift methane onto the pentane/water BIP slot and change dry VPCR4 by about 2%.
   */
  @Test
  void waterNotLastKeepsHydrocarbonBipsForNoWaterVariants() {
    SystemInterface wetMiddle = createOil(0.1, true);
    SystemInterface removed = wetMiddle.clone();
    removed.removeComponent("water");
    removed.init(0);
    Standard_ASTM_D6377 removedStandard = new Standard_ASTM_D6377(removed);
    removedStandard.calculate();
    assertNotEquals(DRY_VPCR4_BARA, removedStandard.getRvpResult(RvpMethod.VPCR4).getValue(), 1.0e-2);

    Standard_ASTM_D6377 standard = new Standard_ASTM_D6377(wetMiddle);
    standard.calculate();
    assertEquals(DRY_VPCR4_BARA, standard.getRvpResult(RvpMethod.VPCR4_NO_WATER).getValue(), 1.0e-9);
    assertEquals(DRY_VPCR4_BARA, standard.getRvpResult(RvpMethod.RVP_ASTM_D323_73_79).getValue(), 1.0e-9);
  }

  /**
   * After an unconverged flash two phase slots can point at the same phase object, which makes a direct
   * removeComponent fail. The water-free copy is reset to its unflashed state first, so the result is unaffected.
   */
  @Test
  void waterRemovalIsRobustToScrambledPhaseBookkeeping() {
    SystemInterface wet = createOil(0.25, false);
    wet.setPhaseIndex(1, 0);
    SystemInterface direct = wet.clone();
    assertThrows(NullPointerException.class, () -> direct.removeComponent("water"));

    Standard_ASTM_D6377 standard = new Standard_ASTM_D6377(wet);
    assertEquals(0.834 * (DRY_VPCR4_BARA + WATER_PSAT_378_BARA),
        standard.getRvpResult(RvpMethod.RVP_ASTM_D6377_ADD_WATER_SATURATED).getValue(), 1.0e-9);
    assertEquals(DRY_VPCR4_BARA, standard.getRvpResult(RvpMethod.VPCR4_NO_WATER).getValue(), 1.0e-9);
  }

  /** RVP is defined at 37.8 C; the user-set reference temperature does not apply to this method. */
  @Test
  void referenceTemperatureIsFixedAt378C() {
    Standard_ASTM_D6377 standard = new Standard_ASTM_D6377(createOil(0.1, false));
    standard.setReferenceTemperature(20.0, "C");
    standard.calculate();
    RvpResult result = standard.getRvpResult(RvpMethod.RVP_ASTM_D6377_ADD_WATER_SATURATED);
    assertEquals(37.8, result.getReferenceTemperatureC(), 0.0);
    assertEquals(0.834 * (DRY_VPCR4_BARA + WATER_PSAT_378_BARA), result.getValue(), 1.0e-9);
    assertEquals(20.0, standard.getRvpResult(RvpMethod.VPCR4_NO_WATER).getReferenceTemperatureC(), 0.0);
    assertNotEquals(DRY_VPCR4_BARA, standard.getRvpResult(RvpMethod.VPCR4_NO_WATER).getValue(), 1.0e-2);
  }

  /** The label, the enum name, the string API, unit conversion and the Stream overload agree. */
  @Test
  void labelStringApiAndStreamAgree() {
    assertSame(RvpMethod.RVP_ASTM_D6377_ADD_WATER_SATURATED,
        RvpMethod.fromLabel("RVP_ASTM_D6377_add_water_saturated"));
    assertSame(RvpMethod.RVP_ASTM_D6377_ADD_WATER_SATURATED,
        RvpMethod.fromLabel("RVP_ASTM_D6377_ADD_WATER_SATURATED"));
    double expected = 0.834 * (DRY_VPCR4_BARA + WATER_PSAT_378_BARA);

    Standard_ASTM_D6377 standard = new Standard_ASTM_D6377(createOil(0.1, false));
    standard.setMethodRVP("RVP_ASTM_D6377_add_water_saturated");
    standard.calculate();
    assertEquals(expected, standard.getValue("RVP", "bara"), 1.0e-9);
    assertEquals(100.0 * expected, standard.getValue("RVP", "kPa"), 1.0e-7);
    assertEquals(expected, standard.getRvpResult().getValue(), 1.0e-9);

    Stream stream = new Stream("wet oil", createOil(0.1, false));
    assertEquals(expected, stream.getRVP(20.0, "C", "bara", "RVP_ASTM_D6377_add_water_saturated"), 1.0e-9);
  }

  /** A dry flash that returns a pressure but misses the V/L = 4 target is reported as invalid, not as a number. */
  @Test
  void missedVolumeRatioTargetIsInvalid() {
    SystemInterface binary = new SystemSrkEos(293.15, 5.0);
    binary.addComponent("n-pentane", 0.2);
    binary.addTBPfraction("C11", 0.8, 0.145, 0.82);
    binary.setMixingRule(2);
    binary.init(0);
    Standard_ASTM_D6377 standard = new Standard_ASTM_D6377(binary);
    standard.calculate();
    // The plain VPCR4 flash returns a positive pressure at a gas volume fraction of about 0.83, not 0.8.
    assertTrue(standard.getRvpResult(RvpMethod.VPCR4).isValid());
    binary.initPhysicalProperties("density");
    assertEquals(0.826, binary.getPhase("gas").getCorrectedVolume() / binary.getCorrectedVolume(), 1.0e-3);
    RvpResult result = standard.getRvpResult(RvpMethod.RVP_ASTM_D6377_ADD_WATER_SATURATED);
    assertTrue(Double.isNaN(result.getValue()));
    assertFalse(result.isValid());
  }

  /**
   * Builds a synthetic SRK oil with a nonzero pentane/methane BIP of 0.0123.
   *
   * @param water water amount in mol (0 for no water)
   * @param middle whether water is added between pentane and methane instead of last
   * @return oil at 293.15 K and 5 bara with 0.9 mol hydrocarbons and multiphase check enabled
   */
  private SystemInterface createOil(double water, boolean middle) {
    boolean hasWater = water > 0.0;
    SystemInterface oil = new SystemSrkEos(293.15, 5.0);
    oil.addComponent("n-pentane", 0.3);
    if (hasWater && middle) {
      oil.addComponent("water", water);
    }
    oil.addComponent("methane", 0.001);
    oil.addComponent("ethane", 0.009);
    oil.addComponent("propane", 0.09);
    oil.addTBPfraction("C11", 0.5, 0.145, 0.82);
    if (hasWater && !middle) {
      oil.addComponent("water", water);
    }
    oil.setMixingRule(2);
    int methaneIndex = hasWater && middle ? 2 : 1;
    for (int phase = 0; phase < oil.getMaxNumberOfPhases(); phase++) {
      ((PhaseEosInterface) oil.getPhase(phase)).getEosMixingRule().setBinaryInteractionParameter(0, methaneIndex,
          0.0123);
    }
    oil.setMultiPhaseCheck(true);
    oil.init(0);
    return oil;
  }
}
