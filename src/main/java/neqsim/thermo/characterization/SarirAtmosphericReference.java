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

  private static final PumparoundReference[] PUMPAROUNDS = {
      new PumparoundReference("Top pump around (TPA)", 3, 1, 29777.64, 143.9, 80.99),
      new PumparoundReference("Bottom pump around (BPA)", 22, 19, 60423.66, 232.4, 173.99) };

  private static final SteamInjectionReference[] STEAM_INJECTIONS = {
      new SteamInjectionReference("Main atmospheric column", SteamInjectionService.MAIN_ATMOSPHERIC_COLUMN, 340.2,
          150.0, 476.0),
      new SteamInjectionReference("Kerosene side stripper", SteamInjectionService.KEROSENE_SIDE_STRIPPER, 68.04, 150.0,
          476.0),
      new SteamInjectionReference("Diesel side stripper", SteamInjectionService.DIESEL_SIDE_STRIPPER, 226.8, 150.0,
          476.0) };

  private static final AduStreamReference[] ADU_STREAMS = {
      new AduStreamReference("Crude oil tower", AduStreamDirection.INLET, 350.0, 233.0, 54420.0),
      new AduStreamReference("Steam", AduStreamDirection.INLET, 150.0, 476.0, 340.2),
      new AduStreamReference("Kerosene steam", AduStreamDirection.INLET, 150.0, 476.0, 68.04),
      new AduStreamReference("Diesel steam", AduStreamDirection.INLET, 150.0, 476.0, 226.8),
      new AduStreamReference("Gas To Flare", AduStreamDirection.OUTLET, 49.0, 140.0, 6.985e-6),
      new AduStreamReference("Naphtha", AduStreamDirection.OUTLET, 49.0, 140.0, 8706.0),
      new AduStreamReference("Kerosene product", AduStreamDirection.OUTLET, 126.3, 210.0, 952.2),
      new AduStreamReference("Diesel product", AduStreamDirection.OUTLET, 214.8, 219.1, 17709.24),
      new AduStreamReference("Residual", AduStreamDirection.OUTLET, 341.9, 230.0, 26937.99),
      new AduStreamReference("Water draw", AduStreamDirection.OUTLET, 49.0, 140.0, 745.5) };

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

  /** @return crude asphaltenes content in mass percent */
  public static double getCrudeAsphaltenesMassPercent() {
    return 0.20;
  }

  /** @return crude mercaptan sulfur content in ppm by mass */
  public static double getCrudeMercaptanSulfurMassPpm() {
    return 8.0;
  }

  /** @return crude water and sediment content in volume percent */
  public static double getCrudeWaterAndSedimentVolumePercent() {
    return 0.05;
  }

  /** @return lower endpoint of the published crude cloud-point interval in degrees Celsius */
  public static double getCrudeCloudPointLowerCelsius() {
    return 48.7;
  }

  /** @return upper endpoint of the published crude cloud-point interval in degrees Celsius */
  public static double getCrudeCloudPointUpperCelsius() {
    return 49.6;
  }

  /** @return crude pour point in degrees Celsius */
  public static double getCrudePourPointCelsius() {
    return 21.0;
  }

  /** @return crude kinematic viscosity at 100 degrees Fahrenheit in cSt */
  public static double getCrudeKinematicViscosityAt100FCst() {
    return 10.63;
  }

  /** @return Fahrenheit reference temperature for the published crude viscosity */
  public static double getCrudeKinematicViscosityReferenceTemperatureFahrenheit() {
    return 100.0;
  }

  /** @return rounded Celsius reference temperature reported in the source prose */
  public static double getCrudeKinematicViscosityReferenceTemperatureCelsius() {
    return 37.7;
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
    return ADU_STREAMS[0].getMassFlowRateKgPerHour();
  }

  /** @return atmospheric-column feed temperature in degrees Celsius */
  public static double getColumnFeedTemperatureCelsius() {
    return ADU_STREAMS[0].getTemperatureCelsius();
  }

  /** @return atmospheric-column feed pressure in kPa absolute as reported */
  public static double getColumnFeedPressureKPa() {
    return ADU_STREAMS[0].getPressureKPa();
  }

  /** @return defensive copy of the complete published ADU stream table in source order */
  public static AduStreamReference[] getAduStreams() {
    return ADU_STREAMS.clone();
  }

  /**
   * Find one ADU stream row by its exact source-table label.
   *
   * @param name exact source-table stream label
   * @return immutable ADU stream reference
   * @throws IllegalArgumentException if the label is null or unknown
   */
  public static AduStreamReference getAduStream(String name) {
    if (name == null) {
      throw new IllegalArgumentException("ADU stream name cannot be null");
    }
    for (AduStreamReference stream : ADU_STREAMS) {
      if (stream.getName().equals(name)) {
        return stream;
      }
    }
    throw new IllegalArgumentException("Unknown Sarir ADU stream: " + name);
  }

  /** @return sum of the four published ADU inlet mass flows in kg/h */
  public static double getPublishedAduInletMassFlowTotalKgPerHour() {
    return sumAduStreamMassFlow(AduStreamDirection.INLET);
  }

  /** @return sum of the six published ADU outlet mass flows in kg/h */
  public static double getPublishedAduOutletMassFlowTotalKgPerHour() {
    return sumAduStreamMassFlow(AduStreamDirection.OUTLET);
  }

  /**
   * Calculate the absolute fractional imbalance of the published ADU stream table.
   *
   * @return absolute inlet-minus-outlet mass-flow difference divided by inlet flow
   */
  public static double calculatePublishedAduMassBalanceErrorFraction() {
    double inlet = getPublishedAduInletMassFlowTotalKgPerHour();
    return Math.abs(inlet - getPublishedAduOutletMassFlowTotalKgPerHour()) / inlet;
  }

  private static double sumAduStreamMassFlow(AduStreamDirection direction) {
    double total = 0.0;
    for (AduStreamReference stream : ADU_STREAMS) {
      if (stream.getDirection() == direction) {
        total += stream.getMassFlowRateKgPerHour();
      }
    }
    return total;
  }

  /** @return main atmospheric-column steam rate in kg/h */
  public static double getMainColumnSteamRateKgPerHour() {
    return STEAM_INJECTIONS[0].getMassFlowRateKgPerHour();
  }

  /** @return kerosene side-stripper steam rate in kg/h */
  public static double getKeroseneStripperSteamRateKgPerHour() {
    return STEAM_INJECTIONS[1].getMassFlowRateKgPerHour();
  }

  /** @return diesel side-stripper steam rate in kg/h */
  public static double getDieselStripperSteamRateKgPerHour() {
    return STEAM_INJECTIONS[2].getMassFlowRateKgPerHour();
  }

  /** @return defensive copy of the source steam-injection rows in source order */
  public static SteamInjectionReference[] getSteamInjections() {
    return STEAM_INJECTIONS.clone();
  }

  /**
   * Find one steam-injection row by its exact source label.
   *
   * @param name exact source label
   * @return immutable steam-injection reference
   * @throws IllegalArgumentException if the label is null or unknown
   */
  public static SteamInjectionReference getSteamInjection(String name) {
    if (name == null) {
      throw new IllegalArgumentException("Steam-injection name cannot be null");
    }
    for (SteamInjectionReference injection : STEAM_INJECTIONS) {
      if (injection.getName().equals(name)) {
        return injection;
      }
    }
    throw new IllegalArgumentException("Unknown Sarir steam injection: " + name);
  }

  /** @return sum of all three published steam rates, in kg/h */
  public static double getTotalSteamRateKgPerHour() {
    double total = 0.0;
    for (SteamInjectionReference injection : STEAM_INJECTIONS) {
      total += injection.getMassFlowRateKgPerHour();
    }
    return total;
  }

  /** @return always false because the source does not report steam injection tray locations */
  public static boolean hasExplicitSteamInjectionLocations() {
    return false;
  }

  /** @return true because Table 3 reports 150 degrees Celsius and 476 kPa for each steam row */
  public static boolean hasExplicitSteamTemperatureAndPressure() {
    return true;
  }

  /** @return always false because the source does not report steam quality */
  public static boolean hasExplicitSteamQuality() {
    return false;
  }

  /** @return always false because temperature and pressure alone do not resolve saturated-steam state */
  public static boolean hasExplicitSteamThermodynamicState() {
    return false;
  }

  /**
   * Return the source pump-around table in source order.
   *
   * @return defensive copy of the two immutable pump-around rows
   */
  public static PumparoundReference[] getPumparounds() {
    return PUMPAROUNDS.clone();
  }

  /**
   * Find one pump-around row by its exact source-table label.
   *
   * @param name exact source-table label
   * @return immutable pump-around reference
   * @throws IllegalArgumentException if the label is null or unknown
   */
  public static PumparoundReference getPumparound(String name) {
    if (name == null) {
      throw new IllegalArgumentException("Pump-around name cannot be null");
    }
    for (PumparoundReference pumparound : PUMPAROUNDS) {
      if (pumparound.getName().equals(name)) {
        return pumparound;
      }
    }
    throw new IllegalArgumentException("Unknown Sarir pump-around: " + name);
  }

  /**
   * Report whether Table 4 explicitly defines the direction used for its tray numbers.
   *
   * @return always false; the raw source labels must not be mapped to NeqSim tray indices without another authority
   */
  public static boolean hasExplicitPumparoundTrayNumberingBasis() {
    return false;
  }

  /** @return top pump-around flow rate in kg/h */
  public static double getTopPumpAroundRateKgPerHour() {
    return PUMPAROUNDS[0].getMassFlowRateKgPerHour();
  }

  /** @return bottom pump-around flow rate in kg/h */
  public static double getBottomPumpAroundRateKgPerHour() {
    return PUMPAROUNDS[1].getMassFlowRateKgPerHour();
  }

  /** Direction of one stream in the published ADU inlet/outlet table. */
  public enum AduStreamDirection {
    /** Stream enters the atmospheric distillation unit. */
    INLET,
    /** Stream leaves the atmospheric distillation unit. */
    OUTLET
  }

  /** Immutable row from the published ADU inlet/outlet stream table. */
  public static final class AduStreamReference implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String name;
    private final AduStreamDirection direction;
    private final double temperatureCelsius;
    private final double pressureKPa;
    private final double massFlowRateKgPerHour;

    private AduStreamReference(String name, AduStreamDirection direction, double temperatureCelsius,
        double pressureKPa, double massFlowRateKgPerHour) {
      this.name = name;
      this.direction = direction;
      this.temperatureCelsius = temperatureCelsius;
      this.pressureKPa = pressureKPa;
      this.massFlowRateKgPerHour = massFlowRateKgPerHour;
    }

    /** @return exact source-table stream label */
    public String getName() {
      return name;
    }

    /** @return inlet or outlet direction in the published table */
    public AduStreamDirection getDirection() {
      return direction;
    }

    /** @return source temperature in degrees Celsius */
    public double getTemperatureCelsius() {
      return temperatureCelsius;
    }

    /** @return source pressure in kPa as reported */
    public double getPressureKPa() {
      return pressureKPa;
    }

    /** @return source mass-flow rate in kg/h */
    public double getMassFlowRateKgPerHour() {
      return massFlowRateKgPerHour;
    }
  }

  /** Source equipment service receiving steam in the published operating case. */
  public enum SteamInjectionService {
    /** Main atmospheric crude column. */
    MAIN_ATMOSPHERIC_COLUMN,
    /** Kerosene side stripper. */
    KEROSENE_SIDE_STRIPPER,
    /** Diesel side stripper. */
    DIESEL_SIDE_STRIPPER
  }

  /**
   * Immutable source steam-injection row.
   *
   * <p>
   * The source identifies the receiving service and mass-flow rate, but not an injection tray or thermodynamic steam
   * state. This evidence must not be treated as an executable stream specification.
   * </p>
   */
  public static final class SteamInjectionReference implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String name;
    private final SteamInjectionService service;
    private final double massFlowRateKgPerHour;
    private final double temperatureCelsius;
    private final double pressureKPa;

    private SteamInjectionReference(String name, SteamInjectionService service, double massFlowRateKgPerHour,
        double temperatureCelsius, double pressureKPa) {
      this.name = name;
      this.service = service;
      this.massFlowRateKgPerHour = massFlowRateKgPerHour;
      this.temperatureCelsius = temperatureCelsius;
      this.pressureKPa = pressureKPa;
    }

    /** @return exact source label */
    public String getName() {
      return name;
    }

    /** @return receiving equipment service identified by the source */
    public SteamInjectionService getService() {
      return service;
    }

    /** @return source steam mass-flow rate in kg/h */
    public double getMassFlowRateKgPerHour() {
      return massFlowRateKgPerHour;
    }

    /** @return source steam temperature in degrees Celsius */
    public double getTemperatureCelsius() {
      return temperatureCelsius;
    }

    /** @return source steam pressure in kPa as reported */
    public double getPressureKPa() {
      return pressureKPa;
    }
  }

  /**
   * Immutable source-table pump-around row.
   *
   * <p>
   * Tray numbers are retained exactly as printed in Table 4. The table does not explicitly state whether they are
   * counted from the top or bottom, so callers must not treat them as NeqSim tray indices without additional evidence.
   * </p>
   */
  public static final class PumparoundReference implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String name;
    private final int sourceDrawTrayNumber;
    private final int sourceReturnTrayNumber;
    private final double massFlowRateKgPerHour;
    private final double drawTemperatureCelsius;
    private final double returnTemperatureCelsius;

    private PumparoundReference(String name, int sourceDrawTrayNumber, int sourceReturnTrayNumber,
        double massFlowRateKgPerHour, double drawTemperatureCelsius, double returnTemperatureCelsius) {
      this.name = name;
      this.sourceDrawTrayNumber = sourceDrawTrayNumber;
      this.sourceReturnTrayNumber = sourceReturnTrayNumber;
      this.massFlowRateKgPerHour = massFlowRateKgPerHour;
      this.drawTemperatureCelsius = drawTemperatureCelsius;
      this.returnTemperatureCelsius = returnTemperatureCelsius;
    }

    /** @return exact source-table label */
    public String getName() {
      return name;
    }

    /** @return raw draw-tray number printed in the source table */
    public int getSourceDrawTrayNumber() {
      return sourceDrawTrayNumber;
    }

    /** @return raw return-tray number printed in the source table */
    public int getSourceReturnTrayNumber() {
      return sourceReturnTrayNumber;
    }

    /** @return source pump-around mass flow rate in kg/h */
    public double getMassFlowRateKgPerHour() {
      return massFlowRateKgPerHour;
    }

    /** @return source draw temperature in degrees Celsius */
    public double getDrawTemperatureCelsius() {
      return drawTemperatureCelsius;
    }

    /** @return source return temperature in degrees Celsius */
    public double getReturnTemperatureCelsius() {
      return returnTemperatureCelsius;
    }

    /** @return draw temperature minus return temperature, in kelvin */
    public double getTemperatureDropKelvin() {
      return drawTemperatureCelsius - returnTemperatureCelsius;
    }
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
