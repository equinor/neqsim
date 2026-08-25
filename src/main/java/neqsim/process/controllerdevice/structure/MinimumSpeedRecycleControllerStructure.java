package neqsim.process.controllerdevice.structure;

import neqsim.process.controllerdevice.ControllerDeviceInterface;

/**
 * Coordinates pressure control between compressor speed and recycle-valve opening when the compressor reaches its
 * minimum speed.
 *
 * <p>
 * The pressure-controller output is divided at a configurable transition. Above the transition, recycle addition is
 * zero and compressor speed increases from its minimum to its maximum. Below the transition, speed is held at its
 * minimum and an inverse recycle contribution increases as pressure-controller output falls. On entry to the lower
 * range, the previously selected recycle command is latched and the contribution is added to that baseline. Independent
 * anti-surge and suction-pressure demands remain able to open the valve further through a high selector.
 *
 * <p>
 * The structure deliberately does not back-calculate the selected recycle output into any of its controllers.
 * Controller integral state and machinery-protection logic remain the responsibility of the configured controllers and
 * control system. This class is intended for engineering simulation and is not certified anti-surge or safety logic.
 *
 * @author NeqSim
 * @version 1.0
 */
public class MinimumSpeedRecycleControllerStructure implements ControlStructureInterface {
  private static final long serialVersionUID = 1000;

  private final ControllerDeviceInterface pressureController;
  private final ControllerDeviceInterface antiSurgeController;
  private final ControllerDeviceInterface suctionPressureController;
  private final double transitionOutput;
  private final double maximumSpeedOutput;
  private final double minimumSpeedPercent;
  private final double maximumSpeedPercent;

  private double outputGuardBand = 1.0;
  private double speedOutput;
  private double effectivePressureOutput;
  private double recycleAddition;
  private double recycleOutput;
  private double latchedRecycleOutput;
  private double previousRecycleOutput;
  private boolean initialized;
  private boolean recycleControlActive;
  private boolean pressureRecycleDemandSelected;
  private boolean isActive = true;

  /**
   * Creates a coordinated minimum-speed and recycle control structure.
   *
   * @param pressureController pressure controller whose normalized output coordinates speed and recycle demand
   * @param antiSurgeController independent anti-surge controller participating in the high selector
   * @param suctionPressureController independent suction-pressure controller participating in the high selector
   * @param transitionOutput pressure-controller output where recycle addition is zero and speed is at its minimum, in
   * percent
   * @param maximumSpeedOutput pressure-controller output corresponding to maximum compressor speed, in percent
   * @param minimumSpeedPercent minimum compressor speed command, in percent of configured maximum
   * @param maximumSpeedPercent maximum compressor speed command, in percent of configured maximum
   * @throws IllegalArgumentException if a controller is null or the configured ranges are invalid
   */
  public MinimumSpeedRecycleControllerStructure(ControllerDeviceInterface pressureController,
      ControllerDeviceInterface antiSurgeController, ControllerDeviceInterface suctionPressureController,
      double transitionOutput, double maximumSpeedOutput, double minimumSpeedPercent, double maximumSpeedPercent) {
    if (pressureController == null || antiSurgeController == null || suctionPressureController == null) {
      throw new IllegalArgumentException("All controllers must be configured.");
    }
    if (transitionOutput <= 0.0 || maximumSpeedOutput <= transitionOutput) {
      throw new IllegalArgumentException("maximumSpeedOutput must be greater than a positive transitionOutput.");
    }
    if (minimumSpeedPercent < 0.0 || maximumSpeedPercent > 100.0 || minimumSpeedPercent > maximumSpeedPercent) {
      throw new IllegalArgumentException("Speed commands must satisfy 0 <= minimum <= maximum <= 100.");
    }
    this.pressureController = pressureController;
    this.antiSurgeController = antiSurgeController;
    this.suctionPressureController = suctionPressureController;
    this.transitionOutput = transitionOutput;
    this.maximumSpeedOutput = maximumSpeedOutput;
    this.minimumSpeedPercent = minimumSpeedPercent;
    this.maximumSpeedPercent = maximumSpeedPercent;
    this.speedOutput = minimumSpeedPercent;
  }

  /** {@inheritDoc} */
  @Override
  public void runTransient(double dt) {
    if (!isActive) {
      return;
    }
    pressureController.runTransient(pressureController.getResponse(), dt);
    antiSurgeController.runTransient(antiSurgeController.getResponse(), dt);
    suctionPressureController.runTransient(suctionPressureController.getResponse(), dt);
    update(pressureController.getResponse(), antiSurgeController.getResponse(),
        suctionPressureController.getResponse());
  }

