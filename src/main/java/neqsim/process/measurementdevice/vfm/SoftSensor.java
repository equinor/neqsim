package neqsim.process.measurementdevice.vfm;

import java.util.HashMap;
import java.util.Map;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.measurementdevice.StreamMeasurementDeviceBaseClass;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Soft sensor for calculating derived properties from primary measurements and thermodynamic
 * models.
 *
 * <p>
 * Soft sensors provide real-time estimates of properties that cannot be directly measured, using a
 * combination of available measurements and physics-based models. This is essential for
 * AI-augmented production optimization.
 * </p>
 *
 * <p>
 * Supported properties include:
 * </p>
 * <ul>
 * <li>Gas-Oil Ratio (GOR)</li>
 * <li>Water Cut</li>
 * <li>Density</li>
 * <li>Viscosity</li>
 * <li>Compressibility</li>
 * <li>Heating Value</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class SoftSensor extends StreamMeasurementDeviceBaseClass {
  private static final long serialVersionUID = 1000L;

  /**
   * Types of properties that can be estimated.
   */
  public enum PropertyType {
    /** Gas-Oil Ratio in Sm3/Sm3. */
    GOR,
    /** Water Cut percentage. */
    WATER_CUT,
    /** Mixture density in kg/m3. */
    DENSITY,
    /** Oil viscosity in cP. */
    OIL_VISCOSITY,
    /** Gas viscosity in cP. */
    GAS_VISCOSITY,
    /** Gas compressibility factor (Z). */
    Z_FACTOR,
    /** Higher heating value in MJ/Sm3. */
    HEATING_VALUE,
    /** Bubble point pressure in bara. */
    BUBBLE_POINT,
    /** Dew point pressure in bara. */
    DEW_POINT,
    /** Formation volume factor Bo. */
    OIL_FVF,
    /** Gas formation volume factor Bg. */
    GAS_FVF,
    /** Solution GOR Rs. */
    SOLUTION_GOR
  }

  private PropertyType propertyType;
  private Map<String, Double> inputValues = new HashMap<>();

  private double lastEstimate = Double.NaN;
  private double[] lastSensitivity;

  /**
   * Creates a new soft sensor.
   *
   * @param name the sensor name/tag
   * @param stream the stream to analyze
   * @param propertyType the property to estimate
   */
  public SoftSensor(String name, StreamInterface stream, PropertyType propertyType) {
    super(name, getUnitForProperty(propertyType), stream);
    this.propertyType = propertyType;
  }

  /**
   * Gets the default unit for a property type.
   */
  private static String getUnitForProperty(PropertyType type) {
    switch (type) {
      case GOR:
        return "Sm3/Sm3";
      case WATER_CUT:
        return "%";
      case DENSITY:
        return "kg/m3";
      case OIL_VISCOSITY:
      case GAS_VISCOSITY:
        return "cP";
      case Z_FACTOR:
        return "-";
      case HEATING_VALUE:
        return "MJ/Sm3";
      case BUBBLE_POINT:
      case DEW_POINT:
        return "bara";
      case OIL_FVF:
      case GAS_FVF:
        return "m3/Sm3";
      case SOLUTION_GOR:
        return "Sm3/Sm3";
      default:
        return "-";
    }
  }

  /**
   * Sets input values for the estimation.
   *
   * @param inputs map of input names to values
   */
  public void setInputs(Map<String, Double> inputs) {
    this.inputValues = new HashMap<>(inputs);
  }

  /**
   * Sets a single input value.
   *
   * @param name input name (e.g., "pressure", "temperature")
   * @param value input value
   */
  public void setInput(String name, double value) {
    this.inputValues.put(name, value);
  }

  /**
   * Estimates the property value from current inputs.
   *
   * @return estimated property value
   */
  public double estimate() {
    return estimate(inputValues);
  }

  /**
   * Estimates the property value from specified inputs.
   *
   * @param inputs map of input values
   * @return estimated property value
   */
  public double estimate(Map<String, Double> inputs) {
    StreamInterface str = getStream();
    if (str == null || str.getFluid() == null) {
      return Double.NaN;
    }

    SystemInterface fluid = str.getFluid().clone();

    // Apply input conditions if provided
    if (inputs.containsKey("pressure")) {
      fluid.setPressure(inputs.get("pressure"));
    }
    if (inputs.containsKey("temperature")) {
      fluid.setTemperature(inputs.get("temperature"));
    }

    // Run flash calculation
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    try {
      ops.TPflash();
    } catch (Exception e) {
      return Double.NaN;
    }

    lastEstimate = calculateProperty(fluid);
    return lastEstimate;
  }

  /**
   * Calculates the property from a flashed fluid.
   */
  private double calculateProperty(SystemInterface fluid) {
    switch (propertyType) {
      case GOR:
        return calculateGOR(fluid);
      case WATER_CUT:
        return calculateWaterCut(fluid);
      case DENSITY:
        return fluid.getDensity("kg/m3");
      case OIL_VISCOSITY:
        return fluid.hasPhaseType("oil") ? fluid.getPhase("oil").getViscosity("cP") : Double.NaN;
      case GAS_VISCOSITY:
        return fluid.hasPhaseType("gas") ? fluid.getPhase("gas").getViscosity("cP") : Double.NaN;
      case Z_FACTOR:
        return fluid.hasPhaseType("gas") ? fluid.getPhase("gas").getZ() : Double.NaN;
      case HEATING_VALUE:
        return calculateHeatingValue(fluid);
      case BUBBLE_POINT:
        return calculateBubblePoint(fluid);
      case DEW_POINT:
        return calculateDewPoint(fluid);
      case OIL_FVF:
        return calculateOilFVF(fluid);
      case GAS_FVF:
        return calculateGasFVF(fluid);
      case SOLUTION_GOR:
        return calculateSolutionGOR(fluid);
      default:
        return Double.NaN;
    }
  }

  private double calculateGOR(SystemInterface fluid) {
    double gasRate = fluid.hasPhaseType("gas") ? fluid.getPhase("gas").getFlowRate("Sm3/day") : 0.0;
    double oilRate = fluid.hasPhaseType("oil") ? fluid.getPhase("oil").getFlowRate("Sm3/day") : 0.0;
    return (oilRate > 0) ? gasRate / oilRate : 0.0;
  }

  private double calculateWaterCut(SystemInterface fluid) {
    double waterRate =
        fluid.hasPhaseType("aqueous") ? fluid.getPhase("aqueous").getFlowRate("Sm3/day") : 0.0;
    double oilRate = fluid.hasPhaseType("oil") ? fluid.getPhase("oil").getFlowRate("Sm3/day") : 0.0;
    double totalLiquid = waterRate + oilRate;
    return (totalLiquid > 0) ? (waterRate / totalLiquid) * 100.0 : 0.0;
  }

  private double calculateHeatingValue(SystemInterface fluid) {
    // Simplified heating value calculation based on composition
    double hv = 0.0;
    if (fluid.hasPhaseType("gas")) {
      SystemInterface gas = fluid.phaseToSystem("gas");
      // Approximate HHV based on methane content
      if (gas.hasComponent("methane")) {
        double methaneFrac = gas.getComponent("methane").getz();
        hv = 37.0 * methaneFrac + 40.0 * (1 - methaneFrac); // MJ/Sm3
      }
    }
    return hv;
  }

  private double calculateBubblePoint(SystemInterface fluid) {
    try {
      ThermodynamicOperations ops = new ThermodynamicOperations(fluid.clone());
      ops.bubblePointPressureFlash(false);
      return fluid.getPressure();
    } catch (Exception e) {
      return Double.NaN;
    }
  }

  private double calculateDewPoint(SystemInterface fluid) {
    try {
      ThermodynamicOperations ops = new ThermodynamicOperations(fluid.clone());
      ops.dewPointPressureFlash();
      return fluid.getPressure();
    } catch (Exception e) {
      return Double.NaN;
    }
  }

  private double calculateOilFVF(SystemInterface fluid) {
    if (!fluid.hasPhaseType("oil")) {
      return Double.NaN;
    }
    double actualVolume = fluid.getPhase("oil").getVolume("m3");
    double standardVolume = fluid.getPhase("oil").getFlowRate("Sm3/day") / 24.0 / 3600.0;
    return (standardVolume > 0) ? actualVolume / standardVolume : 1.0;
  }

  private double calculateGasFVF(SystemInterface fluid) {
    if (!fluid.hasPhaseType("gas")) {
      return Double.NaN;
    }
    double actualVolume = fluid.getPhase("gas").getVolume("m3");
    double standardVolume = fluid.getPhase("gas").getFlowRate("Sm3/day") / 24.0 / 3600.0;
    return (standardVolume > 0) ? actualVolume / standardVolume : 1.0;
  }

  private double calculateSolutionGOR(SystemInterface fluid) {
    // Rs is the GOR at bubble point conditions
    return calculateGOR(fluid);
  }

  /**
   * Calculates sensitivity (Jacobian) of the output to input changes.
   *
   * <p>
   * Uses finite differences to estimate partial derivatives.
   * </p>
   *
   * @return array of sensitivities [dOutput/dPressure, dOutput/dTemperature]
   */
  public double[] getSensitivity() {
    double baseValue = estimate();
    double[] sensitivity = new double[2];

    double pressurePerturbation = 0.1; // bar
    double temperaturePerturbation = 0.1; // K

    // dOutput/dPressure
    Map<String, Double> perturbedInputs = new HashMap<>(inputValues);
    double baseP = perturbedInputs.getOrDefault("pressure", 50.0);
    perturbedInputs.put("pressure", baseP + pressurePerturbation);
    double perturbedValue = estimate(perturbedInputs);
    sensitivity[0] = (perturbedValue - baseValue) / pressurePerturbation;

    // dOutput/dTemperature
    perturbedInputs = new HashMap<>(inputValues);
    double baseT = perturbedInputs.getOrDefault("temperature", 300.0);
    perturbedInputs.put("temperature", baseT + temperaturePerturbation);
    perturbedValue = estimate(perturbedInputs);
    sensitivity[1] = (perturbedValue - baseValue) / temperaturePerturbation;

    lastSensitivity = sensitivity;
    return sensitivity;
  }

  /**
   * Gets the last calculated sensitivities.
   *
   * @return array of [dOutput/dP, dOutput/dT]
   */
  public double[] getLastSensitivity() {
    return lastSensitivity;
  }

  /**
   * Gets uncertainty bounds for the estimate.
   *
   * @param pressureUncertainty pressure measurement uncertainty in bar
   * @param temperatureUncertainty temperature measurement uncertainty in K
   * @return uncertainty bounds for the estimated property
   */
  public UncertaintyBounds getUncertaintyBounds(double pressureUncertainty,
      double temperatureUncertainty) {
    if (Double.isNaN(lastEstimate)) {
      estimate();
    }

    double[] sens = getSensitivity();

    // Error propagation: sigma_y = sqrt(sum((dy/dx_i * sigma_x_i)^2))
    double variance =
        Math.pow(sens[0] * pressureUncertainty, 2) + Math.pow(sens[1] * temperatureUncertainty, 2);

    return new UncertaintyBounds(lastEstimate, Math.sqrt(variance), getUnit());
  }

  /**
   * Gets the property type being estimated.
   *
   * @return the property type
   */
  public PropertyType getPropertyType() {
    return propertyType;
  }

  /**
   * Sets the property type to estimate.
   *
   * @param propertyType the property type
   */
  public void setPropertyType(PropertyType propertyType) {
    this.propertyType = propertyType;
    setUnit(getUnitForProperty(propertyType));
  }

  @Override
  public double getMeasuredValue(String unit) {
    double value = estimate();

    // Unit conversions for common cases
    if (propertyType == PropertyType.DENSITY && "lb/ft3".equalsIgnoreCase(unit)) {
      return value * 0.062428;
    }

    return value;
  }
}
