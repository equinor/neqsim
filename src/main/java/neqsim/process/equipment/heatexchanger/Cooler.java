package neqsim.process.equipment.heatexchanger;

import java.util.UUID;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.heatexchanger.HeatExchangerMechanicalDesign;
import neqsim.process.util.monitor.HeaterResponse;
import neqsim.process.util.report.ReportConfig;
import neqsim.process.util.report.ReportConfig.DetailLevel;
import neqsim.util.unit.TemperatureUnit;

/**
 * Cooler class.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class Cooler extends Heater {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Whether the bounded utility-valve transient temperature model is enabled. */
  private boolean dynamicTemperatureControlEnabled = false;

  /** Cooling-medium temperature used by the dynamic effectiveness model, in Kelvin. */
  private double dynamicCoolingMediumTemperature = 293.15;

  /** Design process mass flow used to scale NTU away from the design point, in kg/hr. */
  private double dynamicDesignMassFlow = 0.0;

  /** Design number of transfer units at the configured design valve opening. */
  private double dynamicDesignNtu = 0.0;

  /** Cooling utility valve opening in percent. */
  private double coolingValveOpening = 50.0;

  /** Utility valve opening at the thermal design point, in percent. */
  private double dynamicDesignValveOpening = 50.0;

  /** First-order cooling-valve actuator time constant, in seconds. */
  private double dynamicActuatorTimeConstant = 10.0;

  /** First-order process-side thermal time constant, in seconds. */
  private double dynamicThermalTimeConstant = 20.0;

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

  /** {@inheritDoc} */
  @Override
  public void runTransient(double dt, UUID id) {
    if (!dynamicTemperatureControlEnabled || getCalculateSteadyState()) {
      super.runTransient(dt, id);
      return;
    }

    boolean alreadyEvaluatedForStep = id != null && id.equals(getCalculationIdentifier());
    double requestedOpening = coolingValveOpening;
    if (hasController && getController().isActive()) {
      getController().runTransient(coolingValveOpening, dt, id);
      requestedOpening = getController().getResponse();
    }
    requestedOpening = Math.max(0.0, Math.min(100.0, requestedOpening));
    double actuatorFraction = dynamicActuatorTimeConstant > 0.0 ? dt / (dynamicActuatorTimeConstant + dt) : 1.0;
    coolingValveOpening += actuatorFraction * (requestedOpening - coolingValveOpening);

    double currentMassFlow = Math.max(getInletStream().getFlowRate("kg/hr"), 1.0e-12);
    double inletTemperature = getInletStream().getTemperature("K");
    double equilibriumOutletTemperature = inletTemperature;
    if (inletTemperature > dynamicCoolingMediumTemperature) {
      double currentNtu = dynamicDesignNtu * coolingValveOpening / dynamicDesignValveOpening * dynamicDesignMassFlow
          / currentMassFlow;
      currentNtu = Math.max(0.0, Math.min(20.0, currentNtu));
      equilibriumOutletTemperature = dynamicCoolingMediumTemperature
          + (inletTemperature - dynamicCoolingMediumTemperature) * Math.exp(-currentNtu);
    }

    double previousOutletTemperature = getOutletStream().getTemperature("K");
    if (!Double.isFinite(previousOutletTemperature)) {
      previousOutletTemperature = equilibriumOutletTemperature;
    }
    double thermalFraction = dynamicThermalTimeConstant > 0.0 ? dt / (dynamicThermalTimeConstant + dt) : 1.0;
    double outletTemperature = previousOutletTemperature
        + thermalFraction * (equilibriumOutletTemperature - previousOutletTemperature);
    outletTemperature = Math.max(dynamicCoolingMediumTemperature, Math.min(inletTemperature, outletTemperature));
    setOutletTemperature(outletTemperature, "K");
    run(id);
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
