/*
 * ValveInterface.java
 *
 * Created on 22. august 2001, 17:20
 */

package neqsim.process.equipment.valve;

import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.TwoPortInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * ValveInterface interface.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public interface ValveInterface extends ProcessEquipmentInterface, TwoPortInterface {
  /**
   * <p>
   * isIsoThermal.
   * </p>
   *
   * @return a boolean
   */
  public boolean isIsoThermal();

  /**
   * <p>
   * setIsoThermal.
   * </p>
   *
   * @param isoThermal a boolean
   */
  public void setIsoThermal(boolean isoThermal);

  /**
   * <p>
   * getPercentValveOpening.
   * </p>
   *
   * @return a double
   */
  public double getPercentValveOpening();

  /**
   * <p>
   * setPercentValveOpening.
   * </p>
   *
   * @param percentValveOpening a double
   */
  public void setPercentValveOpening(double percentValveOpening);

  /**
   * Returns the requested (target) valve opening before dynamic travel limitations are applied.
   *
   * @return requested valve opening in percent
   */
  public double getTargetPercentValveOpening();

  /**
   * Sets the requested (target) valve opening in percent. The actual valve opening may lag the
   * request depending on the selected travel model.
   *
   * @param percentValveOpening target valve opening in percent
   */
  public void setTargetPercentValveOpening(double percentValveOpening);

  /**
   * <p>
   * getCv.
   * </p>
   *
   * @return a double
   */
  public double getCv();

  /**
   * <p>
   * getCg.
   * </p>
   *
   * @return a double
   */
  public double getCg();

  /**
   * <p>
   * getCv.
   * </p>
   *
   * @param unit can be SI or US SI is unit litre/minute US is gallons per minute
   * @return a double
   */
  public double getCv(String unit);

  /**
   * <p>
   * setCv.
   * </p>
   *
   * @param Cv a double
   */
  public void setCv(double Cv);

  /**
   * <p>
   * setCv.
   * </p>
   *
   * @param Cv a double
   * @param unit can be SI or US SI is unit litre/minute US is gallons per minute
   */
  public void setCv(double Cv, String unit);

  /** {@inheritDoc} */
  @Override
  public SystemInterface getThermoSystem();

  /**
   * Sets the Kv (metric flow coefficient) of the valve.
   *
   * @param Kv the metric flow coefficient
   */
  public void setKv(double Kv);

  /**
   * Gets the Kv (metric flow coefficient) of the valve.
   *
   * @return the metric flow coefficient
   */
  public double getKv();

  /**
   * Sets the total travel time (seconds) corresponding to a movement from fully closed to fully
   * open.
   *
   * @param travelTimeSec full travel time in seconds
   */
  public void setTravelTime(double travelTimeSec);

  /**
   * Returns the configured full travel time (seconds) for the valve.
   *
   * @return full travel time in seconds
   */
  public double getTravelTime();

  /**
   * Sets the travel time for opening actions (seconds). When not specified, the symmetric travel
   * time is used.
   *
   * @param travelTimeSec opening travel time in seconds
   */
  public void setOpeningTravelTime(double travelTimeSec);

  /**
   * Returns the configured opening travel time (seconds).
   *
   * @return opening travel time in seconds
   */
  public double getOpeningTravelTime();

  /**
   * Sets the travel time for closing actions (seconds). When not specified, the symmetric travel
   * time is used.
   *
   * @param travelTimeSec closing travel time in seconds
   */
  public void setClosingTravelTime(double travelTimeSec);

  /**
   * Returns the configured closing travel time (seconds).
   *
   * @return closing travel time in seconds
   */
  public double getClosingTravelTime();

  /**
   * Sets the time constant (seconds) for first-order travel dynamics.
   *
   * @param timeConstantSec time constant in seconds
   */
  public void setTravelTimeConstant(double timeConstantSec);

  /**
   * Returns the configured time constant (seconds) for first-order travel dynamics.
   *
   * @return time constant in seconds
   */
  public double getTravelTimeConstant();

  /**
   * Sets the valve travel model used to translate requested opening to actual position.
   *
   * @param travelModel travel model implementation
   */
  public void setTravelModel(ValveTravelModel travelModel);

  /**
   * Returns the current valve travel model.
   *
   * @return travel model
   */
  public ValveTravelModel getTravelModel();
}
