package neqsim.process.equipment.pipeline.twophasepipe;

import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;
import neqsim.process.equipment.pipeline.twophasepipe.closure.InterfacialFriction;
import neqsim.process.equipment.pipeline.twophasepipe.closure.WallFriction;

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
   * The horizontal transition is the default, and the blending it depends on is always available.
   */
  @Test
  @DisplayName("Equilibrium-level transition is on by default, blending is on")
  void testEquilibriumLevelTransitionIsOnByDefault() {
    FlowRegimeDetector detector = new FlowRegimeDetector();

    Assertions.assertTrue(detector.isUseEquilibriumLevelAnnularTransition(),
        "the horizontal branch must decide annular flow on the equilibrium level, not on a vertical criterion");
    Assertions.assertTrue(detector.isBlendRegimeTransitions(),
        "closure blending must default on, otherwise the transition steps hold-up");
  }

  /**
   * A pipe must be able to select the criterion its own detector uses.
   *
   * <p>
   * {@link neqsim.process.equipment.pipeline.TwoFluidPipe} owns a private detector with no accessor, so before the
   * delegating setter the choice of horizontal criterion was unreachable from the equipment class and could only be
   * exercised by reflection.
   * </p>
   */
  @Test
  @DisplayName("TwoFluidPipe exposes the horizontal annular criterion")
  void testPipeDelegatesTheTransitionSetting() {
    neqsim.process.equipment.pipeline.TwoFluidPipe pipe = new neqsim.process.equipment.pipeline.TwoFluidPipe(
        "criterion-delegation");

    Assertions.assertTrue(pipe.isUseEquilibriumLevelAnnularTransition(),
        "a new pipe must start on the same criterion as a new detector");

    pipe.setUseEquilibriumLevelAnnularTransition(false);
    Assertions.assertFalse(pipe.isUseEquilibriumLevelAnnularTransition(),
        "the pipe must delegate the criterion to the detector it owns");

    pipe.setUseEquilibriumLevelAnnularTransition(true);
    Assertions.assertTrue(pipe.isUseEquilibriumLevelAnnularTransition(),
        "the criterion must be selectable in both directions");
  }

  /**
   * Blending must give a continuous regime composition across the transition.
   *
   * <p>
   * A hard switch shows up as a jump in the weight of a regime for an arbitrarily small change in gas velocity. Walking
   * the gas velocity across the transition, no single step may move any regime weight by more than a modest fraction.
   * </p>
   */
  @Test
  @DisplayName("Regime weights vary continuously across the horizontal transition")
  void testBlendedRegimeWeightsAreContinuous() {
    FlowRegimeDetector detector = detector(true);
    double rhoG = 40.0;
    Map<FlowRegime, Double> previous = null;
    double largestStep = 0.0;

    for (int i = 0; i <= 200; i++) {
      double superficialGas = 0.5 + i * 0.02;
      PipeSection section = section(0.035, superficialGas, SMALL_LINE_DIAMETER, rhoG);
      detector.classify(section);
      Map<FlowRegime, Double> current = weightsOf(section);

      if (previous != null) {
        for (FlowRegime regime : FlowRegime.values()) {
          double before = previous.containsKey(regime) ? previous.get(regime) : 0.0;
          double after = current.containsKey(regime) ? current.get(regime) : 0.0;
          largestStep = Math.max(largestStep, Math.abs(after - before));
        }
      }
      previous = current;
    }

    Assertions.assertTrue(largestStep < 0.2,
        "a blended transition must not step a regime weight by more than 0.2 per 0.02 m/s, but stepped " + largestStep);
  }

  /**
   * Transient momentum sources must consume the detector's continuous regime weights.
   *
   * <p>
   * This is a calculation-level check: it compares the source evaluator's stored wall and
   * interfacial stresses with the convex combination of the same authoritative closure models.
   * </p>
   */
  @Test
  @DisplayName("Transient sources consume continuous regime weights")
  void testTransientSourcesConsumeRegimeWeights() {
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    boolean foundTransition = false;

    for (int i = 0; i <= 200; i++) {
      double superficialGas = 0.5 + i * 0.02;
      TwoFluidSection upstream = transientSection(0.0, superficialGas);
      TwoFluidSection downstream = transientSection(100.0, superficialGas);

      equations.calcRHS(new TwoFluidSection[] { upstream, downstream }, 100.0);
      Map<FlowRegime, Double> weights = upstream.getRegimeWeights();
      if (weights == null || weights.size() < 2) {
        continue;
      }
      foundTransition = true;

      WallFriction wallFriction = new WallFriction();
      InterfacialFriction interfacialFriction = new InterfacialFriction();
      double expectedGasWallShear = 0.0;
      double expectedLiquidWallShear = 0.0;
      double expectedInterfacialShear = 0.0;
      double expectedInterfacialArea = 0.0;

      for (Map.Entry<FlowRegime, Double> entry : weights.entrySet()) {
        WallFriction.WallFrictionResult wall =
            wallFriction.calculate(
                entry.getKey(),
                upstream.getGasVelocity(),
                upstream.getLiquidVelocity(),
                upstream.getGasDensity(),
                upstream.getLiquidDensity(),
                upstream.getGasViscosity(),
                upstream.getLiquidViscosity(),
                upstream.getLiquidHoldup(),
                upstream.getDiameter(),
                upstream.getRoughness());
        InterfacialFriction.InterfacialFrictionResult interfacial =
            interfacialFriction.calculate(
                entry.getKey(),
                upstream.getGasVelocity(),
                upstream.getLiquidVelocity(),
                upstream.getGasDensity(),
                upstream.getLiquidDensity(),
                upstream.getGasViscosity(),
                upstream.getLiquidViscosity(),
                upstream.getLiquidHoldup(),
                upstream.getDiameter(),
                upstream.getSurfaceTension());
        expectedGasWallShear += entry.getValue() * wall.gasWallShear;
        expectedLiquidWallShear += entry.getValue() * wall.liquidWallShear;
        expectedInterfacialShear += entry.getValue() * interfacial.interfacialShear;
        expectedInterfacialArea += entry.getValue() * interfacial.interfacialAreaPerLength;
      }

      assertRelativeEquals(expectedGasWallShear, upstream.getGasWallShear());
      assertRelativeEquals(expectedLiquidWallShear, upstream.getLiquidWallShear());
      assertRelativeEquals(expectedInterfacialShear, upstream.getInterfacialShear());
      assertRelativeEquals(expectedInterfacialArea, upstream.getInterfacialWidth());
      break;
    }

    Assertions.assertTrue(foundTransition, "the sweep must cross at least one blended regime transition");
  }

  /**
   * Builds a fully initialized transient section for source evaluation.
   *
   * @param position axial coordinate, in m
   * @param superficialGas superficial gas velocity, in m/s
   * @return initialized two-fluid section
   */
  private TwoFluidSection transientSection(double position, double superficialGas) {
    TwoFluidSection section = new TwoFluidSection(position, 100.0, SMALL_LINE_DIAMETER, 0.0);
    section.setPressure(50.0e5);
    section.setTemperature(300.0);
    section.setGasDensity(40.0);
    section.setOilDensity(RHO_L);
    section.setWaterDensity(1000.0);
    section.setLiquidDensity(RHO_L);
    section.setGasViscosity(1.0e-5);
    section.setOilViscosity(MU_L);
    section.setWaterViscosity(1.0e-3);
    section.setLiquidViscosity(MU_L);
    section.setGasSoundSpeed(300.0);
    section.setLiquidSoundSpeed(1200.0);
    section.setGasEnthalpy(1.0e5);
    section.setLiquidEnthalpy(5.0e4);
    section.setSurfaceTension(SIGMA);
    section.setRoughness(4.6e-5);
    section.setGasHoldup(ALPHA_G);
    section.setLiquidHoldup(ALPHA_L);
    section.setOilHoldup(ALPHA_L);
    section.setWaterHoldup(0.0);
    section.setWaterCut(0.0);
    section.setOilFractionInLiquid(1.0);
    section.setGasVelocity(superficialGas / ALPHA_G);
    section.setLiquidVelocity(0.035 / ALPHA_L);
    section.setOilVelocity(0.035 / ALPHA_L);
    section.setWaterVelocity(0.035 / ALPHA_L);
    section.updateConservativeVariables();
    section.updateDerivedQuantities();
    return section;
  }

  /**
   * Asserts equality with a relative tolerance suitable for closure-source calculations.
   *
   * @param expected expected value
   * @param actual actual value
   */
  private void assertRelativeEquals(double expected, double actual) {
    double tolerance = 1.0e-12 * Math.max(1.0, Math.abs(expected));
    Assertions.assertEquals(expected, actual, tolerance);
  }

  /**
   * Resolves the weight of each closure family, treating a single regime as weight one.
   *
   * <p>
   * Smooth and wavy stratified flow share one closure, so a swap between them is not a closure discontinuity and must
   * not be counted as one.
   * </p>
   *
   * @param section a classified section
   * @return weight per closure family
   */
  private Map<FlowRegime, Double> weightsOf(PipeSection section) {
    Map<FlowRegime, Double> raw = section.getRegimeWeights();
    if (raw == null) {
      raw = new EnumMap<FlowRegime, Double>(FlowRegime.class);
      raw.put(section.getFlowRegime(), 1.0);
    }

    Map<FlowRegime, Double> byClosure = new EnumMap<FlowRegime, Double>(FlowRegime.class);
    for (Map.Entry<FlowRegime, Double> entry : raw.entrySet()) {
      FlowRegime key = entry.getKey() == FlowRegime.STRATIFIED_WAVY ? FlowRegime.STRATIFIED_SMOOTH : entry.getKey();
      Double existing = byClosure.get(key);
      byClosure.put(key, entry.getValue() + (existing == null ? 0.0 : existing));
    }
    return byClosure;
  }
}
