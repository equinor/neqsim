package neqsim.process.engineering.pid;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/** One actionable result from validating a generated P&amp;ID design model. */
public final class PidCompletenessFinding implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Finding severity. */
  public enum Severity {
    ERROR, REVIEW, WARNING, INFORMATION
  }

  private final String code;
  private final Severity severity;
  private final String subject;
  private final String message;

  public PidCompletenessFinding(String code, Severity severity, String subject, String message) {
    this.code = code;
    this.severity = severity;
    this.subject = subject;
    this.message = message;
  }

  public Severity getSeverity() {
    return severity;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> value = new LinkedHashMap<String, Object>();
    value.put("code", code);
    value.put("severity", severity.name());
    value.put("subject", subject);
    value.put("message", message);
    return value;
  }
}
