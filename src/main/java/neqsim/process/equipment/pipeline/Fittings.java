package neqsim.process.equipment.pipeline;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Collection of pipe fittings for equivalent length pressure drop calculations.
 *
 * <p>
 * This class manages a collection of pipe fittings (bends, valves, tees, reducers, etc.) and
 * calculates their contribution to pressure drop using the equivalent length method.
 * </p>
 *
 * <h2>Equivalent Length Method</h2>
 * <p>
 * The equivalent length method converts the pressure loss through a fitting into an equivalent
 * length of straight pipe that would produce the same pressure drop. This is expressed as an L/D
 * ratio (equivalent length divided by pipe diameter).
 * </p>
 *
 * <h3>Mathematical Basis</h3>
 * <p>
 * For a fitting with L/D ratio, the equivalent length is:
 * </p>
 * 
 * <pre>
 * L_eq = (L/D) × D
 * </pre>
 * <p>
 * where:
 * </p>
 * <ul>
 * <li>L_eq = equivalent length (m)</li>
 * <li>L/D = fitting's equivalent length ratio (dimensionless)</li>
 * <li>D = pipe internal diameter (m)</li>
 * </ul>
 *
 * <p>
 * The total effective pipe length for pressure drop calculations becomes:
 * </p>
 * 
 * <pre>
 * L_eff = L_physical + Σ(L/D)_i × D
 * </pre>
 *
 * <h3>Relation to K-Factor Method</h3>
 * <p>
 * The L/D method is related to the resistance coefficient (K-factor) method by:
 * </p>
 * 
 * <pre>
 * K = f × (L/D)
 * </pre>
 * <p>
 * where f is the Darcy friction factor. For turbulent flow with f ≈ 0.02, K ≈ 0.02 × (L/D).
 * </p>
 *
 * <h2>Standard L/D Values (from Crane TP-410)</h2>
 * <table>
 * <caption>Common fitting L/D values</caption>
 * <tr>
 * <th>Fitting Type</th>
 * <th>L/D</th>
 * </tr>
 * <tr>
 * <td>90° elbow, standard (R/D=1)</td>
 * <td>30</td>
 * </tr>
 * <tr>
 * <td>90° elbow, long radius (R/D=1.5)</td>
 * <td>16</td>
 * </tr>
 * <tr>
 * <td>45° elbow, standard</td>
 * <td>16</td>
 * </tr>
 * <tr>
 * <td>Tee, through flow</td>
 * <td>20</td>
 * </tr>
 * <tr>
 * <td>Tee, branch flow</td>
 * <td>60</td>
 * </tr>
 * <tr>
 * <td>Gate valve, fully open</td>
 * <td>8</td>
 * </tr>
 * <tr>
 * <td>Globe valve, fully open</td>
 * <td>340</td>
 * </tr>
 * <tr>
 * <td>Ball valve, fully open</td>
 * <td>3</td>
 * </tr>
 * <tr>
 * <td>Check valve, swing</td>
 * <td>100</td>
 * </tr>
 * </table>
 *
 * <h2>Usage Example</h2>
 * 
 * <pre>
 * {@code
 * // Create fittings collection
 * Fittings fittings = new Fittings();
 *
 * // Add fittings from database
 * fittings.add("Standard elbow (R=1.5D), 90deg"); // Loads L/D from database
 *
 * // Add fittings with explicit L/D values
 * fittings.add("Custom bend", 25.0); // L/D = 25
 *
 * // Add multiple identical fittings
 * fittings.addMultiple("90deg_elbow", 30.0, 4); // 4 elbows, L/D=30 each
 *
 * // Calculate total equivalent length for a 200mm pipe
 * double eqLength = fittings.getTotalEquivalentLength(0.2); // in meters
 * }
 * </pre>
 *
 * @author ESOL
 * @version 2.0
 * @see Pipeline
 * @see PipeBeggsAndBrills
 * @see AdiabaticPipe
 */
