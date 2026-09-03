package neqsim.thermo.characterization;

import java.io.Serializable;

/**
 * Public assay and operating reference for the Sarir refinery atmospheric distillation unit.
 *
 * <p>
 * Values are transcribed from H. E. O. Almansouri, "Simulation of Sarir Crude Oil Refinery Using Aspen HYSYS", Journal
 * of Engineering Research (Libya), issue 33, pages 51-64, published 31 March 2022. The article is licensed CC BY 4.0.
 * </p>
 *
 * <p>
 * The published assay gives numeric TBP coordinates only through 83.70 liquid volume percent at 550 degrees Celsius,
 * followed by a 550 degrees Celsius-plus terminal residue. This class preserves that one-sided boundary and does not
 * invent a numeric 100-percent endpoint or a resolved light-end composition.
 * </p>
 */
public final class SarirAtmosphericReference {
  /** Digital object identifier for the source article. */
  public static final String DOI = "10.66411/jer.v33i.46";

  /** Open-access source article. */
  public static final String ARTICLE_URL = "https://jer.ly/jer/index.php/jer/article/download/46/38/39";

  /** Source license identifier. */
  public static final String LICENSE = "CC BY 4.0";

  /** Source publication date in ISO-8601 format. */
  public static final String PUBLICATION_DATE = "2022-03-31";

  private static final double[] TBP_TEMPERATURE_CELSIUS = { 70.0, 90.0, 110.0, 150.0, 195.0, 215.0, 255.0, 275.0, 295.0,
      335.0, 370.0, 400.0, 460.0, 480.0, 500.0, 520.0, 550.0 };
  private static final double[] TBP_VOLUME_PERCENT = { 7.44, 10.47, 13.83, 21.16, 28.52, 31.54, 38.03, 41.76, 44.68,
      51.97, 59.19, 63.50, 72.52, 75.61, 78.66, 81.05, 83.70 };

  private static final ProductQualityReference[] PRODUCT_QUALITIES = {
      new ProductQualityReference("Light Naphtha", 42.0, 90.0, -9.0, 97.0),
      new ProductQualityReference("Heavy Naphtha", 96.0, 160.0, 83.0, 153.0),
      new ProductQualityReference("Kerosene", 185.0, 221.0, 159.0, 214.0),
      new ProductQualityReference("Diesel", 262.0, 346.0, 235.0, 339.0) };

  private static final ProductYieldReference[] PRODUCT_YIELDS = {
      new ProductYieldReference("Total Naphtha", 208.95, 208.2), new ProductYieldReference("Kerosene", 22.85, 20.0),
      new ProductYieldReference("Diesel", 425.018, 393.0), new ProductYieldReference("Residual", 646.5, 706.1) };

  private SarirAtmosphericReference() {
  }

  /** @return defensive copy of the numeric cumulative liquid-volume coordinates, in percent */
  public static double[] getTbpCumulativeVolumePercent() {
    return TBP_VOLUME_PERCENT.clone();
  }

  /** @return defensive copy of the numeric TBP temperatures, in degrees Celsius */
  public static double[] getTbpTemperatureCelsius() {
    return TBP_TEMPERATURE_CELSIUS.clone();
  }

  /** @return newly allocated numeric TBP temperature array in kelvin */
  public static double[] getTbpTemperatureKelvin() {
    double[] kelvin = new double[TBP_TEMPERATURE_CELSIUS.length];
    for (int i = 0; i < kelvin.length; i++) {
      kelvin[i] = TBP_TEMPERATURE_CELSIUS[i] + 273.15;
    }
    return kelvin;
  }

  /** @return always false because the source gives a one-sided 550 degrees Celsius-plus residue */
  public static boolean hasCompleteNumericTbpCurve() {
    return false;
  }

  /** @return always false because the source reports the light-end analysis as not determined */
  public static boolean hasResolvedLightEndComposition() {
    return false;
  }

  /** @return lower boiling boundary of the terminal residue, in degrees Celsius */
  public static double getTerminalResidueLowerBoundaryCelsius() {
    return 550.0;
  }

  /** @return terminal residue fraction implied by the final numeric cumulative TBP point, in volume percent */
  public static double getTerminalResidueVolumePercent() {
    return 100.0 - TBP_VOLUME_PERCENT[TBP_VOLUME_PERCENT.length - 1];
  }

  /** @return true because plant rates were compared with calculated rather than imposed simulation rates */
  public static boolean isIndependentYieldValidationCase() {
    return true;
  }

  /** @return false because the article does not state that the HYSYS product rates were fixed */
  public static boolean areSimulationProductRatesImposedSpecifications() {
    return false;
  }

  /** @return defensive copy of numeric laboratory/simulation ASTM D86 comparison rows */
  public static ProductQualityReference[] getProductQualities() {
    return PRODUCT_QUALITIES.clone();
  }

  /** @return defensive copy of independent plant/simulation product-yield comparison rows */
  public static ProductYieldReference[] getProductYields() {
    return PRODUCT_YIELDS.clone();
  }

  /**
   * Find one yield row by its exact source-table label.
   *
   * @param productName exact source-table product label
   * @return immutable yield reference
   * @throws IllegalArgumentException if the label is null or unknown
   */
  public static ProductYieldReference getProductYield(String productName) {
    if (productName == null) {
      throw new IllegalArgumentException("Product name cannot be null");
    }
    for (ProductYieldReference product : PRODUCT_YIELDS) {
      if (product.getName().equals(productName)) {
        return product;
      }
    }
    throw new IllegalArgumentException("Unknown Sarir product yield: " + productName);
  }

