package neqsim.process.equipment.subsea;

import java.util.UUID;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.subsea.SubseaBoosterMechanicalDesign;
import neqsim.thermo.system.SystemInterface;

/**
 * Subsea Booster equipment class.
 *
 * <p>
 * A subsea booster is a subsea-installed pump or compressor used to enhance production from subsea
 * wells. Key applications include:
 * </p>
 * <ul>
 * <li>Subsea boosting (multiphase pumping)</li>
 * <li>Subsea compression</li>
 * <li>Subsea separation and boosting</li>
 * <li>Long-distance tieback enabling</li>
 * </ul>
 *
 * <h2>Booster Types</h2>
 * <ul>
 * <li><b>Multiphase Pump</b>: Helico-axial or twin-screw for multiphase flow</li>
 * <li><b>Single-Phase Pump</b>: ESP or centrifugal for separated liquid</li>
 * <li><b>Wet Gas Compressor</b>: Centrifugal compressor handling some liquid</li>
 * <li><b>Dry Gas Compressor</b>: After subsea separation</li>
 * </ul>
 *
 * <h2>Design Standards</h2>
 * <ul>
 * <li>API RP 17Q - Subsea Equipment Qualification</li>
 * <li>API RP 17V - Subsea Boosting Systems</li>
 * <li>DNV-ST-E101 - Drilling Plants</li>
 * <li>ISO 13628-6 - Subsea Production Control Systems</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * // Create multiphase pump booster
 * SubseaBooster booster = new SubseaBooster("Subsea Booster", wellStream);
 * booster.setBoosterType(SubseaBooster.BoosterType.MULTIPHASE_PUMP);
 * booster.setPumpType(SubseaBooster.PumpType.HELICO_AXIAL);
 * booster.setWaterDepth(450.0);
 * booster.setOutletPressure(150.0); // Target outlet pressure in bara
 * booster.setPowerRatingMW(5.0);
 *
 * booster.run();
 *
 * // Get mechanical design
 * SubseaBoosterMechanicalDesign design = booster.getMechanicalDesign();
 * design.calcDesign();
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 * @see SubseaBoosterMechanicalDesign
 */
public class SubseaBooster extends TwoPortEquipment {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /**
   * Booster type.
   */
  public enum BoosterType {
    /** Multiphase pump handling gas-liquid mixture. */
    MULTIPHASE_PUMP("Multiphase Pump"),
    /** Single-phase liquid pump. */
    LIQUID_PUMP("Liquid Pump"),
    /** Wet gas compressor (with liquid tolerance). */
    WET_GAS_COMPRESSOR("Wet Gas Compressor"),
    /** Dry gas compressor (after separation). */
    DRY_GAS_COMPRESSOR("Dry Gas Compressor"),
    /** Combined separator and booster. */
    SEPARATOR_BOOSTER("Separator Booster");

    private final String displayName;

    BoosterType(String displayName) {
      this.displayName = displayName;
    }

    /**
     * Get display name.
     *
     * @return display name
     */
    public String getDisplayName() {
      return displayName;
    }
  }

  /**
   * Pump type for liquid and multiphase service.
   */
  public enum PumpType {
    /** Helico-axial multiphase pump. */
    HELICO_AXIAL("Helico-Axial"),
    /** Twin-screw multiphase pump. */
    TWIN_SCREW("Twin Screw"),
    /** Counter-rotating axial pump. */
    COUNTER_ROTATING_AXIAL("Counter-Rotating Axial"),
    /** Electrical submersible pump. */
    ESP("ESP"),
    /** Centrifugal single-stage. */
    CENTRIFUGAL_SINGLE("Centrifugal Single-Stage"),
    /** Centrifugal multi-stage. */
    CENTRIFUGAL_MULTI("Centrifugal Multi-Stage");

    private final String displayName;

    PumpType(String displayName) {
      this.displayName = displayName;
    }

    /**
     * Get display name.
     *
     * @return display name
     */
    public String getDisplayName() {
      return displayName;
    }
  }

  /**
   * Compressor type for gas service.
   */
  public enum CompressorType {
    /** Centrifugal compressor. */
    CENTRIFUGAL("Centrifugal"),
    /** Axial compressor. */
    AXIAL("Axial"),
    /** Screw compressor. */
    SCREW("Screw");

    private final String displayName;

    CompressorType(String displayName) {
      this.displayName = displayName;
    }

    /**
     * Get display name.
     *
     * @return display name
     */
    public String getDisplayName() {
      return displayName;
    }
  }

