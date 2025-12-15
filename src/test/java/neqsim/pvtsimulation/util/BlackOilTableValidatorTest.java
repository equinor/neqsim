package neqsim.pvtsimulation.util;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import neqsim.pvtsimulation.util.BlackOilTableValidator.ValidationResult;

/**
 * Unit tests for BlackOilTableValidator.
 *
 * @author ESOL
 */
public class BlackOilTableValidatorTest {

  @Test
  void testValidBoRsTable() {
    // Valid black-oil table (decreasing with pressure)
    double[] pressures = {300.0, 250.0, 200.0, 150.0, 100.0};
    double[] Bo = {1.45, 1.40, 1.35, 1.30, 1.25};
    double[] Rs = {200.0, 170.0, 140.0, 110.0, 80.0};

    ValidationResult result = BlackOilTableValidator.validate(pressures, Bo, Rs, null, null, null);

    assertTrue(result.isValid(), "Valid table should pass validation");
    assertFalse(result.hasWarnings(), "Valid table should have no warnings");
    assertTrue(result.getReport().contains("PASS"));
  }

  @Test
  void testNonMonotonicBo() {
    // Bo with non-monotonic behavior
    double[] pressures = {300.0, 250.0, 200.0, 150.0, 100.0};
    double[] Bo = {1.45, 1.40, 1.42, 1.30, 1.25}; // Jump at index 2
    double[] Rs = {200.0, 170.0, 140.0, 110.0, 80.0};

    ValidationResult result = BlackOilTableValidator.validate(pressures, Bo, Rs, null, null, null);

    assertFalse(result.isValid(), "Non-monotonic Bo should fail validation");
    assertTrue(result.getReport().contains("non-monotonic"));
  }

  @Test
  void testNonMonotonicRs() {
    // Rs with non-monotonic behavior
    double[] pressures = {300.0, 250.0, 200.0, 150.0, 100.0};
    double[] Bo = {1.45, 1.40, 1.35, 1.30, 1.25};
    double[] Rs = {200.0, 170.0, 175.0, 110.0, 80.0}; // Jump at index 2

    ValidationResult result = BlackOilTableValidator.validate(pressures, Bo, Rs, null, null, null);

    assertFalse(result.isValid(), "Non-monotonic Rs should fail validation");
    assertTrue(result.getReport().contains("non-monotonic"));
  }

  @Test
  void testNegativeBo() {
    double[] pressures = {300.0, 250.0, 200.0};
    double[] Bo = {1.45, -0.5, 1.35}; // Negative value
    double[] Rs = {200.0, 170.0, 140.0};

    ValidationResult result = BlackOilTableValidator.validate(pressures, Bo, Rs, null, null, null);

    assertFalse(result.isValid());
    assertTrue(result.getReport().contains("non-positive"));
  }

  @Test
  void testNegativeRs() {
    double[] pressures = {300.0, 250.0, 200.0};
    double[] Bo = {1.45, 1.40, 1.35};
    double[] Rs = {200.0, -10.0, 140.0}; // Negative value

    ValidationResult result = BlackOilTableValidator.validate(pressures, Bo, Rs, null, null, null);

    assertFalse(result.isValid());
    assertTrue(result.getReport().contains("negative"));
  }

  @Test
  void testValidBgTable() {
    // Bg should increase with decreasing pressure
    double[] pressures = {300.0, 250.0, 200.0, 150.0, 100.0};
    double[] Bg = {0.005, 0.006, 0.008, 0.010, 0.015};

    ValidationResult result =
        BlackOilTableValidator.validate(pressures, null, null, Bg, null, null);

    assertTrue(result.isValid());
    assertTrue(result.getReport().contains("Bg monotonicity: PASS"));
  }

  @Test
  void testNonMonotonicBg() {
    // Bg decreasing (wrong direction)
    double[] pressures = {300.0, 250.0, 200.0, 150.0, 100.0};
    double[] Bg = {0.015, 0.010, 0.008, 0.006, 0.005}; // Wrong direction

    ValidationResult result =
        BlackOilTableValidator.validate(pressures, null, null, Bg, null, null);

    assertTrue(result.hasWarnings());
    assertTrue(result.getReport().contains("non-monotonic"));
  }

  @Test
  void testValidOilViscosity() {
    // Oil viscosity typically increases as gas liberates
    double[] pressures = {300.0, 250.0, 200.0, 150.0, 100.0};
    double[] oilVisc = {0.5, 0.6, 0.7, 0.9, 1.2};

    ValidationResult result =
        BlackOilTableValidator.validate(pressures, null, null, null, oilVisc, null);

    assertTrue(result.isValid());
  }

  @Test
  void testNonPositiveOilViscosity() {
    double[] pressures = {300.0, 250.0, 200.0};
    double[] oilVisc = {0.5, 0.0, 0.7}; // Zero value

    ValidationResult result =
        BlackOilTableValidator.validate(pressures, null, null, null, oilVisc, null);

    assertFalse(result.isValid());
    assertTrue(result.getReport().contains("non-positive"));
  }

