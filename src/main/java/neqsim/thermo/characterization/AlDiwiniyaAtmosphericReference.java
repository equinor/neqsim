package neqsim.thermo.characterization;

import java.io.Serializable;

/**
 * Public operating reference for the Al-Diwiniya refinery atmospheric distillation unit.
 *
 * <p>
 * Values are transcribed from A. Qasim, H. Yousif, and N. Qasim, "Simulation of Atmospheric Distillation Unit for
 * AL-Diwiniya Crude Oil Refinery by Using Aspen Hysys", Journal of Petroleum Research and Studies 15(3), 85-97,
 * published 21 September 2025. The article is licensed CC BY 4.0.
 * </p>
 *
 * <p>
 * The published TBP curve covers only 2-60 liquid volume percent. This class preserves that partial evidence and does
 * not extrapolate it to 0 or 100 percent. The paper also states that all HYSYS product flow rates were fixed.
 * Consequently, the HYSYS rates retained here are operating specifications and must not be used as independent
 * yield-prediction validation.
 * </p>
 */
public final class AlDiwiniyaAtmosphericReference {
  /** Digital object identifier for the source article. */
  public static final String DOI = "10.52716/jprs.v15i3.965";

  /** Open-access source article. */
  public static final String ARTICLE_URL = "https://jprs.gov.iq/index.php/jprs/article/download/965/614/5888";

  /** Source license identifier. */
  public static final String LICENSE = "CC BY 4.0";

  /** Source publication date in ISO-8601 format. */
  public static final String PUBLICATION_DATE = "2025-09-21";

  private static final double[] TBP_VOLUME_PERCENT = { 2.0, 3.5, 5.0, 7.5, 10.0, 12.5, 15.0, 17.5, 20.0, 25.0, 30.0,
      35.0, 40.0, 45.0, 50.0, 55.0, 60.0 };
  private static final double[] TBP_TEMPERATURE_CELSIUS = { 40.0, 52.0, 62.0, 77.0, 95.0, 112.0, 128.0, 143.0, 159.0,
      189.0, 218.0, 249.0, 279.0, 310.0, 342.0, 373.0, 405.0 };

  private static final ProductReference[] PRODUCTS = {
      new ProductReference("Light Naphtha", true, 8.0, 8.0, 110.0, 109.0),
      new ProductReference("Total Heavy Naphtha", true, 2.0, 2.0, 135.0, 140.0),
      new ProductReference("Kerosene", true, 4.0, 4.0, 180.0, 190.0),
      new ProductReference("Gasoil", true, 10.0, 10.0, 240.0, 235.0),
      new ProductReference("Atmospheric residue", true, 41.0, 41.25, 295.0, 295.0),
      new ProductReference("Off gas", false, 1.0, 0.75, 60.0, 65.0) };

  private AlDiwiniyaAtmosphericReference() {
  }

  /**
   * Return the published cumulative TBP liquid-volume coordinates.
   *
   * @return defensive copy of cumulative liquid volume in percent
   */
  public static double[] getTbpCumulativeVolumePercent() {
    return TBP_VOLUME_PERCENT.clone();
  }

  /**
   * Return the published TBP temperatures.
   *
   * @return defensive copy of temperatures in degrees Celsius
   */
  public static double[] getTbpTemperatureCelsius() {
    return TBP_TEMPERATURE_CELSIUS.clone();
  }

  /**
   * Return the published TBP temperatures in kelvin.
   *
   * @return newly allocated temperature array in kelvin
   */
  public static double[] getTbpTemperatureKelvin() {
    double[] temperaturesKelvin = new double[TBP_TEMPERATURE_CELSIUS.length];
    for (int i = 0; i < temperaturesKelvin.length; i++) {
      temperaturesKelvin[i] = TBP_TEMPERATURE_CELSIUS[i] + 273.15;
    }
    return temperaturesKelvin;
  }

  /**
   * Return whether the source supplies a complete 0-100 liquid-volume-percent TBP curve.
   *
   * @return always false for this partial published curve
   */
  public static boolean hasCompleteTbpCurve() {
    return false;
  }

  /**
   * Return whether the source fixed the HYSYS product rates as simulation specifications.
   *
   * @return always true for this source case
   */
  public static boolean areHysysProductRatesImposedSpecifications() {
    return true;
  }

  /**
   * Return whether this source supports an independent product-yield validation claim.
   *
   * @return always false because the simulated product rates were imposed
   */
  public static boolean isIndependentYieldValidationCase() {
    return false;
  }

