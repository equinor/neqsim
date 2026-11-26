package neqsim.process.equipment.refrigeration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.heatexchanger.CoolingWaterSystem;
import neqsim.process.equipment.stream.EnergyPortDirection;
import neqsim.process.equipment.stream.EnergyPortMode;
import neqsim.process.equipment.stream.EnergyType;
import neqsim.process.equipment.stream.Stream;
import neqsim.util.unit.PowerUnit;
import neqsim.util.validation.ValidationResult;

/**
 * Rigorous multi-level vapour-compression refrigeration plant that integrates with a process flowsheet.
 *
 * <p>
 * The plant serves any number of evaporating temperature levels with one compressor stage per level, in the arrangement
 * used by industrial propane, propene, ammonia and carbon dioxide systems. Every state point is calculated from the
 * refrigerant equation of state through {@link RefrigerantFluid}; nothing is scaled from a Carnot reference. The cycle
 * is closed analytically, so no recycle iteration is needed and the energy balance closes to round-off.
 * </p>
 *
 * <p>
 * Supported features:
 * </p>
 * <ul>
 * <li>loads bound to process coolers, fixed duties, or the condenser of a colder refrigeration system (cascade);</li>
 * <li>flash economizers at every level, or direct expansion from the condenser liquid;</li>
 * <li>open flash inter-stage cooling, where the discharge of a colder stage is desuperheated in the flash vessel;</li>
 * <li>condenser subcooling and pressure drop, evaporator superheat, suction line pressure drop;</li>
 * <li>evaporating temperatures that follow the process outlet temperature minus an approach;</li>
 * <li>shaft power and condenser heat published on energy ports, suction streams for detailed compressor models, and the
 * condenser duty handed to a {@link CoolingWaterSystem}.</li>
 * </ul>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * {@code
 * RefrigerationSystem refrigeration = new RefrigerationSystem("propane refrigeration");
 * refrigeration.setRefrigerant("propane");
 * refrigeration.setCondensingTemperature(40.0, "C");
 * refrigeration.addLevel("MP economizer", 0.0, "C");
 * refrigeration.addLevel("LP chiller", -35.0, "C").addCooler(gasChiller);
 * process.add(refrigeration); // after the coolers it serves
 * process.run();
 * double power = refrigeration.getCompressorPower("kW");
 * double cop = refrigeration.getCOP();
 * }
 * </pre>
 *
 * @author NeqSim
 * @version 1.0
 */
public class RefrigerationSystem extends ProcessEquipmentBaseClass {
  /** Serialization version identifier. */
  private static final long serialVersionUID = 1000L;

  /** Schema version for the emitted JSON / results map. */
  public static final String SCHEMA_VERSION = "1.0";

  /** Name of the energy port carrying the total compressor shaft power. */
  public static final String SHAFT_POWER_PORT = "shaftPower";

  /** Name of the energy port carrying the heat rejected at the condenser. */
  public static final String CONDENSER_HEAT_PORT = "condenserHeat";

  /**
   * How liquid refrigerant is expanded to the evaporating levels.
   */
  public enum EconomizerType {
    /** A flash vessel at every level; flash gas goes to that level's compressor stage. */
    FLASH_TANK,
    /** Every evaporator is fed directly from the condenser liquid through its own expansion valve. */
    NONE
  }

  /**
   * How the discharge of a colder compressor stage is treated before the next stage.
   */
  public enum InterstageCooling {
    /** The discharge mixes with the vapour of the next level without further cooling. */
    NONE,
    /** The discharge is desuperheated to saturation in the flash vessel of the next level by evaporating liquid. */
    FLASH_INTERCOOLER
  }

  /** Refrigerant property layer. */
  private RefrigerantFluid refrigerant = null;

  /** Condensing temperature in K. */
  private double condensingTemperature = 273.15 + 35.0;

  /** Liquid subcooling at the condenser outlet in K. */
  private double subcooling = 0.0;

  /** Pressure drop between the compressor discharge and the condenser outlet in bar. */
  private double condenserPressureDrop = 0.0;

  /** Default isentropic efficiency of the compressor stages. */
  private double isentropicEfficiency = 0.75;

  /** Combined mechanical and driver efficiency used for the electrical power. */
  private double driverEfficiency = 0.95;

  /** Liquid expansion arrangement. */
  private EconomizerType economizerType = EconomizerType.FLASH_TANK;

  /** Inter-stage cooling arrangement. */
  private InterstageCooling interstageCooling = InterstageCooling.NONE;

  /** Minimum approach between process outlet and evaporating refrigerant that does not raise a warning, in K. */
  private double minimumApproach = 2.0;

  /** Cooling medium supply temperature in K, or NaN when the condensing temperature is fixed. */
  private double coolingMediumSupplyTemperature = Double.NaN;

  /** Cooling medium return temperature in K. */
  private double coolingMediumReturnTemperature = Double.NaN;

