package neqsim.process.equipment.pipeline.twophasepipe;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;

/**
 * Validates {@link FlowRegimeDetector} against the published horizontal flow map.
 *
 * <p>
 * The detector had been changed several times against a single hydrocarbon fixture, which is how it came to report
 * annular flow at a liquid fraction of 0.45 with a superficial gas velocity of 1.7 m/s. This class supplies the
 * measurement that was missing: the canonical air-water case of Mandhane, Gregory and Aziz (1974), 25 mm horizontal
 * pipe at ambient conditions, which every horizontal flow-map study since is reported against.
 * </p>
 *
 * <p>
 * Anchors are placed well inside each published region, never on a boundary. Published maps disagree with each other
 * near the transitions — the slug/annular boundary at a superficial liquid velocity of 0.3 m/s is quoted anywhere
 * between 20 and 30 m/s of gas — so a boundary-adjacent anchor measures the disagreement between sources rather than
 * the correctness of the code.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
class FlowRegimeMapValidationTest {
  /** Inside diameter of the canonical air-water case, in m. */
  private static final double AIR_WATER_DIAMETER = 0.025;

  /** Water density at ambient conditions, in kg/m3. */
  private static final double RHO_WATER = 998.0;

  /** Air density at ambient conditions, in kg/m3. */
  private static final double RHO_AIR = 1.2;

  /** Water dynamic viscosity, in Pa.s. */
  private static final double MU_WATER = 1.0e-3;

  /** Air dynamic viscosity, in Pa.s. */
  private static final double MU_AIR = 1.8e-5;

  /** Air-water interfacial tension, in N/m. */
  private static final double SIGMA_AIR_WATER = 0.072;

  /** Holdup used only to convert superficial velocities into phase velocities. */
  private static final double ALPHA_G = 0.90;

  /** Liquid counterpart of {@link #ALPHA_G}. */
  private static final double ALPHA_L = 0.10;

  /**
   * Builds a horizontal section carrying the requested superficial velocities.
   *
   * @param superficialLiquid superficial liquid velocity, in m/s
   * @param superficialGas superficial gas velocity, in m/s
   * @param diameter inside diameter, in m
   * @param liquidDensity liquid density, in kg/m3
   * @param gasDensity gas density, in kg/m3
   * @param liquidViscosity liquid dynamic viscosity, in Pa.s
   * @param surfaceTension interfacial tension, in N/m
   * @return a section ready for regime detection
   */
  private PipeSection section(double superficialLiquid, double superficialGas, double diameter, double liquidDensity,
      double gasDensity, double liquidViscosity, double surfaceTension) {
    PipeSection sec = new PipeSection(0.0, 100.0, diameter, 0.0);
    sec.setGasHoldup(ALPHA_G);
    sec.setLiquidHoldup(ALPHA_L);
    sec.setGasVelocity(superficialGas / ALPHA_G);
    sec.setLiquidVelocity(superficialLiquid / ALPHA_L);
    sec.setGasDensity(gasDensity);
    sec.setLiquidDensity(liquidDensity);
    sec.setLiquidViscosity(liquidViscosity);
    sec.setGasViscosity(MU_AIR);
    sec.setSurfaceTension(surfaceTension);
    sec.updateDerivedQuantities();
    return sec;
  }

  /**
   * Classifies a point on the canonical air-water map.
   *
   * @param superficialLiquid superficial liquid velocity, in m/s
   * @param superficialGas superficial gas velocity, in m/s
   * @return the detected regime
   */
  private FlowRegime airWater(double superficialLiquid, double superficialGas) {
    return new FlowRegimeDetector().detectFlowRegime(
        section(superficialLiquid, superficialGas, AIR_WATER_DIAMETER, RHO_WATER, RHO_AIR, MU_WATER, SIGMA_AIR_WATER));
  }

  /**
   * Asserts one map anchor.
   *
   * @param superficialLiquid superficial liquid velocity, in m/s
   * @param superficialGas superficial gas velocity, in m/s
   * @param expected the published regime for this point
   */
  private void assertAnchor(double superficialLiquid, double superficialGas, FlowRegime expected) {
    FlowRegime actual = airWater(superficialLiquid, superficialGas);
    Assertions.assertEquals(expected, actual, "air-water 25 mm horizontal at vsl = " + superficialLiquid
        + " m/s and vsg = " + superficialGas + " m/s is " + expected + " on the published map, but was " + actual);
  }

  @Test
  @DisplayName("the canonical air-water horizontal map is reproduced away from the transitions")
  void testCanonicalAirWaterMapAnchors() {
    assertAnchor(0.005, 0.5, FlowRegime.STRATIFIED_SMOOTH);
    assertAnchor(0.02, 5.0, FlowRegime.STRATIFIED_WAVY);
    assertAnchor(0.02, 40.0, FlowRegime.ANNULAR);
    assertAnchor(0.05, 60.0, FlowRegime.ANNULAR);
    assertAnchor(0.3, 80.0, FlowRegime.ANNULAR);
    assertAnchor(0.5, 0.3, FlowRegime.SLUG);
    assertAnchor(1.0, 2.0, FlowRegime.SLUG);
    assertAnchor(5.0, 0.5, FlowRegime.DISPERSED_BUBBLE);
  }

  /**
   * The annular region must retreat as the line carries more liquid.
   *
   * <p>
   * A criterion that decides annular flow on gas velocity alone gets the anchors above right and this wrong, because
   * its boundary is a vertical line on the map.
   * </p>
   *
   * <p>
   * Only the liquid-rich side is checked. Below a superficial liquid velocity of about 0.05 m/s the boundary is not
   * monotonic and should not be: the layer is then so thin that it is Kelvin-Helmholtz stable, which delays annular
   * flow to a higher gas velocity again. That dip belongs to the Taitel-Dukler construction and is not a defect.
   * </p>
   */
  @Test
  @DisplayName("the annular boundary moves to higher gas velocity as the liquid rate rises")
  void testAnnularBoundaryRetreatsWithLiquidRate() {
    double[] liquidRates = new double[] { 0.05, 0.3, 1.0 };
    double previousBoundary = 0.0;
    for (int i = 0; i < liquidRates.length; i++) {
      double boundary = Double.NaN;
      for (double vsg = 1.0; vsg <= 400.0; vsg *= 1.15) {
        if (airWater(liquidRates[i], vsg) == FlowRegime.ANNULAR) {
          boundary = vsg;
          break;
        }
      }
      Assertions.assertTrue(!Double.isNaN(boundary),
          "no annular flow was found at any gas rate for vsl = " + liquidRates[i] + " m/s");
      Assertions.assertTrue(boundary > previousBoundary,
          "the annular boundary must move up with liquid rate, but at vsl = " + liquidRates[i] + " m/s it sat at vsg = "
              + boundary + " m/s against " + previousBoundary + " m/s at the rate below");
      previousBoundary = boundary;
    }
  }

  /**
   * Annular flow cannot be reported when the liquid would bridge the bore.
   *
   * <p>
   * This is the invariant the detector previously violated: on a liquid-rich hydrocarbon line it returned annular at a
   * liquid fraction near 0.45 with only 1.7 m/s of gas, a film no gas stream could hold up. Barnea (1987) puts the
   * limit at a liquid fraction of 0.24, half the 0.48 a slug body needs before it can bridge. The case is swept across
   * diameters because the defect only appeared at some of them.
   * </p>
   */
  @Test
  @DisplayName("a liquid-rich line at low gas velocity is never called annular")
  void testNoAnnularFlowWhenTheLiquidCanBridgeTheBore() {
    double[] diameters = new double[] { 0.20, 0.30, 0.40, 0.50 };
    for (int i = 0; i < diameters.length; i++) {
      for (double vsg = 0.5; vsg <= 4.0; vsg += 0.5) {
        FlowRegime regime = new FlowRegimeDetector()
            .detectFlowRegime(section(0.9, vsg, diameters[i], 700.0, 45.0, 5.0e-4, 0.015));
        Assertions.assertNotEquals(FlowRegime.ANNULAR, regime,
            "a liquid-rich line cannot be annular at vsg = " + vsg + " m/s in a " + diameters[i] + " m bore");
      }
    }
  }

  /**
   * The smooth-to-wavy threshold must respond to the liquid rate.
   *
   * <p>
   * Taitel and Dukler (1976) transition D carries the liquid velocity in the divisor, so a faster liquid needs less gas
   * to raise waves. The form previously coded here had a second gas density there instead, which both removed that
   * dependence and left the group dimensionally inconsistent, putting the threshold near 52 m/s for air and water and
   * reporting an ordinary wavy line as smooth.
   * </p>
   */
  @Test
  @DisplayName("more liquid makes waves easier, as Taitel-Dukler transition D requires")
  void testWavyTransitionRespondsToLiquidRate() {
    Assertions.assertEquals(FlowRegime.STRATIFIED_SMOOTH, airWater(0.002, 2.0),
        "a slow thin liquid layer under 2 m/s of gas should still be smooth");
    Assertions.assertEquals(FlowRegime.STRATIFIED_WAVY, airWater(0.02, 5.0),
        "a faster liquid layer under 5 m/s of gas should be wavy");
  }
}
