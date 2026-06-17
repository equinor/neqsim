package neqsim.pvtsimulation.flowassurance;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Maps hydrate formation risk along a pipeline pressure-temperature profile.
 *
 * <p>
 * Takes a series of (pressure, temperature) points along a pipeline and calculates the hydrate
 * formation temperature at each pressure. The subcooling at each point (actual T minus hydrate T)
 * indicates the risk of hydrate formation. Negative subcooling means the fluid is in the hydrate
 * formation region.
 * </p>
 *
 * <h2>Risk Classification:</h2>
 *
 * <table>
 * <caption>Hydrate risk levels based on subcooling</caption>
 * <tr>
 * <th>Subcooling (deltaT)</th>
 * <th>Risk Level</th>
 * </tr>
 * <tr>
 * <td>deltaT &lt; 0 C</td>
 * <td>CRITICAL - inside hydrate region</td>
 * </tr>
 * <tr>
 * <td>0 &lt;= deltaT &lt; 3 C</td>
 * <td>HIGH - close to hydrate curve</td>
 * </tr>
 * <tr>
 * <td>3 &lt;= deltaT &lt; 6 C</td>
 * <td>MEDIUM - moderate margin</td>
 * </tr>
 * <tr>
 * <td>deltaT &gt;= 6 C</td>
 * <td>LOW - safe margin</td>
 * </tr>
 * </table>
 *
 * <h2>Usage:</h2>
 *
 * <pre>
 * SystemInterface fluid = new SystemSrkEos(273.15 + 25, 60);
 * fluid.addComponent("methane", 0.9);
 * fluid.addComponent("ethane", 0.06);
 * fluid.addComponent("propane", 0.02);
 * fluid.addComponent("water", 0.02);
 * fluid.setMixingRule("classic");
 * fluid.setMultiPhaseCheck(true);
 *
 * HydrateRiskMapper mapper = new HydrateRiskMapper(fluid);
 * mapper.addProfilePoint(0.0, 100.0, 30.0); // km, bara, C
 * mapper.addProfilePoint(10.0, 95.0, 25.0);
 * mapper.addProfilePoint(25.0, 80.0, 15.0);
 * mapper.addProfilePoint(50.0, 60.0, 8.0);
 *
 * HydrateRiskMapper.RiskProfile profile = mapper.calculate();
 * System.out.println(profile.toJson());
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 * @see ThermodynamicOperations#hydrateFormationTemperature()
 */
public class HydrateRiskMapper implements Serializable {

  /** Serialization version. */
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(HydrateRiskMapper.class);

  /** Base fluid composition (will be cloned for each calculation). */
  private final SystemInterface baseFluid;

  /** Pipeline profile points. */
  private final List<ProfilePoint> profilePoints = new ArrayList<>();

  /** Critical subcooling threshold in Kelvin/C. */
  private double criticalSubcoolingK = 0.0;

  /** High risk subcooling threshold in Kelvin/C. */
  private double highRiskSubcoolingK = 3.0;

  /** Medium risk subcooling threshold in Kelvin/C. */
  private double mediumRiskSubcoolingK = 6.0;

  /**
   * Hydrate risk classification levels.
   */
  public enum RiskLevel {
    /** Inside hydrate region (subcooling &lt; 0). */
    CRITICAL,
    /** Close to hydrate curve (subcooling &lt; 3 C). */
    HIGH,
    /** Moderate margin (subcooling &lt; 6 C). */
    MEDIUM,
    /** Safe margin (subcooling &gt;= 6 C). */
    LOW
  }

  /**
   * Creates a HydrateRiskMapper for the given fluid composition.
   *
   * @param baseFluid the fluid system (should include water for hydrate calculations)
   */
  public HydrateRiskMapper(SystemInterface baseFluid) {
    if (baseFluid == null) {
      throw new IllegalArgumentException("Base fluid cannot be null");
    }
    this.baseFluid = baseFluid;
  }

  /**
   * Adds a point along the pipeline profile.
   *
   * @param distanceKm distance from inlet in kilometers
   * @param pressureBara pressure at this point in bara
   * @param temperatureC temperature at this point in degrees Celsius
   * @return this for chaining
   */
  public HydrateRiskMapper addProfilePoint(double distanceKm, double pressureBara,
      double temperatureC) {
    profilePoints.add(new ProfilePoint(distanceKm, pressureBara, temperatureC));
    return this;
  }

