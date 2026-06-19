package neqsim.process.processmodel.dexpi;

/**
 * Composes a complete pipe line-identification number following the NORSOK Z-003 / PIP PIC001 convention.
 *
 * <p>
 * The full line number concatenates the nominal size, fluid (service) code, a sequential line number, the piping
 * (material) class and an optional insulation code, separated by hyphens:
 * </p>
 *
 * <pre>
 *   SIZE"-FLUIDCODE-SEQUENCE-PIPINGCLASS[-INSULATION]
 *   e.g.  6"-PG-1001-A1B-H25
 * </pre>
 *
 * <p>
 * where {@code H25} denotes 25&nbsp;mm of heat-conservation insulation. Empty or missing fields are omitted so a
 * partially specified line still produces a sensible identifier.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
final class NorsokLineNumber {

  /** Nominal pipe size including the inch double-quote, e.g. {@code 6"}. */
  private String size;
  /** Fluid / service code, e.g. {@code PG} (process gas). */
  private String fluidCode;
  /** Sequential line number, e.g. {@code 1001}. */
  private String sequence;
  /** Piping (material) class, e.g. {@code A1B}. */
  private String pipingClass;
  /** Optional insulation code, e.g. {@code H25} or {@code PP}. */
  private String insulation;

  /** Creates an empty line-number builder. */
  NorsokLineNumber() {
  }

  /**
   * Sets the nominal pipe size. A bare number is suffixed with the inch mark.
   *
   * @param sizeValue nominal size such as {@code "6"} or {@code "6\""} (may be null)
   * @return this builder
   */
  NorsokLineNumber size(String sizeValue) {
    if (sizeValue == null || sizeValue.trim().isEmpty()) {
      this.size = null;
    } else {
      String s = sizeValue.trim();
      this.size = s.endsWith("\"") ? s : s + "\"";
    }
    return this;
  }

  /**
   * Sets the fluid / service code.
   *
   * @param code fluid code such as {@code "PG"} or {@code "HC"} (may be null)
   * @return this builder
   */
  NorsokLineNumber fluidCode(String code) {
    this.fluidCode = trimToNull(code);
    return this;
  }

  /**
   * Sets the sequential line number.
   *
   * @param seq sequence such as {@code "1001"} (may be null)
   * @return this builder
   */
  NorsokLineNumber sequence(String seq) {
    this.sequence = trimToNull(seq);
    return this;
  }

  /**
   * Sets the piping (material) class.
   *
   * @param clazz piping class such as {@code "A1B"} or {@code "150#"} (may be null)
   * @return this builder
   */
  NorsokLineNumber pipingClass(String clazz) {
    this.pipingClass = trimToNull(clazz);
    return this;
  }

  /**
   * Sets the insulation code.
   *
   * @param code insulation code such as {@code "H25"} (heat conservation) or {@code "PP"} (personnel protection) (may
   * be null)
   * @return this builder
   */
  NorsokLineNumber insulation(String code) {
    this.insulation = trimToNull(code);
    return this;
  }

  /**
   * Builds the complete hyphen-separated line number, omitting empty fields.
   *
   * @return the composed line number (never null; empty string if no fields are set)
   */
  String build() {
    StringBuilder sb = new StringBuilder();
    appendField(sb, size);
    appendField(sb, fluidCode);
    appendField(sb, sequence);
    appendField(sb, pipingClass);
    appendField(sb, insulation);
    return sb.toString();
  }

  /**
   * Appends a field with a leading hyphen when the builder already has content.
   *
   * @param sb the target buffer
   * @param field the field value (skipped when null)
   */
  private static void appendField(StringBuilder sb, String field) {
    if (field == null) {
      return;
    }
    if (sb.length() > 0) {
      sb.append('-');
    }
    sb.append(field);
  }

  /**
   * Trims a string, returning null when the result is empty.
   *
   * @param value the input (may be null)
   * @return the trimmed value, or null when blank
   */
  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String t = value.trim();
    return t.isEmpty() ? null : t;
  }
}