  @Test
  void testGasViscosityValidation() {
    double[] pressures = {300.0, 250.0, 200.0, 150.0};
    double[] gasVisc = {0.015, 0.014, 0.013, 0.012};

    ValidationResult result =
        BlackOilTableValidator.validate(pressures, null, null, null, null, gasVisc);

    assertTrue(result.isValid());
    assertTrue(result.getReport().contains("PASS"));
  }

  @Test
  void testNegativeGasViscosity() {
    double[] pressures = {300.0, 250.0, 200.0};
    double[] gasVisc = {0.015, -0.01, 0.013}; // Negative value

    ValidationResult result =
        BlackOilTableValidator.validate(pressures, null, null, null, null, gasVisc);

    assertFalse(result.isValid());
    assertTrue(result.getReport().contains("non-positive"));
  }

  @Test
  void testNullPressures() {
    double[] Bo = {1.45, 1.40, 1.35};

    ValidationResult result = BlackOilTableValidator.validate(null, Bo, null, null, null, null);

    assertFalse(result.isValid());
    assertTrue(result.getReport().contains("null or empty"));
  }

  @Test
  void testEmptyPressures() {
    double[] pressures = {};
    double[] Bo = {};

    ValidationResult result =
        BlackOilTableValidator.validate(pressures, Bo, null, null, null, null);

    assertFalse(result.isValid());
    assertTrue(result.getReport().contains("null or empty"));
  }

  @Test
  void testCompleteValidation() {
    // Complete black-oil table
    double[] pressures = {300.0, 250.0, 200.0, 150.0, 100.0};
    double[] Bo = {1.45, 1.40, 1.35, 1.30, 1.25};
    double[] Rs = {200.0, 170.0, 140.0, 110.0, 80.0};
    double[] Bg = {0.005, 0.006, 0.008, 0.010, 0.015};
    double[] oilVisc = {0.5, 0.6, 0.7, 0.9, 1.2};
    double[] gasVisc = {0.015, 0.014, 0.013, 0.012, 0.011};

    ValidationResult result =
        BlackOilTableValidator.validate(pressures, Bo, Rs, Bg, oilVisc, gasVisc);

    assertTrue(result.isValid());
    String report = result.getReport();
    assertTrue(report.contains("Bo monotonicity: PASS"));
    assertTrue(report.contains("Rs monotonicity: PASS"));
    assertTrue(report.contains("Bg monotonicity: PASS"));
    assertTrue(report.contains("Bo-Rs consistency check: PASS"));
  }

  @Test
  void testBoLessThanOne() {
    double[] pressures = {300.0, 250.0, 200.0};
    double[] Bo = {0.95, 0.90, 0.85}; // Bo < 1 (unusual)

    ValidationResult result =
        BlackOilTableValidator.validate(pressures, Bo, null, null, null, null);

    assertTrue(result.hasWarnings());
    assertTrue(result.getReport().contains("less than 1.0"));
  }

  @Test
  void testInterpolation() {
    double[] pressures = {300.0, 200.0, 100.0};
    double[] Bo = {1.50, 1.30, 1.10};

    // Interpolate at 250 bar
    double interpolated = BlackOilTableValidator.interpolate(pressures, Bo, 250.0);
    assertEquals(1.40, interpolated, 0.001);

    // Interpolate at 150 bar
    double interpolated2 = BlackOilTableValidator.interpolate(pressures, Bo, 150.0);
    assertEquals(1.20, interpolated2, 0.001);

    // Interpolate at exact point
    double interpolated3 = BlackOilTableValidator.interpolate(pressures, Bo, 200.0);
    assertEquals(1.30, interpolated3, 0.001);
  }

  @Test
  void testInterpolationOutOfRange() {
    double[] pressures = {300.0, 200.0, 100.0};
    double[] Bo = {1.50, 1.30, 1.10};

    // Outside range should return NaN
    double result = BlackOilTableValidator.interpolate(pressures, Bo, 400.0);
    assertTrue(Double.isNaN(result));

    double result2 = BlackOilTableValidator.interpolate(pressures, Bo, 50.0);
    assertTrue(Double.isNaN(result2));
  }

  @Test
  void testInterpolationNullArrays() {
    double result = BlackOilTableValidator.interpolate(null, null, 100.0);
    assertTrue(Double.isNaN(result));
  }

  @Test
  void testValidationReport() {
    double[] pressures = {300.0, 250.0, 200.0};
    double[] Bo = {1.45, 1.40, 1.35};

    ValidationResult result =
        BlackOilTableValidator.validate(pressures, Bo, null, null, null, null);

    String report = result.getReport();
    assertTrue(report.contains("Black-Oil Table Validation Report"));
    assertTrue(report.contains("Status:"));
    assertTrue(report.contains("PASSED"));
  }

  @Test
  void testBoRsConsistencyCheck() {
    // Inconsistent Bo-Rs (Bo increases while Rs decreases)
    double[] pressures = {300.0, 250.0, 200.0, 150.0, 100.0};
    double[] Bo = {1.25, 1.30, 1.35, 1.40, 1.45}; // Increasing (wrong for decreasing pressure)
    double[] Rs = {200.0, 170.0, 140.0, 110.0, 80.0}; // Decreasing (correct)

    ValidationResult result = BlackOilTableValidator.validate(pressures, Bo, Rs, null, null, null);

    // Should fail due to Bo monotonicity
    assertFalse(result.isValid());
  }
}