  /** Approach between cooling medium return and condensing refrigerant in K. */
  private double condenserApproach = Double.NaN;

  /** Cooling medium heat capacity in J/(kg K). */
  private double coolingMediumHeatCapacity = 4186.0;

  /** Evaporating levels in the order they were added. */
  private final List<RefrigerationLevel> levels = new ArrayList<RefrigerationLevel>();

  // Results
  private List<RefrigerationStage> stages = new ArrayList<RefrigerationStage>();
  private List<String> warnings = new ArrayList<String>();
  private RefrigerantState condenserOutlet = null;
  private double refrigerationDuty = 0.0;
  private double compressorPower = 0.0;
  private double condenserDuty = 0.0;
  private double carnotPower = 0.0;
  private double circulation = 0.0;
  private boolean solved = false;

  /**
   * Creates a refrigeration system.
   *
   * @param name equipment name
   */
  public RefrigerationSystem(String name) {
    super(name);
    registerEnergyPort(SHAFT_POWER_PORT, EnergyType.SHAFT_WORK, EnergyPortDirection.INPUT, EnergyPortMode.CALCULATED);
    registerEnergyPort(CONDENSER_HEAT_PORT, EnergyType.HEAT, EnergyPortDirection.OUTPUT, EnergyPortMode.CALCULATED);
  }

  // ==========================================================================
  // Configuration
  // ==========================================================================

  /**
   * Sets the refrigerant by name, using the recommended equation of state.
   *
   * @param refrigerantName NeqSim component name or ASHRAE designation, for example {@code "propane"} or {@code "R717"}
   */
  public void setRefrigerant(String refrigerantName) {
    setRefrigerant(RefrigerantFluid.of(refrigerantName));
  }

  /**
   * Sets the refrigerant property layer.
   *
   * @param refrigerantFluid single-component refrigerant
   */
  public void setRefrigerant(RefrigerantFluid refrigerantFluid) {
    if (refrigerantFluid == null) {
      throw new IllegalArgumentException("refrigerant must not be null");
    }
    this.refrigerant = refrigerantFluid;
    solved = false;
  }

  /**
   * Gets the refrigerant property layer.
   *
   * @return refrigerant, or null when none is set
   */
  public RefrigerantFluid getRefrigerant() {
    return refrigerant;
  }

  /**
   * Adds an evaporating temperature level.
   *
   * @param levelName level name, unique within this system
   * @param temperature evaporating temperature
   * @param unit temperature unit: {@code "C"}, {@code "K"} or {@code "F"}
   * @return the new level, for adding loads and level settings
   */
  public RefrigerationLevel addLevel(String levelName, double temperature, String unit) {
    if (getLevel(levelName) != null) {
      throw new IllegalArgumentException("refrigeration level '" + levelName + "' already exists");
    }
    RefrigerationLevel level = new RefrigerationLevel(levelName, toKelvin(temperature, unit));
    levels.add(level);
    solved = false;
    return level;
  }

  /**
   * Adds an economizer level at the geometric mean of the condensing pressure and the coldest evaporating pressure.
   *
   * <p>
   * The geometric mean gives equal pressure ratios over two compressor stages and is the usual first choice for the
   * intermediate pressure of a two-stage system. Call this after the condensing temperature and the evaporating levels
   * are set; the level temperature is fixed when it is added.
   * </p>
   *
   * @param levelName level name
   * @return the new level
   */
  public RefrigerationLevel addGeometricMeanEconomizer(String levelName) {
    requireRefrigerant();
    if (levels.isEmpty()) {
      throw new IllegalStateException("add the evaporating levels before the geometric-mean economizer");
    }
    double coldest = Double.POSITIVE_INFINITY;
    for (RefrigerationLevel level : levels) {
      coldest = Math.min(coldest, level.getTemperature());
    }
    double condensingPressure = refrigerant.getSaturationPressure(resolveCondensingTemperature());
    double evaporatingPressure = refrigerant.getSaturationPressure(coldest);
    double temperature = refrigerant.getSaturationTemperature(Math.sqrt(condensingPressure * evaporatingPressure));
    return addLevel(levelName, temperature, "K");
  }

  /**
   * Gets a level by name.
   *
   * @param levelName level name
   * @return the level, or null when it does not exist
   */
  public RefrigerationLevel getLevel(String levelName) {
    for (RefrigerationLevel level : levels) {
      if (level.getName().equals(levelName)) {
        return level;
      }
    }
    return null;
  }

  /**
   * Gets the levels in the order they were added.
   *
   * @return unmodifiable list of levels
   */
  public List<RefrigerationLevel> getLevels() {
    return Collections.unmodifiableList(levels);
  }

  /**
   * Fixes the condensing temperature.
   *
   * @param temperature condensing temperature
   * @param unit temperature unit: {@code "C"}, {@code "K"} or {@code "F"}
   */
  public void setCondensingTemperature(double temperature, String unit) {
    this.condensingTemperature = toKelvin(temperature, unit);
    this.coolingMediumSupplyTemperature = Double.NaN;
    this.coolingMediumReturnTemperature = Double.NaN;
    this.condenserApproach = Double.NaN;
    solved = false;
  }

