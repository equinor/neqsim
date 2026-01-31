package neqsim.process.equipment.util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Models utility air systems for offshore and onshore facilities.
 *
 * <p>
 * Utility air systems provide compressed air for:
 * </p>
 * <ul>
 * <li><b>Instrument air</b> - Clean, dry air for pneumatic instruments and controls</li>
 * <li><b>Plant air</b> - General purpose air for tools and cleaning</li>
 * <li><b>Service air</b> - Air for maintenance activities</li>
 * <li><b>Breathing air</b> - Respirable air for personnel (higher purity)</li>
 * </ul>
 *
 * <h2>Design Standards</h2>
 * <ul>
 * <li>ISO 8573-1 - Compressed Air Quality Classes</li>
 * <li>NORSOK P-002 - Process System Design</li>
 * <li>API RP 11P - Packaged Reciprocating Compressors</li>
 * </ul>
 *
 * <h2>Typical Specifications</h2>
 * <table border="1">
 * <caption>Air quality requirements by service</caption>
 * <tr>
 * <th>Service</th>
 * <th>Pressure</th>
 * <th>Dew Point</th>
 * <th>Oil Content</th>
 * </tr>
 * <tr>
 * <td>Instrument Air</td>
 * <td>7-8 barg</td>
 * <td>-40°C</td>
 * <td>&lt;0.01 mg/m³</td>
 * </tr>
 * <tr>
 * <td>Plant Air</td>
 * <td>6-7 barg</td>
 * <td>+3°C</td>
 * <td>&lt;1 mg/m³</td>
 * </tr>
 * <tr>
 * <td>Breathing Air</td>
 * <td>5-7 barg</td>
 * <td>-40°C</td>
 * <td>&lt;0.003 mg/m³</td>
 * </tr>
 * </table>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class UtilityAirSystem extends ProcessEquipmentBaseClass {

  private static final long serialVersionUID = 1001L;
  private static final Logger logger = LogManager.getLogger(UtilityAirSystem.class);

  // ============================================================================
  // Air Quality Classes (ISO 8573-1)
  // ============================================================================

  /**
   * ISO 8573-1 Air Quality Classes.
   */
  public enum AirQualityClass {
    /** Class 1 - Highest quality (breathing air). */
    CLASS_1(0.1, -70, 0.01, "Breathing/Medical"),
    /** Class 2 - Very high quality (sensitive instruments). */
    CLASS_2(1.0, -40, 0.1, "Instrument Air"),
    /** Class 3 - High quality (general instruments). */
    CLASS_3(5.0, -20, 1.0, "Control Air"),
    /** Class 4 - Medium quality (plant air). */
    CLASS_4(15.0, 3, 5.0, "Plant Air"),
    /** Class 5 - Standard quality (general use). */
    CLASS_5(40.0, 7, 25.0, "Service Air");

    private final double maxParticleSizeMicron;
    private final double maxDewPointC;
    private final double maxOilMgM3;
    private final String typicalUse;

    AirQualityClass(double particles, double dewPoint, double oil, String use) {
      this.maxParticleSizeMicron = particles;
      this.maxDewPointC = dewPoint;
      this.maxOilMgM3 = oil;
      this.typicalUse = use;
    }

    /**
     * Gets max particle size.
     *
     * @return particle size in microns
     */
    public double getMaxParticleSizeMicron() {
      return maxParticleSizeMicron;
    }

    /**
     * Gets max dew point.
     *
     * @return dew point in °C
     */
    public double getMaxDewPointC() {
      return maxDewPointC;
    }

    /**
     * Gets max oil content.
     *
     * @return oil content in mg/m³
     */
    public double getMaxOilMgM3() {
      return maxOilMgM3;
    }

    /**
     * Gets typical use description.
     *
     * @return typical use
     */
    public String getTypicalUse() {
      return typicalUse;
    }
  }

  // ============================================================================
  // System Components
  // ============================================================================

  /**
   * Air compressor type.
   */
  public enum CompressorType {
    /** Rotary screw compressor - most common for continuous duty. */
    ROTARY_SCREW,
    /** Reciprocating compressor - high pressure capability. */
    RECIPROCATING,
    /** Centrifugal compressor - large capacity. */
    CENTRIFUGAL,
    /** Scroll compressor - small, quiet operation. */
    SCROLL
  }

  /**
   * Air dryer type.
   */
  public enum DryerType {
    /** Refrigerated dryer - dew point +3 to +10°C. */
    REFRIGERATED(3.0, 0.95),
    /** Desiccant dryer (heatless) - dew point -40°C. */
    DESICCANT_HEATLESS(-40.0, 0.85),
    /** Desiccant dryer (heated) - dew point -40°C, more efficient. */
    DESICCANT_HEATED(-40.0, 0.92),
    /** Membrane dryer - compact, no moving parts. */
    MEMBRANE(-20.0, 0.80),
    /** Combination refrigerated + desiccant. */
    HYBRID(-70.0, 0.88);

    private final double achievableDewPointC;
    private final double airYieldFraction; // Fraction of inlet air delivered

    DryerType(double dewPoint, double yield) {
      this.achievableDewPointC = dewPoint;
      this.airYieldFraction = yield;
    }

    /**
     * Gets achievable dew point.
     *
     * @return dew point in °C
     */
    public double getAchievableDewPointC() {
      return achievableDewPointC;
    }

    /**
     * Gets air yield fraction.
     *
     * @return yield (0-1)
     */
    public double getAirYieldFraction() {
      return airYieldFraction;
    }
  }

  // ============================================================================
  // System Parameters
  // ============================================================================

  /** Target air quality class. */
  private AirQualityClass targetQuality = AirQualityClass.CLASS_2;

  /** Compressor type. */
  private CompressorType compressorType = CompressorType.ROTARY_SCREW;

  /** Dryer type. */
  private DryerType dryerType = DryerType.DESICCANT_HEATED;

  /** Number of compressors (N+1 redundancy typical). */
  private int numberOfCompressors = 3; // 2 duty + 1 standby

  /** Receiver tank volume [m³]. */
  private double receiverVolume = 10.0;

  // Operating Parameters
  /** Discharge pressure [barg]. */
  private double dischargePressure = 8.0;

  /** Inlet temperature [°C]. */
  private double inletTemperature = 25.0;

  /** Relative humidity at inlet [%]. */
  private double inletRelativeHumidity = 70.0;

  /** Aftercooler outlet temperature [°C]. */
  private double aftercoolerOutletTemp = 35.0;

  // Demand Parameters
  /** Total air demand [Nm³/hr]. */
  private double totalAirDemand = 500.0;

  /** Instrument air demand fraction. */
  private double instrumentAirFraction = 0.60;

  /** Plant air demand fraction. */
  private double plantAirFraction = 0.30;

  /** Service/breathing air fraction. */
  private double serviceAirFraction = 0.10;

  // Consumer list
  private final List<AirConsumer> consumers = new ArrayList<>();

  // Calculated Results
  private double compressorPowerKW = 0.0;
  private double dryerPurgeLoss = 0.0;
  private double condensateVolumeM3h = 0.0;
  private double actualDewPointC = 0.0;
  private double specificEnergyKWhPerNm3 = 0.0;
  private boolean systemRunning = false;

  // ============================================================================
  // Constructors
  // ============================================================================

  /**
   * Default constructor.
   *
   * @param name system name
   */
  public UtilityAirSystem(String name) {
    super(name);
  }

  /**
   * Constructor with capacity.
   *
   * @param name system name
   * @param airDemandNm3h total air demand in Nm³/hr
   */
  public UtilityAirSystem(String name, double airDemandNm3h) {
    super(name);
    this.totalAirDemand = airDemandNm3h;
  }

  /**
   * Constructor with quality specification.
   *
   * @param name system name
   * @param airDemandNm3h total air demand
   * @param qualityClass target air quality
   */
  public UtilityAirSystem(String name, double airDemandNm3h, AirQualityClass qualityClass) {
    super(name);
    this.totalAirDemand = airDemandNm3h;
    this.targetQuality = qualityClass;
    selectDryerForQuality();
  }

  // ============================================================================
  // Configuration
  // ============================================================================

  /**
   * Add an air consumer.
   *
   * @param consumer air consumer to add
   */
  public void addConsumer(AirConsumer consumer) {
    consumers.add(consumer);
    updateTotalDemand();
  }

  /**
   * Add an air consumer by parameters.
   *
   * @param name consumer name
   * @param demandNm3h air demand [Nm³/hr]
   * @param quality required quality class
   */
  public void addConsumer(String name, double demandNm3h, AirQualityClass quality) {
    consumers.add(new AirConsumer(name, demandNm3h, quality));
    updateTotalDemand();
  }

  /**
   * Update total demand from consumers.
   */
  private void updateTotalDemand() {
    if (!consumers.isEmpty()) {
      totalAirDemand = consumers.stream().mapToDouble(AirConsumer::getDemandNm3h).sum();
    }
  }

  /**
   * Select appropriate dryer for quality requirement.
   */
  private void selectDryerForQuality() {
    double requiredDewPoint = targetQuality.getMaxDewPointC();
    if (requiredDewPoint <= -40) {
      dryerType = DryerType.DESICCANT_HEATED;
    } else if (requiredDewPoint <= -20) {
      dryerType = DryerType.MEMBRANE;
    } else {
      dryerType = DryerType.REFRIGERATED;
    }
  }

  /**
   * Auto-size system based on demand.
   */
  public void autoSize() {
    // Calculate compressor capacity with margin
    double requiredCapacity = totalAirDemand / dryerType.getAirYieldFraction() * 1.15;

    // Size receiver for 1 minute of demand at working pressure
    receiverVolume = (totalAirDemand / 60.0) * (1.0 + 1.013 / (dischargePressure + 1.013));

    // Determine number of compressors (N+1)
    double compressorCapacity = requiredCapacity / 2.0; // 2 duty compressors
    numberOfCompressors = 3; // 2 duty + 1 standby

    logger.info("Auto-sized utility air system: {} Nm³/hr capacity", requiredCapacity);
  }

  // ============================================================================
  // Run Calculation
  // ============================================================================

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    systemRunning = true;

    // Calculate inlet air moisture content
    double saturationPressure = calculateSaturationPressure(inletTemperature);
    double inletMoistureKgKg = 0.622 * (inletRelativeHumidity / 100.0 * saturationPressure)
        / (101.325 - inletRelativeHumidity / 100.0 * saturationPressure);

    // Calculate air flow through compressor (accounting for dryer purge)
    double compressorFlowNm3h = totalAirDemand / dryerType.getAirYieldFraction();
    dryerPurgeLoss = compressorFlowNm3h - totalAirDemand;

    // Calculate compressor power
    // Isothermal power: P = p1 * V1 * ln(p2/p1) / η
    double pressureRatio = (dischargePressure + 1.013) / 1.013;
    double polytropicExponent = 1.3; // For air
    double isentropicEfficiency = 0.75; // Typical for rotary screw

    // Polytropic compression power [kW]
    double inletPressurePa = 101325.0;
    double flowM3s = compressorFlowNm3h / 3600.0;
    compressorPowerKW = (polytropicExponent / (polytropicExponent - 1)) * inletPressurePa * flowM3s
        * (Math.pow(pressureRatio, (polytropicExponent - 1) / polytropicExponent) - 1)
        / isentropicEfficiency / 1000.0;

    // Account for motor/drive efficiency
    compressorPowerKW = compressorPowerKW / 0.95;

    // Specific energy
    specificEnergyKWhPerNm3 = compressorPowerKW / totalAirDemand;

    // Condensate calculation (moisture removed by aftercooler and dryer)
    double aftercoolerSatPressure = calculateSaturationPressure(aftercoolerOutletTemp);
    double aftercoolerMoistureKgKg = 0.622 * aftercoolerSatPressure
        / ((dischargePressure + 1.013) * 100.0 - aftercoolerSatPressure);

    double moistureRemoved = Math.max(0, inletMoistureKgKg - aftercoolerMoistureKgKg);
    double airMassKgh = compressorFlowNm3h * 1.293; // kg/hr at NTP
    condensateVolumeM3h = moistureRemoved * airMassKgh / 1000.0; // m³/hr water

    // Achieved dew point
    actualDewPointC = dryerType.getAchievableDewPointC();

    setCalculationIdentifier(id);
  }

  /**
   * Calculate saturation pressure of water vapor.
   *
   * @param temperatureC temperature in °C
   * @return saturation pressure in kPa
   */
  private double calculateSaturationPressure(double temperatureC) {
    // Antoine equation for water
    double A = 8.07131;
    double B = 1730.63;
    double C = 233.426;
    double mmHg = Math.pow(10, A - B / (C + temperatureC));
    return mmHg * 0.133322; // Convert to kPa
  }

  // ============================================================================
  // Results
  // ============================================================================

  /**
   * Gets compressor power.
   *
   * @return total compressor power [kW]
   */
  public double getCompressorPowerKW() {
    return compressorPowerKW;
  }

  /**
   * Gets specific energy consumption.
   *
   * @return energy per Nm³ [kWh/Nm³]
   */
  public double getSpecificEnergy() {
    return specificEnergyKWhPerNm3;
  }

  /**
   * Gets dryer purge air loss.
   *
   * @return purge loss [Nm³/hr]
   */
  public double getDryerPurgeLoss() {
    return dryerPurgeLoss;
  }

  /**
   * Gets condensate volume.
   *
   * @return condensate [m³/hr]
   */
  public double getCondensateVolume() {
    return condensateVolumeM3h;
  }

  /**
   * Gets achieved dew point.
   *
   * @return dew point [°C]
   */
  public double getActualDewPoint() {
    return actualDewPointC;
  }

  /**
   * Checks if quality target is met.
   *
   * @return true if achieved dew point meets target
   */
  public boolean isQualityTargetMet() {
    return actualDewPointC <= targetQuality.getMaxDewPointC();
  }

  /**
   * Gets receiver holdup time at full demand.
   *
   * @return holdup time [minutes]
   */
  public double getReceiverHoldupMinutes() {
    double pressureRatio = (dischargePressure + 1.013) / 1.013;
    double storedVolume = receiverVolume * pressureRatio;
    return storedVolume / totalAirDemand * 60.0;
  }

  /**
   * Calculate annual operating cost.
   *
   * @param electricityCostPerKWh electricity cost
   * @param operatingHoursPerYear annual operating hours
   * @return annual cost
   */
  public double calculateAnnualOperatingCost(double electricityCostPerKWh,
      double operatingHoursPerYear) {
    return compressorPowerKW * operatingHoursPerYear * electricityCostPerKWh;
  }

  /**
   * Gets results as JSON.
   *
   * @return JSON string
   */
  public String toJson() {
    Map<String, Object> results = new LinkedHashMap<>();
    results.put("systemName", getName());
    results.put("totalAirDemandNm3h", totalAirDemand);
    results.put("dischargePressureBarg", dischargePressure);
    results.put("targetQuality", targetQuality.name());
    results.put("compressorType", compressorType.name());
    results.put("dryerType", dryerType.name());
    results.put("numberOfCompressors", numberOfCompressors);
    results.put("receiverVolumeM3", receiverVolume);

    Map<String, Object> operatingResults = new LinkedHashMap<>();
    operatingResults.put("compressorPowerKW", compressorPowerKW);
    operatingResults.put("specificEnergyKWhPerNm3", specificEnergyKWhPerNm3);
    operatingResults.put("dryerPurgeLossNm3h", dryerPurgeLoss);
    operatingResults.put("condensateM3h", condensateVolumeM3h);
    operatingResults.put("actualDewPointC", actualDewPointC);
    operatingResults.put("qualityTargetMet", isQualityTargetMet());
    operatingResults.put("receiverHoldupMinutes", getReceiverHoldupMinutes());
    results.put("operatingResults", operatingResults);

    if (!consumers.isEmpty()) {
      List<Map<String, Object>> consumerList = new ArrayList<>();
      for (AirConsumer c : consumers) {
        Map<String, Object> cmap = new LinkedHashMap<>();
        cmap.put("name", c.getName());
        cmap.put("demandNm3h", c.getDemandNm3h());
        cmap.put("qualityClass", c.getRequiredQuality().name());
        consumerList.add(cmap);
      }
      results.put("consumers", consumerList);
    }

    return new GsonBuilder().setPrettyPrinting().create().toJson(results);
  }

  // ============================================================================
  // Getters and Setters
  // ============================================================================

  /**
   * Gets target quality class.
   *
   * @return quality class
   */
  public AirQualityClass getTargetQuality() {
    return targetQuality;
  }

  /**
   * Sets target quality class.
   *
   * @param quality quality class
   */
  public void setTargetQuality(AirQualityClass quality) {
    this.targetQuality = quality;
    selectDryerForQuality();
  }

  /**
   * Gets compressor type.
   *
   * @return compressor type
   */
  public CompressorType getCompressorType() {
    return compressorType;
  }

  /**
   * Sets compressor type.
   *
   * @param type compressor type
   */
  public void setCompressorType(CompressorType type) {
    this.compressorType = type;
  }

  /**
   * Gets dryer type.
   *
   * @return dryer type
   */
  public DryerType getDryerType() {
    return dryerType;
  }

  /**
   * Sets dryer type.
   *
   * @param type dryer type
   */
  public void setDryerType(DryerType type) {
    this.dryerType = type;
  }

  /**
   * Gets discharge pressure.
   *
   * @return pressure [barg]
   */
  public double getDischargePressure() {
    return dischargePressure;
  }

  /**
   * Sets discharge pressure.
   *
   * @param pressure pressure [barg]
   */
  public void setDischargePressure(double pressure) {
    this.dischargePressure = pressure;
  }

  /**
   * Gets total air demand.
   *
   * @return demand [Nm³/hr]
   */
  public double getTotalAirDemand() {
    return totalAirDemand;
  }

  /**
   * Sets total air demand.
   *
   * @param demand demand [Nm³/hr]
   */
  public void setTotalAirDemand(double demand) {
    this.totalAirDemand = demand;
  }

  /**
   * Gets inlet temperature.
   *
   * @return temperature [°C]
   */
  public double getInletTemperature() {
    return inletTemperature;
  }

  /**
   * Sets inlet temperature.
   *
   * @param temperature temperature [°C]
   */
  public void setInletTemperature(double temperature) {
    this.inletTemperature = temperature;
  }

  /**
   * Gets inlet relative humidity.
   *
   * @return humidity [%]
   */
  public double getInletRelativeHumidity() {
    return inletRelativeHumidity;
  }

  /**
   * Sets inlet relative humidity.
   *
   * @param humidity humidity [%]
   */
  public void setInletRelativeHumidity(double humidity) {
    this.inletRelativeHumidity = humidity;
  }

  /**
   * Gets receiver volume.
   *
   * @return volume [m³]
   */
  public double getReceiverVolume() {
    return receiverVolume;
  }

  /**
   * Sets receiver volume.
   *
   * @param volume volume [m³]
   */
  public void setReceiverVolume(double volume) {
    this.receiverVolume = volume;
  }

  /**
   * Gets number of compressors.
   *
   * @return number of compressors
   */
  public int getNumberOfCompressors() {
    return numberOfCompressors;
  }

  /**
   * Sets number of compressors.
   *
   * @param number number of compressors
   */
  public void setNumberOfCompressors(int number) {
    this.numberOfCompressors = number;
  }

  // ============================================================================
  // Inner Classes
  // ============================================================================

  /**
   * Air consumer (equipment or system using compressed air).
   */
  public static class AirConsumer implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String name;
    private double demandNm3h;
    private AirQualityClass requiredQuality;
    private boolean isCritical;

    /**
     * Creates an air consumer.
     *
     * @param name consumer name
     * @param demandNm3h air demand [Nm³/hr]
     * @param quality required air quality
     */
    public AirConsumer(String name, double demandNm3h, AirQualityClass quality) {
      this.name = name;
      this.demandNm3h = demandNm3h;
      this.requiredQuality = quality;
      this.isCritical = false;
    }

    /**
     * Gets consumer name.
     *
     * @return name
     */
    public String getName() {
      return name;
    }

    /**
     * Gets air demand.
     *
     * @return demand [Nm³/hr]
     */
    public double getDemandNm3h() {
      return demandNm3h;
    }

    /**
     * Sets air demand.
     *
     * @param demand demand [Nm³/hr]
     */
    public void setDemandNm3h(double demand) {
      this.demandNm3h = demand;
    }

    /**
     * Gets required quality.
     *
     * @return quality class
     */
    public AirQualityClass getRequiredQuality() {
      return requiredQuality;
    }

    /**
     * Sets required quality.
     *
     * @param quality quality class
     */
    public void setRequiredQuality(AirQualityClass quality) {
      this.requiredQuality = quality;
    }

    /**
     * Checks if consumer is critical.
     *
     * @return true if critical
     */
    public boolean isCritical() {
      return isCritical;
    }

    /**
     * Sets critical flag.
     *
     * @param critical true if critical
     */
    public void setCritical(boolean critical) {
      this.isCritical = critical;
    }
  }
}
