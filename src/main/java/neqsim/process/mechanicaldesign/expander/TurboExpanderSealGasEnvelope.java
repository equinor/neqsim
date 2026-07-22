package neqsim.process.mechanicaldesign.expander;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.expander.TurboExpanderCompressor;

/**
 * TurboExpanderSealGasEnvelope (P5) converts a thermodynamically feasible turbo-expander-compressor operating point
 * into a <em>mechanically allowable</em> one by checking the rotating-equipment envelope that the thermodynamic model
 * does not see:
 *
 * <ul>
 * <li>Net axial thrust as a function of expander and compressor differential pressure, and the resulting thrust-bearing
 * utilisation against its rated capacity.</li>
 * <li>Dry-gas-seal seal-gas flow and the seal-gas heater duty required to keep the seal-gas supply above its dew-point
 * margin, checked against the installed heater rating (Oseberg FE-25832 = 28 kW with a 30 &deg;C thermostat set
 * point).</li>
 * <li>First lateral critical-speed separation margin per API 617 (15% minimum).</li>
 * </ul>
 *
 * <p>
 * All quantities use simple, transparent engineering correlations with configurable areas and limits so the envelope
 * can be anchored to certified OEM data. The class is independent of the heavier
 * {@link TurboExpanderCompressorMechanicalDesign} so it can be used as a fast feasibility gate inside
 * operating-envelope sweeps.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class TurboExpanderSealGasEnvelope implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  static final Logger logger = LogManager.getLogger(TurboExpanderSealGasEnvelope.class);

  /** The machine whose mechanical envelope is evaluated. */
  private TurboExpanderCompressor machine = null;

  // --- Thrust configuration ---
  /** Effective expander thrust-balance area in m^2. */
  private double expanderThrustArea = 0.0140;
  /** Effective compressor thrust-balance area in m^2. */
  private double compressorThrustArea = 0.0150;
  /**
   * Balance-piston offload fraction (0..1); fraction of gross thrust removed by the balance drum.
   */
  private double balancePistonOffload = 0.85;
  /** Rated thrust-bearing capacity in N (tilting-pad double-acting). */
  private double thrustBearingCapacity = 45000.0;

  // --- Seal-gas configuration ---
  /** Installed seal-gas heater rating in W (FE-25832 = 28 kW). */
  private double sealGasHeaterRating = 28000.0;
  /** Seal-gas heater thermostat set point in deg C. */
  private double sealGasSetPointC = 30.0;
  /** Seal-gas supply temperature in deg C. */
  private double sealGasSupplyTemperatureC = 5.0;
  /** Seal-gas mass flow per seal in kg/s (two dry-gas seals). */
  private double sealGasFlowPerSeal = 0.010;
  /** Seal-gas specific heat in J/(kg K). */
  private double sealGasSpecificHeat = 2200.0;

  // --- Critical speed configuration ---
  /** First lateral critical speed in rpm. */
  private double firstCriticalSpeed = 4500.0;
  /** Maximum continuous operating speed in rpm. */
  private double maxContinuousSpeed = 9000.0;
  /** Minimum API 617 critical-speed separation margin (fraction). */
  private double minSeparationMargin = 0.15;

  // --- Results ---
  /** Net axial thrust result in N. */
  private double netAxialThrust = 0.0;
  /** Thrust-bearing utilisation fraction (load/capacity). */
  private double thrustUtilisation = 0.0;
  /** Required seal-gas heater duty in W. */
  private double requiredHeaterDuty = 0.0;
  /** Critical-speed separation margin (fraction) at the operating speed. */
  private double criticalSpeedMargin = 0.0;

  /**
   * Default constructor.
   */
  public TurboExpanderSealGasEnvelope() {
  }

  /**
   * Constructs an envelope evaluator for the given machine.
   *
   * @param machine the turbo-expander-compressor to evaluate
   */
  public TurboExpanderSealGasEnvelope(TurboExpanderCompressor machine) {
    this.machine = machine;
  }

  /**
   * Evaluate the full mechanical envelope at the machine's current operating point. The machine must already have been
   * run so the streams and speed reflect the operating point.
   *
   * @return {@code true} if the operating point is mechanically allowable (thrust within bearing capacity, heater duty
   * within rating and critical-speed margin satisfied)
   */
  public boolean evaluate() {
    calcAxialThrust();
    calcSealGasHeaterDuty();
    calcCriticalSpeedMargin();
    return isThrustAcceptable() && isHeaterDutyAcceptable() && isCriticalSpeedMarginAcceptable();
  }

  /**
   * Compute the net axial thrust from the expander and compressor differential pressures and the configured
   * thrust-balance areas and balance-piston offload.
   *
   * @return the net axial thrust in N
   */
  public double calcAxialThrust() {
    double dpExpanderPa = 0.0;
    double dpCompressorPa = 0.0;
    if (machine != null) {
      try {
        double expIn = machine.getInletStream().getPressure("bara");
        double expOut = machine.getExpanderOutPressure();
        dpExpanderPa = (expIn - expOut) * 1.0e5;
      } catch (Exception ex) {
        logger.debug("Could not read expander pressures for thrust", ex);
      }
      try {
        double compIn = machine.getCompressorFeedStream().getPressure("bara");
        double compOut = machine.getCompressorOutletStream().getPressure("bara");
        dpCompressorPa = (compOut - compIn) * 1.0e5;
      } catch (Exception ex) {
        logger.debug("Could not read compressor pressures for thrust", ex);
      }
    }
    // Expander and compressor axial thrusts act in opposite directions on a back-to-back rotor.
    double grossThrust = dpExpanderPa * expanderThrustArea - dpCompressorPa * compressorThrustArea;
    netAxialThrust = grossThrust * (1.0 - balancePistonOffload);
    thrustUtilisation = thrustBearingCapacity > 0.0 ? Math.abs(netAxialThrust) / thrustBearingCapacity : 0.0;
    return netAxialThrust;
  }

  /**
   * Compute the seal-gas heater duty required to raise the seal-gas supply from its supply temperature to the
   * thermostat set point for both dry-gas seals.
   *
   * @return the required seal-gas heater duty in W
   */
  public double calcSealGasHeaterDuty() {
    double deltaT = sealGasSetPointC - sealGasSupplyTemperatureC;
    if (deltaT < 0.0) {
      deltaT = 0.0;
    }
    double totalFlow = 2.0 * sealGasFlowPerSeal;
    requiredHeaterDuty = totalFlow * sealGasSpecificHeat * deltaT;
    return requiredHeaterDuty;
  }

  /**
   * Compute the first-critical-speed separation margin at the operating speed.
   *
   * @return the separation margin (fraction); positive means the operating speed is sufficiently above the first
   * critical speed
   */
  public double calcCriticalSpeedMargin() {
    double speed = maxContinuousSpeed;
    if (machine != null && machine.getSpeed() > 0.0) {
      speed = machine.getSpeed();
    }
    if (firstCriticalSpeed <= 0.0) {
      criticalSpeedMargin = Double.POSITIVE_INFINITY;
      return criticalSpeedMargin;
    }
    criticalSpeedMargin = (speed - firstCriticalSpeed) / firstCriticalSpeed;
    return criticalSpeedMargin;
  }

  /**
   * Returns whether the net axial thrust is within the thrust-bearing capacity.
   *
   * @return {@code true} if the thrust utilisation is at or below 1.0
   */
  public boolean isThrustAcceptable() {
    return thrustUtilisation <= 1.0;
  }

  /**
   * Returns whether the required seal-gas heater duty is within the installed heater rating.
   *
   * @return {@code true} if the required duty is at or below the heater rating
   */
  public boolean isHeaterDutyAcceptable() {
    return requiredHeaterDuty <= sealGasHeaterRating;
  }

  /**
   * Returns whether the first-critical-speed separation margin satisfies the API 617 minimum.
   *
   * @return {@code true} if the separation margin is at or above the configured minimum
   */
  public boolean isCriticalSpeedMarginAcceptable() {
    return criticalSpeedMargin >= minSeparationMargin;
  }

  /**
   * Build a map of the mechanical envelope results.
   *
   * @return an ordered map of named result values
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("netAxialThrust_N", netAxialThrust);
    map.put("thrustBearingCapacity_N", thrustBearingCapacity);
    map.put("thrustUtilisation_fraction", thrustUtilisation);
    map.put("thrustAcceptable", isThrustAcceptable());
    map.put("requiredSealGasHeaterDuty_W", requiredHeaterDuty);
    map.put("installedSealGasHeaterRating_W", sealGasHeaterRating);
    map.put("sealGasSetPoint_C", sealGasSetPointC);
    map.put("heaterDutyAcceptable", isHeaterDutyAcceptable());
    map.put("firstCriticalSpeed_rpm", firstCriticalSpeed);
    map.put("operatingSpeed_rpm",
        machine != null && machine.getSpeed() > 0.0 ? machine.getSpeed() : maxContinuousSpeed);
    map.put("criticalSpeedMargin_fraction", criticalSpeedMargin);
    map.put("criticalSpeedMarginAcceptable", isCriticalSpeedMarginAcceptable());
    map.put("mechanicallyAllowable",
        isThrustAcceptable() && isHeaterDutyAcceptable() && isCriticalSpeedMarginAcceptable());
    return map;
  }

  /**
   * Serialise the mechanical envelope results to JSON.
   *
   * @return a pretty-printed JSON string of the envelope results
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(toMap());
  }

  // --- Getters / setters ---

  /**
   * Get the net axial thrust.
   *
   * @return the net axial thrust in N
   */
  public double getNetAxialThrust() {
    return netAxialThrust;
  }

  /**
   * Get the thrust-bearing utilisation.
   *
   * @return the thrust utilisation (load/capacity)
   */
  public double getThrustUtilisation() {
    return thrustUtilisation;
  }

  /**
   * Get the required seal-gas heater duty.
   *
   * @return the required heater duty in W
   */
  public double getRequiredHeaterDuty() {
    return requiredHeaterDuty;
  }

  /**
   * Get the critical-speed separation margin.
   *
   * @return the critical-speed margin (fraction)
   */
  public double getCriticalSpeedMargin() {
    return criticalSpeedMargin;
  }

  /**
   * Set the machine to evaluate.
   *
   * @param machine the turbo-expander-compressor
   */
  public void setMachine(TurboExpanderCompressor machine) {
    this.machine = machine;
  }

  /**
   * Set the effective expander and compressor thrust-balance areas.
   *
   * @param expanderArea the effective expander thrust area in m^2
   * @param compressorArea the effective compressor thrust area in m^2
   */
  public void setThrustAreas(double expanderArea, double compressorArea) {
    this.expanderThrustArea = expanderArea;
    this.compressorThrustArea = compressorArea;
  }

  /**
   * Set the balance-piston offload fraction.
   *
   * @param balancePistonOffload the fraction of gross thrust removed by the balance drum (0..1)
   */
  public void setBalancePistonOffload(double balancePistonOffload) {
    this.balancePistonOffload = balancePistonOffload;
  }

  /**
   * Set the rated thrust-bearing capacity.
   *
   * @param thrustBearingCapacity the rated thrust-bearing capacity in N
   */
  public void setThrustBearingCapacity(double thrustBearingCapacity) {
    this.thrustBearingCapacity = thrustBearingCapacity;
  }

  /**
   * Set the installed seal-gas heater rating.
   *
   * @param sealGasHeaterRating the heater rating in W
   */
  public void setSealGasHeaterRating(double sealGasHeaterRating) {
    this.sealGasHeaterRating = sealGasHeaterRating;
  }

  /**
   * Set the seal-gas heater thermostat set point.
   *
   * @param sealGasSetPointC the set point in deg C
   */
  public void setSealGasSetPointC(double sealGasSetPointC) {
    this.sealGasSetPointC = sealGasSetPointC;
  }

  /**
   * Set the seal-gas supply temperature.
   *
   * @param sealGasSupplyTemperatureC the supply temperature in deg C
   */
  public void setSealGasSupplyTemperatureC(double sealGasSupplyTemperatureC) {
    this.sealGasSupplyTemperatureC = sealGasSupplyTemperatureC;
  }

  /**
   * Set the seal-gas mass flow per seal.
   *
   * @param sealGasFlowPerSeal the seal-gas flow per seal in kg/s
   */
  public void setSealGasFlowPerSeal(double sealGasFlowPerSeal) {
    this.sealGasFlowPerSeal = sealGasFlowPerSeal;
  }

  /**
   * Set the seal-gas specific heat.
   *
   * @param sealGasSpecificHeat the seal-gas specific heat in J/(kg K)
   */
  public void setSealGasSpecificHeat(double sealGasSpecificHeat) {
    this.sealGasSpecificHeat = sealGasSpecificHeat;
  }

  /**
   * Set the first lateral critical speed.
   *
   * @param firstCriticalSpeed the first critical speed in rpm
   */
  public void setFirstCriticalSpeed(double firstCriticalSpeed) {
    this.firstCriticalSpeed = firstCriticalSpeed;
  }

  /**
   * Set the maximum continuous operating speed (used when the machine has not been run).
   *
   * @param maxContinuousSpeed the maximum continuous speed in rpm
   */
  public void setMaxContinuousSpeed(double maxContinuousSpeed) {
    this.maxContinuousSpeed = maxContinuousSpeed;
  }

  /**
   * Set the minimum critical-speed separation margin.
   *
   * @param minSeparationMargin the minimum separation margin (fraction)
   */
  public void setMinSeparationMargin(double minSeparationMargin) {
    this.minSeparationMargin = minSeparationMargin;
  }
}