  /**
   * Derives the condensing temperature from the cooling medium.
   *
   * <p>
   * The condensing temperature becomes the cooling medium return temperature plus the approach, and the cooling medium
   * flow needed to remove the condenser duty is reported by {@link #getCoolingMediumFlow(String)}.
   * </p>
   *
   * @param supplyTemperature cooling medium supply temperature
   * @param returnTemperature cooling medium return temperature, above the supply temperature
   * @param approach approach between cooling medium return and condensing refrigerant, in K
   * @param unit unit of the two temperatures: {@code "C"}, {@code "K"} or {@code "F"}
   */
  public void setCondenserCoolingMedium(double supplyTemperature, double returnTemperature, double approach,
      String unit) {
    double supply = toKelvin(supplyTemperature, unit);
    double back = toKelvin(returnTemperature, unit);
    if (!(back > supply)) {
      throw new IllegalArgumentException("cooling medium return temperature must be above the supply temperature");
    }
    if (!(approach > 0.0)) {
      throw new IllegalArgumentException("condenser approach must be positive, got " + approach);
    }
    this.coolingMediumSupplyTemperature = supply;
    this.coolingMediumReturnTemperature = back;
    this.condenserApproach = approach;
    this.condensingTemperature = back + approach;
    solved = false;
  }

  /**
   * Sets the heat capacity of the cooling medium used for the cooling medium flow.
   *
   * @param heatCapacity heat capacity in J/(kg K); the default 4186 is fresh water
   */
  public void setCoolingMediumHeatCapacity(double heatCapacity) {
    if (!(heatCapacity > 0.0)) {
      throw new IllegalArgumentException("heat capacity must be positive, got " + heatCapacity);
    }
    this.coolingMediumHeatCapacity = heatCapacity;
  }

  /**
   * Sets the liquid subcooling at the condenser outlet.
   *
   * @param subcoolingK subcooling in K, must not be negative
   */
  public void setSubcooling(double subcoolingK) {
    if (subcoolingK < 0.0) {
      throw new IllegalArgumentException("subcooling must be non-negative, got " + subcoolingK);
    }
    this.subcooling = subcoolingK;
    solved = false;
  }

  /**
   * Sets the pressure drop between the compressor discharge and the condenser outlet.
   *
   * @param pressureDropBar pressure drop in bar, must not be negative
   */
  public void setCondenserPressureDrop(double pressureDropBar) {
    if (pressureDropBar < 0.0) {
      throw new IllegalArgumentException("condenser pressure drop must be non-negative, got " + pressureDropBar);
    }
    this.condenserPressureDrop = pressureDropBar;
    solved = false;
  }

  /**
   * Sets the default isentropic efficiency of the compressor stages.
   *
   * @param efficiency isentropic efficiency in the range (0, 1]
   */
  public void setIsentropicEfficiency(double efficiency) {
    if (!(efficiency > 0.0) || efficiency > 1.0) {
      throw new IllegalArgumentException("isentropic efficiency must be in (0, 1], got " + efficiency);
    }
    this.isentropicEfficiency = efficiency;
    solved = false;
  }

  /**
   * Sets the combined mechanical and driver efficiency used for the electrical power.
   *
   * @param efficiency efficiency in the range (0, 1]
   */
  public void setDriverEfficiency(double efficiency) {
    if (!(efficiency > 0.0) || efficiency > 1.0) {
      throw new IllegalArgumentException("driver efficiency must be in (0, 1], got " + efficiency);
    }
    this.driverEfficiency = efficiency;
  }

  /**
   * Sets how liquid refrigerant is expanded to the levels.
   *
   * @param type economizer arrangement
   */
  public void setEconomizerType(EconomizerType type) {
    if (type == null) {
      throw new IllegalArgumentException("economizer type must not be null");
    }
    this.economizerType = type;
    solved = false;
  }

  /**
   * Gets how liquid refrigerant is expanded to the levels.
   *
   * @return economizer arrangement
   */
  public EconomizerType getEconomizerType() {
    return economizerType;
  }

  /**
   * Sets how the discharge of a colder stage is treated before the next stage.
   *
   * @param cooling inter-stage cooling arrangement; {@link InterstageCooling#FLASH_INTERCOOLER} requires
   * {@link EconomizerType#FLASH_TANK}
   */
  public void setInterstageCooling(InterstageCooling cooling) {
    if (cooling == null) {
      throw new IllegalArgumentException("inter-stage cooling must not be null");
    }
    this.interstageCooling = cooling;
    solved = false;
  }

  /**
   * Gets how the discharge of a colder stage is treated before the next stage.
   *
   * @return inter-stage cooling arrangement
   */
  public InterstageCooling getInterstageCooling() {
    return interstageCooling;
  }