  /**
   * Drive type.
   */
  public enum DriveType {
    /** Permanent magnet motor. */
    PERMANENT_MAGNET("Permanent Magnet Motor"),
    /** Induction motor. */
    INDUCTION("Induction Motor"),
    /** High-speed permanent magnet. */
    HIGH_SPEED_PM("High-Speed PM Motor");

    private final String displayName;

    DriveType(String displayName) {
      this.displayName = displayName;
    }

    /**
     * Get display name.
     *
     * @return display name
     */
    public String getDisplayName() {
      return displayName;
    }
  }

  // ============ Configuration ============
  /** Booster type. */
  private BoosterType boosterType = BoosterType.MULTIPHASE_PUMP;

  /** Pump type (for pump booster). */
  private PumpType pumpType = PumpType.HELICO_AXIAL;

  /** Compressor type (for compressor booster). */
  private CompressorType compressorType = CompressorType.CENTRIFUGAL;

  /** Drive type. */
  private DriveType driveType = DriveType.PERMANENT_MAGNET;

  /** Water depth in meters. */
  private double waterDepth = 450.0;

  // ============ Operating Parameters ============
  /** Design inlet pressure in bara. */
  private double designInletPressure = 50.0;

  /** Target outlet pressure in bara. */
  private double outletPressure = 150.0;

  /** Pressure ratio (for compressor). */
  private double pressureRatio = 3.0;

  /** Differential pressure in bar (for pump). */
  private double differentialPressure = 100.0;

  /** Design flow rate in m³/h. */
  private double designFlowRate = 500.0;

  /** Design GVF (gas volume fraction) for multiphase. */
  private double designGVF = 0.5;

  /** Maximum GVF tolerance. */
  private double maxGVF = 0.95;

  /** Speed in RPM. */
  private double speedRPM = 5000.0;

  /** Maximum speed in RPM. */
  private double maxSpeedRPM = 6000.0;

  // ============ Power ============
  /** Power rating in MW. */
  private double powerRatingMW = 5.0;

  /** Operating voltage in V. */
  private double operatingVoltage = 6600.0;

  /** Frequency in Hz. */
  private double frequency = 50.0;

  /** Efficiency. */
  private double efficiency = 0.75;

  // ============ Temperature ============
  /** Design temperature in Celsius. */
  private double designTemperature = 100.0;

  /** Motor winding temperature limit in Celsius. */
  private double motorTempLimit = 180.0;

  /** Cooling type. */
  private String coolingType = "Process Fluid";

  // ============ Structural ============
  /** Module dry weight in tonnes. */
  private double moduleDryWeight = 150.0;

  /** Module submerged weight in tonnes. */
  private double moduleSubmergedWeight = 130.0;

  /** Module height in meters. */
  private double moduleHeight = 6.0;

  /** Module footprint diameter in meters. */
  private double moduleDiameter = 5.0;

  // ============ Reliability ============
  /** Design life in years. */
  private int designLifeYears = 25;

  /** MTBF (Mean Time Between Failures) in hours. */
  private double mtbfHours = 40000.0;

  /** Whether retrievable. */
  private boolean retrievable = true;

  /** Whether has redundant motor. */
  private boolean redundantMotor = false;

  /** Number of pump/compressor stages. */
  private int numberOfStages = 6;

  // ============ Connection ============
  /** Inlet connection size in inches. */
  private double inletConnectionInches = 10.0;

  /** Outlet connection size in inches. */
  private double outletConnectionInches = 8.0;

  // ============ Internal Equipment ============
  /** Internal pump (for liquid/multiphase). */
  private Pump internalPump;

  /** Internal compressor (for gas). */
  private Compressor internalCompressor;

  /** Mechanical design instance. */
  private SubseaBoosterMechanicalDesign mechanicalDesign;

  /**
   * Default constructor.
   */
  public SubseaBooster() {
    super("Subsea Booster");
  }

  /**
   * Constructor with name.
   *
   * @param name equipment name
   */
  public SubseaBooster(String name) {
    super(name);
  }

  /**
   * Constructor with name and inlet stream.
   *
   * @param name equipment name
   * @param inStream inlet stream
   */
  public SubseaBooster(String name, StreamInterface inStream) {
    super(name, inStream);
    initializeInternalEquipment();
  }

