package neqsim.thermo.component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Validity limits for the source-qualified, temperature-only component dielectric records.
 *
 * <p>
 * The limits describe pure reference liquids, not mixture-density correlations. Records absent from the qualification
 * table retain their legacy behavior and are not thereby qualified.
 *
 * @author Even Solbraa
 * @version 1.0
 */
final class DielectricReferenceData {
  /** Immutable metadata, loaded once on first dielectric-property use. */
  private static final Map<String, ReferenceRange> REFERENCES = loadReferences();

  /** Prevent construction of the metadata utility. */
  private DielectricReferenceData() {
  }

  /**
   * Check the source domain before evaluating a property or either temperature derivative.
   *
   * @param componentName canonical database record name
   * @param temperature absolute temperature in kelvin
   * @param coefficients component polynomial coefficients in their stored order
   * @throws IllegalArgumentException if a qualified record is unavailable or outside its domain
   */
  static void validate(String componentName, double temperature, double[] coefficients) {
    ReferenceRange reference = REFERENCES.get(componentName);
    if (reference == null) {
      return;
    }
    if (!reference.available) {
      throw new IllegalArgumentException("No qualified temperature-only dielectric data for " + componentName + " ("
          + reference.source + "). See docs/thermo/DielectricPropertyData.md");
    }
    for (int i = 0; i < reference.coefficients.length; i++) {
      if (Double.doubleToLongBits(coefficients[i]) != Double.doubleToLongBits(reference.coefficients[i])) {
        throw new IllegalArgumentException("Dielectric coefficients for " + componentName
            + " do not match the source-qualified record (" + reference.source
            + "). Reload component data; stale serialized or custom database values need qualification.");
      }
    }
    if (!Double.isFinite(temperature) || temperature < reference.minimum || temperature > reference.maximum) {
      throw new IllegalArgumentException(
          "Dielectric data for " + componentName + " are valid from " + reference.minimum + " to " + reference.maximum
              + " K; requested " + temperature + " K (" + reference.source + "). Extrapolation is not qualified.");
    }
  }

  /**
   * Load the small qualification table without changing the component database schema.
   *
   * @return immutable source domains indexed by database record name
   * @throws IllegalStateException if the packaged qualification table is missing or malformed
   */
  private static Map<String, ReferenceRange> loadReferences() {
    Map<String, ReferenceRange> references = new HashMap<String, ReferenceRange>();
    InputStream stream = DielectricReferenceData.class.getResourceAsStream("/data/DielectricReferenceData.csv");
    if (stream == null) {
      throw new IllegalStateException("Missing dielectric reference qualification table");
    }
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      if (!"NAME,TMIN_K,TMAX_K,STATUS,SOURCE,A,B,C,D,E".equals(reader.readLine())) {
        throw new IllegalStateException("Invalid dielectric reference qualification header");
      }
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.trim().isEmpty()) {
          continue;
        }
        String[] fields = line.split(",", -1);
        if (fields.length != 10 || fields[0].isEmpty() || fields[4].isEmpty()
            || !("bounded".equals(fields[3]) || "unavailable".equals(fields[3]))) {
          throw new IllegalStateException("Malformed dielectric reference qualification: " + line);
        }
        double minimum = Double.parseDouble(fields[1]);
        double maximum = Double.parseDouble(fields[2]);
        boolean available = "bounded".equals(fields[3]);
        double[] coefficients = new double[5];
        for (int i = 0; i < coefficients.length; i++) {
          coefficients[i] = Double.parseDouble(fields[5 + i]);
          if (available && !Double.isFinite(coefficients[i])) {
            throw new IllegalStateException("Nonfinite qualified dielectric coefficients: " + fields[0]);
          }
        }
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum)
            || (available && (minimum <= 0.0 || maximum < minimum))) {
          throw new IllegalStateException("Invalid dielectric reference temperature limits: " + line);
        }
        if (references.put(fields[0],
            new ReferenceRange(minimum, maximum, available, fields[4], coefficients)) != null) {
          throw new IllegalStateException("Duplicate dielectric reference record: " + fields[0]);
        }
      }
    } catch (IOException | NumberFormatException ex) {
      throw new IllegalStateException("Cannot read dielectric reference qualification table", ex);
    }
    return Collections.unmodifiableMap(references);
  }

  /**
   * Immutable source-domain entry; it is shared metadata and is not serialized with fluids.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static final class ReferenceRange {
    /** Lowest qualified absolute temperature in kelvin. */
    private final double minimum;
    /** Highest qualified absolute temperature in kelvin. */
    private final double maximum;
    /** Whether source-qualified coefficients are available. */
    private final boolean available;
    /** Source or reason for quarantine. */
    private final String source;
    /** Exact polynomial values to which the source and domain apply. */
    private final double[] coefficients;

    /**
     * Construct a source-domain record.
     *
     * @param minimum lowest qualified temperature in kelvin
     * @param maximum highest qualified temperature in kelvin
     * @param available whether coefficients are qualified
     * @param source source identifier or reason for quarantine
     * @param coefficients polynomial coefficients covered by this source
     */
    private ReferenceRange(double minimum, double maximum, boolean available, String source, double[] coefficients) {
      this.minimum = minimum;
      this.maximum = maximum;
      this.available = available;
      this.source = source;
      this.coefficients = coefficients;
    }
  }
}
