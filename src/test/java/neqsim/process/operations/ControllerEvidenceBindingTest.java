package neqsim.process.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import neqsim.process.operations.ControllerEvidenceBinding.EvidenceQuality;
import neqsim.process.operations.ControllerEvidenceBinding.SignalEvidence;
import neqsim.util.validation.ValidationResult;

/**
 * Tests controller operational-evidence readiness and serialization.
 *
 * @author ESOL
 * @version 1.0
 */
class ControllerEvidenceBindingTest extends neqsim.NeqSimTest {
  private static final long EVALUATION_TIME = 200000L;

  /**
   * Command evidence cannot substitute for independently identified applied feedback.
   */
  @Test
  void rejectsCommandWithoutIndependentFeedback() {
    SignalEvidence command = signal("choke-a-command", "42.0", "%", EvidenceQuality.GOOD, "dataset:command");
    ControllerEvidenceBinding binding = completeBuilder().addActuator("choke-a", command,
        signal("choke-a-command", "41.5", "%", EvidenceQuality.GOOD, "dataset:feedback")).build();

    ValidationResult result = binding.validate(EVALUATION_TIME);

    assertFalse(result.isReady());
    assertTrue(result.getErrors().stream().anyMatch(issue -> "feedback".equals(issue.getCategory())));
  }

  /**
   * Questionable quality is rejected, while substituted-good evidence follows the explicit study policy.
   */
  @Test
  void appliesExplicitQualityPolicy() {
    ControllerEvidenceBinding questionable = completeBuilder()
        .addActuator("choke-a", signal("choke-a-command", "42.0", "%", EvidenceQuality.QUESTIONABLE, "dataset:q"),
            signal("choke-a-feedback", "41.5", "%", EvidenceQuality.GOOD, "dataset:f"))
        .build();
    assertFalse(questionable.validate(EVALUATION_TIME).isReady());

    ControllerEvidenceBinding substitutedRejected = completeBuilder()
        .addActuator("choke-a", signal("choke-a-command", "42.0", "%", EvidenceQuality.SUBSTITUTED_GOOD, "dataset:s"),
            signal("choke-a-feedback", "41.5", "%", EvidenceQuality.GOOD, "dataset:f"))
        .build();
    assertFalse(substitutedRejected.validate(EVALUATION_TIME).isReady());

    ControllerEvidenceBinding substitutedAccepted = completeBuilder().allowSubstitutedGood(true)
        .addActuator("choke-a", signal("choke-a-command", "42.0", "%", EvidenceQuality.SUBSTITUTED_GOOD, "dataset:s"),
            signal("choke-a-feedback", "41.5", "%", EvidenceQuality.GOOD, "dataset:f"))
        .build();
    assertTrue(substitutedAccepted.validate(EVALUATION_TIME).isReady());
  }

  /**
   * Command and applied-position feedback must use the same engineering unit.
   */
  @Test
  void rejectsCommandFeedbackUnitMismatch() {
    ControllerEvidenceBinding binding = completeBuilder()
        .addActuator("choke-a", signal("choke-a-command", "42.0", "%", EvidenceQuality.GOOD, "dataset:c"),
            signal("choke-a-feedback", "0.415", "fraction", EvidenceQuality.GOOD, "dataset:f"))
        .build();

    ValidationResult result = binding.validate(EVALUATION_TIME);

    assertFalse(result.isReady());
    assertTrue(result.getErrors().stream().anyMatch(issue -> "unit".equals(issue.getCategory())));
  }

