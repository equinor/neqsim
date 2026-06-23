package neqsim.process.safety.rupture;

import com.google.gson.GsonBuilder;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Temperature-dependent material properties for blowdown pipe fire-rupture strain-rate studies.
 *
 * <p>
 * The curve stores the material properties used by the legacy strain-rate workbook: heat capacity, thermal
 * conductivity, ultimate tensile strength, strain-rate multiplier, rupture strain limit, steel density, and
 * Sellars-Tegart creep/strain constants. Temperatures are represented in degrees Celsius to align with
 * material-certificate and spreadsheet tables.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class PipeFireRuptureMaterial implements Serializable {
  private static final long serialVersionUID = 1L;

  private final String materialName;
  private final double[] temperatureC;
  private final double[] heatCapacityJPerKgK;
  private final double[] thermalConductivityWPerMK;
  private final double[] ultimateTensileStrengthMPa;
  private final double[] strainEffectFactor;
  private final double[] ruptureStrainLimit;
  private final double densityKgPerM3;
  private final double sellarsTegartA;
  private final double sellarsTegartAlpha;
  private final double sellarsTegartN;
  private final double activationTemperatureK;
  private final double correctionStrainRatePerMinute;
  private final double correctionLimitTemperatureC;
  private final String dataSource;

  /**
   * Creates a material curve for pipe fire-rupture strain-rate calculations.
   *
   * @param materialName material name; must not be empty
   * @param temperatureC temperature grid in degrees Celsius; must be strictly increasing
   * @param heatCapacityJPerKgK heat capacity values in J/kg-K; must match the temperature grid
   * @param thermalConductivityWPerMK thermal conductivity values in W/m-K; must match the grid
   * @param ultimateTensileStrengthMPa UTS values in MPa; must match the grid
   * @param strainEffectFactor strain-rate multiplier values; must match the grid
   * @param ruptureStrainLimit limiting accumulated strain values; must match the grid
   * @param densityKgPerM3 material density in kg/m3; must be positive
   * @param sellarsTegartA Sellars-Tegart pre-exponential constant; must be positive
   * @param sellarsTegartAlpha Sellars-Tegart alpha constant in 1/MPa; must be positive
   * @param sellarsTegartN Sellars-Tegart exponent; must be positive
   * @param activationTemperatureK activation term Q/R in K; must be positive
   * @param correctionStrainRatePerMinute strain rate used to correct extrapolated temperatures; must be positive
   * @param correctionLimitTemperatureC temperature above which the correction factor is set to one
   * @param dataSource description of data source or assumption basis
   * @throws IllegalArgumentException if the material curve is invalid
   */
  public PipeFireRuptureMaterial(String materialName, double[] temperatureC, double[] heatCapacityJPerKgK,
      double[] thermalConductivityWPerMK, double[] ultimateTensileStrengthMPa, double[] strainEffectFactor,
      double[] ruptureStrainLimit, double densityKgPerM3, double sellarsTegartA, double sellarsTegartAlpha,
      double sellarsTegartN, double activationTemperatureK, double correctionStrainRatePerMinute,
      double correctionLimitTemperatureC, String dataSource) {
    validateName(materialName);
    validateCurves(temperatureC, heatCapacityJPerKgK, thermalConductivityWPerMK, ultimateTensileStrengthMPa,
	strainEffectFactor, ruptureStrainLimit);
    validatePositive(densityKgPerM3, "densityKgPerM3");
    validatePositive(sellarsTegartA, "sellarsTegartA");
    validatePositive(sellarsTegartAlpha, "sellarsTegartAlpha");
    validatePositive(sellarsTegartN, "sellarsTegartN");
    validatePositive(activationTemperatureK, "activationTemperatureK");
    validatePositive(correctionStrainRatePerMinute, "correctionStrainRatePerMinute");
    this.materialName = materialName.trim();
    this.temperatureC = Arrays.copyOf(temperatureC, temperatureC.length);
    this.heatCapacityJPerKgK = Arrays.copyOf(heatCapacityJPerKgK, heatCapacityJPerKgK.length);
    this.thermalConductivityWPerMK = Arrays.copyOf(thermalConductivityWPerMK, thermalConductivityWPerMK.length);
    this.ultimateTensileStrengthMPa = Arrays.copyOf(ultimateTensileStrengthMPa, ultimateTensileStrengthMPa.length);
    this.strainEffectFactor = Arrays.copyOf(strainEffectFactor, strainEffectFactor.length);
    this.ruptureStrainLimit = Arrays.copyOf(ruptureStrainLimit, ruptureStrainLimit.length);
    this.densityKgPerM3 = densityKgPerM3;
    this.sellarsTegartA = sellarsTegartA;
    this.sellarsTegartAlpha = sellarsTegartAlpha;
    this.sellarsTegartN = sellarsTegartN;
    this.activationTemperatureK = activationTemperatureK;
    this.correctionStrainRatePerMinute = correctionStrainRatePerMinute;
    this.correctionLimitTemperatureC = correctionLimitTemperatureC;
    this.dataSource = dataSource == null ? "" : dataSource.trim();
  }

  /**
   * Creates the 22Cr duplex material curve used in the benchmark workbook.
   *
   * @return 22Cr duplex material curve
   */
  public static PipeFireRuptureMaterial spreadsheetDuplex22Cr() {
    return new PipeFireRuptureMaterial("22Cr duplex", defaultTemperatureGrid(),
	new double[] { 480.0, 500.0, 530.0, 550.0, 590.0, 635.0, 670.0, 710.0, 730.0, 750.0, 790.0, 840.0, 870.0, 879.9,
	    900.0 },
	new double[] { 15.0, 16.0, 17.0, 18.0, 20.0, 24.0, 28.0, 28.0, 28.0, 28.0, 28.0, 28.0, 28.0, 28.0, 28.0 },
	new double[] { 650.0, 605.0, 553.0, 540.0, 533.0, 462.0, 371.0, 247.0, 202.0, 157.0, 78.0, 28.0, 12.0, 4.0,
	    0.0 },
	new double[] { 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0.9, 0.7, 1.0, 1.4, 2.3, 2.3, 2.3 },
	new double[] { 0.22, 0.22, 0.22, 0.22, 0.22, 0.22, 0.22, 0.22, 0.22, 0.23, 0.26, 0.33, 0.5, 0.5, 0.5 }, 7850.0,
	12311757039.742393, 0.03, 2.156323825544742, 34115.772963646705, 0.03, 900.0,
	"Fire rupture strain-rate workbook Rev4.02 material table");
  }

  /**
   * Creates the SS 316 material curve from the benchmark workbook.
   *
   * @return SS 316 material curve
   */
  public static PipeFireRuptureMaterial spreadsheetSs316() {
    return new PipeFireRuptureMaterial("SS 316", defaultTemperatureGrid(),
	new double[] { 479.81656050955417, 495.06496815286624, 511.3299363057325, 520.4789808917197, 528.6114649681529,
	    538.7770700636943, 549.9592356687898, 560.1248407643312, 564.0, 568.2573248407643, 574.3566878980891,
	    574.3566878980891, 574.3566878980891, 574.3566878980891, 574.0 },
	new double[] { 13.5, 14.9, 16.7, 18.3, 19.8, 21.3, 22.7, 24.2, 24.8, 25.6, 27.1, 28.6, 30.5, 34.2, 34.2 },
	new double[] { 485.0, 467.0, 429.0, 426.0, 421.0, 398.0, 363.0, 277.0, 208.5, 140.0, 80.0, 45.0, 20.0, 10.0,
	    0.0 },
	new double[] { 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0.7, 0.5, 0.3, 0.25, 0.3, 0.3, 0.3 },
	new double[] { 0.15, 0.15, 0.15, 0.15, 0.15, 0.15, 0.15, 0.15, 0.16, 0.17, 0.18, 0.2, 0.23, 0.25, 0.3 }, 7850.0,
	23449524917238.52, 0.015, 3.9045874502366082, 42315.65824131732, 0.05, 900.0,
	"Fire rupture strain-rate workbook Rev4.02 material table");
  }

  /**
   * Creates the carbon-steel 235 material curve from the benchmark workbook.
   *
   * @return carbon-steel 235 material curve
   */
  public static PipeFireRuptureMaterial spreadsheetCarbonSteel235() {
    return new PipeFireRuptureMaterial("CS 235 ASME A106-B", defaultTemperatureGrid(),
	new double[] { 450.0, 480.0, 510.0, 550.0, 600.0, 660.0, 750.0, 900.0, 1450.0, 820.0, 540.0, 540.0, 540.0,
	    540.0, 540.0 },
	new double[] { 54.2, 50.95, 47.45, 43.7, 40.45, 37.2, 33.95, 30.7, 28.0, 27.4, 27.4, 27.4, 27.4, 27.4, 27.4 },
	new double[] { 420.0, 407.0, 397.0, 382.0, 370.0, 308.0, 189.0, 92.0, 81.5, 71.0, 53.0, 29.0, 17.0, 4.0, 0.0 },
	new double[] { 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0.7, 0.5, 0.5, 0.4, 0.4, 0.3, 0.3 },
	new double[] { 0.15, 0.15, 0.15, 0.15, 0.15, 0.15, 0.15, 0.15, 0.15, 0.15, 0.2, 0.2, 0.2, 0.2, 0.3 }, 7850.0,
	4425186183349.352, 0.03, 3.7222401933501312, 41462.45192432549, 0.05, 900.0,
	"Fire rupture strain-rate workbook Rev4.02 material table");
  }

  /**
   * Creates the carbon-steel 360 / API 5L X52 material curve from the benchmark workbook.
   *
   * @return carbon-steel 360 / API 5L X52 material curve
   */
  public static PipeFireRuptureMaterial spreadsheetCarbonSteel360Api5lX52() {
    return new PipeFireRuptureMaterial("CS 360 API 5L-X52", defaultTemperatureGrid(),
	new double[] { 450.0, 480.0, 510.0, 550.0, 600.0, 660.0, 750.0, 900.0, 1450.0, 820.0, 540.0, 540.0, 540.0,
	    540.0, 540.0 },
	new double[] { 54.2, 50.95, 47.45, 43.7, 40.45, 37.2, 33.95, 30.7, 28.0, 27.4, 27.4, 27.4, 27.4, 27.4, 27.4 },
	new double[] { 545.0, 529.0, 515.0, 496.0, 480.0, 400.0, 245.0, 120.0, 99.0, 78.0, 60.0, 38.0, 22.0, 5.0, 0.0 },
	new double[] { 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0.5, 0.4, 0.6, 0.8, 0.9, 1.0, 1.0 },
	new double[] { 0.15, 0.15, 0.15, 0.15, 0.15, 0.15, 0.15, 0.15, 0.15, 0.15, 0.2, 0.2, 0.2, 0.2, 0.3 }, 7850.0,
	7508371948335.949, 0.015, 4.346909864208215, 38301.22162583593, 0.05, 900.0,
	"Fire rupture strain-rate workbook Rev4.02 material table");
  }

  /**
   * Creates the superduplex material curve from the benchmark workbook.
   *
   * @return superduplex material curve
   */
  public static PipeFireRuptureMaterial spreadsheetSuperduplex() {
    return new PipeFireRuptureMaterial("Superduplex", defaultTemperatureGrid(),
	new double[] { 480.0, 500.0, 530.0, 550.0, 590.0, 635.0, 670.0, 710.0, 730.0, 750.0, 790.0, 840.0, 870.0, 879.9,
	    900.0 },
	new double[] { 15.0, 16.0, 17.0, 18.0, 20.0, 24.0, 28.0, 28.0, 28.0, 28.0, 28.0, 28.0, 28.0, 28.0, 28.0 },
	new double[] { 750.0, 698.0, 638.0, 638.0, 613.0, 531.0, 427.0, 284.0, 234.5, 185.0, 100.0, 31.0, 13.0, 5.0,
	    0.0 },
	new double[] { 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0.9, 0.7, 1.0, 1.2, 2.2, 2.3, 2.2 },
	new double[] { 0.22, 0.22, 0.22, 0.22, 0.22, 0.22, 0.22, 0.22, 0.22, 0.23, 0.26, 0.33, 0.5, 0.5, 0.5 }, 7850.0,
	352941684892.5195, 0.03, 2.1486991997520266, 39301.04947184141, 0.03, 900.0,
	"Fire rupture strain-rate workbook Rev4.02 material table");
  }

  /**
   * Creates the 6Mo material curve from the benchmark workbook.
   *
   * @return 6Mo material curve
   */
  public static PipeFireRuptureMaterial spreadsheet6Mo() {
    return new PipeFireRuptureMaterial("6Mo", defaultTemperatureGrid(),
	new double[] { 479.81656050955417, 495.06496815286624, 511.3299363057325, 520.4789808917197, 528.6114649681529,
	    538.7770700636943, 549.9592356687898, 560.1248407643312, 564.0, 568.2573248407643, 574.3566878980891,
	    574.3566878980891, 574.3566878980891, 574.3566878980891, 574.0 },
	new double[] { 13.5, 14.9, 16.7, 18.3, 19.8, 21.3, 22.7, 24.2, 24.8, 25.6, 27.1, 28.6, 30.5, 34.2, 34.2 },
	new double[] { 650.0, 646.0, 589.0, 557.0, 546.0, 528.0, 480.0, 380.0, 315.0, 250.0, 135.0, 90.0, 40.0, 20.0,
	    0.0 },
	new double[] { 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0.7, 0.5, 0.3, 0.3, 0.3, 0.25, 0.3 },
	new double[] { 0.15, 0.15, 0.15, 0.15, 0.15, 0.15, 0.15, 0.15, 0.15, 0.15, 0.2, 0.2, 0.2, 0.2, 0.3 }, 7850.0,
	2443499691962.7837, 0.015, 3.0808262954383796, 41637.0426470313, 0.05, 900.0,
	"Fire rupture strain-rate workbook Rev4.02 material table");
  }

  /**
   * Selects a built-in spreadsheet material curve from a material name.
   *
   * @param materialName material name such as 22Cr duplex, SS 316, or CS 235
   * @return matching material curve
   * @throws IllegalArgumentException if no built-in curve matches the name
   */
  public static PipeFireRuptureMaterial fromSpreadsheetMaterialName(String materialName) {
    validateName(materialName);
    String normalized = materialName.toLowerCase(Locale.ROOT);
    if (normalized.contains("superduplex") || normalized.contains("25cr")) {
      return spreadsheetSuperduplex();
    }
    if (normalized.contains("22cr") || normalized.contains("duplex")) {
      return spreadsheetDuplex22Cr();
    }
    if (normalized.contains("316")) {
      return spreadsheetSs316();
    }
    if (normalized.contains("360") || normalized.contains("x52") || normalized.contains("5l")) {
      return spreadsheetCarbonSteel360Api5lX52();
    }
    if (normalized.contains("235") || normalized.contains("a106")) {
      return spreadsheetCarbonSteel235();
    }
    if (normalized.contains("6mo") || normalized.contains("6 mo")) {
      return spreadsheet6Mo();
    }
    throw new IllegalArgumentException("No built-in pipe fire rupture material for: " + materialName);
  }

  /**
   * Gets material name.
   *
   * @return material name
   */
  public String getMaterialName() {
    return materialName;
  }

  /**
   * Gets material density.
   *
   * @return density in kg/m3
   */
  public double getDensityKgPerM3() {
    return densityKgPerM3;
  }

  /**
   * Gets the material data source text.
   *
   * @return data source or assumption basis
   */
  public String getDataSource() {
    return dataSource;
  }

  /**
   * Gets heat capacity at a metal temperature.
   *
   * @param metalTemperatureC metal temperature in degrees Celsius
   * @return heat capacity in J/kg-K
   */
  public double heatCapacityAt(double metalTemperatureC) {
    return interpolate(metalTemperatureC, temperatureC, heatCapacityJPerKgK);
  }

  /**
   * Gets thermal conductivity at a metal temperature.
   *
   * @param metalTemperatureC metal temperature in degrees Celsius
   * @return thermal conductivity in W/m-K
   */
  public double thermalConductivityAt(double metalTemperatureC) {
    return interpolate(metalTemperatureC, temperatureC, thermalConductivityWPerMK);
  }

  /**
   * Gets ultimate tensile strength at a metal temperature.
   *
   * @param metalTemperatureC metal temperature in degrees Celsius
   * @return ultimate tensile strength in MPa
   */
  public double ultimateTensileStrengthAt(double metalTemperatureC) {
    return interpolate(metalTemperatureC, temperatureC, ultimateTensileStrengthMPa);
  }

  /**
   * Gets the strain-rate multiplier at a metal temperature.
   *
   * @param metalTemperatureC metal temperature in degrees Celsius
   * @return strain-rate multiplier
   */
  public double strainEffectAt(double metalTemperatureC) {
    return interpolate(metalTemperatureC, temperatureC, strainEffectFactor);
  }

  /**
   * Gets rupture strain limit at a metal temperature.
   *
   * @param metalTemperatureC metal temperature in degrees Celsius
   * @return accumulated strain limit
   */
  public double ruptureStrainLimitAt(double metalTemperatureC) {
    return interpolate(metalTemperatureC, temperatureC, ruptureStrainLimit);
  }

  /**
   * Calculates the spreadsheet temperature-correction factor for strain-rate extrapolation.
   *
   * @param metalTemperatureC mean wall temperature in degrees Celsius
   * @return temperature-correction factor
   */
  public double temperatureCorrectionFactor(double metalTemperatureC) {
    if (metalTemperatureC >= correctionLimitTemperatureC) {
      return 1.0;
    }
    double ultimateTensileStrength = Math.max(0.0, ultimateTensileStrengthAt(metalTemperatureC));
    double hyperbolicSine = Math.sinh(sellarsTegartAlpha * ultimateTensileStrength);
    if (hyperbolicSine <= 0.0) {
      return 1.0;
    }
    double absoluteTemperatureK = metalTemperatureC + 273.0;
    return absoluteTemperatureK / activationTemperatureK * (Math.log(hyperbolicSine) * sellarsTegartN
	- Math.log(correctionStrainRatePerMinute) + Math.log(sellarsTegartA));
  }

  /**
   * Calculates strain rate from von Mises stress and metal temperature.
   *
   * @param vonMisesStressMPa von Mises stress in MPa
   * @param metalTemperatureC mean wall temperature in degrees Celsius
   * @return strain rate per minute
   */
  public double strainRatePerMinute(double vonMisesStressMPa, double metalTemperatureC) {
    double effectiveStressMPa = Math.max(0.0, vonMisesStressMPa);
    double hyperbolicSine = Math.sinh(sellarsTegartAlpha * effectiveStressMPa);
    double temperatureCorrection = temperatureCorrectionFactor(metalTemperatureC);
    double absoluteTemperatureK = metalTemperatureC + 273.0;
    return strainEffectAt(metalTemperatureC) * sellarsTegartA * Math.pow(hyperbolicSine, sellarsTegartN)
	* Math.exp(-temperatureCorrection * activationTemperatureK / absoluteTemperatureK);
  }

  /**
   * Converts the material curve to a JSON-friendly map.
   *
   * @return ordered map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("materialName", materialName);
    map.put("temperatureC", copyToList(temperatureC));
    map.put("heatCapacityJPerKgK", copyToList(heatCapacityJPerKgK));
    map.put("thermalConductivityWPerMK", copyToList(thermalConductivityWPerMK));
    map.put("ultimateTensileStrengthMPa", copyToList(ultimateTensileStrengthMPa));
    map.put("strainEffectFactor", copyToList(strainEffectFactor));
    map.put("ruptureStrainLimit", copyToList(ruptureStrainLimit));
    map.put("densityKgPerM3", densityKgPerM3);
    map.put("sellarsTegartA", sellarsTegartA);
    map.put("sellarsTegartAlpha", sellarsTegartAlpha);
    map.put("sellarsTegartN", sellarsTegartN);
    map.put("activationTemperatureK", activationTemperatureK);
    map.put("correctionStrainRatePerMinute", correctionStrainRatePerMinute);
    map.put("correctionLimitTemperatureC", correctionLimitTemperatureC);
    map.put("dataSource", dataSource);
    return map;
  }

  /**
   * Converts the material curve to JSON.
   *
   * @return pretty-printed JSON representation
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(toMap());
  }

  /**
   * Gets the default temperature grid used by workbook material tables.
   *
   * @return temperature grid in degrees Celsius
   */
  private static double[] defaultTemperatureGrid() {
    return new double[] { 0.0, 100.0, 200.0, 300.0, 400.0, 500.0, 600.0, 700.0, 750.0, 800.0, 900.0, 1000.0, 1100.0,
	1150.0, 1350.0 };
  }

  /**
   * Performs linear interpolation with endpoint clamping.
   *
   * @param value requested x value
   * @param xValues increasing x grid
   * @param yValues y values corresponding to the x grid
   * @return interpolated y value
   */
  private static double interpolate(double value, double[] xValues, double[] yValues) {
    if (value <= xValues[0]) {
      return yValues[0];
    }
    for (int index = 1; index < xValues.length; index++) {
      if (value <= xValues[index]) {
	double fraction = (value - xValues[index - 1]) / (xValues[index] - xValues[index - 1]);
	return yValues[index - 1] + fraction * (yValues[index] - yValues[index - 1]);
      }
    }
    return yValues[yValues.length - 1];
  }

  /**
   * Converts a primitive array to a list.
   *
   * @param values values to convert
   * @return list of boxed double values
   */
  private static List<Double> copyToList(double[] values) {
    List<Double> list = new ArrayList<Double>();
    for (double value : values) {
      list.add(Double.valueOf(value));
    }
    return list;
  }

  /**
   * Validates a material name.
   *
   * @param materialName material name
   * @throws IllegalArgumentException if the name is empty
   */
  private static void validateName(String materialName) {
    if (materialName == null || materialName.trim().isEmpty()) {
      throw new IllegalArgumentException("materialName must not be empty");
    }
  }

  /**
   * Validates that a value is positive and finite.
   *
   * @param value value to validate
   * @param name parameter name for messages
   * @throws IllegalArgumentException if the value is invalid
   */
  private static void validatePositive(double value, String name) {
    if (value <= 0.0 || Double.isNaN(value) || Double.isInfinite(value)) {
      throw new IllegalArgumentException(name + " must be positive and finite");
    }
  }

  /**
   * Validates curve arrays.
   *
   * @param temperatureC temperature grid
   * @param heatCapacityJPerKgK heat capacity values
   * @param thermalConductivityWPerMK thermal conductivity values
   * @param ultimateTensileStrengthMPa ultimate tensile strength values
   * @param strainEffectFactor strain-effect values
   * @param ruptureStrainLimit rupture strain limit values
   * @throws IllegalArgumentException if any array is invalid
   */
  private static void validateCurves(double[] temperatureC, double[] heatCapacityJPerKgK,
      double[] thermalConductivityWPerMK, double[] ultimateTensileStrengthMPa, double[] strainEffectFactor,
      double[] ruptureStrainLimit) {
    int expectedLength = validateGrid(temperatureC);
    validateCurveLength(heatCapacityJPerKgK, expectedLength, "heatCapacityJPerKgK");
    validateCurveLength(thermalConductivityWPerMK, expectedLength, "thermalConductivityWPerMK");
    validateCurveLength(ultimateTensileStrengthMPa, expectedLength, "ultimateTensileStrengthMPa");
    validateCurveLength(strainEffectFactor, expectedLength, "strainEffectFactor");
    validateCurveLength(ruptureStrainLimit, expectedLength, "ruptureStrainLimit");
  }

  /**
   * Validates the temperature grid.
   *
   * @param temperatureC temperature grid
   * @return validated grid length
   * @throws IllegalArgumentException if the grid is invalid
   */
  private static int validateGrid(double[] temperatureC) {
    if (temperatureC == null || temperatureC.length < 2) {
      throw new IllegalArgumentException("temperatureC must contain at least two values");
    }
    double previous = temperatureC[0];
    if (Double.isNaN(previous) || Double.isInfinite(previous)) {
      throw new IllegalArgumentException("temperatureC values must be finite");
    }
    for (int index = 1; index < temperatureC.length; index++) {
      double current = temperatureC[index];
      if (Double.isNaN(current) || Double.isInfinite(current) || current <= previous) {
	throw new IllegalArgumentException("temperatureC must be strictly increasing and finite");
      }
      previous = current;
    }
    return temperatureC.length;
  }

  /**
   * Validates one curve array length and values.
   *
   * @param values curve values
   * @param expectedLength expected array length
   * @param name parameter name for messages
   * @throws IllegalArgumentException if the curve is invalid
   */
  private static void validateCurveLength(double[] values, int expectedLength, String name) {
    if (values == null || values.length != expectedLength) {
      throw new IllegalArgumentException(name + " must match the temperature grid length");
    }
    for (double value : values) {
      if (Double.isNaN(value) || Double.isInfinite(value)) {
	throw new IllegalArgumentException(name + " values must be finite");
      }
    }
  }
}
