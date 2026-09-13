package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;
import neqsim.process.equipment.pipeline.twophasepipe.closure.InterfacialFriction;
import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;

/** Independently checks the inclined thin-film eligibility constraint against liquid inventory and flow direction. */
class FlowRegimeInclinedFilmBridgingTest {
  private static final double GAS_DENSITY = 1.382;
  private static final double LIQUID_DENSITY = 851.26;
  private static final double SURFACE_TENSION = 0.02;

  @Test
  void liftedGasCannotSelectAnnularWhenTheLiquidCanBridgeTheCore() {
    FlowRegimeDetector detector = enabledDetector();
    double critical = criticalGasVelocity();
    for (double liquidHoldup : new double[] { 0.24, 0.4, 0.593863293446687, 0.8 }) {
      for (double multiplier : new double[] { 1.0 - 1.0e-8, 1.0 + 1.0e-8, 2.0 }) {
        TwoFluidSection cell = section(liquidHoldup, critical * multiplier, -2.05, Math.PI / 2.0);
        double[] initial = cell.getStateVector();
        assertEquals(FlowRegime.SLUG, detector.detectFlowRegime(cell));
        assertEquals(FlowRegime.SLUG, detector.classify(cell));
        assertArrayEquals(initial, cell.getStateVector(), 0.0);
      }
    }
  }

  @Test
  void countercurrentCapturedRiserStateHasContinuousDragAcrossTheGasLiftThreshold() {
    FlowRegimeDetector detector = enabledDetector();
    TwoFluidSection below = section(0.593863293446687, criticalGasVelocity() * (1.0 - 1.0e-8), -2.05, Math.PI / 2.0);
    TwoFluidSection above = section(0.593863293446687, criticalGasVelocity() * (1.0 + 1.0e-8), -2.05, Math.PI / 2.0);
    assertEquals(FlowRegime.SLUG, detector.classify(below));
    assertEquals(FlowRegime.SLUG, detector.classify(above));
    double first = drag(below);
    double second = drag(above);
    assertTrue(first > 0.0 && Double.isFinite(second));
    assertEquals(first, second, Math.abs(first) * 1.0e-6,
        "The gas-lift threshold must not introduce a thin-film drag jump in a thick-liquid state");
  }

  @Test
  void thinFilmRetainsTheOriginalGasLiftBoundaryAndZeroInventoryLimits() {
    FlowRegimeDetector detector = enabledDetector();
    assertEquals(FlowRegime.SLUG,
        detector.classify(section(0.1, criticalGasVelocity() * (1.0 - 1.0e-8), 0.1, Math.PI / 2.0)));
    assertEquals(FlowRegime.ANNULAR,
        detector.classify(section(0.1, criticalGasVelocity() * (1.0 + 1.0e-8), 0.1, Math.PI / 2.0)));
    TwoFluidSection gas = section(0.0, 1.0, 0.0, Math.PI / 2.0);
    assertEquals(FlowRegime.SINGLE_PHASE_GAS, detector.classify(gas));
    TwoFluidSection liquid = section(1.0, 0.0, 1.0, Math.PI / 2.0);
    assertEquals(FlowRegime.SINGLE_PHASE_LIQUID, detector.classify(liquid));
  }

  @Test
  void downwardAndReversedCoordinatesApplyTheSameInventoryConstraint() {
    FlowRegimeDetector detector = enabledDetector();
    for (double inclination : new double[] { -Math.PI / 2.0, -0.4, 0.4, Math.PI / 2.0 }) {
      TwoFluidSection forward = section(0.6, 1.2 * criticalGasVelocity(), 0.1, inclination);
      TwoFluidSection reversed = section(0.6, -1.2 * criticalGasVelocity(), -0.1, -inclination);
      FlowRegime forwardRegime = detector.classify(forward);
      assertTrue(forwardRegime != FlowRegime.ANNULAR);
      assertEquals(forwardRegime, detector.classify(reversed));
    }
  }