  /**
   * Return independent immutable copies of the product-reference rows.
   *
   * @return product operating references in source-table order
   */
  public static ProductReference[] getProducts() {
    return PRODUCTS.clone();
  }

  /**
   * Find one product reference by its exact published label.
   *
   * @param productName exact product label
   * @return immutable product reference
   * @throws IllegalArgumentException when the label is null or unknown
   */
  public static ProductReference getProduct(String productName) {
    if (productName == null) {
      throw new IllegalArgumentException("Product name cannot be null");
    }
    for (ProductReference product : PRODUCTS) {
      if (product.getName().equals(productName)) {
        return product;
      }
    }
    throw new IllegalArgumentException("Unknown Al-Diwiniya product: " + productName);
  }

  /**
   * Calculate the signed relative error convention used by the source article.
   *
   * @param actualValue plant value
   * @param simulatedValue HYSYS value
   * @return {@code 100 * (actual - simulated) / actual}, in percent
   */
  public static double calculateSignedRelativeErrorPercent(double actualValue, double simulatedValue) {
    if (!Double.isFinite(actualValue) || actualValue == 0.0) {
      throw new IllegalArgumentException("Actual value must be finite and non-zero");
    }
    if (!Double.isFinite(simulatedValue)) {
      throw new IllegalArgumentException("Simulated value must be finite");
    }
    return 100.0 * (actualValue - simulatedValue) / actualValue;
  }

  /**
   * Sum the five measured hydrocarbon-liquid product rates.
   *
   * <p>
   * Off-gas and the separately reported 0.4 m3/h water stream are excluded because their measurement/reference states
   * are not supplied and are not silently commensurate with crude liquid volume.
   * </p>
   *
   * @return measured hydrocarbon-liquid product total in m3/h
   */
  public static double getMeasuredHydrocarbonLiquidProductRateM3PerHour() {
    double total = 0.0;
    for (ProductReference product : PRODUCTS) {
      if (product.isHydrocarbonLiquid()) {
        total += product.getPlantRateM3PerHour();
      }
    }
    return total;
  }

  /**
   * Return measured-liquid closure relative to the published crude feed rate.
   *
   * @return liquid-product rate divided by crude-feed rate, in percent
   */
  public static double getMeasuredHydrocarbonLiquidClosurePercent() {
    return 100.0 * getMeasuredHydrocarbonLiquidProductRateM3PerHour() / getCrudeFeedRateM3PerHour();
  }

  /** @return refinery crude capacity in barrels per day */
  public static double getCrudeCapacityBarrelsPerDay() {
    return 10000.0;
  }

  /** @return published crude feed rate in m3/h */
  public static double getCrudeFeedRateM3PerHour() {
    return 66.0;
  }

  /** @return published crude API gravity at 15 degrees Celsius */
  public static double getCrudeApiGravityAt15C() {
    return 29.8;
  }

  /** @return published crude density in kg/m3; source temperature is not stated */
  public static double getCrudeDensityKgPerCubicMetre() {
    return 876.0;
  }

  /** @return published crude total sulfur in mass percent */
  public static double getCrudeSulfurMassPercent() {
    return 3.0;
  }

  /** @return published crude salt content in ppm */
  public static double getCrudeSaltPpm() {
    return 159.0;
  }

  /** @return published water-and-bottom-sediment content in volume percent */
  public static double getWaterAndBottomSedimentVolumePercent() {
    return 0.15;
  }

  /** @return published kinematic viscosity at 20 degrees Celsius in cSt */
  public static double getKinematicViscosityAt20CCst() {
    return 12.7;
  }

  /** @return crude temperature after the preheat train in degrees Celsius */
  public static double getPreheatOutletTemperatureCelsius() {
    return 150.0;
  }

  /** @return furnace/column-feed temperature in degrees Celsius */
  public static double getColumnFeedTemperatureCelsius() {
    return 300.0;
  }

  /** @return published column-feed gauge pressure in bar */
  public static double getColumnFeedPressureBarGauge() {
    return 1.5;
  }

  /** @return number of published atmospheric-column stages */
  public static int getColumnStageCount() {
    return 29;
  }

  /** @return lower tray bordering the feed flash zone */
  public static int getFeedFlashZoneLowerTray() {
    return 3;
  }

  /** @return upper tray bordering the feed flash zone */
  public static int getFeedFlashZoneUpperTray() {
    return 4;
  }

  /** @return published top gauge pressure in bar */
  public static double getColumnTopPressureBarGauge() {
    return 0.75;
  }

  /** @return published bottom gauge pressure in bar */
  public static double getColumnBottomPressureBarGauge() {
    return 1.2;
  }