  /**
   * Sets the subcooling thresholds for risk classification.
   *
   * @param highRiskC subcooling below which risk is HIGH (default 3.0 C)
   * @param mediumRiskC subcooling below which risk is MEDIUM (default 6.0 C)
   * @return this for chaining
   */
  public HydrateRiskMapper setRiskThresholds(double highRiskC, double mediumRiskC) {
    this.highRiskSubcoolingK = highRiskC;
    this.mediumRiskSubcoolingK = mediumRiskC;
    return this;
  }

  /**
   * Calculates hydrate formation temperatures and risk levels at all profile points.
   *
   * @return risk profile with results at each point
   */
  public RiskProfile calculate() {
    if (profilePoints.isEmpty()) {
      throw new IllegalStateException("No profile points defined. Call addProfilePoint() first.");
    }

    List<RiskPoint> results = new ArrayList<>();
    RiskLevel worstRisk = RiskLevel.LOW;
    double minSubcooling = Double.MAX_VALUE;
    int criticalCount = 0;

    for (ProfilePoint point : profilePoints) {
      double hydrateTC;
      try {
        SystemInterface fluid = baseFluid.clone();
        fluid.setTemperature(point.temperatureC + 273.15);
        fluid.setPressure(point.pressureBara);
        ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
        ops.hydrateFormationTemperature();
        hydrateTC = fluid.getTemperature("C");
      } catch (Exception e) {
        logger.warn("Hydrate calculation failed at {} km, {} bara: {}", point.distanceKm,
            point.pressureBara, e.getMessage());
        hydrateTC = Double.NaN;
      }

      double subcooling = Double.isNaN(hydrateTC) ? Double.NaN : point.temperatureC - hydrateTC;

      RiskLevel risk;
      if (Double.isNaN(subcooling)) {
        risk = RiskLevel.LOW; // assume safe if calculation failed
      } else if (subcooling < criticalSubcoolingK) {
        risk = RiskLevel.CRITICAL;
        criticalCount++;
      } else if (subcooling < highRiskSubcoolingK) {
        risk = RiskLevel.HIGH;
      } else if (subcooling < mediumRiskSubcoolingK) {
        risk = RiskLevel.MEDIUM;
      } else {
        risk = RiskLevel.LOW;
      }

      if (risk.ordinal() < worstRisk.ordinal()) {
        worstRisk = risk;
      }
      if (!Double.isNaN(subcooling) && subcooling < minSubcooling) {
        minSubcooling = subcooling;
      }

      results.add(new RiskPoint(point.distanceKm, point.pressureBara, point.temperatureC, hydrateTC,
          subcooling, risk));
    }

    return new RiskProfile(results, worstRisk, minSubcooling, criticalCount);
  }

  // ============================================================
  // Internal data classes
  // ============================================================

  /**
   * A point along the pipeline profile.
   */
  private static class ProfilePoint implements Serializable {
    private static final long serialVersionUID = 1L;
    final double distanceKm;
    final double pressureBara;
    final double temperatureC;

    /**
     * Creates a profile point.
     *
     * @param distanceKm distance from inlet in km
     * @param pressureBara pressure in bara
     * @param temperatureC temperature in C
     */
    ProfilePoint(double distanceKm, double pressureBara, double temperatureC) {
      this.distanceKm = distanceKm;
      this.pressureBara = pressureBara;
      this.temperatureC = temperatureC;
    }
  }

  /**
   * Risk assessment at a single pipeline point.
   */
  public static class RiskPoint implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Distance from inlet in km. */
    public final double distanceKm;
    /** Pressure at this point in bara. */
    public final double pressureBara;
    /** Actual temperature at this point in C. */
    public final double actualTemperatureC;
    /** Hydrate formation temperature at this pressure in C. */
    public final double hydrateTemperatureC;
    /** Subcooling margin (actual T - hydrate T) in C. */
    public final double subcoolingC;
    /** Risk classification. */
    public final RiskLevel riskLevel;

