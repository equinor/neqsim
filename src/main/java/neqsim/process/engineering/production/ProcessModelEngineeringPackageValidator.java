package neqsim.process.engineering.production;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Structural and governance validation for the coordinated multi-area engineering manifest. */
public final class ProcessModelEngineeringPackageValidator {
  private ProcessModelEngineeringPackageValidator() {
  }

  /** Returns immutable validation findings; an empty list means the manifest is structurally valid. */
  public static List<String> validate(Map<String, Object> manifest) {
    List<String> findings = new ArrayList<String>();
    if (manifest == null) {
      findings.add("MANIFEST_MISSING");
      return Collections.unmodifiableList(findings);
    }
    require(manifest, "schemaVersion", "neqsim_process_model_engineering_manifest.v1", findings);
    require(manifest, "schemaUri", "urn:neqsim:schema:process-model-engineering-manifest:v1", findings);
    if (!Boolean.TRUE.equals(manifest.get("complete"))) {
      findings.add("RESULT_NOT_COMPLETE");
    }
    Object blockers = manifest.get("blockers");
    if (!(blockers instanceof List<?>) || !((List<?>) blockers).isEmpty()) {
      findings.add("OPEN_BLOCKERS");
    }
    requireFingerprint(manifest, "fingerprint", findings);
    requireFingerprint(manifest, "coordinationFingerprint", findings);
    if (!(manifest.get("areas") instanceof Map<?, ?>) || ((Map<?, ?>) manifest.get("areas")).isEmpty()) {
      findings.add("AREA_RESULTS_MISSING");
    }
    if (!(manifest.get("areaPackages") instanceof Map<?, ?>) || ((Map<?, ?>) manifest.get("areaPackages")).isEmpty()) {
      findings.add("AREA_PACKAGES_MISSING");
    } else if (manifest.get("areas") instanceof Map<?, ?>) {
      Set<?> areaNames = ((Map<?, ?>) manifest.get("areas")).keySet();
      Set<?> packageNames = ((Map<?, ?>) manifest.get("areaPackages")).keySet();
      if (!areaNames.equals(packageNames)) {
        findings.add("AREA_PACKAGE_SET_MISMATCH");
      }
    }
    if (!(manifest.get("sharedStreamDependencies") instanceof List<?>)) {
      findings.add("SHARED_STREAM_DEPENDENCIES_MISSING");
    }
    if (!(manifest.get("sharedSystemResults") instanceof List<?>)) {
      findings.add("SHARED_SYSTEM_RESULTS_MISSING");
    }
    return Collections.unmodifiableList(findings);
  }

  /** Throws when a manifest cannot be released as a coordinated package. */
  public static void validateOrThrow(Map<String, Object> manifest) {
    List<String> findings = validate(manifest);
    if (!findings.isEmpty()) {
      throw new IllegalStateException("Invalid process-model engineering manifest: " + findings);
    }
  }

  private static void require(Map<String, Object> manifest, String field, String expected, List<String> findings) {
    if (!expected.equals(manifest.get(field))) {
      findings.add("INVALID_" + field.toUpperCase());
    }
  }

  private static void requireFingerprint(Map<String, Object> manifest, String field, List<String> findings) {
    Object value = manifest.get(field);
    if (!(value instanceof String) || !((String) value).matches("[0-9a-f]{64}")) {
      findings.add("INVALID_" + field.toUpperCase());
    }
  }
}
