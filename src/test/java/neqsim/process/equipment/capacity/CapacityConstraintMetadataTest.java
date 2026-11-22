package neqsim.process.equipment.capacity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.capacity.CapacityConstraint.ApplicableLimitRole;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintSource;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType;

/**
 * Tests evidence-quality and validity metadata on {@link CapacityConstraint}.
 */
class CapacityConstraintMetadataTest {

  /** Verifies explicit unset semantics for legacy and default constraints. */
  @Test
  void metadataIsUnsetByDefault() {
    CapacityConstraint constraint = new CapacityConstraint("gasFlow", "kg/h", ConstraintType.HARD);

    assertFalse(constraint.hasConfidence());
    assertTrue(Double.isNaN(constraint.getConfidence()));
    assertFalse(constraint.hasValidityRange());
    assertTrue(Double.isNaN(constraint.getValidityMinimum()));
    assertTrue(Double.isNaN(constraint.getValidityMaximum()));
    assertFalse(constraint.isCurrentValueWithinValidityRange());
  }

  /** Verifies inclusive validity limits and fluent metadata assignment. */
  @Test
  void metadataPreservesEngineeringBasis() {
    CapacityConstraint constraint = new CapacityConstraint("gasFlow", "kg/h", ConstraintType.HARD)
        .setDesignValue(12000.0).setCurrentValue(10000.0).setDataSource("installedDataSheet").setConfidence(0.95)
        .setValidityRange(8000.0, 12000.0);

    assertEquals("installedDataSheet", constraint.getDataSource());
    assertTrue(constraint.hasConfidence());
    assertEquals(0.95, constraint.getConfidence(), 0.0);
    assertTrue(constraint.hasValidityRange());
    assertEquals(8000.0, constraint.getValidityMinimum(), 0.0);
    assertEquals(12000.0, constraint.getValidityMaximum(), 0.0);
    assertTrue(constraint.isCurrentValueWithinValidityRange());

    constraint.setCurrentValue(8000.0);
    assertTrue(constraint.isCurrentValueWithinValidityRange());
    constraint.setCurrentValue(12000.0);
    assertTrue(constraint.isCurrentValueWithinValidityRange());
    constraint.setCurrentValue(12000.1);
    assertFalse(constraint.isCurrentValueWithinValidityRange());
  }

  /** Verifies metadata never changes the existing utilization or feasibility calculation. */
  @Test
  void validityMetadataDoesNotChangeUtilization() {
    CapacityConstraint constraint = new CapacityConstraint("gasFlow", "kg/h", ConstraintType.HARD)
        .setDesignValue(12000.0).setCurrentValue(13000.0);
    double baselineUtilization = constraint.getUtilization();

    constraint.setConfidence(0.25).setValidityRange(8000.0, 12000.0);

    assertFalse(constraint.isCurrentValueWithinValidityRange());
    assertEquals(baselineUtilization, constraint.getUtilization(), 0.0);
    assertTrue(constraint.isViolated());

    CapacityConstraint minimumConstraint = new CapacityConstraint("npshMargin", "m", ConstraintType.HARD)
        .setMinValue(45.0).setCurrentValue(50.0);
    double minimumBaselineUtilization = minimumConstraint.getUtilization();
    minimumConstraint.setConfidence(0.80).setValidityRange(40.0, 60.0);

    assertTrue(minimumConstraint.isCurrentValueWithinValidityRange());
    assertEquals(minimumBaselineUtilization, minimumConstraint.getUtilization(), 0.0);
    assertFalse(minimumConstraint.isViolated());
  }