  /**
   * Initialize internal pump/compressor.
   */
  private void initializeInternalEquipment() {
    if (inStream != null) {
      if (isCompressor()) {
        internalCompressor = new Compressor(getName() + " Compressor", inStream);
        internalCompressor.setUsePolytropicCalc(true);
        internalCompressor.setPolytropicEfficiency(efficiency);
      } else {
        internalPump = new Pump(getName() + " Pump", inStream);
        // Configure pump
      }
    }
  }

  /**
   * Create multiphase pump booster.
   *
   * @param name booster name
   * @param inStream inlet stream
   * @param outletPressure target outlet pressure in bara
   * @return configured booster
   */
  public static SubseaBooster createMultiphasePump(String name, StreamInterface inStream,
      double outletPressure) {
    SubseaBooster booster = new SubseaBooster(name, inStream);
    booster.setBoosterType(BoosterType.MULTIPHASE_PUMP);
    booster.setPumpType(PumpType.HELICO_AXIAL);
    booster.setOutletPressure(outletPressure);
    return booster;
  }

  /**
   * Create wet gas compressor booster.
   *
   * @param name booster name
   * @param inStream inlet stream
   * @param pressureRatio pressure ratio
   * @return configured booster
   */
  public static SubseaBooster createWetGasCompressor(String name, StreamInterface inStream,
      double pressureRatio) {
    SubseaBooster booster = new SubseaBooster(name, inStream);
    booster.setBoosterType(BoosterType.WET_GAS_COMPRESSOR);
    booster.setCompressorType(CompressorType.CENTRIFUGAL);
    booster.setPressureRatio(pressureRatio);
    return booster;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    if (inStream == null) {
      return;
    }

    // Reinitialize if needed
    if (isCompressor() && internalCompressor == null) {
      initializeInternalEquipment();
    } else if (!isCompressor() && internalPump == null) {
      initializeInternalEquipment();
    }

    SystemInterface outFluid;

    if (isCompressor()) {
      // Run as compressor
      internalCompressor.setInletStream(inStream);
      internalCompressor.setOutletPressure(outletPressure);
      internalCompressor.run(id);
      outFluid = internalCompressor.getOutletStream().getFluid().clone();
    } else {
      // Run as pump (simplified - use pressure increase)
      outFluid = inStream.getFluid().clone();

      // Calculate required pressure increase
      double inletPressure = inStream.getPressure();
      if (outletPressure > inletPressure) {
        outFluid.setPressure(outletPressure);
      } else {
        outFluid.setPressure(inletPressure + differentialPressure);
      }

      // Estimate temperature rise (compression heating)
      double tempRise = estimateTemperatureRise();
      double newTemp = outFluid.getTemperature() + tempRise;
      outFluid.setTemperature(newTemp);
    }

    // Set outlet
    outStream.setFluid(outFluid);
    setCalculationIdentifier(id);
  }

  /**
   * Check if booster is compressor type.
   *
   * @return true if compressor
   */
  public boolean isCompressor() {
    return boosterType == BoosterType.WET_GAS_COMPRESSOR
        || boosterType == BoosterType.DRY_GAS_COMPRESSOR;
  }

  /**
   * Estimate temperature rise from compression/pumping.
   *
   * @return temperature rise in Kelvin
   */
  private double estimateTemperatureRise() {
    if (isCompressor()) {
      // Adiabatic compression temperature rise
      double gamma = 1.3; // Approximate for hydrocarbon gas
      double tempRise = inStream.getTemperature()
          * (Math.pow(pressureRatio, (gamma - 1) / gamma) - 1) / efficiency;
      return tempRise;
    } else {
      // Pump temperature rise is minimal
      return 2.0; // Approximate 2K rise
    }
  }

  /**
   * Calculate required power.
   *
   * @return required power in MW
   */
  public double calculateRequiredPower() {
    if (inStream == null) {
      return powerRatingMW;
    }

    double power;
    if (isCompressor()) {
      // Gas power calculation
      double massFlow = inStream.getFlowRate("kg/sec");
      double cp = 2200.0; // Approximate Cp for gas in J/kgK
      double tempRise = estimateTemperatureRise();
      power = massFlow * cp * tempRise / efficiency / 1e6; // MW
    } else {
      // Pump power calculation
      double volumeFlow = inStream.getFlowRate("m3/sec");
      double deltaPressure = (outletPressure - inStream.getPressure()) * 100000; // Pa
      power = volumeFlow * deltaPressure / efficiency / 1e6; // MW
    }

    return power;
  }

