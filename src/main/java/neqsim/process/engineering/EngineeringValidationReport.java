package neqsim.process.engineering;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Validation findings for an engineering project. */
public final class EngineeringValidationReport implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Finding severity. */
  public enum Severity {
    ERROR,
    WARNING,
    REVIEW,
    INFORMATION
  }

  /** One validation finding. */
  public static final class Finding implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final Severity severity;
    private final String code;
    private final String equipmentTag;
    private final String message;

    Finding(Severity severity, String code, String equipmentTag, String message) {
      this.severity = severity;
      this.code = code;
      this.equipmentTag = equipmentTag;
      this.message = message;
    }

    /** @return finding severity */
    public Severity getSeverity() {
      return severity;
    }

    /** @return stable validation code */
    public String getCode() {
      return code;
    }

    /** @return affected equipment tag */
    public String getEquipmentTag() {
      return equipmentTag;
    }

    /** @return human-readable finding */
    public String getMessage() {
      return message;
    }
  }

  private final List<Finding> findings = new ArrayList<Finding>();

  /** Adds a validation finding. */
  public void add(Severity severity, String code, String equipmentTag, String message) {
    findings.add(new Finding(severity, code, equipmentTag, message));
  }

  /** @return immutable finding list */
  public List<Finding> getFindings() {
    return Collections.unmodifiableList(findings);
  }

  /** @return true when one or more error findings exist */
  public boolean hasErrors() {
    for (Finding finding : findings) {
      if (finding.getSeverity() == Severity.ERROR) {
        return true;
      }
    }
    return false;
  }

  /** @return number of findings at the requested severity */
  public int count(Severity severity) {
    int result = 0;
    for (Finding finding : findings) {
      if (finding.getSeverity() == severity) {
        result++;
      }
    }
    return result;
  }
}
