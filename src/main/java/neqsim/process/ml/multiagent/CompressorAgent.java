package neqsim.process.ml.multiagent;

import neqsim.process.ml.Constraint;
import neqsim.process.ml.StateVector;
import neqsim.process.equipment.compressor.Compressor;

/**
 * RL agent for compressor control with anti-surge protection.
 *
 * <p>
 * Controls:
 * <ul>
 * <li>Compressor speed for pressure/flow control</li>
 * <li>Recycle valve for anti-surge protection</li>
 * </ul>
 *
 * <p>
 * Observations:
 * <ul>
 * <li>inlet_pressure - Suction pressure [bar]</li>
 * <li>outlet_pressure - Discharge pressure [bar]</li>
 * <li>compression_ratio - P_out/P_in</li>
 * <li>surge_fraction - Distance from surge line</li>
 * <li>speed - Current speed [normalized]</li>
 * <li>power - Shaft power [normalized]</li>
 * <li>pressure_error - Deviation from setpoint</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class CompressorAgent extends ProcessAgent {
  private static final long serialVersionUID = 1000L;

  private final Compressor compressor;
  private double currentSpeedFraction = 0.5;
  private double minSpeed = 3000;
  private double maxSpeed = 12000;

  /**
   * Create compressor agent.
   *
   * @param agentId agent ID
   * @param compressor the compressor
   */
  public CompressorAgent(String agentId, Compressor compressor) {
    super(agentId, compressor);
    this.compressor = compressor;

    initializeSpaces();
  }

  private void initializeSpaces() {
    // Observation space
    observationNames = new String[] {"inlet_pressure", "outlet_pressure", "compression_ratio",
        "surge_fraction", "speed", "power", "pressure_error"};

    // Action space: speed change [-0.05, 0.05] normalized
    actionNames = new String[] {"speed_delta"};
    actionLow = new double[] {-0.05};
    actionHigh = new double[] {0.05};

    // Default setpoints
    setSetpoint("outlet_pressure", 50.0, 10.0);

    // Default constraints
    localConstraints.addHardRange("surge_protection", "surge_fraction", 1.1, Double.MAX_VALUE,
        "fraction"); // Must stay above surge line
    localConstraints.addSoftRange("discharge_pressure", "outlet_pressure", 0.0, 100.0, "bar");
    localConstraints.add(new Constraint("power_limit", "Maximum power limit", Constraint.Type.HARD,
        Constraint.Category.EQUIPMENT, "power", 0.0, 10000.0, "kW"));
  }

  @Override
  public double[] getLocalObservation(StateVector globalState) {
    double inletP = globalState.getValue("inlet_pressure");
    double outletP = globalState.getValue("outlet_pressure");
    double compRatio = globalState.getValue("compression_ratio");
    double surgeFrac = globalState.getValue("surge_fraction");
    double speed = globalState.getValue("speed");
    double power = globalState.getValue("power");

    double pressureSP = getSetpoint("outlet_pressure");
    double pressureError = Double.isNaN(pressureSP) ? 0.0 : (outletP - pressureSP) / 20.0;

    // Normalize values
    return new double[] {inletP / 50.0, // Normalized inlet pressure
        outletP / 100.0, // Normalized outlet pressure
        (compRatio - 1.0) / 5.0, // Normalized compression ratio
        surgeFrac / 2.0, // Normalized surge fraction
        currentSpeedFraction, // Current speed [0-1]
        power / 5000.0, // Normalized power
        pressureError // Pressure error
    };
  }

  @Override
  public void applyAction(double[] action) {
    if (action.length > 0) {
      currentSpeedFraction = Math.max(0.0, Math.min(1.0, currentSpeedFraction + action[0]));
      double actualSpeed = minSpeed + currentSpeedFraction * (maxSpeed - minSpeed);
      compressor.setSpeed(actualSpeed);
    }
  }

  @Override
  public double computeReward(StateVector globalState, double[] action) {
    double reward = super.computeReward(globalState, action);

    // Surge margin bonus
    double surgeFrac = globalState.getValue("surge_fraction");
    if (!Double.isNaN(surgeFrac) && surgeFrac > 1.0) {
      reward += 0.5 * Math.min(surgeFrac - 1.0, 0.5); // Bonus for staying away from surge
    }

    // Energy efficiency penalty
    double power = globalState.getValue("power");
    if (!Double.isNaN(power)) {
      reward -= 0.001 * power; // Small penalty for power consumption
    }

    // Action smoothness
    if (action.length > 0) {
      reward -= 0.5 * action[0] * action[0];
    }

    return reward;
  }

  @Override
  public boolean isTerminated(StateVector globalState) {
    // Terminate on surge
    double surgeFrac = globalState.getValue("surge_fraction");
    if (!Double.isNaN(surgeFrac) && surgeFrac < 1.0) {
      return true; // In surge region
    }
    return super.isTerminated(globalState);
  }

  /**
   * Set discharge pressure setpoint.
   *
   * @param pressure target pressure [bar]
   */
  public void setDischargePressureSetpoint(double pressure) {
    setSetpoint("outlet_pressure", pressure, 10.0);
  }

  /**
   * Get the compressor.
   *
   * @return compressor
   */
  public Compressor getCompressor() {
    return compressor;
  }

  /**
   * Set speed range.
   *
   * @param minRpm minimum speed
   * @param maxRpm maximum speed
   */
  public void setSpeedRange(double minRpm, double maxRpm) {
    this.minSpeed = minRpm;
    this.maxSpeed = maxRpm;
  }
}
