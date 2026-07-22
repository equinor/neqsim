package neqsim.process.safety.scenario;

import java.util.Map;

/** Supplies structured evidence from protection logic executed by a dynamic safety scenario. */
public interface DynamicSafetyScenarioEvidenceProvider {

  /**
   * Gets a serialization-safe evidence record for the completed or current logic execution.
   *
   * @return ordered evidence fields suitable for JSON serialization
   */
  Map<String, Object> getDynamicSafetyScenarioEvidence();
}