  /**
   * Updates the coordinated outputs from already-calculated controller demands.
   *
   * <p>
   * This method is useful when the surrounding process has already advanced the controllers for the current time step.
   * Inputs are normalized controller outputs; recycle demands are clamped to 0-100 percent before selection.
   *
   * @param pressureOutput pressure-controller output in percent
   * @param antiSurgeOutput anti-surge recycle demand in percent opening
   * @param suctionPressureOutput suction-pressure recycle demand in percent opening
   */
  public void update(double pressureOutput, double antiSurgeOutput, double suctionPressureOutput) {
    double boundedPressureOutput = clamp(pressureOutput, 0.0, maximumSpeedOutput);
    double boundedAntiSurgeOutput = clamp(antiSurgeOutput, 0.0, 100.0);
    double boundedSuctionOutput = clamp(suctionPressureOutput, 0.0, 100.0);
    double independentRecycleOutput = Math.max(boundedAntiSurgeOutput, boundedSuctionOutput);

    if (boundedPressureOutput < transitionOutput) {
      if (!recycleControlActive) {
        latchedRecycleOutput = initialized ? previousRecycleOutput : independentRecycleOutput;
      }
      recycleControlActive = true;
      speedOutput = minimumSpeedPercent;
      effectivePressureOutput = Math.max(boundedPressureOutput, getMinimumPressureControllerOutput());
      recycleAddition = (transitionOutput - effectivePressureOutput) / transitionOutput * 100.0;
      double pressureRecycleOutput = clamp(latchedRecycleOutput + recycleAddition, 0.0, 100.0);
      recycleOutput = Math.max(pressureRecycleOutput, independentRecycleOutput);
      pressureRecycleDemandSelected = pressureRecycleOutput >= independentRecycleOutput;
    } else {
      recycleControlActive = false;
      effectivePressureOutput = boundedPressureOutput;
      recycleAddition = 0.0;
      speedOutput = minimumSpeedPercent + (boundedPressureOutput - transitionOutput)
          / (maximumSpeedOutput - transitionOutput) * (maximumSpeedPercent - minimumSpeedPercent);
      speedOutput = clamp(speedOutput, minimumSpeedPercent, maximumSpeedPercent);
      recycleOutput = independentRecycleOutput;
      pressureRecycleDemandSelected = false;
    }

    previousRecycleOutput = recycleOutput;
    initialized = true;
  }

  /**
   * Clamps a value to an inclusive range.
   *
   * @param value candidate value
   * @param minimum minimum permitted value
   * @param maximum maximum permitted value
   * @return the clamped value
   */
  private double clamp(double value, double minimum, double maximum) {
    return Math.max(minimum, Math.min(maximum, value));
  }

  /**
   * Gets the compressor speed command.
   *
   * @return speed command in percent of configured maximum speed
   */
  public double getSpeedOutput() {
    return speedOutput;
  }

  /**
   * Gets the recycle-valve contribution added by the pressure controller.
   *
   * @return recycle addition in percent opening
   */
  public double getRecycleAddition() {
    return recycleAddition;
  }

  /**
   * Gets the recycle command latched when pressure control entered the lower range.
   *
   * @return latched recycle command in percent opening
   */
  public double getLatchedRecycleOutput() {
    return latchedRecycleOutput;
  }

  /**
   * Gets the dynamic minimum pressure-controller output associated with recycle saturation.
   *
   * <p>
   * The configurable guard band defaults to one percentage point. With a 75 percent transition, this reproduces the
   * common discrete-control expression {@code latchedOutput * 74 / 100}.
   *
   * @return minimum pressure-controller output in percent
   */
  public double getMinimumPressureControllerOutput() {
    return latchedRecycleOutput * Math.max(transitionOutput - outputGuardBand, 0.0) / 100.0;
  }

  /**
   * Gets the pressure-controller output used by the coordinated mapping after applying the dynamic saturation floor.
   *
   * @return effective pressure-controller output in percent
   */
  public double getEffectivePressureControllerOutput() {
    return effectivePressureOutput;
  }

  /**
   * Sets the output guard band used to calculate the dynamic saturation floor.
   *
   * @param outputGuardBand non-negative guard band in controller-output percentage points
   * @throws IllegalArgumentException if the guard band is negative or not below the transition
   */
  public void setOutputGuardBand(double outputGuardBand) {
    if (outputGuardBand < 0.0 || outputGuardBand >= transitionOutput) {
      throw new IllegalArgumentException("outputGuardBand must be non-negative and below transitionOutput.");
    }
    this.outputGuardBand = outputGuardBand;
  }

  /**
   * Checks whether pressure control is operating in the recycle range.
   *
   * @return {@code true} when pressure-controller output is below the transition
   */
  public boolean isRecycleControlActive() {
    return recycleControlActive;
  }

  /**
   * Checks whether the pressure-derived recycle demand won the high selector.
   *
   * @return {@code true} when the pressure-derived demand was at least as high as both independent demands
   */
  public boolean isPressureRecycleDemandSelected() {
    return pressureRecycleDemandSelected;
  }

  /** {@inheritDoc} */
  @Override
  public double getOutput() {
    return recycleOutput;
  }

  /** {@inheritDoc} */
  @Override
  public void setActive(boolean isActive) {
    this.isActive = isActive;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isActive() {
    return isActive;
  }
}