public class Fittings implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001;

  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Fittings.class);

  /** List of fittings in this collection. */
  ArrayList<Fitting> fittingList = new ArrayList<Fitting>();

  /** Standard L/D values for common fittings (from Crane TP-410). */
  private static final Map<String, Double> STANDARD_LD_VALUES = new HashMap<String, Double>();

  static {
    // 90-degree elbows
    STANDARD_LD_VALUES.put("elbow_90_standard", 30.0);
    STANDARD_LD_VALUES.put("elbow_90_long_radius", 16.0);
    STANDARD_LD_VALUES.put("elbow_90_mitre", 60.0);

    // 45-degree elbows
    STANDARD_LD_VALUES.put("elbow_45_standard", 16.0);
    STANDARD_LD_VALUES.put("elbow_45_long_radius", 10.0);

    // Tees
    STANDARD_LD_VALUES.put("tee_through", 20.0);
    STANDARD_LD_VALUES.put("tee_branch", 60.0);

    // Valves
    STANDARD_LD_VALUES.put("valve_gate_open", 8.0);
    STANDARD_LD_VALUES.put("valve_gate_3_4_open", 35.0);
    STANDARD_LD_VALUES.put("valve_gate_1_2_open", 160.0);
    STANDARD_LD_VALUES.put("valve_gate_1_4_open", 900.0);
    STANDARD_LD_VALUES.put("valve_globe_open", 340.0);
    STANDARD_LD_VALUES.put("valve_ball_open", 3.0);
    STANDARD_LD_VALUES.put("valve_butterfly_open", 45.0);
    STANDARD_LD_VALUES.put("valve_check_swing", 100.0);
    STANDARD_LD_VALUES.put("valve_check_lift", 600.0);

    // Reducers and expanders
    STANDARD_LD_VALUES.put("reducer_sudden", 30.0);
    STANDARD_LD_VALUES.put("expander_sudden", 50.0);
    STANDARD_LD_VALUES.put("reducer_gradual", 10.0);
    STANDARD_LD_VALUES.put("expander_gradual", 20.0);

    // Pipe entrance/exit
    STANDARD_LD_VALUES.put("entrance_sharp", 25.0);
    STANDARD_LD_VALUES.put("entrance_rounded", 10.0);
    STANDARD_LD_VALUES.put("exit", 50.0);
  }

  /**
   * Default constructor for Fittings.
   */
  public Fittings() {}

  /**
   * Add a fitting with a specified L/D ratio.
   *
   * <p>
   * The L/D ratio represents the equivalent length of the fitting in terms of pipe diameters. For
   * example, an L/D of 30 means the fitting causes the same pressure drop as 30 diameters of
   * straight pipe.
   * </p>
   *
   * @param name descriptive name for the fitting
   * @param LdivD equivalent length ratio (L/D), dimensionless
   */
  public void add(String name, double LdivD) {
    fittingList.add(new Fitting(name, LdivD));
  }

  /**
   * Add a fitting by name, loading L/D from database.
   *
   * <p>
   * The fitting name must exist in the 'fittings' database table. If not found, falls back to
   * standard values or logs an error.
   * </p>
   *
   * @param name fitting name as stored in database
   */
  public void add(String name) {
    fittingList.add(new Fitting(name));
  }

  /**
   * Add a standard fitting type with predefined L/D value.
   *
   * <p>
   * Uses built-in L/D values from Crane TP-410. Available types:
   * </p>
   * <ul>
   * <li>elbow_90_standard, elbow_90_long_radius, elbow_90_mitre</li>
   * <li>elbow_45_standard, elbow_45_long_radius</li>
   * <li>tee_through, tee_branch</li>
   * <li>valve_gate_open, valve_globe_open, valve_ball_open, valve_butterfly_open</li>
   * <li>valve_check_swing, valve_check_lift</li>
   * <li>reducer_sudden, expander_sudden, reducer_gradual, expander_gradual</li>
   * <li>entrance_sharp, entrance_rounded, exit</li>
   * </ul>
   *
   * @param type fitting type (must match a predefined type)
   * @return true if fitting was added, false if type not recognized
   */
  public boolean addStandard(String type) {
    Double ldValue = STANDARD_LD_VALUES.get(type.toLowerCase());
    if (ldValue != null) {
      fittingList.add(new Fitting(type, ldValue));
      return true;
    }
    logger.warn("Unknown standard fitting type: {}. Use add(name, LdivD) for custom fittings.",
        type);
    return false;
  }

  /**
   * Add multiple identical fittings.
   *
   * <p>
   * Convenience method for adding several fittings of the same type.
   * </p>
   *
   * @param name fitting name
   * @param LdivD L/D ratio for each fitting
   * @param count number of fittings to add
   */
  public void addMultiple(String name, double LdivD, int count) {
    for (int i = 0; i < count; i++) {
      fittingList.add(new Fitting(name + "_" + (i + 1), LdivD));
    }
  }

  /**
   * Add multiple standard fittings of the same type.
   *
   * @param type standard fitting type
   * @param count number of fittings to add
   * @return true if all fittings were added, false if type not recognized
   */
  public boolean addStandardMultiple(String type, int count) {
    Double ldValue = STANDARD_LD_VALUES.get(type.toLowerCase());
    if (ldValue != null) {
      for (int i = 0; i < count; i++) {
        fittingList.add(new Fitting(type + "_" + (i + 1), ldValue));
      }
      return true;
    }
    logger.warn("Unknown standard fitting type: {}", type);
    return false;
  }

  /**
   * Get the list of all fittings.
   *
   * @return ArrayList of Fitting objects
   */
  public ArrayList<Fitting> getFittingsList() {
    return fittingList;
  }

  /**
   * Get the number of fittings in this collection.
   *
   * @return number of fittings
   */
  public int size() {
    return fittingList.size();
  }

  /**
   * Check if the fittings collection is empty.
   *
   * @return true if no fittings have been added
   */
  public boolean isEmpty() {
    return fittingList.isEmpty();
  }

  /**
   * Clear all fittings from the collection.
   */
  public void clear() {
    fittingList.clear();
  }

  /**
   * Get the total L/D ratio of all fittings.
   *
   * <p>
   * This is the sum of all individual fitting L/D ratios.
   * </p>
   *
   * @return total L/D ratio (dimensionless)
   */
  public double getTotalLdRatio() {
    double totalLd = 0.0;
    for (Fitting fitting : fittingList) {
      totalLd += fitting.getLtoD();
    }
    return totalLd;
  }

  /**
   * Calculate total equivalent length of all fittings for a given pipe diameter.
   *
   * <p>
   * Uses the formula: L_eq = Σ(L/D)_i × D
   * </p>
   *
   * @param diameter pipe internal diameter in meters
   * @return total equivalent length in meters
   */
  public double getTotalEquivalentLength(double diameter) {
    return getTotalLdRatio() * diameter;
  }

  /**
   * Get list of available standard fitting types.
   *
   * @return map of fitting types to their L/D values
   */
  public static Map<String, Double> getStandardFittingTypes() {
    return new HashMap<String, Double>(STANDARD_LD_VALUES);
  }

  /**
   * Generate a summary of all fittings in the collection.
   *
   * @return formatted string summary
   */
  public String getSummary() {
    StringBuilder sb = new StringBuilder();
    sb.append("Fittings Summary:\n");
    sb.append(String.format("%-40s %10s%n", "Fitting Name", "L/D"));
    sb.append(
        String.format("%-40s %10s%n", "----------------------------------------", "----------"));
    for (Fitting fitting : fittingList) {
      sb.append(String.format("%-40s %10.1f%n", fitting.getFittingName(), fitting.getLtoD()));
    }
    sb.append(
        String.format("%-40s %10s%n", "----------------------------------------", "----------"));
    sb.append(String.format("%-40s %10.1f%n", "TOTAL L/D", getTotalLdRatio()));
    return sb.toString();
  }

  /**
   * Inner class representing a single pipe fitting.
   *
   * <p>
   * Each fitting has a name and an L/D ratio that defines its equivalent length contribution to
   * pressure drop calculations.
   * </p>
   */
  public class Fitting implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1001;

    /** Descriptive name of the fitting. */
    private String fittingName = "";

    /** Equivalent length ratio (L/D). */
    private double LtoD = 1.0;

    /** Optional K-factor (resistance coefficient). */
    private double kFactor = 0.0;

    /**
     * Constructor with explicit L/D ratio.
     *
     * @param name fitting name
     * @param LdivD equivalent length ratio (L/D)
     */
    public Fitting(String name, double LdivD) {
      this.fittingName = name;
      this.LtoD = LdivD;
    }

    /**
     * Constructor that loads L/D from database.
     *
     * <p>
     * Attempts to load the fitting data from the 'fittings' database table. If not found, attempts
     * to match against standard fitting types.
     * </p>
     *
     * @param name fitting name as stored in database
     */
    public Fitting(String name) {
      this.fittingName = name;

      try (neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();
          java.sql.ResultSet dataSet =
              database.getResultSet(("SELECT * FROM fittings WHERE name='" + name + "'"))) {
        if (dataSet.next()) {
          LtoD = Double.parseDouble(dataSet.getString("LtoD"));
          logger.debug("Loaded fitting '{}' with L/D = {}", name, LtoD);
        } else {
          // Try standard values as fallback
          Double stdValue = STANDARD_LD_VALUES.get(name.toLowerCase());
          if (stdValue != null) {
            LtoD = stdValue;
            logger.debug("Using standard L/D value for '{}': {}", name, LtoD);
          } else {
            logger.warn("Fitting '{}' not found in database or standard values. Using L/D = 1.0",
                name);
            LtoD = 1.0;
          }
        }
      } catch (Exception ex) {
        logger.error("Error loading fitting '{}': {}", name, ex.getMessage());
        // Try standard values as fallback
        Double stdValue = STANDARD_LD_VALUES.get(name.toLowerCase());
        if (stdValue != null) {
          LtoD = stdValue;
        }
      }
    }

    /**
     * Get the fitting name.
     *
     * @return fitting name
     */
    public String getFittingName() {
      return fittingName;
    }

    /**
     * Set the fitting name.
     *
     * @param fittingName new fitting name
     */
    public void setFittingName(String fittingName) {
      this.fittingName = fittingName;
    }

    /**
     * Get the equivalent length ratio (L/D).
     *
     * @return L/D ratio (dimensionless)
     */
    public double getLtoD() {
      return LtoD;
    }

    /**
     * Set the equivalent length ratio (L/D).
     *
     * @param LtoD new L/D ratio
     */
    public void setLtoD(double LtoD) {
      this.LtoD = LtoD;
    }

    /**
     * Get the K-factor (resistance coefficient).
     *
     * <p>
     * The K-factor relates to L/D by: K = f × (L/D) where f is the friction factor.
     * </p>
     *
     * @return K-factor (dimensionless)
     */
    public double getKFactor() {
      return kFactor;
    }

    /**
     * Set the K-factor (resistance coefficient).
     *
     * @param kFactor new K-factor
     */
    public void setKFactor(double kFactor) {
      this.kFactor = kFactor;
    }

    /**
     * Calculate equivalent length for a given pipe diameter.
     *
     * @param diameter pipe internal diameter in meters
     * @return equivalent length in meters
     */
    public double getEquivalentLength(double diameter) {
      return LtoD * diameter;
    }

    @Override
    public String toString() {
      return String.format("Fitting[name=%s, L/D=%.1f]", fittingName, LtoD);
    }
  }
}
