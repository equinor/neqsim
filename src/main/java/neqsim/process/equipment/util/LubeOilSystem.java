package neqsim.process.equipment.util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.util.validation.ValidationResult;

/**
 * Screening design of a lubrication (lube oil) console for rotating machinery according to API 614 / ISO 10438
 * (Lubrication, shaft-sealing and control-oil systems).
 *
 * <p>
 * The system sizes the complete oil console from the heat load of the served bearings, gears and seals:
 * </p>
 * <ul>
 * <li>oil flow per consumer from the bearing heat load and the allowable temperature rise</li>
 * <li>main and standby pump rated capacity, differential pressure, shaft power and motor rating</li>
 * <li>reservoir working capacity from retention time and turnover rate</li>
 * <li>cooler duty and cooling water demand (twin 2 x 100 % coolers)</li>
 * <li>twin 2 x 100 % filters with transfer valve</li>
 * <li>overhead (rundown) tank volume for the machine coast-down</li>
 * <li>accumulator volume covering the pump change-over transient</li>
 * <li>electric immersion heater power and minimum sheath area (watt density limit)</li>
 * <li>oil supply pipe size and gravity drain pipe size (half full, sloped)</li>
 * </ul>
 *
 * <h2>Design basis</h2>
 * <table border="1">
 * <caption>Default acceptance criteria taken from API 614 / ISO 10438 practice</caption>
 * <tr>
 * <th>Item</th>
 * <th>Criterion</th>
 * </tr>
 * <tr>
 * <td>Reservoir working capacity</td>
 * <td>Minimum 5 min retention (special purpose), 3 min (general purpose)</td>
 * </tr>
 * <tr>
 * <td>Reservoir turnover</td>
 * <td>Maximum 8 turnovers per hour</td>
 * </tr>
 * <tr>
 * <td>Oil supply temperature</td>
 * <td>Maximum 49 &deg;C to hydrodynamic bearings</td>
 * </tr>
 * <tr>
 * <td>Pumps</td>
 * <td>2 x 100 %, rated for at least 110 % of maximum system demand</td>
 * </tr>
 * <tr>
 * <td>Filters</td>
 * <td>Twin 2 x 100 %, 10 micron nominal, clean pressure drop max 0.35 bar</td>
 * </tr>
 * <tr>
 * <td>Overhead tank</td>
 * <td>Minimum 3 min bearing flow during coast-down</td>
 * </tr>
 * <tr>
 * <td>Immersion heater</td>
 * <td>Maximum watt density 2.3 W/cm&sup2; to avoid oil coking</td>
 * </tr>
 * <tr>
 * <td>Drain lines</td>
 * <td>Half full at a slope of at least 20.8 mm/m</td>
 * </tr>
 * </table>
 *
 * <p>
 * The defaults reflect common API 614 / ISO 10438 practice and are all configurable. They must be verified against the
 * edition of the standard and the project specification that governs the actual purchase.
 * </p>
 *
 * <h2>Example</h2>
 *
 * <pre>
 * LubeOilSystem console = new LubeOilSystem("Lube oil console 20-LO-001");
 * console.setServiceCategory(LubeOilSystem.ServiceCategory.SPECIAL_PURPOSE);
 * console.setOilGrade(LubeOilSystem.OilGrade.ISO_VG_46);
 * console.addConsumerFromShaftPower("Compressor bearings", LubeOilSystem.ConsumerType.JOURNAL_BEARING, 6000.0);
 * console.addConsumerFromShaftPower("Gearbox", LubeOilSystem.ConsumerType.GEARBOX, 6000.0);
 * console.addControlOilConsumer("Governor", 60.0, 10.0);
 * console.run();
 * String json = console.toJson();
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class LubeOilSystem extends ProcessEquipmentBaseClass {

  private static final long serialVersionUID = 1000L;
  private static final Logger logger = LogManager.getLogger(LubeOilSystem.class);

  /** Maximum oil supply temperature to hydrodynamic bearings [C]. */
  public static final double MAX_BEARING_SUPPLY_TEMPERATURE = 49.0;

  /** Maximum reservoir turnover rate [1/hr]. */
  public static final double MAX_RESERVOIR_TURNOVERS_PER_HOUR = 8.0;

  /** Maximum filter rating for lube oil service [micron nominal]. */
  public static final double MAX_FILTER_RATING_MICRON = 10.0;

  /** Maximum clean filter pressure drop at rated flow and 38 C [bar]. */
  public static final double MAX_CLEAN_FILTER_PRESSURE_DROP = 0.35;

  /** Minimum pump rated capacity relative to maximum system demand [-]. */
  public static final double MIN_PUMP_RATED_MARGIN = 1.10;

  /** Minimum overhead (rundown) tank holdup for special purpose machines [min]. */
  public static final double MIN_RUNDOWN_TIME = 3.0;

  /** Maximum watt density of electric immersion heaters [W/cm2]. */
  public static final double MAX_HEATER_WATT_DENSITY = 2.3;

  /** Minimum slope of gravity drain lines [m/m], equal to 20.8 mm/m. */
  public static final double MIN_DRAIN_SLOPE = 0.0208;

  /** Standard IEC motor ratings used when selecting the pump motor [kW]. */
  private static final double[] STANDARD_MOTOR_RATINGS = { 0.75, 1.1, 1.5, 2.2, 3.0, 4.0, 5.5, 7.5, 11.0, 15.0, 18.5,
      22.0, 30.0, 37.0, 45.0, 55.0, 75.0, 90.0, 110.0, 132.0, 160.0, 200.0, 250.0, 315.0, 355.0, 400.0 };

  // ============================================================================
  // Enumerations
  // ============================================================================

  /**
   * Machinery service category, which sets the applicable part of the standard and the minimum reservoir retention
   * time.
   */
  public enum ServiceCategory {
    /** General purpose machinery, API 614 Chapter 2 / ISO 10438-2. */
    GENERAL_PURPOSE("API 614 Chapter 2 / ISO 10438-2", 3.0, false),
    /** Special purpose machinery, API 614 Chapter 3 / ISO 10438-3. */
    SPECIAL_PURPOSE("API 614 Chapter 3 / ISO 10438-3", 5.0, true);

    private final String reference;
    private final double minRetentionMinutes;
    private final boolean rundownTankRequired;

    ServiceCategory(String reference, double minRetentionMinutes, boolean rundownTankRequired) {
      this.reference = reference;
      this.minRetentionMinutes = minRetentionMinutes;
      this.rundownTankRequired = rundownTankRequired;
    }

    /**
     * Gets the standard reference for the category.
     *
     * @return standard reference text
     */
    public String getReference() {
      return reference;
    }

    /**
     * Gets the minimum reservoir retention time.
     *
     * @return retention time [min]
     */
    public double getMinRetentionMinutes() {
      return minRetentionMinutes;
    }

    /**
     * Checks whether an overhead (rundown) tank is required.
     *
     * @return true when a rundown tank is required
     */
    public boolean isRundownTankRequired() {
      return rundownTankRequired;
    }
  }

  /**
   * ISO 3448 viscosity grades commonly used for turbomachinery lube oil.
   */
  public enum OilGrade {
    /** ISO VG 32 turbine oil. */
    ISO_VG_32(32.0, 5.4, 857.0),
    /** ISO VG 46 turbine oil. */
    ISO_VG_46(46.0, 6.8, 865.0),
    /** ISO VG 68 gear and bearing oil. */
    ISO_VG_68(68.0, 8.7, 872.0),
    /** ISO VG 100 gear oil. */
    ISO_VG_100(100.0, 11.4, 878.0);

    private final double viscosity40;
    private final double viscosity100;
    private final double density15;

    OilGrade(double viscosity40, double viscosity100, double density15) {
      this.viscosity40 = viscosity40;
      this.viscosity100 = viscosity100;
      this.density15 = density15;
    }

    /**
     * Gets the nominal kinematic viscosity at 40 C.
     *
     * @return kinematic viscosity [cSt]
     */
    public double getViscosity40() {
      return viscosity40;
    }

    /**
     * Gets the nominal kinematic viscosity at 100 C.
     *
     * @return kinematic viscosity [cSt]
     */
    public double getViscosity100() {
      return viscosity100;
    }

    /**
     * Gets the density at 15 C.
     *
     * @return density [kg/m3]
     */
    public double getDensity15() {
      return density15;
    }
  }

  /**
   * Type of oil consumer served by the console.
   */
  public enum ConsumerType {
    /** Hydrodynamic journal bearing. */
    JOURNAL_BEARING(0.008, 25.0, false),
    /** Tilting pad thrust bearing. */
    THRUST_BEARING(0.006, 25.0, false),
    /** Speed increasing or reducing gear. */
    GEARBOX(0.015, 20.0, false),
    /** Oil lubricated mechanical or film riding seal. */
    MECHANICAL_SEAL(0.003, 20.0, false),
    /** Control oil consumer such as a governor or trip and throttle valve. */
    CONTROL_OIL(0.0, 0.0, true),
    /** Other lube oil consumer. */
    OTHER(0.005, 20.0, false);

    private final double lossFraction;
    private final double temperatureRise;
    private final boolean controlOil;

    ConsumerType(double lossFraction, double temperatureRise, boolean controlOil) {
      this.lossFraction = lossFraction;
      this.temperatureRise = temperatureRise;
      this.controlOil = controlOil;
    }

    /**
     * Gets the indicative mechanical loss as a fraction of transmitted shaft power.
     *
     * @return loss fraction [-]
     */
    public double getLossFraction() {
      return lossFraction;
    }

    /**
     * Gets the default oil temperature rise across the consumer.
     *
     * @return temperature rise [K]
     */
    public double getTemperatureRise() {
      return temperatureRise;
    }

    /**
     * Checks whether the consumer is a control oil consumer.
     *
     * @return true for control oil consumers
     */
    public boolean isControlOil() {
      return controlOil;
    }
  }

  // ============================================================================
  // Design input
  // ============================================================================

  /** Machinery service category. */
  private ServiceCategory serviceCategory = ServiceCategory.SPECIAL_PURPOSE;

  /** Selected oil viscosity grade. */
  private OilGrade oilGrade = OilGrade.ISO_VG_46;

  /** Oil supply temperature to the bearings [C]. */
  private double supplyTemperature = 45.0;

  /** Reservoir bulk oil temperature during normal operation [C]. */
  private double reservoirTemperature = 60.0;

  /** Minimum oil temperature before start-up, used for heater sizing [C]. */
  private double minimumStartTemperature = 5.0;

  /** Oil temperature required before the machine may be started [C]. */
  private double requiredStartTemperature = 20.0;

  /** Lube oil header pressure at the bearings [barg]. */
  private double lubeHeaderPressure = 1.5;

  /** Control oil header pressure [barg]. */
  private double controlOilHeaderPressure = 10.0;

  /** Static elevation from the reservoir to the highest bearing [m]. */
  private double bearingElevation = 5.0;

  /** Cooler pressure drop at rated flow [bar]. */
  private double coolerPressureDrop = 0.35;

  /** Clean filter pressure drop at rated flow and 38 C [bar]. */
  private double filterPressureDropClean = 0.30;

  /** Filter pressure drop used for pump sizing, that is the fouled condition [bar]. */
  private double filterPressureDropFouled = 1.0;

  /** Piping and fitting pressure losses [bar]. */
  private double pipingPressureDrop = 0.5;

  /** Pressure drop across the header pressure control valve [bar]. */
  private double controlValvePressureDrop = 1.0;

  /** Filter rating [micron nominal]. */
  private double filterRatingMicron = 10.0;

  /** Number of installed main pumps, normally 2 x 100 percent. */
  private int numberOfPumps = 2;

  /** Number of installed coolers, normally 2 x 100 percent. */
  private int numberOfCoolers = 2;

  /** Number of installed filters, normally 2 x 100 percent. */
  private int numberOfFilters = 2;

  /** Pump rated capacity relative to the maximum system demand [-]. */
  private double pumpDesignMargin = 1.10;

  /** Pump hydraulic efficiency [-]. */
  private double pumpEfficiency = 0.65;

  /** Cooling water inlet temperature [C]. */
  private double coolingWaterInletTemperature = 20.0;

  /** Cooling water outlet temperature [C]. */
  private double coolingWaterOutletTemperature = 32.0;

  /** Reservoir free board and vapour space fraction of the total volume [-]. */
  private double reservoirFreeboardFraction = 0.20;

  /** Overhead (rundown) tank holdup time [min]. */
  private double rundownTime = 3.0;

  /** Pump change-over transient covered by the accumulator [s]. */
  private double accumulatorTransferTime = 10.0;

  /** Allowable header pressure decay during the pump change-over [bar]. */
  private double accumulatorAllowablePressureDrop = 0.5;

  /** Polytropic exponent used for the accumulator gas expansion [-]. */
  private double accumulatorGasExponent = 1.4;

  /** True when a change-over accumulator is fitted. */
  private boolean accumulatorFitted = true;

  /** Heater soak time used to size the immersion heater [hr]. */
  private double heaterSoakTime = 8.0;

  /** Maximum oil velocity in pressure piping [m/s]. */
  private double maxSupplyVelocity = 3.0;

  /** Slope of the gravity drain lines [m/m]. */
  private double drainSlope = 0.0208;

  /** Manning roughness coefficient used for the gravity drain lines [-]. */
  private double drainRoughness = 0.012;

  /** Consumers served by the console. */
  private final List<OilConsumer> consumers = new ArrayList<OilConsumer>();

  // ============================================================================
  // Calculated results
  // ============================================================================

  private double totalHeatLoad = 0.0;
  private double lubeOilFlow = 0.0;
  private double controlOilFlow = 0.0;
  private double normalOilFlow = 0.0;
  private double ratedPumpFlow = 0.0;
  private double pumpDischargePressure = 0.0;
  private double pumpDifferentialPressure = 0.0;
  private double pumpHydraulicPower = 0.0;
  private double pumpShaftPower = 0.0;
  private double pumpMotorRating = 0.0;
  private double reservoirWorkingVolume = 0.0;
  private double reservoirTotalVolume = 0.0;
  private double reservoirRetentionTime = 0.0;
  private double reservoirTurnoverRate = 0.0;
  private double coolerDuty = 0.0;
  private double coolerDutyPerUnit = 0.0;
  private double coolingWaterFlow = 0.0;
  private double rundownTankVolume = 0.0;
  private double accumulatorVolume = 0.0;
  private double accumulatorPrechargePressure = 0.0;
  private double heaterPower = 0.0;
  private double heaterMinimumArea = 0.0;
  private double supplyPipeDiameter = 0.0;
  private double drainPipeDiameter = 0.0;
  private double oilDensityAtSupply = 0.0;
  private double oilHeatCapacityAtSupply = 0.0;
  private double oilViscosityAtSupply = 0.0;
  private boolean systemRunning = false;

  /** Compliance checks populated by the last run. */
  private final List<ComplianceCheck> complianceChecks = new ArrayList<ComplianceCheck>();

  // ============================================================================
  // Constructors
  // ============================================================================

  /**
   * Creates a lube oil console with default design data.
   *
   * @param name equipment name
   */
  public LubeOilSystem(String name) {
    super(name);
  }

  /**
   * Creates a lube oil console for a given machinery service category.
   *
   * @param name equipment name
   * @param serviceCategory machinery service category
   */
  public LubeOilSystem(String name, ServiceCategory serviceCategory) {
    super(name);
    setServiceCategory(serviceCategory);
  }

  // ============================================================================
  // Consumer configuration
  // ============================================================================

  /**
   * Adds a consumer to the console.
   *
   * @param consumer consumer to add
   */
  public void addConsumer(OilConsumer consumer) {
    consumers.add(consumer);
  }

  /**
   * Adds a lube oil consumer defined by its heat load.
   *
   * @param name consumer name
   * @param type consumer type
   * @param heatLoadKW dissipated heat load [kW]
   * @return the created consumer
   */
  public OilConsumer addConsumer(String name, ConsumerType type, double heatLoadKW) {
    OilConsumer consumer = new OilConsumer(name, type);
    consumer.setHeatLoad(heatLoadKW);
    consumers.add(consumer);
    return consumer;
  }

  /**
   * Adds a lube oil consumer where the heat load is estimated from the transmitted shaft power using the indicative
   * loss fraction of the consumer type.
   *
   * @param name consumer name
   * @param type consumer type
   * @param shaftPowerKW transmitted shaft power [kW]
   * @return the created consumer
   */
  public OilConsumer addConsumerFromShaftPower(String name, ConsumerType type, double shaftPowerKW) {
    OilConsumer consumer = new OilConsumer(name, type);
    consumer.setHeatLoad(shaftPowerKW * type.getLossFraction());
    consumer.setShaftPower(shaftPowerKW);
    consumers.add(consumer);
    return consumer;
  }

  /**
   * Adds a control oil consumer with a specified flow demand.
   *
   * @param name consumer name
   * @param flowLitrePerMin control oil flow [L/min]
   * @param supplyPressure required supply pressure [barg]
   * @return the created consumer
   */
  public OilConsumer addControlOilConsumer(String name, double flowLitrePerMin, double supplyPressure) {
    OilConsumer consumer = new OilConsumer(name, ConsumerType.CONTROL_OIL);
    consumer.setSpecifiedFlow(flowLitrePerMin);
    consumer.setRequiredSupplyPressure(supplyPressure);
    consumers.add(consumer);
    return consumer;
  }

  /**
   * Gets the consumers served by the console.
   *
   * @return unmodifiable list of consumers
   */
  public List<OilConsumer> getConsumers() {
    return Collections.unmodifiableList(consumers);
  }

  // ============================================================================
  // Oil properties
  // ============================================================================

  /**
   * Calculates the oil density at a given temperature from the density at 15 C using a thermal expansion coefficient of
   * 7.0e-4 1/K.
   *
   * @param temperatureC temperature [C]
   * @return density [kg/m3]
   */
  public double getOilDensity(double temperatureC) {
    return oilGrade.getDensity15() / (1.0 + 7.0e-4 * (temperatureC - 15.0));
  }

  /**
   * Calculates the oil specific heat capacity from the standard petroleum liquid correlation cp = (1.685 + 0.0039 T) /
   * sqrt(SG).
   *
   * @param temperatureC temperature [C]
   * @return specific heat capacity [kJ/kg K]
   */
  public double getOilHeatCapacity(double temperatureC) {
    double specificGravity = oilGrade.getDensity15() / 999.0;
    return (1.685 + 0.0039 * temperatureC) / Math.sqrt(specificGravity);
  }

  /**
   * Calculates the kinematic viscosity at a given temperature with the ASTM D341 (Walther) relation fitted to the grade
   * viscosities at 40 C and 100 C.
   *
   * @param temperatureC temperature [C]
   * @return kinematic viscosity [cSt]
   */
  public double getOilKinematicViscosity(double temperatureC) {
    double z40 = Math.log10(Math.log10(oilGrade.getViscosity40() + 0.7));
    double z100 = Math.log10(Math.log10(oilGrade.getViscosity100() + 0.7));
    double logT40 = Math.log10(313.15);
    double logT100 = Math.log10(373.15);
    double slope = (z40 - z100) / (logT100 - logT40);
    double intercept = z40 + slope * logT40;
    double logT = Math.log10(temperatureC + 273.15);
    return Math.pow(10.0, Math.pow(10.0, intercept - slope * logT)) - 0.7;
  }

  /**
   * Calculates the dynamic viscosity at a given temperature.
   *
   * @param temperatureC temperature [C]
   * @return dynamic viscosity [Pa s]
   */
  public double getOilDynamicViscosity(double temperatureC) {
    return getOilKinematicViscosity(temperatureC) * 1.0e-6 * getOilDensity(temperatureC);
  }

  // ============================================================================
  // Run calculation
  // ============================================================================

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    oilDensityAtSupply = getOilDensity(supplyTemperature);
    oilHeatCapacityAtSupply = getOilHeatCapacity(supplyTemperature);
    oilViscosityAtSupply = getOilKinematicViscosity(supplyTemperature);

    calculateFlows();
    calculatePumps();
    calculateReservoir();
    calculateCoolers();
    calculateRundownTank();
    calculateAccumulator();
    calculateHeater();
    calculatePiping();
    buildComplianceReport();

    systemRunning = true;
    setCalculationIdentifier(id);

    logger.info("Lube oil console {} sized: {} m3/hr normal flow, {} m3 reservoir, {} kW cooler duty", getName(),
        normalOilFlow, reservoirTotalVolume, coolerDuty);
  }

  /**
   * Calculates the oil flow for each consumer and the total system flow.
   */
  private void calculateFlows() {
    totalHeatLoad = 0.0;
    lubeOilFlow = 0.0;
    controlOilFlow = 0.0;

    for (OilConsumer consumer : consumers) {
      double flowLitrePerMin;
      if (!Double.isNaN(consumer.getSpecifiedFlow())) {
        flowLitrePerMin = consumer.getSpecifiedFlow();
      } else {
        double rise = consumer.getTemperatureRise();
        if (rise <= 0.0) {
          flowLitrePerMin = 0.0;
        } else {
          double flowM3PerSec = consumer.getHeatLoad() / (oilDensityAtSupply * oilHeatCapacityAtSupply * rise);
          flowLitrePerMin = flowM3PerSec * 60000.0;
        }
      }
      consumer.setCalculatedFlow(flowLitrePerMin);
      totalHeatLoad += consumer.getHeatLoad();
      if (consumer.getType().isControlOil()) {
        controlOilFlow += flowLitrePerMin;
      } else {
        lubeOilFlow += flowLitrePerMin;
      }
    }

    normalOilFlow = (lubeOilFlow + controlOilFlow) * 0.06;
    ratedPumpFlow = normalOilFlow * pumpDesignMargin;
  }

  /**
   * Calculates the pump duty point, shaft power and motor rating.
   */
  private void calculatePumps() {
    double requiredHeaderPressure = lubeHeaderPressure;
    for (OilConsumer consumer : consumers) {
      if (consumer.getType().isControlOil()) {
        requiredHeaderPressure = Math.max(requiredHeaderPressure, controlOilHeaderPressure);
      }
      if (!Double.isNaN(consumer.getRequiredSupplyPressure())) {
        requiredHeaderPressure = Math.max(requiredHeaderPressure, consumer.getRequiredSupplyPressure());
      }
    }

    double staticHead = oilDensityAtSupply * 9.80665 * bearingElevation / 1.0e5;
    pumpDischargePressure = requiredHeaderPressure + coolerPressureDrop + filterPressureDropFouled + pipingPressureDrop
        + controlValvePressureDrop + staticHead;
    pumpDifferentialPressure = pumpDischargePressure;

    double flowM3PerSec = ratedPumpFlow / 3600.0;
    pumpHydraulicPower = flowM3PerSec * pumpDifferentialPressure * 1.0e5 / 1000.0;
    pumpShaftPower = pumpEfficiency > 0.0 ? pumpHydraulicPower / pumpEfficiency : Double.NaN;
    pumpMotorRating = selectStandardMotor(pumpShaftPower * 1.10);
  }

  /**
   * Selects the smallest standard motor rating that covers the required power.
   *
   * @param requiredPowerKW required motor power [kW]
   * @return selected standard motor rating [kW]
   */
  private double selectStandardMotor(double requiredPowerKW) {
    for (int i = 0; i < STANDARD_MOTOR_RATINGS.length; i++) {
      if (STANDARD_MOTOR_RATINGS[i] >= requiredPowerKW) {
        return STANDARD_MOTOR_RATINGS[i];
      }
    }
    return Math.ceil(requiredPowerKW / 50.0) * 50.0;
  }

  /**
   * Sizes the reservoir on the governing criterion of retention time and turnover rate.
   */
  private void calculateReservoir() {
    double retentionVolume = normalOilFlow / 60.0 * serviceCategory.getMinRetentionMinutes();
    double turnoverVolume = normalOilFlow / MAX_RESERVOIR_TURNOVERS_PER_HOUR;
    reservoirWorkingVolume = Math.max(retentionVolume, turnoverVolume);
    reservoirTotalVolume = reservoirWorkingVolume / (1.0 - reservoirFreeboardFraction);
    reservoirRetentionTime = normalOilFlow > 0.0 ? reservoirWorkingVolume / (normalOilFlow / 60.0) : Double.NaN;
    reservoirTurnoverRate = reservoirWorkingVolume > 0.0 ? normalOilFlow / reservoirWorkingVolume : Double.NaN;
  }

  /**
   * Calculates the cooler duty and the cooling water demand.
   */
  private void calculateCoolers() {
    double pumpHeat = Double.isNaN(pumpShaftPower) ? 0.0 : pumpShaftPower;
    coolerDuty = totalHeatLoad + pumpHeat;
    int dutyCoolers = Math.max(1, numberOfCoolers - 1);
    coolerDutyPerUnit = coolerDuty / dutyCoolers;

    double waterRise = coolingWaterOutletTemperature - coolingWaterInletTemperature;
    if (waterRise > 0.0) {
      double waterMassFlow = coolerDuty / (4.18 * waterRise);
      coolingWaterFlow = waterMassFlow * 3.6;
    } else {
      coolingWaterFlow = Double.NaN;
    }
  }

  /**
   * Sizes the overhead (rundown) tank for the machine coast-down.
   */
  private void calculateRundownTank() {
    if (serviceCategory.isRundownTankRequired()) {
      rundownTankVolume = lubeOilFlow / 1000.0 * rundownTime;
    } else {
      rundownTankVolume = 0.0;
    }
  }

  /**
   * Sizes the change-over accumulator using the adiabatic gas law for a bladder accumulator.
   */
  private void calculateAccumulator() {
    if (!accumulatorFitted || lubeOilFlow <= 0.0) {
      accumulatorVolume = 0.0;
      accumulatorPrechargePressure = 0.0;
      return;
    }

    double deliveredVolume = lubeOilFlow / 1000.0 / 60.0 * accumulatorTransferTime;
    double maxPressureAbs = lubeHeaderPressure + 1.01325;
    double minPressureAbs = Math.max(0.2, lubeHeaderPressure - accumulatorAllowablePressureDrop) + 1.01325;
    accumulatorPrechargePressure = 0.9 * minPressureAbs;

    double exponent = 1.0 / accumulatorGasExponent;
    double expansionFactor = Math.pow(accumulatorPrechargePressure / minPressureAbs, exponent)
        - Math.pow(accumulatorPrechargePressure / maxPressureAbs, exponent);
    accumulatorVolume = expansionFactor > 1.0e-9 ? deliveredVolume / expansionFactor : Double.NaN;
  }

  /**
   * Sizes the electric immersion heater and the minimum sheath area from the watt density limit.
   */
  private void calculateHeater() {
    double deltaT = requiredStartTemperature - minimumStartTemperature;
    if (deltaT <= 0.0 || heaterSoakTime <= 0.0) {
      heaterPower = 0.0;
      heaterMinimumArea = 0.0;
      return;
    }
    double meanTemperature = 0.5 * (minimumStartTemperature + requiredStartTemperature);
    double oilMass = reservoirWorkingVolume * getOilDensity(meanTemperature);
    heaterPower = oilMass * getOilHeatCapacity(meanTemperature) * deltaT / (heaterSoakTime * 3600.0);
    heaterMinimumArea = heaterPower * 1000.0 / MAX_HEATER_WATT_DENSITY / 10000.0;
  }

  /**
   * Sizes the oil supply pipe from the velocity limit and the gravity drain pipe from half full Manning flow at the
   * specified slope.
   */
  private void calculatePiping() {
    double flowM3PerSec = ratedPumpFlow / 3600.0;
    if (flowM3PerSec > 0.0 && maxSupplyVelocity > 0.0) {
      supplyPipeDiameter = Math.sqrt(4.0 * flowM3PerSec / (Math.PI * maxSupplyVelocity));
    } else {
      supplyPipeDiameter = 0.0;
    }

    double drainFlow = lubeOilFlow / 60000.0;
    if (drainFlow > 0.0 && drainSlope > 0.0) {
      double numerator = drainFlow * drainRoughness * 8.0 * Math.pow(4.0, 2.0 / 3.0);
      double denominator = Math.PI * Math.sqrt(drainSlope);
      drainPipeDiameter = Math.pow(numerator / denominator, 3.0 / 8.0);
    } else {
      drainPipeDiameter = 0.0;
    }
  }

  /**
   * Builds the API 614 / ISO 10438 compliance checklist for the calculated design.
   */
  private void buildComplianceReport() {
    complianceChecks.clear();
    String reference = serviceCategory.getReference();

    addCheck("Reservoir retention time", reference,
        String.format("Minimum %.1f min at normal flow", serviceCategory.getMinRetentionMinutes()),
        String.format("%.1f min", reservoirRetentionTime),
        reservoirRetentionTime >= serviceCategory.getMinRetentionMinutes() - 1.0e-6);

    addCheck("Reservoir turnover rate", reference,
        String.format("Maximum %.0f turnovers per hour", MAX_RESERVOIR_TURNOVERS_PER_HOUR),
        String.format("%.1f 1/hr", reservoirTurnoverRate),
        reservoirTurnoverRate <= MAX_RESERVOIR_TURNOVERS_PER_HOUR + 1.0e-6);

    addCheck("Bearing oil supply temperature", reference,
        String.format("Maximum %.0f C", MAX_BEARING_SUPPLY_TEMPERATURE), String.format("%.1f C", supplyTemperature),
        supplyTemperature <= MAX_BEARING_SUPPLY_TEMPERATURE);

    addCheck("Main oil pumps", reference, "2 x 100 % pumps with independent drivers", numberOfPumps + " x 100 %",
        numberOfPumps >= 2);

    addCheck("Pump rated capacity", reference,
        String.format("Minimum %.0f %% of maximum system demand", MIN_PUMP_RATED_MARGIN * 100.0),
        String.format("%.0f %%", pumpDesignMargin * 100.0), pumpDesignMargin >= MIN_PUMP_RATED_MARGIN - 1.0e-6);

    addCheck("Oil coolers", reference, "Twin 2 x 100 % coolers with transfer valve", numberOfCoolers + " x 100 %",
        numberOfCoolers >= 2);

    addCheck("Oil filters", reference, "Twin 2 x 100 % filters with transfer valve", numberOfFilters + " x 100 %",
        numberOfFilters >= 2);

    addCheck("Filter rating", reference, String.format("Maximum %.0f micron nominal", MAX_FILTER_RATING_MICRON),
        String.format("%.0f micron", filterRatingMicron), filterRatingMicron <= MAX_FILTER_RATING_MICRON + 1.0e-6);

    addCheck("Clean filter pressure drop", reference,
        String.format("Maximum %.2f bar at rated flow and 38 C", MAX_CLEAN_FILTER_PRESSURE_DROP),
        String.format("%.2f bar", filterPressureDropClean),
        filterPressureDropClean <= MAX_CLEAN_FILTER_PRESSURE_DROP + 1.0e-6);

    if (serviceCategory.isRundownTankRequired()) {
      addCheck("Overhead (rundown) tank", reference,
          String.format("Minimum %.0f min of bearing flow", MIN_RUNDOWN_TIME),
          String.format("%.1f min (%.2f m3)", rundownTime, rundownTankVolume),
          rundownTime >= MIN_RUNDOWN_TIME - 1.0e-6 && rundownTankVolume > 0.0);
    }

    addCheck("Heater watt density", reference, String.format("Maximum %.1f W/cm2", MAX_HEATER_WATT_DENSITY),
        String.format("%.2f m2 minimum sheath area at %.1f kW", heaterMinimumArea, heaterPower),
        heaterMinimumArea >= 0.0);

    addCheck("Oil supply velocity", reference, String.format("Maximum %.1f m/s in pressure piping", maxSupplyVelocity),
        String.format("%.0f mm supply pipe inside diameter", supplyPipeDiameter * 1000.0), supplyPipeDiameter > 0.0);

    addCheck("Gravity drain lines", reference,
        String.format("Half full, slope minimum %.1f mm/m", MIN_DRAIN_SLOPE * 1000.0),
        String.format("%.0f mm drain at %.1f mm/m", drainPipeDiameter * 1000.0, drainSlope * 1000.0),
        drainSlope >= MIN_DRAIN_SLOPE - 1.0e-9 && drainPipeDiameter > 0.0);
  }

  /**
   * Adds a single entry to the compliance checklist.
   *
   * @param item item description
   * @param reference standard reference
   * @param requirement requirement text
   * @param actual actual design value
   * @param compliant true when the requirement is met
   */
  private void addCheck(String item, String reference, String requirement, String actual, boolean compliant) {
    complianceChecks.add(new ComplianceCheck(item, reference, requirement, actual, compliant));
  }

  // ============================================================================
  // Results
  // ============================================================================

  /**
   * Gets the total dissipated heat load of all consumers.
   *
   * @return heat load [kW]
   */
  public double getTotalHeatLoad() {
    return totalHeatLoad;
  }

  /**
   * Gets the total lube oil flow to bearings, gears and seals.
   *
   * @return lube oil flow [L/min]
   */
  public double getLubeOilFlow() {
    return lubeOilFlow;
  }

  /**
   * Gets the total control oil flow.
   *
   * @return control oil flow [L/min]
   */
  public double getControlOilFlow() {
    return controlOilFlow;
  }

  /**
   * Gets the normal total system flow.
   *
   * @return normal flow [m3/hr]
   */
  public double getNormalOilFlow() {
    return normalOilFlow;
  }

  /**
   * Gets the rated pump capacity including the design margin.
   *
   * @return rated pump flow [m3/hr]
   */
  public double getRatedPumpFlow() {
    return ratedPumpFlow;
  }

  /**
   * Gets the required pump discharge pressure.
   *
   * @return discharge pressure [barg]
   */
  public double getPumpDischargePressure() {
    return pumpDischargePressure;
  }

  /**
   * Gets the pump differential pressure.
   *
   * @return differential pressure [bar]
   */
  public double getPumpDifferentialPressure() {
    return pumpDifferentialPressure;
  }

  /**
   * Gets the pump hydraulic power.
   *
   * @return hydraulic power [kW]
   */
  public double getPumpHydraulicPower() {
    return pumpHydraulicPower;
  }

  /**
   * Gets the pump shaft power.
   *
   * @return shaft power [kW]
   */
  public double getPumpShaftPower() {
    return pumpShaftPower;
  }

  /**
   * Gets the selected standard motor rating for each main pump.
   *
   * @return motor rating [kW]
   */
  public double getPumpMotorRating() {
    return pumpMotorRating;
  }

  /**
   * Gets the reservoir working capacity.
   *
   * @return working volume [m3]
   */
  public double getReservoirWorkingVolume() {
    return reservoirWorkingVolume;
  }

  /**
   * Gets the total reservoir volume including free board and vapour space.
   *
   * @return total volume [m3]
   */
  public double getReservoirTotalVolume() {
    return reservoirTotalVolume;
  }

  /**
   * Gets the reservoir retention time at normal flow.
   *
   * @return retention time [min]
   */
  public double getReservoirRetentionTime() {
    return reservoirRetentionTime;
  }

  /**
   * Gets the reservoir turnover rate at normal flow.
   *
   * @return turnover rate [1/hr]
   */
  public double getReservoirTurnoverRate() {
    return reservoirTurnoverRate;
  }

  /**
   * Gets the total cooler duty including the pump work dissipated into the oil.
   *
   * @return cooler duty [kW]
   */
  public double getCoolerDuty() {
    return coolerDuty;
  }

  /**
   * Gets the duty of each installed duty cooler.
   *
   * @return duty per cooler [kW]
   */
  public double getCoolerDutyPerUnit() {
    return coolerDutyPerUnit;
  }

  /**
   * Gets the cooling water demand.
   *
   * @return cooling water flow [m3/hr]
   */
  public double getCoolingWaterFlow() {
    return coolingWaterFlow;
  }

  /**
   * Gets the overhead (rundown) tank volume.
   *
   * @return rundown tank volume [m3]
   */
  public double getRundownTankVolume() {
    return rundownTankVolume;
  }

  /**
   * Gets the total accumulator volume covering the pump change-over.
   *
   * @return accumulator volume [m3]
   */
  public double getAccumulatorVolume() {
    return accumulatorVolume;
  }

  /**
   * Gets the accumulator gas precharge pressure.
   *
   * @return precharge pressure [bara]
   */
  public double getAccumulatorPrechargePressure() {
    return accumulatorPrechargePressure;
  }

  /**
   * Gets the required immersion heater power.
   *
   * @return heater power [kW]
   */
  public double getHeaterPower() {
    return heaterPower;
  }

  /**
   * Gets the minimum heater sheath area set by the watt density limit.
   *
   * @return minimum sheath area [m2]
   */
  public double getHeaterMinimumArea() {
    return heaterMinimumArea;
  }

  /**
   * Gets the required oil supply pipe inside diameter.
   *
   * @return inside diameter [m]
   */
  public double getSupplyPipeDiameter() {
    return supplyPipeDiameter;
  }

  /**
   * Gets the required gravity drain pipe inside diameter for half full flow.
   *
   * @return inside diameter [m]
   */
  public double getDrainPipeDiameter() {
    return drainPipeDiameter;
  }

  /**
   * Gets the oil kinematic viscosity at the bearing supply temperature.
   *
   * @return kinematic viscosity [cSt]
   */
  public double getOilViscosityAtSupply() {
    return oilViscosityAtSupply;
  }

  /**
   * Gets the oil density at the bearing supply temperature.
   *
   * @return density [kg/m3]
   */
  public double getOilDensityAtSupply() {
    return oilDensityAtSupply;
  }

  /**
   * Gets the oil specific heat capacity at the bearing supply temperature.
   *
   * @return specific heat capacity [kJ/kg K]
   */
  public double getOilHeatCapacityAtSupply() {
    return oilHeatCapacityAtSupply;
  }

  /**
   * Checks whether the console has been sized.
   *
   * @return true when the last run completed
   */
  public boolean isSystemRunning() {
    return systemRunning;
  }

  /**
   * Gets the compliance checklist from the last run.
   *
   * @return unmodifiable list of compliance checks
   */
  public List<ComplianceCheck> getComplianceChecks() {
    return Collections.unmodifiableList(complianceChecks);
  }

  /**
   * Checks whether all compliance items are met.
   *
   * @return true when every compliance check passes
   */
  public boolean isApiCompliant() {
    if (complianceChecks.isEmpty()) {
      return false;
    }
    for (ComplianceCheck check : complianceChecks) {
      if (!check.isCompliant()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Gets the compliance items that are not met.
   *
   * @return list of failing compliance checks
   */
  public List<ComplianceCheck> getDeviations() {
    List<ComplianceCheck> deviations = new ArrayList<ComplianceCheck>();
    for (ComplianceCheck check : complianceChecks) {
      if (!check.isCompliant()) {
        deviations.add(check);
      }
    }
    return deviations;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    Map<String, Object> results = new LinkedHashMap<String, Object>();
    results.put("name", getName());
    results.put("standard", "API 614 / ISO 10438");
    results.put("serviceCategory", serviceCategory.name());
    results.put("standardReference", serviceCategory.getReference());
    results.put("oilGrade", oilGrade.name());

    Map<String, Object> oil = new LinkedHashMap<String, Object>();
    oil.put("supplyTemperatureC", supplyTemperature);
    oil.put("reservoirTemperatureC", reservoirTemperature);
    oil.put("densityAtSupplyKgM3", oilDensityAtSupply);
    oil.put("heatCapacityAtSupplyKJkgK", oilHeatCapacityAtSupply);
    oil.put("kinematicViscosityAtSupplyCSt", oilViscosityAtSupply);
    results.put("oilProperties", oil);

    Map<String, Object> load = new LinkedHashMap<String, Object>();
    load.put("totalHeatLoadKW", totalHeatLoad);
    load.put("lubeOilFlowLpm", lubeOilFlow);
    load.put("controlOilFlowLpm", controlOilFlow);
    load.put("normalFlowM3h", normalOilFlow);
    load.put("ratedPumpFlowM3h", ratedPumpFlow);
    results.put("flowSummary", load);

    Map<String, Object> pumps = new LinkedHashMap<String, Object>();
    pumps.put("numberOfPumps", numberOfPumps);
    pumps.put("dischargePressureBarg", pumpDischargePressure);
    pumps.put("differentialPressureBar", pumpDifferentialPressure);
    pumps.put("hydraulicPowerKW", pumpHydraulicPower);
    pumps.put("shaftPowerKW", pumpShaftPower);
    pumps.put("motorRatingKW", pumpMotorRating);
    results.put("pumps", pumps);

    Map<String, Object> reservoir = new LinkedHashMap<String, Object>();
    reservoir.put("workingVolumeM3", reservoirWorkingVolume);
    reservoir.put("totalVolumeM3", reservoirTotalVolume);
    reservoir.put("retentionTimeMin", reservoirRetentionTime);
    reservoir.put("turnoverRatePerHour", reservoirTurnoverRate);
    results.put("reservoir", reservoir);

    Map<String, Object> coolers = new LinkedHashMap<String, Object>();
    coolers.put("numberOfCoolers", numberOfCoolers);
    coolers.put("totalDutyKW", coolerDuty);
    coolers.put("dutyPerCoolerKW", coolerDutyPerUnit);
    coolers.put("coolingWaterFlowM3h", coolingWaterFlow);
    results.put("coolers", coolers);

    Map<String, Object> filters = new LinkedHashMap<String, Object>();
    filters.put("numberOfFilters", numberOfFilters);
    filters.put("ratingMicron", filterRatingMicron);
    filters.put("cleanPressureDropBar", filterPressureDropClean);
    filters.put("fouledPressureDropBar", filterPressureDropFouled);
    results.put("filters", filters);

    Map<String, Object> transient1 = new LinkedHashMap<String, Object>();
    transient1.put("rundownTankVolumeM3", rundownTankVolume);
    transient1.put("rundownTimeMin", rundownTime);
    transient1.put("accumulatorVolumeM3", accumulatorVolume);
    transient1.put("accumulatorPrechargeBara", accumulatorPrechargePressure);
    transient1.put("accumulatorTransferTimeS", accumulatorTransferTime);
    results.put("transientProtection", transient1);

    Map<String, Object> heater = new LinkedHashMap<String, Object>();
    heater.put("powerKW", heaterPower);
    heater.put("minimumSheathAreaM2", heaterMinimumArea);
    heater.put("maxWattDensityWcm2", MAX_HEATER_WATT_DENSITY);
    results.put("heater", heater);

    Map<String, Object> piping = new LinkedHashMap<String, Object>();
    piping.put("supplyPipeIdMm", supplyPipeDiameter * 1000.0);
    piping.put("maxSupplyVelocityMs", maxSupplyVelocity);
    piping.put("drainPipeIdMm", drainPipeDiameter * 1000.0);
    piping.put("drainSlopeMmPerM", drainSlope * 1000.0);
    results.put("piping", piping);

    List<Map<String, Object>> consumerList = new ArrayList<Map<String, Object>>();
    for (OilConsumer consumer : consumers) {
      Map<String, Object> entry = new LinkedHashMap<String, Object>();
      entry.put("name", consumer.getName());
      entry.put("type", consumer.getType().name());
      entry.put("heatLoadKW", consumer.getHeatLoad());
      entry.put("temperatureRiseK", consumer.getTemperatureRise());
      entry.put("flowLpm", consumer.getCalculatedFlow());
      consumerList.add(entry);
    }
    results.put("consumers", consumerList);

    List<Map<String, Object>> checkList = new ArrayList<Map<String, Object>>();
    for (ComplianceCheck check : complianceChecks) {
      Map<String, Object> entry = new LinkedHashMap<String, Object>();
      entry.put("item", check.getItem());
      entry.put("reference", check.getReference());
      entry.put("requirement", check.getRequirement());
      entry.put("actual", check.getActual());
      entry.put("compliant", check.isCompliant());
      checkList.add(entry);
    }
    results.put("complianceChecks", checkList);
    results.put("apiCompliant", isApiCompliant());

    return new GsonBuilder().setPrettyPrinting().create().toJson(results);
  }

  /** {@inheritDoc} */
  @Override
  public ValidationResult validateSetup() {
    ValidationResult result = new ValidationResult(getName());
    if (consumers.isEmpty()) {
      result.addError("configuration", "No oil consumers defined",
          "Call addConsumer(name, type, heatLoadKW) or addConsumerFromShaftPower(...)");
    }
    if (supplyTemperature > MAX_BEARING_SUPPLY_TEMPERATURE) {
      result.addWarning("design",
          "Oil supply temperature exceeds the API 614 limit of " + MAX_BEARING_SUPPLY_TEMPERATURE + " C",
          "Call setSupplyTemperature(45.0) or increase the cooler duty");
    }
    if (numberOfPumps < 2) {
      result.addWarning("design", "Only one main oil pump is configured",
          "API 614 requires a full capacity installed spare, call setNumberOfPumps(2)");
    }
    if (pumpEfficiency <= 0.0 || pumpEfficiency > 1.0) {
      result.addError("design", "Pump efficiency must be in the interval (0, 1]", "Call setPumpEfficiency(0.65)");
    }
    if (coolingWaterOutletTemperature <= coolingWaterInletTemperature) {
      result.addError("design", "Cooling water outlet temperature must exceed the inlet",
          "Call setCoolingWaterOutletTemperature(32.0)");
    }
    return result;
  }

  // ============================================================================
  // Getters and setters for design input
  // ============================================================================

  /**
   * Gets the machinery service category.
   *
   * @return service category
   */
  public ServiceCategory getServiceCategory() {
    return serviceCategory;
  }

  /**
   * Sets the machinery service category.
   *
   * @param serviceCategory service category
   */
  public void setServiceCategory(ServiceCategory serviceCategory) {
    this.serviceCategory = serviceCategory;
    this.accumulatorFitted = serviceCategory.isRundownTankRequired();
  }

  /**
   * Gets the oil viscosity grade.
   *
   * @return oil grade
   */
  public OilGrade getOilGrade() {
    return oilGrade;
  }

  /**
   * Sets the oil viscosity grade.
   *
   * @param oilGrade oil grade
   */
  public void setOilGrade(OilGrade oilGrade) {
    this.oilGrade = oilGrade;
  }

  /**
   * Gets the oil supply temperature to the bearings.
   *
   * @return supply temperature [C]
   */
  public double getSupplyTemperature() {
    return supplyTemperature;
  }

  /**
   * Sets the oil supply temperature to the bearings.
   *
   * @param supplyTemperature supply temperature [C]
   */
  public void setSupplyTemperature(double supplyTemperature) {
    this.supplyTemperature = supplyTemperature;
  }

  /**
   * Gets the reservoir bulk oil temperature.
   *
   * @return reservoir temperature [C]
   */
  public double getReservoirTemperature() {
    return reservoirTemperature;
  }

  /**
   * Sets the reservoir bulk oil temperature.
   *
   * @param reservoirTemperature reservoir temperature [C]
   */
  public void setReservoirTemperature(double reservoirTemperature) {
    this.reservoirTemperature = reservoirTemperature;
  }

  /**
   * Gets the minimum oil temperature used for heater sizing.
   *
   * @return minimum start temperature [C]
   */
  public double getMinimumStartTemperature() {
    return minimumStartTemperature;
  }

  /**
   * Sets the minimum oil temperature used for heater sizing.
   *
   * @param minimumStartTemperature minimum start temperature [C]
   */
  public void setMinimumStartTemperature(double minimumStartTemperature) {
    this.minimumStartTemperature = minimumStartTemperature;
  }

  /**
   * Gets the oil temperature required before start-up.
   *
   * @return required start temperature [C]
   */
  public double getRequiredStartTemperature() {
    return requiredStartTemperature;
  }

  /**
   * Sets the oil temperature required before start-up.
   *
   * @param requiredStartTemperature required start temperature [C]
   */
  public void setRequiredStartTemperature(double requiredStartTemperature) {
    this.requiredStartTemperature = requiredStartTemperature;
  }

  /**
   * Gets the lube oil header pressure.
   *
   * @return header pressure [barg]
   */
  public double getLubeHeaderPressure() {
    return lubeHeaderPressure;
  }

  /**
   * Sets the lube oil header pressure.
   *
   * @param lubeHeaderPressure header pressure [barg]
   */
  public void setLubeHeaderPressure(double lubeHeaderPressure) {
    this.lubeHeaderPressure = lubeHeaderPressure;
  }

  /**
   * Gets the control oil header pressure.
   *
   * @return control oil pressure [barg]
   */
  public double getControlOilHeaderPressure() {
    return controlOilHeaderPressure;
  }

  /**
   * Sets the control oil header pressure.
   *
   * @param controlOilHeaderPressure control oil pressure [barg]
   */
  public void setControlOilHeaderPressure(double controlOilHeaderPressure) {
    this.controlOilHeaderPressure = controlOilHeaderPressure;
  }

  /**
   * Gets the static elevation from the reservoir to the highest bearing.
   *
   * @return elevation [m]
   */
  public double getBearingElevation() {
    return bearingElevation;
  }

  /**
   * Sets the static elevation from the reservoir to the highest bearing.
   *
   * @param bearingElevation elevation [m]
   */
  public void setBearingElevation(double bearingElevation) {
    this.bearingElevation = bearingElevation;
  }

  /**
   * Gets the cooler pressure drop.
   *
   * @return cooler pressure drop [bar]
   */
  public double getCoolerPressureDrop() {
    return coolerPressureDrop;
  }

  /**
   * Sets the cooler pressure drop.
   *
   * @param coolerPressureDrop cooler pressure drop [bar]
   */
  public void setCoolerPressureDrop(double coolerPressureDrop) {
    this.coolerPressureDrop = coolerPressureDrop;
  }

  /**
   * Gets the clean filter pressure drop.
   *
   * @return clean pressure drop [bar]
   */
  public double getFilterPressureDropClean() {
    return filterPressureDropClean;
  }

  /**
   * Sets the clean filter pressure drop.
   *
   * @param filterPressureDropClean clean pressure drop [bar]
   */
  public void setFilterPressureDropClean(double filterPressureDropClean) {
    this.filterPressureDropClean = filterPressureDropClean;
  }

  /**
   * Gets the fouled filter pressure drop used for pump sizing.
   *
   * @return fouled pressure drop [bar]
   */
  public double getFilterPressureDropFouled() {
    return filterPressureDropFouled;
  }

  /**
   * Sets the fouled filter pressure drop used for pump sizing.
   *
   * @param filterPressureDropFouled fouled pressure drop [bar]
   */
  public void setFilterPressureDropFouled(double filterPressureDropFouled) {
    this.filterPressureDropFouled = filterPressureDropFouled;
  }

  /**
   * Gets the piping pressure loss allowance.
   *
   * @return piping pressure drop [bar]
   */
  public double getPipingPressureDrop() {
    return pipingPressureDrop;
  }

  /**
   * Sets the piping pressure loss allowance.
   *
   * @param pipingPressureDrop piping pressure drop [bar]
   */
  public void setPipingPressureDrop(double pipingPressureDrop) {
    this.pipingPressureDrop = pipingPressureDrop;
  }

  /**
   * Gets the header pressure control valve pressure drop.
   *
   * @return control valve pressure drop [bar]
   */
  public double getControlValvePressureDrop() {
    return controlValvePressureDrop;
  }

  /**
   * Sets the header pressure control valve pressure drop.
   *
   * @param controlValvePressureDrop control valve pressure drop [bar]
   */
  public void setControlValvePressureDrop(double controlValvePressureDrop) {
    this.controlValvePressureDrop = controlValvePressureDrop;
  }

  /**
   * Gets the filter rating.
   *
   * @return filter rating [micron nominal]
   */
  public double getFilterRatingMicron() {
    return filterRatingMicron;
  }

  /**
   * Sets the filter rating.
   *
   * @param filterRatingMicron filter rating [micron nominal]
   */
  public void setFilterRatingMicron(double filterRatingMicron) {
    this.filterRatingMicron = filterRatingMicron;
  }

  /**
   * Gets the number of installed main pumps.
   *
   * @return number of pumps
   */
  public int getNumberOfPumps() {
    return numberOfPumps;
  }

  /**
   * Sets the number of installed main pumps.
   *
   * @param numberOfPumps number of pumps
   */
  public void setNumberOfPumps(int numberOfPumps) {
    this.numberOfPumps = numberOfPumps;
  }

  /**
   * Gets the number of installed coolers.
   *
   * @return number of coolers
   */
  public int getNumberOfCoolers() {
    return numberOfCoolers;
  }

  /**
   * Sets the number of installed coolers.
   *
   * @param numberOfCoolers number of coolers
   */
  public void setNumberOfCoolers(int numberOfCoolers) {
    this.numberOfCoolers = numberOfCoolers;
  }

  /**
   * Gets the number of installed filters.
   *
   * @return number of filters
   */
  public int getNumberOfFilters() {
    return numberOfFilters;
  }

  /**
   * Sets the number of installed filters.
   *
   * @param numberOfFilters number of filters
   */
  public void setNumberOfFilters(int numberOfFilters) {
    this.numberOfFilters = numberOfFilters;
  }

  /**
   * Gets the pump design margin.
   *
   * @return design margin [-]
   */
  public double getPumpDesignMargin() {
    return pumpDesignMargin;
  }

  /**
   * Sets the pump design margin.
   *
   * @param pumpDesignMargin design margin [-]
   */
  public void setPumpDesignMargin(double pumpDesignMargin) {
    this.pumpDesignMargin = pumpDesignMargin;
  }

  /**
   * Gets the pump hydraulic efficiency.
   *
   * @return efficiency [-]
   */
  public double getPumpEfficiency() {
    return pumpEfficiency;
  }

  /**
   * Sets the pump hydraulic efficiency.
   *
   * @param pumpEfficiency efficiency [-]
   */
  public void setPumpEfficiency(double pumpEfficiency) {
    this.pumpEfficiency = pumpEfficiency;
  }

  /**
   * Gets the cooling water inlet temperature.
   *
   * @return inlet temperature [C]
   */
  public double getCoolingWaterInletTemperature() {
    return coolingWaterInletTemperature;
  }

  /**
   * Sets the cooling water inlet temperature.
   *
   * @param coolingWaterInletTemperature inlet temperature [C]
   */
  public void setCoolingWaterInletTemperature(double coolingWaterInletTemperature) {
    this.coolingWaterInletTemperature = coolingWaterInletTemperature;
  }

  /**
   * Gets the cooling water outlet temperature.
   *
   * @return outlet temperature [C]
   */
  public double getCoolingWaterOutletTemperature() {
    return coolingWaterOutletTemperature;
  }

  /**
   * Sets the cooling water outlet temperature.
   *
   * @param coolingWaterOutletTemperature outlet temperature [C]
   */
  public void setCoolingWaterOutletTemperature(double coolingWaterOutletTemperature) {
    this.coolingWaterOutletTemperature = coolingWaterOutletTemperature;
  }

  /**
   * Gets the reservoir free board fraction.
   *
   * @return free board fraction [-]
   */
  public double getReservoirFreeboardFraction() {
    return reservoirFreeboardFraction;
  }

  /**
   * Sets the reservoir free board fraction.
   *
   * @param reservoirFreeboardFraction free board fraction [-]
   */
  public void setReservoirFreeboardFraction(double reservoirFreeboardFraction) {
    this.reservoirFreeboardFraction = reservoirFreeboardFraction;
  }

  /**
   * Gets the overhead tank holdup time.
   *
   * @return rundown time [min]
   */
  public double getRundownTime() {
    return rundownTime;
  }

  /**
   * Sets the overhead tank holdup time.
   *
   * @param rundownTime rundown time [min]
   */
  public void setRundownTime(double rundownTime) {
    this.rundownTime = rundownTime;
  }

  /**
   * Gets the pump change-over transient time covered by the accumulator.
   *
   * @return transfer time [s]
   */
  public double getAccumulatorTransferTime() {
    return accumulatorTransferTime;
  }

  /**
   * Sets the pump change-over transient time covered by the accumulator.
   *
   * @param accumulatorTransferTime transfer time [s]
   */
  public void setAccumulatorTransferTime(double accumulatorTransferTime) {
    this.accumulatorTransferTime = accumulatorTransferTime;
  }

  /**
   * Gets the allowable header pressure decay during the pump change-over.
   *
   * @return allowable pressure drop [bar]
   */
  public double getAccumulatorAllowablePressureDrop() {
    return accumulatorAllowablePressureDrop;
  }

  /**
   * Sets the allowable header pressure decay during the pump change-over.
   *
   * @param accumulatorAllowablePressureDrop allowable pressure drop [bar]
   */
  public void setAccumulatorAllowablePressureDrop(double accumulatorAllowablePressureDrop) {
    this.accumulatorAllowablePressureDrop = accumulatorAllowablePressureDrop;
  }

  /**
   * Checks whether a change-over accumulator is fitted.
   *
   * @return true when an accumulator is fitted
   */
  public boolean isAccumulatorFitted() {
    return accumulatorFitted;
  }

  /**
   * Sets whether a change-over accumulator is fitted.
   *
   * @param accumulatorFitted true when an accumulator is fitted
   */
  public void setAccumulatorFitted(boolean accumulatorFitted) {
    this.accumulatorFitted = accumulatorFitted;
  }

  /**
   * Gets the heater soak time.
   *
   * @return soak time [hr]
   */
  public double getHeaterSoakTime() {
    return heaterSoakTime;
  }

  /**
   * Sets the heater soak time.
   *
   * @param heaterSoakTime soak time [hr]
   */
  public void setHeaterSoakTime(double heaterSoakTime) {
    this.heaterSoakTime = heaterSoakTime;
  }

  /**
   * Gets the maximum oil velocity in pressure piping.
   *
   * @return maximum velocity [m/s]
   */
  public double getMaxSupplyVelocity() {
    return maxSupplyVelocity;
  }

  /**
   * Sets the maximum oil velocity in pressure piping.
   *
   * @param maxSupplyVelocity maximum velocity [m/s]
   */
  public void setMaxSupplyVelocity(double maxSupplyVelocity) {
    this.maxSupplyVelocity = maxSupplyVelocity;
  }

  /**
   * Gets the gravity drain line slope.
   *
   * @return slope [m/m]
   */
  public double getDrainSlope() {
    return drainSlope;
  }

  /**
   * Sets the gravity drain line slope.
   *
   * @param drainSlope slope [m/m]
   */
  public void setDrainSlope(double drainSlope) {
    this.drainSlope = drainSlope;
  }

  // ============================================================================
  // Inner classes
  // ============================================================================

  /**
   * A bearing, gear, seal or control oil consumer served by the console.
   */
  public static class OilConsumer implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String name;
    private final ConsumerType type;
    private double heatLoad = 0.0;
    private double shaftPower = Double.NaN;
    private double temperatureRise;
    private double specifiedFlow = Double.NaN;
    private double requiredSupplyPressure = Double.NaN;
    private double calculatedFlow = 0.0;

    /**
     * Creates an oil consumer.
     *
     * @param name consumer name
     * @param type consumer type
     */
    public OilConsumer(String name, ConsumerType type) {
      this.name = name;
      this.type = type;
      this.temperatureRise = type.getTemperatureRise();
    }

    /**
     * Gets the consumer name.
     *
     * @return consumer name
     */
    public String getName() {
      return name;
    }

    /**
     * Gets the consumer type.
     *
     * @return consumer type
     */
    public ConsumerType getType() {
      return type;
    }

    /**
     * Gets the dissipated heat load.
     *
     * @return heat load [kW]
     */
    public double getHeatLoad() {
      return heatLoad;
    }

    /**
     * Sets the dissipated heat load.
     *
     * @param heatLoad heat load [kW]
     */
    public void setHeatLoad(double heatLoad) {
      this.heatLoad = heatLoad;
    }

    /**
     * Gets the transmitted shaft power used to estimate the heat load.
     *
     * @return shaft power [kW], or NaN when not set
     */
    public double getShaftPower() {
      return shaftPower;
    }

    /**
     * Sets the transmitted shaft power used to estimate the heat load.
     *
     * @param shaftPower shaft power [kW]
     */
    public void setShaftPower(double shaftPower) {
      this.shaftPower = shaftPower;
    }

    /**
     * Gets the allowable oil temperature rise across the consumer.
     *
     * @return temperature rise [K]
     */
    public double getTemperatureRise() {
      return temperatureRise;
    }

    /**
     * Sets the allowable oil temperature rise across the consumer.
     *
     * @param temperatureRise temperature rise [K]
     */
    public void setTemperatureRise(double temperatureRise) {
      this.temperatureRise = temperatureRise;
    }

    /**
     * Gets the specified oil flow that overrides the heat balance flow.
     *
     * @return specified flow [L/min], or NaN when not set
     */
    public double getSpecifiedFlow() {
      return specifiedFlow;
    }

    /**
     * Sets a specified oil flow that overrides the heat balance flow.
     *
     * @param specifiedFlow specified flow [L/min]
     */
    public void setSpecifiedFlow(double specifiedFlow) {
      this.specifiedFlow = specifiedFlow;
    }

    /**
     * Gets the required supply pressure.
     *
     * @return supply pressure [barg], or NaN when not set
     */
    public double getRequiredSupplyPressure() {
      return requiredSupplyPressure;
    }

    /**
     * Sets the required supply pressure.
     *
     * @param requiredSupplyPressure supply pressure [barg]
     */
    public void setRequiredSupplyPressure(double requiredSupplyPressure) {
      this.requiredSupplyPressure = requiredSupplyPressure;
    }

    /**
     * Gets the oil flow calculated by the console.
     *
     * @return calculated flow [L/min]
     */
    public double getCalculatedFlow() {
      return calculatedFlow;
    }

    /**
     * Sets the oil flow calculated by the console.
     *
     * @param calculatedFlow calculated flow [L/min]
     */
    public void setCalculatedFlow(double calculatedFlow) {
      this.calculatedFlow = calculatedFlow;
    }
  }

  /**
   * A single entry in the API 614 / ISO 10438 compliance checklist.
   */
  public static class ComplianceCheck implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String item;
    private final String reference;
    private final String requirement;
    private final String actual;
    private final boolean compliant;

    /**
     * Creates a compliance check entry.
     *
     * @param item item description
     * @param reference standard reference
     * @param requirement requirement text
     * @param actual actual design value
     * @param compliant true when the requirement is met
     */
    public ComplianceCheck(String item, String reference, String requirement, String actual, boolean compliant) {
      this.item = item;
      this.reference = reference;
      this.requirement = requirement;
      this.actual = actual;
      this.compliant = compliant;
    }

    /**
     * Gets the item description.
     *
     * @return item description
     */
    public String getItem() {
      return item;
    }

    /**
     * Gets the standard reference.
     *
     * @return standard reference
     */
    public String getReference() {
      return reference;
    }

    /**
     * Gets the requirement text.
     *
     * @return requirement text
     */
    public String getRequirement() {
      return requirement;
    }

    /**
     * Gets the actual design value.
     *
     * @return actual design value
     */
    public String getActual() {
      return actual;
    }

    /**
     * Checks whether the requirement is met.
     *
     * @return true when the requirement is met
     */
    public boolean isCompliant() {
      return compliant;
    }
  }
}
