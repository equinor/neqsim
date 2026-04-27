package neqsim.process.equipment.separator.entrainment;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Applicability report from an {@link EnhancedEntrainmentProvider} for a given
 * {@code Separator} configuration. A provider returns this object to tell the
 * caller whether its correlation is valid for the supplied geometry and
 * operating conditions, and if not, which inputs are out of range.
 *
 * <p>
 * Diagnostics are simple human-readable strings — one entry per out-of-range
 * input. The aggregate {@link #isApplicable()} flag is the AND of all
 * diagnostics: if any input is out of range, the report is not applicable.
 *
 * @author NeqSim
 * @version 1.0
 */
public final class EntrainmentApplicability implements Serializable {
  private static final long serialVersionUID = 1L;

  /** True if every input is inside the provider's validity envelope. */
  private final boolean applicable;

  /** Human-readable diagnostics, one per out-of-range input. Never null. */
  private final List<String> diagnostics;

  /**
   * Constructs an applicability report.
   *
   * @param applicable true if every input is inside the provider's envelope
   * @param diagnostics human-readable diagnostics, one per out-of-range input;
   *        may be null or empty when {@code applicable} is true
   */
  public EntrainmentApplicability(boolean applicable, List<String> diagnostics) {
    this.applicable = applicable;
    this.diagnostics = (diagnostics == null) ? Collections.<String>emptyList()
        : Collections.unmodifiableList(new ArrayList<String>(diagnostics));
  }

  /**
   * Returns the aggregate applicability flag.
   *
   * @return true if every input is inside the provider's envelope
   */
  public boolean isApplicable() {
    return applicable;
  }

  /**
   * Returns the human-readable diagnostics.
   *
   * @return unmodifiable list of diagnostics, one per out-of-range input;
   *         empty list (never null) when applicable
   */
  public List<String> getDiagnostics() {
    return diagnostics;
  }

  /**
   * Convenience factory for the "applicable" case.
   *
   * @return an applicable report with no diagnostics
   */
  public static EntrainmentApplicability ok() {
    return new EntrainmentApplicability(true, Collections.<String>emptyList());
  }

  /**
   * Convenience factory for the "not applicable" case.
   *
   * @param diagnostics human-readable diagnostics, one per out-of-range input
   * @return a non-applicable report with the supplied diagnostics
   */
  public static EntrainmentApplicability notApplicable(List<String> diagnostics) {
    return new EntrainmentApplicability(false, diagnostics);
  }

  /**
   * Returns a multi-line text rendering suitable for {@code System.out.println}.
   *
   * @return a text rendering of this applicability report
   */
  public String toTextReport() {
    StringBuilder sb = new StringBuilder();
    sb.append("Applicability: ").append(applicable ? "OK" : "OUT OF RANGE").append('\n');
    for (String d : diagnostics) {
      sb.append("  - ").append(d).append('\n');
    }
    return sb.toString();
  }
}
