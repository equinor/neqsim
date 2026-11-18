package neqsim.process.safety.depressurization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import neqsim.process.safety.depressurization.DynamicPsvSizingStudy.SizingComparison;
import neqsim.process.safety.depressurization.DynamicPsvSizingStudy.SizingStatus;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for {@link DynamicPsvSizingStudy}.
 *
 * <p>
 * Validates the dynamic-versus-steady relief-area comparison: accounting for the vessel's thermal inertia lets the
 * pressure overshoot lag, so the dynamically required orifice is smaller than the steady API 520/521 fire-case area and
 * the resulting oversizing ratio exceeds unity.
 *
 * @author ESOL
 * @version 1.0
 */
public class DynamicPsvSizingStudyTest {

  /** Builds a dry gas blowdown fluid. */
  private static SystemInterface gasFluid() {
    SystemInterface gas = new SystemSrkEos(350.0, 50.0);
    gas.addComponent("methane", 0.85);
    gas.addComponent("ethane", 0.10);
    gas.addComponent("propane", 0.05);
    gas.setMixingRule("classic");
    return gas;
  }

  /** The dynamic area must be smaller than the steady area, giving oversizing &gt; 1. */
  @Test
  public void dynamicAreaSmallerThanSteady() {
    DynamicPsvSizingStudy study = new DynamicPsvSizingStudy(gasFluid(), 5.0, 500000.0, 50.0e5, 0.21, 1.0e5);
    DynamicPsvSizingStudy.SizingComparison result = study.run();

    assertTrue(result.steadyRequiredAreaM2 > 0.0, "Steady area should be positive");
    assertTrue(result.dynamicRequiredAreaM2 > 0.0, "Dynamic area should be positive");
    assertTrue(result.dynamicRequiredAreaM2 < result.steadyRequiredAreaM2,
        "Dynamic sizing should require a smaller orifice than steady sizing");
    assertTrue(result.oversizingRatio > 1.0, "Steady sizing should be conservative");
  }

  /** A very small discharge coefficient leaves the expanded upper bound above the pressure limit. */
  @Test
  void overpressureAtExpandedBoundHasNoRequiredArea() {
    SizingComparison result = new DynamicPsvSizingStudy(gasFluid(), 5.0, 500000.0, 50e5, 0.21, 1e5)
        .setDischargeCoefficient(1e-6).setMaxTime(120.0).run();
    assertEquals(SizingStatus.NO_FEASIBLE_AREA_IN_SEARCH, result.status);
    assertEquals(10.0 * result.steadyRequiredAreaM2, result.attemptedAreaM2);
    assertTrue(result.peakDynamicPressurePa > result.allowablePressurePa);
    assertEquals(120.0, result.elapsedSeconds);
    assertTrue(Double.isNaN(result.dynamicRequiredAreaM2));
    assertTrue(Double.isNaN(result.oversizingRatio));
  }

  /** An unresolved inventory never qualifies on its initial pressure. */
  @Test
  void numericalCutoffReturnsIncompleteTrialEvidence() {
    SizingComparison result = new DynamicPsvSizingStudy(gasFluid(), 1e-12, 500000.0, 50e5, 0.21, 1e5).setMaxTime(120.0)
        .run();
    assertEquals(SizingStatus.INCOMPLETE, result.status);
    assertEquals(0.0, result.elapsedSeconds);
    assertTrue(result.terminationReason.contains("resolution"));
    assertTrue(result.attemptedAreaM2 > 0.0);
    assertTrue(Double.isNaN(result.dynamicRequiredAreaM2));
  }

  /** The last partial integration step can set the actual peak. */
  @Test
  void pressureAtFinalPartialStepCounts() {
    SizingComparison result = new DynamicPsvSizingStudy(gasFluid(), 5.0, 500000.0, 50e5, 0.21, 1e5).setTimeStep(120.0)
        .setMaxTime(80.0).run();
    assertEquals(SizingStatus.NO_FEASIBLE_AREA_IN_SEARCH, result.status);
    assertEquals(80.0, result.elapsedSeconds);
    assertTrue(result.peakDynamicPressurePa > result.allowablePressurePa);
  }

