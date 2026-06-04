package neqsim.process.automation;

import java.io.Serializable;
import com.google.gson.JsonObject;

/**
 * Outcome of a
 * {@link WriteValidator#validate(neqsim.process.equipment.ProcessEquipmentInterface, String, double, String)
 * write validation}. Carries a pass/fail flag together with a structured reason that can be
 * returned to the calling agent as JSON.
 *
 * <p>
 * Use the static factory methods {@link #ok()} for a passing check and
 * {@link #fail(String, String)} or {@link #warn(String, String)} for a failure or warning. The
 * {@code severity} field distinguishes hard rejections ({@code ERROR}) from soft hints
 * ({@code WARNING}) — only {@code ERROR}-level results abort a transactional batch.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class WriteValidationResult implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Severity classification for a validation result. */
  public enum Severity {
    /** Validation passed. */
    OK,
    /** Soft warning: write is applied but the agent is informed of an unusual value. */
    WARNING,
    /** Hard rejection: the write is refused and any enclosing batch is rolled back. */
    ERROR
  }

  private static final WriteValidationResult OK_INSTANCE =
      new WriteValidationResult(Severity.OK, null, null);

  private final Severity severity;
  private final String code;
  private final String message;

  /**
   * Creates a validation result.
   *
   * @param severity the severity classification; never null
   * @param code a short machine-readable code such as {@code "OUTLET_PRESSURE_BELOW_INLET"}, or
   *        {@code null} for {@link Severity#OK}
   * @param message a human-readable explanation, or {@code null} for {@link Severity#OK}
   */
  public WriteValidationResult(Severity severity, String code, String message) {
    if (severity == null) {
      throw new IllegalArgumentException("severity must not be null");
    }
    this.severity = severity;
    this.code = code;
    this.message = message;
  }

  /**
   * Returns the shared {@link Severity#OK OK} instance.
   *
   * @return a passing validation result
   */
  public static WriteValidationResult ok() {
    return OK_INSTANCE;
  }

  /**
   * Creates a hard-failure validation result.
   *
   * @param code the machine-readable code
   * @param message the human-readable explanation
   * @return a failing validation result with severity {@link Severity#ERROR}
   */
  public static WriteValidationResult fail(String code, String message) {
    return new WriteValidationResult(Severity.ERROR, code, message);
  }

  /**
   * Creates a soft-warning validation result.
   *
   * @param code the machine-readable code
   * @param message the human-readable explanation
   * @return a validation result with severity {@link Severity#WARNING}
   */
  public static WriteValidationResult warn(String code, String message) {
    return new WriteValidationResult(Severity.WARNING, code, message);
  }

  /**
   * Returns the severity of this result.
   *
   * @return the severity; never null
   */
  public Severity getSeverity() {
    return severity;
  }

  /**
   * Returns whether the write should be allowed. Both {@link Severity#OK OK} and
   * {@link Severity#WARNING WARNING} are considered passing.
   *
   * @return true if the write is allowed
   */
  public boolean isAllowed() {
    return severity != Severity.ERROR;
  }

  /**
   * Returns the machine-readable code, or {@code null} for {@link Severity#OK}.
   *
   * @return the code or null
   */
  public String getCode() {
    return code;
  }

  /**
   * Returns the human-readable explanation, or {@code null} for {@link Severity#OK}.
   *
   * @return the message or null
   */
  public String getMessage() {
    return message;
  }

  /**
   * Renders this result as a JSON object suitable for inclusion in {@link TransactionalBatchResult}
   * payloads.
   *
   * @return a JSON object with {@code severity}, {@code code} and {@code message} fields
   */
  public JsonObject toJson() {
    JsonObject obj = new JsonObject();
    obj.addProperty("severity", severity.name());
    if (code != null) {
      obj.addProperty("code", code);
    }
    if (message != null) {
      obj.addProperty("message", message);
    }
    return obj;
  }
}