  /**
   * Sets the smallest approach between a process outlet and the evaporating refrigerant that is accepted without a
   * warning.
   *
   * @param approachK minimum approach in K, must not be negative
   */
  public void setMinimumApproach(double approachK) {
    if (approachK < 0.0) {
      throw new IllegalArgumentException("minimum approach must be non-negative, got " + approachK);
    }
    this.minimumApproach = approachK;
  }

  // ==========================================================================
  // Calculation
  // ==========================================================================

  /** {@inheritDoc} */
  @Override
  public ValidationResult validateSetup() {
    ValidationResult result = new ValidationResult(getName());
    if (refrigerant == null) {
      result.addError("refrigerant", "No refrigerant is set",
          "Call setRefrigerant(\"propane\") or another refrigerant");
      return result;
    }
    if (levels.isEmpty()) {
      result.addError("levels", "No evaporating level is defined", "Call addLevel(name, temperature, unit)");
    }
    double condensing = resolveCondensingTemperature();
    if (condensing >= refrigerant.getCriticalTemperature()) {
      result.addError("condenser",
          "Condensing temperature " + (condensing - 273.15) + " C is at or above the critical temperature of "
              + refrigerant.getName() + " (" + (refrigerant.getCriticalTemperature() - 273.15) + " C)",
          "Transcritical operation is not supported; lower the condensing temperature or cascade against a warmer "
              + "refrigeration system");
    }
    for (RefrigerationLevel level : levels) {
      if (level.getTemperature() >= condensing) {
        result.addError("levels", "Level '" + level.getName() + "' evaporates at or above the condensing temperature",
            "Lower the level temperature or raise the condensing temperature");
      }
    }
    if (interstageCooling == InterstageCooling.FLASH_INTERCOOLER && economizerType != EconomizerType.FLASH_TANK) {
      result.addError("configuration", "Flash inter-stage cooling needs flash vessels at the levels",
          "Use EconomizerType.FLASH_TANK or InterstageCooling.NONE");
    }
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public List<ProcessEquipmentInterface> getSignalSources() {
    List<ProcessEquipmentInterface> sources = new ArrayList<ProcessEquipmentInterface>();
    for (RefrigerationLevel level : levels) {
      for (RefrigerationLoad load : level.getLoads()) {
        ProcessEquipmentInterface source = load.getSourceEquipment();
        if (source != null && !sources.contains(source)) {
          sources.add(source);
        }
      }
    }
    return sources;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    ValidationResult validation = validateSetup();
    if (!validation.isValid()) {
      throw new IllegalStateException("RefrigerationSystem '" + getName() + "' is not ready: " + validation);
    }
    for (RefrigerationLevel level : levels) {
      level.refresh();
    }
    solveCycle();
    if (getEnergyPort(SHAFT_POWER_PORT).isConnected()
        && getEnergyPort(SHAFT_POWER_PORT).getMode() == EnergyPortMode.CALCULATED) {
      getEnergyPort(SHAFT_POWER_PORT).setDuty(compressorPower);
    }
    if (getEnergyPort(CONDENSER_HEAT_PORT).isConnected()
        && getEnergyPort(CONDENSER_HEAT_PORT).getMode() == EnergyPortMode.CALCULATED) {
      getEnergyPort(CONDENSER_HEAT_PORT).setDuty(condenserDuty);
    }
    setCalculationIdentifier(id);
  }

  /**
   * Solves the refrigeration cycle for the current level duties and temperatures.
   */
  private void solveCycle() {
    List<RefrigerationLevel> sortedLevels = sortLevels();
    int count = sortedLevels.size();
    double condensing = resolveCondensingTemperature();
    for (int k = 0; k < count; k++) {
      RefrigerationLevel level = sortedLevels.get(k);
      if (level.getTemperature() >= condensing) {
        throw new IllegalStateException("level '" + level.getName() + "' evaporates at " + level.getTemperature()
            + " K, at or above the condensing temperature " + condensing + " K");
      }
      if (k > 0 && !(sortedLevels.get(k - 1).getTemperature() - level.getTemperature() > 1.0e-6)) {
        throw new IllegalStateException("levels '" + sortedLevels.get(k - 1).getName() + "' and '" + level.getName()
            + "' have the same evaporating temperature; merge their loads into one level");
      }
    }

    double condensingPressure = refrigerant.getSaturationPressure(condensing);
    condenserOutlet = subcooling > 0.0 ? refrigerant.liquidAt(condensingPressure, condensing - subcooling)
        : refrigerant.saturatedLiquid(condensing);
    double dischargePressure = condensingPressure + condenserPressureDrop;

    boolean flash = economizerType == EconomizerType.FLASH_TANK;
    boolean intercooled = interstageCooling == InterstageCooling.FLASH_INTERCOOLER;
    RefrigerationStage[] stageResults = new RefrigerationStage[count];
    double liquidToColderLevels = 0.0;
    double colderDischargeFlow = 0.0;
    double colderDischargeEnthalpy = 0.0;
    double totalDuty = 0.0;
    double totalPower = 0.0;
    double reversiblePower = 0.0;

    for (int k = count - 1; k >= 0; k--) {
      RefrigerationLevel level = sortedLevels.get(k);
      double temperature = level.getTemperature();
      RefrigerantState liquid = refrigerant.saturatedLiquid(temperature);
      RefrigerantState vapor = refrigerant.saturatedVapor(temperature);
      double pressure = liquid.getPressure();
      RefrigerantState evaporatorOutlet = level.getSuperheat() > 0.0
          ? refrigerant.vaporAt(pressure, temperature + level.getSuperheat())
          : vapor;
      double feedEnthalpy = k == 0 || !flash ? condenserOutlet.getEnthalpy()
          : refrigerant.saturatedLiquid(sortedLevels.get(k - 1).getTemperature()).getEnthalpy();
      double evaporatorInletEnthalpy = flash ? liquid.getEnthalpy() : condenserOutlet.getEnthalpy();
      double latent = vapor.getEnthalpy() - liquid.getEnthalpy();
      double feedVaporFraction = Math.max(0.0, (feedEnthalpy - liquid.getEnthalpy()) / latent);

      double duty = level.getDuty();
      double evaporatorFlow = duty > 0.0 ? duty / (evaporatorOutlet.getEnthalpy() - evaporatorInletEnthalpy) : 0.0;
      double feedFlow;
      double flashGas;
      double suctionFlow;
      double suctionEnthalpyFlow;
      if (!flash) {
        feedFlow = evaporatorFlow;
        flashGas = 0.0;
        suctionFlow = evaporatorFlow + colderDischargeFlow;
        suctionEnthalpyFlow = evaporatorFlow * evaporatorOutlet.getEnthalpy()
            + colderDischargeFlow * colderDischargeEnthalpy;
      } else if (intercooled) {
        double liquidOut = evaporatorFlow + liquidToColderLevels;
        feedFlow = (liquidOut * latent + colderDischargeFlow * (colderDischargeEnthalpy - vapor.getEnthalpy()))
            / (vapor.getEnthalpy() - feedEnthalpy);
        flashGas = feedFlow + colderDischargeFlow - liquidOut;
        suctionFlow = evaporatorFlow + flashGas;
        suctionEnthalpyFlow = evaporatorFlow * evaporatorOutlet.getEnthalpy() + flashGas * vapor.getEnthalpy();
      } else {
        double liquidOut = evaporatorFlow + liquidToColderLevels;
        feedFlow = liquidOut / (1.0 - feedVaporFraction);
        flashGas = feedFlow - liquidOut;
        suctionFlow = evaporatorFlow + flashGas + colderDischargeFlow;
        suctionEnthalpyFlow = evaporatorFlow * evaporatorOutlet.getEnthalpy() + flashGas * vapor.getEnthalpy()
            + colderDischargeFlow * colderDischargeEnthalpy;
      }
      level.setResults(pressure, evaporatorFlow, flashGas, feedFlow, feedVaporFraction);

      double efficiency = Double.isNaN(level.getIsentropicEfficiency()) ? isentropicEfficiency
          : level.getIsentropicEfficiency();
      double stageDischargePressure = k == 0 ? dischargePressure
          : refrigerant.getSaturationPressure(sortedLevels.get(k - 1).getTemperature());
      if (suctionFlow > 0.0) {
        double suctionPressure = pressure - level.getSuctionPressureDrop();
        if (!(suctionPressure > 0.0)) {
          throw new IllegalStateException("suction pressure drop of level '" + level.getName()
              + "' exceeds its evaporating pressure " + pressure + " bara");
        }
        RefrigerantState suction = refrigerant.stateAtPH(suctionPressure, suctionEnthalpyFlow / suctionFlow);
        RefrigerantState isentropic = refrigerant.stateAtPS(stageDischargePressure, suction.getEntropy());
        double dischargeEnthalpy = suction.getEnthalpy()
            + (isentropic.getEnthalpy() - suction.getEnthalpy()) / efficiency;
        RefrigerantState discharge = refrigerant.stateAtPH(stageDischargePressure, dischargeEnthalpy);
        double power = suctionFlow * (dischargeEnthalpy - suction.getEnthalpy());
        stageResults[k] = new RefrigerationStage(level.getName(), suction, discharge, suctionFlow, efficiency, power);
        totalPower += power;
        colderDischargeEnthalpy = dischargeEnthalpy;
      } else {
        stageResults[k] = new RefrigerationStage(level.getName(), null, null, 0.0, efficiency, 0.0);
        colderDischargeEnthalpy = 0.0;
      }
      colderDischargeFlow = suctionFlow;
      liquidToColderLevels = feedFlow;
      totalDuty += duty;
      reversiblePower += duty * (condensing - temperature) / temperature;
    }

    stages = new ArrayList<RefrigerationStage>();
    for (int k = 0; k < count; k++) {
      stages.add(stageResults[k]);
    }
    circulation = colderDischargeFlow;
    refrigerationDuty = totalDuty;
    compressorPower = totalPower;
    carnotPower = reversiblePower;
    condenserDuty = circulation > 0.0 ? circulation * (colderDischargeEnthalpy - condenserOutlet.getEnthalpy()) : 0.0;
    warnings = collectWarnings();
    solved = true;
  }

  /**
   * Checks the temperature approach of every bound load and the wetness of the compressor suctions.
   *
   * @return list of warnings, empty when the integration is consistent
   */
  private List<String> collectWarnings() {
    List<String> found = new ArrayList<String>();
    for (RefrigerationLevel level : levels) {
      for (RefrigerationLoad load : level.getLoads()) {
        double processTemperature = load.getProcessOutletTemperature();
        if (!Double.isNaN(processTemperature) && load.getDuty() > 0.0
            && processTemperature - level.getTemperature() < minimumApproach - 1.0e-9) {
          found.add("Load '" + load.getName() + "' leaves at " + format(processTemperature - 273.15) + " C but level '"
              + level.getName() + "' evaporates at " + format(level.getTemperature() - 273.15)
              + " C; the approach is below the minimum of " + format(minimumApproach) + " K");
        }
      }
    }
    for (RefrigerationStage stage : stages) {
      if (stage.getSuction() != null && stage.getSuction().getVaporFraction() < 1.0) {
        found.add("Compressor stage on level '" + stage.getLevelName() + "' has wet suction, vapour fraction "
            + format(stage.getSuction().getVaporFraction()));
      }
    }
    return found;
  }

  /**
   * Formats a number for a warning message.
   *
   * @param value value to format
   * @return value with two decimals
   */
  private static String format(double value) {
    return String.format(java.util.Locale.ROOT, "%.2f", value);
  }

  /**
   * Gets the condensing temperature implied by the current condenser settings.
   *
   * @return condensing temperature in K
   */
  private double resolveCondensingTemperature() {
    return condensingTemperature;
  }

  /**
   * Checks that a refrigerant is set.
   */
  private void requireRefrigerant() {
    if (refrigerant == null) {
      throw new IllegalStateException("set the refrigerant first");
    }
  }

  /**
   * Checks that the cycle has been solved.
   */
  private void requireSolved() {
    if (!solved) {
      throw new IllegalStateException("RefrigerationSystem '" + getName() + "' has not been run");
    }
  }

  /**
   * Converts a temperature to kelvin.
   *
   * @param value temperature value
   * @param unit temperature unit: {@code "C"}, {@code "K"} or {@code "F"}
   * @return temperature in K
   */
  private static double toKelvin(double value, String unit) {
    if ("K".equals(unit)) {
      return value;
    } else if ("C".equals(unit)) {
      return value + 273.15;
    } else if ("F".equals(unit)) {
      return (value - 32.0) / 1.8 + 273.15;
    }
    throw new IllegalArgumentException("unsupported temperature unit '" + unit + "'; use C, K or F");
  }

  /**
   * Converts a mass flow from kg/s.
   *
   * @param kgPerSec mass flow in kg/s
   * @param unit flow unit: {@code "kg/sec"}, {@code "kg/min"}, {@code "kg/hr"} or {@code "tonnes/hr"}
   * @return mass flow in the requested unit
   */
  private static double fromKgPerSec(double kgPerSec, String unit) {
    if ("kg/sec".equals(unit) || "kg/s".equals(unit)) {
      return kgPerSec;
    } else if ("kg/min".equals(unit)) {
      return kgPerSec * 60.0;
    } else if ("kg/hr".equals(unit)) {
      return kgPerSec * 3600.0;
    } else if ("tonnes/hr".equals(unit)) {
      return kgPerSec * 3.6;
    }
    throw new IllegalArgumentException(
        "unsupported mass flow unit '" + unit + "'; use kg/sec, kg/min, kg/hr or " + "tonnes/hr");
  }

  // ==========================================================================
  // Results
  // ==========================================================================

  /**
   * Gets the condensing temperature.
   *
   * @return condensing temperature in K
   */
  public double getCondensingTemperature() {
    return resolveCondensingTemperature();
  }

  /**
   * Gets the condensing pressure from the last run.
   *
   * @return condensing pressure in bara
   */
  public double getCondensingPressure() {
    requireSolved();
    return condenserOutlet.getPressure();
  }

  /**
   * Gets the state of the liquid leaving the condenser from the last run.
   *
   * @return condenser outlet state
   */
  public RefrigerantState getCondenserOutlet() {
    requireSolved();
    return condenserOutlet;
  }

  /**
   * Gets the total refrigeration duty served.
   *
   * @return duty in W
   */
  public double getRefrigerationDuty() {
    requireSolved();
    return refrigerationDuty;
  }

  /**
   * Gets the total refrigeration duty served.
   *
   * @param unit power unit, for example {@code "kW"}
   * @return duty in the requested unit
   */
  public double getRefrigerationDuty(String unit) {
    return new PowerUnit(getRefrigerationDuty(), "W").getValue(unit);
  }

  /**
   * Gets the total compressor shaft power.
   *
   * @return shaft power in W
   */
  public double getCompressorPower() {
    requireSolved();
    return compressorPower;
  }

  /**
   * Gets the total compressor shaft power.
   *
   * @param unit power unit, for example {@code "kW"}
   * @return shaft power in the requested unit
   */
  public double getCompressorPower(String unit) {
    return new PowerUnit(getCompressorPower(), "W").getValue(unit);
  }

  /**
   * Gets the electrical power, the shaft power divided by the driver efficiency.
   *
   * @param unit power unit, for example {@code "kW"}
   * @return electrical power in the requested unit
   */
  public double getElectricalPower(String unit) {
    return new PowerUnit(getCompressorPower() / driverEfficiency, "W").getValue(unit);
  }

  /**
   * Gets the heat rejected at the condenser.
   *
   * @return condenser duty in W, or zero before the first run
   */
  public double getCondenserDuty() {
    return solved ? condenserDuty : 0.0;
  }

  /**
   * Gets the heat rejected at the condenser.
   *
   * @param unit power unit, for example {@code "kW"}
   * @return condenser duty in the requested unit
   */
  public double getCondenserDuty(String unit) {
    return new PowerUnit(getCondenserDuty(), "W").getValue(unit);
  }

  /**
   * Gets the coefficient of performance, the refrigeration duty divided by the compressor shaft power.
   *
   * @return coefficient of performance, or NaN when no duty is served
   */
  public double getCOP() {
    requireSolved();
    return compressorPower > 0.0 ? refrigerationDuty / compressorPower : Double.NaN;
  }

  /**
   * Gets the coefficient of performance of a reversible machine working between each level and the condensing
   * temperature.
   *
   * @return Carnot coefficient of performance, or NaN when no duty is served
   */
  public double getCarnotCOP() {
    requireSolved();
    return carnotPower > 0.0 ? refrigerationDuty / carnotPower : Double.NaN;
  }

  /**
   * Gets the fraction of the Carnot coefficient of performance that the cycle reaches.
   *
   * <p>
   * This is the value to pass to {@code RefrigerationCycle.setCycleEfficiency} when a screening model has to reproduce
   * this rigorous cycle.
   * </p>
   *
   * @return actual coefficient of performance divided by the Carnot value, or NaN when no duty is served
   */
  public double getCarnotFraction() {
    requireSolved();
    return compressorPower > 0.0 ? carnotPower / compressorPower : Double.NaN;
  }

  /**
   * Gets the refrigerant flow through the condenser.
   *
   * @param unit flow unit: {@code "kg/sec"}, {@code "kg/min"}, {@code "kg/hr"} or {@code "tonnes/hr"}
   * @return refrigerant circulation in the requested unit
   */
  public double getRefrigerantCirculation(String unit) {
    requireSolved();
    return fromKgPerSec(circulation, unit);
  }

  /**
   * Gets the cooling medium flow that removes the condenser duty.
   *
   * @param unit flow unit: {@code "kg/sec"}, {@code "kg/min"}, {@code "kg/hr"} or {@code "tonnes/hr"}
   * @return cooling medium flow in the requested unit
   * @throws IllegalStateException if no cooling medium was set with {@link #setCondenserCoolingMedium}
   */
  public double getCoolingMediumFlow(String unit) {
    requireSolved();
    if (Double.isNaN(coolingMediumSupplyTemperature)) {
      throw new IllegalStateException("no cooling medium is set; call setCondenserCoolingMedium first");
    }
    double flow = condenserDuty
        / (coolingMediumHeatCapacity * (coolingMediumReturnTemperature - coolingMediumSupplyTemperature));
    return fromKgPerSec(flow, unit);
  }

  /**
   * Gets the residual of the overall energy balance, refrigeration duty plus shaft power minus condenser duty.
   *
   * @return residual in W; it is zero to round-off for a consistent solution
   */
  public double getEnergyBalanceResidual() {
    requireSolved();
    return refrigerationDuty + compressorPower - condenserDuty;
  }

  /**
   * Gets the compressor stage results, warmest level first.
   *
   * @return unmodifiable list of stages; stage 0 discharges to the condenser
   */
  public List<RefrigerationStage> getStages() {
    requireSolved();
    return Collections.unmodifiableList(stages);
  }

  /**
   * Gets the warnings raised by the last run: loads colder than their level allows and wet compressor suction.
   *
   * @return unmodifiable list of warnings, empty when there are none
   */
  public List<String> getWarnings() {
    requireSolved();
    return Collections.unmodifiableList(warnings);
  }

  /**
   * Creates a process stream at the suction of a compressor stage.
   *
   * <p>
   * Use the stream as the inlet of a detailed {@code Compressor} with a performance chart, driver or mechanical design.
   * The stream is a snapshot of the last run and is not updated by later runs.
   * </p>
   *
   * @param stageIndex stage index, 0 for the stage discharging to the condenser
   * @return stream holding the suction state and flow
   */
  public Stream createSuctionStream(int stageIndex) {
    requireSolved();
    RefrigerationStage stage = stages.get(stageIndex);
    if (stage.getSuction() == null) {
      throw new IllegalStateException("stage " + stageIndex + " has no flow");
    }
    Stream stream = new Stream(getName() + " stage " + stageIndex + " suction",
        refrigerant.createFluid(stage.getSuction(), stage.getMassFlow() * 3600.0));
    stream.run();
    return stream;
  }

  /**
   * Registers the condenser of this system as a consumer of a cooling water system.
   *
   * @param coolingWater cooling water system to load
   * @param approachK approach between cooling water return and condensing refrigerant in K
   */
  public void addCondenserTo(CoolingWaterSystem coolingWater, double approachK) {
    requireSolved();
    coolingWater.addCoolingRequirement(getName() + " condenser", condenserDuty / 1000.0,
        resolveCondensingTemperature() - 273.15, approachK);
  }

  /**
   * Builds an ordered results map suitable for JSON serialization.
   *
   * @return ordered results map
   */
  public Map<String, Object> toResultsMap() {
    requireSolved();
    Map<String, Object> root = new LinkedHashMap<String, Object>();
    root.put("schemaVersion", SCHEMA_VERSION);
    root.put("name", getName());
    Map<String, Object> basis = new LinkedHashMap<String, Object>();
    basis.put("refrigerant", refrigerant.getName());
    basis.put("thermodynamicModel", refrigerant.getModelName());
    basis.put("economizerType", economizerType.name());
    basis.put("interstageCooling", interstageCooling.name());
    basis.put("condensingTemperature_C", resolveCondensingTemperature() - 273.15);
    basis.put("condensingPressure_bara", condenserOutlet.getPressure());
    basis.put("subcooling_K", subcooling);
    basis.put("condenserPressureDrop_bar", condenserPressureDrop);
    basis.put("defaultIsentropicEfficiency", isentropicEfficiency);
    basis.put("driverEfficiency", driverEfficiency);
    root.put("designBasis", basis);
    Map<String, Object> results = new LinkedHashMap<String, Object>();
    results.put("refrigerationDuty_kW", refrigerationDuty / 1000.0);
    results.put("compressorPower_kW", compressorPower / 1000.0);
    results.put("electricalPower_kW", compressorPower / driverEfficiency / 1000.0);
    results.put("condenserDuty_kW", condenserDuty / 1000.0);
    results.put("COP", compressorPower > 0.0 ? Double.valueOf(refrigerationDuty / compressorPower) : null);
    results.put("carnotCOP", carnotPower > 0.0 ? Double.valueOf(refrigerationDuty / carnotPower) : null);
    results.put("carnotFraction", compressorPower > 0.0 ? Double.valueOf(carnotPower / compressorPower) : null);
    results.put("refrigerantCirculation_kg_per_hr", circulation * 3600.0);
    if (!Double.isNaN(coolingMediumSupplyTemperature)) {
      results.put("coolingMediumFlow_kg_per_hr", getCoolingMediumFlow("kg/hr"));
    }
    results.put("energyBalanceResidual_kW", getEnergyBalanceResidual() / 1000.0);
    root.put("results", results);
    List<Map<String, Object>> levelMaps = new ArrayList<Map<String, Object>>();
    for (RefrigerationLevel level : sortLevels()) {
      levelMaps.add(level.toMap());
    }
    root.put("levels", levelMaps);
    List<Map<String, Object>> stageMaps = new ArrayList<Map<String, Object>>();
    for (RefrigerationStage stage : stages) {
      stageMaps.add(stage.toMap());
    }
    root.put("compressorStages", stageMaps);
    root.put("condenserOutlet", condenserOutlet.toMap());
    root.put("warnings", new ArrayList<String>(warnings));
    return root;
  }

  /**
   * Gets the levels sorted from warmest to coldest.
   *
   * @return new sorted list of levels
   */
  private List<RefrigerationLevel> sortLevels() {
    List<RefrigerationLevel> sorted = new ArrayList<RefrigerationLevel>(levels);
    Collections.sort(sorted, new Comparator<RefrigerationLevel>() {
      @Override
      public int compare(RefrigerationLevel first, RefrigerationLevel second) {
        return Double.compare(second.getTemperature(), first.getTemperature());
      }
    });
    return sorted;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    if (!solved) {
      return null;
    }
    return new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(toResultsMap());
  }
}