  /** Verifies fail-fast validation of ambiguous confidence and validity metadata. */
  @Test
  void metadataRejectsInvalidValues() {
    final CapacityConstraint constraint = new CapacityConstraint("gasFlow", "kg/h", ConstraintType.HARD);

    assertThrows(IllegalArgumentException.class, () -> constraint.setConfidence(Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> constraint.setConfidence(Double.POSITIVE_INFINITY));
    assertThrows(IllegalArgumentException.class, () -> constraint.setConfidence(-0.01));
    assertThrows(IllegalArgumentException.class, () -> constraint.setConfidence(1.01));
    assertThrows(IllegalArgumentException.class, () -> constraint.setValidityRange(Double.NEGATIVE_INFINITY, 1.0));
    assertThrows(IllegalArgumentException.class, () -> constraint.setValidityRange(0.0, Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> constraint.setValidityRange(2.0, 1.0));
  }

  /** Verifies serialized constraints retain metadata while remaining supplier-independent. */
  @Test
  void metadataSurvivesSerialization() throws Exception {
    CapacityConstraint constraint = new CapacityConstraint("gasFlow", "kg/h", ConstraintType.HARD)
        .setDesignValue(12000.0).setCurrentValue(10000.0).setConfidence(0.95).setValidityRange(8000.0, 12000.0)
        .setOperatingLimit(9000.0, ConstraintSource.USER_RULE, "operating procedure OP-17")
        .setOperatingLimitConfidence(0.85).setOperatingLimitValidityRange(7000.0, 10000.0);

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ObjectOutputStream output = new ObjectOutputStream(bytes);
    output.writeObject(constraint);
    output.close();

    ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    CapacityConstraint restored = (CapacityConstraint) input.readObject();
    input.close();

    assertTrue(restored.hasConfidence());
    assertEquals(0.95, restored.getConfidence(), 0.0);
    assertTrue(restored.hasValidityRange());
    assertEquals(8000.0, restored.getValidityMinimum(), 0.0);
    assertEquals(12000.0, restored.getValidityMaximum(), 0.0);
    assertTrue(restored.isCurrentValueWithinValidityRange());
    assertTrue(restored.hasOperatingLimit());
    assertEquals(9000.0, restored.getApplicableLimit(), 0.0);
    assertEquals(ConstraintSource.USER_RULE, restored.getApplicableLimitSource());
    assertEquals("operating procedure OP-17", restored.getApplicableLimitSourceReference());
    assertEquals(0.85, restored.getApplicableLimitConfidence(), 0.0);
    assertTrue(restored.isCurrentValueWithinApplicableLimitValidityRange());
  }

  /** Verifies metadata can be cleared without changing the constraint definition. */
  @Test
  void metadataCanBeCleared() {
    CapacityConstraint constraint = new CapacityConstraint("gasFlow", "kg/h", ConstraintType.HARD)
        .setDesignValue(12000.0).setConfidence(0.95).setValidityRange(8000.0, 12000.0);

    constraint.clearConfidence().clearValidityRange();

    assertFalse(constraint.hasConfidence());
    assertTrue(Double.isNaN(constraint.getConfidence()));
    assertFalse(constraint.hasValidityRange());
    assertTrue(Double.isNaN(constraint.getValidityMinimum()));
    assertTrue(Double.isNaN(constraint.getValidityMaximum()));
    assertEquals(12000.0, constraint.getDesignValue(), 0.0);
  }

  /** Verifies an operating override preserves the qualified design/default basis. */
  @Test
  void operatingLimitPreservesDesignAndUsesSeparateEvidence() {
    CapacityConstraint constraint = new CapacityConstraint("gasFlow", "kg/h", ConstraintType.HARD)
        .setDesignValue(12000.0).setCurrentValue(10000.0)
        .setSource(ConstraintSource.VENDOR_DATASHEET, "vendor sheet rev 4").setDataSource("installedDataSheet")
        .setConfidence(0.95).setValidityRange(8000.0, 12000.0);

    constraint.setOperatingLimit(9000.0, ConstraintSource.USER_RULE, "operating procedure OP-17")
        .setOperatingLimitConfidence(0.85).setOperatingLimitValidityRange(7000.0, 10000.0);

    assertEquals(12000.0, constraint.getDesignValue(), 0.0);
    assertEquals(9000.0, constraint.getApplicableLimit(), 0.0);
    assertEquals(ApplicableLimitRole.CONFIGURED_OPERATING, constraint.getApplicableLimitRole());
    assertEquals(ConstraintSource.VENDOR_DATASHEET, constraint.getSource());
    assertEquals("vendor sheet rev 4", constraint.getSourceReference());
    assertEquals(ConstraintSource.USER_RULE, constraint.getApplicableLimitSource());
    assertEquals("operating procedure OP-17", constraint.getApplicableLimitSourceReference());
    assertEquals(0.85, constraint.getApplicableLimitConfidence(), 0.0);
    assertTrue(constraint.isCurrentValueWithinApplicableLimitValidityRange());
    assertTrue(constraint.isViolated());

    constraint.clearOperatingLimit();

    assertFalse(constraint.hasOperatingLimit());
    assertEquals(12000.0, constraint.getApplicableLimit(), 0.0);
    assertEquals(ApplicableLimitRole.DESIGN_RATED, constraint.getApplicableLimitRole());
    assertEquals(ConstraintSource.VENDOR_DATASHEET, constraint.getApplicableLimitSource());
    assertEquals(0.95, constraint.getApplicableLimitConfidence(), 0.0);
    assertFalse(constraint.isViolated());
  }

  /** Verifies operating metadata fails closed when no valid operating limit exists. */
  @Test
  void operatingLimitRejectsInvalidOrUnboundMetadata() {
    final CapacityConstraint constraint = new CapacityConstraint("gasFlow", "kg/h", ConstraintType.HARD)
        .setDesignValue(12000.0);

    assertThrows(IllegalArgumentException.class,
        () -> constraint.setOperatingLimit(Double.NaN, ConstraintSource.USER_RULE, "invalid"));
    assertThrows(IllegalArgumentException.class,
        () -> constraint.setOperatingLimit(0.0, ConstraintSource.USER_RULE, "invalid"));
    assertThrows(IllegalStateException.class, () -> constraint.setOperatingLimitConfidence(0.8));
    assertThrows(IllegalStateException.class, () -> constraint.setOperatingLimitValidityRange(1.0, 2.0));
  }

  /** Verifies an operating override preserves minimum-directed constraint semantics. */
  @Test
  void operatingLimitUsesMinimumConstraintDirection() {
    CapacityConstraint minimum = new CapacityConstraint("residenceTime", "min", ConstraintType.HARD).setMinValue(4.0)
        .setCurrentValue(5.0).setOperatingLimit(4.5, ConstraintSource.USER_RULE, "winter operating rule");

    assertTrue(minimum.isMinimumConstraint());
    assertEquals(0.9, minimum.getUtilization(), 1.0e-12);
    assertFalse(minimum.isViolated());

    minimum.setCurrentValue(4.0);
    assertEquals(4.5 / 4.0, minimum.getUtilization(), 1.0e-12);
    assertTrue(minimum.isViolated());
  }
}
