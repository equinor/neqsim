/*
 * CompressorInterface.java
 *
 * Created on 22. august 2001, 17:20
 */

package neqsim.process.equipment.compressor;

import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.TwoPortInterface;

/**
 * <p>
 * CompressorInterface interface.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public interface CompressorInterface extends ProcessEquipmentInterface, TwoPortInterface {
  /**
   * <p>
   * getEnergy.
   * </p>
   *
   * @return a double
   */
  public double getEnergy();

  /**
   * <p>
   * getIsentropicEfficiency.
   * </p>
   *
   * @return a double
   */
  public double getIsentropicEfficiency();

  /**
   * <p>
   * setIsentropicEfficiency.
   * </p>
   *
   * @param isentropicEfficiency a double
   */
  public void setIsentropicEfficiency(double isentropicEfficiency);

  /**
   * <p>
   * getPolytropicEfficiency.
   * </p>
   *
   * @return a double
   */
  public double getPolytropicEfficiency();

  /**
   * <p>
   * setPolytropicEfficiency.
   * </p>
   *
   * @param polytropicEfficiency a double
   */
  public void setPolytropicEfficiency(double polytropicEfficiency);

  /**
   * <p>
   * getAntiSurge.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.compressor.AntiSurge} object
   */
  public AntiSurge getAntiSurge();

  /**
   * <p>
   * getDistanceToSurge.
   * </p>
   *
   * @return a double
   */
  public double getDistanceToSurge();

  /**
   * <p>
   * setMaximumSpeed.
   * </p>
   *
   * @param maxSpeed a double
   */
  public void setMaximumSpeed(double maxSpeed);

  /**
   * <p>
   * setMinimumSpeed.
   * </p>
   *
   * @param minspeed a double
   */
  public void setMinimumSpeed(double minspeed);

  /**
   * <p>
   * getMaximumSpeed.
   * </p>
   *
   * @return a double
   */
  public double getMaximumSpeed();

  /**
   * <p>
   * getMinimumSpeed.
   * </p>
   *
   * @return a double
   */
  public double getMinimumSpeed();

  /**
   * <p>
   * getSurgeFlowRateMargin.
   * </p>
   *
   * @return a double
   */
  public double getSurgeFlowRateMargin();

  /**
   * <p>
   * getSurgeFlowRate.
   * </p>
   *
   * @return a double
   */
  public double getSurgeFlowRate();

  /**
   * <p>
   * Set CompressorChartType
   * </p>
   *
   * @param type a {@link java.lang.String} object
   */
  public void setCompressorChartType(String type);

  /**
   * <p>
   * isSurge.
   * </p>
   *
   * @return a boolean
   */
  default boolean isSurge() {
    if (getDistanceToSurge() < 0) {
      return true;
    } else {
      return false;
    }
  }

}
