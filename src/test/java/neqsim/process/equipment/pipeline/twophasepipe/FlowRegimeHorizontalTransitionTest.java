package neqsim.process.equipment.pipeline.twophasepipe;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;

/**
 * Regression tests for the horizontal annular transition in {@link FlowRegimeDetector}.
 *
 * <p>
 * The default horizontal path decides annular flow with {@code isAnnularFlow}, which is the vertical
 * droplet-entrainment criterion {@code U_SG > 3.1 * (sigma * g * drho / rhoG^2)^0.25}, and it is checked before the
 * stratified/slug transition. That threshold is around 1.6 m/s for a 200 mm gas line and 0.75 m/s for a 14-inch
 * high-pressure export line, so in horizontal flow it short-circuits the flow map and returns annular for essentially
 * any gas pipeline, whatever its liquid level.
 * </p>
 *
 * <p>
 * {@link FlowRegimeDetector#setUseEquilibriumLevelAnnularTransition(boolean)} selects the horizontal criterion of
 * Taitel and Dukler (1976), where the branch after the Kelvin-Helmholtz instability is set by the equilibrium liquid
 * level. These tests pin the difference between the two so the distinction cannot be lost.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
class FlowRegimeHorizontalTransitionTest {
  /** Inside diameter of the low-velocity case, in m. */
  private static final double SMALL_LINE_DIAMETER = 0.20;

  /** Inside diameter of the export-line case, in m. */
  private static final double EXPORT_LINE_DIAMETER = 0.355;

  /** Gas-oil interfacial tension, in N/m. */
  private static final double SIGMA = 0.02;

  /** Liquid viscosity, in Pa.s. */
  private static final double MU_L = 5.0e-4;

  /** Liquid density, in kg/m3. */
  private static final double RHO_L = 600.0;

  /** Gas holdup used to impose the superficial velocities; both phases must be present. */
  private static final double ALPHA_G = 0.95;

  /** Liquid holdup used to impose the superficial velocities. */
  private static final double ALPHA_L = 0.05;

  /**
   * Builds a detector with the requested horizontal transition.
   *
   * @param equilibriumLevel true to select the Taitel-Dukler equilibrium-level transition
   * @return a configured detector
   */
  private FlowRegimeDetector detector(boolean equilibriumLevel) {
    FlowRegimeDetector detector = new FlowRegimeDetector();
    detector.setUseEquilibriumLevelAnnularTransition(equilibriumLevel);
    return detector;
  }

  /**
   * Builds a horizontal section carrying the requested superficial velocities.
   *
   * @param superficialLiquid superficial liquid velocity, in m/s
   * @param superficialGas superficial gas velocity, in m/s
   * @param diameter inside diameter, in m
   * @param gasDensity gas density, in kg/m3
   * @return a section ready for regime detection
   */
  private PipeSection section(double superficialLiquid, double superficialGas, double diameter, double gasDensity) {
    PipeSection section = new PipeSection(0.0, 100.0, diameter, 0.0);
    section.setGasHoldup(ALPHA_G);
    section.setLiquidHoldup(ALPHA_L);
    section.setGasVelocity(superficialGas / ALPHA_G);
    section.setLiquidVelocity(superficialLiquid / ALPHA_L);
    section.setGasDensity(gasDensity);
    section.setLiquidDensity(RHO_L);
    section.setLiquidViscosity(MU_L);
    section.setSurfaceTension(SIGMA);
    section.updateDerivedQuantities();
    return section;
  }

  /**
   * Detects the regime for a horizontal section with the requested transition.
   *
   * @param equilibriumLevel true to select the Taitel-Dukler equilibrium-level transition
   * @param superficialLiquid superficial liquid velocity, in m/s
   * @param superficialGas superficial gas velocity, in m/s
   * @param diameter inside diameter, in m
   * @param gasDensity gas density, in kg/m3
   * @return the detected flow regime
   */
  private FlowRegime detect(boolean equilibriumLevel, double superficialLiquid, double superficialGas, double diameter,
      double gasDensity) {
    return detector(equilibriumLevel)
        .detectFlowRegime(section(superficialLiquid, superficialGas, diameter, gasDensity));
  }

  /**
   * The droplet-entrainment threshold is low enough to catch a slow horizontal gas line.
   *
   * <p>
   * This documents the magnitude that motivates the alternative transition rather than asserting that it is desirable:
   * at 40 kg/m3 gas density the criterion sits near 1.6 m/s.
   * </p>
   */
  @Test
  @DisplayName("Vertical droplet criterion triggers near 1.6 m/s for a 200 mm gas line")
  void testDropletCriterionThresholdIsLowInHorizontalFlow() {
    double rhoG = 40.0;
    double expectedThreshold = 3.1 * Math.pow(SIGMA * 9.81 * (RHO_L - rhoG) / (rhoG * rhoG), 0.25);

    Assertions.assertTrue(expectedThreshold > 1.0 && expectedThreshold < 2.5,
        "the vertical droplet criterion should sit near 1.6 m/s for this gas density, but was " + expectedThreshold);

    FlowRegime slow = detect(false, 0.035, 0.9 * expectedThreshold, SMALL_LINE_DIAMETER, rhoG);
    FlowRegime fast = detect(false, 0.035, 1.1 * expectedThreshold, SMALL_LINE_DIAMETER, rhoG);

    Assertions.assertNotEquals(FlowRegime.ANNULAR, slow, "below the threshold the legacy path must not report annular");
    Assertions.assertEquals(FlowRegime.ANNULAR, fast, "above the threshold the legacy path reports annular");
  }

  /**
   * The equilibrium-level transition must not call a slow, liquid-loaded horizontal line annular.
   */
  @Test
  @DisplayName("Equilibrium-level transition keeps a slow 200 mm gas line out of annular flow")
  void testEquilibriumLevelTransitionRejectsAnnularAtLowGasVelocity() {
    FlowRegime regime = detect(true, 0.035, 1.65, SMALL_LINE_DIAMETER, 40.0);

    Assertions.assertNotEquals(FlowRegime.ANNULAR, regime,
        "a 200 mm horizontal line at 1.65 m/s superficial gas velocity is not annular flow, but got " + regime);
  }

  /**
   * A genuine high-velocity lean export line must still be annular under both transitions.
   */
  @Test
  @DisplayName("Both transitions report annular for a fast lean export line")
  void testExportLineRemainsAnnularUnderBothTransitions() {
    double rhoG = 120.0;
    double superficialGas = 8.5;
    double superficialLiquid = 0.07;

    FlowRegime legacy = detect(false, superficialLiquid, superficialGas, EXPORT_LINE_DIAMETER, rhoG);
    FlowRegime equilibrium = detect(true, superficialLiquid, superficialGas, EXPORT_LINE_DIAMETER, rhoG);

    Assertions.assertEquals(FlowRegime.ANNULAR, legacy, "the legacy path must keep a fast lean line annular");
    Assertions.assertEquals(FlowRegime.ANNULAR, equilibrium,
        "the equilibrium-level transition must also keep a fast lean line annular, but got " + equilibrium);
  }

  /**
   * The selection must default to the legacy behaviour so existing results do not move.
   */
  @Test
  @DisplayName("Equilibrium-level transition is off by default")
  void testEquilibriumLevelTransitionIsOffByDefault() {
    Assertions.assertFalse(new FlowRegimeDetector().isUseEquilibriumLevelAnnularTransition(),
        "the alternative transition must be opt-in until closure blending is available");
  }
}