  /** Both integration grids must produce a full, pressure-compliant candidate. */
  @Test
  void passingAreaIsStableOnTimeStepRefinement() {
    DynamicPsvSizingStudy study = new DynamicPsvSizingStudy(gasFluid(), 5.0, 500000.0, 50e5, 0.21, 1e5)
        .setMaxTime(120.0);
    SizingComparison coarse = study.setTimeStep(0.5).run();
    SizingComparison fine = study.setTimeStep(0.25).run();
    for (SizingComparison result : new SizingComparison[] {coarse, fine}) {
      assertEquals(SizingStatus.FEASIBLE, result.status);
      assertEquals(120.0, result.elapsedSeconds);
      assertEquals(result.attemptedAreaM2, result.dynamicRequiredAreaM2);
      assertTrue(result.peakDynamicPressurePa <= result.allowablePressurePa);
    }
    assertEquals(0.25, fine.timeStepSeconds);
    assertEquals(fine.dynamicRequiredAreaM2, coarse.dynamicRequiredAreaM2, 0.01 * fine.dynamicRequiredAreaM2);
  }

  /** The allowable pressure must equal set pressure times (1 + overpressure). */
  @Test
  public void allowablePressureMatchesDefinition() {
    DynamicPsvSizingStudy study = new DynamicPsvSizingStudy(gasFluid(), 5.0, 500000.0, 50.0e5, 0.21, 1.0e5);
    DynamicPsvSizingStudy.SizingComparison result = study.run();
    assertEquals(50.0e5 * 1.21, result.allowablePressurePa, 50.0e5 * 1.21 * 1.0e-9);
  }

  /** A null fluid must be rejected at construction. */
  @Test
  public void rejectsNullFluid() {
    assertThrows(IllegalArgumentException.class,
        () -> new DynamicPsvSizingStudy(null, 5.0, 500000.0, 50.0e5, 0.21, 1.0e5));
  }

  /** Non-physical sizing inputs must be rejected. */
  @Test
  public void rejectsInvalidInputs() {
    assertThrows(IllegalArgumentException.class,
        () -> new DynamicPsvSizingStudy(gasFluid(), -1.0, 500000.0, 50.0e5, 0.21, 1.0e5));
    assertThrows(IllegalArgumentException.class,
        () -> new DynamicPsvSizingStudy(gasFluid(), 5.0, 500000.0, 50.0e5, 0.21, 1.0e5).setBlowdownFraction(1.5));
  }

  /**
   * Reproduces the overpressure-protection application result of Andreasen (2026), J. Loss Prev. Process Ind. 103,
   * 106088, &sect;4.1: a transient PSV-cycling fire simulation needs a smaller orifice than the steady-state API STD
   * 521 fire-case equation, so API 521 sizing is conservative. The paper reports the largest oversizing in the
   * low-pressure, small-diameter corner of its 100-case parameter study.
   */
  @Nested
  public class Api521OversizingStudy {
    /**
     * A low-pressure, small vessel (the worst-oversizing corner) must show steady sizing clearly above the dynamic
     * requirement, within the published oversizing band.
     */
    @Test
    public void lowPressureSmallVesselIsStronglyOversized() {
      DynamicPsvSizingStudy study = new DynamicPsvSizingStudy(gasFluid(), 1.0, 150000.0, 11.0e5, 0.21, 1.0e5);
      DynamicPsvSizingStudy.SizingComparison result = study.run();
      assertTrue(result.oversizingRatio > 1.1,
          "API 521 should be clearly conservative at low pressure, ratio = " + result.oversizingRatio);
      assertTrue(result.oversizingRatio < 6.0,
          "oversizing should stay within the published band, ratio = " + result.oversizingRatio);
    }
  }
}