  /**
   * Reported active count must agree with route states and fallback evidence must exist.
   */
  @Test
  void rejectsActiveCountDisagreementAndMissingFallback() {
    ControllerEvidenceBinding binding = ControllerEvidenceBinding.builder("coordinated-choke-control")
        .addActuator("choke-a", signal("choke-a-command", "42.0", "%", EvidenceQuality.GOOD, "dataset:c"),
            signal("choke-a-feedback", "41.5", "%", EvidenceQuality.GOOD, "dataset:f"))
        .mode(signal("controller-mode", "AUTO", "state", EvidenceQuality.GOOD, "dataset:m"))
        .activeCount(signal("active-count", "2", "count", EvidenceQuality.GOOD, "dataset:a"))
        .addRouting(signal("route-a-active", "true", "state", EvidenceQuality.GOOD, "dataset:r"))
        .addRouting(signal("route-b-active", "false", "state", EvidenceQuality.GOOD, "dataset:r"))
        .permissive(signal("controller-permissive", "true", "state", EvidenceQuality.GOOD, "dataset:p")).build();

    ValidationResult result = binding.validate(EVALUATION_TIME);

    assertFalse(result.isReady());
    assertTrue(result.getErrors().stream().anyMatch(issue -> "active-count".equals(issue.getCategory())));
    assertTrue(result.getErrors().stream().anyMatch(issue -> "fallback".equals(issue.getCategory())));
  }

  /**
   * Java serialization preserves public logical tags, opaque provenance identifiers, and readiness behavior.
   *
   * @throws Exception if Java object serialization fails
   */
  @Test
  void serializationPreservesProvenanceAndReadiness() throws Exception {
    ControllerEvidenceBinding original = completeBuilder()
        .addActuator("choke-a", signal("choke-a-command", "42.0", "%", EvidenceQuality.GOOD, "retrieval:command:17"),
            signal("choke-a-feedback", "41.5", "%", EvidenceQuality.GOOD, "retrieval:feedback:18"))
        .build();

    ControllerEvidenceBinding restored = roundTrip(original);

    assertEquals("retrieval:command:17", restored.getActuators().get(0).getCommand().getProvenanceId());
    assertEquals("retrieval:feedback:18", restored.getActuators().get(0).getFeedback().getProvenanceId());
    assertEquals("%", restored.getActuators().get(0).getFeedback().getEngineeringUnit());
    assertTrue(restored.validate(EVALUATION_TIME).isReady());
    String readinessJson = restored.toReadinessJson(EVALUATION_TIME);
    assertTrue(readinessJson.contains("\"schemaVersion\": \"1.0\""));
    assertTrue(readinessJson.contains("\"ready\": true"));
    assertFalse(readinessJson.contains("historianTag"));
  }

  /**
   * Creates a complete valid controller-level evidence builder without actuator evidence.
   *
   * @return configured builder
   */
  private ControllerEvidenceBinding.Builder completeBuilder() {
    return ControllerEvidenceBinding.builder("coordinated-choke-control")
        .mode(signal("controller-mode", "AUTO", "state", EvidenceQuality.GOOD, "dataset:mode"))
        .activeCount(signal("active-count", "1", "count", EvidenceQuality.GOOD, "dataset:count"))
        .addRouting(signal("route-a-active", "true", "state", EvidenceQuality.GOOD, "dataset:routing"))
        .permissive(signal("controller-permissive", "true", "state", EvidenceQuality.GOOD, "dataset:permissive"))
        .fallbackAvailable(
            signal("manual-fallback-available", "true", "state", EvidenceQuality.GOOD, "document:fallback"));
  }

  /**
   * Creates fresh signal evidence for a focused test.
   *
   * @param logicalTag public logical signal name
   * @param value signal value
   * @param engineeringUnit source engineering unit or state/count qualifier
   * @param quality source quality
   * @param provenanceId opaque provenance identifier
   * @return signal evidence
   */
  private SignalEvidence signal(String logicalTag, String value, String engineeringUnit, EvidenceQuality quality,
      String provenanceId) {
    return new SignalEvidence(logicalTag, value, engineeringUnit, EVALUATION_TIME - 1000L, quality, provenanceId);
  }

  /**
   * Serializes and restores a controller evidence binding.
   *
   * @param binding binding to round-trip
   * @return restored binding
   * @throws Exception if Java object serialization fails
   */
  private ControllerEvidenceBinding roundTrip(ControllerEvidenceBinding binding) throws Exception {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(buffer)) {
      output.writeObject(binding);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(buffer.toByteArray()))) {
      return (ControllerEvidenceBinding) input.readObject();
    }
  }
}