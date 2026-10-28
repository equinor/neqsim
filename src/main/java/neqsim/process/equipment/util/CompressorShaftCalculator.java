package neqsim.process.equipment.util;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import neqsim.process.automation.AutomationAddressable;
import neqsim.process.automation.SimulationVariable;
import neqsim.process.automation.SimulationVariable.VariableType;
import neqsim.process.equipment.capacity.CapacityConstrainedEquipment;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.compressor.CompressorShaft;
import neqsim.process.equipment.compressor.CompressorShaftReport;
import neqsim.process.equipment.powergeneration.GasTurbine;

/**
 * Process-integrated common-speed controller for a {@link CompressorShaft}.
 *
 * <p>
 * Several compressor bodies on ONE driver shaft must turn at the same speed, and only the string's final discharge
 * pressure is a controlled target (the intermediate inter-body pressures float). The stand-alone
 * {@link CompressorShaft#solveSpeed} re-solves the whole flowsheet many times through an external callback. This
 * calculator instead converges the common speed <em>inside</em> the normal
 * {@link neqsim.process.processmodel.ProcessSystem#run()} iteration: like {@link AntiSurgeCalculator}, it runs on every
 * internal pass and takes one damped secant step on the common speed toward the target discharge, so the shaft speed
 * converges together with the recycle loops in a single {@code run()} — no separate N full-field solves.
 * </p>
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * CompressorShaft shaft = new CompressorShaft("23-KA train");
 * shaft.addCompressor(rc1);
 * shaft.addCompressor(rc2);
 * shaft.addCompressor(rc3); // reference (last body)
 * CompressorShaftCalculator shaftCalc = new CompressorShaftCalculator("23-KA shaft speed", shaft, rc3, 49.0, "bara");
 * shaftCalc.setMaxShaftPower(24.0, "MW"); // or shaftCalc.setTurbineDriver(gasTurbine)
 * process.add(shaftCalc);
 * process.run(); // shaft speed converges with the recycles
 * double rpm = shaftCalc.getSpeed();
 * double util = shaftCalc.getShaftPowerUtilization(); // whole-string power vs the driver limit
 * }</pre>
 *
 * <p>
 * When a power limit is configured the calculator carries a {@code shaftPower} capacity constraint, so the shaft shows
 * up as an ordinary unit in {@code getUtilizationSnapshotJson()} and can bind as the plant bottleneck.
 * </p>
 *
 * <h2>Automation addresses</h2>
 *
 * <p>
 * The calculator publishes the shaft to {@link neqsim.process.automation.ProcessAutomation} (see
 * {@link AutomationAddressable}); with the calculator named {@code HT shaft} the addresses are
 * </p>
 *
 * <table>
 * <caption>Shaft automation addresses</caption>
 * <tr>
 * <th>Address (after the unit name)</th>
 * <th>Type</th>
 * <th>Meaning</th>
 * </tr>
 * <tr>
 * <td>speed, targetPressure, ambientTemperature, maxShaftPower</td>
 * <td>input</td>
 * <td>common speed [rpm] (inside the speed band), controlled discharge pressure, ambient [C], explicit driver limit
 * [kW]</td>
 * </tr>
 * <tr>
 * <td>power, requiredDriverPower, availablePower, powerUtilization, overPower, feasible, minSpeed, maxSpeed</td>
 * <td>output</td>
 * <td>shaft power balance [kW], utilization [fraction], flags (1 or 0), speed band [rpm]</td>
 * </tr>
 * <tr>
 * <td>member.NAME.speed, power, surgeMargin, stonewallMargin, recycleFraction, recyclePower, dischargeMargin</td>
 * <td>output</td>
 * <td>per member report values</td>
 * </tr>
 * <tr>
 * <td>member.NAME.active, member.NAME.gearRatio, applyRecycle</td>
 * <td>input</td>
 * <td>active flag (0 or 1), gear ratio, and a trigger (write 1) that runs
 * {@link CompressorShaft#applyRequiredRecycle()}</td>
 * </tr>
 * </table>
 *
 * @author NeqSim Development Team
 * @version 1.1
 */
