package neqsim.process.safety.rupture;

import com.google.gson.GsonBuilder;
import java.io.Serializable;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.util.database.NeqSimProcessDesignDataBase;

/**
 * Temperature-dependent material strength curve for fire rupture screening.
 *
 * <p>
 * The curve stores room-temperature yield and tensile strength together with a generic retained
 * strength factor versus metal temperature. The default carbon-steel curve is intended for
 * screening studies based on industry practice where detailed certified material data is not yet
 * available. Project-specific studies should replace the curve with verified values from the
 * applicable material certificate, pressure-piping code, or finite-element assessment.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class MaterialStrengthCurve implements Serializable {
  private static final long serialVersionUID = 1L;

  private static final Logger logger = LogManager.getLogger(MaterialStrengthCurve.class);

  private static final double PSI_TO_PA = 6894.76;
  private static final double MPA_TO_PA = 1.0e6;

  private final String materialName;
  private final double ambientYieldStrengthPa;
  private final double ambientTensileStrengthPa;
  private final double[] temperatureK;
  private final double[] retainedStrengthFactor;
  private final String dataSource;

  /**
   * Creates a material strength curve.
   *
   * @param materialName material or grade name; must not be empty
   * @param ambientYieldStrengthPa yield strength at ambient temperature in Pa; must be positive
   * @param ambientTensileStrengthPa tensile strength at ambient temperature in Pa; must be positive
   * @param temperatureK temperature grid in K; must be strictly increasing
   * @param retainedStrengthFactor retained strength factors on the temperature grid; each value
   *        must be positive
   * @param dataSource short description of the data source or assumption basis
   * @throws IllegalArgumentException if the curve input is invalid
   */
  public MaterialStrengthCurve(String materialName, double ambientYieldStrengthPa,
      double ambientTensileStrengthPa, double[] temperatureK, double[] retainedStrengthFactor,
      String dataSource) {
    if (materialName == null || materialName.trim().isEmpty()) {
      throw new IllegalArgumentException("materialName must not be empty");
    }
    validatePositive(ambientYieldStrengthPa, "ambientYieldStrengthPa");
    validatePositive(ambientTensileStrengthPa, "ambientTensileStrengthPa");
    validateCurve(temperatureK, retainedStrengthFactor);
    this.materialName = materialName.trim();
    this.ambientYieldStrengthPa = ambientYieldStrengthPa;
    this.ambientTensileStrengthPa = ambientTensileStrengthPa;
    this.temperatureK = Arrays.copyOf(temperatureK, temperatureK.length);
    this.retainedStrengthFactor =
        Arrays.copyOf(retainedStrengthFactor, retainedStrengthFactor.length);
    this.dataSource = dataSource == null ? "" : dataSource.trim();
  }

  /**
   * Creates a generic carbon-steel curve from ambient strengths.
   *
   * @param materialName material name
   * @param yieldStrengthPa ambient yield strength in Pa
   * @param tensileStrengthPa ambient tensile strength in Pa
   * @return generic carbon-steel temperature-strength curve
   */
  public static MaterialStrengthCurve carbonSteel(String materialName, double yieldStrengthPa,
      double tensileStrengthPa) {
    return new MaterialStrengthCurve(materialName, yieldStrengthPa, tensileStrengthPa,
        defaultCarbonSteelTemperatures(), defaultCarbonSteelFactors(),
        "Generic carbon-steel retained-strength curve for fire screening");
  }

  /**
   * Loads an API 5L pipe grade from the NeqSim design database.
   *
   * <p>
   * If the database table is unavailable, a small built-in fallback table is used for common API 5L
   * grades. Strengths from {@code MaterialPipeProperties} are stored in psi and converted to Pa.
   * </p>
   *
   * @param grade API 5L grade, for example B, X52, X65, or X70
   * @return material strength curve using the generic carbon-steel temperature reduction
   * @throws IllegalArgumentException if the grade is empty
   */
  public static MaterialStrengthCurve forApi5LPipeGrade(String grade) {
    if (grade == null || grade.trim().isEmpty()) {
      throw new IllegalArgumentException("grade must not be empty");
    }
    String normalizedGrade = grade.trim();
    double[] strengths = queryApi5LStrengths(normalizedGrade);
    if (strengths == null) {
      strengths = fallbackApi5LStrengths(normalizedGrade);
    }
    return new MaterialStrengthCurve("API 5L " + normalizedGrade, strengths[0], strengths[1],
        defaultCarbonSteelTemperatures(), defaultCarbonSteelFactors(),
        "API 5L ambient SMYS/SMTS with generic fire-temperature reduction");
  }

  /**
   * Gets the material name.
   *
   * @return material name
   */
  public String getMaterialName() {
    return materialName;
  }

  /**
   * Gets the ambient yield strength.
   *
   * @return yield strength in Pa
   */
  public double getAmbientYieldStrengthPa() {
    return ambientYieldStrengthPa;
  }

  /**
   * Gets the ambient tensile strength.
   *
   * @return tensile strength in Pa
   */
  public double getAmbientTensileStrengthPa() {
    return ambientTensileStrengthPa;
  }

  /**
   * Gets the retained-strength data source description.
   *
   * @return data source description
   */
  public String getDataSource() {
    return dataSource;
  }

  /**
   * Calculates retained-strength factor by linear interpolation.
   *
   * @param metalTemperatureK metal temperature in K; must be positive
   * @return retained strength factor at the specified temperature
   * @throws IllegalArgumentException if the temperature is non-positive
   */
  public double retainedStrengthFactor(double metalTemperatureK) {
    validatePositive(metalTemperatureK, "metalTemperatureK");
    return interpolate(metalTemperatureK, temperatureK, retainedStrengthFactor);
  }

  /**
   * Calculates yield strength at metal temperature.
   *
   * @param metalTemperatureK metal temperature in K; must be positive
   * @return yield strength in Pa
   */
  public double yieldStrengthAt(double metalTemperatureK) {
    return ambientYieldStrengthPa * retainedStrengthFactor(metalTemperatureK);
  }

  /**
   * Calculates tensile strength at metal temperature.
   *
   * @param metalTemperatureK metal temperature in K; must be positive
   * @return tensile strength in Pa
   */
  public double tensileStrengthAt(double metalTemperatureK) {
    return ambientTensileStrengthPa * retainedStrengthFactor(metalTemperatureK);
  }

  /**
   * Calculates allowable rupture stress at metal temperature.
   *
   * @param metalTemperatureK metal temperature in K; must be positive
   * @param tensileStrengthFactor fraction of retained tensile strength accepted for rupture
   *        screening; must be positive
   * @return allowable stress in Pa
   */
  public double allowableRuptureStressAt(double metalTemperatureK, double tensileStrengthFactor) {
    validatePositive(tensileStrengthFactor, "tensileStrengthFactor");
    return tensileStrengthAt(metalTemperatureK) * tensileStrengthFactor;
  }

  /**
   * Converts the curve metadata to a JSON-friendly map.
   *
   * @return ordered map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("materialName", materialName);
    map.put("ambientYieldStrengthMPa", ambientYieldStrengthPa / MPA_TO_PA);
    map.put("ambientTensileStrengthMPa", ambientTensileStrengthPa / MPA_TO_PA);
    map.put("temperatureK", copyToList(temperatureK));
    map.put("retainedStrengthFactor", copyToList(retainedStrengthFactor));
    map.put("dataSource", dataSource);
    return map;
  }

  /**
   * Converts the curve metadata to JSON.
   *
   * @return JSON representation
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(toMap());
  }

  /**
   * Queries ambient API 5L strengths from the process design database.
   *
   * @param grade API 5L grade
   * @return two-element array with yield and tensile strength in Pa, or null when not found
   */
  private static double[] queryApi5LStrengths(String grade) {
    try (NeqSimProcessDesignDataBase database = new NeqSimProcessDesignDataBase()) {
      String safeGrade = grade.replace("'", "''");
      String query = "SELECT minimumYeildStrength, minimumTensileStrength "
          + "FROM MaterialPipeProperties WHERE grade='" + safeGrade + "'";
      try (ResultSet dataSet = database.getResultSet(query)) {
        if (dataSet.next()) {
          double yieldPa = dataSet.getDouble("minimumYeildStrength") * PSI_TO_PA;
          double tensilePa = dataSet.getDouble("minimumTensileStrength") * PSI_TO_PA;
          if (yieldPa > 0.0 && tensilePa > 0.0) {
            return new double[] {yieldPa, tensilePa};
          }
        }
      }
    } catch (Exception ex) {
      logger.debug("Could not load API 5L material grade {} from design database: {}", grade,
          ex.getMessage());
    }
    return null;
  }

  /**
   * Gets fallback ambient API 5L strengths.
   *
   * @param grade API 5L grade
   * @return two-element array with yield and tensile strength in Pa
   */
  private static double[] fallbackApi5LStrengths(String grade) {
    String key = grade.trim().toUpperCase();
    if ("B".equals(key) || "GRADE B".equals(key)) {
      return psiStrengths(35000.0, 60000.0);
    }
    if ("X42".equals(key)) {
      return psiStrengths(42000.0, 60000.0);
    }
    if ("X52".equals(key)) {
      return psiStrengths(52000.0, 66000.0);
    }
    if ("X60".equals(key)) {
      return psiStrengths(60000.0, 75000.0);
    }
    if ("X65".equals(key)) {
      return psiStrengths(65000.0, 77000.0);
    }
    if ("X70".equals(key)) {
      return psiStrengths(70000.0, 82000.0);
    }
    if ("X80".equals(key)) {
      return psiStrengths(80000.0, 90000.0);
    }
    return psiStrengths(35000.0, 60000.0);
  }

  /**
   * Converts psi strengths to Pa.
   *
   * @param yieldPsi yield strength in psi
   * @param tensilePsi tensile strength in psi
   * @return two-element array with yield and tensile strength in Pa
   */
  private static double[] psiStrengths(double yieldPsi, double tensilePsi) {
    return new double[] {yieldPsi * PSI_TO_PA, tensilePsi * PSI_TO_PA};
  }

  /**
   * Gets the default carbon-steel temperature grid.
   *
   * @return temperature grid in K
   */
  private static double[] defaultCarbonSteelTemperatures() {
    return new double[] {293.15, 373.15, 473.15, 573.15, 673.15, 773.15, 873.15, 973.15, 1073.15};
  }

  /**
   * Gets the default retained-strength factors for carbon steel.
   *
   * @return retained-strength factors
   */
  private static double[] defaultCarbonSteelFactors() {
    return new double[] {1.00, 0.95, 0.85, 0.72, 0.52, 0.32, 0.16, 0.07, 0.03};
  }

  /**
   * Validates a strength curve grid.
   *
   * @param temperatures temperature grid in K
   * @param factors retained-strength factors
   * @throws IllegalArgumentException if the curve is invalid
   */
  private static void validateCurve(double[] temperatures, double[] factors) {
    if (temperatures == null || factors == null || temperatures.length != factors.length
        || temperatures.length < 2) {
      throw new IllegalArgumentException(
          "temperature and factor arrays must have equal length >= 2");
    }
    double previous = 0.0;
    for (int i = 0; i < temperatures.length; i++) {
      validatePositive(temperatures[i], "temperatureK[" + i + "]");
      validatePositive(factors[i], "retainedStrengthFactor[" + i + "]");
      if (i > 0 && temperatures[i] <= previous) {
        throw new IllegalArgumentException("temperature grid must be strictly increasing");
      }
      previous = temperatures[i];
    }
  }

  /**
   * Validates that a numeric value is positive and finite.
   *
   * @param value value to validate
   * @param name parameter name used in exception messages
   * @throws IllegalArgumentException if the value is invalid
   */
  private static void validatePositive(double value, String name) {
    if (value <= 0.0 || Double.isNaN(value) || Double.isInfinite(value)) {
      throw new IllegalArgumentException(name + " must be positive and finite");
    }
  }

  /**
   * Interpolates a value on a piecewise-linear curve.
   *
   * @param x interpolation coordinate
   * @param xs x-grid
   * @param ys y-grid
   * @return interpolated or endpoint-clamped value
   */
  private static double interpolate(double x, double[] xs, double[] ys) {
    if (x <= xs[0]) {
      return ys[0];
    }
    int last = xs.length - 1;
    if (x >= xs[last]) {
      return ys[last];
    }
    for (int i = 1; i < xs.length; i++) {
      if (x <= xs[i]) {
        double fraction = (x - xs[i - 1]) / (xs[i] - xs[i - 1]);
        return ys[i - 1] + fraction * (ys[i] - ys[i - 1]);
      }
    }
    return ys[last];
  }

  /**
   * Copies a double array to a JSON-friendly list.
   *
   * @param values array values
   * @return ordered list representation
   */
  private static java.util.List<Double> copyToList(double[] values) {
    java.util.List<Double> list = new java.util.ArrayList<Double>();
    for (double value : values) {
      list.add(value);
    }
    return list;
  }
}
