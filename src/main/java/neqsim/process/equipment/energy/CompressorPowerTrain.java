package neqsim.process.equipment.energy;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import com.google.gson.GsonBuilder;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import neqsim.process.equipment.compressor.CompressorShaft;
import neqsim.process.equipment.powergeneration.gasturbine.GasTurbineUnit;

/**
 * Steady-state matching of a compressor shaft to a turbine or an electrical drive.
 *
 * <p>
 * Reuses turbine site performance and motor/generator inverse conversion models. A gas-electric train has separate
 * turbine and motor shafts: the compressor speed is passed only to the motor. Electrical auxiliaries are supplied
 * upstream of the motor. Gearbox losses belong to {@link CompressorShaft#setMechanicalEfficiency(double)}. Evaluating a
 * train updates turbine fuel accounting, but does not change compressor duties or dispatch energy ports. Use
 * {@link CoupledProcessEnergySolver} for a shared electrical network.
 * </p>
 *
 * <p>
 * Catalogue turbine maps and default motor efficiencies remain screening assumptions. This adapter does not add
 * gas-generator component matching, turbine output-speed maps, or rotor/control dynamics.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public final class CompressorPowerTrain implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Supply topology. */
  public enum Mode {
    /** Turbine output directly drives the compressor shaft. */
    DIRECT_TURBINE,
    /** An external electrical supply feeds a motor, including its optional VFD. */
    ELECTRIC_MOTOR,
    /** A turbine and generator supply an electrically driven compressor. */
    TURBINE_GENERATOR_MOTOR
  }

  private final Mode mode;
  private final GasTurbineUnit turbine;
  private final Generator generator;
  private final ElectricMotor motor;
  private double electricalSupplyLimitW = Double.POSITIVE_INFINITY;
  private double electricalAuxiliariesW;

  /**
   * Creates a configured topology through the named factory methods.
   *
   * @param mode topology
   * @param turbine turbine, or null for external electricity
   * @param generator generator, or null without gas-electric conversion
   * @param motor motor, or null for direct drive
   */
  private CompressorPowerTrain(Mode mode, GasTurbineUnit turbine, Generator generator, ElectricMotor motor) {
    this.mode = mode;
    this.turbine = turbine;
    this.generator = generator;
    this.motor = motor;
  }

  /**
   * Creates a direct mechanical turbine drive.
   *
   * @param turbine site-rated turbine with a fuel stream
   * @return configured train
   */
  public static CompressorPowerTrain directDrive(GasTurbineUnit turbine) {
    return new CompressorPowerTrain(Mode.DIRECT_TURBINE, Objects.requireNonNull(turbine, "turbine"), null, null);
  }

  /**
   * Creates an externally supplied electrical drive.
   *
   * @param motor motor with constant efficiency or a motor/VFD performance model
   * @return configured train
   */
  public static CompressorPowerTrain electricDrive(ElectricMotor motor) {
    return new CompressorPowerTrain(Mode.ELECTRIC_MOTOR, null, null, Objects.requireNonNull(motor, "motor"));
  }

  /**
   * Creates a dedicated turbine-generator-motor drive.
   *
   * @param turbine turbine with a fuel stream; its output speed is independent of compressor speed
   * @param generator shaft-to-electric converter, optionally with a part-load map
   * @param motor motor, optionally with VFD and environmental derating
   * @return configured train
   */
  public static CompressorPowerTrain gasElectricDrive(GasTurbineUnit turbine, Generator generator,
      ElectricMotor motor) {
    return new CompressorPowerTrain(Mode.TURBINE_GENERATOR_MOTOR, Objects.requireNonNull(turbine, "turbine"),
        Objects.requireNonNull(generator, "generator"), Objects.requireNonNull(motor, "motor"));
  }

  /**
   * Sets electrical power available to the motor and auxiliaries.
   *
   * @param powerW non-negative power in W; zero represents a blackout, positive infinity removes the cap
   */
  public void setElectricalSupplyLimitW(double powerW) {
    if (Double.isNaN(powerW) || powerW < 0.0) {
      throw new IllegalArgumentException("Electrical supply must be non-negative; infinity removes the limit");
    }
    if (motor == null) {
      throw new IllegalStateException("An electrical supply limit requires an electric drive");
    }
    electricalSupplyLimitW = powerW;
  }

  /**
   * Sets fixed electrical auxiliaries consumed whenever the compressor drive delivers positive power.
   *
   * @param powerW non-negative finite auxiliary power in W
   */
  public void setElectricalAuxiliariesW(double powerW) {
    requireNonNegative(powerW, "Auxiliary power");
    if (motor == null) {
      throw new IllegalStateException("Electrical auxiliaries require an electric drive");
    }
    electricalAuxiliariesW = powerW;
  }

  /**
   * Gets the supply topology.
   *
   * @return topology
   */
  public Mode getMode() {
    return mode;
  }

  /**
   * Gets the turbine used for fuel and exhaust accounting.
   *
   * <p>
   * Returns the connected equipment itself. Configuration changes through this reference affect subsequent matching,
   * and matching updates its fuel accounting. The train and its equipment must not be evaluated concurrently.
   * </p>
   *
   * @return live turbine, or null for an externally supplied motor
   */
  @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "The turbine is connected process equipment; copying would detach configuration and accounting")
  public GasTurbineUnit getTurbine() {
    return turbine;
  }

  /**
   * Gets available compressor-driver output including every configured conversion limit.
   *
   * <p>
   * Bisection inverts the complete chain, including part-load efficiencies and idle losses. Inverse input power must
   * increase with useful output over the configured range. No fuel calculation or energy-port dispatch is performed.
   * </p>
   *
   * @param speedRpm compressor driver speed in rpm
   * @param ambientTemperatureK turbine ambient temperature in K
   * @return available mechanical output in W; infinity for an unbounded constant-efficiency electrical drive
   */
  public double getAvailablePowerW(double speedRpm, double ambientTemperatureK) {
    requireOperatingConditions(speedRpm, ambientTemperatureK);
    if (speedRpm == 0.0) {
      return 0.0;
    }
    double turbinePower = turbine == null ? Double.POSITIVE_INFINITY
        : turbine.getAvailablePowerAtAmbient(ambientTemperatureK);
    if ((!Double.isFinite(turbinePower) && turbine != null) || turbinePower < 0.0) {
      throw new IllegalStateException("Turbine returned invalid site power");
    }
    if (motor == null) {
      return turbinePower;
    }
    motor.setOperatingSpeed(speedRpm);
    if (motor.hasPerformanceModel() && Math.abs(motor.getOperatingSpeed() - speedRpm) > 1.0e-6) {
      throw new IllegalStateException(
          "Connected motor shaft speed differs from compressor driver speed; synchronize the shafts");
    }
    if (motor.isTripped() || generator != null && generator.isTripped()) {
      return 0.0;
    }
    double upper = Math.min(motor.getAvailableShaftPower(), motor.getMaximumInputPower());
    upper = Math.min(upper, Math.max(0.0, electricalSupplyLimitW - electricalAuxiliariesW));
    if (generator != null) {
      upper = Math.min(upper, Math.min(turbinePower, generator.getMaximumInputPower()));
      upper = Math.min(upper, generator.getRatedOutputPower());
    }
    if (upper == Double.POSITIVE_INFINITY || upper <= 0.0) {
      return upper;
    }
    if (canSupply(upper, turbinePower)) {
      return upper;
    }
    double lower = 0.0;
    for (int iteration = 0; iteration < 60; iteration++) {
      double trial = lower + 0.5 * (upper - lower);
      if (canSupply(trial, turbinePower)) {
        lower = trial;
      } else {
        upper = trial;
      }
    }
    return lower;
  }

  /**
   * Checks inverse conversion and supply limits at one output.
   *
   * @param outputW motor mechanical output in W
   * @param turbinePowerW available turbine shaft power in W
   * @return true when all upstream limits permit this output
   */
  private boolean canSupply(double outputW, double turbinePowerW) {
    try {
      double electrical = motor.getRequiredInputPowerForOutput(outputW) + electricalAuxiliariesW;
      if (electrical > electricalSupplyLimitW) {
        return false;
      }
      return generator == null || electrical <= generator.getRatedOutputPower()
          && generator.getRequiredInputPowerForOutput(electrical) <= turbinePowerW;
    } catch (IllegalArgumentException ex) {
      return false;
    }
  }

  /**
   * Matches actual delivery to a requested mechanical duty and calculates fuel/electricity and losses.
   *
   * <p>
   * A shortfall is reported without overwriting the compressor's calculated pressure or duty. The caller must reduce
   * throughput or re-solve speed before accepting that compressor point. Turbine minimum-load violations are reported
   * separately: this method does not invent a dump load or a stable low-load combustion regime.
   * </p>
   *
   * @param requiredPowerW driver output required, including downstream gearbox losses, in W
   * @param speedRpm compressor driver speed in rpm
   * @param ambientTemperatureK turbine ambient temperature in K
   * @return immutable operating point
   */
  public Result evaluate(double requiredPowerW, double speedRpm, double ambientTemperatureK) {
    return evaluate(requiredPowerW, speedRpm, ambientTemperatureK, Double.POSITIVE_INFINITY);
  }

  /**
   * Matches duty with an additional installed mechanical output limit.
   *
   * @param requiredPowerW driver output required in W
   * @param speedRpm compressor driver speed in rpm
   * @param ambientTemperatureK turbine ambient temperature in K
   * @param outputLimitW additional non-negative output limit in W; positive infinity removes this limit
   * @return immutable operating point
   */
  public Result evaluate(double requiredPowerW, double speedRpm, double ambientTemperatureK, double outputLimitW) {
    requireNonNegative(requiredPowerW, "Required power");
    if (Double.isNaN(outputLimitW) || outputLimitW < 0.0) {
      throw new IllegalArgumentException("Output limit must be non-negative; infinity removes the limit");
    }
    double available = Math.min(getAvailablePowerW(speedRpm, ambientTemperatureK), outputLimitW);
    double delivered = Math.min(requiredPowerW, available);
    double electrical = motor == null || delivered <= 0.0 ? 0.0
        : motor.getRequiredInputPowerForOutput(delivered) + electricalAuxiliariesW;
    double turbineOutput = 0.0;
    if (motor == null) {
      turbineOutput = delivered;
    } else if (generator != null && electrical > 0.0) {
      turbineOutput = generator.getRequiredInputPowerForOutput(electrical);
    }
    if (turbine != null) {
      if (turbine.getSpec() == null || turbine.getInletStream() == null) {
        throw new IllegalStateException("Configure a turbine rating and fuel stream before matching a power train");
      }
      turbine.setAmbientTemperatureK(ambientTemperatureK);
      turbine.setDemandedPower(turbineOutput);
      turbine.run();
      turbineOutput = turbine.getShaftPowerW();
    }
    double source = turbine == null ? electrical : turbineOutput;
    return new Result(mode, requiredPowerW, delivered, available, electrical, turbineOutput, source - delivered,
        turbine == null ? 0.0 : turbine.getFuelEnergyRateW(), turbine == null ? 0.0 : turbine.getFuelMassFlowKgPerS(),
        turbine == null ? 0.0 : turbine.getCO2EmissionKgPerS(), turbine != null && turbine.isBelowMinLoad());
  }

  /**
   * Checks non-negative finite power input.
   *
   * @param value value to check
   * @param label quantity name
   */
  private static void requireNonNegative(double value, String label) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(label + " must be non-negative and finite");
    }
  }

  /**
   * Checks non-negative finite speed and positive absolute temperature.
   *
   * @param speedRpm speed in rpm
   * @param ambientTemperatureK temperature in K
   */
  private static void requireOperatingConditions(double speedRpm, double ambientTemperatureK) {
    if (!Double.isFinite(speedRpm) || speedRpm < 0.0 || !Double.isFinite(ambientTemperatureK)
        || ambientTemperatureK <= 0.0) {
      throw new IllegalArgumentException("Speed must be non-negative and temperature positive; both must be finite");
    }
  }

  /**
   * Immutable supply-side operating point; powers are positive in W and mass rates in kg/s.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Mode mode;
    private final double requiredPowerW;
    private final double deliveredPowerW;
    private final double availablePowerW;
    private final double electricalInputW;
    private final double turbineShaftW;
    private final double conversionLossW;
    private final double fuelEnergyW;
    private final double fuelMassKgPerS;
    private final double co2KgPerS;
    private final boolean belowMinimumLoad;

    /**
     * Creates an immutable result.
     *
     * @param mode supply topology
     * @param requiredPowerW requested mechanical output in W
     * @param deliveredPowerW delivered mechanical output in W
     * @param availablePowerW available mechanical output in W
     * @param electricalInputW motor and auxiliary electrical demand in W
     * @param turbineShaftW turbine shaft output in W
     * @param conversionLossW generator, motor and auxiliary losses in W, excluding turbine exhaust heat
     * @param fuelEnergyW fuel LHV energy input in W
     * @param fuelMassKgPerS fuel mass flow in kg/s
     * @param co2KgPerS fuel-derived carbon dioxide rate in kg/s
     * @param belowMinimumLoad whether the turbine is below its stable-load screening limit
     */
    private Result(Mode mode, double requiredPowerW, double deliveredPowerW, double availablePowerW,
        double electricalInputW, double turbineShaftW, double conversionLossW, double fuelEnergyW,
        double fuelMassKgPerS, double co2KgPerS, boolean belowMinimumLoad) {
      this.mode = mode;
      this.requiredPowerW = requiredPowerW;
      this.deliveredPowerW = deliveredPowerW;
      this.availablePowerW = availablePowerW;
      this.electricalInputW = electricalInputW;
      this.turbineShaftW = turbineShaftW;
      this.conversionLossW = conversionLossW;
      this.fuelEnergyW = fuelEnergyW;
      this.fuelMassKgPerS = fuelMassKgPerS;
      this.co2KgPerS = co2KgPerS;
      this.belowMinimumLoad = belowMinimumLoad;
    }

    /**
     * Gets the requested mechanical output.
     *
     * @return required mechanical output in W
     */
    public double getRequiredPowerW() {
      return requiredPowerW;
    }

    /**
     * Gets the mechanical output actually delivered.
     *
     * @return delivered mechanical output in W
     */
    public double getDeliveredPowerW() {
      return deliveredPowerW;
    }

    /**
     * Gets the available mechanical output.
     *
     * @return available mechanical output in W
     */
    public double getAvailablePowerW() {
      return availablePowerW;
    }

    /**
     * Gets the unmet mechanical demand.
     *
     * @return unmet mechanical demand in W
     */
    public double getPowerShortfallW() {
      return Math.max(0.0, requiredPowerW - deliveredPowerW);
    }

    /**
     * Gets motor and auxiliary electrical demand.
     *
     * @return motor and auxiliary electrical input in W
     */
    public double getElectricalInputW() {
      return electricalInputW;
    }

    /**
     * Gets the turbine shaft output.
     *
     * @return turbine mechanical output in W
     */
    public double getTurbineShaftW() {
      return turbineShaftW;
    }

    /**
     * Gets conversion and auxiliary losses.
     *
     * @return conversion and auxiliary losses excluding turbine fuel conversion in W
     */
    public double getConversionLossW() {
      return conversionLossW;
    }

    /**
     * Gets the fuel lower-heating-value energy input.
     *
     * @return fuel LHV input in W
     */
    public double getFuelEnergyW() {
      return fuelEnergyW;
    }

    /**
     * Gets fuel mass consumption.
     *
     * @return fuel consumption in kg/s
     */
    public double getFuelMassKgPerS() {
      return fuelMassKgPerS;
    }

    /**
     * Gets the fuel-derived carbon dioxide rate.
     *
     * @return fuel-derived carbon dioxide rate in kg/s
     */
    public double getCO2KgPerS() {
      return co2KgPerS;
    }

    /**
     * Checks the turbine minimum-load limit.
     *
     * @return true when the running turbine is below its minimum-load limit
     */
    public boolean isBelowMinimumLoad() {
      return belowMinimumLoad;
    }

    /**
     * Checks supply against requested mechanical power.
     *
     * @return true when supply meets demand within numerical precision
     */
    public boolean isPowerFeasible() {
      return getPowerShortfallW() <= Math.max(1.0e-6, requiredPowerW * 1.0e-10);
    }

    /**
     * Checks supply power and minimum-load feasibility.
     *
     * @return true when power is feasible and the turbine is not below its minimum-load limit
     */
    public boolean isFeasible() {
      return isPowerFeasible() && !belowMinimumLoad;
    }

    /**
     * Checks the supply conversion energy balance.
     *
     * @return supply/conversion energy balance residual in W, excluding turbine fuel conversion
     */
    public double getEnergyBalanceResidualW() {
      return (mode == Mode.ELECTRIC_MOTOR ? electricalInputW : turbineShaftW) - deliveredPowerW - conversionLossW;
    }

    /**
     * Builds a structured result with explicit units.
     *
     * @return structured result with explicit units; unbounded capability is represented by null
     */
    public Map<String, Object> toMap() {
      Map<String, Object> values = new LinkedHashMap<String, Object>();
      values.put("mode", mode.name());
      values.put("feasible", isFeasible());
      values.put("powerFeasible", isPowerFeasible());
      values.put("belowMinimumLoad", belowMinimumLoad);
      values.put("requiredPowerW", requiredPowerW);
      values.put("deliveredPowerW", deliveredPowerW);
      values.put("availablePowerW", Double.isFinite(availablePowerW) ? Double.valueOf(availablePowerW) : null);
      values.put("powerShortfallW", getPowerShortfallW());
      values.put("electricalInputW", electricalInputW);
      values.put("turbineShaftW", turbineShaftW);
      values.put("conversionLossW", conversionLossW);
      values.put("fuelEnergyW", fuelEnergyW);
      values.put("fuelMassKgPerS", fuelMassKgPerS);
      values.put("co2KgPerS", co2KgPerS);
      values.put("energyBalanceResidualW", getEnergyBalanceResidualW());
      return values;
    }

    /**
     * Serializes the operating point with explicit units.
     *
     * @return JSON operating point with explicit units
     */
    public String toJson() {
      return new GsonBuilder().serializeNulls().create().toJson(toMap());
    }
  }
}