  /**
   * Get mechanical design.
   *
   * @return mechanical design instance
   */
  @Override
  public SubseaBoosterMechanicalDesign getMechanicalDesign() {
    if (mechanicalDesign == null) {
      mechanicalDesign = new SubseaBoosterMechanicalDesign(this);
    }
    return mechanicalDesign;
  }

  /**
   * Initialize mechanical design.
   */
  public void initMechanicalDesign() {
    mechanicalDesign = new SubseaBoosterMechanicalDesign(this);
  }

  // ============ Getters and Setters ============

  /**
   * Get booster type.
   *
   * @return booster type
   */
  public BoosterType getBoosterType() {
    return boosterType;
  }

  /**
   * Set booster type.
   *
   * @param boosterType booster type
   */
  public void setBoosterType(BoosterType boosterType) {
    this.boosterType = boosterType;
  }

  /**
   * Get pump type.
   *
   * @return pump type
   */
  public PumpType getPumpType() {
    return pumpType;
  }

  /**
   * Set pump type.
   *
   * @param pumpType pump type
   */
  public void setPumpType(PumpType pumpType) {
    this.pumpType = pumpType;
  }

  /**
   * Get compressor type.
   *
   * @return compressor type
   */
  public CompressorType getCompressorType() {
    return compressorType;
  }

  /**
   * Set compressor type.
   *
   * @param compressorType compressor type
   */
  public void setCompressorType(CompressorType compressorType) {
    this.compressorType = compressorType;
  }

  /**
   * Get drive type.
   *
   * @return drive type
   */
  public DriveType getDriveType() {
    return driveType;
  }

  /**
   * Set drive type.
   *
   * @param driveType drive type
   */
  public void setDriveType(DriveType driveType) {
    this.driveType = driveType;
  }

  /**
   * Get water depth.
   *
   * @return water depth in meters
   */
  public double getWaterDepth() {
    return waterDepth;
  }

  /**
   * Set water depth.
   *
   * @param waterDepth water depth in meters
   */
  public void setWaterDepth(double waterDepth) {
    this.waterDepth = waterDepth;
  }

  /**
   * Get outlet pressure.
   *
   * @return outlet pressure in bara
   */
  public double getOutletPressure() {
    return outletPressure;
  }

  /**
   * Set outlet pressure.
   *
   * @param outletPressure outlet pressure in bara
   */
  public void setOutletPressure(double outletPressure) {
    this.outletPressure = outletPressure;
  }

  /**
   * Get pressure ratio.
   *
   * @return pressure ratio
   */
  public double getPressureRatio() {
    return pressureRatio;
  }

  /**
   * Set pressure ratio.
   *
   * @param pressureRatio pressure ratio
   */
  public void setPressureRatio(double pressureRatio) {
    this.pressureRatio = pressureRatio;
  }

  /**
   * Get differential pressure.
   *
   * @return differential pressure in bar
   */
  public double getDifferentialPressure() {
    return differentialPressure;
  }

  /**
   * Set differential pressure.
   *
   * @param differentialPressure differential pressure in bar
   */
  public void setDifferentialPressure(double differentialPressure) {
    this.differentialPressure = differentialPressure;
  }

  /**
   * Get design flow rate.
   *
   * @return flow rate in m³/h
   */
  public double getDesignFlowRate() {
    return designFlowRate;
  }

  /**
   * Set design flow rate.
   *
   * @param designFlowRate flow rate in m³/h
   */
  public void setDesignFlowRate(double designFlowRate) {
    this.designFlowRate = designFlowRate;
  }

  /**
   * Get design GVF.
   *
   * @return gas volume fraction
   */
  public double getDesignGVF() {
    return designGVF;
  }

  /**
   * Set design GVF.
   *
   * @param designGVF gas volume fraction (0-1)
   */
  public void setDesignGVF(double designGVF) {
    this.designGVF = designGVF;
  }

  /**
   * Get speed.
   *
   * @return speed in RPM
   */
  public double getSpeedRPM() {
    return speedRPM;
  }

  /**
   * Set speed.
   *
   * @param speedRPM speed in RPM
   */
  public void setSpeedRPM(double speedRPM) {
    this.speedRPM = speedRPM;
  }

  /**
   * Get power rating.
   *
   * @return power rating in MW
   */
  public double getPowerRatingMW() {
    return powerRatingMW;
  }

  /**
   * Set power rating.
   *
   * @param powerRatingMW power rating in MW
   */
  public void setPowerRatingMW(double powerRatingMW) {
    this.powerRatingMW = powerRatingMW;
  }