  /**
   * Calculate absolute relative error against the measured plant value.
   *
   * @param plantValue measured plant value
   * @param simulatedValue simulated value
   * @return {@code 100 * abs(simulated - plant) / plant}, in percent
   */
  public static double calculateAbsoluteRelativeErrorPercent(double plantValue, double simulatedValue) {
    if (!Double.isFinite(plantValue) || plantValue <= 0.0) {
      throw new IllegalArgumentException("Plant value must be finite and positive");
    }
    if (!Double.isFinite(simulatedValue)) {
      throw new IllegalArgumentException("Simulated value must be finite");
    }
    return 100.0 * Math.abs(simulatedValue - plantValue) / plantValue;
  }

  /** @return crude density at 15 degrees Celsius, in kg/m3 */
  public static double getCrudeDensityAt15CKgPerCubicMetre() {
    return 841.5;
  }

  /** @return crude API gravity at 60 degrees Fahrenheit */
  public static double getCrudeApiGravityAt60F() {
    return 36.5;
  }

  /** @return crude sulfur content in mass percent */
  public static double getCrudeSulfurMassPercent() {
    return 0.120;
  }

  /** @return crude average molar mass in kg/mol */
  public static double getCrudeAverageMolarMassKgPerMol() {
    return 0.2447;
  }

  /** @return atmospheric-column valve-tray count */
  public static int getColumnTrayCount() {
    return 34;
  }

  /** @return feed tray number used by the source, counted from the top */
  public static int getFeedTrayFromTop() {
    return 31;
  }

  /** @return crude feed rate to the atmospheric column in kg/h */
  public static double getColumnCrudeFeedRateKgPerHour() {
    return 54420.0;
  }

  /** @return atmospheric-column feed temperature in degrees Celsius */
  public static double getColumnFeedTemperatureCelsius() {
    return 350.0;
  }

  /** @return atmospheric-column feed pressure in kPa absolute as reported */
  public static double getColumnFeedPressureKPa() {
    return 233.0;
  }

  /** @return main atmospheric-column steam rate in kg/h */
  public static double getMainColumnSteamRateKgPerHour() {
    return 340.2;
  }

  /** @return kerosene side-stripper steam rate in kg/h */
  public static double getKeroseneStripperSteamRateKgPerHour() {
    return 68.04;
  }

  /** @return diesel side-stripper steam rate in kg/h */
  public static double getDieselStripperSteamRateKgPerHour() {
    return 226.8;
  }

  /** @return top pump-around flow rate in kg/h */
  public static double getTopPumpAroundRateKgPerHour() {
    return 29777.64;
  }

  /** @return bottom pump-around flow rate in kg/h */
  public static double getBottomPumpAroundRateKgPerHour() {
    return 60423.66;
  }

  /** Immutable numeric ASTM D86 comparison row. */
  public static final class ProductQualityReference implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String name;
    private final double laboratoryFivePercentCelsius;
    private final double laboratoryNinetyFivePercentCelsius;
    private final double simulationFivePercentCelsius;
    private final double simulationNinetyFivePercentCelsius;

    private ProductQualityReference(String name, double laboratoryFivePercentCelsius,
        double laboratoryNinetyFivePercentCelsius, double simulationFivePercentCelsius,
        double simulationNinetyFivePercentCelsius) {
      this.name = name;
      this.laboratoryFivePercentCelsius = laboratoryFivePercentCelsius;
      this.laboratoryNinetyFivePercentCelsius = laboratoryNinetyFivePercentCelsius;
      this.simulationFivePercentCelsius = simulationFivePercentCelsius;
      this.simulationNinetyFivePercentCelsius = simulationNinetyFivePercentCelsius;
    }

    /** @return exact source-table product label */
    public String getName() {
      return name;
    }

    /** @return measured ASTM D86 T5 in degrees Celsius */
    public double getLaboratoryFivePercentCelsius() {
      return laboratoryFivePercentCelsius;
    }

    /** @return measured ASTM D86 T95 in degrees Celsius */
    public double getLaboratoryNinetyFivePercentCelsius() {
      return laboratoryNinetyFivePercentCelsius;
    }

    /** @return simulated ASTM D86 T5 in degrees Celsius */
    public double getSimulationFivePercentCelsius() {
      return simulationFivePercentCelsius;
    }

    /** @return simulated ASTM D86 T95 in degrees Celsius */
    public double getSimulationNinetyFivePercentCelsius() {
      return simulationNinetyFivePercentCelsius;
    }
  }

  /** Immutable plant/simulation product-yield comparison row. */
  public static final class ProductYieldReference implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String name;
    private final double plantMetricTonPerDay;
    private final double simulationMetricTonPerDay;

    private ProductYieldReference(String name, double plantMetricTonPerDay, double simulationMetricTonPerDay) {
      this.name = name;
      this.plantMetricTonPerDay = plantMetricTonPerDay;
      this.simulationMetricTonPerDay = simulationMetricTonPerDay;
    }

    /** @return exact source-table product label */
    public String getName() {
      return name;
    }

    /** @return measured refinery product rate in metric tonnes/day */
    public double getPlantMetricTonPerDay() {
      return plantMetricTonPerDay;
    }

    /** @return simulated product rate in metric tonnes/day */
    public double getSimulationMetricTonPerDay() {
      return simulationMetricTonPerDay;
    }

    /** @return absolute relative rate error against the measured plant value, in percent */
    public double getAbsoluteRelativeErrorPercent() {
      return calculateAbsoluteRelativeErrorPercent(plantMetricTonPerDay, simulationMetricTonPerDay);
    }
  }
}
