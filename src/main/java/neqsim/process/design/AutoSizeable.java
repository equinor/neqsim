package neqsim.process.design;

/**
 * Interface for process equipment that can be automatically sized based on design criteria.
 *
 * <p>
 * Equipment implementing this interface can calculate their dimensions and design parameters from
 * connected stream conditions and design safety factors. This enables automated process design
 * workflows where equipment is sized from specifications rather than manually configured.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 * 
 * <pre>
 * ThreePhaseSeparator separator = new ThreePhaseSeparator("HP-Sep", feedStream);
 * separator.autoSize(1.2); // Size with 20% safety factor
 * System.out.println(separator.getSizingReport());
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public interface AutoSizeable {

  /**
   * Automatically size the equipment based on connected stream conditions.
   *
   * <p>
   * This method calculates dimensions and design parameters using the inlet stream properties and
   * applies the specified safety factor. The equipment must have a valid inlet stream connected
   * before calling this method.
   * </p>
   *
   * @param safetyFactor multiplier for design capacity, typically 1.1-1.3 (10-30% over design)
   * @throws IllegalStateException if inlet stream is not connected or not initialized
   */
  void autoSize(double safetyFactor);

  /**
   * Automatically size using default safety factor (1.2 = 20% margin).
   */
  default void autoSize() {
    autoSize(1.2);
  }

  /**
   * Automatically size using company-specific design standards.
   *
   * <p>
   * This method applies design rules from the specified company's technical requirements (TR)
   * documents. The standards are loaded from the NeqSim design database.
   * </p>
   *
   * @param companyStandard company name (e.g., "Equinor", "Shell", "TotalEnergies")
   * @param trDocument TR document reference (e.g., "TR2000", "DEP-31.38.01.11")
   */
  default void autoSize(String companyStandard, String trDocument) {
    // Default implementation uses standard safety factor
    autoSize(1.2);
  }

  /**
   * Check if equipment has been auto-sized.
   *
   * @return true if autoSize() has been called successfully
   */
  boolean isAutoSized();

  /**
   * Get a detailed sizing report after auto-sizing.
   *
   * <p>
   * The report includes:
   * </p>
   * <ul>
   * <li>Design basis (flow rates, pressures, temperatures)</li>
   * <li>Calculated dimensions</li>
   * <li>Design parameters (K-factor, Cv, velocity, etc.)</li>
   * <li>Safety margins</li>
   * </ul>
   *
   * @return formatted sizing report string
   */
  String getSizingReport();

  /**
   * Get sizing report as JSON for programmatic access.
   *
   * @return JSON string with sizing data
   */
  default String getSizingReportJson() {
    return "{}";
  }
}