  /** @return defensive copy of heavy-naphtha draw tray numbers */
  public static int[] getHeavyNaphthaDrawTrays() {
    return new int[] { 24, 22 };
  }

  /** @return kerosene draw tray number */
  public static int getKeroseneDrawTray() {
    return 15;
  }

  /** @return gasoil draw tray number */
  public static int getGasoilDrawTray() {
    return 9;
  }

  /** @return gasoil pump-around rate in m3/h */
  public static double getGasoilPumpAroundRateM3PerHour() {
    return 3.0;
  }

  /** @return gasoil pump-around return temperature in degrees Celsius */
  public static double getGasoilPumpAroundReturnTemperatureCelsius() {
    return 60.0;
  }

  /** @return number of stages in each published side stripper */
  public static int getSideStripperStageCount() {
    return 4;
  }

  /** @return kerosene-stripper steam rate in kg/h */
  public static double getKeroseneStripperSteamRateKgPerHour() {
    return 75.0;
  }

  /** @return gasoil-stripper steam rate in kg/h */
  public static double getGasoilStripperSteamRateKgPerHour() {
    return 125.0;
  }

  /** @return bottom stripping-steam rate in kg/h */
  public static double getBottomSteamRateKgPerHour() {
    return 300.0;
  }

  /** @return bottom stripping-steam temperature in degrees Celsius */
  public static double getBottomSteamTemperatureCelsius() {
    return 220.0;
  }

  /** @return bottom stripping-steam gauge pressure in bar */
  public static double getBottomSteamPressureBarGauge() {
    return 5.0;
  }

  /** @return measured overhead water rate in m3/h */
  public static double getOverheadWaterRateM3PerHour() {
    return 0.4;
  }

  /**
   * Immutable published product operating-reference row.
   */
  public static final class ProductReference implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String name;
    private final boolean hydrocarbonLiquid;
    private final double plantRateM3PerHour;
    private final double hysysRateM3PerHour;
    private final double plantDrawTemperatureCelsius;
    private final double hysysDrawTemperatureCelsius;

    private ProductReference(String name, boolean hydrocarbonLiquid, double plantRateM3PerHour,
        double hysysRateM3PerHour, double plantDrawTemperatureCelsius, double hysysDrawTemperatureCelsius) {
      this.name = name;
      this.hydrocarbonLiquid = hydrocarbonLiquid;
      this.plantRateM3PerHour = plantRateM3PerHour;
      this.hysysRateM3PerHour = hysysRateM3PerHour;
      this.plantDrawTemperatureCelsius = plantDrawTemperatureCelsius;
      this.hysysDrawTemperatureCelsius = hysysDrawTemperatureCelsius;
    }

    /** @return exact source product label */
    public String getName() {
      return name;
    }

    /** @return true for the five liquid hydrocarbon product rows */
    public boolean isHydrocarbonLiquid() {
      return hydrocarbonLiquid;
    }

    /** @return measured plant product rate in m3/h */
    public double getPlantRateM3PerHour() {
      return plantRateM3PerHour;
    }

    /** @return imposed HYSYS product rate in m3/h */
    public double getHysysRateM3PerHour() {
      return hysysRateM3PerHour;
    }

    /** @return measured plant draw temperature in degrees Celsius */
    public double getPlantDrawTemperatureCelsius() {
      return plantDrawTemperatureCelsius;
    }

    /** @return calculated HYSYS draw temperature in degrees Celsius */
    public double getHysysDrawTemperatureCelsius() {
      return hysysDrawTemperatureCelsius;
    }

    /** @return signed source-convention rate error in percent */
    public double getRateRelativeErrorPercent() {
      return calculateSignedRelativeErrorPercent(plantRateM3PerHour, hysysRateM3PerHour);
    }

    /** @return signed source-convention draw-temperature error in percent */
    public double getDrawTemperatureRelativeErrorPercent() {
      return calculateSignedRelativeErrorPercent(plantDrawTemperatureCelsius, hysysDrawTemperatureCelsius);
    }

    @Override
    public String toString() {
      return "ProductReference{" + "name='" + name + '\'' + ", hydrocarbonLiquid=" + hydrocarbonLiquid
          + ", plantRateM3PerHour=" + plantRateM3PerHour + ", hysysRateM3PerHour=" + hysysRateM3PerHour
          + ", plantDrawTemperatureCelsius=" + plantDrawTemperatureCelsius + ", hysysDrawTemperatureCelsius="
          + hysysDrawTemperatureCelsius + '}';
    }
  }
}
