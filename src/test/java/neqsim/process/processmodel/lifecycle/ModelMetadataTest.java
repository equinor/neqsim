package neqsim.process.processmodel.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for ModelMetadata lifecycle tracking.
 */
public class ModelMetadataTest {
  private ModelMetadata metadata;

  @BeforeEach
  void setUp() {
    metadata = new ModelMetadata();
    metadata.setAssetId("TEST-001");
    metadata.setAssetName("Test Process Model");
    metadata.setLifecyclePhase(ModelMetadata.LifecyclePhase.DESIGN);
  }

  @Test
  void testBasicProperties() {
    assertEquals("TEST-001", metadata.getAssetId());
    assertEquals("Test Process Model", metadata.getAssetName());
    assertEquals(ModelMetadata.LifecyclePhase.DESIGN, metadata.getLifecyclePhase());
  }

  @Test
  void testLifecyclePhaseTransition() {
    metadata.setLifecyclePhase(ModelMetadata.LifecyclePhase.CONCEPT);
    assertEquals(ModelMetadata.LifecyclePhase.CONCEPT, metadata.getLifecyclePhase());

    metadata.setLifecyclePhase(ModelMetadata.LifecyclePhase.DESIGN);
    assertEquals(ModelMetadata.LifecyclePhase.DESIGN, metadata.getLifecyclePhase());

    metadata.setLifecyclePhase(ModelMetadata.LifecyclePhase.COMMISSIONING);
    assertEquals(ModelMetadata.LifecyclePhase.COMMISSIONING, metadata.getLifecyclePhase());

    metadata.setLifecyclePhase(ModelMetadata.LifecyclePhase.OPERATION);
    assertEquals(ModelMetadata.LifecyclePhase.OPERATION, metadata.getLifecyclePhase());
  }

  @Test
  void testRecordValidation() {
    assertTrue(metadata.getValidationHistory().isEmpty());

    metadata.recordValidation("Initial validation passed", "REF-001");

    assertFalse(metadata.getValidationHistory().isEmpty());
    assertEquals(1, metadata.getValidationHistory().size());
  }

  @Test
  void testRecordModification() {
    // setUp already has one modification from setLifecyclePhase
    int initialCount = metadata.getModificationHistory().size();

    metadata.recordModification("Updated heat exchanger duty");

    assertEquals(initialCount + 1, metadata.getModificationHistory().size());
  }

  @Test
  void testRecordModificationWithAuthor() {
    int initialCount = metadata.getModificationHistory().size();

    metadata.recordModification("Updated compressor curve", "Jane Smith");

    assertEquals(initialCount + 1, metadata.getModificationHistory().size());
  }

  @Test
  void testCalibrationStatus() {
    assertEquals(ModelMetadata.CalibrationStatus.UNCALIBRATED, metadata.getCalibrationStatus());

    metadata.updateCalibration(ModelMetadata.CalibrationStatus.CALIBRATED, 0.02);

    assertEquals(ModelMetadata.CalibrationStatus.CALIBRATED, metadata.getCalibrationStatus());
    assertEquals(0.02, metadata.getCalibrationAccuracy(), 0.001);
    assertNotNull(metadata.getLastCalibrated());
  }

  @Test
  void testNeedsRevalidation() {
    // Never validated - should need revalidation
    assertTrue(metadata.needsRevalidation(90));

    // After validation
    metadata.recordValidation("Validated against field data", "WELL-TEST-001");

    // Just validated - should not need revalidation for 90 days
    assertFalse(metadata.needsRevalidation(90));
  }

  @Test
  void testLifecyclePhaseOrdering() {
    assertTrue(ModelMetadata.LifecyclePhase.CONCEPT.ordinal() < ModelMetadata.LifecyclePhase.DESIGN
        .ordinal());
    assertTrue(ModelMetadata.LifecyclePhase.DESIGN
        .ordinal() < ModelMetadata.LifecyclePhase.COMMISSIONING.ordinal());
    assertTrue(ModelMetadata.LifecyclePhase.COMMISSIONING
        .ordinal() < ModelMetadata.LifecyclePhase.OPERATION.ordinal());
    assertTrue(ModelMetadata.LifecyclePhase.OPERATION
        .ordinal() < ModelMetadata.LifecyclePhase.LATE_LIFE.ordinal());
    assertTrue(ModelMetadata.LifecyclePhase.LATE_LIFE
        .ordinal() < ModelMetadata.LifecyclePhase.ARCHIVED.ordinal());
  }

  @Test
  void testCalibrationStatusValues() {
    assertEquals(5, ModelMetadata.CalibrationStatus.values().length);

    assertNotNull(ModelMetadata.CalibrationStatus.UNCALIBRATED);
    assertNotNull(ModelMetadata.CalibrationStatus.CALIBRATED);
    assertNotNull(ModelMetadata.CalibrationStatus.IN_PROGRESS);
    assertNotNull(ModelMetadata.CalibrationStatus.FRESHLY_CALIBRATED);
    assertNotNull(ModelMetadata.CalibrationStatus.NEEDS_RECALIBRATION);
  }

  @Test
  void testMultipleValidations() {
    metadata.recordValidation("Failed initial check", "REF-001");
    metadata.recordValidation("Passed after fixes", "REF-002");
    metadata.recordValidation("Final approval", "REF-003");

    assertEquals(3, metadata.getValidationHistory().size());
  }

  @Test
  void testMultipleModifications() {
    int initialCount = metadata.getModificationHistory().size();

    metadata.recordModification("Added new stream");
    metadata.recordModification("Updated pressures", "Dev1");
    metadata.recordModification("Fixed heat balance");

    assertEquals(initialCount + 3, metadata.getModificationHistory().size());
  }

  @Test
  void testDaysSinceValidation() {
    // Never validated
    assertEquals(-1, metadata.getDaysSinceValidation());

    // After validation
    metadata.recordValidation("Test validation", "REF-001");
    assertTrue(metadata.getDaysSinceValidation() >= 0);
  }

  @Test
  void testDaysSinceCalibration() {
    // Never calibrated
    assertEquals(-1, metadata.getDaysSinceCalibration());

    // After calibration
    metadata.updateCalibration(ModelMetadata.CalibrationStatus.CALIBRATED, 0.05);
    assertTrue(metadata.getDaysSinceCalibration() >= 0);
  }

  @Test
  void testFacilityAndRegion() {
    metadata.setFacility("Platform Alpha");
    metadata.setRegion("North Sea");

    assertEquals("Platform Alpha", metadata.getFacility());
    assertEquals("North Sea", metadata.getRegion());
  }

  @Test
  void testResponsibleEngineer() {
    metadata.setResponsibleEngineer("john.doe@company.com");
    metadata.setResponsibleTeam("Process Engineering");

    assertEquals("john.doe@company.com", metadata.getResponsibleEngineer());
    assertEquals("Process Engineering", metadata.getResponsibleTeam());
  }
}