  @Test
  void compatibilityAndSerializationKeepTheConstraintExplicit() {
    FlowRegimeDetector detector = new FlowRegimeDetector();
    assertFalse(detector.isUseInclinedFilmBridgingCriterion());
    TwoFluidSection thick = section(0.6, 1.2 * criticalGasVelocity(), -2.05, Math.PI / 2.0);
    assertEquals(FlowRegime.ANNULAR, detector.classify(thick));
    detector.setUseInclinedFilmBridgingCriterion(true);
    FlowRegimeDetector copy = SerializationUtils.clone(detector);
    assertTrue(copy.isUseInclinedFilmBridgingCriterion());
    assertEquals(FlowRegime.SLUG, copy.classify(thick));
    copy.setUseInclinedFilmBridgingCriterion(false);
    assertEquals(FlowRegime.ANNULAR, copy.classify(thick));
  }

  @Test
  void horizontalAndMinimumSlipMapsRemainIndependent() {
    FlowRegimeDetector legacy = new FlowRegimeDetector();
    FlowRegimeDetector enabled = enabledDetector();
    for (double inclination : new double[] { -Math.toRadians(10.0), 0.0, Math.toRadians(10.0) }) {
      TwoFluidSection first = section(0.6, 1.2 * criticalGasVelocity(), 0.1, inclination);
      TwoFluidSection second = first.clone();
      assertEquals(legacy.classify(first), enabled.classify(second));
      assertEquals(first.getRegimeWeights(), second.getRegimeWeights());
    }
    legacy.setUseMinimumSlipCriterion(true);
    enabled.setUseMinimumSlipCriterion(true);
    TwoFluidSection first = section(0.6, 1.2 * criticalGasVelocity(), 0.1, Math.PI / 2.0);
    assertEquals(legacy.classify(first), enabled.classify(first.clone()));
  }

  private static FlowRegimeDetector enabledDetector() {
    FlowRegimeDetector detector = new FlowRegimeDetector();
    detector.setUseInclinedFilmBridgingCriterion(true);
    return detector;
  }

  private static double criticalGasVelocity() {
    return 3.1 * Math.pow(SURFACE_TENSION * 9.81 * (LIQUID_DENSITY - GAS_DENSITY) / (GAS_DENSITY * GAS_DENSITY), 0.25);
  }

  private static double drag(TwoFluidSection section) {
    return new InterfacialFriction().calcInterfacialForce(section.getFlowRegime(), section.getGasVelocity(),
        section.getLiquidVelocity(), section.getGasDensity(), section.getLiquidDensity(), section.getGasViscosity(),
        section.getLiquidViscosity(), section.getLiquidHoldup(), section.getDiameter(), section.getSurfaceTension());
  }

  private static TwoFluidSection section(double liquidHoldup, double gasFlux, double liquidFlux, double inclination) {
    TwoFluidSection cell = new TwoFluidSection(22.8046875, 2.171875, 0.0762, inclination);
    cell.setPressure(122296.42);
    cell.setTemperature(298.15);
    cell.setGasDensity(GAS_DENSITY);
    cell.setLiquidDensity(LIQUID_DENSITY);
    cell.setOilDensity(LIQUID_DENSITY);
    cell.setWaterDensity(1000.0);
    cell.setGasHoldup(1.0 - liquidHoldup);
    cell.setLiquidHoldup(liquidHoldup);
    cell.setOilHoldup(liquidHoldup);
    cell.setWaterHoldup(0.0);
    cell.setWaterCut(0.0);
    cell.setOilFractionInLiquid(1.0);
    cell.setGasVelocity(liquidHoldup < 1.0 ? gasFlux / (1.0 - liquidHoldup) : 0.0);
    cell.setLiquidVelocity(liquidHoldup > 0.0 ? liquidFlux / liquidHoldup : 0.0);
    cell.setOilVelocity(cell.getLiquidVelocity());
    cell.setWaterVelocity(0.0);
    cell.setGasViscosity(1.8138e-5);
    cell.setLiquidViscosity(0.0068465);
    cell.setOilViscosity(0.0068465);
    cell.setWaterViscosity(1.0e-3);
    cell.setSurfaceTension(SURFACE_TENSION);
    cell.setRoughness(1.5e-6);
    cell.updateConservativeVariables();
    cell.updateDerivedQuantitiesWithoutNormalization(gasFlux, liquidFlux);
    return cell;
  }
}