public class CompressorShaftCalculator extends Calculator
    implements CapacityConstrainedEquipment, AutomationAddressable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1L;

  /** The shaft whose bodies share a common speed and a common driver. */
  private CompressorShaft shaft;

  /** Compressor bodies on the shaft (in flow order). */
  private final List<Compressor> bodies = new ArrayList<Compressor>();

  /** The body whose outlet pressure is the controlled target. */
  private Compressor reference;

  /** Target outlet pressure of the reference body. */
  private double targetPressure;

  /** Pressure unit for the target and read-back. */
  private String pressureUnit = "bara";

  /** Lower speed bound in rpm. */
  private double minSpeed = 500.0;

  /** Upper speed bound in rpm. */
  private double maxSpeed = 25000.0;

  /** Maximum fractional speed change per iteration (damping for stability). */
  private double maxStepFraction = 0.10;

  /** True while the speed band follows the driver band of the shaft (no explicit {@link #setSpeedBounds}). */
  private boolean speedBandFromShaft = true;

  /** Current common shaft speed in rpm. */
  private double speed = 10000.0;

  /** Previous speed (for the secant step). */
  private double prevSpeed = Double.NaN;

  /** Previous outlet-pressure error (for the secant step). */
  private double prevError = Double.NaN;

  /** Tolerance (in the pressure unit) used to classify the solve as feasible / saturated. */
  private double pressureTolerance = 0.05;

  /** Pressure-control action for a target below the minimum-speed capability. */
  private CompressorShaft.PressureControl pressureControl = CompressorShaft.PressureControl.NONE;

  /** Feasibility result of the most recent {@link #run(UUID)} pass ({@code null} until first run). */
  private CompressorShaft.SolveResult lastResult = null;

  /**
   * Constructor for CompressorShaftCalculator.
   *
   * @param name the calculator name
   */
  public CompressorShaftCalculator(String name) {
    super(name);
  }

  /**
   * Constructor with explicit wiring from a {@link CompressorShaft}.
   *
   * @param name the calculator name
   * @param shaft the shaft whose bodies share a common speed
   * @param reference the body whose outlet pressure is the controlled target; {@code null} uses the last body added to
   * the shaft
   * @param targetPressure the required outlet pressure of the reference body
   * @param unit the pressure unit (e.g. "bara")
   */
  public CompressorShaftCalculator(String name, CompressorShaft shaft, Compressor reference, double targetPressure,
      String unit) {
    super(name);
    this.shaft = shaft;
    for (Compressor compressor : shaft.getCompressors()) {
      bodies.add(compressor);
      addInputVariable(compressor);
    }
    if (reference != null) {
      this.reference = reference;
    } else if (!bodies.isEmpty()) {
      this.reference = bodies.get(bodies.size() - 1);
    }
    this.targetPressure = targetPressure;
    this.pressureUnit = unit;
    this.speed = shaft.getSpeed();
    ensureShaftPowerConstraint();
  }

  /**
   * Get the shaft this calculator controls.
   *
   * @return the shaft, or {@code null} if the calculator was built with the name-only constructor
   */
  public CompressorShaft getShaft() {
    return shaft;
  }

  /**
   * Set an explicit installed shaft power limit for the whole string. This alone gives a shaft utilization; no driver
   * model is required.
   *
   * @param maxPower the installed shaft power limit
   * @param unit power unit: "W", "kW" or "MW"
   */
  public void setMaxShaftPower(double maxPower, String unit) {
    if (shaft != null) {
      shaft.setMaxShaftPower(maxPower, unit);
      ensureShaftPowerConstraint();
    }
  }

  /**
   * Attach a {@link GasTurbine} as the driver of this shaft: the bodies become driven loads of the turbine (so its fuel
   * gas tracks the string duty) and its rated power limits the shaft.
   *
   * @param turbine the driving gas turbine
   */
  public void setTurbineDriver(GasTurbine turbine) {
    if (shaft != null) {
      shaft.setTurbineDriver(turbine);
      ensureShaftPowerConstraint();
    }
  }

  /**
   * Set the gearbox / coupling efficiency between the driver output and the compressor bodies.
   *
   * @param efficiency the mechanical efficiency in the range (0, 1]
   */
  public void setMechanicalEfficiency(double efficiency) {
    if (shaft != null) {
      shaft.setMechanicalEfficiency(efficiency);
    }
  }

  /**
   * Get the shaft power utilization: the whole-string power (plus gearbox losses) divided by the driver power available
   * at the current speed.
   *
   * @return the utilization as a fraction (1.0 = at the limit), or 0 when no power limit is configured
   */
  public double getShaftPowerUtilization() {
    return shaft == null ? 0.0 : shaft.getPowerUtilization();
  }

  /**
   * Get the total power drawn by the bodies on this shaft.
   *
   * @param unit power unit: "W", "kW" or "MW"
   * @return the total shaft power in the requested unit
   */
  public double getTotalPower(String unit) {
    return shaft == null ? 0.0 : shaft.getTotalPower(unit);
  }

  /**
   * Register the {@code shaftPower} capacity constraint once a power limit is available, so the shaft participates in
   * bottleneck detection and the utilization snapshot. The constraint tracks utilization in percent, matching how the
   * compressor power constraint is reported.
   */
  private void ensureShaftPowerConstraint() {
    if (shaft == null || getCapacityConstraints().containsKey("shaftPower")
        || shaft.getAvailableShaftPower("W") <= 0.0) {
      return;
    }
    addCapacityConstraint(new CapacityConstraint("shaftPower", "%", CapacityConstraint.ConstraintType.HARD)
        .setDesignValue(100.0).setMaxValue(110.0).setWarningThreshold(0.9).setDataSource("equipment")
        .setDescription("Common-shaft power vs installed driver power")
        .setValueSupplier(() -> shaft.getPowerUtilization() * 100.0));
  }

  /**
   * Set the speed search bounds.
   *
   * @param min the lower speed bound in rpm
   * @param max the upper speed bound in rpm
   */
  public void setSpeedBounds(double min, double max) {
    this.minSpeed = min;
    this.maxSpeed = max;
    this.speedBandFromShaft = false;
  }

  /**
   * Set the maximum fractional speed change allowed per iteration (damping).
   *
   * @param fraction the maximum fractional step (e.g. 0.10 for 10%)
   */
  public void setMaxStepFraction(double fraction) {
    this.maxStepFraction = fraction;
  }

  /**
   * Get the lower speed bound in force (explicit, or taken from the driver of the shaft).
   *
   * @return the lower bound in rpm
   */
  public double getMinSpeed() {
    return minSpeed;
  }

  /**
   * Get the upper speed bound in force (explicit, or taken from the driver of the shaft).
   *
   * @return the upper bound in rpm
   */
  public double getMaxSpeed() {
    return maxSpeed;
  }

  /**
   * Get the current common shaft speed.
   *
   * @return the shaft speed in rpm
   */
  public double getSpeed() {
    return speed;
  }

  /**
   * Set the pressure-control action used when the requested pressure is below what the string makes at minimum speed.
   *
   * @param control the pressure-control action (must not be {@code null})
   */
  public void setPressureControl(CompressorShaft.PressureControl control) {
    if (control != null) {
      this.pressureControl = control;
    }
  }

  /**
   * Get the pressure-control action used for a target below the minimum-speed capability.
   *
   * @return the configured pressure-control action
   */
  public CompressorShaft.PressureControl getPressureControl() {
    return pressureControl;
  }

  /**
   * Set the tolerance (in the pressure unit) used to classify the solve as feasible / saturated.
   *
   * @param tolerance the tolerance (must be positive)
   */
  public void setPressureTolerance(double tolerance) {
    if (tolerance > 0.0) {
      this.pressureTolerance = tolerance;
    }
  }

  /**
   * Get the feasibility result of the most recent {@link #run(UUID)} pass.
   *
   * @return the last solve result, or {@code null} if the calculator has not run yet
   */
  public CompressorShaft.SolveResult getLastSolveResult() {
    return lastResult;
  }

  /**
   * Whether the most recent pass reached (or is converging to) a feasible operating point.
   *
   * @return {@code true} if no pass has run yet or the last pass was feasible; {@code false} if the target is saturated
   * above the maximum speed (or below the minimum speed with no pressure control)
   */
  public boolean isFeasible() {
    return lastResult == null || lastResult.isFeasible();
  }

  /**
   * Put every body into fixed-speed, chart-forward mode at the current common speed (gear ratios and idle members of
   * the {@link CompressorShaft} are respected).
   */
  private void applySpeed() {
    if (shaft != null) {
      shaft.setSpeed(speed);
      return;
    }
    for (Compressor compressor : bodies) {
      compressor.setSolveSpeed(false);
      compressor.setSpeed(speed);
      if (compressor.getCompressorChart() != null) {
        compressor.getCompressorChart().setUseCompressorChart(true);
      }
      compressor.setUsePolytropicCalc(true);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    if (bodies.isEmpty() || reference == null) {
      return;
    }
    ensureShaftPowerConstraint();
    if (shaft != null && speedBandFromShaft && shaft.isSpeedBandFromDriver()) {
      shaft.refreshSpeedBandFromDriver();
      minSpeed = shaft.getMinSpeed();
      maxSpeed = shaft.getMaxSpeed();
    }
    double pout = reference.getOutletStream().getPressure(pressureUnit);
    double error = pout - targetPressure; // drive to zero
    double newSpeed;
    if (Double.isNaN(prevError) || Math.abs(error - prevError) < 1.0e-9) {
      // First step (or a stalled secant): proportional bump. Higher speed raises discharge, so a
      // negative error (pout below target) increases speed.
      double frac = -error / Math.max(Math.abs(targetPressure), 1.0);
      frac = Math.max(-maxStepFraction, Math.min(maxStepFraction, frac));
      newSpeed = speed * (1.0 + frac);
    } else {
      // Secant step on the (speed -> error) relation.
      newSpeed = speed - error * (speed - prevSpeed) / (error - prevError);
    }
    // Damp the step and clamp to the speed bounds for stability inside the recycle iteration.
    double maxStep = Math.abs(speed) * maxStepFraction;
    if (newSpeed > speed + maxStep) {
      newSpeed = speed + maxStep;
    }
    if (newSpeed < speed - maxStep) {
      newSpeed = speed - maxStep;
    }
    if (newSpeed < minSpeed) {
      newSpeed = minSpeed;
    }
    if (newSpeed > maxSpeed) {
      newSpeed = maxSpeed;
    }
    prevSpeed = speed;
    prevError = error;
    speed = newSpeed;
    applySpeed();
    updateFeasibility();
    setCalculationIdentifier(id);
  }

  /**
   * Classify the current pass as feasible / pressure-controlled / infeasible and store the result. The target is
   * saturated when the common speed is pinned at a bound and the reference discharge still misses the target: above the
   * maximum-speed capability (infeasible) or below the minimum-speed capability (infeasible unless a
   * {@link CompressorShaft.PressureControl} sheds the surplus head).
   */
  private void updateFeasibility() {
    double pout = reference.getOutletStream().getPressure(pressureUnit);
    double error = pout - targetPressure;
    boolean atMax = speed >= maxSpeed - 1.0e-6;
    boolean atMin = speed <= minSpeed + 1.0e-6;
    if (atMax && error < -pressureTolerance) {
      lastResult = new CompressorShaft.SolveResult(false, CompressorShaft.SolveStatus.PRESSURE_ABOVE_MAX_SPEED,
          targetPressure, pout, Double.NaN, pout, speed, pressureUnit, "target above maximum-speed capability");
    } else if (atMin && error > pressureTolerance) {
      if (pressureControl != CompressorShaft.PressureControl.NONE) {
        try {
          reference.getOutletStream().setPressure(targetPressure, pressureUnit);
          reference.getOutletStream().run();
        } catch (RuntimeException ex) {
          // best-effort screening choke; leave the discharge as produced if it cannot be set
        }
        lastResult = new CompressorShaft.SolveResult(true, CompressorShaft.SolveStatus.PRESSURE_CONTROLLED,
            targetPressure, targetPressure, pout, Double.NaN, speed, pressureUnit,
            "surplus head shed by " + pressureControl);
      } else {
        lastResult = new CompressorShaft.SolveResult(false, CompressorShaft.SolveStatus.PRESSURE_BELOW_MIN_SPEED,
            targetPressure, pout, pout, Double.NaN, speed, pressureUnit,
            "target below minimum-speed capability; no pressure control");
      }
    } else {
      boolean reached = Math.abs(error) < pressureTolerance;
      lastResult = new CompressorShaft.SolveResult(true, CompressorShaft.SolveStatus.FEASIBLE, targetPressure, pout,
          Double.NaN, Double.NaN, speed, pressureUnit, reached ? "target reached" : "converging toward target");
    }
    if (shaft != null && shaft.isOverPower()) {
      lastResult = new CompressorShaft.SolveResult(false, CompressorShaft.SolveStatus.OVER_POWER, targetPressure, pout,
          Double.NaN, Double.NaN, speed, pressureUnit, String.format("shaft draws %.0f kW of %.0f kW installed",
              shaft.getRequiredDriverPower("kW"), shaft.getAvailableShaftPower("kW")));
    }
  }

  /** {@inheritDoc} */
  @Override
  public List<SimulationVariable> getAutomationVariables(String unitAddress) {
    List<SimulationVariable> vars = new ArrayList<SimulationVariable>();
    String p = unitAddress + ".";
    vars.add(new SimulationVariable(p + "speed", "speed", VariableType.INPUT, "rpm", "Common shaft speed")
        .withBounds(Double.valueOf(0.0), null));
    vars.add(new SimulationVariable(p + "targetPressure", "targetPressure", VariableType.INPUT, pressureUnit,
        "Controlled discharge pressure of the reference body").withBounds(Double.valueOf(0.0), null));
    vars.add(new SimulationVariable(p + "ambientTemperature", "ambientTemperature", VariableType.INPUT, "C",
        "Ambient air temperature seen by the driver").withBounds(Double.valueOf(-80.0), Double.valueOf(80.0))
        .withUnitFamily("temperature"));
    vars.add(new SimulationVariable(p + "maxShaftPower", "maxShaftPower", VariableType.INPUT, "kW",
        "Explicit installed driver power limit (0 clears it)").withBounds(Double.valueOf(0.0), null));
    vars.add(new SimulationVariable(p + "applyRecycle", "applyRecycle", VariableType.INPUT, "-",
        "Write 1 to feed the required recycle fraction into the member anti-surge")
        .withBounds(Double.valueOf(0.0), Double.valueOf(1.0)));
    String[] outputs = {"power", "requiredDriverPower", "availablePower"};
    for (String o : outputs) {
      vars.add(new SimulationVariable(p + o, o, VariableType.OUTPUT, "kW", "Shaft " + o).withUnitFamily("power"));
    }
    String[] flags = {"powerUtilization", "overPower", "feasible", "turning", "recycleFractionMax"};
    for (String o : flags) {
      vars.add(new SimulationVariable(p + o, o, VariableType.OUTPUT, "-", "Shaft " + o));
    }
    vars.add(new SimulationVariable(p + "minSpeed", "minSpeed", VariableType.OUTPUT, "rpm", "Lower speed bound"));
    vars.add(new SimulationVariable(p + "maxSpeed", "maxSpeed", VariableType.OUTPUT, "rpm", "Upper speed bound"));
    if (shaft != null) {
      for (Compressor c : shaft.getCompressors()) {
        String m = p + "member." + c.getName() + ".";
        String[][] memberOutputs = {{"speed", "rpm"}, {"power", "kW"}, {"surgeMargin", "-"}, {"stonewallMargin", "-"},
            {"recycleFraction", "-"}, {"recyclePower", "kW"}, {"dischargeMargin", "bar"}};
        for (String[] mo : memberOutputs) {
          vars.add(new SimulationVariable(m + mo[0], "member." + c.getName() + "." + mo[0], VariableType.OUTPUT, mo[1],
              "Member " + mo[0]));
        }
        vars.add(new SimulationVariable(m + "active", "member." + c.getName() + ".active", VariableType.INPUT, "-",
            "Member in service (1) or idle (0)").withBounds(Double.valueOf(0.0), Double.valueOf(1.0)));
        vars.add(new SimulationVariable(m + "gearRatio", "member." + c.getName() + ".gearRatio", VariableType.INPUT,
            "-", "Member rpm per shaft rpm").withBounds(Double.valueOf(0.0), null));
      }
    }
    return vars;
  }

  /** {@inheritDoc} */
  @Override
  public double getAutomationValue(String property, String unitOfMeasure) {
    if (shaft == null) {
      throw new IllegalArgumentException("shaft calculator '" + getName() + "' has no shaft");
    }
    if (property.startsWith("member.")) {
      return getMemberValue(property, unitOfMeasure);
    }
    switch (property) {
    case "speed":
      return shaft.getSpeed();
    case "targetPressure":
      return targetPressure;
    case "ambientTemperature":
      return AutomationAddressable.temperatureFromKelvin(shaft.getAmbientTemperature("K"), unitOfMeasure);
    case "maxShaftPower":
      return AutomationAddressable.powerFromWatts(shaft.getMaxShaftPower("W"), unitOfMeasure);
    case "applyRecycle":
      return 0.0;
    case "power":
      return AutomationAddressable.powerFromWatts(shaft.getTotalPower(), unitOfMeasure);
    case "requiredDriverPower":
      return AutomationAddressable.powerFromWatts(shaft.getRequiredDriverPower("W"), unitOfMeasure);
    case "availablePower":
      return AutomationAddressable.powerFromWatts(shaft.getAvailableShaftPower("W"), unitOfMeasure);
    case "powerUtilization":
      return shaft.getPowerUtilization();
    case "overPower":
      return shaft.isOverPower() ? 1.0 : 0.0;
    case "feasible":
      return isFeasible() ? 1.0 : 0.0;
    case "turning":
      return shaft.isTurning() ? 1.0 : 0.0;
    case "minSpeed":
      return minSpeed;
    case "maxSpeed":
      return maxSpeed;
    case "recycleFractionMax":
      double max = 0.0;
      for (CompressorShaftReport.MemberReport mr : shaft.getOperatingReport().getMembers()) {
        max = Math.max(max, mr.getRequiredRecycleFraction());
      }
      return max;
    default:
      throw new IllegalArgumentException("Unknown property '" + property + "' for shaft calculator " + getName());
    }
  }

  /**
   * Read a per-member value.
   *
   * @param property property of the form {@code member.NAME.quantity}
   * @param unitOfMeasure requested unit for power quantities
   * @return the value
   * @throws IllegalArgumentException if the member or quantity is unknown
   */
  private double getMemberValue(String property, String unitOfMeasure) {
    int last = property.lastIndexOf('.');
    String memberName = property.substring("member.".length(), last);
    String quantity = property.substring(last + 1);
    Compressor c = findMember(memberName);
    if ("active".equals(quantity)) {
      return shaft.isMemberActive(c) ? 1.0 : 0.0;
    }
    if ("gearRatio".equals(quantity)) {
      return shaft.getGearRatio(c);
    }
    CompressorShaftReport.MemberReport mr = shaft.getOperatingReport().getMember(memberName);
    switch (quantity) {
    case "speed":
      return mr.getSpeedRpm();
    case "power":
      return AutomationAddressable.powerFromWatts(mr.getPowerW(), unitOfMeasure);
    case "surgeMargin":
      return mr.getDistanceToSurge();
    case "stonewallMargin":
      return mr.getDistanceToStoneWall();
    case "recycleFraction":
      return mr.getRequiredRecycleFraction();
    case "recyclePower":
      return AutomationAddressable.powerFromWatts(mr.getRecyclePowerW(), unitOfMeasure);
    case "dischargeMargin":
      return mr.getDischargeMarginBar();
    default:
      throw new IllegalArgumentException(
          "Unknown member quantity '" + quantity + "' for shaft calculator " + getName());
    }
  }

  /**
   * Find a member of the shaft by compressor name.
   *
   * @param memberName the compressor name
   * @return the compressor
   * @throws IllegalArgumentException if no member has the name
   */
  private Compressor findMember(String memberName) {
    for (Compressor c : shaft.getCompressors()) {
      if (c.getName().equals(memberName)) {
        return c;
      }
    }
    throw new IllegalArgumentException("'" + memberName + "' is not a member of shaft " + getName());
  }

  /** {@inheritDoc} */
  @Override
  public void setAutomationValue(String property, double value, String unitOfMeasure) {
    if (shaft == null) {
      throw new IllegalArgumentException("shaft calculator '" + getName() + "' has no shaft");
    }
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException("value must be finite");
    }
    if (property.startsWith("member.")) {
      setMemberValue(property, value);
      return;
    }
    switch (property) {
    case "speed":
      if (value < minSpeed || value > maxSpeed) {
        throw new IllegalArgumentException(
            "speed " + value + " rpm is outside the speed band " + minSpeed + " to " + maxSpeed + " rpm");
      }
      speed = value;
      prevSpeed = Double.NaN;
      prevError = Double.NaN;
      applySpeed();
      break;
    case "targetPressure":
      if (!(value > 0.0)) {
        throw new IllegalArgumentException("target pressure must be positive");
      }
      if (unitOfMeasure != null && !unitOfMeasure.trim().isEmpty()
          && !unitOfMeasure.trim().equalsIgnoreCase(pressureUnit)
          && !(unitOfMeasure.trim().toLowerCase().startsWith("bar") && pressureUnit.toLowerCase().startsWith("bar"))) {
        throw new IllegalArgumentException(
            "unsupported pressure unit '" + unitOfMeasure + "' (use " + pressureUnit + ")");
      }
      targetPressure = value;
      prevError = Double.NaN;
      break;
    case "ambientTemperature":
      double kelvin = AutomationAddressable.temperatureToKelvin(value, unitOfMeasure);
      if (kelvin < 193.15 || kelvin > 353.15) {
        throw new IllegalArgumentException("ambient temperature must be between -80 and 80 C");
      }
      shaft.setAmbientTemperature(kelvin, "K");
      break;
    case "maxShaftPower":
      if (value < 0.0) {
        throw new IllegalArgumentException("power limit must not be negative (0 clears the limit)");
      }
      setMaxShaftPower(value, unitOfMeasure == null || unitOfMeasure.trim().isEmpty() ? "kW" : unitOfMeasure);
      break;
    case "applyRecycle":
      if (Math.abs(value - 1.0) > 1.0e-9) {
        throw new IllegalArgumentException("write 1 to apply the required recycle");
      }
      shaft.applyRequiredRecycle();
      break;
    default:
      throw new IllegalArgumentException(
          "Property '" + property + "' of shaft calculator " + getName() + " is unknown or read-only");
    }
  }

  /**
   * Write a per-member input.
   *
   * @param property property of the form {@code member.NAME.quantity}
   * @param value the value
   * @throws IllegalArgumentException if the member, quantity or value is invalid
   */
  private void setMemberValue(String property, double value) {
    int last = property.lastIndexOf('.');
    String memberName = property.substring("member.".length(), last);
    String quantity = property.substring(last + 1);
    Compressor c = findMember(memberName);
    if ("active".equals(quantity)) {
      if (Math.abs(value) > 1.0e-9 && Math.abs(value - 1.0) > 1.0e-9) {
        throw new IllegalArgumentException("active flag must be 0 or 1");
      }
      shaft.setMemberActive(c, value > 0.5);
    } else if ("gearRatio".equals(quantity)) {
      shaft.setGearRatio(c, value);
      applySpeed();
    } else {
      throw new IllegalArgumentException(
          "Member quantity '" + quantity + "' of " + getName() + " is unknown or read-only");
    }
  }
}
