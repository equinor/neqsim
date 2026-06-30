package neqsim.process.safety.depressurization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
}