  /**
   * Get efficiency.
   *
   * @return efficiency (0-1)
   */
  public double getEfficiency() {
    return efficiency;
  }

  /**
   * Set efficiency.
   *
   * @param efficiency efficiency (0-1)
   */
  public void setEfficiency(double efficiency) {
    this.efficiency = efficiency;
  }

  /**
   * Get design temperature.
   *
   * @return design temperature in Celsius
   */
  public double getDesignTemperature() {
    return designTemperature;
  }

  /**
   * Set design temperature.
   *
   * @param designTemperature design temperature in Celsius
   */
  public void setDesignTemperature(double designTemperature) {
    this.designTemperature = designTemperature;
  }

  /**
   * Get module dry weight.
   *
   * @return dry weight in tonnes
   */
  public double getModuleDryWeight() {
    return moduleDryWeight;
  }

  /**
   * Set module dry weight.
   *
   * @param moduleDryWeight dry weight in tonnes
   */
  public void setModuleDryWeight(double moduleDryWeight) {
    this.moduleDryWeight = moduleDryWeight;
  }

  /**
   * Get design life.
   *
   * @return design life in years
   */
  public int getDesignLifeYears() {
    return designLifeYears;
  }

  /**
   * Set design life.
   *
   * @param designLifeYears design life in years
   */
  public void setDesignLifeYears(int designLifeYears) {
    this.designLifeYears = designLifeYears;
  }

  /**
   * Check if retrievable.
   *
   * @return true if retrievable
   */
  public boolean isRetrievable() {
    return retrievable;
  }

  /**
   * Set whether retrievable.
   *
   * @param retrievable true for retrievable design
   */
  public void setRetrievable(boolean retrievable) {
    this.retrievable = retrievable;
  }

  /**
   * Get number of stages.
   *
   * @return number of stages
   */
  public int getNumberOfStages() {
    return numberOfStages;
  }

  /**
   * Set number of stages.
   *
   * @param numberOfStages number of stages
   */
  public void setNumberOfStages(int numberOfStages) {
    this.numberOfStages = numberOfStages;
  }

  /**
   * Get operating voltage.
   *
   * @return operating voltage in V
   */
  public double getOperatingVoltage() {
    return operatingVoltage;
  }

  /**
   * Set operating voltage.
   *
   * @param operatingVoltage operating voltage in V
   */
  public void setOperatingVoltage(double operatingVoltage) {
    this.operatingVoltage = operatingVoltage;
  }

  /**
   * Get inlet connection size.
   *
   * @return inlet connection size in inches
   */
  public double getInletConnectionInches() {
    return inletConnectionInches;
  }

  /**
   * Set inlet connection size.
   *
   * @param inletConnectionInches inlet connection size in inches
   */
  public void setInletConnectionInches(double inletConnectionInches) {
    this.inletConnectionInches = inletConnectionInches;
  }

  /**
   * Get outlet connection size.
   *
   * @return outlet connection size in inches
   */
  public double getOutletConnectionInches() {
    return outletConnectionInches;
  }

  /**
   * Set outlet connection size.
   *
   * @param outletConnectionInches outlet connection size in inches
   */
  public void setOutletConnectionInches(double outletConnectionInches) {
    this.outletConnectionInches = outletConnectionInches;
  }

  /**
   * Get MTBF.
   *
   * @return MTBF in hours
   */
  public double getMtbfHours() {
    return mtbfHours;
  }

  /**
   * Set MTBF.
   *
   * @param mtbfHours MTBF in hours
   */
  public void setMtbfHours(double mtbfHours) {
    this.mtbfHours = mtbfHours;
  }

  /**
   * Check if has redundant motor.
   *
   * @return true if has redundant motor
   */
  public boolean hasRedundantMotor() {
    return redundantMotor;
  }

  /**
   * Set whether has redundant motor.
   *
   * @param redundantMotor true for redundant motor
   */
  public void setRedundantMotor(boolean redundantMotor) {
    this.redundantMotor = redundantMotor;
  }

  /**
   * Get design inlet pressure.
   *
   * @return design inlet pressure in bara
   */
  public double getDesignInletPressure() {
    return designInletPressure;
  }

  /**
   * Set design inlet pressure.
   *
   * @param designInletPressure design inlet pressure in bara
   */
  public void setDesignInletPressure(double designInletPressure) {
    this.designInletPressure = designInletPressure;
  }
}
