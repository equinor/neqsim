package neqsim.process.equipment.network;

import java.io.Serializable;

/**
 * Bounded, typed network optimization decision variable.
 */
public class NetworkDecisionVariable implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Supported network degrees of freedom. */
  public enum Type {
    /** Source mass, molar, volume, or energy rate. */
    SOURCE_RATE,
    /** Source pressure. */
    SOURCE_PRESSURE,
    /** Sink nomination rate. */
    SINK_NOMINATION,
    /** Choke opening fraction. */
    CHOKE_OPENING,
    /** Regulator pressure set point. */
    REGULATOR_SET_POINT,
    /** Compressor speed factor. */
    COMPRESSOR_SPEED,
    /** Pump speed factor. */
    PUMP_SPEED,
    /** Route-allocation fraction. */
    ROUTE_ALLOCATION,
    /** Edge availability or derating fraction. */
    EDGE_AVAILABILITY,
    /** Discrete physical pipe-diameter candidate. */
    PIPE_DIAMETER,
    /** User-supplied getter and setter. */
    CUSTOM
  }

  /** Explicit rate basis. */
  public enum RateBasis {
    /** Not a rate variable. */
    NONE,
    /** Mass rate. */
    MASS,
    /** Molar rate. */
    MOLAR,
    /** Standard-condition volume rate. */
    STANDARD_VOLUME,
    /** Actual-condition volume rate. */
    ACTUAL_VOLUME,
    /** Energy rate. */
    ENERGY
  }

  /** Read a custom network variable. */
  public interface Getter extends Serializable {
    /**
     * Read the current value.
     *
     * @param network network
     * @return value in the variable unit
     */
    double get(LoopedPipeNetwork network);
  }

  /** Write a custom network variable. */
  public interface Setter extends Serializable {
    /**
     * Write a value.
     *
     * @param network network
     * @param value value in the variable unit
     */
    void set(LoopedPipeNetwork network, double value);
  }

  private final String name;
  private final Type type;
  private final String targetName;
  private final String unit;
  private final RateBasis rateBasis;
  private final double lowerBound;
  private final double upperBound;
  private final Getter customGetter;
  private final Setter customSetter;
  private final double[] discreteValues;

  /**
   * Create a built-in network decision variable.
   *
   * @param name stable variable name
   * @param type type
   * @param targetName node or edge name
   * @param unit unit
   * @param rateBasis rate basis
   * @param lowerBound finite lower bound
   * @param upperBound finite upper bound
   */
  public NetworkDecisionVariable(String name, Type type, String targetName, String unit, RateBasis rateBasis,
      double lowerBound, double upperBound) {
    this(name, type, targetName, unit, rateBasis, lowerBound, upperBound, null, null, null);
  }

  private NetworkDecisionVariable(String name, Type type, String targetName, String unit, RateBasis rateBasis,
      double lowerBound, double upperBound, Getter getter, Setter setter, double[] candidates) {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("Decision variable name cannot be empty");
    }
    if (!Double.isFinite(lowerBound) || !Double.isFinite(upperBound) || upperBound <= lowerBound) {
      throw new IllegalArgumentException("Decision variable bounds must be finite and increasing");
    }
    this.name = name;
    this.type = type;
    this.targetName = targetName;
    this.unit = unit;
    this.rateBasis = rateBasis == null ? RateBasis.NONE : rateBasis;
    this.lowerBound = lowerBound;
    this.upperBound = upperBound;
    this.customGetter = getter;
    this.customSetter = setter;
    this.discreteValues = candidates == null ? null : candidates.clone();
  }

  /**
   * Create a custom bounded variable.
   *
   * @param name name
   * @param unit unit
   * @param lowerBound lower bound
   * @param upperBound upper bound
   * @param getter getter
   * @param setter setter
   * @return variable
   */
  public static NetworkDecisionVariable custom(String name, String unit, double lowerBound, double upperBound,
      Getter getter, Setter setter) {
    if (getter == null || setter == null) {
      throw new IllegalArgumentException("Custom getter and setter are required");
    }
    return new NetworkDecisionVariable(name, Type.CUSTOM, null, unit, RateBasis.NONE, lowerBound, upperBound, getter,
        setter, null);
  }

  /**
   * Create a discrete pipe-diameter design variable over existing canonical edge geometry.
   *
   * @param name stable decision name
   * @param edgeName canonical network edge identifier
   * @param candidateDiametersM increasing candidate inner diameters in metres
   * @return discrete pipe-diameter variable
   */
  public static NetworkDecisionVariable pipeDiameter(String name, String edgeName, double[] candidateDiametersM) {
    if (candidateDiametersM == null || candidateDiametersM.length < 2) {
      throw new IllegalArgumentException("At least two pipe-diameter candidates are required");
    }
    double[] values = candidateDiametersM.clone();
    for (int index = 0; index < values.length; index++) {
      if (!(values[index] > 0.0) || !Double.isFinite(values[index])) {
        throw new IllegalArgumentException("Pipe-diameter candidates must be finite and positive");
      }
      if (index > 0 && values[index] <= values[index - 1]) {
        throw new IllegalArgumentException("Pipe-diameter candidates must be strictly increasing");
      }
    }
    return new NetworkDecisionVariable(name, Type.PIPE_DIAMETER, edgeName, "m", RateBasis.NONE, values[0],
        values[values.length - 1], null, null, values);
  }

  /** @return variable name */
  public String getName() {
    return name;
  }

  /** @return variable type */
  public Type getType() {
    return type;
  }

  /** @return node or edge name */
  public String getTargetName() {
    return targetName;
  }

  /** @return unit */
  public String getUnit() {
    return unit;
  }

  /** @return explicit rate basis */
  public RateBasis getRateBasis() {
    return rateBasis;
  }

  /** @return finite lower bound */
  public double getLowerBound() {
    return lowerBound;
  }

  /** @return finite upper bound */
  public double getUpperBound() {
    return upperBound;
  }

  /**
   * Read the current value.
   *
   * @param network network
   * @return value in the configured unit
   */
  public double getValue(LoopedPipeNetwork network) {
    if (type == Type.CUSTOM) {
      return customGetter.get(network);
    }
    if (type == Type.SOURCE_RATE) {
      return fromKgPerSecond(-network.getNode(targetName).getDemand(), unit);
    }
    if (type == Type.SINK_NOMINATION) {
      return fromKgPerSecond(network.getNode(targetName).getDemand(), unit);
    }
    if (type == Type.SOURCE_PRESSURE) {
      return fromPascal(network.getNode(targetName).getPressure(), unit);
    }
    LoopedPipeNetwork.NetworkPipe edge = network.getPipe(targetName);
    switch (type) {
    case CHOKE_OPENING:
      return edge.getChokeOpening();
    case REGULATOR_SET_POINT:
      return fromPascal(edge.getRegulatorSetPoint(), unit);
    case COMPRESSOR_SPEED:
      return edge.getCompressorSpeed();
    case PUMP_SPEED:
      return edge.getPumpSpeed();
    case ROUTE_ALLOCATION:
    case EDGE_AVAILABILITY:
      return edge.getAvailability();
    case PIPE_DIAMETER:
      return edge.getDiameter();
    default:
      throw new IllegalStateException("Unsupported decision variable type: " + type);
    }
  }

  /**
   * Apply a bounded value.
   *
   * @param network network
   * @param value value in the configured unit
   */
  public void setValue(LoopedPipeNetwork network, double value) {
    if (value < lowerBound || value > upperBound || !Double.isFinite(value)) {
      throw new IllegalArgumentException("Value for " + name + " is outside [" + lowerBound + ", " + upperBound + "]");
    }
    if (isDiscrete() && !containsDiscreteValue(value)) {
      throw new IllegalArgumentException("Value for " + name + " is not one of its discrete candidates");
    }
    if (type == Type.CUSTOM) {
      customSetter.set(network, value);
      return;
    }
    if (type == Type.SOURCE_RATE) {
      network.getNode(targetName).setDemand(-toKgPerSecond(value, unit));
      return;
    }
    if (type == Type.SINK_NOMINATION) {
      network.getNode(targetName).setDemand(toKgPerSecond(value, unit));
      return;
    }
    if (type == Type.SOURCE_PRESSURE) {
      network.getNode(targetName).setPressure(toPascal(value, unit));
      return;
    }
    LoopedPipeNetwork.NetworkPipe edge = network.getPipe(targetName);
    switch (type) {
    case CHOKE_OPENING:
      edge.setChokeOpening(value);
      break;
    case REGULATOR_SET_POINT:
      edge.setRegulatorSetPoint(toPascal(value, unit));
      break;
    case COMPRESSOR_SPEED:
      edge.setCompressorSpeed(value);
      break;
    case PUMP_SPEED:
      edge.setPumpSpeed(value);
      break;
    case ROUTE_ALLOCATION:
    case EDGE_AVAILABILITY:
      edge.setAvailability(value);
      break;
    case PIPE_DIAMETER:
      edge.setDiameter(value);
      break;
    default:
      throw new IllegalStateException("Unsupported decision variable type: " + type);
    }
  }

  /** @return true when this variable must use an explicit discrete candidate */
  public boolean isDiscrete() {
    return discreteValues != null;
  }

  /**
   * Get the explicit discrete candidates.
   *
   * @return defensive candidate copy, or an empty array for a continuous variable
   */
  public double[] getDiscreteValues() {
    return discreteValues == null ? new double[0] : discreteValues.clone();
  }

  /**
   * Check an exact candidate with a small floating-point tolerance.
   *
   * @param value proposed value
   * @return true when the value matches a registered candidate
   */
  private boolean containsDiscreteValue(double value) {
    for (double candidate : discreteValues) {
      double tolerance = 1.0e-12 * Math.max(1.0, Math.abs(candidate));
      if (Math.abs(value - candidate) <= tolerance) {
        return true;
      }
    }
    return false;
  }

  private static double toKgPerSecond(double value, String unit) {
    if ("kg/s".equalsIgnoreCase(unit) || "kg/sec".equalsIgnoreCase(unit)) {
      return value;
    }
    if ("kg/hr".equalsIgnoreCase(unit)) {
      return value / 3600.0;
    }
    throw new IllegalArgumentException("Unsupported mass-rate unit: " + unit);
  }

  private static double fromKgPerSecond(double value, String unit) {
    if ("kg/s".equalsIgnoreCase(unit) || "kg/sec".equalsIgnoreCase(unit)) {
      return value;
    }
    if ("kg/hr".equalsIgnoreCase(unit)) {
      return value * 3600.0;
    }
    throw new IllegalArgumentException("Unsupported mass-rate unit: " + unit);
  }

  private static double toPascal(double value, String unit) {
    if ("Pa".equalsIgnoreCase(unit)) {
      return value;
    }
    if ("bara".equalsIgnoreCase(unit) || "bar".equalsIgnoreCase(unit)) {
      return value * 1.0e5;
    }
    if ("barg".equalsIgnoreCase(unit)) {
      return (value + 1.01325) * 1.0e5;
    }
    throw new IllegalArgumentException("Unsupported pressure unit: " + unit);
  }

  private static double fromPascal(double value, String unit) {
    if ("Pa".equalsIgnoreCase(unit)) {
      return value;
    }
    if ("bara".equalsIgnoreCase(unit) || "bar".equalsIgnoreCase(unit)) {
      return value / 1.0e5;
    }
    if ("barg".equalsIgnoreCase(unit)) {
      return value / 1.0e5 - 1.01325;
    }
    throw new IllegalArgumentException("Unsupported pressure unit: " + unit);
  }
}
