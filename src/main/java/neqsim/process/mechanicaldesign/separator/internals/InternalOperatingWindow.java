package neqsim.process.mechanicaldesign.separator.internals;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Describes the Souders-Brown K-factor operating window of a separator internal (mist eliminator, vane pack, cyclone
 * deck) and where the current operating point sits inside that window.
 *
 * <p>
 * Demisting internals only perform well within a band of gas load. The band is expressed as a K-factor window
 * [K<sub>min</sub>, K<sub>max</sub>] where:
 * </p>
 *
 * <table>
 * <caption>K-factor window interpretation</caption>
 * <tr>
 * <th>Region</th>
 * <th>Physical meaning</th>
 * </tr>
 * <tr>
 * <td>K &lt; K<sub>min</sub></td>
 * <td>Below the effective turndown limit: too little inertial capture and poor film drainage / coalescence, small
 * droplets slip through.</td>
 * </tr>
 * <tr>
 * <td>K<sub>min</sub> &le; K &le; K<sub>max</sub></td>
 * <td>Good performance band: the internal operates at its rated grade efficiency.</td>
 * </tr>
 * <tr>
 * <td>K &gt; K<sub>max</sub></td>
 * <td>Above the capacity limit: flooding and re-entrainment of captured liquid off the element face.</td>
 * </tr>
 * </table>
 *
 * <p>
 * K-factor limits are sourced from the {@code SeparatorInternalsDatabase} (open literature: Brunazzi &amp; Paglianti
 * 1998, Phillips &amp; Listak 1996, Hoffmann &amp; Stein 2008, GPSA Engineering Data Book).
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class InternalOperatingWindow implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /**
   * Classification of the operating point relative to the K-factor window.
   */
  public enum WindowStatus {
    /** Operating below the minimum K-factor (turndown / coalescence limited). */
    BELOW_MIN_TURNDOWN,
    /** Operating inside the recommended K-factor window (good performance). */
    IN_RANGE,
    /** Operating above the maximum K-factor (flooding / re-entrainment risk). */
    ABOVE_MAX_FLOODING,
    /** Window limits are not defined, status cannot be determined. */
    UNKNOWN
  }

  /** Internal name (e.g. "mist eliminator"). */
  private String name = "";

  /** Internal type ("wire_mesh", "vane_pack", "cyclone"). */
  private String type = "";

  /** Database sub-type description (e.g. "High Efficiency"). */
  private String subType = "";

  /** Minimum recommended Souders-Brown K-factor [m/s]. */
  private double minKFactor = 0.0;

  /** Maximum recommended Souders-Brown K-factor [m/s]. */
  private double maxKFactor = 0.0;

  /** Operating Souders-Brown K-factor [m/s]. */
  private double operatingKFactor = 0.0;

  /** Nominal (rated) grade efficiency of the internal [0-1]. */
  private double ratedEfficiency = Double.NaN;

  /** Literature reference for the window data. */
  private String reference = "";

  /** Classification of the operating point. */
  private WindowStatus status = WindowStatus.UNKNOWN;

  /**
   * Constructs an empty operating window.
   */
  public InternalOperatingWindow() {
  }

  /**
   * Constructs an operating window and classifies the operating point.
   *
   * @param name internal name
   * @param type internal type ("wire_mesh", "vane_pack", "cyclone")
   * @param subType database sub-type description
   * @param minKFactor minimum recommended K-factor [m/s]
   * @param maxKFactor maximum recommended K-factor [m/s]
   * @param operatingKFactor operating K-factor [m/s]
   * @param ratedEfficiency nominal grade efficiency [0-1]
   * @param reference literature reference
   */
  public InternalOperatingWindow(String name, String type, String subType, double minKFactor, double maxKFactor,
      double operatingKFactor, double ratedEfficiency, String reference) {
    this.name = name;
    this.type = type;
    this.subType = subType;
    this.minKFactor = minKFactor;
    this.maxKFactor = maxKFactor;
    this.operatingKFactor = operatingKFactor;
    this.ratedEfficiency = ratedEfficiency;
    this.reference = reference;
    classify();
  }

  /**
   * Classifies the operating point relative to the K-factor window and stores the result in {@link #status}.
   */
  public final void classify() {
    if (maxKFactor <= 0.0) {
      status = WindowStatus.UNKNOWN;
    } else if (minKFactor > 0.0 && operatingKFactor < minKFactor) {
      status = WindowStatus.BELOW_MIN_TURNDOWN;
    } else if (operatingKFactor > maxKFactor) {
      status = WindowStatus.ABOVE_MAX_FLOODING;
    } else {
      status = WindowStatus.IN_RANGE;
    }
  }

  /**
   * Returns whether the operating point is inside the recommended K-factor window.
   *
   * @return true if the status is {@link WindowStatus#IN_RANGE}
   */
  public boolean isInRange() {
    return status == WindowStatus.IN_RANGE;
  }

  /**
   * Returns the K-factor utilization (operating K / maximum K). A value of 1.0 corresponds to the capacity limit.
   *
   * @return utilization [-], or 0.0 if the maximum K-factor is not defined
   */
  public double getUtilization() {
    return (maxKFactor > 0.0) ? operatingKFactor / maxKFactor : 0.0;
  }

  /**
   * Returns the turndown ratio (operating K / minimum K). A value of 1.0 corresponds to the low-load limit.
   *
   * @return turndown ratio [-], or 0.0 if the minimum K-factor is not defined
   */
  public double getTurndownRatio() {
    return (minKFactor > 0.0) ? operatingKFactor / minKFactor : 0.0;
  }

  /**
   * Builds a map representation of this window for serialization or reporting.
   *
   * @return an ordered map of window properties
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("name", name);
    map.put("type", type);
    map.put("subType", subType);
    map.put("minKFactor_m_s", minKFactor);
    map.put("maxKFactor_m_s", maxKFactor);
    map.put("operatingKFactor_m_s", operatingKFactor);
    map.put("utilization", getUtilization());
    map.put("turndownRatio", getTurndownRatio());
    map.put("ratedEfficiency", ratedEfficiency);
    map.put("status", status.name());
    map.put("reference", reference);
    return map;
  }

  /**
   * Serializes this window to a pretty-printed JSON string.
   *
   * @return JSON representation
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(toMap());
  }

  /**
   * Gets the internal name.
   *
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * Sets the internal name.
   *
   * @param name the name to set
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Gets the internal type.
   *
   * @return the type
   */
  public String getType() {
    return type;
  }

  /**
   * Sets the internal type.
   *
   * @param type the type to set
   */
  public void setType(String type) {
    this.type = type;
  }

  /**
   * Gets the database sub-type description.
   *
   * @return the sub-type
   */
  public String getSubType() {
    return subType;
  }

  /**
   * Sets the database sub-type description.
   *
   * @param subType the sub-type to set
   */
  public void setSubType(String subType) {
    this.subType = subType;
  }

  /**
   * Gets the minimum recommended K-factor.
   *
   * @return minimum K-factor [m/s]
   */
  public double getMinKFactor() {
    return minKFactor;
  }

  /**
   * Sets the minimum recommended K-factor and re-classifies.
   *
   * @param minKFactor minimum K-factor [m/s]
   */
  public void setMinKFactor(double minKFactor) {
    this.minKFactor = minKFactor;
    classify();
  }

  /**
   * Gets the maximum recommended K-factor.
   *
   * @return maximum K-factor [m/s]
   */
  public double getMaxKFactor() {
    return maxKFactor;
  }

  /**
   * Sets the maximum recommended K-factor and re-classifies.
   *
   * @param maxKFactor maximum K-factor [m/s]
   */
  public void setMaxKFactor(double maxKFactor) {
    this.maxKFactor = maxKFactor;
    classify();
  }

  /**
   * Gets the operating K-factor.
   *
   * @return operating K-factor [m/s]
   */
  public double getOperatingKFactor() {
    return operatingKFactor;
  }

  /**
   * Sets the operating K-factor and re-classifies.
   *
   * @param operatingKFactor operating K-factor [m/s]
   */
  public void setOperatingKFactor(double operatingKFactor) {
    this.operatingKFactor = operatingKFactor;
    classify();
  }

  /**
   * Gets the nominal (rated) grade efficiency.
   *
   * @return rated efficiency [0-1]
   */
  public double getRatedEfficiency() {
    return ratedEfficiency;
  }

  /**
   * Sets the nominal (rated) grade efficiency.
   *
   * @param ratedEfficiency rated efficiency [0-1]
   */
  public void setRatedEfficiency(double ratedEfficiency) {
    this.ratedEfficiency = ratedEfficiency;
  }

  /**
   * Gets the literature reference.
   *
   * @return the reference
   */
  public String getReference() {
    return reference;
  }

  /**
   * Sets the literature reference.
   *
   * @param reference the reference to set
   */
  public void setReference(String reference) {
    this.reference = reference;
  }

  /**
   * Gets the classification of the operating point.
   *
   * @return the window status
   */
  public WindowStatus getStatus() {
    return status;
  }
}
