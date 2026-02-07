package neqsim.process.equipment.compressor;

import java.awt.Container;
import java.awt.FlowLayout;
import java.text.DecimalFormat;
import java.text.FieldPosition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.physicalproperties.PhysicalPropertyType;
import neqsim.process.design.AutoSizeable;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.capacity.CapacityConstrainedEquipment;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.capacity.StandardConstraintType;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.compressor.CompressorMechanicalDesign;
import neqsim.process.ml.StateVector;
import neqsim.process.ml.StateVectorProvider;
import neqsim.process.util.monitor.CompressorResponse;
import neqsim.process.util.report.ReportConfig;
import neqsim.process.util.report.ReportConfig.DetailLevel;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * Compressor class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class Compressor extends TwoPortEquipment implements CompressorInterface,
    StateVectorProvider, CapacityConstrainedEquipment, AutoSizeable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Compressor.class);

  public SystemInterface thermoSystem;
  private double outTemperature = 298.15;
  private boolean useOutTemperature = false;
  private double compressionRatio = 2.0;
  private double actualCompressionRatio = 2.0;
  private boolean useCompressionRatio = false;
  private double maxOutletPressure = 10000.0;
  private boolean isSetMaxOutletPressure = false;
  /** Maximum discharge temperature in Kelvin for capacity constraint. */
  private double maxDischargeTemperature = 473.15; // 200°C default
  private boolean isSetMaxDischargeTemperature = false;
  private CompressorPropertyProfile propertyProfile = new CompressorPropertyProfile();
  public double dH = 0.0;
  public double inletEnthalpy = 0;
  private boolean solveSpeed = false;
  public double pressure = 0.0;
  private double speed = 3000;
  private double maxspeed = 30000;
  private double minspeed = 0;
  public double isentropicEfficiency = 1.0;
  public double polytropicEfficiency = 1.0;
  public boolean usePolytropicCalc = false;
  public boolean powerSet = false;
  public boolean calcPressureOut = false;
  private CompressorChartInterface compressorChart = new CompressorChart();
  private AntiSurge antiSurge = new AntiSurge();
  private double polytropicHead = 0;
  private double polytropicFluidHead = 0;
  private double polytropicHeadMeter = 0.0;
  private double polytropicExponent = 0;
  private int numberOfCompressorCalcSteps = 40;
  private boolean useRigorousPolytropicMethod = false;
  private boolean useGERG2008 = false;
  private boolean useLeachman = false;
  private boolean useVega = false;
  private boolean limitSpeed = false;
  private boolean useEnergyEfficiencyChart = false;

  // Dynamic simulation fields
  private CompressorState operatingState = CompressorState.STOPPED;
  private CompressorDriver driver = null;
  private CompressorOperatingHistory operatingHistory = null;
  private StartupProfile startupProfile = null;
  private ShutdownProfile shutdownProfile = null;
  private transient java.util.List<CompressorEventListener> eventListeners =
      new java.util.ArrayList<>();

  // Inertia and acceleration modeling
  private double rotationalInertia = 10.0; // kg⋅m² combined rotor inertia
  private double maxAccelerationRate = 100.0; // RPM/s maximum acceleration
  private double maxDecelerationRate = 200.0; // RPM/s maximum deceleration
  private double targetSpeed = 0.0; // Target speed for dynamic control
  private boolean autoSpeedMode = false; // Automatically calculate speed from operating point

  // Performance degradation modeling
  private double degradationFactor = 1.0; // 1.0 = new, <1.0 = degraded
  private double foulingFactor = 0.0; // Head reduction due to fouling (0-1)
  private double operatingHours = 0.0; // Total operating hours

  // Efficiency solving tolerance (Kelvin)
  private double efficiencySolveTolerance = 0.01;

  // AutoSizeable fields
  /** Flag indicating if compressor has been auto-sized. */
  private boolean autoSized = false;
  /** Template used for curve generation. */
  private String curveTemplate = "CENTRIFUGAL_STANDARD";
  /** Number of speed curves to generate. */
  private int numberOfSpeedCurves = 5;

  // Surge margin thresholds for warnings/alarms
  private double surgeWarningThreshold = 0.15; // 15% margin triggers warning
  private double surgeCriticalThreshold = 0.05; // 5% margin triggers critical
  private double stoneWallWarningThreshold = 0.10; // 10% margin to stone wall

  // Startup/shutdown tracking
  private double startupElapsedTime = 0.0;
  private double shutdownElapsedTime = 0.0;
  private double speedAtShutdownStart = 0.0;

  // Event tracking flags
  private boolean surgeWarningActive = false;
  private boolean surgeCriticalActive = false;
  private boolean speedLimitWarningActive = false;

  // State memory for improved chart convergence
  private double previousActualFlow = 0.0;
  private double previousPolyHead = 0.0;

  CompressorMechanicalDesign mechanicalDesign;

  /** Mechanical losses model for seal gas and bearing calculations. */
  private CompressorMechanicalLosses mechanicalLosses = null;

  private String pressureUnit = "bara";
  private String polytropicMethod = "schultz";

  /**
   * Capacity constraints map for this compressor. Marked transient because constraints contain
   * lambdas/method references that are not serializable. Constraints are re-initialized after
   * deserialization when needed.
   */
  private transient Map<String, CapacityConstraint> capacityConstraints =
      new LinkedHashMap<String, CapacityConstraint>();

  /**
   * Constructor for Compressor.
   *
   * @param name Name of compressor
   */
  public Compressor(String name) {
    super(name);
    initMechanicalDesign();
    initializeCapacityConstraints();
  }

  /**
   * <p>
   * Constructor for Compressor.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param inletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public Compressor(String name, StreamInterface inletStream) {
    this(name);
    setInletStream(inletStream);
  }

  /**
   * <p>
   * Constructor for Compressor.
   * </p>
   *
   * @param name Name of compressor
   * @param interpolateMapLookup a boolean
   */
  public Compressor(String name, boolean interpolateMapLookup) {
    this(name);
    if (interpolateMapLookup) {
      compressorChart = new CompressorChartAlternativeMapLookup();
    }
  }

  /** {@inheritDoc} */
  @Override
  public CompressorMechanicalDesign getMechanicalDesign() {
    return mechanicalDesign;
  }

  /** {@inheritDoc} */
  @Override
  public void initMechanicalDesign() {
    mechanicalDesign = new CompressorMechanicalDesign(this);
  }

  /**
   * Get the mechanical losses model for seal gas and bearing calculations.
   *
   * @return mechanical losses model, or null if not configured
   */
  public CompressorMechanicalLosses getMechanicalLosses() {
    return mechanicalLosses;
  }

  /**
   * Set the mechanical losses model.
   *
   * @param mechanicalLosses the mechanical losses model
   */
  public void setMechanicalLosses(CompressorMechanicalLosses mechanicalLosses) {
    this.mechanicalLosses = mechanicalLosses;
  }

  /**
   * Initialize a default mechanical losses model based on current compressor configuration.
   *
   * <p>
   * This creates a mechanical losses model with typical parameters for a centrifugal compressor
   * with dry gas seals and tilting pad bearings.
   * </p>
   *
   * @return the initialized mechanical losses model
   */
  public CompressorMechanicalLosses initMechanicalLosses() {
    mechanicalLosses = new CompressorMechanicalLosses();
    return mechanicalLosses;
  }

  /**
   * Initialize a mechanical losses model with specified shaft diameter.
   *
   * @param shaftDiameterMm shaft diameter in mm
   * @return the initialized mechanical losses model
   */
  public CompressorMechanicalLosses initMechanicalLosses(double shaftDiameterMm) {
    mechanicalLosses = new CompressorMechanicalLosses(shaftDiameterMm);
    return mechanicalLosses;
  }

  /**
   * Update mechanical losses model with current operating conditions.
   *
   * <p>
   * Call this after running the compressor to update seal gas consumption and bearing losses.
   * </p>
   */
  public void updateMechanicalLosses() {
    if (mechanicalLosses == null) {
      return;
    }
    double suctionP = getInletStream().getPressure("bara");
    double dischargeP = getOutletStream().getPressure("bara");
    double gasMW = getInletStream().getFluid().getMolarMass() * 1000.0; // kg/kmol
    double gasZ = getInletStream().getFluid().getZ();

    mechanicalLosses.setOperatingConditions(suctionP, dischargeP, speed, gasMW, gasZ);
  }

  /**
   * Get total seal gas consumption including primary leakage, buffer gas, and separation gas.
   *
   * @return seal gas consumption in Nm³/hr, or 0.0 if mechanical losses not configured
   */
  public double getSealGasConsumption() {
    if (mechanicalLosses == null) {
      return 0.0;
    }
    updateMechanicalLosses();
    return mechanicalLosses.getTotalSealGasConsumption();
  }

  /**
   * Get total bearing power loss.
   *
   * @return bearing power loss in kW, or 0.0 if mechanical losses not configured
   */
  public double getBearingLoss() {
    if (mechanicalLosses == null) {
      return 0.0;
    }
    updateMechanicalLosses();
    return mechanicalLosses.getTotalBearingLoss();
  }

  /**
   * Get mechanical efficiency accounting for bearing and seal friction losses.
   *
   * @return mechanical efficiency (0-1), or 0.98 if mechanical losses not configured
   */
  public double getMechanicalEfficiency() {
    if (mechanicalLosses == null) {
      return 0.98; // Default assumption
    }
    updateMechanicalLosses();
    return mechanicalLosses.getMechanicalEfficiency(getPower("kW"));
  }

  /** {@inheritDoc} */
  @Override
  public Compressor copy() {
    return (Compressor) super.copy();
  }

  /** {@inheritDoc} */
  @Override
  public void setInletStream(StreamInterface inletStream) {
    this.inStream = inletStream;
    try {
      this.outStream = inletStream.clone();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }

  /**
   * <p>
   * solveAntiSurge.
   * </p>
   */
  public void solveAntiSurge() {
    if (getAntiSurge().isActive()) {
      // ....
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setOutletPressure(double pressure) {
    this.pressure = pressure;
  }

  /**
   * <p>
   * setOutletPressure.
   * </p>
   *
   * @param pressure a double
   * @param unit a {@link java.lang.String} object
   */
  public void setOutletPressure(double pressure, String unit) {
    this.pressure = pressure;
    this.pressureUnit = unit;
  }

  /** {@inheritDoc} */
  @Override
  public double getOutletPressure() {
    return pressure;
  }

  /** {@inheritDoc} */
  @Override
  public double getEnergy() {
    return getTotalWork();
  }

  /**
   * <p>
   * getPower.
   * </p>
   *
   * @return a double
   */
  public double getPower() {
    return getTotalWork();
  }

  /**
   * <p>
   * getPower.
   * </p>
   *
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public double getPower(String unit) {
    double conversionFactor = 1.0;
    if (unit.equals("MW")) {
      conversionFactor = 1.0 / 1.0e6;
    } else if (unit.equals("kW")) {
      conversionFactor = 1.0 / 1.0e3;
    }
    return conversionFactor * getPower();
  }

  /**
   * <p>
   * setPower.
   * </p>
   *
   * @param p a double
   */
  public void setPower(double p) {
    powerSet = true;
    dH = p;
  }

  /**
   * Calculates polytropic or isentropic efficiency by iteratively matching the specified outlet
   * temperature. The iteration continues until the temperature difference is within the tolerance
   * set by {@link #setEfficiencySolveTolerance(double)} (default 0.01 K) or the maximum iteration
   * count is reached.
   *
   * @param outTemperature the target outlet temperature in Kelvin
   * @return the calculated efficiency (polytropic or isentropic depending on configuration)
   * @see #setEfficiencySolveTolerance(double)
   */
  public double solveEfficiency(double outTemperature) {
    double funk = 0.0;
    double funkOld = 0.0;
    double newPoly;
    double dfunkdPoly = 100.0;
    double dPoly = 100.0;
    double oldPoly = outTemperature;
    useOutTemperature = false;
    run();
    useOutTemperature = true;
    int iter = 0;
    boolean useOld = usePolytropicCalc;
    // usePolytropicCalc = true;
    // System.out.println("use polytropic " + usePolytropicCalc);
    do {
      iter++;
      funk = getThermoSystem().getTemperature() - outTemperature;
      dfunkdPoly = (funk - funkOld) / dPoly;
      newPoly = polytropicEfficiency - funk / dfunkdPoly;
      if (iter <= 1) {
        newPoly = polytropicEfficiency + 0.01;
      }
      oldPoly = polytropicEfficiency;
      polytropicEfficiency = newPoly;
      isentropicEfficiency = newPoly;
      dPoly = polytropicEfficiency - oldPoly;
      funkOld = funk;
      useOutTemperature = false;
      run();
      useOutTemperature = true;
      // System.out.println("temperature compressor " +
      // getThermoSystem().getTemperature() + " funk " + funk + " polytropic " +
      // polytropicEfficiency);
    } while ((Math
        .abs((getThermoSystem().getTemperature() - outTemperature)) > efficiencySolveTolerance
        || iter < 3) && (iter < 50));
    usePolytropicCalc = useOld;
    return newPoly;
  }

  /**
   * <p>
   * findOutPressure.
   * </p>
   *
   * @param hinn a double
   * @param hout a double
   * @param polytropicEfficiency a double
   * @return a double
   */
  public double findOutPressure(double hinn, double hout, double polytropicEfficiency) {
    double entropy = getThermoSystem().getEntropy();
    getThermoSystem().setPressure(getThermoSystem().getPressure() + 1.0, pressureUnit);

    // System.out.println("entropy inn.." + entropy);
    ThermodynamicOperations thermoOps = new ThermodynamicOperations(getThermoSystem());
    thermoOps.PSflash(entropy);

    double houtGuess = hinn + dH / polytropicEfficiency;
    thermoOps.PHflash(houtGuess, 0);
    System.out.println("TEMPERATURE .." + getThermoSystem().getTemperature());
    return getThermoSystem().getPressure();
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    thermoSystem = inStream.getThermoSystem().clone();

    isActive(true);

    // Update compressor chart with inlet stream for automatic MW detection
    if (compressorChart != null) {
      compressorChart.setInletStream(inStream);
    }

    if (inStream.getFlowRate("kg/hr") < getMinimumFlow()) {
      isActive(false);
      thermoSystem.setPressure(pressure, pressureUnit);
      getOutletStream().setThermoSystem(thermoSystem);
      return;
    }

    if (Math.abs(pressure - thermoSystem.getPressure(pressureUnit)) < 1e-6
        && !compressorChart.isUseCompressorChart()) {
      thermoSystem.initProperties();
      outStream.setThermoSystem(getThermoSystem());
      outStream.setCalculationIdentifier(id);
      dH = 0.0;
      polytropicFluidHead = 0.0;
      polytropicHeadMeter = 0.0;
      return;
    }

    if (isSetEnergyStream()) {
      setPower(energyStream.getDuty());
    }

    ThermodynamicOperations thermoOps = new ThermodynamicOperations(getThermoSystem());
    thermoOps = new ThermodynamicOperations(getThermoSystem());
    getThermoSystem().init(3);
    getThermoSystem().initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
    double presinn = getThermoSystem().getPressure();
    double hinn = getThermoSystem().getEnthalpy();
    double densInn = getThermoSystem().getDensity();
    double entropy = getThermoSystem().getEntropy();

    if (useEnergyEfficiencyChart()) {
      double flow = getThermoSystem().getFlowRate("m3/hr");
      polytropicEfficiency = getCompressorChart().getPolytropicEfficiency(flow, getSpeed()) / 100.0;
    }

    if (useGERG2008 && inStream.getThermoSystem().getNumberOfPhases() == 1) {
      double[] gergProps;
      gergProps = getThermoSystem().getPhase(0).getProperties_GERG2008();
      hinn = gergProps[7] * getThermoSystem().getPhase(0).getNumberOfMolesInPhase();
      entropy = gergProps[8] * getThermoSystem().getPhase(0).getNumberOfMolesInPhase();
      densInn = getThermoSystem().getPhase(0).getDensity_GERG2008();
    }

    if (useLeachman && inStream.getThermoSystem().getNumberOfPhases() == 1) {
      double[] LeachmanProps;
      LeachmanProps = getThermoSystem().getPhase(0).getProperties_Leachman();
      hinn = LeachmanProps[7] * getThermoSystem().getPhase(0).getNumberOfMolesInPhase();
      entropy = LeachmanProps[8] * getThermoSystem().getPhase(0).getNumberOfMolesInPhase();
      densInn = getThermoSystem().getPhase(0).getDensity_Leachman();
    }

    if (useVega && inStream.getThermoSystem().getNumberOfPhases() == 1) {
      double[] VegaProps;
      VegaProps = getThermoSystem().getPhase(0).getProperties_Vega();
      hinn = VegaProps[7] * getThermoSystem().getPhase(0).getNumberOfMolesInPhase();
      entropy = VegaProps[8] * getThermoSystem().getPhase(0).getNumberOfMolesInPhase();
      densInn = getThermoSystem().getPhase(0).getDensity_Vega();
    }

    inletEnthalpy = hinn;
    boolean surgeCheck = false;
    double orginalMolarFLow = thermoSystem.getTotalNumberOfMoles();
    double fractionAntiSurge = 0.0;
    double kappa = 0.0;
    if (useCompressionRatio) {
      double outpres = presinn * compressionRatio;
      if (isSetMaxOutletPressure && outpres > maxOutletPressure) {
        outpres = maxOutletPressure;
      }
      setOutletPressure(outpres);
    }
    if (isCalcPressureOut()) {
      double actualFlowRate = thermoSystem.getFlowRate("m3/hr");
      double z_inlet = thermoSystem.getZ();
      double MW = thermoSystem.getMolarMass();
      double efficiency = 0.8;
      if (usePolytropicCalc) {
        efficiency = getPolytropicEfficiency();
      } else {
        efficiency = getIsentropicEfficiency();
      }
      polytropicHead = dH / getThermoSystem().getFlowRate("kg/sec") / 1000.0 * efficiency;
      polytropicFluidHead = polytropicHead;
      polytropicHeadMeter = polytropicFluidHead * 1000.0 / ThermodynamicConstantsInterface.gravity;
      double temperature_inlet = thermoSystem.getTemperature();
      if (getCompressorChart().useRealKappa()) {
        kappa = thermoSystem.getGamma();
      } else {
        kappa = thermoSystem.getGamma2();
      }
      double n = 1.0 / (1.0 - (kappa - 1.0) / kappa * 1.0 / (getPolytropicEfficiency()));
      double pressureRatio = Math.pow((polytropicFluidHead * 1000.0 + (n / (n - 1.0) * z_inlet
          * ThermodynamicConstantsInterface.R * (temperature_inlet) / MW))
          / (n / (n - 1.0) * z_inlet * ThermodynamicConstantsInterface.R * (temperature_inlet)
              / MW),
          n / (n - 1.0));
      setOutletPressure(pressureRatio * getInletPressure());
    }
    if (useOutTemperature)

    {
      if (useRigorousPolytropicMethod) {
        solveEfficiency(outTemperature);
        polytropicHead = getPower() / getThermoSystem().getFlowRate("kg/sec") / 1000.0
            * getPolytropicEfficiency();
        polytropicFluidHead = polytropicHead;
        polytropicHeadMeter =
            polytropicFluidHead * 1000.0 / ThermodynamicConstantsInterface.gravity;
        return;
      } else {
        double MW = thermoSystem.getMolarMass();
        thermoSystem.setPressure(getOutletPressure(), pressureUnit);
        thermoOps.PSflash(entropy);
        if (useGERG2008 && inStream.getThermoSystem().getNumberOfPhases() == 1) {
          thermoOps.PSflashGERG2008(entropy);
        }
        if (useLeachman && inStream.getThermoSystem().getNumberOfPhases() == 1) {
          thermoOps.PSflashLeachman(entropy);
        }
        if (useVega && inStream.getThermoSystem().getNumberOfPhases() == 1) {
          thermoOps.PSflashVega(entropy);
        }
        thermoSystem.initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
        double densOutIsentropic = thermoSystem.getDensity("kg/m3");
        double enthalpyOutIsentropic = thermoSystem.getEnthalpy();
        if (useGERG2008 && inStream.getThermoSystem().getNumberOfPhases() == 1) {
          double[] gergProps;
          gergProps = getThermoSystem().getPhase(0).getProperties_GERG2008();
          densOutIsentropic = getThermoSystem().getPhase(0).getDensity_GERG2008();
          enthalpyOutIsentropic =
              gergProps[7] * getThermoSystem().getPhase(0).getNumberOfMolesInPhase();
        }
        if (useLeachman && inStream.getThermoSystem().getNumberOfPhases() == 1) {
          double[] LeachmanProps;
          LeachmanProps = getThermoSystem().getPhase(0).getProperties_Leachman();
          densOutIsentropic = getThermoSystem().getPhase(0).getDensity_Leachman();
          enthalpyOutIsentropic =
              LeachmanProps[7] * getThermoSystem().getPhase(0).getNumberOfMolesInPhase();
        }
        if (useVega && inStream.getThermoSystem().getNumberOfPhases() == 1) {
          double[] VegaProps;
          VegaProps = getThermoSystem().getPhase(0).getProperties_Vega();
          densOutIsentropic = getThermoSystem().getPhase(0).getDensity_Vega();
          enthalpyOutIsentropic =
              VegaProps[7] * getThermoSystem().getPhase(0).getNumberOfMolesInPhase();
        }
        thermoSystem.setTemperature(outTemperature);
        thermoOps.TPflash();
        thermoSystem.init(2);
        thermoSystem.initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
        double outEnthalpy = thermoSystem.getEnthalpy();
        double densOut = thermoSystem.getDensity("kg/m3");
        if (useGERG2008 && inStream.getThermoSystem().getNumberOfPhases() == 1) {
          double[] gergProps;
          gergProps = getThermoSystem().getPhase(0).getProperties_GERG2008();
          outEnthalpy = gergProps[7] * getThermoSystem().getPhase(0).getNumberOfMolesInPhase();
          densOut = getThermoSystem().getPhase(0).getDensity_GERG2008();
        }
        if (useLeachman && inStream.getThermoSystem().getNumberOfPhases() == 1) {
          double[] LeachmanProps;
          LeachmanProps = getThermoSystem().getPhase(0).getProperties_Leachman();
          outEnthalpy = LeachmanProps[7] * getThermoSystem().getPhase(0).getNumberOfMolesInPhase();
          densOut = getThermoSystem().getPhase(0).getDensity_Leachman();
        }
        if (useVega && inStream.getThermoSystem().getNumberOfPhases() == 1) {
          double[] VegaProps;
          VegaProps = getThermoSystem().getPhase(0).getProperties_Vega();
          outEnthalpy = VegaProps[7] * getThermoSystem().getPhase(0).getNumberOfMolesInPhase();
          densOut = getThermoSystem().getPhase(0).getDensity_Vega();
        }
        dH = outEnthalpy - inletEnthalpy;
        // System.out.println("total power " +
        // dH/getThermoSystem().getFlowRate("kg/sec"));

        double n = Math.log(getOutletPressure() / presinn) / Math.log(densOut / densInn);
        double CF = (enthalpyOutIsentropic - inletEnthalpy) / thermoSystem.getFlowRate("kg/sec")
            / (n / (n - 1.0)
                * (getOutletPressure() * 1e5 / densOutIsentropic - presinn * 1e5 / densInn));

        double F1 = thermoSystem.getTotalNumberOfMoles();
        double polytropicPower = F1 * MW * (n / (n - 1.0)) * CF * presinn * 1e5 / densInn
            * (Math.pow((getOutletPressure() / presinn), (n - 1.0) / n) - 1.0);
        // System.out.println("polytropic power " +
        // polytropicPower/getThermoSystem().getFlowRate("kg/sec"));
        polytropicEfficiency = polytropicPower / getThermoSystem().getFlowRate("kg/sec")
            / (dH / getThermoSystem().getFlowRate("kg/sec"));
        isentropicEfficiency = (enthalpyOutIsentropic - inletEnthalpy) / dH;

        // isentropicEfficiency = (getThermoSystem().getEnthalpy() - hinn) / dH;
        double k = Math.log(getOutletPressure() / presinn) / Math.log(densOutIsentropic / densInn);
        double term1 = Math.pow(getOutletPressure() / presinn, (n - 1.0) / n) - 1.0;
        double term2 = n / (n - 1.0) * (k - 1.0) / k;
        double term3 = Math.pow(getOutletPressure() / presinn, (k - 1.0) / k) - 1.0;
        double polyPow = term1 * term2 / term3 * isentropicEfficiency;
        polytropicEfficiency = polyPow;
        polytropicPower = dH * polytropicEfficiency;
        // System.out.println("polytropic eff " + polytropicEfficiency);
        // System.out.println("isentropic eff " + isentropicEfficiency);
        polytropicFluidHead = polytropicPower / getThermoSystem().getFlowRate("kg/sec") / 1000.0;
        polytropicHeadMeter = polytropicFluidHead * 1000.0 / 9.81;
        polytropicHead = polytropicFluidHead;
        if (getCompressorChart().isUseCompressorChart()) {
          if (getCompressorChart().getHeadUnit().equals("meter")) {
            polytropicHead = polytropicHeadMeter;
          } else {
            polytropicHead = polytropicFluidHead;
          }
        }
        outStream.setThermoSystem(getThermoSystem());
        outStream.setCalculationIdentifier(id);
        setCalculationIdentifier(id);
        return;
      }
    }
    if (compressorChart.isUseCompressorChart()) {
      if (solveSpeed) {
        double targetPressure = getOutletPressure(); // Desired outlet pressure
        double tolerance = 1e-3; // Tolerance for pressure difference (0.001 bar)
        double minSpeed = getMinimumSpeed(); // Minimum speed for the compressor
        double maxSpeed = getMaximumSpeed(); // Maximum speed for the compressor
        double currentSpeed = getSpeed(); // Initial guess for speed

        // Improve initial guess: if starting speed is far from chart range, use chart
        // reference
        // speed as initial guess to avoid polynomial extrapolation issues
        double chartMinSpeed = getCompressorChart().getMinSpeedCurve();
        double chartMaxSpeed = getCompressorChart().getMaxSpeedCurve();
        double chartRefSpeed = (chartMinSpeed + chartMaxSpeed) / 2.0;
        if (currentSpeed < chartMinSpeed * 0.5 || currentSpeed > chartMaxSpeed * 2.0) {
          currentSpeed = chartRefSpeed;
        }

        double maxIterations = 200; // Maximum number of iterations
        double deltaSpeed = 100.0; // Small increment for numerical derivative
        int iteration = 1;

        while (iteration < maxIterations) {
          // Calculate the pressure at the current speed
          double actualFlowRate = thermoSystem.getFlowRate("m3/hr");
          double z_inlet = thermoSystem.getZ();
          double MW = thermoSystem.getMolarMass();
          if (getCompressorChart().useRealKappa()) {
            kappa = thermoSystem.getGamma();
          } else {
            kappa = thermoSystem.getGamma2();
          }

          if (useGERG2008 && inStream.getThermoSystem().getNumberOfPhases() == 1) {
            double[] gergProps = getThermoSystem().getPhase(0).getProperties_GERG2008();
            actualFlowRate *= gergProps[1] / z_inlet;
            kappa = gergProps[14];
            z_inlet = gergProps[1];
          }

          if (useLeachman && inStream.getThermoSystem().getNumberOfPhases() == 1) {
            double[] LeachmanProps = getThermoSystem().getPhase(0).getProperties_Leachman();
            actualFlowRate *= LeachmanProps[1] / z_inlet;
            kappa = LeachmanProps[14];
            z_inlet = LeachmanProps[1];
          }

          if (useVega && inStream.getThermoSystem().getNumberOfPhases() == 1) {
            double[] VegaProps = getThermoSystem().getPhase(0).getProperties_Vega();
            actualFlowRate *= VegaProps[1] / z_inlet;
            kappa = VegaProps[14];
            z_inlet = VegaProps[1];
          }

          double polytropEff =
              getCompressorChart().getPolytropicEfficiency(actualFlowRate, currentSpeed);
          setPolytropicEfficiency(polytropEff / 100.0);
          if (polytropEff <= 0.0) {
            polytropEff = 0.01;
            setPolytropicEfficiency(0.01);
          }
          if (polytropEff > 100.0) {
            polytropEff = 100;
            setPolytropicEfficiency(100.0);
          }

          polytropicHead = getCompressorChart().getPolytropicHead(actualFlowRate, currentSpeed);
          double temperature_inlet = thermoSystem.getTemperature();
          double n = 1.0 / (1.0 - (kappa - 1.0) / kappa * 1.0 / (polytropEff / 100.0));
          polytropicFluidHead =
              (getCompressorChart().getHeadUnit().equals("meter")) ? polytropicHead / 1000.0 * 9.81
                  : polytropicHead;
          if (polytropicFluidHead <= 0.0) {
            polytropicFluidHead = 0.0001;
          }
          double pressureRatio = Math.pow((polytropicFluidHead * 1000.0 + (n / (n - 1.0) * z_inlet
              * ThermodynamicConstantsInterface.R * (temperature_inlet) / MW))
              / (n / (n - 1.0) * z_inlet * ThermodynamicConstantsInterface.R * (temperature_inlet)
                  / MW),
              n / (n - 1.0));
          double currentPressure = thermoSystem.getPressure() * pressureRatio;

          // Calculate the derivative of pressure with respect to speed
          double polytropEffDelta = getCompressorChart().getPolytropicEfficiency(actualFlowRate,
              currentSpeed + deltaSpeed);
          double polytropicHeadDelta =
              getCompressorChart().getPolytropicHead(actualFlowRate, currentSpeed + deltaSpeed);
          double nDelta = 1.0 / (1.0 - (kappa - 1.0) / kappa * 1.0 / (polytropEffDelta / 100.0));
          double polytropicFluidHeadDelta = (getCompressorChart().getHeadUnit().equals("meter"))
              ? polytropicHeadDelta / 1000.0 * 9.81
              : polytropicHeadDelta;
          double pressureRatioDelta =
              Math.pow((polytropicFluidHeadDelta * 1000.0 + (nDelta / (nDelta - 1.0) * z_inlet
                  * ThermodynamicConstantsInterface.R * (temperature_inlet) / MW))
                  / (nDelta / (nDelta - 1.0) * z_inlet * ThermodynamicConstantsInterface.R
                      * (temperature_inlet) / MW),
                  nDelta / (nDelta - 1.0));
          double pressureNew = thermoSystem.getPressure() * pressureRatioDelta;

          double dPressure_dSpeed = (pressureNew - currentPressure) / deltaSpeed;

          // Handle case where derivative is too small or wrong sign
          if (Math.abs(dPressure_dSpeed) < 1e-6) {
            // Derivative too small - use a reasonable default based on typical compressor
            // behavior
            // Pressure typically increases with speed, so assume positive relationship
            dPressure_dSpeed = 0.01; // Approximate 0.01 bar per RPM
          }

          // Update speed using Newton-Raphson method
          double relaxationFactor = Math.min(0.8, iteration / (iteration + 3.0));

          double speedUpdate = (targetPressure - currentPressure) / dPressure_dSpeed;

          // Limit speed update to avoid large oscillations
          double maxSpeedUpdate = Math.max(500.0, 0.2 * currentSpeed);
          if (Math.abs(speedUpdate) > maxSpeedUpdate) {
            speedUpdate = Math.signum(speedUpdate) * maxSpeedUpdate;
          }

          currentSpeed += relaxationFactor * speedUpdate;
          if (currentSpeed < 0) {
            if (minSpeed > 1) {
              currentSpeed = minSpeed;
            } else {
              currentSpeed = getCompressorChart().getMinSpeedCurve();
            }
          }
          if (iteration % 10 == 0 && deltaSpeed > 10) {
            deltaSpeed = deltaSpeed / 2;
          }

          powerSet = true;
          dH = polytropicFluidHead * 1000.0 * thermoSystem.getMolarMass()
              / getPolytropicEfficiency() * thermoSystem.getTotalNumberOfMoles();
          // Check if speed is within bounds
          if (currentSpeed < minSpeed || currentSpeed > maxSpeed) {
            if (limitSpeed) {
              setSolveSpeed(false);
              setCalcPressureOut(true);
              if (currentSpeed > maxSpeed) {
                setSpeed(maxSpeed);
              } else if (currentSpeed < minSpeed) {
                setSpeed(minSpeed);
              }
              run();
              setSolveSpeed(true);
              setCalcPressureOut(false);
              return;
            } else {
              // throw new IllegalArgumentException(
              // "Speed out of bounds during Newton-Raphson iteration.");
            }
            // throw new IllegalArgumentException(
            // "Speed out of bounds during Newton-Raphson iteration.");
          }

          // Check for convergence
          if (Math.abs(currentPressure - targetPressure) <= tolerance) {
            setSpeed(currentSpeed); // Update the final speed
            break;
          }

          iteration++;
        }

        if (iteration == maxIterations) {
          // Did not converge, but use the best estimate and log a warning
          logger.warn("Newton-Raphson speed solver did not fully converge after " + maxIterations
              + " iterations. Using best estimate speed: " + currentSpeed);
          setSpeed(currentSpeed);
        }
      } else {
        do {
          double actualFlowRate = thermoSystem.getFlowRate("m3/hr");
          double z_inlet = thermoSystem.getZ();
          double MW = thermoSystem.getMolarMass();

          if (getCompressorChart().useRealKappa()) {
            kappa = thermoSystem.getGamma();
          } else {
            kappa = thermoSystem.getGamma2();
          }
          if (useGERG2008 && inStream.getThermoSystem().getNumberOfPhases() == 1) {
            double[] gergProps;
            gergProps = getThermoSystem().getPhase(0).getProperties_GERG2008();
            actualFlowRate *= gergProps[1] / z_inlet;
            kappa = gergProps[14];
            z_inlet = gergProps[1];
          }

          if (useLeachman && inStream.getThermoSystem().getNumberOfPhases() == 1) {
            double[] LeachmanProps;
            LeachmanProps = getThermoSystem().getPhase(0).getProperties_Leachman();
            actualFlowRate *= LeachmanProps[1] / z_inlet;
            kappa = LeachmanProps[14];
            z_inlet = LeachmanProps[1];
          }

          if (useVega && inStream.getThermoSystem().getNumberOfPhases() == 1) {
            double[] VegaProps;
            VegaProps = getThermoSystem().getPhase(0).getProperties_Vega();
            actualFlowRate *= VegaProps[1] / z_inlet;
            kappa = VegaProps[14];
            z_inlet = VegaProps[1];
          }

          double polytropEff =
              getCompressorChart().getPolytropicEfficiency(actualFlowRate, getSpeed());
          setPolytropicEfficiency(polytropEff / 100.0);
          polytropicHead = getCompressorChart().getPolytropicHead(actualFlowRate, getSpeed());
          double temperature_inlet = thermoSystem.getTemperature();
          double n = 1.0 / (1.0 - (kappa - 1.0) / kappa * 1.0 / (polytropEff / 100.0));
          polytropicExponent = n;
          if (getCompressorChart().getHeadUnit().equals("meter")) {
            polytropicFluidHead = polytropicHead / 1000.0 * 9.81;
            polytropicHeadMeter = polytropicHead;
          } else {
            polytropicFluidHead = polytropicHead;
            polytropicHeadMeter = polytropicHead * 1000.0 / 9.81;
          }
          double pressureRatio = Math.pow((polytropicFluidHead * 1000.0 + (n / (n - 1.0) * z_inlet
              * ThermodynamicConstantsInterface.R * (temperature_inlet) / MW))
              / (n / (n - 1.0) * z_inlet * ThermodynamicConstantsInterface.R * (temperature_inlet)
                  / MW),
              n / (n - 1.0));
          setOutletPressure(thermoSystem.getPressure() * pressureRatio);
          if (getAntiSurge().isActive()) {
            logger.info("surge flow "
                + getCompressorChart().getSurgeCurve().getSurgeFlow(polytropicHead) + " m3/hr");
            surgeCheck = isSurge(polytropicHead, actualFlowRate);
          }
          if (getCompressorChart().getStoneWallCurve().isActive()) {
            // logger.info("stone wall? " + isStoneWall(polytropicHead,
            // thermoSystem.getFlowRate("m3/hr")));
          }
          if (surgeCheck && getAntiSurge().isActive()) {
            thermoSystem.setTotalFlowRate(
                getAntiSurge().getSurgeControlFactor()
                    * getCompressorChart().getSurgeCurve().getSurgeFlow(polytropicFluidHead),
                "Am3/hr");
            thermoSystem.init(3);
            fractionAntiSurge = thermoSystem.getTotalNumberOfMoles() / orginalMolarFLow - 1.0;
            getAntiSurge().setCurrentSurgeFraction(fractionAntiSurge);
          }

          powerSet = true;
          dH = polytropicFluidHead * 1000.0 * thermoSystem.getMolarMass()
              / getPolytropicEfficiency() * thermoSystem.getTotalNumberOfMoles();
        } while (surgeCheck && getAntiSurge().isActive());
      }
    }

    if (usePolytropicCalc)

    {
      if (powerSet) {
        double hout = hinn * (1 - 0 + fractionAntiSurge) + dH;
        thermoSystem.setPressure(pressure, pressureUnit);
        thermoOps = new ThermodynamicOperations(getThermoSystem());
        thermoOps.PHflash(hout, 0);
        if (useGERG2008 && inStream.getThermoSystem().getNumberOfPhases() == 1) {
          thermoOps.PHflashGERG2008(hout);
        }
        if (useLeachman && inStream.getThermoSystem().getNumberOfPhases() == 1) {
          thermoOps.PHflashLeachman(hout);
        }
        if (useVega && inStream.getThermoSystem().getNumberOfPhases() == 1) {
          thermoOps.PHflashVega(hout);
        }
      } else {
        if (polytropicMethod.equals("detailed")) {
          // TODO: add detailed output of compressor calculations
          int numbersteps = numberOfCompressorCalcSteps;
          double dp = (pressure - getThermoSystem().getPressure()) / (1.0 * numbersteps);
          for (int i = 0; i < numbersteps; i++) {
            entropy = getThermoSystem().getEntropy();
            hinn = getThermoSystem().getEnthalpy();
            if (useGERG2008 && inStream.getThermoSystem().getNumberOfPhases() == 1) {
              double[] gergProps;
              gergProps = getThermoSystem().getPhase(0).getProperties_GERG2008();
              hinn = gergProps[7] * getThermoSystem().getPhase(0).getNumberOfMolesInPhase();
              entropy = gergProps[8] * getThermoSystem().getPhase(0).getNumberOfMolesInPhase();
            }
            if (useLeachman && inStream.getThermoSystem().getNumberOfPhases() == 1) {
              double[] LeachmanProps;
              LeachmanProps = getThermoSystem().getPhase(0).getProperties_Leachman();
              hinn = LeachmanProps[7] * getThermoSystem().getPhase(0).getNumberOfMolesInPhase();
              entropy = LeachmanProps[8] * getThermoSystem().getPhase(0).getNumberOfMolesInPhase();
            }
            if (useVega && inStream.getThermoSystem().getNumberOfPhases() == 1) {
              double[] VegaProps;
              VegaProps = getThermoSystem().getPhase(0).getProperties_Vega();
              hinn = VegaProps[7] * getThermoSystem().getPhase(0).getNumberOfMolesInPhase();
              entropy = VegaProps[8] * getThermoSystem().getPhase(0).getNumberOfMolesInPhase();
            }
            getThermoSystem().setPressure(getThermoSystem().getPressure() + dp, pressureUnit);
            thermoOps = new ThermodynamicOperations(getThermoSystem());
            if (useGERG2008 && inStream.getThermoSystem().getNumberOfPhases() == 1) {
              thermoOps.PSflashGERG2008(entropy);
            } else if (useLeachman && inStream.getThermoSystem().getNumberOfPhases() == 1) {
              thermoOps.PSflashLeachman(entropy);
            } else if (useVega && inStream.getThermoSystem().getNumberOfPhases() == 1) {
              thermoOps.PSflashVega(entropy);
            } else {
              double oleTemp = getThermoSystem().getTemperature();
              thermoOps.PSflash(entropy);
              if (Math.abs(getThermoSystem().getEntropy() - entropy) > 1e-3) {
                getThermoSystem().setTemperature(oleTemp);
                thermoOps.TPflash();
                getThermoSystem().init(2);
                continue;
              }
            }
            double newEnt = getThermoSystem().getEnthalpy();
            if (useGERG2008 && inStream.getThermoSystem().getNumberOfPhases() == 1) {
              double[] gergProps;
              gergProps = getThermoSystem().getPhase(0).getProperties_GERG2008();
              newEnt = gergProps[7] * getThermoSystem().getPhase(0).getNumberOfMolesInPhase();
            }
            if (useLeachman && inStream.getThermoSystem().getNumberOfPhases() == 1) {
              double[] LeachmanProps;
              LeachmanProps = getThermoSystem().getPhase(0).getProperties_Leachman();
              newEnt = LeachmanProps[7] * getThermoSystem().getPhase(0).getNumberOfMolesInPhase();
            }
            if (useVega && inStream.getThermoSystem().getNumberOfPhases() == 1) {
              double[] VegaProps;
              VegaProps = getThermoSystem().getPhase(0).getProperties_Vega();
              newEnt = VegaProps[7] * getThermoSystem().getPhase(0).getNumberOfMolesInPhase();
            }
            double hout = hinn + (newEnt - hinn) / polytropicEfficiency;
            thermoOps.PHflash(hout, 0);
            if (useGERG2008 && inStream.getThermoSystem().getNumberOfPhases() == 1) {
              thermoOps.PHflashGERG2008(hout);
            }
            if (useLeachman && inStream.getThermoSystem().getNumberOfPhases() == 1) {
              thermoOps.PHflashLeachman(hout);
            }
            if (useVega && inStream.getThermoSystem().getNumberOfPhases() == 1) {
              thermoOps.PHflashVega(hout);
            }
            if (propertyProfile.isActive()) {
              propertyProfile.addFluid(thermoSystem.clone());
            }
          }
        } else if (polytropicMethod.equals("schultz")) {
          double schultzX =
              thermoSystem.getTemperature() / thermoSystem.getVolume() * thermoSystem.getdVdTpn()
                  - 1.0;
          double schultzY =
              -thermoSystem.getPressure() / thermoSystem.getVolume() * thermoSystem.getdVdPtn();
          thermoSystem.setPressure(getOutletPressure(), pressureUnit);
          thermoOps.PSflash(entropy);
          thermoSystem.initProperties();
          double densOutIsentropic = thermoSystem.getDensity("kg/m3");
          double enthalpyOutIsentropic = thermoSystem.getEnthalpy();
          if (useGERG2008 && inStream.getThermoSystem().getNumberOfPhases() == 1) {
            thermoOps.PSflashGERG2008(entropy);
            double[] gergProps;
            gergProps = getThermoSystem().getPhase(0).getProperties_GERG2008();
            densOutIsentropic = getThermoSystem().getPhase(0).getDensity_GERG2008();
            enthalpyOutIsentropic =
                gergProps[7] * getThermoSystem().getPhase(0).getNumberOfMolesInPhase();
          }
          if (useLeachman && inStream.getThermoSystem().getNumberOfPhases() == 1) {
            thermoOps.PSflashLeachman(entropy);
            double[] LeachmanProps;
            LeachmanProps = getThermoSystem().getPhase(0).getProperties_Leachman();
            densOutIsentropic = getThermoSystem().getPhase(0).getDensity_Leachman();
            enthalpyOutIsentropic =
                LeachmanProps[7] * getThermoSystem().getPhase(0).getNumberOfMolesInPhase();
          }
          if (useVega && inStream.getThermoSystem().getNumberOfPhases() == 1) {
            thermoOps.PSflashVega(entropy);
            double[] VegaProps;
            VegaProps = getThermoSystem().getPhase(0).getProperties_Vega();
            densOutIsentropic = getThermoSystem().getPhase(0).getDensity_Vega();
            enthalpyOutIsentropic =
                VegaProps[7] * getThermoSystem().getPhase(0).getNumberOfMolesInPhase();
          }
          double isenthalpicvolumeexponent =
              Math.log(getOutletPressure() / presinn) / Math.log(densOutIsentropic / densInn);
          double nV = (1.0 + schultzX)
              / (1.0 / isenthalpicvolumeexponent * (1.0 / polytropicEfficiency + schultzX)
                  - schultzY * (1.0 / polytropicEfficiency - 1.0));
          polytropicExponent = nV;
          double term = nV / (nV - 1.0);
          double term2 = 1e5 * (getOutletPressure() / densOutIsentropic - presinn / densInn);
          double term3 = isenthalpicvolumeexponent / (isenthalpicvolumeexponent - 1.0);
          double CF = (enthalpyOutIsentropic - inletEnthalpy) / (term2 * term3);
          dH = term * CF * 1e5 * presinn / densInn
              * (Math.pow(getOutletPressure() / presinn, 1.0 / term) - 1.0) / polytropicEfficiency;
          double hout = hinn + dH;
          thermoOps = new ThermodynamicOperations(getThermoSystem());
          thermoOps.PHflash(hout, 0);
          if (useGERG2008 && inStream.getThermoSystem().getNumberOfPhases() == 1) {
            thermoOps.PHflashGERG2008(hout);
          }
          if (useLeachman && inStream.getThermoSystem().getNumberOfPhases() == 1) {
            thermoOps.PHflashLeachman(hout);
          }
          if (useVega && inStream.getThermoSystem().getNumberOfPhases() == 1) {
            thermoOps.PHflashVega(hout);
          }
        } else {
          thermoSystem.setPressure(getOutletPressure(), pressureUnit);
          thermoOps.PSflash(entropy);
          thermoSystem.initProperties();
          double densOutIsentropic = thermoSystem.getDensity("kg/m3");
          double enthalpyOutIsentropic = thermoSystem.getEnthalpy();
          if (useGERG2008 && inStream.getThermoSystem().getNumberOfPhases() == 1) {
            thermoOps.PSflashGERG2008(entropy);
            double[] gergProps;
            gergProps = getThermoSystem().getPhase(0).getProperties_GERG2008();
            densOutIsentropic = getThermoSystem().getPhase(0).getDensity_GERG2008();
            enthalpyOutIsentropic =
                gergProps[7] * getThermoSystem().getPhase(0).getNumberOfMolesInPhase();
          }
          if (useLeachman && inStream.getThermoSystem().getNumberOfPhases() == 1) {
            thermoOps.PSflashLeachman(entropy);
            double[] LeachmanProps;
            LeachmanProps = getThermoSystem().getPhase(0).getProperties_Leachman();
            densOutIsentropic = getThermoSystem().getPhase(0).getDensity_Leachman();
            enthalpyOutIsentropic =
                LeachmanProps[7] * getThermoSystem().getPhase(0).getNumberOfMolesInPhase();
          }
          if (useVega && inStream.getThermoSystem().getNumberOfPhases() == 1) {
            thermoOps.PSflashVega(entropy);
            double[] VegaProps;
            VegaProps = getThermoSystem().getPhase(0).getProperties_Vega();
            densOutIsentropic = getThermoSystem().getPhase(0).getDensity_Vega();
            enthalpyOutIsentropic =
                VegaProps[7] * getThermoSystem().getPhase(0).getNumberOfMolesInPhase();
          }
          double isenthalpicvolumeexponent =
              Math.log(getOutletPressure() / presinn) / Math.log(densOutIsentropic / densInn);
          // Calculate polytropic exponent from isentropic exponent and efficiency
          // n_poly = k_s / (1 - (k_s - 1) / k_s * (1 - eta_poly))
          // Simplified: use isenthalpicvolumeexponent as base
          polytropicExponent = isenthalpicvolumeexponent / (1.0 - (isenthalpicvolumeexponent - 1.0)
              / isenthalpicvolumeexponent * (1.0 - polytropicEfficiency));
          double term = isenthalpicvolumeexponent / (isenthalpicvolumeexponent - 1.0)
              * (polytropicEfficiency);
          double term2 = 1e5 * (getOutletPressure() / densOutIsentropic - presinn / densInn);
          double CF = (enthalpyOutIsentropic - inletEnthalpy) / (term * term2);
          dH = term * CF * 1e5 * presinn / densInn
              * (Math.pow(getOutletPressure() / presinn, 1.0 / term) - 1.0);
          double hout = hinn + dH;
          thermoOps = new ThermodynamicOperations(getThermoSystem());
          thermoOps.PHflash(hout, 0);
          if (useGERG2008 && inStream.getThermoSystem().getNumberOfPhases() == 1) {
            thermoOps.PHflashGERG2008(hout);
          }
          if (useLeachman && inStream.getThermoSystem().getNumberOfPhases() == 1) {
            thermoOps.PHflashLeachman(hout);
          }
          if (useVega && inStream.getThermoSystem().getNumberOfPhases() == 1) {
            thermoOps.PHflashVega(hout);
          }
        }
      }
    } else {
      getThermoSystem().setPressure(pressure, pressureUnit);
      // System.out.println("entropy inn.." + entropy);
      thermoOps = new ThermodynamicOperations(getThermoSystem());
      thermoOps.PSflash(entropy);
      if (useGERG2008 && inStream.getThermoSystem().getNumberOfPhases() == 1) {
        thermoOps.PSflashGERG2008(entropy);
      }
      if (useLeachman && inStream.getThermoSystem().getNumberOfPhases() == 1) {
        thermoOps.PSflashLeachman(entropy);
      }
      if (useVega && inStream.getThermoSystem().getNumberOfPhases() == 1) {
        thermoOps.PSflashVega(entropy);
      }
      // double densOutIdeal = getThermoSystem().getDensity();
      double newEnt = getThermoSystem().getEnthalpy();
      if (!powerSet) {
        dH = (getThermoSystem().getEnthalpy() - hinn) / isentropicEfficiency;
        if (useGERG2008 && inStream.getThermoSystem().getNumberOfPhases() == 1) {
          double[] gergProps;
          gergProps = getThermoSystem().getPhase(0).getProperties_GERG2008();
          newEnt = gergProps[7] * getThermoSystem().getPhase(0).getNumberOfMolesInPhase();
          dH = (newEnt - hinn) / isentropicEfficiency;
        }
        if (useLeachman && inStream.getThermoSystem().getNumberOfPhases() == 1) {
          double[] LeachmanProps;
          LeachmanProps = getThermoSystem().getPhase(0).getProperties_Leachman();
          newEnt = LeachmanProps[7] * getThermoSystem().getPhase(0).getNumberOfMolesInPhase();
          dH = (newEnt - hinn) / isentropicEfficiency;
        }
        if (useVega && inStream.getThermoSystem().getNumberOfPhases() == 1) {
          double[] VegaProps;
          VegaProps = getThermoSystem().getPhase(0).getProperties_Vega();
          newEnt = VegaProps[7] * getThermoSystem().getPhase(0).getNumberOfMolesInPhase();
          dH = (newEnt - hinn) / isentropicEfficiency;
        }
      }
      double hout = hinn + dH;
      isentropicEfficiency = (newEnt - hinn) / dH;
      // TODO: the polytropic efficiency calculation here need to be corrected, it is
      // always larger
      // than isentropic efficiency
      polytropicEfficiency = isentropicEfficiency;
      dH = hout - hinn;
      thermoOps = new ThermodynamicOperations(getThermoSystem());
      thermoOps.PHflash(hout, 0);
      if (useGERG2008 && inStream.getThermoSystem().getNumberOfPhases() == 1) {
        thermoOps.PHflashGERG2008(hout);
      }
      if (useLeachman && inStream.getThermoSystem().getNumberOfPhases() == 1) {
        thermoOps.PHflashLeachman(hout);
      }
      if (useVega && inStream.getThermoSystem().getNumberOfPhases() == 1) {
        thermoOps.PHflashVega(hout);
      }
    }
    // thermoSystem.display();

    if (getCompressorChart().isUseCompressorChart() && getAntiSurge().isActive()) {
      thermoSystem.setTotalNumberOfMoles(orginalMolarFLow);
      thermoSystem.init(3);
    }
    thermoSystem.initProperties();
    outStream.setThermoSystem(getThermoSystem());
    outStream.setCalculationIdentifier(id);

    polytropicFluidHead =
        getPower() / getThermoSystem().getFlowRate("kg/sec") / 1000.0 * getPolytropicEfficiency();
    polytropicHeadMeter = polytropicFluidHead * 1000.0 / 9.81;
    actualCompressionRatio = getOutletPressure() / presinn;

    // Calculate polytropic exponent from actual compression process if not already
    // set
    // n = ln(P2/P1) / ln(rho2/rho1) - polytropic volume exponent
    if (polytropicExponent == 0 || !getCompressorChart().isUseCompressorChart()) {
      double densOut = getThermoSystem().getDensity("kg/m3");
      if (useGERG2008 && getThermoSystem().getNumberOfPhases() == 1) {
        densOut = getThermoSystem().getPhase(0).getDensity_GERG2008();
      } else if (useLeachman && getThermoSystem().getNumberOfPhases() == 1) {
        densOut = getThermoSystem().getPhase(0).getDensity_Leachman();
      } else if (useVega && getThermoSystem().getNumberOfPhases() == 1) {
        densOut = getThermoSystem().getPhase(0).getDensity_Vega();
      }
      double pressureRatioCalc = getOutletPressure() / presinn;
      double densityRatio = densOut / densInn;
      if (pressureRatioCalc > 1.0 && densityRatio > 1.0) {
        polytropicExponent = Math.log(pressureRatioCalc) / Math.log(densityRatio);
      }
    }

    setCalculationIdentifier(id);
  }

  private boolean useEnergyEfficiencyChart() {
    return useEnergyEfficiencyChart;
  }

  public void setUseEnergyEfficiencyChart(boolean useEnergyEfficiencyChart) {
    this.useEnergyEfficiencyChart = useEnergyEfficiencyChart;
  }

  /** {@inheritDoc} */
  @Override
  public void runTransient(double dt, UUID id) {
    if (getCalculateSteadyState()) {
      run(id);
      increaseTime(dt);
      return;
    }

    // Update compressor chart with inlet stream for automatic MW detection
    if (compressorChart != null) {
      compressorChart.setInletStream(inStream);
    }

    runController(dt, id);

    inStream.getThermoSystem().init(3);
    outStream.getThermoSystem().init(3);
    double head = (outStream.getThermoSystem().getEnthalpy("kJ/kg")
        - inStream.getThermoSystem().getEnthalpy("kJ/kg"));

    // Use state memory for better initial guess - improves reversibility
    double guessFlow;
    if (previousActualFlow > 0.0) {
      // Use previous converged solution as initial guess
      guessFlow = previousActualFlow;
    } else {
      // Fallback to current stream flow
      guessFlow = inStream.getFluid().getFlowRate("m3/hr");
    }

    double actualFlowRateNew = getCompressorChart().getFlow(head, getSpeed(), guessFlow);
    if (actualFlowRateNew < 0.0 || Double.isNaN(actualFlowRateNew)) {
      logger.error(
          "actual flow rate is negative or NaN and would lead to failure of calculation: actual flow rate "
              + actualFlowRateNew + ", using previous flow rate: " + guessFlow);
      actualFlowRateNew = guessFlow > 0.0 ? guessFlow : 1.0; // Use previous flow or fallback
    }

    // Store state for next iteration
    previousActualFlow = actualFlowRateNew;
    previousPolyHead = head;

    inStream.setFlowRate(actualFlowRateNew, "Am3/hr");

    inStream.getThermoSystem().init(3);
    inStream.getThermoSystem().initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
    inStream.run(id);
    inStream.getThermoSystem().init(3);

    outStream.setFlowRate(inStream.getFlowRate("kg/hr"), "kg/hr");
    outStream.run();
    outStream.getThermoSystem().init(3);

    inletEnthalpy = inStream.getFluid().getEnthalpy();
    thermoSystem = outStream.getThermoSystem().clone();
    thermoSystem.initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);

    polytropicEfficiency =
        compressorChart.getPolytropicEfficiency(inStream.getFlowRate("m3/hr"), speed) / 100.0;
    if (polytropicEfficiency <= 0.0) {
      polytropicEfficiency = 0.0001; // Prevent division by zero
    }
    polytropicFluidHead = head * polytropicEfficiency;
    dH = polytropicFluidHead * 1000.0 * thermoSystem.getMolarMass() / getPolytropicEfficiency()
        * inStream.getThermoSystem().getTotalNumberOfMoles();
    setCalculationIdentifier(id);
  }

  /**
   * <p>
   * generateCompressorCurves.
   * </p>
   */
  public void generateCompressorCurves() {
    double flowRef = getThermoSystem().getFlowRate("m3/hr");
    double factor = flowRef / 4000.0;
    double[] chartConditions = new double[] {0.3, 1.0, 1.0, 1.0};
    double[] speed = new double[] {12913, 12298, 11683, 11098, 10453, 9224, 8609, 8200};
    double[][] flow = new double[][] {
        {2789.1285, 3174.0375, 3689.2288, 4179.4503, 4570.2768, 4954.7728, 5246.0329, 5661.0331},
        {2571.1753, 2943.7254, 3440.2675, 3837.4448, 4253.0898, 4668.6643, 4997.1926, 5387.4952},
        {2415.3793, 2763.0706, 3141.7095, 3594.7436, 4047.6467, 4494.1889, 4853.7353, 5138.7858},
        {2247.2043, 2799.7342, 3178.3428, 3656.1551, 4102.778, 4394.1591, 4648.3224, 4840.4998},
        {2072.8397, 2463.9483, 2836.4078, 3202.5266, 3599.6333, 3978.0203, 4257.0022, 4517.345},
        {1835.9552, 2208.455, 2618.1322, 2940.8034, 3244.7852, 3530.1279, 3753.3738, 3895.9746},
        {1711.3386, 1965.8848, 2356.9431, 2685.9247, 3008.5154, 3337.2855, 3591.5092},
        {1636.5807, 2002.8708, 2338.0319, 2642.1245, 2896.4894, 3113.6264, 3274.8764, 3411.2977}};

    for (int i = 0; i < flow.length; i++) {
      for (int j = 0; j < flow[i].length; j++) {
        flow[i][j] *= factor;
      }
    }

    double[][] head =
        new double[][] {{80.0375, 78.8934, 76.2142, 71.8678, 67.0062, 60.6061, 53.0499, 39.728},
            {72.2122, 71.8369, 68.9009, 65.8341, 60.7167, 54.702, 47.2749, 35.7471},
            {65.1576, 64.5253, 62.6118, 59.1619, 54.0455, 47.0059, 39.195, 31.6387},
            {58.6154, 56.9627, 54.6647, 50.4462, 44.4322, 38.4144, 32.9084, 28.8109},
            {52.3295, 51.0573, 49.5283, 46.3326, 42.3685, 37.2502, 31.4884, 25.598},
            {40.6578, 39.6416, 37.6008, 34.6603, 30.9503, 27.1116, 23.2713, 20.4546},
            {35.2705, 34.6359, 32.7228, 31.0645, 27.0985, 22.7482, 18.0113},
            {32.192, 31.1756, 29.1329, 26.833, 23.8909, 21.3324, 18.7726, 16.3403},};

    for (int i = 0; i < head.length; i++) {
      for (int j = 0; j < head[i].length; j++) {
        head[i][j] *= factor / 5.0;
      }
    }
    double[][] polyEff = new double[][] {
        {77.2452238409573, 79.4154186459363, 80.737960012489, 80.5229826589649, 79.2210931638144,
            75.4719133864634, 69.6034181197298, 58.7322388482707},
        {77.0107837113504, 79.3069974136389, 80.8941189021135, 80.7190194665918, 79.5313242980328,
            75.5912622896367, 69.6846136362097, 60.0043057990909},
        {77.0043065299874, 79.1690958847856, 80.8038169975675, 80.6543975614197, 78.8532389102705,
            73.6664774270613, 66.2735600426727, 57.671664571658},
        {77.0716623789093, 80.4629750233093, 81.1390811169072, 79.6374242667478, 75.380928428817,
            69.5332969549779, 63.7997587622339, 58.8120614497758},
        {76.9705872525642, 79.8335492585324, 80.9468133671171, 80.5806471927835, 78.0462158225426,
            73.0403707523258, 66.5572286338589, 59.8624822515064},
        {77.5063036680357, 80.2056198362559, 81.0339108025933, 79.6085962687939, 76.3814534404405,
            70.8027503005902, 64.6437367160571, 60.5299349982342},
        {77.8175271586685, 80.065165942218, 81.0631362122632, 79.8955051771299, 76.1983240929369,
            69.289982774309, 60.8567149372229},
        {78.0924334304045, 80.9353551568667, 80.7904437766234, 78.8639325223295, 75.2170936751143,
            70.3105081673411, 65.5507568533569, 61.0391468300337}};

    getCompressorChart().setCurves(chartConditions, speed, flow, head, polyEff);
    getCompressorChart().setHeadUnit("kJ/kg");
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    DecimalFormat nf = new DecimalFormat();
    nf.setMaximumFractionDigits(5);
    nf.applyPattern("#.#####E0");

    JDialog dialog = new JDialog(new JFrame(), "Results from TPflash");
    Container dialogContentPane = dialog.getContentPane();
    dialogContentPane.setLayout(new FlowLayout());

    getThermoSystem().initPhysicalProperties();
    String[][] table = new String[50][5];
    String[] names = {"", "Phase 1", "Phase 2", "Phase 3", "Unit"};
    table[0][0] = "";
    table[0][1] = "";
    table[0][2] = "";
    table[0][3] = "";
    StringBuffer buf = new StringBuffer();
    FieldPosition test = new FieldPosition(0);

    for (int i = 0; i < getThermoSystem().getNumberOfPhases(); i++) {
      for (int j = 0; j < getThermoSystem().getPhases()[0].getNumberOfComponents(); j++) {
        table[j + 1][0] = getThermoSystem().getPhases()[0].getComponent(j).getName();
        buf = new StringBuffer();
        table[j + 1][i + 1] = nf
            .format(getThermoSystem().getPhases()[i].getComponent(j).getx(), buf, test).toString();
        table[j + 1][4] = "[-]";
      }
      buf = new StringBuffer();
      table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 2][0] = "Density";
      table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 2][i + 1] = nf
          .format(getThermoSystem().getPhases()[i].getPhysicalProperties().getDensity(), buf, test)
          .toString();
      table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 2][4] = "[kg/m^3]";

      // Double.longValue(thermoSystem.getPhases()[i].getBeta());
      buf = new StringBuffer();
      table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 3][0] = "PhaseFraction";
      table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 3][i + 1] =
          nf.format(getThermoSystem().getPhases()[i].getBeta(), buf, test).toString();
      table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 3][4] = "[-]";

      buf = new StringBuffer();
      table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 4][0] = "MolarMass";
      table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 4][i + 1] =
          nf.format(getThermoSystem().getPhases()[i].getMolarMass() * 1000, buf, test).toString();
      table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 4][4] = "[kg/kmol]";

      buf = new StringBuffer();
      table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 5][0] = "Cp";
      table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 5][i + 1] =
          nf.format((getThermoSystem().getPhases()[i].getCp()
              / getThermoSystem().getPhases()[i].getNumberOfMolesInPhase() * 1.0
              / getThermoSystem().getPhases()[i].getMolarMass() * 1000), buf, test).toString();
      table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 5][4] = "[kJ/kg*K]";

      buf = new StringBuffer();
      table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 7][0] = "Viscosity";
      table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 7][i + 1] =
          nf.format((getThermoSystem().getPhases()[i].getPhysicalProperties().getViscosity()), buf,
              test).toString();
      table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 7][4] = "[kg/m*sec]";

      buf = new StringBuffer();
      table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 8][0] = "Conductivity";
      table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 8][i + 1] =
          nf.format(getThermoSystem().getPhases()[i].getPhysicalProperties().getConductivity(), buf,
              test).toString();
      table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 8][4] = "[W/m*K]";

      buf = new StringBuffer();
      table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 10][0] = "Pressure";
      table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 10][i + 1] =
          Double.toString(getThermoSystem().getPhases()[i].getPressure());
      table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 10][4] = "[bar]";

      buf = new StringBuffer();
      table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 11][0] = "Temperature";
      table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 11][i + 1] =
          Double.toString(getThermoSystem().getPhases()[i].getTemperature());
      table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 11][4] = "[K]";
      Double.toString(getThermoSystem().getPhases()[i].getTemperature());

      buf = new StringBuffer();
      table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 13][0] = "Stream";
      table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 13][i + 1] = name;
      table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 13][4] = "-";
    }

    JTable Jtab = new JTable(table, names);
    JScrollPane scrollpane = new JScrollPane(Jtab);
    dialogContentPane.add(scrollpane);
    dialog.pack();
    dialog.setVisible(true);
  }

  /** {@inheritDoc} */
  @Override
  public String[][] getResultTable() {
    return thermoSystem.getResultTable();
  }

  /**
   * <p>
   * getTotalWork.
   * </p>
   *
   * @return a double
   */
  public double getTotalWork() {
    double multi = 1.0;
    if (getAntiSurge().isActive()) {
      multi = 1.0 + getAntiSurge().getCurrentSurgeFraction();
    }
    if (useGERG2008 && inStream.getThermoSystem().getNumberOfPhases() == 1) {
      double[] gergProps;
      gergProps = getThermoSystem().getPhase(0).getProperties_GERG2008();
      double enth = gergProps[7] * getThermoSystem().getPhase(0).getNumberOfMolesInPhase();
      return (enth - inletEnthalpy) * multi;
    } else if (useLeachman && inStream.getThermoSystem().getNumberOfPhases() == 1) {
      double[] LeachmanProps;
      LeachmanProps = getThermoSystem().getPhase(0).getProperties_Leachman();
      double enth = LeachmanProps[7] * getThermoSystem().getPhase(0).getNumberOfMolesInPhase();
      return (enth - inletEnthalpy) * multi;
    } else if (useVega && inStream.getThermoSystem().getNumberOfPhases() == 1) {
      double[] VegaProps;
      VegaProps = getThermoSystem().getPhase(0).getProperties_Vega();
      double enth = VegaProps[7] * getThermoSystem().getPhase(0).getNumberOfMolesInPhase();
      return (enth - inletEnthalpy) * multi;
    } else {
      return multi * (getThermoSystem().getEnthalpy() - inletEnthalpy);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getIsentropicEfficiency() {
    return isentropicEfficiency;
  }

  /** {@inheritDoc} */
  @Override
  public void setIsentropicEfficiency(double isentropicEfficiency) {
    this.isentropicEfficiency = isentropicEfficiency;
  }

  /**
   * <p>
   * usePolytropicCalc.
   * </p>
   *
   * @return the usePolytropicCalc
   */
  public boolean usePolytropicCalc() {
    return usePolytropicCalc;
  }

  /**
   * <p>
   * Setter for the field <code>usePolytropicCalc</code>.
   * </p>
   *
   * @param usePolytropicCalc the usePolytropicCalc to set
   */
  public void setUsePolytropicCalc(boolean usePolytropicCalc) {
    this.usePolytropicCalc = usePolytropicCalc;
  }

  /** {@inheritDoc} */
  @Override
  public double getPolytropicEfficiency() {
    return polytropicEfficiency;
  }

  /** {@inheritDoc} */
  @Override
  public void setPolytropicEfficiency(double polytropicEfficiency) {
    this.polytropicEfficiency = polytropicEfficiency;
  }

  /**
   * Get the temperature tolerance used for efficiency solving iterations.
   *
   * @return the efficiency solve tolerance in Kelvin
   */
  public double getEfficiencySolveTolerance() {
    return efficiencySolveTolerance;
  }

  /**
   * Set the temperature tolerance used for efficiency solving iterations. A larger tolerance
   * provides faster convergence when using computationally expensive equations of state like
   * GERG-2008, while still providing good engineering accuracy.
   *
   * @param tolerance the temperature tolerance in Kelvin (default is 0.01 K)
   */
  public void setEfficiencySolveTolerance(double tolerance) {
    this.efficiencySolveTolerance = tolerance;
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface getThermoSystem() {
    return thermoSystem;
  }

  /**
   * <p>
   * Getter for the field <code>compressorChart</code>.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.compressor.CompressorChartInterface} object
   */
  public CompressorChartInterface getCompressorChart() {
    return compressorChart;
  }

  /**
   * <p>
   * Setter for the field <code>compressorChart</code>.
   * </p>
   *
   * @param compressorChart a {@link neqsim.process.equipment.compressor.CompressorChartInterface}
   *        object
   */
  public void setCompressorChart(CompressorChartInterface compressorChart) {
    this.compressorChart = compressorChart;
  }

  /**
   * Save compressor chart to a CSV file.
   *
   * <p>
   * The CSV format is compatible with {@link CompressorChartReader} for loading:
   * </p>
   * 
   * <pre>
   * speed;flow;head;polyEff
   * 2000.00;9598.75;33.36;78.30
   * ...
   * </pre>
   *
   * @param filePath the path to save the CSV file
   * @throws java.io.IOException if file cannot be written
   */
  public void saveCompressorChartToCsv(String filePath) throws java.io.IOException {
    if (compressorChart == null || !compressorChart.isUseCompressorChart()) {
      throw new IllegalStateException("No compressor chart available to save");
    }

    double[] speeds = compressorChart.getSpeeds();
    double[][] flows = compressorChart.getFlows();
    double[][] heads = compressorChart.getHeads();
    double[][] efficiencies = compressorChart.getPolytropicEfficiencies();

    try (java.io.PrintWriter writer = new java.io.PrintWriter(filePath)) {
      // Write header
      writer.println("speed;flow;head;polyEff");

      // Write data points for each speed curve
      for (int i = 0; i < speeds.length; i++) {
        if (flows != null && flows[i] != null) {
          for (int j = 0; j < flows[i].length; j++) {
            writer.println(String.format(java.util.Locale.US, "%.2f;%.2f;%.2f;%.2f", speeds[i],
                flows[i][j], heads[i][j], efficiencies[i][j]));
          }
        }
      }
    }
  }

  /**
   * Save compressor chart to a JSON file.
   *
   * <p>
   * The JSON format includes metadata (name, head unit, max design power) and is compatible with
   * {@link CompressorChartJsonReader} for loading:
   * </p>
   * 
   * <pre>
   * {
   *   "compressorName": "Compressor Name",
   *   "headUnit": "kJ/kg",
   *   "maxDesignPower_kW": 16619.42,
   *   "speedCurves": [...]
   * }
   * </pre>
   *
   * @param filePath the path to save the JSON file
   * @throws java.io.IOException if file cannot be written
   */
  public void saveCompressorChartToJson(String filePath) throws java.io.IOException {
    if (compressorChart == null || !compressorChart.isUseCompressorChart()) {
      throw new IllegalStateException("No compressor chart available to save");
    }

    double[] speeds = compressorChart.getSpeeds();
    double[][] flows = compressorChart.getFlows();
    double[][] heads = compressorChart.getHeads();
    double[][] efficiencies = compressorChart.getPolytropicEfficiencies();

    try (java.io.PrintWriter writer = new java.io.PrintWriter(filePath)) {
      StringBuilder json = new StringBuilder();
      json.append("{\n");
      json.append("  \"compressorName\": \"").append(getName()).append("\",\n");
      json.append("  \"headUnit\": \"").append(compressorChart.getHeadUnit()).append("\",\n");
      json.append("  \"maxDesignPower_kW\": ").append(getMechanicalDesign().maxDesignPower)
          .append(",\n");
      json.append("  \"speedCurves\": [\n");

      for (int i = 0; i < speeds.length; i++) {
        json.append("    {\n");
        json.append("      \"speed_rpm\": ").append(speeds[i]).append(",\n");
        json.append("      \"flow_m3h\": [");
        for (int j = 0; j < flows[i].length; j++) {
          json.append(String.format(java.util.Locale.US, "%.2f", flows[i][j]));
          if (j < flows[i].length - 1) {
            json.append(", ");
          }
        }
        json.append("],\n");
        json.append("      \"head_kJkg\": [");
        for (int j = 0; j < heads[i].length; j++) {
          json.append(String.format(java.util.Locale.US, "%.2f", heads[i][j]));
          if (j < heads[i].length - 1) {
            json.append(", ");
          }
        }
        json.append("],\n");
        json.append("      \"polytropicEfficiency_pct\": [");
        for (int j = 0; j < efficiencies[i].length; j++) {
          json.append(String.format(java.util.Locale.US, "%.2f", efficiencies[i][j]));
          if (j < efficiencies[i].length - 1) {
            json.append(", ");
          }
        }
        json.append("]\n");
        json.append("    }");
        if (i < speeds.length - 1) {
          json.append(",");
        }
        json.append("\n");
      }
      json.append("  ]\n");
      json.append("}");
      writer.print(json.toString());
    }
  }

  /**
   * Load compressor chart from a CSV file.
   *
   * <p>
   * The CSV format should match the output of {@link #saveCompressorChartToCsv(String)}:
   * </p>
   * 
   * <pre>
   * speed;flow;head;polyEff
   * 2000.00;9598.75;33.36;78.30
   * ...
   * </pre>
   *
   * @param filePath the path to the CSV file
   * @throws Exception if file cannot be read or parsed
   */
  public void loadCompressorChartFromCsv(String filePath) throws Exception {
    CompressorChartReader reader = new CompressorChartReader(filePath);
    reader.setCurvesToCompressor(this);
    compressorChart.setUseCompressorChart(true);
  }

  /**
   * Load compressor chart from a JSON file.
   *
   * <p>
   * The JSON format should match the output of {@link #saveCompressorChartToJson(String)}. This
   * method also restores the maxDesignPower if present in the JSON file.
   * </p>
   *
   * @param filePath the path to the JSON file
   * @throws Exception if file cannot be read or parsed
   */
  public void loadCompressorChartFromJson(String filePath) throws Exception {
    CompressorChartJsonReader reader = new CompressorChartJsonReader(filePath);
    reader.setCurvesToCompressor(this);
    compressorChart.setUseCompressorChart(true);
  }

  /**
   * Load compressor chart from a JSON string.
   *
   * @param jsonString the JSON string containing chart data
   * @throws Exception if JSON cannot be parsed
   */
  public void loadCompressorChartFromJsonString(String jsonString) throws Exception {
    CompressorChartJsonReader reader = new CompressorChartJsonReader(jsonString, true);
    reader.setCurvesToCompressor(this);
    compressorChart.setUseCompressorChart(true);
  }

  /**
   * Get the compressor chart as a JSON string.
   *
   * @return JSON string representation of the compressor chart
   */
  public String getCompressorChartAsJson() {
    if (compressorChart == null || !compressorChart.isUseCompressorChart()) {
      return "{}";
    }

    double[] speeds = compressorChart.getSpeeds();
    double[][] flows = compressorChart.getFlows();
    double[][] heads = compressorChart.getHeads();
    double[][] efficiencies = compressorChart.getPolytropicEfficiencies();

    StringBuilder json = new StringBuilder();
    json.append("{\n");
    json.append("  \"compressorName\": \"").append(getName()).append("\",\n");
    json.append("  \"chartType\": \"").append(compressorChart.getClass().getSimpleName())
        .append("\",\n");
    json.append("  \"headUnit\": \"").append(compressorChart.getHeadUnit()).append("\",\n");
    json.append("  \"maxDesignPower_kW\": ").append(getMechanicalDesign().maxDesignPower)
        .append(",\n");

    // Reference conditions
    json.append("  \"referenceConditions\": {\n");
    double[] conditions = compressorChart.getChartConditions();
    if (conditions != null && conditions.length >= 4) {
      json.append("    \"molecularWeight\": ").append(conditions[0]).append(",\n");
      json.append("    \"temperature_K\": ").append(conditions[1]).append(",\n");
      json.append("    \"pressure_bara\": ").append(conditions[2]).append(",\n");
      json.append("    \"compressibilityZ\": ").append(conditions[3]).append("\n");
    }
    json.append("  },\n");

    // Speed curves
    json.append("  \"speedCurves\": [\n");
    if (speeds != null) {
      for (int i = 0; i < speeds.length; i++) {
        json.append("    {\n");
        json.append("      \"speed_rpm\": ").append(speeds[i]).append(",\n");

        json.append("      \"flow_m3h\": [");
        if (flows != null && flows[i] != null) {
          for (int j = 0; j < flows[i].length; j++) {
            json.append(String.format(java.util.Locale.US, "%.2f", flows[i][j]));
            if (j < flows[i].length - 1) {
              json.append(", ");
            }
          }
        }
        json.append("],\n");

        json.append("      \"head_kJkg\": [");
        if (heads != null && heads[i] != null) {
          for (int j = 0; j < heads[i].length; j++) {
            json.append(String.format(java.util.Locale.US, "%.2f", heads[i][j]));
            if (j < heads[i].length - 1) {
              json.append(", ");
            }
          }
        }
        json.append("],\n");

        json.append("      \"polytropicEfficiency_pct\": [");
        if (efficiencies != null && efficiencies[i] != null) {
          for (int j = 0; j < efficiencies[i].length; j++) {
            json.append(String.format(java.util.Locale.US, "%.2f", efficiencies[i][j]));
            if (j < efficiencies[i].length - 1) {
              json.append(", ");
            }
          }
        }
        json.append("]\n");

        json.append("    }");
        if (i < speeds.length - 1) {
          json.append(",");
        }
        json.append("\n");
      }
    }
    json.append("  ],\n");

    // Surge and stonewall info
    json.append("  \"surgeCurve\": { \"active\": ").append(compressorChart.getSurgeCurve() != null)
        .append(" },\n");
    json.append("  \"stonewallCurve\": { \"active\": ")
        .append(compressorChart.getStoneWallCurve() != null
            && compressorChart.getStoneWallCurve().isActive())
        .append(" }\n");

    json.append("}");

    return json.toString();
  }

  /** {@inheritDoc} */
  @Override
  public AntiSurge getAntiSurge() {
    return antiSurge;
  }

  /**
   * <p>
   * isSurge.
   * </p>
   *
   * @param flow a double
   * @param head a double
   * @return a boolean
   */
  public boolean isSurge(double flow, double head) {
    getAntiSurge().setSurge(getCompressorChart().getSurgeCurve().isSurge(flow, head));
    return getAntiSurge().isSurge();
  }

  /** {@inheritDoc} */
  @Override
  public double getDistanceToSurge() {
    // For single speed compressors, surge curve is not active, so use
    // getSurgeFlowAtSpeed
    if (!getCompressorChart().getSurgeCurve().isActive()) {
      double surgeFlowAtSpeed = getCompressorChart().getSurgeFlowAtSpeed(getSpeed());
      if (surgeFlowAtSpeed > 0) {
        return getInletStream().getFlowRate("m3/hr") / surgeFlowAtSpeed - 1.0;
      }
      return Double.POSITIVE_INFINITY; // No surge data available
    }
    // For multi-speed compressors, use the surge curve interpolation
    return getInletStream().getFlowRate("m3/hr")
        / getCompressorChart().getSurgeCurve().getSurgeFlow(getPolytropicFluidHead()) - 1;
  }

  /** {@inheritDoc} */
  @Override
  public double getSurgeFlowRateMargin() {
    return getInletStream().getFlowRate("m3/hr")
        - getCompressorChart().getSurgeCurve().getSurgeFlow(getPolytropicFluidHead());
  }

  /** {@inheritDoc} */
  @Override
  public double getSurgeFlowRate() {
    return getCompressorChart().getSurgeCurve().getSurgeFlow(getPolytropicFluidHead());
  }

  /** {@inheritDoc} */
  @Override
  public double getSurgeFlowRateStd() {
    return getCompressorChart().getSurgeCurve().getSurgeFlow(getPolytropicFluidHead())
        * getInletPressure() / 1.01325 * 288.15 / getInletTemperature() * 1.0
        / getInletStream().getFluid().getZvolcorr();
  }

  @Override
  public boolean isStoneWall() {
    StoneWallCurve stoneWallCurve = getCompressorChart().getStoneWallCurve();
    if (stoneWallCurve == null || !stoneWallCurve.isActive()) {
      return false;
    }
    double head = getPolytropicFluidHead();
    double flow = getInletStream().getFlowRate("m3/hr");
    if (Double.isNaN(head) || Double.isNaN(flow)) {
      return false;
    }
    return stoneWallCurve.isStoneWall(head, flow);
  }

  /**
   * Calculate the distance to the stone wall (choke) limit.
   * 
   * <p>
   * Returns a positive value indicating the percentage margin to stone wall. For example, 0.5 means
   * the stone wall is 50% above the current flow rate.
   * </p>
   * 
   * <p>
   * For single speed compressors where the stone wall curve is not active, this method uses the
   * maximum flow point at the current speed. For multi-speed compressors, it uses the stone wall
   * curve interpolation.
   * </p>
   *
   * @return distance to stone wall as a ratio (stone wall flow / current flow - 1)
   */
  public double getDistanceToStoneWall() {
    // For single speed compressors, stone wall curve is not active, so use
    // getStoneWallFlowAtSpeed
    if (!getCompressorChart().getStoneWallCurve().isActive()) {
      double stoneWallFlowAtSpeed = getCompressorChart().getStoneWallFlowAtSpeed(getSpeed());
      if (stoneWallFlowAtSpeed > 0) {
        return stoneWallFlowAtSpeed / getInletStream().getFlowRate("m3/hr") - 1.0;
      }
      return Double.POSITIVE_INFINITY; // No stone wall data available
    }
    // For multi-speed compressors, use the stone wall curve interpolation
    double stoneWallFlow =
        getCompressorChart().getStoneWallCurve().getStoneWallFlow(getPolytropicFluidHead());
    if (stoneWallFlow > 0) {
      return stoneWallFlow / getInletStream().getFlowRate("m3/hr") - 1.0;
    }
    return Double.POSITIVE_INFINITY;
  }

  /**
   * <p>
   * isStoneWall.
   * </p>
   *
   * @param flow a double
   * @param head a double
   * @return a boolean
   */
  public boolean isStoneWall(double flow, double head) {
    return getCompressorChart().getStoneWallCurve().isStoneWall(flow, head);
  }

  /**
   * <p>
   * Setter for the field <code>antiSurge</code>.
   * </p>
   *
   * @param antiSurge a {@link neqsim.process.equipment.compressor.AntiSurge} object
   */
  public void setAntiSurge(AntiSurge antiSurge) {
    this.antiSurge = antiSurge;
  }

  /**
   * <p>
   * Get the safety-factor-corrected surge flow and head at the current compressor speed.
   * </p>
   * <p>
   * This method returns the safe minimum operating point by applying the surge control factor
   * (typically 1.05 for 5% margin) to the surge flow at the current speed. The head is calculated
   * at this safe flow rate using the compressor chart. This is particularly useful for single speed
   * compressors where speed cannot be adjusted to move away from surge.
   * </p>
   *
   * @return A double array with two elements: [0] = safe surge flow (m3/hr), [1] = head at safe
   *         flow (kJ/kg or meter depending on chart headUnit)
   */
  public double[] getSafetyFactorCorrectedFlowHeadAtCurrentSpeed() {
    double currentSpeed = getSpeed();
    double surgeFlow = getCompressorChart().getSurgeFlowAtSpeed(currentSpeed);
    double surgeControlFactor = getAntiSurge().getSurgeControlFactor();

    // Apply safety factor to surge flow
    double safeFlow = surgeFlow * surgeControlFactor;

    // Get head at the safe flow rate
    double safeHead = getCompressorChart().getPolytropicHead(safeFlow, currentSpeed);

    return new double[] {safeFlow, safeHead};
  }

  /**
   * <p>
   * Getter for the field <code>speed</code>.
   * </p>
   *
   * @return a double
   */
  public double getSpeed() {
    return speed;
  }

  /**
   * Check if the current compressor speed is higher than the maximum speed in the compressor
   * curves.
   *
   * <p>
   * This method is useful for detecting when the compressor is operating outside its design
   * envelope (requires speed extrapolation beyond the defined curves).
   * </p>
   *
   * @return true if the current speed exceeds the maximum curve speed, false otherwise
   */
  public boolean isHigherThanMaxSpeed() {
    if (getCompressorChart() == null) {
      return false;
    }
    return getCompressorChart().isHigherThanMaxSpeed(getSpeed());
  }

  /**
   * Check if a calculated speed is higher than the maximum speed in the compressor curves.
   *
   * @param calculatedSpeed the speed to check in RPM
   * @return true if the calculated speed exceeds the maximum curve speed, false otherwise
   */
  public boolean isHigherThanMaxSpeed(double calculatedSpeed) {
    if (getCompressorChart() == null) {
      return false;
    }
    return getCompressorChart().isHigherThanMaxSpeed(calculatedSpeed);
  }

  /**
   * Get the ratio of the current compressor speed to the maximum speed in the compressor curves.
   *
   * <p>
   * A ratio greater than 1.0 indicates the speed exceeds the maximum curve speed.
   * </p>
   *
   * @return the ratio speed/maxSpeedCurve (dimensionless), or NaN if chart not set
   */
  public double getRatioToMaxSpeed() {
    if (getCompressorChart() == null) {
      return Double.NaN;
    }
    return getCompressorChart().getRatioToMaxSpeed(getSpeed());
  }

  /**
   * Get the ratio of a calculated speed to the maximum speed in the compressor curves.
   *
   * @param calculatedSpeed the speed to compare in RPM
   * @return the ratio calculatedSpeed/maxSpeedCurve (dimensionless), or NaN if chart not set
   */
  public double getRatioToMaxSpeed(double calculatedSpeed) {
    if (getCompressorChart() == null) {
      return Double.NaN;
    }
    return getCompressorChart().getRatioToMaxSpeed(calculatedSpeed);
  }

  /**
   * Check if the current compressor speed is lower than the minimum speed in the compressor curves.
   *
   * <p>
   * This method is useful for detecting when the compressor is operating below its design envelope
   * (requires speed extrapolation below the defined curves), which may indicate turndown issues.
   * </p>
   *
   * @return true if the current speed is below the minimum curve speed, false otherwise
   */
  public boolean isLowerThanMinSpeed() {
    if (getCompressorChart() == null) {
      return false;
    }
    return getCompressorChart().isLowerThanMinSpeed(getSpeed());
  }

  /**
   * Check if a calculated speed is lower than the minimum speed in the compressor curves.
   *
   * @param calculatedSpeed the speed to check in RPM
   * @return true if the calculated speed is below the minimum curve speed, false otherwise
   */
  public boolean isLowerThanMinSpeed(double calculatedSpeed) {
    if (getCompressorChart() == null) {
      return false;
    }
    return getCompressorChart().isLowerThanMinSpeed(calculatedSpeed);
  }

  /**
   * Get the ratio of the current compressor speed to the minimum speed in the compressor curves.
   *
   * <p>
   * A ratio less than 1.0 indicates the speed is below the minimum curve speed.
   * </p>
   *
   * @return the ratio speed/minSpeedCurve (dimensionless), or NaN if chart not set
   */
  public double getRatioToMinSpeed() {
    if (getCompressorChart() == null) {
      return Double.NaN;
    }
    return getCompressorChart().getRatioToMinSpeed(getSpeed());
  }

  /**
   * Get the ratio of a calculated speed to the minimum speed in the compressor curves.
   *
   * @param calculatedSpeed the speed to compare in RPM
   * @return the ratio calculatedSpeed/minSpeedCurve (dimensionless), or NaN if chart not set
   */
  public double getRatioToMinSpeed(double calculatedSpeed) {
    if (getCompressorChart() == null) {
      return Double.NaN;
    }
    return getCompressorChart().getRatioToMinSpeed(calculatedSpeed);
  }

  /**
   * Check if the current compressor speed is within the defined compressor curve speed range.
   *
   * @return true if the speed is within [minSpeedCurve, maxSpeedCurve], false otherwise
   */
  public boolean isSpeedWithinRange() {
    if (getCompressorChart() == null) {
      return true; // No chart means no range constraints
    }
    return getCompressorChart().isSpeedWithinRange(getSpeed());
  }

  /**
   * Check if a calculated speed is within the defined compressor curve speed range.
   *
   * @param calculatedSpeed the speed to check in RPM
   * @return true if the speed is within [minSpeedCurve, maxSpeedCurve], false otherwise
   */
  public boolean isSpeedWithinRange(double calculatedSpeed) {
    if (getCompressorChart() == null) {
      return true; // No chart means no range constraints
    }
    return getCompressorChart().isSpeedWithinRange(calculatedSpeed);
  }

  /**
   * <p>
   * Setter for the field <code>speed</code>.
   * </p>
   *
   * @param speed a int
   */
  public void setSpeed(double speed) {
    if (Math.abs(this.speed - speed) > 1.0) {
      // Speed changed significantly - reset state memory to avoid using
      // flow rates from previous operating point as initial guess
      previousActualFlow = 0.0;
      previousPolyHead = 0.0;
    }
    this.speed = speed;
  }

  /**
   * <p>
   * Getter for the field <code>polytropicHead</code>.
   * </p>
   *
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public double getPolytropicHead(String unit) {
    if (unit.equals("kJ/kg")) {
      return polytropicFluidHead;
    } else if (unit.equals("meter")) {
      return polytropicHeadMeter;
    } else {
      return polytropicHead;
    }
  }

  /**
   * <p>
   * Getter for the field <code>polytropicHead</code>.
   * </p>
   *
   * @return a double
   */
  public double getPolytropicHead() {
    return polytropicHead;
  }

  /**
   * <p>
   * Getter for the field <code>polytropicFluidHead</code>.
   * </p>
   *
   * @return a double
   */
  public double getPolytropicFluidHead() {
    return polytropicFluidHead;
  }

  /**
   * <p>
   * Getter for the field <code>polytropicExponent</code>.
   * </p>
   *
   * @return a double
   */
  public double getPolytropicExponent() {
    return polytropicExponent;
  }

  /**
   * <p>
   * Getter for the field <code>polytropicHeadMeter</code>.
   * </p>
   *
   * @return a double
   */
  public double getPolytropicHeadMeter() {
    return polytropicHeadMeter;
  }

  /**
   * <p>
   * Setter for the field <code>polytropicHeadMeter</code>.
   * </p>
   *
   * @param polytropicHeadMeter a double
   */
  public void setPolytropicHeadMeter(double polytropicHeadMeter) {
    this.polytropicHeadMeter = polytropicHeadMeter;
  }

  /**
   * <p>
   * Getter for the field <code>outTemperature</code>.
   * </p>
   *
   * @return outlet temperature in Kelvin
   */
  public double getOutTemperature() {
    if (useOutTemperature) {
      return outTemperature;
    } else {
      return getThermoSystem().getTemperature();
    }
  }

  /**
   * <p>
   * Set the outlet temperature of the compressor.
   * </p>
   *
   * @param outTemperature outlet temperature in Kelvin
   */
  @Override
  public void setOutletTemperature(double outTemperature) {
    useOutTemperature = true;
    this.outTemperature = outTemperature;
  }

  /**
   * <p>
   * Set the outlet temperature of the compressor with unit specification.
   * </p>
   *
   * @param temperature outlet temperature value
   * @param unit temperature unit (e.g., "K", "C", "R", "F")
   */
  @Override
  public void setOutletTemperature(double temperature, String unit) {
    useOutTemperature = true;
    if (unit.equalsIgnoreCase("K") || unit.equalsIgnoreCase("Kelvin")) {
      this.outTemperature = temperature;
    } else if (unit.equalsIgnoreCase("C") || unit.equalsIgnoreCase("Celsius")) {
      this.outTemperature = temperature + 273.15;
    } else if (unit.equalsIgnoreCase("F") || unit.equalsIgnoreCase("Fahrenheit")) {
      this.outTemperature = (temperature - 32.0) * 5.0 / 9.0 + 273.15;
    } else if (unit.equalsIgnoreCase("R") || unit.equalsIgnoreCase("Rankine")) {
      this.outTemperature = temperature * 5.0 / 9.0;
    } else {
      this.outTemperature = temperature;
    }
  }

  /**
   * <p>
   * Setter for the field <code>outTemperature</code>.
   * </p>
   *
   * @param outTemperature outlet temperature in Kelvin
   * @deprecated use {@link #setOutletTemperature(double)} instead
   */
  @Deprecated
  public void setOutTemperature(double outTemperature) {
    setOutletTemperature(outTemperature);
  }

  /**
   * <p>
   * useOutTemperature.
   * </p>
   *
   * @param useOutTemperature a boolean
   */
  public void useOutTemperature(boolean useOutTemperature) {
    this.useOutTemperature = useOutTemperature;
  }

  /**
   * <p>
   * Getter for the field <code>numberOfCompressorCalcSteps</code>.
   * </p>
   *
   * @return the number of calculation steps in compressor
   */
  public int getNumberOfCompressorCalcSteps() {
    return numberOfCompressorCalcSteps;
  }

  /**
   * <p>
   * Setter for the field <code>numberOfCompressorCalcSteps</code>.
   * </p>
   *
   * @param numberOfCompressorCalcSteps a int
   */
  public void setNumberOfCompressorCalcSteps(int numberOfCompressorCalcSteps) {
    this.numberOfCompressorCalcSteps = numberOfCompressorCalcSteps;
  }

  /**
   * <p>
   * isUseRigorousPolytropicMethod.
   * </p>
   *
   * @return a boolean
   */
  public boolean isUseRigorousPolytropicMethod() {
    return useRigorousPolytropicMethod;
  }

  /**
   * <p>
   * Setter for the field <code>useRigorousPolytropicMethod</code>.
   * </p>
   *
   * @param useRigorousPolytropicMethod a boolean
   */
  public void setUseRigorousPolytropicMethod(boolean useRigorousPolytropicMethod) {
    this.useRigorousPolytropicMethod = useRigorousPolytropicMethod;
  }

  /** {@inheritDoc} */
  @Override
  public void setPressure(double pressure) {
    setOutletPressure(pressure);
  }

  /**
   * <p>
   * Setter for the field <code>pressure</code>.
   * </p>
   *
   * @param pressure a double
   * @param unit a {@link java.lang.String} object
   */
  public void setPressure(double pressure, String unit) {
    setOutletPressure(pressure);
    pressureUnit = unit;
  }

  /** {@inheritDoc} */
  @Override
  public double getEntropyProduction(String unit) {
    return outStream.getThermoSystem().getEntropy(unit)
        - inStream.getThermoSystem().getEntropy(unit);
  }

  /** {@inheritDoc} */
  @Override
  public double getExergyChange(String unit, double surroundingTemperature) {
    return outStream.getThermoSystem().getExergy(surroundingTemperature, unit)
        - inStream.getThermoSystem().getExergy(surroundingTemperature, unit);
  }

  /**
   * <p>
   * Getter for the field <code>polytropicMethod</code>.
   * </p>
   *
   * @return a {@link java.lang.String} object
   */
  public String getPolytropicMethod() {
    return polytropicMethod;
  }

  /**
   * <p>
   * Setter for the field <code>polytropicMethod</code>.
   * </p>
   *
   * @param polytropicMethod a {@link java.lang.String} object
   */
  public void setPolytropicMethod(String polytropicMethod) {
    this.polytropicMethod = polytropicMethod;
  }

  /**
   * Getter for property useGERG2008.
   *
   * @return Value
   */
  public boolean isUseGERG2008() {
    return useGERG2008;
  }

  /**
   * Setter for property useGERG2008.
   *
   * @param useGERG2008 Value to set
   */
  public void setUseGERG2008(boolean useGERG2008) {
    this.useGERG2008 = useGERG2008;
  }

  /**
   * Getter for property useLeachman.
   *
   * @return Value
   */
  public boolean isUseLeachman() {
    return useLeachman;
  }

  /**
   * Setter for property useLeachman.
   *
   * @param useLeachman Value to set
   */
  public void setUseLeachman(boolean useLeachman) {
    this.useLeachman = useLeachman;
  }

  /**
   * Getter for property useVega.
   *
   * @return Value
   */
  public boolean isUseVega() {
    return useVega;
  }

  /**
   * Setter for property useVega.
   *
   * @param useVega Value to set
   */
  public void setUseVega(boolean useVega) {
    this.useVega = useVega;
  }

  /**
   * <p>
   * Getter for the field <code>propertyProfile</code>.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.compressor.CompressorPropertyProfile} object
   */
  public CompressorPropertyProfile getPropertyProfile() {
    return propertyProfile;
  }

  /**
   * <p>
   * Setter for the field <code>propertyProfile</code>.
   * </p>
   *
   * @param propertyProfile a {@link neqsim.process.equipment.compressor.CompressorPropertyProfile}
   *        object
   */
  public void setPropertyProfile(CompressorPropertyProfile propertyProfile) {
    this.propertyProfile = propertyProfile;
  }

  /**
   * <p>
   * runController.
   * </p>
   *
   * @param dt a double
   * @param id Calculation identifier
   */
  public void runController(double dt, UUID id) {
    if (hasController && getController().isActive()) {
      getController().runTransient(this.speed, dt, id);
      this.speed = getController().getResponse();
      if (this.speed > maxspeed) {
        this.speed = maxspeed;
      }
      if (this.speed < minspeed) {
        this.speed = minspeed;
      }
      // System.out.println("valve opening " + this.percentValveOpening + " %");
    }
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + Objects.hash(antiSurge, compressorChart, dH, inletEnthalpy, inStream,
        isentropicEfficiency, numberOfCompressorCalcSteps, outStream, outTemperature,
        polytropicEfficiency, polytropicExponent, polytropicFluidHead, polytropicHead,
        polytropicHeadMeter, polytropicMethod, powerSet, pressure, pressureUnit, speed,
        thermoSystem, useGERG2008, useLeachman, useVega, useOutTemperature, usePolytropicCalc,
        useRigorousPolytropicMethod);
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!super.equals(obj)) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    Compressor other = (Compressor) obj;
    return Objects.equals(antiSurge, other.antiSurge)
        && Objects.equals(compressorChart, other.compressorChart)
        && Double.doubleToLongBits(dH) == Double.doubleToLongBits(other.dH)
        && Double.doubleToLongBits(inletEnthalpy) == Double.doubleToLongBits(other.inletEnthalpy)
        && Objects.equals(inStream, other.inStream)
        && Double.doubleToLongBits(isentropicEfficiency) == Double
            .doubleToLongBits(other.isentropicEfficiency)
        && numberOfCompressorCalcSteps == other.numberOfCompressorCalcSteps
        && Objects.equals(outStream, other.outStream)
        && Double.doubleToLongBits(outTemperature) == Double.doubleToLongBits(other.outTemperature)
        && Double.doubleToLongBits(polytropicEfficiency) == Double
            .doubleToLongBits(other.polytropicEfficiency)
        && Double.doubleToLongBits(polytropicExponent) == Double
            .doubleToLongBits(other.polytropicExponent)
        && Double.doubleToLongBits(polytropicFluidHead) == Double
            .doubleToLongBits(other.polytropicFluidHead)
        && Double.doubleToLongBits(polytropicHead) == Double.doubleToLongBits(other.polytropicHead)
        && Double.doubleToLongBits(polytropicHeadMeter) == Double
            .doubleToLongBits(other.polytropicHeadMeter)
        && Objects.equals(polytropicMethod, other.polytropicMethod) && powerSet == other.powerSet
        && Double.doubleToLongBits(pressure) == Double.doubleToLongBits(other.pressure)
        && Objects.equals(pressureUnit, other.pressureUnit) && speed == other.speed
        && Objects.equals(thermoSystem, other.thermoSystem) && useGERG2008 == other.useGERG2008
        && useLeachman == other.useLeachman && useVega == other.useVega
        && useOutTemperature == other.useOutTemperature
        && usePolytropicCalc == other.usePolytropicCalc
        && useRigorousPolytropicMethod == other.useRigorousPolytropicMethod;
  }

  /** {@inheritDoc} */
  @Override
  public void setMaximumSpeed(double maxSpeed) {
    this.maxspeed = maxSpeed;
  }

  /** {@inheritDoc} */
  @Override
  public void setMinimumSpeed(double minspeed) {
    this.minspeed = minspeed;
  }

  /** {@inheritDoc} */
  @Override
  public double getMaximumSpeed() {
    return maxspeed;
  }

  /** {@inheritDoc} */
  @Override
  public double getMinimumSpeed() {
    return minspeed;
  }

  /**
   * <p>
   * Setter for the field <code>compressionRatio</code>.
   * </p>
   *
   * @param compRatio a double
   */
  public void setCompressionRatio(double compRatio) {
    this.compressionRatio = compRatio;
    useCompressionRatio = true;
  }

  /**
   * <p>
   * Getter for the field <code>compressionRatio</code>.
   * </p>
   *
   * @return a double
   */
  public double getCompressionRatio() {
    return compressionRatio;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new GsonBuilder().serializeSpecialFloatingPointValues().create()
        .toJson(new CompressorResponse(this));
  }

  /** {@inheritDoc} */
  @Override
  public String toJson(ReportConfig cfg) {
    if (cfg != null && cfg.getDetailLevel(getName()) == DetailLevel.HIDE) {
      return null;
    }
    CompressorResponse res = new CompressorResponse(this);
    res.applyConfig(cfg);
    return new GsonBuilder().serializeSpecialFloatingPointValues().create().toJson(res);
  }

  /**
   * <p>
   * Getter for the field <code>maxOutletPressure</code>.
   * </p>
   *
   * @return a double
   */
  public double getMaxOutletPressure() {
    return maxOutletPressure;
  }

  /**
   * <p>
   * Setter for the field <code>maxOutletPressure</code>.
   * </p>
   *
   * @param maxOutletPressure a double
   */
  public void setMaxOutletPressure(double maxOutletPressure) {
    this.maxOutletPressure = maxOutletPressure;
    this.isSetMaxOutletPressure = true;
  }

  /**
   * <p>
   * isSetMaxOutletPressure.
   * </p>
   *
   * @return a boolean
   */
  public boolean isSetMaxOutletPressure() {
    return isSetMaxOutletPressure;
  }

  /**
   * <p>
   * Setter for the field <code>isSetMaxOutletPressure</code>.
   * </p>
   *
   * @param isSetMaxOutletPressure a boolean
   */
  public void setIsSetMaxOutletPressure(boolean isSetMaxOutletPressure) {
    this.isSetMaxOutletPressure = isSetMaxOutletPressure;
  }

  /**
   * Gets the maximum discharge temperature.
   *
   * @return maximum discharge temperature in Kelvin
   */
  public double getMaxDischargeTemperature() {
    return maxDischargeTemperature;
  }

  /**
   * Gets the maximum discharge temperature in specified unit.
   *
   * @param unit temperature unit ("K", "C", or "F")
   * @return maximum discharge temperature in specified unit
   */
  public double getMaxDischargeTemperature(String unit) {
    if (unit.equalsIgnoreCase("C")) {
      return maxDischargeTemperature - 273.15;
    } else if (unit.equalsIgnoreCase("F")) {
      return (maxDischargeTemperature - 273.15) * 9.0 / 5.0 + 32.0;
    }
    return maxDischargeTemperature; // Kelvin
  }

  /**
   * Sets the maximum discharge temperature constraint.
   *
   * <p>
   * This value is used by the capacity constraint framework to track discharge temperature
   * utilization. When the actual discharge temperature exceeds this limit, the compressor will be
   * flagged as over-utilized.
   * </p>
   *
   * @param temp maximum discharge temperature
   * @param unit temperature unit ("K", "C", or "F")
   */
  public void setMaxDischargeTemperature(double temp, String unit) {
    if (unit.equalsIgnoreCase("C")) {
      this.maxDischargeTemperature = temp + 273.15;
    } else if (unit.equalsIgnoreCase("F")) {
      this.maxDischargeTemperature = (temp - 32.0) * 5.0 / 9.0 + 273.15;
    } else {
      this.maxDischargeTemperature = temp; // Kelvin
    }
    this.isSetMaxDischargeTemperature = true;
  }

  /**
   * Sets the maximum discharge temperature in Kelvin.
   *
   * @param tempKelvin maximum discharge temperature in Kelvin
   */
  public void setMaxDischargeTemperature(double tempKelvin) {
    this.maxDischargeTemperature = tempKelvin;
    this.isSetMaxDischargeTemperature = true;
  }

  /**
   * Checks if maximum discharge temperature has been explicitly set.
   *
   * @return true if max discharge temperature is set
   */
  public boolean isSetMaxDischargeTemperature() {
    return isSetMaxDischargeTemperature;
  }

  /**
   * <p>
   * Getter for the field <code>actualCompressionRatio</code>.
   * </p>
   *
   * @return a double
   */
  public double getActualCompressionRatio() {
    return actualCompressionRatio;
  }

  /** {@inheritDoc} */
  @Override
  public void setCompressorChartType(String type) {
    if (type.equals("simple") || type.equals("fan law")) {
      compressorChart = new CompressorChart();
    } else if (type.equals("interpolate")) {
      compressorChart = new CompressorChartAlternativeMapLookup();
    } else if (type.equals("interpolate and extrapolate")) {
      compressorChart = new CompressorChartAlternativeMapLookupExtrapolate();
    } else if (type.equals("khader 2015")) {
      compressorChart = new CompressorChartKhader2015(inStream, 1.0);
    } else {
      compressorChart = new CompressorChart();
    }
  }

  /**
   * Generates a compressor chart based on the current operating point.
   *
   * <p>
   * This is a convenience method that creates a single-speed compressor chart using the
   * compressor's current speed and operating conditions. The chart type will match the compressor's
   * current chart type setting.
   * </p>
   *
   * <p>
   * Example usage:
   * </p>
   *
   * <pre>
   * compressor.setSpeed(10000);
   * compressor.run();
   * compressor.generateCompressorChart(); // Generates chart at current speed
   * </pre>
   */
  public void generateCompressorChart() {
    generateCompressorChart("normal curves", 1);
  }

  /**
   * Generates a compressor chart with multiple speed curves.
   *
   * <p>
   * Creates a multi-speed compressor chart centered around the current speed. The speeds are
   * distributed from 80% to 120% of the current speed.
   * </p>
   *
   * @param numberOfSpeeds Number of speed curves to generate (must be at least 1)
   */
  public void generateCompressorChart(int numberOfSpeeds) {
    generateCompressorChart("normal curves", numberOfSpeeds);
  }

  /**
   * Generates a compressor chart with specified options.
   *
   * <p>
   * Available generation options:
   * </p>
   * <ul>
   * <li>"normal curves" - Standard 5-point curves with surge, design, and stonewall points</li>
   * <li>"mid range" - 3-point simplified curves</li>
   * </ul>
   *
   * @param generationOption The generation option to use
   */
  public void generateCompressorChart(String generationOption) {
    generateCompressorChart(generationOption, 1);
  }

  /**
   * Generates a compressor chart with specified options and number of speeds.
   *
   * <p>
   * This is the main chart generation method that provides full control over the generated chart.
   * The generated chart will automatically use the compressor's current chart type (simple,
   * interpolate, or interpolate and extrapolate).
   * </p>
   *
   * <p>
   * Example usage:
   * </p>
   *
   * <pre>
   * // Generate 5-speed chart with normal curves
   * compressor.generateCompressorChart("normal curves", 5);
   *
   * // Generate 3-speed chart with simplified curves
   * compressor.generateCompressorChart("mid range", 3);
   * </pre>
   *
   * @param generationOption The generation option: "normal curves" or "mid range"
   * @param numberOfSpeeds Number of speed curves to generate (must be at least 1)
   */
  public void generateCompressorChart(String generationOption, int numberOfSpeeds) {
    CompressorChartGenerator generator = new CompressorChartGenerator(this);
    // Use the current chart type
    String chartType = getCompressorChartType();
    generator.setChartType(chartType);
    CompressorChartInterface newChart =
        generator.generateCompressorChart(generationOption, numberOfSpeeds);
    this.compressorChart = newChart;
  }

  /**
   * Generates a compressor chart from a predefined template.
   *
   * <p>
   * Templates provide realistic compressor curve shapes based on typical compressor
   * characteristics. Available templates:
   * </p>
   * <ul>
   * <li>"CENTRIFUGAL_STANDARD" - Standard centrifugal compressor curves</li>
   * <li>"CENTRIFUGAL_HIGH_FLOW" - High flow, lower head compressor</li>
   * <li>"CENTRIFUGAL_HIGH_HEAD" - High head, narrower operating range</li>
   * </ul>
   *
   * <p>
   * Example usage:
   * </p>
   *
   * <pre>
   * compressor.generateCompressorChartFromTemplate("CENTRIFUGAL_STANDARD", 9);
   * </pre>
   *
   * @param templateName Name of the template to use
   * @param numberOfSpeeds Number of speed curves to generate
   */
  public void generateCompressorChartFromTemplate(String templateName, int numberOfSpeeds) {
    CompressorChartGenerator generator = new CompressorChartGenerator(this);
    String chartType = getCompressorChartType();
    generator.setChartType(chartType);
    CompressorChartInterface newChart =
        generator.generateFromTemplate(templateName, numberOfSpeeds);
    this.compressorChart = newChart;
  }

  /**
   * Generates a compressor chart with specific speed values.
   *
   * <p>
   * This method allows precise control over which speeds are included in the chart.
   * </p>
   *
   * @param generationOption The generation option: "normal curves" or "mid range"
   * @param speeds Array of speed values in RPM
   */
  public void generateCompressorChart(String generationOption, double[] speeds) {
    CompressorChartGenerator generator = new CompressorChartGenerator(this);
    String chartType = getCompressorChartType();
    generator.setChartType(chartType);
    CompressorChartInterface newChart = generator.generateCompressorChart(generationOption, speeds);
    this.compressorChart = newChart;
  }

  /**
   * Gets the current compressor chart type as a string.
   *
   * @return The chart type: "simple", "interpolate", or "interpolate and extrapolate"
   */
  public String getCompressorChartType() {
    if (compressorChart instanceof CompressorChartAlternativeMapLookupExtrapolate) {
      return "interpolate and extrapolate";
    } else if (compressorChart instanceof CompressorChartAlternativeMapLookup) {
      return "interpolate";
    } else {
      return "simple";
    }
  }

  /**
   * <p>
   * isSolveSpeed.
   * </p>
   *
   * @return a boolean
   */
  public boolean isSolveSpeed() {
    return solveSpeed;
  }

  /**
   * <p>
   * Setter for the field <code>solveSpeed</code>.
   * </p>
   *
   * @param solveSpeed a boolean
   */
  public void setSolveSpeed(boolean solveSpeed) {
    this.solveSpeed = solveSpeed;
  }

  /**
   * <p>
   * isCalcPressureOut.
   * </p>
   *
   * @return a boolean
   */
  public boolean isCalcPressureOut() {
    return calcPressureOut;
  }

  /**
   * <p>
   * Setter for the field <code>calcPressureOut</code>.
   * </p>
   *
   * @param calcPressureOut a boolean
   */
  public void setCalcPressureOut(boolean calcPressureOut) {
    this.calcPressureOut = calcPressureOut;
  }

  /**
   * Checks if the compressor speed is limited.
   *
   * @return {@code true} if the compressor speed is limited, {@code false} otherwise.
   */
  public boolean isLimitSpeed() {
    return limitSpeed;
  }

  /**
   * Sets whether the compressor speed should be limited.
   *
   * @param limitSpeed {@code true} to limit the compressor speed, {@code false} otherwise.
   */
  public void setLimitSpeed(boolean limitSpeed) {
    this.limitSpeed = limitSpeed;
  }

  /** {@inheritDoc} */
  @Override
  public double getExergyChange(String unit) {
    return getExergyChange(unit, 288.15);
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * For compressors, capacity duty is defined as the total shaft work (power consumption) in Watts.
   * This is used in conjunction with {@link #getCapacityMax()} for bottleneck analysis via
   * {@link neqsim.process.processmodel.ProcessSystem#getBottleneck()}.
   * </p>
   *
   * <p>
   * For more detailed constraint analysis including speed and surge limits, use the
   * {@link neqsim.process.equipment.capacity.CapacityConstrainedEquipment} interface methods.
   * </p>
   *
   * @return shaft power in Watts
   */
  @Override
  public double getCapacityDuty() {
    return getTotalWork();
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * For compressors, maximum capacity is determined in priority order:
   * </p>
   * <ol>
   * <li>Driver speed-dependent max power curve (if driver and speed are set)</li>
   * <li>Mechanical design maximum power</li>
   * <li>Driver rated power with 10% overload margin</li>
   * </ol>
   *
   * @return maximum design power in Watts (converted from kW if from driver)
   */
  @Override
  public double getCapacityMax() {
    // Priority 1: Driver with speed-dependent power curve
    if (driver != null && driver.getRatedSpeed() > 0 && speed > 0) {
      // Driver returns kW, convert to Watts for consistency with getTotalWork()
      return driver.getMaxAvailablePowerAtSpeed(speed) * 1000.0;
    }
    // Priority 2: Mechanical design max power
    if (getMechanicalDesign().maxDesignPower > 0) {
      return getMechanicalDesign().maxDesignPower;
    }
    // Priority 3: Driver rated power with 10% overload margin
    if (driver != null && driver.getRatedPower() > 0) {
      return driver.getRatedPower() * 1.1 * 1000.0; // kW to W
    }
    // Fallback: no limit defined
    return Double.MAX_VALUE;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Returns state vector containing:
   * <ul>
   * <li>inlet_pressure - Inlet pressure [bar]</li>
   * <li>outlet_pressure - Outlet pressure [bar]</li>
   * <li>inlet_temperature - Inlet temperature [K]</li>
   * <li>outlet_temperature - Outlet temperature [K]</li>
   * <li>compression_ratio - Compression ratio [-]</li>
   * <li>polytropic_efficiency - Polytropic efficiency [fraction]</li>
   * <li>isentropic_efficiency - Isentropic efficiency [fraction]</li>
   * <li>power - Shaft power [kW]</li>
   * <li>speed - Rotational speed [rpm]</li>
   * <li>surge_margin - Distance to surge [%]</li>
   * <li>polytropic_head - Polytropic head [kJ/kg]</li>
   * <li>inlet_flow - Inlet mass flow [kg/s]</li>
   * </ul>
   */
  @Override
  public StateVector getStateVector() {
    StateVector state = new StateVector();

    // Pressures
    state.add("inlet_pressure", getInletStream().getPressure("bar"), 0.0, 200.0, "bar");
    state.add("outlet_pressure", getOutletPressure(), 0.0, 500.0, "bar");

    // Temperatures
    state.add("inlet_temperature", getInletStream().getTemperature("K"), 200.0, 500.0, "K");
    state.add("outlet_temperature", getOutletStream().getTemperature("K"), 200.0, 700.0, "K");

    // Performance
    state.add("compression_ratio", getCompressionRatio(), 1.0, 10.0, "ratio");
    state.add("polytropic_efficiency", getPolytropicEfficiency(), 0.0, 1.0, "fraction");
    state.add("isentropic_efficiency", getIsentropicEfficiency(), 0.0, 1.0, "fraction");
    state.add("power", getPower("kW"), 0.0, 50000.0, "kW");
    state.add("speed", getSpeed(), minspeed, maxspeed, "rpm");

    // Surge protection
    if (getAntiSurge() != null) {
      // Surge fraction: 0 = at surge line, 1 = far from surge
      double surgeFraction = getAntiSurge().getCurrentSurgeFraction();
      state.add("surge_fraction", surgeFraction, 0.0, 2.0, "fraction");
    }

    // Head
    state.add("polytropic_head", getPolytropicHead("kJ/kg"), 0.0, 500.0, "kJ/kg");

    // Flow
    state.add("inlet_flow", getInletStream().getFlowRate("kg/sec"), 0.0, 500.0, "kg/s");

    return state;
  }

  // ================================================================================
  // Dynamic Simulation Methods
  // ================================================================================

  /**
   * Get the current operating state of the compressor.
   *
   * @return the current CompressorState
   */
  public CompressorState getOperatingState() {
    return operatingState;
  }

  /**
   * Set the operating state of the compressor.
   *
   * @param state the new operating state
   */
  public void setOperatingState(CompressorState state) {
    CompressorState oldState = this.operatingState;
    this.operatingState = state;
    if (oldState != state) {
      fireStateChangeEvent(oldState, state);
    }
  }

  /**
   * Get the compressor driver model.
   *
   * @return the driver model, or null if not set
   */
  public CompressorDriver getDriver() {
    return driver;
  }

  /**
   * Set the compressor driver model.
   *
   * <p>
   * Setting a new driver will reinitialize capacity constraints to incorporate the driver's rated
   * speed limit into the speed constraint.
   * </p>
   *
   * @param driver the driver model
   */
  public void setDriver(CompressorDriver driver) {
    this.driver = driver;
    // Reinitialize constraints since speed constraint depends on driver rated speed
    reinitializeCapacityConstraints();
  }

  /**
   * Create and set a new driver with specified type and rated power.
   *
   * <p>
   * Setting a new driver will reinitialize capacity constraints to incorporate the driver's rated
   * speed limit into the speed constraint.
   * </p>
   *
   * @param type driver type
   * @param ratedPower rated power in kW
   */
  public void setDriver(DriverType type, double ratedPower) {
    this.driver = new CompressorDriver(type, ratedPower);
    // Reinitialize constraints since speed constraint depends on driver rated speed
    reinitializeCapacityConstraints();
  }

  /**
   * Get the operating history tracker.
   *
   * @return the operating history, or null if not enabled
   */
  public CompressorOperatingHistory getOperatingHistory() {
    return operatingHistory;
  }

  /**
   * Enable operating history tracking.
   */
  public void enableOperatingHistory() {
    if (operatingHistory == null) {
      operatingHistory = new CompressorOperatingHistory();
    }
  }

  /**
   * Disable operating history tracking.
   */
  public void disableOperatingHistory() {
    operatingHistory = null;
  }

  /**
   * Record the current operating point to history.
   *
   * @param time simulation time in seconds
   */
  public void recordOperatingPoint(double time) {
    if (operatingHistory != null) {
      operatingHistory.recordOperatingPoint(time, this);
    }
  }

  /**
   * Get the startup profile.
   *
   * @return the startup profile, or null if not set
   */
  public StartupProfile getStartupProfile() {
    return startupProfile;
  }

  /**
   * Set the startup profile.
   *
   * @param profile the startup profile
   */
  public void setStartupProfile(StartupProfile profile) {
    this.startupProfile = profile;
  }

  /**
   * Get the shutdown profile.
   *
   * @return the shutdown profile, or null if not set
   */
  public ShutdownProfile getShutdownProfile() {
    return shutdownProfile;
  }

  /**
   * Set the shutdown profile.
   *
   * @param profile the shutdown profile
   */
  public void setShutdownProfile(ShutdownProfile profile) {
    this.shutdownProfile = profile;
  }

  /**
   * Add an event listener for compressor events.
   *
   * @param listener the event listener to add
   */
  public void addEventListener(CompressorEventListener listener) {
    if (eventListeners == null) {
      eventListeners = new java.util.ArrayList<>();
    }
    if (listener != null && !eventListeners.contains(listener)) {
      eventListeners.add(listener);
    }
  }

  /**
   * Remove an event listener.
   *
   * @param listener the event listener to remove
   */
  public void removeEventListener(CompressorEventListener listener) {
    if (eventListeners != null) {
      eventListeners.remove(listener);
    }
  }

  /**
   * Get the rotational inertia of the compressor rotor.
   *
   * @return inertia in kg⋅m²
   */
  public double getRotationalInertia() {
    return rotationalInertia;
  }

  /**
   * Set the rotational inertia of the compressor rotor.
   *
   * @param inertia inertia in kg⋅m²
   */
  public void setRotationalInertia(double inertia) {
    this.rotationalInertia = inertia;
  }

  /**
   * Get the maximum acceleration rate.
   *
   * @return max acceleration in RPM/s
   */
  public double getMaxAccelerationRate() {
    return maxAccelerationRate;
  }

  /**
   * Set the maximum acceleration rate.
   *
   * @param rate max acceleration in RPM/s
   */
  public void setMaxAccelerationRate(double rate) {
    this.maxAccelerationRate = rate;
  }

  /**
   * Get the maximum deceleration rate.
   *
   * @return max deceleration in RPM/s
   */
  public double getMaxDecelerationRate() {
    return maxDecelerationRate;
  }

  /**
   * Set the maximum deceleration rate.
   *
   * @param rate max deceleration in RPM/s
   */
  public void setMaxDecelerationRate(double rate) {
    this.maxDecelerationRate = rate;
  }

  /**
   * Get the target speed for dynamic control.
   *
   * @return target speed in RPM
   */
  public double getTargetSpeed() {
    return targetSpeed;
  }

  /**
   * Set the target speed for dynamic control.
   *
   * @param speed target speed in RPM
   */
  public void setTargetSpeed(double speed) {
    this.targetSpeed = speed;
  }

  /**
   * Check if auto-speed mode is enabled.
   *
   * <p>
   * In auto-speed mode, the compressor automatically calculates the required speed from the current
   * operating point (flow and head) using the compressor chart.
   * </p>
   *
   * @return true if auto-speed mode is enabled
   */
  public boolean isAutoSpeedMode() {
    return autoSpeedMode;
  }

  /**
   * Enable or disable auto-speed mode.
   *
   * @param enabled true to enable auto-speed mode
   */
  public void setAutoSpeedMode(boolean enabled) {
    this.autoSpeedMode = enabled;
  }

  /**
   * Get the performance degradation factor.
   *
   * @return degradation factor (1.0 = new, less than 1.0 = degraded)
   */
  public double getDegradationFactor() {
    return degradationFactor;
  }

  /**
   * Set the performance degradation factor.
   *
   * @param factor degradation factor (1.0 = new, less than 1.0 = degraded)
   */
  public void setDegradationFactor(double factor) {
    this.degradationFactor = Math.max(0.0, Math.min(1.0, factor));
  }

  /**
   * Get the fouling factor.
   *
   * @return fouling factor (0 = clean, higher = more fouled)
   */
  public double getFoulingFactor() {
    return foulingFactor;
  }

  /**
   * Set the fouling factor.
   *
   * @param factor fouling factor (0 = clean, higher = more fouled)
   */
  public void setFoulingFactor(double factor) {
    this.foulingFactor = Math.max(0.0, factor);
  }

  /**
   * Get the total operating hours.
   *
   * @return operating hours
   */
  public double getOperatingHours() {
    return operatingHours;
  }

  /**
   * Set the total operating hours.
   *
   * @param hours operating hours
   */
  public void setOperatingHours(double hours) {
    this.operatingHours = hours;
  }

  /**
   * Add operating time.
   *
   * @param hours hours to add
   */
  public void addOperatingHours(double hours) {
    this.operatingHours += hours;
  }

  /**
   * Get the surge warning threshold.
   *
   * @return threshold as ratio (e.g., 0.15 = 15% margin)
   */
  public double getSurgeWarningThreshold() {
    return surgeWarningThreshold;
  }

  /**
   * Set the surge warning threshold.
   *
   * @param threshold threshold as ratio
   */
  public void setSurgeWarningThreshold(double threshold) {
    this.surgeWarningThreshold = threshold;
  }

  /**
   * Get the surge critical threshold.
   *
   * @return threshold as ratio (e.g., 0.05 = 5% margin)
   */
  public double getSurgeCriticalThreshold() {
    return surgeCriticalThreshold;
  }

  /**
   * Set the surge critical threshold.
   *
   * @param threshold threshold as ratio
   */
  public void setSurgeCriticalThreshold(double threshold) {
    this.surgeCriticalThreshold = threshold;
  }

  /**
   * Get the stone wall warning threshold.
   *
   * @return threshold as ratio
   */
  public double getStoneWallWarningThreshold() {
    return stoneWallWarningThreshold;
  }

  /**
   * Set the stone wall warning threshold.
   *
   * @param threshold threshold as ratio
   */
  public void setStoneWallWarningThreshold(double threshold) {
    this.stoneWallWarningThreshold = threshold;
  }

  /**
   * Check surge margin and fire events if thresholds are crossed.
   */
  public void checkSurgeMargin() {
    double surgeMargin = getDistanceToSurge();

    // Check for critical threshold
    if (surgeMargin <= surgeCriticalThreshold) {
      if (!surgeCriticalActive) {
        surgeCriticalActive = true;
        fireSurgeApproachEvent(surgeMargin, true);
      }
    } else if (surgeMargin <= surgeWarningThreshold) {
      if (!surgeWarningActive) {
        surgeWarningActive = true;
        surgeCriticalActive = false;
        fireSurgeApproachEvent(surgeMargin, false);
      }
    } else {
      surgeWarningActive = false;
      surgeCriticalActive = false;
    }

    // Check for actual surge
    if (surgeMargin < 0) {
      fireSurgeOccurredEvent(surgeMargin);
      setOperatingState(CompressorState.SURGE_PROTECTION);
    }
  }

  /**
   * Check stone wall margin and fire events if threshold is crossed.
   */
  public void checkStoneWallMargin() {
    double stoneWallMargin = getDistanceToStoneWall();
    if (stoneWallMargin <= stoneWallWarningThreshold) {
      fireStoneWallApproachEvent(stoneWallMargin);
    }
  }

  /**
   * Check speed limits and fire events if limits are exceeded.
   */
  public void checkSpeedLimits() {
    if (isHigherThanMaxSpeed()) {
      if (!speedLimitWarningActive) {
        speedLimitWarningActive = true;
        fireSpeedLimitExceededEvent(getSpeed(), getRatioToMaxSpeed());
      }
      setOperatingState(CompressorState.SPEED_LIMITED);
    } else if (isLowerThanMinSpeed() && operatingState.isOperational()) {
      fireSpeedBelowMinimumEvent(getSpeed(), getRatioToMinSpeed());
    } else {
      speedLimitWarningActive = false;
    }
  }

  /**
   * Check driver power limits and fire events if limits are exceeded.
   */
  public void checkPowerLimits() {
    if (driver != null) {
      double currentPower = getPower("kW");
      double maxPower = driver.getMaxAvailablePower();
      if (currentPower > maxPower) {
        firePowerLimitExceededEvent(currentPower, maxPower);
      }
    }
  }

  /**
   * Start the compressor following the startup profile.
   *
   * @param targetOperatingSpeed the final target speed in RPM
   */
  public void startCompressor(double targetOperatingSpeed) {
    if (!operatingState.canStart()) {
      logger.warn("Cannot start compressor from state: {}", operatingState);
      return;
    }

    this.targetSpeed = targetOperatingSpeed;
    this.startupElapsedTime = 0.0;
    setOperatingState(CompressorState.STARTING);

    if (startupProfile == null) {
      startupProfile = new StartupProfile();
    }

    // Open antisurge valve if required
    if (startupProfile.isRequireAntisurgeOpen() && antiSurge != null) {
      antiSurge.setActive(true);
    }
  }

  /**
   * Stop the compressor following the shutdown profile.
   *
   * @param type the type of shutdown
   */
  public void stopCompressor(ShutdownProfile.ShutdownType type) {
    if (operatingState == CompressorState.STOPPED || operatingState == CompressorState.SHUTDOWN) {
      return;
    }

    this.speedAtShutdownStart = getSpeed();
    this.shutdownElapsedTime = 0.0;
    setOperatingState(CompressorState.SHUTDOWN);

    if (shutdownProfile == null) {
      shutdownProfile = new ShutdownProfile(type, speedAtShutdownStart);
    } else {
      shutdownProfile.setShutdownType(type, speedAtShutdownStart);
    }
  }

  /**
   * Normal shutdown.
   */
  public void stopCompressor() {
    stopCompressor(ShutdownProfile.ShutdownType.NORMAL);
  }

  /**
   * Emergency shutdown (ESD).
   */
  public void emergencyShutdown() {
    stopCompressor(ShutdownProfile.ShutdownType.EMERGENCY);
    setOperatingState(CompressorState.TRIPPED);
  }

  /**
   * Update the compressor state during a transient simulation step.
   *
   * <p>
   * This method should be called during dynamic simulation to update the compressor speed, state,
   * and fire appropriate events.
   * </p>
   *
   * @param timeStep the time step in seconds
   */
  public void updateDynamicState(double timeStep) {
    // Update operating hours if running
    if (operatingState.isOperational()) {
      addOperatingHours(timeStep / 3600.0);
    }

    // Handle state-specific updates
    switch (operatingState) {
      case STARTING:
        updateStartup(timeStep);
        break;
      case SHUTDOWN:
      case DEPRESSURIZING:
        updateShutdown(timeStep);
        break;
      case RUNNING:
      case SURGE_PROTECTION:
        // Check limits during normal operation
        checkSurgeMargin();
        checkStoneWallMargin();
        checkSpeedLimits();
        checkPowerLimits();

        // Update speed if in auto-speed mode
        if (autoSpeedMode) {
          updateAutoSpeed();
        }
        break;
      default:
        break;
    }

    // Record to history if enabled
    recordOperatingPoint(operatingHours * 3600.0);
  }

  /**
   * Update startup sequence.
   *
   * @param timeStep time step in seconds
   */
  private void updateStartup(double timeStep) {
    startupElapsedTime += timeStep;

    if (startupProfile != null) {
      double targetSpd = startupProfile.getTargetSpeedAtTime(startupElapsedTime, targetSpeed);
      updateSpeedWithInertia(targetSpd, timeStep);

      if (startupProfile.isStartupComplete(startupElapsedTime, getSpeed(), targetSpeed, 10.0)) {
        setOperatingState(CompressorState.RUNNING);
        fireStartupCompleteEvent();
      }
    } else {
      // Simple linear ramp if no profile
      updateSpeedWithInertia(targetSpeed, timeStep);
      if (Math.abs(getSpeed() - targetSpeed) < 10.0) {
        setOperatingState(CompressorState.RUNNING);
        fireStartupCompleteEvent();
      }
    }
  }

  /**
   * Update shutdown sequence.
   *
   * @param timeStep time step in seconds
   */
  private void updateShutdown(double timeStep) {
    shutdownElapsedTime += timeStep;

    if (shutdownProfile != null) {
      double targetSpd =
          shutdownProfile.getTargetSpeedAtTime(shutdownElapsedTime, speedAtShutdownStart);
      updateSpeedWithInertia(targetSpd, timeStep);

      // Open antisurge if needed
      if (shutdownProfile.shouldOpenAntisurge(shutdownElapsedTime) && antiSurge != null) {
        antiSurge.setActive(true);
      }

      if (shutdownProfile.isShutdownComplete(shutdownElapsedTime, getSpeed())) {
        setSpeed(0.0);
        setOperatingState(CompressorState.STOPPED);
        fireShutdownCompleteEvent();
      }
    } else {
      // Simple linear ramp down if no profile
      updateSpeedWithInertia(0.0, timeStep);
      if (getSpeed() < 10.0) {
        setSpeed(0.0);
        setOperatingState(CompressorState.STOPPED);
        fireShutdownCompleteEvent();
      }
    }
  }

  /**
   * Update speed considering inertia constraints.
   *
   * @param targetSpd target speed in RPM
   * @param timeStep time step in seconds
   */
  private void updateSpeedWithInertia(double targetSpd, double timeStep) {
    double currentSpd = getSpeed();
    double speedDiff = targetSpd - currentSpd;

    if (Math.abs(speedDiff) < 0.1) {
      setSpeed(targetSpd);
      return;
    }

    double maxChange;
    if (driver != null) {
      // Use driver model for acceleration limits
      double newSpeed =
          driver.calculateSpeedChange(currentSpd, targetSpd, getPower("kW"), timeStep);
      setSpeed(newSpeed);
    } else {
      // Use simple rate limits
      if (speedDiff > 0) {
        maxChange = maxAccelerationRate * timeStep;
        setSpeed(currentSpd + Math.min(speedDiff, maxChange));
      } else {
        maxChange = maxDecelerationRate * timeStep;
        setSpeed(currentSpd + Math.max(speedDiff, -maxChange));
      }
    }
  }

  /**
   * Update speed automatically based on operating point.
   */
  private void updateAutoSpeed() {
    if (compressorChart != null && compressorChart.isUseCompressorChart()) {
      double flow = getInletStream().getFlowRate("m3/hr");
      double head = getPolytropicFluidHead();
      double calculatedSpeed = compressorChart.getSpeed(flow, head);
      if (!Double.isNaN(calculatedSpeed) && calculatedSpeed > 0) {
        setTargetSpeed(calculatedSpeed);
      }
    }
  }

  /**
   * Get the effective polytropic head accounting for degradation.
   *
   * @return effective head in kJ/kg
   */
  public double getEffectivePolytropicHead() {
    return polytropicFluidHead * degradationFactor * (1.0 - foulingFactor);
  }

  /**
   * Get the effective efficiency accounting for degradation.
   *
   * @return effective polytropic efficiency
   */
  public double getEffectivePolytropicEfficiency() {
    return polytropicEfficiency * degradationFactor;
  }

  // Event firing methods

  /**
   * Fire surge approach event to all listeners.
   *
   * @param surgeMargin current surge margin
   * @param isCritical true if critical threshold
   */
  private void fireSurgeApproachEvent(double surgeMargin, boolean isCritical) {
    if (eventListeners != null) {
      for (CompressorEventListener listener : eventListeners) {
        listener.onSurgeApproach(this, surgeMargin, isCritical);
      }
    }
  }

  /**
   * Fire surge occurred event.
   *
   * @param surgeMargin current surge margin
   */
  private void fireSurgeOccurredEvent(double surgeMargin) {
    if (eventListeners != null) {
      for (CompressorEventListener listener : eventListeners) {
        listener.onSurgeOccurred(this, surgeMargin);
      }
    }
  }

  /**
   * Fire speed limit exceeded event.
   *
   * @param currentSpeed current speed
   * @param ratio ratio to max speed
   */
  private void fireSpeedLimitExceededEvent(double currentSpeed, double ratio) {
    if (eventListeners != null) {
      for (CompressorEventListener listener : eventListeners) {
        listener.onSpeedLimitExceeded(this, currentSpeed, ratio);
      }
    }
  }

  /**
   * Fire speed below minimum event.
   *
   * @param currentSpeed current speed
   * @param ratio ratio to min speed
   */
  private void fireSpeedBelowMinimumEvent(double currentSpeed, double ratio) {
    if (eventListeners != null) {
      for (CompressorEventListener listener : eventListeners) {
        listener.onSpeedBelowMinimum(this, currentSpeed, ratio);
      }
    }
  }

  /**
   * Fire power limit exceeded event.
   *
   * @param currentPower current power in kW
   * @param maxPower max available power in kW
   */
  private void firePowerLimitExceededEvent(double currentPower, double maxPower) {
    if (eventListeners != null) {
      for (CompressorEventListener listener : eventListeners) {
        listener.onPowerLimitExceeded(this, currentPower, maxPower);
      }
    }
  }

  /**
   * Fire state change event.
   *
   * @param oldState previous state
   * @param newState new state
   */
  private void fireStateChangeEvent(CompressorState oldState, CompressorState newState) {
    if (eventListeners != null) {
      for (CompressorEventListener listener : eventListeners) {
        listener.onStateChange(this, oldState, newState);
      }
    }
  }

  /**
   * Fire stone wall approach event.
   *
   * @param stoneWallMargin margin to stone wall
   */
  private void fireStoneWallApproachEvent(double stoneWallMargin) {
    if (eventListeners != null) {
      for (CompressorEventListener listener : eventListeners) {
        listener.onStoneWallApproach(this, stoneWallMargin);
      }
    }
  }

  /**
   * Fire startup complete event.
   */
  private void fireStartupCompleteEvent() {
    if (eventListeners != null) {
      for (CompressorEventListener listener : eventListeners) {
        listener.onStartupComplete(this);
      }
    }
  }

  /**
   * Fire shutdown complete event.
   */
  private void fireShutdownCompleteEvent() {
    if (eventListeners != null) {
      for (CompressorEventListener listener : eventListeners) {
        listener.onShutdownComplete(this);
      }
    }
  }

  /**
   * Acknowledge a tripped compressor to allow restart.
   */
  public void acknowledgeTrip() {
    if (operatingState == CompressorState.TRIPPED) {
      setOperatingState(CompressorState.STANDBY);
    }
  }

  /**
   * Reset all dynamic simulation state.
   */
  public void resetDynamicState() {
    operatingState = CompressorState.STOPPED;
    startupElapsedTime = 0.0;
    shutdownElapsedTime = 0.0;
    speedAtShutdownStart = 0.0;
    surgeWarningActive = false;
    surgeCriticalActive = false;
    speedLimitWarningActive = false;
    if (driver != null) {
      driver.resetOverloadTimer();
    }
  }

  // ==================== CapacityConstrainedEquipment Interface Implementation
  // ====================

  /**
   * Initializes default capacity constraints for this compressor.
   *
   * <p>
   * Creates constraints for speed, power, and surge margin based on the compressor's design
   * parameters. Additional constraints can be added after construction.
   * </p>
   */
  protected void initializeCapacityConstraints() {
    // Determine max speed from curve, mechanical limit, OR driver rated speed
    // Priority: Use driver rated speed if available, otherwise mechanical limit, otherwise chart
    // max
    double effectiveMaxSpeed = maxspeed;
    double effectiveMinSpeed = minspeed;
    if (getCompressorChart() != null && getCompressorChart().isUseCompressorChart()) {
      double curveMaxSpeed = getCompressorChart().getMaxSpeedCurve();
      double curveMinSpeed = getCompressorChart().getMinSpeedCurve();
      if (!Double.isNaN(curveMaxSpeed) && curveMaxSpeed > 0) {
        effectiveMaxSpeed = Math.min(maxspeed, curveMaxSpeed);
      }
      if (!Double.isNaN(curveMinSpeed) && curveMinSpeed > 0 && curveMinSpeed < Double.MAX_VALUE) {
        effectiveMinSpeed = Math.max(minspeed, curveMinSpeed);
      }
    }

    // NOTE: The driver rated speed is NOT used to clamp effectiveMaxSpeed.
    // For VFD motors, the rated speed is the base speed (constant torque region).
    // Above base speed the motor enters field-weakening (constant power region)
    // with reduced torque — but this power limitation is already captured by the
    // power constraint which uses driver.getMaxAvailablePowerAtSpeed(speed).
    // Using the driver rated speed as a hard speed limit would double-count the
    // limitation and make all operating points above base speed appear infeasible.
    // The speed constraint should reflect the MECHANICAL maximum (chart max or
    // compressor design limit), not the electrical base speed.

    // Max speed constraint (from curve or mechanical limit)
    // Design value = max speed, so utilization = currentSpeed / maxSpeed
    // This gives proper utilization: 87% speed = 87% utilization
    final double maxSpeedLimit = effectiveMaxSpeed;
    addCapacityConstraint(StandardConstraintType.COMPRESSOR_SPEED.createConstraint()
        .setDesignValue(maxSpeedLimit).setMaxValue(maxSpeedLimit).setWarningThreshold(0.9)
        .setValueSupplier(() -> this.speed));

    // Min speed constraint (from curve minimum)
    // This constraint tracks if the compressor speed is above the minimum allowable
    // speed
    // Utilization = minSpeed/currentSpeed: <100% means above min (good), >100%
    // means below min
    // (bad)
    if (effectiveMinSpeed > 0) {
      final double minSpeedLimit = effectiveMinSpeed;
      addCapacityConstraint(StandardConstraintType.COMPRESSOR_MIN_SPEED.createConstraint()
          .setDesignValue(Double.MAX_VALUE) // MAX_VALUE signals this is a min constraint
          .setMinValue(minSpeedLimit).setWarningThreshold(0.95) // Warning when within 5% of minimum
          .setValueSupplier(() -> this.speed));
    }

    // Power constraint - dynamically evaluates against speed-dependent max power
    // from driver curve
    // This shows the actual operating margin at current speed
    addCapacityConstraint(
        StandardConstraintType.COMPRESSOR_POWER.createConstraint().setDesignValue(100.0) // 100%
            .setMaxValue(110.0) // 110% overload
            .setWarningThreshold(0.9).setValueSupplier(() -> {
              if (getThermoSystem() == null) {
                return 0.0;
              }
              // Use kW to match maxDesignPower units
              double currentPowerKW = this.getPower("kW");
              if (currentPowerKW <= 0 || Double.isNaN(currentPowerKW)) {
                return 0.0;
              }
              // Determine max power: driver speed-dependent > mechanical design > driver
              // rated
              double maxPowerLimitKW = 0.0;
              if (driver != null && driver.getRatedSpeed() > 0 && this.speed > 0) {
                // Use speed-dependent max power from driver curve
                maxPowerLimitKW = driver.getMaxAvailablePowerAtSpeed(this.speed);
              } else if (getMechanicalDesign().maxDesignPower > 0) {
                maxPowerLimitKW = getMechanicalDesign().maxDesignPower;
              } else if (driver != null && driver.getRatedPower() > 0) {
                maxPowerLimitKW = driver.getRatedPower() * 1.1; // 10% overload margin
              }
              if (maxPowerLimitKW <= 0) {
                return 0.0; // No limit defined
              }
              // Return utilization percentage (0-100+)
              return (currentPowerKW / maxPowerLimitKW) * 100.0;
            }));

    // Rated power constraint - compares against driver's rated power (for capacity
    // planning)
    // This shows utilization vs the full motor rating, regardless of current speed
    addCapacityConstraint(
        new CapacityConstraint("ratedPower", "%", CapacityConstraint.ConstraintType.DESIGN)
            .setDesignValue(100.0).setMaxValue(110.0).setWarningThreshold(0.9)
            .setDescription("Power utilization vs driver rated power (capacity planning)")
            .setValueSupplier(() -> {
              if (getThermoSystem() == null) {
                return 0.0;
              }
              double currentPowerKW = this.getPower("kW");
              if (currentPowerKW <= 0 || Double.isNaN(currentPowerKW)) {
                return 0.0;
              }
              // Use driver rated power or mechanical design power
              double ratedPowerKW = 0.0;
              if (driver != null && driver.getRatedPower() > 0) {
                ratedPowerKW = driver.getRatedPower();
              } else if (getMechanicalDesign().maxDesignPower > 0) {
                ratedPowerKW = getMechanicalDesign().maxDesignPower;
              }
              if (ratedPowerKW <= 0) {
                return 0.0;
              }
              return (currentPowerKW / ratedPowerKW) * 100.0;
            }));

    // Surge margin constraint
    // getDistanceToSurge() returns a ratio: (currentFlow / surgeFlow) - 1
    // e.g., 0.5 means current flow is 50% above surge flow
    // For utilization: 0 margin = 100% utilized (at surge), large margin = low
    // utilization
    addCapacityConstraint(StandardConstraintType.COMPRESSOR_SURGE_MARGIN.createConstraint()
        .setDesignValue(100.0).setMinValue(10.0) // Minimum 10% surge margin required
        .setWarningThreshold(0.85) // Warning at 85% utilization (15% margin)
        .setValueSupplier(() -> {
          double marginRatio = this.getDistanceToSurge();
          if (marginRatio <= 0 || Double.isNaN(marginRatio) || Double.isInfinite(marginRatio)) {
            return 100.0; // At or below surge = 100% utilized
          }
          // Convert ratio to utilization: utilization = 1 / (1 + marginRatio)
          // e.g., margin=0.5 -> utilization = 1/1.5 = 66.7%
          return 100.0 / (1.0 + marginRatio);
        }));

    // Stonewall margin constraint
    // getDistanceToStoneWall() returns a ratio: (stoneWallFlow / currentFlow) - 1
    // e.g., 0.5 means stonewall is 50% above current flow
    addCapacityConstraint(StandardConstraintType.COMPRESSOR_STONEWALL_MARGIN.createConstraint()
        .setDesignValue(100.0).setMinValue(5.0) // Minimum 5% stonewall margin
        .setWarningThreshold(0.90) // Warning at 90% utilization (10% margin)
        .setValueSupplier(() -> {
          double marginRatio = this.getDistanceToStoneWall();
          if (marginRatio <= 0 || Double.isNaN(marginRatio) || Double.isInfinite(marginRatio)) {
            return 100.0; // At or above stonewall = 100% utilized
          }
          // Convert ratio to utilization: utilization = 1 / (1 + marginRatio)
          return 100.0 / (1.0 + marginRatio);
        }));

    // Discharge temperature constraint
    // Track actual discharge temperature vs maximum allowable
    if (isSetMaxDischargeTemperature) {
      final double maxTempC = maxDischargeTemperature - 273.15;
      addCapacityConstraint(new CapacityConstraint("dischargeTemperature").setDesignValue(maxTempC)
          .setMaxValue(maxTempC * 1.1).setUnit("C")
          .setSeverity(CapacityConstraint.ConstraintSeverity.HARD).setWarningThreshold(0.9)
          .setValueSupplier(() -> {
            if (getOutletStream() == null || getOutletStream().getThermoSystem() == null) {
              return 0.0;
            }
            return getOutletStream().getTemperature("C");
          }));
    }
  }

  /**
   * Reinitializes capacity constraints with current configuration.
   *
   * <p>
   * Call this method after setting compressor charts or changing speed limits to update the
   * constraints with the new values. This clears existing constraints and recreates them based on
   * current settings.
   * </p>
   */
  public void reinitializeCapacityConstraints() {
    ensureCapacityConstraintsInitialized();
    capacityConstraints.clear();
    initializeCapacityConstraints();
  }

  /**
   * Ensures the capacity constraints map is initialized. Called after deserialization since the map
   * is transient.
   */
  private void ensureCapacityConstraintsInitialized() {
    if (capacityConstraints == null) {
      capacityConstraints = new LinkedHashMap<String, CapacityConstraint>();
      initializeCapacityConstraints();
    }
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, CapacityConstraint> getCapacityConstraints() {
    ensureCapacityConstraintsInitialized();
    return Collections.unmodifiableMap(capacityConstraints);
  }

  /** {@inheritDoc} */
  @Override
  public CapacityConstraint getBottleneckConstraint() {
    ensureCapacityConstraintsInitialized();
    CapacityConstraint bottleneck = null;
    double maxUtil = 0.0;
    for (CapacityConstraint constraint : capacityConstraints.values()) {
      double util = constraint.getUtilization();
      if (!Double.isNaN(util) && util > maxUtil) {
        maxUtil = util;
        bottleneck = constraint;
      }
    }
    return bottleneck;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isCapacityExceeded() {
    ensureCapacityConstraintsInitialized();
    for (CapacityConstraint constraint : capacityConstraints.values()) {
      if (constraint.isViolated()) {
        return true;
      }
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isHardLimitExceeded() {
    ensureCapacityConstraintsInitialized();
    for (CapacityConstraint constraint : capacityConstraints.values()) {
      if (constraint.isHardLimitExceeded()) {
        return true;
      }
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public double getMaxUtilization() {
    ensureCapacityConstraintsInitialized();
    double maxUtil = 0.0;
    for (CapacityConstraint constraint : capacityConstraints.values()) {
      if (!constraint.isEnabled()) {
        continue;
      }
      double util = constraint.getUtilization();
      if (!Double.isNaN(util) && util > maxUtil) {
        maxUtil = util;
      }
    }
    // If no enabled constraints or all returned NaN, fall back to simulation
    // validity
    // A compressor in surge or operating outside its envelope should show high
    // utilization
    if (maxUtil == 0.0 && !isSimulationValid()) {
      return 1.5; // 150% utilization indicates operating beyond capacity
    }
    return maxUtil;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Validates that the compressor simulation produced physically reasonable results. Checks for:
   * <ul>
   * <li>Positive power consumption (compressors consume energy)</li>
   * <li>Positive polytropic head (work done on gas)</li>
   * <li>Outlet temperature greater than inlet (compression heats gas)</li>
   * <li>Pressure ratio greater than 1.0</li>
   * <li>Non-NaN values for key properties</li>
   * <li>Speed within compressor chart limits (if chart is active)</li>
   * <li>Operating point not in surge region (if surge curve is defined)</li>
   * <li>Operating point not beyond stonewall/choke (if stonewall curve is defined)</li>
   * </ul>
   */
  @Override
  public boolean isSimulationValid() {
    // Check basic thermodynamic system
    if (thermoSystem == null) {
      return false;
    }

    // Check for NaN values
    double power = getPower();
    double head = getPolytropicFluidHead();
    double inletTemp = getInletTemperature();
    double outletTemp = getOutletTemperature();
    double pressureRatio = getActualCompressionRatio();

    if (Double.isNaN(power) || Double.isNaN(head) || Double.isNaN(inletTemp)
        || Double.isNaN(outletTemp) || Double.isNaN(pressureRatio)) {
      return false;
    }

    // Power must be positive (compressor consumes energy)
    if (power < 0) {
      return false;
    }

    // Polytropic head must be strictly positive (work done on gas)
    // Zero head indicates invalid operating point (e.g., speed outside chart range)
    if (head <= 0) {
      return false;
    }

    // Outlet temperature must be greater than or equal to inlet for compression
    // (Allow small tolerance for numerical precision)
    if (outletTemp < inletTemp - 1.0) {
      return false;
    }

    // Pressure ratio must be >= 1.0 for compression
    if (pressureRatio < 0.99) {
      return false;
    }

    // Check if operating within compressor chart speed limits (if chart is active)
    if (getCompressorChart() != null && getCompressorChart().isUseCompressorChart()) {
      double chartMinSpeed = getCompressorChart().getMinSpeedCurve();
      double chartMaxSpeed = getCompressorChart().getMaxSpeedCurve();
      // Strict enforcement - speed must be within chart boundaries
      if (chartMinSpeed > 0 && speed < chartMinSpeed) {
        return false;
      }
      if (chartMaxSpeed > 0 && speed > chartMaxSpeed) {
        return false;
      }

      // Check if operating in surge region (if surge curve is defined)
      // Skip surge check if AntiSurge is active - the controller handles surge
      // protection
      if (getCompressorChart().getSurgeCurve() != null
          && getCompressorChart().getSurgeCurve().isActive()
          && !(getAntiSurge() != null && getAntiSurge().isActive())) {
        try {
          double actualFlow = getInletStream().getFlowRate("m3/hr");
          double polytropicHeadValue = getPolytropicFluidHead();
          double surgeFlow = getCompressorChart().getSurgeCurve().getSurgeFlow(polytropicHeadValue);
          // Only flag as surge if actual flow is significantly below surge flow
          // Allow small tolerance (0.5%) to handle floating-point precision and
          // compressors operating exactly on the surge line with anti-surge control
          double surgeTolerance = 0.005; // 0.5% tolerance
          if (actualFlow < surgeFlow * (1.0 - surgeTolerance)) {
            return false;
          }
        } catch (Exception e) {
          // If we can't check surge, don't fail validation
          logger.debug("Could not check surge condition: " + e.getMessage());
        }
      }

      // Check if operating beyond stonewall (choke) region (if stonewall curve is
      // defined)
      if (getCompressorChart().getStoneWallCurve() != null
          && getCompressorChart().getStoneWallCurve().isActive()) {
        try {
          double actualFlow = getInletStream().getFlowRate("m3/hr");
          double polytropicHeadValue = getPolytropicFluidHead();
          if (getCompressorChart().getStoneWallCurve().isStoneWall(polytropicHeadValue,
              actualFlow)) {
            return false;
          }
        } catch (Exception e) {
          // If we can't check stonewall, don't fail validation
          logger.debug("Could not check stonewall condition: " + e.getMessage());
        }
      }
    }

    return true;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Returns detailed validation errors for the compressor simulation.
   * </p>
   */
  @Override
  public List<String> getSimulationValidationErrors() {
    List<String> errors = new ArrayList<String>();

    if (thermoSystem == null) {
      errors.add(getName() + ": No thermodynamic system - simulation not run");
      return errors;
    }

    double power = getPower();
    double head = getPolytropicFluidHead();
    double inletTemp = getInletTemperature();
    double outletTemp = getOutletTemperature();
    double pressureRatio = getActualCompressionRatio();

    if (Double.isNaN(power)) {
      errors.add(getName() + ": Power calculation returned NaN");
    } else if (power < 0) {
      errors
          .add(String.format("%s: Negative power (%.1f kW) - compressor beyond operating envelope",
              getName(), power / 1000.0));
    }

    if (Double.isNaN(head)) {
      errors.add(getName() + ": Polytropic head calculation returned NaN");
    } else if (head <= 0) {
      errors.add(String.format(
          "%s: Zero or negative polytropic head (%.2f J/kg) - compressor outside valid operating range",
          getName(), head));
    }

    if (Double.isNaN(pressureRatio)) {
      errors.add(getName() + ": Pressure ratio calculation returned NaN");
    } else if (pressureRatio < 0.99) {
      errors.add(String.format("%s: Pressure ratio (%.2f) less than 1.0 - indicates pressure drop",
          getName(), pressureRatio));
    }

    if (Double.isNaN(outletTemp) || Double.isNaN(inletTemp)) {
      errors.add(getName() + ": Temperature calculation returned NaN");
    } else if (outletTemp < inletTemp - 1.0) {
      errors.add(String.format("%s: Outlet temperature (%.1f K) less than inlet (%.1f K)",
          getName(), outletTemp, inletTemp));
    }

    // Check if operating within compressor chart speed limits
    if (getCompressorChart() != null && getCompressorChart().isUseCompressorChart()) {
      double chartMinSpeed = getCompressorChart().getMinSpeedCurve();
      double chartMaxSpeed = getCompressorChart().getMaxSpeedCurve();
      if (chartMinSpeed > 0 && speed < chartMinSpeed * 0.95) {
        errors.add(String.format(
            "%s: Speed (%.0f RPM) below chart minimum (%.0f RPM) - outside valid operating range",
            getName(), speed, chartMinSpeed));
      }
      if (chartMaxSpeed > 0 && speed > chartMaxSpeed * 1.05) {
        errors.add(String.format(
            "%s: Speed (%.0f RPM) above chart maximum (%.0f RPM) - outside valid operating range",
            getName(), speed, chartMaxSpeed));
      }

      // Check surge condition (skip if AntiSurge is active - controller handles it)
      if (getCompressorChart().getSurgeCurve() != null
          && getCompressorChart().getSurgeCurve().isActive()
          && !(getAntiSurge() != null && getAntiSurge().isActive())) {
        try {
          double actualFlow = getInletStream().getFlowRate("m3/hr");
          double polytropicHeadValue = getPolytropicFluidHead();
          double surgeFlow = getCompressorChart().getSurgeCurve().getSurgeFlow(polytropicHeadValue);
          // Only flag as surge if actual flow is significantly below surge flow
          // Allow small tolerance (0.5%) to handle floating-point precision and
          // compressors operating exactly on the surge line with anti-surge control
          double surgeTolerance = 0.005; // 0.5% tolerance
          if (actualFlow < surgeFlow * (1.0 - surgeTolerance)) {
            double surgeMargin = (actualFlow - surgeFlow) / surgeFlow * 100.0;
            errors.add(String.format(
                "%s: Operating in SURGE region - actual flow %.0f m3/hr < surge flow %.0f m3/hr (margin: %.1f%%)",
                getName(), actualFlow, surgeFlow, surgeMargin));
          }
        } catch (Exception e) {
          // Ignore if we can't check
        }
      }

      // Check stonewall condition
      if (getCompressorChart().getStoneWallCurve() != null
          && getCompressorChart().getStoneWallCurve().isActive()) {
        try {
          double actualFlow = getInletStream().getFlowRate("m3/hr");
          double polytropicHeadValue = getPolytropicFluidHead();
          if (getCompressorChart().getStoneWallCurve().isStoneWall(polytropicHeadValue,
              actualFlow)) {
            double stonewallFlow =
                getCompressorChart().getStoneWallCurve().getStoneWallFlow(polytropicHeadValue);
            errors.add(String.format(
                "%s: Operating beyond STONEWALL (choke) - actual flow %.0f m3/hr > stonewall flow %.0f m3/hr",
                getName(), actualFlow, stonewallFlow));
          }
        } catch (Exception e) {
          // Ignore if we can't check
        }
      }
    }

    return errors;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Checks if the compressor is operating within its valid envelope (between surge and stonewall).
   * </p>
   */
  @Override
  public boolean isWithinOperatingEnvelope() {
    if (!isSimulationValid()) {
      return false;
    }

    // Check surge condition
    if (isSurge()) {
      return false;
    }

    // Check stonewall condition
    if (isStoneWall()) {
      return false;
    }

    // Check if compressor chart is being used and we're within bounds
    if (getCompressorChart() != null && getCompressorChart().isUseCompressorChart()) {
      double surgeMargin = getDistanceToSurge();
      double stonewallMargin = getDistanceToStoneWall();

      // Negative margin means beyond the limit
      if (!Double.isNaN(surgeMargin) && !Double.isInfinite(surgeMargin) && surgeMargin < 0) {
        return false;
      }
      if (!Double.isNaN(stonewallMargin) && !Double.isInfinite(stonewallMargin)
          && stonewallMargin < 0) {
        return false;
      }
    }

    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getOperatingEnvelopeViolation() {
    if (!isSimulationValid()) {
      List<String> errors = getSimulationValidationErrors();
      if (!errors.isEmpty()) {
        return errors.get(0);
      }
      return "Invalid simulation result";
    }

    if (isSurge()) {
      return "Operating below surge limit - flow too low for current head";
    }

    if (isStoneWall()) {
      return "Operating above stonewall (choke) limit - flow too high";
    }

    if (getCompressorChart() != null && getCompressorChart().isUseCompressorChart()) {
      double surgeMargin = getDistanceToSurge();
      double stonewallMargin = getDistanceToStoneWall();

      if (!Double.isNaN(surgeMargin) && !Double.isInfinite(surgeMargin) && surgeMargin < 0) {
        return String.format("Below surge: margin = %.1f%%", surgeMargin);
      }
      if (!Double.isNaN(stonewallMargin) && !Double.isInfinite(stonewallMargin)
          && stonewallMargin < 0) {
        return String.format("Above stonewall: margin = %.1f%%", stonewallMargin);
      }
    }

    return null;
  }

  /** {@inheritDoc} */
  @Override
  public void addCapacityConstraint(CapacityConstraint constraint) {
    ensureCapacityConstraintsInitialized();
    if (constraint != null) {
      capacityConstraints.put(constraint.getName(), constraint);
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean removeCapacityConstraint(String constraintName) {
    ensureCapacityConstraintsInitialized();
    return capacityConstraints.remove(constraintName) != null;
  }

  /** {@inheritDoc} */
  @Override
  public void clearCapacityConstraints() {
    ensureCapacityConstraintsInitialized();
    capacityConstraints.clear();
  }

  /**
   * Updates the speed constraint design values.
   *
   * <p>
   * Call this method after setting design speed and maximum speed to update the constraint.
   * </p>
   *
   * @param designSpeed the design speed in RPM
   * @param maximumSpeed the maximum allowable speed in RPM
   */
  public void updateSpeedConstraint(double designSpeed, double maximumSpeed) {
    CapacityConstraint speedConstraint = capacityConstraints.get("speed");
    if (speedConstraint != null) {
      speedConstraint.setDesignValue(designSpeed);
      speedConstraint.setMaxValue(maximumSpeed);
    }
  }

  /**
   * Updates the power constraint design value based on driver rating.
   *
   * @param driverPowerRating the driver power rating in kW
   */
  public void updatePowerConstraint(double driverPowerRating) {
    CapacityConstraint powerConstraint = capacityConstraints.get("power");
    if (powerConstraint != null) {
      powerConstraint.setDesignValue(driverPowerRating);
      powerConstraint.setMaxValue(driverPowerRating * 1.1); // 10% overload margin
    }
  }

  // ============================================================================
  // AutoSizeable Implementation
  // ============================================================================

  /** Minimum default design power in kW for zero-flow compressors. */
  private static final double MIN_DEFAULT_DESIGN_POWER_KW = 100.0;

  /** Minimum default design volume flow in m3/hr for zero-flow compressors. */
  private static final double MIN_DEFAULT_DESIGN_FLOW_M3H = 500.0;

  /** {@inheritDoc} */
  @Override
  public void autoSize(double safetyFactor) {
    if (inStream == null) {
      throw new IllegalStateException("Inlet stream must be set before auto-sizing");
    }

    // First run to get operating point if not already run
    if (thermoSystem == null) {
      run();
    }

    // Initialize mechanical design if not already done
    initMechanicalDesign();
    getMechanicalDesign().calcDesign();

    // Generate compressor curves from template scaled to current operating point
    CompressorChartGenerator chartGenerator = new CompressorChartGenerator(this);
    chartGenerator.setChartType("interpolate and extrapolate");

    // Select template based on application (can be overridden via setCurveTemplate)
    CompressorChartInterface generatedChart =
        chartGenerator.generateFromTemplate(curveTemplate, numberOfSpeedCurves);

    // Set the chart on the compressor
    setCompressorChart(generatedChart);
    getCompressorChart().setUseCompressorChart(true);

    // Update max speed to match the chart's max speed curve with safety margin
    double chartMaxSpeed = getCompressorChart().getMaxSpeedCurve();
    if (!Double.isNaN(chartMaxSpeed) && chartMaxSpeed > 0) {
      // Set max speed to chart max - this represents the design limit
      setMaximumSpeed(chartMaxSpeed);
    }

    // Update min speed to match the chart's min speed curve
    double chartMinSpeed = getCompressorChart().getMinSpeedCurve();
    if (!Double.isNaN(chartMinSpeed) && chartMinSpeed > 0 && chartMinSpeed < Double.MAX_VALUE) {
      setMinimumSpeed(chartMinSpeed);
    }

    // Apply safety factor to power design value
    // This ensures the compressor is sized with margin above current operating
    // power
    double currentPowerKW = getPower("kW");
    double designPowerKW;
    if (!Double.isNaN(currentPowerKW) && currentPowerKW > 0) {
      designPowerKW = currentPowerKW * safetyFactor;
    } else {
      // For zero-flow or zero-power compressors, estimate design power from stream
      // properties
      // Estimate based on typical specific power consumption (200-300 kJ/m3)
      double inletFlowM3h = inStream.getFlowRate("m3/hr");
      if (!Double.isNaN(inletFlowM3h) && inletFlowM3h > 0) {
        // Estimate 250 kJ/m3 specific power, convert to kW
        designPowerKW = (inletFlowM3h / 3600.0) * 250.0 * safetyFactor;
      } else {
        // Use minimum default design power
        designPowerKW = MIN_DEFAULT_DESIGN_POWER_KW;
      }
    }
    getMechanicalDesign().setMaxDesignPower(designPowerKW);
    logger.debug("Set maxDesignPower to {} kW (current {} kW * safety factor {})", designPowerKW,
        currentPowerKW, safetyFactor);

    // Set design volume flow with safety factor for capacity tracking
    double currentFlowM3h = inStream.getFlowRate("m3/hr");
    double designFlowM3h;
    if (!Double.isNaN(currentFlowM3h) && currentFlowM3h > 0) {
      designFlowM3h = currentFlowM3h * safetyFactor;
    } else {
      // For zero-flow compressors, use minimum default design flow
      designFlowM3h = MIN_DEFAULT_DESIGN_FLOW_M3H;
    }
    getMechanicalDesign().setMaxDesignVolumeFlow(designFlowM3h);
    getMechanicalDesign().setMaxDesignGassVolumeFlow(designFlowM3h);
    logger.debug("Set maxDesignVolumeFlow to {} m3/hr (current {} m3/hr * safety factor {})",
        designFlowM3h, currentFlowM3h, safetyFactor);

    // Enable speed solving - compressor will calculate speed from pressure ratio
    setSolveSpeed(true);

    // Reinitialize capacity constraints with updated design values
    reinitializeCapacityConstraints();

    autoSized = true;
    logger.info(
        "Compressor {} auto-sized with {} template, {} speed curves, solveSpeed=true, safety factor {}",
        getName(), curveTemplate, numberOfSpeedCurves, safetyFactor);
  }

  /** {@inheritDoc} */
  @Override
  public void autoSize() {
    autoSize(1.2);
  }

  /** {@inheritDoc} */
  @Override
  public void autoSize(String company, String trDocument) {
    // Initialize mechanical design with company standards
    initMechanicalDesign();
    getMechanicalDesign().setCompanySpecificDesignStandards(company);
    getMechanicalDesign().readDesignSpecifications();

    // Run design calculations
    getMechanicalDesign().calcDesign();

    // Select template based on typical application
    // Could be enhanced to read from TR document
    selectTemplateForApplication();

    // Generate and apply curves
    autoSize(1.2);
  }

  /** {@inheritDoc} */
  @Override
  public boolean isAutoSized() {
    return autoSized;
  }

  /** {@inheritDoc} */
  @Override
  public String getSizingReport() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== Compressor Auto-Sizing Report ===\n");
    sb.append("Equipment: ").append(getName()).append("\n");
    sb.append("Auto-sized: ").append(autoSized).append("\n");
    sb.append("Curve Template: ").append(curveTemplate).append("\n");
    sb.append("Number of Speed Curves: ").append(numberOfSpeedCurves).append("\n");
    sb.append("Solve Speed Mode: ").append(isSolveSpeed()).append("\n");
    sb.append("\n--- Operating Point ---\n");
    sb.append("Speed: ").append(String.format("%.0f RPM", getSpeed())).append("\n");
    sb.append("Polytropic Efficiency: ")
        .append(String.format("%.1f%%", getPolytropicEfficiency() * 100)).append("\n");
    sb.append("Polytropic Head: ").append(String.format("%.2f kJ/kg", getPolytropicFluidHead()))
        .append("\n");
    sb.append("Power: ").append(String.format("%.2f kW", getPower("kW"))).append("\n");

    if (inStream != null) {
      sb.append("\n--- Design Basis ---\n");
      sb.append("Inlet Flow: ").append(String.format("%.1f m3/hr", inStream.getFlowRate("m3/hr")))
          .append("\n");
      sb.append("Inlet Pressure: ").append(String.format("%.2f bara", inStream.getPressure("bara")))
          .append("\n");
      sb.append("Outlet Pressure: ").append(String.format("%.2f bara", getOutletPressure()))
          .append("\n");
      sb.append("Compression Ratio: ").append(String.format("%.2f", getActualCompressionRatio()))
          .append("\n");
    }

    if (getCompressorChart() != null && getCompressorChart().isUseCompressorChart()) {
      sb.append("\n--- Compressor Map ---\n");
      sb.append("Using Compressor Chart: Yes\n");
      double[] speeds = getCompressorChart().getSpeeds();
      if (speeds != null && speeds.length > 0) {
        sb.append("Speed Range: ")
            .append(String.format("%.0f - %.0f RPM", speeds[0], speeds[speeds.length - 1]))
            .append("\n");
      }
    }

    if (getMechanicalDesign() != null) {
      sb.append("\n--- Mechanical Design ---\n");
      CompressorMechanicalDesign mechDesign = (CompressorMechanicalDesign) getMechanicalDesign();
      sb.append("Number of Stages: ").append(mechDesign.getNumberOfStages()).append("\n");
      sb.append("Impeller Diameter: ")
          .append(String.format("%.0f mm", mechDesign.getImpellerDiameter())).append("\n");
      sb.append("Driver Power: ").append(String.format("%.0f kW", mechDesign.getDriverPower()))
          .append("\n");
    }

    return sb.toString();
  }

  /** {@inheritDoc} */
  @Override
  public String getSizingReportJson() {
    Map<String, Object> report = new LinkedHashMap<String, Object>();
    report.put("equipmentName", getName());
    report.put("autoSized", autoSized);
    report.put("curveTemplate", curveTemplate);
    report.put("numberOfSpeedCurves", numberOfSpeedCurves);
    report.put("solveSpeed", isSolveSpeed());
    report.put("speed_rpm", getSpeed());
    report.put("polytropicEfficiency", getPolytropicEfficiency());
    report.put("polytropicHead_kJkg", getPolytropicFluidHead());
    report.put("power_kW", getPower("kW"));

    if (inStream != null) {
      report.put("inletFlow_m3hr", inStream.getFlowRate("m3/hr"));
      report.put("inletPressure_bara", inStream.getPressure("bara"));
      report.put("outletPressure_bara", getOutletPressure());
      report.put("compressionRatio", getActualCompressionRatio());
    }

    if (getMechanicalDesign() != null) {
      CompressorMechanicalDesign mechDesign = (CompressorMechanicalDesign) getMechanicalDesign();
      Map<String, Object> mechReport = new LinkedHashMap<String, Object>();
      mechReport.put("numberOfStages", mechDesign.getNumberOfStages());
      mechReport.put("impellerDiameter_mm", mechDesign.getImpellerDiameter());
      mechReport.put("driverPower_kW", mechDesign.getDriverPower());
      report.put("mechanicalDesign", mechReport);
    }

    return new GsonBuilder().setPrettyPrinting().create().toJson(report);
  }

  /**
   * Set the curve template to use for auto-sizing.
   *
   * <p>
   * Available templates:
   * </p>
   * <ul>
   * <li>"CENTRIFUGAL_STANDARD" - Standard centrifugal compressor (default)</li>
   * <li>"CENTRIFUGAL_HIGH_FLOW" - High flow, lower head compressor</li>
   * <li>"CENTRIFUGAL_HIGH_HEAD" - High head, narrower operating range</li>
   * <li>"PIPELINE" - Pipeline/export compressor</li>
   * <li>"EXPORT" - Offshore export compressor</li>
   * <li>"INJECTION" - Gas injection compressor</li>
   * <li>"GAS_LIFT" - Gas lift compressor</li>
   * <li>"REFRIGERATION" - Refrigeration compressor</li>
   * <li>"BOOSTER" - Booster compressor</li>
   * </ul>
   *
   * @param template the template name
   */
  public void setCurveTemplate(String template) {
    this.curveTemplate = template;
  }

  /**
   * Get the curve template used for auto-sizing.
   *
   * @return the template name
   */
  public String getCurveTemplate() {
    return curveTemplate;
  }

  /**
   * Set the number of speed curves to generate during auto-sizing.
   *
   * @param numberOfCurves number of curves (typically 3-9)
   */
  public void setNumberOfSpeedCurves(int numberOfCurves) {
    this.numberOfSpeedCurves = numberOfCurves;
  }

  /**
   * Get the number of speed curves generated during auto-sizing.
   *
   * @return number of speed curves
   */
  public int getNumberOfSpeedCurves() {
    return numberOfSpeedCurves;
  }

  /**
   * Select an appropriate curve template based on operating conditions. Called during
   * autoSize(company, trDocument) to pick a suitable template.
   */
  private void selectTemplateForApplication() {
    if (inStream == null) {
      return;
    }

    double compressionRatioVal = getActualCompressionRatio();
    double flowRate = inStream.getFlowRate("m3/hr");

    // Select template based on compression ratio and flow
    if (compressionRatioVal > 5.0) {
      curveTemplate = "INJECTION"; // High compression ratio
    } else if (compressionRatioVal > 3.0) {
      curveTemplate = "EXPORT"; // Medium-high compression
    } else if (flowRate > 50000) {
      curveTemplate = "PIPELINE"; // High flow
    } else if (compressionRatioVal < 2.0) {
      curveTemplate = "BOOSTER"; // Low compression
    } else {
      curveTemplate = "CENTRIFUGAL_STANDARD"; // Default
    }
  }

  /**
   * Creates a new Builder for constructing a Compressor with a fluent API.
   *
   * <p>
   * Example usage:
   * </p>
   * 
   * <pre>
   * Compressor comp = Compressor.builder("K-100").inletStream(feed).outletPressure(50.0, "bara")
   *     .polytropicEfficiency(0.75).speed(8000).build();
   * </pre>
   *
   * @param name the name of the compressor
   * @return a new Builder instance
   */
  public static Builder builder(String name) {
    return new Builder(name);
  }

  /**
   * Builder class for constructing Compressor instances with a fluent API.
   *
   * <p>
   * Provides a readable and maintainable way to construct complex compressor configurations. All
   * configuration options available through setters on Compressor are accessible via builder
   * methods.
   * </p>
   *
   * @author NeqSim
   * @version 1.0
   */
  public static class Builder {
    private final String name;
    private StreamInterface inletStream = null;
    private double outletPressure = -1.0;
    private String pressureUnit = "bara";
    private double compressionRatio = -1.0;
    private double isentropicEfficiency = 1.0;
    private double polytropicEfficiency = 1.0;
    private boolean usePolytropicCalc = false;
    private double speed = 3000;
    private double maxSpeed = 30000;
    private double minSpeed = 0;
    private double outTemperature = -1.0;
    private boolean useOutTemperature = false;
    private double maxOutletPressure = -1.0;
    private boolean useCompressorChart = false;
    private String polytropicMethod = "schultz";
    private boolean useRigorousPolytropicMethod = false;
    private boolean useGERG2008 = false;
    private boolean useLeachman = false;
    private boolean useVega = false;
    private int numberOfCompressorCalcSteps = 40;
    private boolean interpolateMapLookup = false;

    /**
     * Creates a new Builder with the specified compressor name.
     *
     * @param name the name of the compressor
     */
    public Builder(String name) {
      this.name = name;
    }

    /**
     * Sets the inlet stream for the compressor.
     *
     * @param stream the inlet stream
     * @return this builder for chaining
     */
    public Builder inletStream(StreamInterface stream) {
      this.inletStream = stream;
      return this;
    }

    /**
     * Sets the outlet pressure in bara.
     *
     * @param pressure outlet pressure value in bara
     * @return this builder for chaining
     */
    public Builder outletPressure(double pressure) {
      this.outletPressure = pressure;
      this.pressureUnit = "bara";
      return this;
    }

    /**
     * Sets the outlet pressure with unit specification.
     *
     * @param pressure outlet pressure value
     * @param unit pressure unit (e.g., "bara", "barg", "psia")
     * @return this builder for chaining
     */
    public Builder outletPressure(double pressure, String unit) {
      this.outletPressure = pressure;
      this.pressureUnit = unit;
      return this;
    }

    /**
     * Sets the compression ratio (outlet/inlet pressure).
     *
     * @param ratio compression ratio (must be {@code >} 1.0)
     * @return this builder for chaining
     */
    public Builder compressionRatio(double ratio) {
      this.compressionRatio = ratio;
      return this;
    }

    /**
     * Sets the isentropic efficiency.
     *
     * @param efficiency efficiency value (0.0-1.0)
     * @return this builder for chaining
     */
    public Builder isentropicEfficiency(double efficiency) {
      this.isentropicEfficiency = efficiency;
      this.usePolytropicCalc = false;
      return this;
    }

    /**
     * Sets the polytropic efficiency.
     *
     * @param efficiency efficiency value (0.0-1.0)
     * @return this builder for chaining
     */
    public Builder polytropicEfficiency(double efficiency) {
      this.polytropicEfficiency = efficiency;
      this.usePolytropicCalc = true;
      return this;
    }

    /**
     * Sets the compressor speed in RPM.
     *
     * @param speed speed in RPM
     * @return this builder for chaining
     */
    public Builder speed(double speed) {
      this.speed = speed;
      return this;
    }

    /**
     * Sets the maximum allowed speed in RPM.
     *
     * @param maxSpeed maximum speed in RPM
     * @return this builder for chaining
     */
    public Builder maxSpeed(double maxSpeed) {
      this.maxSpeed = maxSpeed;
      return this;
    }

    /**
     * Sets the minimum allowed speed in RPM.
     *
     * @param minSpeed minimum speed in RPM
     * @return this builder for chaining
     */
    public Builder minSpeed(double minSpeed) {
      this.minSpeed = minSpeed;
      return this;
    }

    /**
     * Sets the target outlet temperature (used for back-calculating efficiency).
     *
     * @param temperature outlet temperature in Kelvin
     * @return this builder for chaining
     */
    public Builder outletTemperature(double temperature) {
      this.outTemperature = temperature;
      this.useOutTemperature = true;
      return this;
    }

    /**
     * Sets the outlet temperature with unit specification.
     *
     * @param temperature outlet temperature value
     * @param unit temperature unit ("K", "C", "F")
     * @return this builder for chaining
     */
    public Builder outletTemperature(double temperature, String unit) {
      if ("C".equalsIgnoreCase(unit)) {
        this.outTemperature = temperature + 273.15;
      } else if ("F".equalsIgnoreCase(unit)) {
        this.outTemperature = (temperature - 32.0) * 5.0 / 9.0 + 273.15;
      } else {
        this.outTemperature = temperature;
      }
      this.useOutTemperature = true;
      return this;
    }

    /**
     * Sets the maximum outlet pressure limit.
     *
     * @param pressure maximum outlet pressure in bara
     * @return this builder for chaining
     */
    public Builder maxOutletPressure(double pressure) {
      this.maxOutletPressure = pressure;
      return this;
    }

    /**
     * Enables use of a compressor performance chart.
     *
     * @return this builder for chaining
     */
    public Builder useCompressorChart() {
      this.useCompressorChart = true;
      return this;
    }

    /**
     * Enables interpolated map lookup for compressor chart.
     *
     * @return this builder for chaining
     */
    public Builder interpolateMapLookup() {
      this.interpolateMapLookup = true;
      return this;
    }

    /**
     * Sets the polytropic calculation method.
     *
     * @param method method name ("schultz", "mallen", or "huntington")
     * @return this builder for chaining
     */
    public Builder polytropicMethod(String method) {
      this.polytropicMethod = method;
      return this;
    }

    /**
     * Enables the rigorous polytropic calculation method with many steps.
     *
     * @return this builder for chaining
     */
    public Builder useRigorousPolytropicMethod() {
      this.useRigorousPolytropicMethod = true;
      return this;
    }

    /**
     * Sets the number of calculation steps for polytropic calculations.
     *
     * @param steps number of steps (default is 40)
     * @return this builder for chaining
     */
    public Builder numberOfCompressorCalcSteps(int steps) {
      this.numberOfCompressorCalcSteps = steps;
      return this;
    }

    /**
     * Enables GERG-2008 equation of state for calculations.
     *
     * @return this builder for chaining
     */
    public Builder useGERG2008() {
      this.useGERG2008 = true;
      return this;
    }

    /**
     * Enables Leachman equation of state for hydrogen calculations.
     *
     * @return this builder for chaining
     */
    public Builder useLeachman() {
      this.useLeachman = true;
      return this;
    }

    /**
     * Enables VEGA equation of state for helium calculations.
     *
     * @return this builder for chaining
     */
    public Builder useVega() {
      this.useVega = true;
      return this;
    }

    /**
     * Builds and returns the configured Compressor instance.
     *
     * @return a new Compressor instance with the specified configuration
     * @throws IllegalStateException if required parameters are missing
     */
    public Compressor build() {
      Compressor compressor;
      if (interpolateMapLookup) {
        compressor = new Compressor(name, true);
      } else {
        compressor = new Compressor(name);
      }

      if (inletStream != null) {
        compressor.setInletStream(inletStream);
      }

      if (outletPressure > 0) {
        compressor.setOutletPressure(outletPressure, pressureUnit);
      }

      if (compressionRatio > 0) {
        compressor.setCompressionRatio(compressionRatio);
      }

      compressor.setIsentropicEfficiency(isentropicEfficiency);
      compressor.setPolytropicEfficiency(polytropicEfficiency);
      compressor.setUsePolytropicCalc(usePolytropicCalc);
      compressor.setSpeed(speed);
      compressor.setMaximumSpeed(maxSpeed);
      compressor.setMinimumSpeed(minSpeed);

      if (useOutTemperature && outTemperature > 0) {
        compressor.setOutTemperature(outTemperature);
      }

      if (maxOutletPressure > 0) {
        compressor.setMaxOutletPressure(maxOutletPressure);
        compressor.setIsSetMaxOutletPressure(true);
      }

      if (useCompressorChart) {
        compressor.getCompressorChart().setUseCompressorChart(true);
      }

      compressor.setPolytropicMethod(polytropicMethod);
      compressor.setUseRigorousPolytropicMethod(useRigorousPolytropicMethod);
      compressor.setNumberOfCompressorCalcSteps(numberOfCompressorCalcSteps);
      compressor.setUseGERG2008(useGERG2008);
      compressor.setUseLeachman(useLeachman);
      compressor.setUseVega(useVega);

      return compressor;
    }
  }
}