    /**
     * Creates a risk point.
     *
     * @param distanceKm distance from inlet
     * @param pressureBara pressure
     * @param actualTemperatureC actual temperature
     * @param hydrateTemperatureC hydrate formation temperature
     * @param subcoolingC subcooling margin
     * @param riskLevel risk classification
     */
    RiskPoint(double distanceKm, double pressureBara, double actualTemperatureC,
        double hydrateTemperatureC, double subcoolingC, RiskLevel riskLevel) {
      this.distanceKm = distanceKm;
      this.pressureBara = pressureBara;
      this.actualTemperatureC = actualTemperatureC;
      this.hydrateTemperatureC = hydrateTemperatureC;
      this.subcoolingC = subcoolingC;
      this.riskLevel = riskLevel;
    }
  }

  /**
   * Complete hydrate risk profile along the pipeline.
   */
  public static class RiskProfile implements Serializable {
    private static final long serialVersionUID = 1L;

    private final List<RiskPoint> points;
    private final RiskLevel overallRisk;
    private final double minimumSubcoolingC;
    private final int criticalPointCount;

    /**
     * Creates a risk profile.
     *
     * @param points list of risk points along the pipeline
     * @param overallRisk worst risk level across all points
     * @param minimumSubcoolingC minimum subcooling margin
     * @param criticalPointCount number of points in CRITICAL region
     */
    RiskProfile(List<RiskPoint> points, RiskLevel overallRisk, double minimumSubcoolingC,
        int criticalPointCount) {
      this.points = points;
      this.overallRisk = overallRisk;
      this.minimumSubcoolingC = minimumSubcoolingC;
      this.criticalPointCount = criticalPointCount;
    }

    /**
     * Gets all risk assessment points.
     *
     * @return list of risk points
     */
    public List<RiskPoint> getPoints() {
      return points;
    }

    /**
     * Gets the overall (worst) risk level.
     *
     * @return overall risk level
     */
    public RiskLevel getOverallRisk() {
      return overallRisk;
    }

    /**
     * Gets the minimum subcooling margin across all points.
     *
     * @return minimum subcooling in degrees C
     */
    public double getMinimumSubcoolingC() {
      return minimumSubcoolingC;
    }

    /**
     * Gets the number of points in the CRITICAL (hydrate) region.
     *
     * @return count of critical points
     */
    public int getCriticalPointCount() {
      return criticalPointCount;
    }

    /**
     * Converts the risk profile to a JSON string.
     *
     * @return JSON representation
     */
    public String toJson() {
      JsonObject json = new JsonObject();
      json.addProperty("overallRisk", overallRisk.name());
      json.addProperty("minimumSubcooling_C", minimumSubcoolingC);
      json.addProperty("criticalPointCount", criticalPointCount);
      json.addProperty("totalPoints", points.size());

      JsonArray pointsArray = new JsonArray();
      for (RiskPoint rp : points) {
        JsonObject pj = new JsonObject();
        pj.addProperty("distance_km", rp.distanceKm);
        pj.addProperty("pressure_bara", rp.pressureBara);
        pj.addProperty("actualTemperature_C", rp.actualTemperatureC);
        pj.addProperty("hydrateTemperature_C", rp.hydrateTemperatureC);
        pj.addProperty("subcooling_C", rp.subcoolingC);
        pj.addProperty("riskLevel", rp.riskLevel.name());
        pointsArray.add(pj);
      }
      json.add("profile", pointsArray);

      return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
          .toJson(json);
    }

    /**
     * Converts the risk profile to CSV format.
     *
     * @return CSV string with header
     */
    public String toCSV() {
      StringBuilder sb = new StringBuilder();
      sb.append(
          "Distance (km),Pressure (bara),Actual T (C),Hydrate T (C),Subcooling (C),Risk Level\n");
      for (RiskPoint rp : points) {
        sb.append(rp.distanceKm).append(",");
        sb.append(rp.pressureBara).append(",");
        sb.append(rp.actualTemperatureC).append(",");
        sb.append(rp.hydrateTemperatureC).append(",");
        sb.append(rp.subcoolingC).append(",");
        sb.append(rp.riskLevel.name()).append("\n");
      }
      return sb.toString();
    }
  }
}
