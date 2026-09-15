package neqsim.process.mechanicaldesign.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Absolute-value tests for {@link DnvStF101PipelineDesignCalculator}.
 *
 * <p>
 * The existing kernel tests only compare utilizations against each other, so a scaling error in a resistance formula
 * passes them. These tests pin the pressure-containment resistance to a hand-calculable DNV-ST-F101 value.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class DnvStF101PipelineDesignCalculatorTest {
  /** 30 inch outside diameter in metres. */
  private static final double OD_M = 0.762;

  /** Nominal wall thickness in metres. */
  private static final double WT_M = 0.028;

  /** Corrosion allowance in metres. */
  private static final double CA_M = 0.003;

  /** Mill tolerance as a fraction of the nominal wall thickness. */
  private static final double FAB_TOL = 0.125;

  /** Specified minimum yield strength in MPa. */
  private static final double SMYS = 450.0;

  /** Specified minimum tensile strength in MPa. */
  private static final double SMTS = 535.0;

  /**
   * Builds a representative offshore trunkline input.
   *
   * @return design input
   */
  private DnvStF101PipelineDesignInput input() {
    return DnvStF101PipelineDesignInput.builder().safetyClass(DnvStF101PipelineDesignInput.SafetyClass.MEDIUM)
        .fabricationRoute(DnvStF101PipelineDesignInput.FabricationRoute.SEAMLESS).geometry(OD_M, WT_M, CA_M)
        .fabrication(FAB_TOL, 0.005, 0.03, 1.0).material(SMYS, SMTS, 207000.0, 0.30)
        .resistanceFactors(1.0, 1.0, 0.96, 1.15).pressures(15.0, 16.5, 3.0, 0.2, 18.5, 3.0)
        .designLoads(1000.0, 1500.0, 250.0).installationStrains(0.002, 0.005, 0.003, 0.025)
        .fatigueCurve(12.0, 3.0, 1.0, 3.0).addFatigueBin(60.0, 100000.0).build();
  }

  /**
   * Pressure containment must follow DNV-ST-F101 Sec.5, p_b(t) = 2t/(D-t) * f_cb * 2/sqrt(3).
   *
   * <p>
   * An earlier implementation omitted the factor two and reported half the correct burst resistance, which made every
   * realistic deep-water design fail the screening check.
   * </p>
   */
  @Test
  void burstResistanceFollowsTheStandardFormula() {
    DnvStF101PipelineDesignInput input = input();
    DnvStF101PipelineAssessment assessment = DnvStF101PipelineDesignCalculator.calculate(input);

    double characteristicThickness = WT_M * (1.0 - FAB_TOL) - CA_M;
    double characteristicStrength = Math.min(SMYS * 1.0, SMTS * 0.96);
    double gamma = 1.15 * input.getSafetyClass().getResistanceFactor();
    double expected = 2.0 * characteristicThickness * characteristicStrength / (OD_M - characteristicThickness) * 2.0
        / Math.sqrt(3.0) / gamma;

    assertEquals(characteristicThickness, assessment.getCharacteristicWallThicknessM(), 1.0e-12);
    assertEquals(expected, assessment.getBurstResistanceMPa(), 1.0e-9);
    assertTrue(assessment.getBurstResistanceMPa() > 20.0,
        "a 28 mm X65 30 inch pipe must resist well above 20 MPa; got " + assessment.getBurstResistanceMPa());
  }

  /** A realistic offshore trunkline must pass pressure containment rather than fail by a factor. */
  @Test
  void realisticTrunklineDesignPassesPressureContainment() {
    DnvStF101PipelineAssessment assessment = DnvStF101PipelineDesignCalculator.calculate(input());

    for (DnvStF101LimitStateCheck check : assessment.getChecks()) {
      if (check.getLimitState() == DnvStF101LimitStateCheck.LimitState.SYSTEM_TEST_PRESSURE_CONTAINMENT
          || check.getLimitState() == DnvStF101LimitStateCheck.LimitState.INCIDENTAL_PRESSURE_CONTAINMENT
          || check.getLimitState() == DnvStF101LimitStateCheck.LimitState.OPERATING_PRESSURE_CONTAINMENT) {
        assertTrue(check.getUtilization() < 1.0,
            check.getLimitState() + " utilization must be below unity, got " + check.getUtilization());
      }
    }
  }

  /** Thicker wall must raise the burst resistance monotonically. */
  @Test
  void thickerWallRaisesBurstResistance() {
    DnvStF101PipelineAssessment thin = DnvStF101PipelineDesignCalculator
        .calculate(DnvStF101PipelineDesignInput.builder().safetyClass(DnvStF101PipelineDesignInput.SafetyClass.MEDIUM)
            .fabricationRoute(DnvStF101PipelineDesignInput.FabricationRoute.SEAMLESS).geometry(OD_M, 0.020, CA_M)
            .fabrication(FAB_TOL, 0.005, 0.03, 1.0).material(SMYS, SMTS, 207000.0, 0.30)
            .resistanceFactors(1.0, 1.0, 0.96, 1.15).pressures(15.0, 16.5, 3.0, 0.2, 18.5, 3.0)
            .designLoads(1000.0, 1500.0, 250.0).installationStrains(0.002, 0.005, 0.003, 0.025)
            .fatigueCurve(12.0, 3.0, 1.0, 3.0).addFatigueBin(60.0, 100000.0).build());
    DnvStF101PipelineAssessment thick = DnvStF101PipelineDesignCalculator.calculate(input());

    assertTrue(thick.getBurstResistanceMPa() > thin.getBurstResistanceMPa());
    assertTrue(thick.getCollapseResistanceMPa() > thin.getCollapseResistanceMPa());
  }
}
