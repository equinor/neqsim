package neqsim.process.equipment.heatexchanger;

import com.google.gson.GsonBuilder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.heatexchanger.HeatExchangerMechanicalDesign;
import neqsim.process.util.monitor.HeaterResponse;
import neqsim.process.util.report.ReportConfig;
import neqsim.process.util.report.ReportConfig.DetailLevel;
import neqsim.util.unit.TemperatureUnit;

/**
 * Cooler class.
 *
 * <p>
 * Configuration and calculation require a single coordinated owner. Published scalar settings use volatile fields for
 * atomic readback and visibility; a multi-setting update or process calculation is not a thread-safe transaction.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class Cooler extends Heater {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Whether the bounded utility-valve transient temperature model is enabled. */
  private volatile boolean dynamicTemperatureControlEnabled = false;

  /** Cooling-medium temperature used by the dynamic effectiveness model, in Kelvin. */
  private volatile double dynamicCoolingMediumTemperature = 293.15;

  /** Design process mass flow used to scale NTU away from the design point, in kg/hr. */
  private volatile double dynamicDesignMassFlow = 0.0;

  /** Design number of transfer units at the configured design valve opening. */
  private volatile double dynamicDesignNtu = 0.0;

  /** Cooling utility valve opening in percent. */
  private double coolingValveOpening = 50.0;

  /** Utility valve opening at the thermal design point, in percent. */
  private volatile double dynamicDesignValveOpening = 50.0;

  /** First-order cooling-valve actuator time constant, in seconds. */
  private volatile double dynamicActuatorTimeConstant = 10.0;

  /** First-order process-side thermal time constant, in seconds. */
  private volatile double dynamicThermalTimeConstant = 20.0;

  /** Unavailable utility fraction; an absent legacy serialized field therefore retains full availability. */
  private volatile double dynamicUtilityUnavailabilityFraction = 0.0;

  /** Identifier of the physical timestep whose initial dynamic state is cached. */
  private UUID dynamicStepIdentifier;

  /** Utility-valve opening at the beginning of the current physical timestep, in percent. */
  private double dynamicStepInitialValveOpening;

  /** Outlet temperature at the beginning of the current physical timestep, in Kelvin. */
  private double dynamicStepInitialOutletTemperature;

  /**
   * Constructor for Cooler.
   *
   * @param name name of cooler
   */
  public Cooler(String name) {
    super(name);
  }

  /**
   * Constructor for Cooler.
   *
   * @param name a {@link java.lang.String} object
   * @param inStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public Cooler(String name, StreamInterface inStream) {
    super(name, inStream);
  }

  /**
   * Configures a bounded dynamic cooler model driven by the equipment controller output.
   *
   * <p>
   * The controller output is interpreted as cooling-utility valve opening in percent. Heat-transfer effectiveness is
   * represented by an NTU model scaled by valve opening and inverse process mass flow. This provides a reusable
   * first-order dynamic model for control studies without requiring a fully specified utility-side stream.
   * </p>
   *
   * @param designMassFlow design process mass flow in kg/hr
   * @param designInletTemperature design process inlet temperature
   * @param designOutletTemperature design process outlet temperature
   * @param coolingMediumTemperature cooling-medium supply temperature
   * @param temperatureUnit temperature unit shared by the three temperature arguments
   */
  public void configureDynamicTemperatureControl(double designMassFlow, double designInletTemperature,
      double designOutletTemperature, double coolingMediumTemperature, String temperatureUnit) {
    double inletKelvin = new TemperatureUnit(designInletTemperature, temperatureUnit).getValue("K");
    double outletKelvin = new TemperatureUnit(designOutletTemperature, temperatureUnit).getValue("K");
    double mediumKelvin = new TemperatureUnit(coolingMediumTemperature, temperatureUnit).getValue("K");
    if (!Double.isFinite(designMassFlow) || designMassFlow <= 0.0 || !Double.isFinite(inletKelvin)
        || !Double.isFinite(outletKelvin) || !Double.isFinite(mediumKelvin) || inletKelvin <= outletKelvin
        || outletKelvin <= mediumKelvin || dynamicDesignValveOpening <= 0.0) {
      throw new IllegalArgumentException("Invalid dynamic thermal design point for cooler " + getName());
    }

    dynamicDesignMassFlow = designMassFlow;
    dynamicCoolingMediumTemperature = mediumKelvin;
    dynamicDesignNtu = -Math.log((outletKelvin - mediumKelvin) / (inletKelvin - mediumKelvin));
    coolingValveOpening = dynamicDesignValveOpening;
    dynamicStepIdentifier = null;
    dynamicTemperatureControlEnabled = true;
    setOutletTemperature(outletKelvin, "K");
  }

  /**
   * Sets the utility-valve opening associated with the configured thermal design point.
   *
   * @param openingPercent design opening in percent, greater than zero and at most 100
   */
  public void setDynamicDesignValveOpening(double openingPercent) {
    if (!Double.isFinite(openingPercent) || openingPercent <= 0.0 || openingPercent > 100.0) {
      throw new IllegalArgumentException("Dynamic cooler design valve opening must be in (0, 100]");
    }
    dynamicDesignValveOpening = openingPercent;
  }

  /**
   * Sets the dynamic actuator and thermal time constants.
   *
   * @param actuatorTimeConstant utility-valve actuator time constant in seconds, non-negative
   * @param thermalTimeConstant process-side thermal time constant in seconds, non-negative
   */
  public void setDynamicTimeConstants(double actuatorTimeConstant, double thermalTimeConstant) {
    if (!Double.isFinite(actuatorTimeConstant) || actuatorTimeConstant < 0.0 || !Double.isFinite(thermalTimeConstant)
        || thermalTimeConstant < 0.0) {
      throw new IllegalArgumentException("Dynamic cooler time constants must be finite and non-negative");
    }
    dynamicActuatorTimeConstant = actuatorTimeConstant;
    dynamicThermalTimeConstant = thermalTimeConstant;
  }

  /**
   * Returns the current cooling-utility valve opening.
   *
   * @return valve opening in percent
   */
  public double getCoolingValveOpening() {
    return coolingValveOpening;
  }

  /**
   * Sets the current cooling-utility valve opening and initial controller output.
   *
   * @param openingPercent opening in percent
   */
  public void setCoolingValveOpening(double openingPercent) {
    if (!Double.isFinite(openingPercent)) {
      throw new IllegalArgumentException("Cooling valve opening must be finite");
    }
    coolingValveOpening = Math.max(0.0, Math.min(100.0, openingPercent));
  }

  /**
   * Checks whether the dynamic utility-valve temperature model is configured.
   *
   * @return true when dynamic temperature control is enabled
   */
  public boolean isDynamicTemperatureControlEnabled() {
    return dynamicTemperatureControlEnabled;
  }

  /**
   * Returns the complete finite static policy of the bounded dynamic utility model.
   *
   * <p>
   * Actual valve opening, outlet temperature, clocks and cached physical-step state are deliberately separate. The
   * base-unit steady/transient dispatch mode must also be checked before granting dynamic control evidence.
   * </p>
   *
   * @return detached policy map with explicit physical units and utility availability
   */
  public Map<String, Object> getDynamicTemperatureControlConfiguration() {
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    values.put("schemaVersion", "neqsim.cooler.dynamic-temperature-control.v1");
    values.put("enabled", Boolean.valueOf(dynamicTemperatureControlEnabled));
    values.put("designMassFlowKgPerHour", Double.valueOf(dynamicDesignMassFlow));
    values.put("designNtu", Double.valueOf(dynamicDesignNtu));
    values.put("coolingMediumTemperatureK", Double.valueOf(dynamicCoolingMediumTemperature));
    values.put("designValveOpeningPercent", Double.valueOf(dynamicDesignValveOpening));
    values.put("actuatorTimeConstantSeconds", Double.valueOf(dynamicActuatorTimeConstant));
    values.put("thermalTimeConstantSeconds", Double.valueOf(dynamicThermalTimeConstant));
    values.put("utilityAvailabilityFraction", Double.valueOf(getCoolingUtilityAvailabilityFraction()));
    return values;
  }

  /**
   * Applies a complete validated dynamic policy without moving physical state.
   *
   * <p>
   * Validation is atomic and rejects missing/unknown keys, wrong types and non-finite or out-of-range settings. The
   * actual valve position, connected streams and temperatures remain unchanged. Resetting the scan cache starts a new
   * configuration epoch; callers must obtain fresh configuration and execution receipts.
   * </p>
   *
   * @param configuration complete policy returned by the configuration getter, possibly after a JSON round trip
   * @throws IllegalArgumentException if the complete policy is unsupported or invalid
   */
  public void applyDynamicTemperatureControlConfiguration(Map<String, Object> configuration) {
    if (configuration == null || !configuration.keySet().equals(getDynamicTemperatureControlConfiguration().keySet())
        || !"neqsim.cooler.dynamic-temperature-control.v1".equals(configuration.get("schemaVersion"))
        || !(configuration.get("enabled") instanceof Boolean)) {
      throw new IllegalArgumentException("A complete supported dynamic cooler policy is required");
    }
    boolean enabled = ((Boolean) configuration.get("enabled")).booleanValue();
    double massFlow = dynamicPolicyNumber(configuration, "designMassFlowKgPerHour");
    double ntu = dynamicPolicyNumber(configuration, "designNtu");
    double mediumTemperature = dynamicPolicyNumber(configuration, "coolingMediumTemperatureK");
    double designOpening = dynamicPolicyNumber(configuration, "designValveOpeningPercent");
    double actuatorConstant = dynamicPolicyNumber(configuration, "actuatorTimeConstantSeconds");
    double thermalConstant = dynamicPolicyNumber(configuration, "thermalTimeConstantSeconds");
    double availability = dynamicPolicyNumber(configuration, "utilityAvailabilityFraction");
    if (massFlow < 0.0 || ntu < 0.0 || (enabled && (massFlow <= 0.0 || ntu <= 0.0)) || mediumTemperature <= 0.0
        || designOpening <= 0.0 || designOpening > 100.0 || actuatorConstant < 0.0 || thermalConstant < 0.0
        || availability < 0.0 || availability > 1.0) {
      throw new IllegalArgumentException("Dynamic cooler policy contains an out-of-range physical setting");
    }
    dynamicTemperatureControlEnabled = enabled;
    dynamicDesignMassFlow = massFlow;
    dynamicDesignNtu = ntu;
    dynamicCoolingMediumTemperature = mediumTemperature;
    dynamicDesignValveOpening = designOpening;
    dynamicActuatorTimeConstant = actuatorConstant;
    dynamicThermalTimeConstant = thermalConstant;
    dynamicUtilityUnavailabilityFraction = 1.0 - availability;
    dynamicStepIdentifier = null;
  }

  /**
   * Reads one finite numeric setting without accepting string or null substitutions.
   *
   * @param values complete policy map
   * @param key setting being read
   * @return finite numeric setting
   * @throws IllegalArgumentException if the value is absent, not numeric or non-finite
   */
  private static double dynamicPolicyNumber(Map<String, Object> values, String key) {
    Object value = values.get(key);
    if (!(value instanceof Number) || !Double.isFinite(((Number) value).doubleValue())) {
      throw new IllegalArgumentException("Dynamic cooler setting must be a finite number: " + key);
    }
    return ((Number) value).doubleValue();
  }

  /**
   * Gets effective heat-transfer utility availability in the configured dynamic model.
   *
   * @return fraction from zero (lost utility) to one (fully available)
   */
  public double getCoolingUtilityAvailabilityFraction() {
    return 1.0 - dynamicUtilityUnavailabilityFraction;
  }

  /**
   * Sets utility availability independently of actuator position and controller demand.
   *
   * <p>
   * The fraction scales the native dynamic model's effective NTU. It does not change the steady fixed-temperature
   * model, fabricate a utility-side material stream, freeze a valve or impose a hot outlet temperature.
   * </p>
   *
   * @param fraction finite availability from zero to one
   * @throws IllegalArgumentException if the fraction is non-finite or outside zero to one
   */
  public void setCoolingUtilityAvailabilityFraction(double fraction) {
    if (!Double.isFinite(fraction) || fraction < 0.0 || fraction > 1.0) {
      throw new IllegalArgumentException("Cooling utility availability must be finite and within [0, 1]");
    }
    dynamicUtilityUnavailabilityFraction = 1.0 - fraction;
  }

  /**
   * Selects complete utility availability or complete loss for a dynamic fault study.
   *
   * @param available true for full availability, false for complete loss
   */
  public void setDynamicCoolingUtilityAvailable(boolean available) {
    setCoolingUtilityAvailabilityFraction(available ? 1.0 : 0.0);
  }

  /**
   * Checks whether any cooling utility is available to the configured dynamic model.
   *
   * @return true for a positive availability fraction
   */
  public boolean isDynamicCoolingUtilityAvailable() {
    return getCoolingUtilityAvailabilityFraction() > 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public void runTransient(double dt, UUID id) {
    if (!dynamicTemperatureControlEnabled || getCalculateSteadyState()) {
      dynamicStepIdentifier = null;
      super.runTransient(dt, id);
      return;
    }

    boolean alreadyEvaluatedForStep = id != null && id.equals(dynamicStepIdentifier);
    if (!alreadyEvaluatedForStep) {
      dynamicStepInitialValveOpening = coolingValveOpening;
      dynamicStepInitialOutletTemperature = getOutletStream().getTemperature("K");
    }
    double requestedOpening = dynamicStepInitialValveOpening;
    if (hasController && getController().isActive()) {
      getController().runTransient(dynamicStepInitialValveOpening, dt, id);
      requestedOpening = getController().getResponse();
    }
    requestedOpening = Math.max(0.0, Math.min(100.0, requestedOpening));
    double actuatorFraction = dynamicActuatorTimeConstant > 0.0 ? dt / (dynamicActuatorTimeConstant + dt) : 1.0;
    coolingValveOpening = dynamicStepInitialValveOpening
        + actuatorFraction * (requestedOpening - dynamicStepInitialValveOpening);

    double currentMassFlow = Math.max(getInletStream().getFlowRate("kg/hr"), 1.0e-12);
    double inletTemperature = getInletStream().getTemperature("K");
    double equilibriumOutletTemperature = inletTemperature;
    if (inletTemperature > dynamicCoolingMediumTemperature) {
      double currentNtu = dynamicDesignNtu * coolingValveOpening / dynamicDesignValveOpening * dynamicDesignMassFlow
          / currentMassFlow * getCoolingUtilityAvailabilityFraction();
      currentNtu = Math.max(0.0, Math.min(20.0, currentNtu));
      equilibriumOutletTemperature = dynamicCoolingMediumTemperature
          + (inletTemperature - dynamicCoolingMediumTemperature) * Math.exp(-currentNtu);
    }

    double previousOutletTemperature = dynamicStepInitialOutletTemperature;
    if (!Double.isFinite(previousOutletTemperature)) {
      previousOutletTemperature = equilibriumOutletTemperature;
    }
    double thermalFraction = dynamicThermalTimeConstant > 0.0 ? dt / (dynamicThermalTimeConstant + dt) : 1.0;
    double outletTemperature = previousOutletTemperature
        + thermalFraction * (equilibriumOutletTemperature - previousOutletTemperature);
    double minimumOutletTemperature = Math.min(dynamicCoolingMediumTemperature, inletTemperature);
    outletTemperature = Math.max(minimumOutletTemperature, Math.min(inletTemperature, outletTemperature));
    setOutletTemperature(outletTemperature, "K");
    run(id);
    dynamicStepIdentifier = id;
    if (!alreadyEvaluatedForStep) {
      increaseTime(dt);
    }
  }

  /** {@inheritDoc} */
  @Override
  public HeatExchangerMechanicalDesign getMechanicalDesign() {
    return super.getMechanicalDesign();
  }

  /** {@inheritDoc} */
  @Override
  public void initMechanicalDesign() {
    super.initMechanicalDesign();
  }

  /** {@inheritDoc} */
  @Override
  public double getEntropyProduction(String unit) {
    UUID id = UUID.randomUUID();
    inStream.run(id);
    inStream.getFluid().init(2);
    getOutletStream().run(id);
    getOutletStream().getFluid().init(2);

    return getOutletStream().getThermoSystem().getEntropy(unit) - inStream.getThermoSystem().getEntropy(unit);
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new GsonBuilder().serializeSpecialFloatingPointValues().create().toJson(new HeaterResponse(this));
  }

  /** {@inheritDoc} */
  @Override
  public String toJson(ReportConfig cfg) {
    if (cfg != null && cfg.getDetailLevel(getName()) == DetailLevel.HIDE) {
      return null;
    }
    HeaterResponse res = new HeaterResponse(this);
    res.applyConfig(cfg);
    return new GsonBuilder().serializeSpecialFloatingPointValues().create().toJson(res);
  }
}
