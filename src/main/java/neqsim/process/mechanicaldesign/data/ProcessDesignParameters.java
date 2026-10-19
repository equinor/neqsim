package neqsim.process.mechanicaldesign.data;

import java.sql.ResultSet;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.mechanicaldesign.designstandards.DesignStandard;
import neqsim.util.database.NeqSimProcessDesignDataBase;

/**
 * Explicit layered process requirements from the bundled design database. Historical StatoilTR rows provide the legacy
 * baseline, generic default rows supplement it, and selected company rows take precedence. Every value retains its
 * actual company and document identifier.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class ProcessDesignParameters {
  /** Utility class. */
  private ProcessDesignParameters() {
  }

  /**
   * Resolve a company against the database without silently accepting a missing identifier.
   *
   * @param company requested company or the explicit {@code default} selection
   * @return canonical database company
   * @throws IllegalArgumentException if the identifier is absent or unknown
   * @throws IllegalStateException if the company table cannot be read
   */
  public static String resolveCompany(String company) {
    String normalized = DesignStandard.normalizeCompanyIdentifier(company);
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("Company is required; select 'default' explicitly for generic requirements");
    }
    try (NeqSimProcessDesignDataBase database = new NeqSimProcessDesignDataBase();
        ResultSet rows = database.getResultSet("SELECT DISTINCT Company FROM TechnicalRequirements_Process")) {
      while (rows.next()) {
        String candidate = rows.getString("Company");
        if (normalized.equalsIgnoreCase(candidate)) {
          return candidate;
        }
      }
    } catch (Exception ex) {
      throw new IllegalStateException("Cannot resolve process-design company " + normalized, ex);
    }
    throw new IllegalArgumentException("No process-design requirements for company: " + normalized);
  }

  /**
   * Load numeric requirements, retaining ranges and provenance. Non-numeric descriptive rows are not parameters.
   * Numeric rows with invalid, missing or inverted bounds fail explicitly.
   *
   * @param equipmentType database equipment type
   * @param company requested company
   * @return immutable requirements keyed by the database specification name
   * @throws IllegalArgumentException if the company is unknown
   * @throws IllegalStateException if a numeric row is invalid or the database cannot be read
   */
  public static Map<String, Value> load(String equipmentType, String company) {
    String selected = resolveCompany(company);
    Map<String, Value> values = new LinkedHashMap<String, Value>();
    try (NeqSimProcessDesignDataBase database = new NeqSimProcessDesignDataBase()) {
      String[] layers = {"StatoilTR", "default", selected};
      for (String layer : layers) {
        String query = "SELECT * FROM TechnicalRequirements_Process WHERE EQUIPMENTTYPE='"
            + equipmentType.replace("'", "''") + "' AND Company='" + layer.replace("'", "''") + "'";
        try (ResultSet rows = database.getResultSet(query)) {
          while (rows.next()) {
            String min = rows.getString("MINVALUE");
            String max = rows.getString("MAXVALUE");
            if ((min == null || min.trim().isEmpty()) && (max == null || max.trim().isEmpty())) {
              continue;
            }
            double lower = Double.parseDouble(min);
            double upper = Double.parseDouble(max);
            String key = rows.getString("SPECIFICATION");
            if (!Double.isFinite(lower) || !Double.isFinite(upper) || lower > upper) {
              throw new IllegalStateException("Invalid range for " + equipmentType + "/" + key + "/" + layer);
            }
            values.put(key, new Value(lower, upper, rows.getString("UNIT"), layer, rows.getString("DOCUMENTID")));
          }
        }
      }
    } catch (Exception ex) {
      throw new IllegalStateException("Cannot load process requirements for " + equipmentType + "/" + selected, ex);
    }
    if (values.isEmpty()) {
      throw new IllegalStateException("No numeric process requirements for equipment: " + equipmentType);
    }
    return Collections.unmodifiableMap(values);
  }

  /**
   * Require a named property without a numerical fallback.
   *
   * @param values validated requirement snapshot
   * @param specification required database key
   * @return matching range
   * @throws IllegalStateException if the required property is absent
   */
  public static Value require(Map<String, Value> values, String specification) {
    Value value = values.get(specification);
    if (value == null) {
      throw new IllegalStateException("Missing process-design specification: " + specification);
    }
    return value;
  }

  /**
   * Immutable requirement range with its original units and provenance.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  public static final class Value {
    /** Lower database bound. */
    public final double minimum;
    /** Upper database bound. */
    public final double maximum;
    /** Original database unit. */
    public final String unit;
    /** Company that actually supplies this property. */
    public final String company;
    /** Source document identifier. */
    public final String document;

    /**
     * Create an immutable database value.
     *
     * @param minimum lower bound
     * @param maximum upper bound
     * @param unit source unit
     * @param company source company
     * @param document source document
     */
    private Value(double minimum, double maximum, String unit, String company, String document) {
      this.minimum = minimum;
      this.maximum = maximum;
      this.unit = unit;
      this.company = company;
      this.document = document;
    }

    /**
     * Return the historical screening midpoint of a bounded requirement.
     *
     * @return midpoint in source units
     */
    public double midpoint() {
      return minimum / 2.0 + maximum / 2.0;
    }

    /**
     * Identify the actual source, independently of the requested company.
     *
     * @return database company and document
     */
    public String source() {
      return "TechnicalRequirements_Process:" + company + ":" + document;
    }
  }
}
