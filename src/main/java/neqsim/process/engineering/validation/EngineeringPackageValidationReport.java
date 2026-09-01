package neqsim.process.engineering.validation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;

/** Machine-readable structural and semantic validation result for an engineering package. */
public final class EngineeringPackageValidationReport implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Validation finding severity. */
  public enum Severity {
    ERROR, WARNING
  }

  /** One stable, artifact-scoped validation finding. */
  public static final class Finding implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final Severity severity;
    private final String code;
    private final String artifact;
    private final String path;
    private final String message;

    Finding(Severity severity, String code, String artifact, String path, String message) {
      this.severity = severity;
      this.code = code;
      this.artifact = artifact;
      this.path = path;
      this.message = message;
    }

    public Severity getSeverity() {
      return severity;
    }

    public String getCode() {
      return code;
    }

    public String getArtifact() {
      return artifact;
    }

    public String getPath() {
      return path;
    }

    public String getMessage() {
      return message;
    }

    Map<String, Object> toMap() {
      Map<String, Object> value = new LinkedHashMap<String, Object>();
      value.put("severity", severity.name());
      value.put("code", code);
      value.put("artifact", artifact);
      value.put("path", path);
      value.put("message", message);
      return value;
    }
  }

  private final List<Finding> findings = new ArrayList<Finding>();

  public void addError(String code, String artifact, String path, String message) {
    add(Severity.ERROR, code, artifact, path, message);
  }

  public void addWarning(String code, String artifact, String path, String message) {
    add(Severity.WARNING, code, artifact, path, message);
  }

  public void addAll(EngineeringPackageValidationReport other) {
    if (other != null) {
      findings.addAll(other.findings);
    }
  }

  public List<Finding> getFindings() {
    return Collections.unmodifiableList(findings);
  }

  public boolean isValid() {
    return getErrorCount() == 0;
  }

  public int getErrorCount() {
    return count(Severity.ERROR);
  }

  public int getWarningCount() {
    return count(Severity.WARNING);
  }

  public Map<String, Object> toMap() {
    Map<String, Object> value = new LinkedHashMap<String, Object>();
    value.put("schemaVersion", EngineeringSchemaCatalog.VALIDATION_REPORT);
    value.put("schemaUri", EngineeringSchemaCatalog.schemaUri(EngineeringSchemaCatalog.VALIDATION_REPORT));
    value.put("valid", Boolean.valueOf(isValid()));
    value.put("errorCount", Integer.valueOf(getErrorCount()));
    value.put("warningCount", Integer.valueOf(getWarningCount()));
    List<Map<String, Object>> resultFindings = new ArrayList<Map<String, Object>>();
    for (Finding finding : findings) {
      resultFindings.add(finding.toMap());
    }
    value.put("findings", resultFindings);
    return value;
  }

  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(toMap());
  }

  private void add(Severity severity, String code, String artifact, String path, String message) {
    findings.add(new Finding(severity, requireText(code, "code"), requireText(artifact, "artifact"),
        path == null ? "" : path, requireText(message, "message")));
  }

  private int count(Severity severity) {
    int count = 0;
    for (Finding finding : findings) {
      if (finding.getSeverity() == severity) {
        count++;
      }
    }
    return count;
  }

  private static String requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value.trim();
  }
}
