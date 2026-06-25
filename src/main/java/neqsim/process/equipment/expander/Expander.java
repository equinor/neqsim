package neqsim.process.equipment.expander;

import java.util.UUID;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.expander.ExpanderMechanicalDesign;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Expander class.
 *
 * @author esol
 * @version $Id: $Id
 */
public class Expander extends Compressor implements ExpanderInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Mechanical design for the expander. */
  private ExpanderMechanicalDesign expanderMechanicalDesign;

  /**
   * Rated recovered shaft power of the expander in kW. A value of {@code 0} (the default) means no recovered-power
   * capacity limit is defined, so no {@code recoveredPower} constraint is created.
   */
  private double ratedRecoveredPower = 0.0;

  /** Current inlet guide vane or nozzle opening fraction. */
  private double nozzleOpening = 1.0;

  /** Target inlet guide vane or nozzle opening fraction. */
  private double targetNozzleOpening = 1.0;

  /** Minimum allowed inlet guide vane or nozzle opening fraction. */
  private double minimumNozzleOpening = 0.05;

  /** Maximum allowed inlet guide vane or nozzle opening fraction. */
  private double maximumNozzleOpening = 1.0;

  /** Maximum absolute inlet guide vane or nozzle stroke rate in fraction per second. */
  private double nozzleOpeningRate = 0.1;

  /** Dynamic recovered shaft power used for ramped load coupling in kW. */
  private double dynamicRecoveredPowerKW = 0.0;

  /** Maximum ramp rate for recovered shaft power in kW/s. */
  private double recoveredPowerRampRateKWperSec = 1000.0;

  /** External shaft load attached to the expander in kW. */
  private double externalShaftLoadKW = 0.0;

  /** Compressor or rotating load mechanically coupled to the expander shaft. */
  private Compressor coupledCompressorLoad = null;

  /** Speed multiplier from expander shaft speed to the coupled compressor speed. */
  private double coupledCompressorSpeedRatio = 1.0;

  /**
   * Constructor for Expander.
   *
   * @param name name of unit operation
   */
  public Expander(String name) {
    super(name);
    initExpanderMechanicalDesign();
  }

  /**
   * Constructor for Expander.
   *
   * @param name a {@link java.lang.String} object
   * @param inletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public Expander(String name, StreamInterface inletStream) {
    super(name, inletStream);
    initExpanderMechanicalDesign();
  }

  /**
   * Get the expander-specific mechanical design.
   *
   * @return expander mechanical design
   */
  public ExpanderMechanicalDesign getExpanderMechanicalDesign() {
    return expanderMechanicalDesign;
  }

  /**
   * Initialize the expander mechanical design.
   */
  private void initExpanderMechanicalDesign() {
    expanderMechanicalDesign = new ExpanderMechanicalDesign(this);
  }

  /**
   * Gets the rated recovered shaft power of the expander.
   *
   * @return rated recovered power in kW; {@code 0} means no recovered-power limit is defined
   */
  public double getRatedRecoveredPower() {
    return ratedRecoveredPower;
  }

  /**
   * Sets the rated recovered shaft power of the expander and rebuilds the capacity constraints so the
   * {@code recoveredPower} utilization reflects the new rating.
   *
   * <p>
   * The recovered power of an expander is mechanical work extracted from the gas, so {@link #getPower()} returns a
   * negative value. The {@code recoveredPower} constraint compares the magnitude of the extracted power against this
   * rating, giving a meaningful 0&ndash;100+% utilization for the turbo-expander. When the rating is {@code 0} no
   * recovered-power constraint is created.
   * </p>
   *
   * @param ratedRecoveredPowerKW rated recovered power in kW; must be &ge; 0
   */
  public void setRatedRecoveredPower(double ratedRecoveredPowerKW) {
    this.ratedRecoveredPower = ratedRecoveredPowerKW;
    reinitializeCapacityConstraints();
  }

  /**
   * Gets the current inlet guide vane or nozzle opening.
   *
   * @return current opening fraction between the configured minimum and maximum values
   */
  public double getNozzleOpening() {
    return nozzleOpening;
  }

  /**
   * Sets the current inlet guide vane or nozzle opening.
   *
   * @param opening opening fraction where {@code 0} is closed and {@code 1} is fully open
   */
  public void setNozzleOpening(double opening) {
    this.nozzleOpening = clampOpening(opening);
  }

  /**
   * Gets the target inlet guide vane or nozzle opening.
   *
   * @return target opening fraction where {@code 0} is closed and {@code 1} is fully open
   */
  public double getTargetNozzleOpening() {
    return targetNozzleOpening;
  }

  /**
   * Sets the target inlet guide vane or nozzle opening.
   *
   * @param opening target opening fraction where {@code 0} is closed and {@code 1} is fully open
   */
  public void setTargetNozzleOpening(double opening) {
    this.targetNozzleOpening = clampOpening(opening);
  }

  /**
   * Sets the allowed inlet guide vane or nozzle opening range.
   *
   * @param minimumOpening minimum opening fraction where {@code 0} is closed and {@code 1} is fully open
   * @param maximumOpening maximum opening fraction where {@code 0} is closed and {@code 1} is fully open
   */
  public void setNozzleOpeningLimits(double minimumOpening, double maximumOpening) {
    this.minimumNozzleOpening = Math.max(0.0, Math.min(1.0, minimumOpening));
    this.maximumNozzleOpening = Math.max(this.minimumNozzleOpening, Math.min(1.0, maximumOpening));
    this.nozzleOpening = clampOpening(nozzleOpening);
    this.targetNozzleOpening = clampOpening(targetNozzleOpening);
  }

  /**
   * Gets the maximum inlet guide vane or nozzle stroke rate.
   *
   * @return stroke rate in opening fraction per second
   */
  public double getNozzleOpeningRate() {
    return nozzleOpeningRate;
  }

  /**
   * Sets the maximum inlet guide vane or nozzle stroke rate.
   *
   * @param nozzleOpeningRate stroke rate in opening fraction per second; negative values are treated as zero
   */
  public void setNozzleOpeningRate(double nozzleOpeningRate) {
    this.nozzleOpeningRate = Math.max(0.0, nozzleOpeningRate);
  }

  /**
   * Gets the ramped dynamic recovered shaft power.
   *
   * @return dynamic recovered power in kW
   */
  public double getDynamicRecoveredPower() {
    return dynamicRecoveredPowerKW;
  }

  /**
   * Gets the ramped dynamic recovered shaft power.
   *
   * @param unit power unit, either {@code kW} or {@code MW}
   * @return dynamic recovered power in the requested unit
   */
  public double getDynamicRecoveredPower(String unit) {
    if (unit.equals("MW")) {
      return dynamicRecoveredPowerKW / 1000.0;
    }
    return dynamicRecoveredPowerKW;
  }

  /**
   * Sets the maximum recovered-power ramp rate.
   *
   * @param recoveredPowerRampRateKWperSec ramp rate in kW/s; negative values are treated as zero
   */
  public void setRecoveredPowerRampRate(double recoveredPowerRampRateKWperSec) {
    this.recoveredPowerRampRateKWperSec = Math.max(0.0, recoveredPowerRampRateKWperSec);
  }

  /**
   * Gets the maximum recovered-power ramp rate.
   *
   * @return ramp rate in kW/s
   */
  public double getRecoveredPowerRampRate() {
    return recoveredPowerRampRateKWperSec;
  }

  /**
   * Sets an external shaft load coupled to the expander shaft.
   *
   * @param externalShaftLoadKW shaft load in kW; negative values are treated as zero
   */
  public void setExternalShaftLoad(double externalShaftLoadKW) {
    this.externalShaftLoadKW = Math.max(0.0, externalShaftLoadKW);
  }

  /**
   * Gets the external shaft load coupled to the expander shaft.
   *
   * @return external shaft load in kW
   */
  public double getExternalShaftLoad() {
    return externalShaftLoadKW;
  }

  /**
   * Couples a compressor or rotating load to the expander shaft.
   *
   * @param coupledCompressorLoad compressor load to drive from the expander shaft; may be {@code null}
   */
  public void setCoupledCompressorLoad(Compressor coupledCompressorLoad) {
    this.coupledCompressorLoad = coupledCompressorLoad;
  }

  /**
   * Gets the compressor or rotating load coupled to the expander shaft.
   *
   * @return coupled compressor load, or {@code null} when no compressor is coupled
   */
  public Compressor getCoupledCompressorLoad() {
    return coupledCompressorLoad;
  }

  /**
   * Sets the speed ratio from the expander shaft to the coupled compressor shaft.
   *
   * @param coupledCompressorSpeedRatio speed ratio; negative values are treated as zero
   */
  public void setCoupledCompressorSpeedRatio(double coupledCompressorSpeedRatio) {
    this.coupledCompressorSpeedRatio = Math.max(0.0, coupledCompressorSpeedRatio);
  }

  /**
   * Gets the speed ratio from the expander shaft to the coupled compressor shaft.
   *
   * @return coupled compressor speed ratio
   */
  public double getCoupledCompressorSpeedRatio() {
    return coupledCompressorSpeedRatio;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Builds the inherited compressor capacity constraints, then adapts them for expander operation. The consumed-power
   * constraints ({@code power} and {@code ratedPower}) read {@link #getPower()}, which is negative for an expander and
   * therefore always reports zero utilization, so they are removed. When a rated recovered power has been set, a
   * {@code recoveredPower} constraint is added that tracks the magnitude of the extracted shaft power against the
   * rating.
   * </p>
   */
  @Override
  protected void initializeCapacityConstraints() {
    super.initializeCapacityConstraints();
    // Consumed-power constraints are meaningless for an expander (power < 0 -> 0% utilization).
    removeCapacityConstraint("power");
    removeCapacityConstraint("ratedPower");
    if (ratedRecoveredPower > 0.0) {
      final double ratedKW = ratedRecoveredPower;
      addCapacityConstraint(new CapacityConstraint("recoveredPower", "kW", CapacityConstraint.ConstraintType.HARD)
          .setDesignValue(ratedKW).setMaxValue(ratedKW * 1.1).setWarningThreshold(0.9)
          .setDescription("Recovered shaft power vs rated recovered power").setDataSource("equipment")
          .setValueSupplier(() -> {
            if (getThermoSystem() == null) {
              return 0.0;
            }
            double recoveredKW = Math.abs(this.getPower("kW"));
            if (Double.isNaN(recoveredKW)) {
              return 0.0;
            }
            return recoveredKW;
          }));
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Validates that the expander simulation produced physically reasonable results. Unlike a compressor, an expander
   * extracts work from the gas, so it is valid for the shaft power to be negative, the polytropic head to be negative,
   * and the gas to cool across the machine. This override checks only for non-NaN key values and that the outlet gas is
   * not hotter than the inlet (within a small numerical tolerance). Using the compressor validity rules here would
   * incorrectly flag every expander as invalid and trigger a spurious 150% utilization fallback.
   * </p>
   */
  @Override
  public boolean isSimulationValid() {
    if (thermoSystem == null) {
      return false;
    }
    double power = getPower();
    double inletTemp = getInletTemperature();
    double outletTemp = getOutletTemperature();
    if (Double.isNaN(power) || Double.isNaN(inletTemp) || Double.isNaN(outletTemp)) {
      return false;
    }
    // Expansion cools the gas; allow small tolerance for numerical precision.
    if (outletTemp > inletTemp + 1.0) {
      return false;
    }
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public void runTransient(double dt, UUID id) {
    if (getCalculateSteadyState()) {
      run(id);
      increaseTime(dt);
      return;
    }

    applyNozzleController(dt, id);
    updateNozzleOpening(dt);

    double targetPressure = pressure;
    double targetEfficiency = isentropicEfficiency;
    double inletPressure = inStream.getPressure("bara");
    double effectivePressure = inletPressure - nozzleOpening * (inletPressure - targetPressure);
    if (effectivePressure > inletPressure) {
      effectivePressure = inletPressure;
    }
    pressure = effectivePressure;
    isentropicEfficiency = targetEfficiency * (0.5 + 0.5 * nozzleOpening);

    run(id);

    double steadyRecoveredKW = Math.max(0.0, Math.abs(getPower("kW")));
    updateRecoveredPower(steadyRecoveredKW, dt);
    updateShaftSpeed(dt);
    updateCoupledCompressor(id);

    pressure = targetPressure;
    isentropicEfficiency = targetEfficiency;
    increaseTime(dt);
    setCalculationIdentifier(id);
  }

  /**
   * Applies the attached controller to the nozzle-opening target when active.
   *
   * @param dt transient time step in seconds
   * @param id calculation identifier
   */
  private void applyNozzleController(double dt, UUID id) {
    if (hasController && getController().isActive()) {
      getController().runTransient(nozzleOpening, dt, id);
      setTargetNozzleOpening(getController().getResponse());
    }
  }

  /**
   * Updates the actual nozzle opening with the configured stroke-rate limit.
   *
   * @param dt transient time step in seconds
   */
  private void updateNozzleOpening(double dt) {
    double delta = targetNozzleOpening - nozzleOpening;
    double maxDelta = nozzleOpeningRate * Math.max(0.0, dt);
    if (Math.abs(delta) <= maxDelta) {
      nozzleOpening = targetNozzleOpening;
    } else if (delta > 0.0) {
      nozzleOpening += maxDelta;
    } else {
      nozzleOpening -= maxDelta;
    }
    nozzleOpening = clampOpening(nozzleOpening);
  }

  /**
   * Updates the dynamic recovered-power state with a ramp-rate limit.
   *
   * @param targetRecoveredKW target recovered power in kW
   * @param dt transient time step in seconds
   */
  private void updateRecoveredPower(double targetRecoveredKW, double dt) {
    double delta = targetRecoveredKW - dynamicRecoveredPowerKW;
    double maxDelta = recoveredPowerRampRateKWperSec * Math.max(0.0, dt);
    if (Math.abs(delta) <= maxDelta) {
      dynamicRecoveredPowerKW = targetRecoveredKW;
    } else if (delta > 0.0) {
      dynamicRecoveredPowerKW += maxDelta;
    } else {
      dynamicRecoveredPowerKW -= maxDelta;
    }
    if (dynamicRecoveredPowerKW < 0.0) {
      dynamicRecoveredPowerKW = 0.0;
    }
  }

  /**
   * Updates shaft speed from recovered power, external load, coupled compressor load and rotor inertia.
   *
   * @param dt transient time step in seconds
   */
  private void updateShaftSpeed(double dt) {
    double loadKW = externalShaftLoadKW + getCoupledCompressorLoadKW();
    double netPowerW = (dynamicRecoveredPowerKW - loadKW) * 1000.0;
    double speedRPM = Math.max(0.0, getSpeed());
    double omega = speedRPM * 2.0 * Math.PI / 60.0;
    double inertia = Math.max(1.0e-12, getRotationalInertia());
    double newSpeedRPM;
    if (omega < 1.0e-9) {
      newSpeedRPM = speedRPM + Math.signum(netPowerW) * getMaxAccelerationRate() * Math.max(0.0, dt);
    } else {
      double newOmega = omega + netPowerW / (inertia * omega) * Math.max(0.0, dt);
      newSpeedRPM = Math.max(0.0, newOmega * 60.0 / (2.0 * Math.PI));
    }
    setSpeed(limitSpeedChange(speedRPM, newSpeedRPM, dt));
  }

  /**
   * Gets the current coupled compressor shaft load.
   *
   * @return coupled compressor load in kW
   */
  private double getCoupledCompressorLoadKW() {
    if (coupledCompressorLoad == null || coupledCompressorLoad.getThermoSystem() == null) {
      return 0.0;
    }
    return Math.max(0.0, coupledCompressorLoad.getPower("kW"));
  }

  /**
   * Limits the shaft speed change using the inherited acceleration and deceleration rates.
   *
   * @param currentSpeedRPM current shaft speed in rpm
   * @param targetSpeedRPM target shaft speed in rpm from the inertia equation
   * @param dt transient time step in seconds
   * @return limited shaft speed in rpm
   */
  private double limitSpeedChange(double currentSpeedRPM, double targetSpeedRPM, double dt) {
    double delta = targetSpeedRPM - currentSpeedRPM;
    if (delta > 0.0) {
      return currentSpeedRPM + Math.min(delta, getMaxAccelerationRate() * Math.max(0.0, dt));
    }
    return currentSpeedRPM + Math.max(delta, -getMaxDecelerationRate() * Math.max(0.0, dt));
  }

  /**
   * Updates a mechanically coupled compressor load with the current expander shaft speed.
   *
   * @param id calculation identifier
   */
  private void updateCoupledCompressor(UUID id) {
    if (coupledCompressorLoad != null) {
      coupledCompressorLoad.setSpeed(getSpeed() * coupledCompressorSpeedRatio);
      coupledCompressorLoad.run(id);
    }
  }

  /**
   * Clamps a nozzle opening to the configured physical limits.
   *
   * @param opening requested nozzle opening fraction
   * @return clamped nozzle opening fraction
   */
  private double clampOpening(double opening) {
    double finiteOpening = Double.isNaN(opening) ? minimumNozzleOpening : opening;
    return Math.max(minimumNozzleOpening, Math.min(maximumNozzleOpening, finiteOpening));
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    // System.out.println("expander running..");
    thermoSystem = inStream.getThermoSystem().clone();
    ThermodynamicOperations thermoOps = new ThermodynamicOperations(getThermoSystem());
    thermoOps = new ThermodynamicOperations(thermoSystem);
    thermoSystem.init(3);
    // double presinn = getThermoSystem().getPressure();
    double hinn = getThermoSystem().getEnthalpy();
    // double densInn = getThermoSystem().getDensity();
    double entropy = getThermoSystem().getEntropy();
    inletEnthalpy = hinn;
    double hout = hinn;
    if (usePolytropicCalc) {
      int numbersteps = 40;
      double dp = (pressure - getThermoSystem().getPressure()) / (1.0 * numbersteps);

      for (int i = 0; i < numbersteps; i++) {
        entropy = getThermoSystem().getEntropy();
        double hinn_loc = getThermoSystem().getEnthalpy();
        getThermoSystem().setPressure(getThermoSystem().getPressure() + dp);
        thermoOps.PSflash(entropy);
        hout = hinn_loc + (getThermoSystem().getEnthalpy() - hinn_loc) * polytropicEfficiency;
        thermoOps.PHflash(hout, 0);
      }

      dH = hout - hinn;
      /*
       * HYSYS method double oldPolyt = 10.5; int iter = 0; do {
       *
       * iter++; double n = Math.log(thermoSystem.getPressure() / presinn) / Math.log(thermoSystem.getDensity() /
       * densInn); double k = Math.log(thermoSystem.getPressure() / presinn) / Math.log(densOutIdeal / densInn); double
       * factor = ((Math.pow(thermoSystem.getPressure() / presinn, (n - 1.0) / n) - 1.0) * (n / (n - 1.0)) * (k - 1) /
       * k) / (Math.pow(thermoSystem.getPressure() / presinn, (k - 1.0) / k) - 1.0); oldPolyt = polytropicEfficiency;
       * polytropicEfficiency = factor * isentropicEfficiency; dH = thermoSystem.getEnthalpy() - hinn; hout = hinn + dH
       * / polytropicEfficiency; thermoOps.PHflash(hout, 0); System.out.println(" factor " + factor + " n " + n + " k "
       * + k + " polytropic effici " + polytropicEfficiency + " iter " + iter); } while (Math.abs((oldPolyt -
       * polytropicEfficiency) / oldPolyt) > 1e-5 && iter < 500); // polytropicEfficiency = isentropicEfficiency * ();
       */
    } else {
      getThermoSystem().setPressure(pressure);

      // System.out.println("entropy inn.." + entropy);
      thermoOps.PSflash(entropy);
      // double densOutIdeal = getThermoSystem().getDensity();
      if (!powerSet) {
        dH = (getThermoSystem().getEnthalpy() - hinn) * isentropicEfficiency;
      }
      hout = hinn + dH;
      isentropicEfficiency = dH / (getThermoSystem().getEnthalpy() - hinn);
      // System.out.println("isentropicEfficiency.. " + isentropicEfficiency);
      dH = hout - hinn;
      thermoOps.PHflash(hout, 0);
    }
    if (isSetEnergyStream()) {
      energyStream.setDuty(-dH);
    }
    // thermoSystem.display();
    outStream.setThermoSystem(getThermoSystem());
    setCalculationIdentifier(id);
  }
}
