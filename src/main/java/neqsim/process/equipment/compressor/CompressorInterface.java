/*
 * CompressorInterface.java
 *
 * Created on 22. august 2001, 17:20
 */

package neqsim.process.equipment.compressor;

import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.TwoPortInterface;

/**
 * CompressorInterface interface.
 *
 * @author esol
 * @version $Id: $Id
 */
public interface CompressorInterface extends ProcessEquipmentInterface, TwoPortInterface {
  /**
   * getEnergy.
   *
   * @return a double
   */
  public double getEnergy();

  /**
   * getIsentropicEfficiency.
   *
   * @return a double
   */
  public double getIsentropicEfficiency();

  /**
   * setIsentropicEfficiency.
   *
   * @param isentropicEfficiency a double
   */
  public void setIsentropicEfficiency(double isentropicEfficiency);

  public double getSurgeFlowRateStd();

  /**
   * getPolytropicEfficiency.
   *
   * @return a double
   */
  public double getPolytropicEfficiency();

  /**
   * setPolytropicEfficiency.
   *
   * @param polytropicEfficiency a double
   */
  public void setPolytropicEfficiency(double polytropicEfficiency);

  /**
   * getAntiSurge.
   *
   * @return a {@link neqsim.process.equipment.compressor.AntiSurge} object
   */
  public AntiSurge getAntiSurge();

  /**
   * getDistanceToSurge.
   *
   * @return a double
   */
  public double getDistanceToSurge();

  /**
   * setMaximumSpeed.
   *
   * @param maxSpeed a double
   */
  public void setMaximumSpeed(double maxSpeed);

  /**
   * setMinimumSpeed.
   *
   * @param minspeed a double
   */
  public void setMinimumSpeed(double minspeed);

  /**
   * getMaximumSpeed.
   *
   * @return a double
   */
  public double getMaximumSpeed();

  /**
   * getMinimumSpeed.
   *
   * @return a double
   */
  public double getMinimumSpeed();

  /**
   * getSurgeFlowRateMargin.
   *
   * @return a double
   */
  public double getSurgeFlowRateMargin();

  /**
   * getSurgeFlowRate.
   *
   * @return a double
   */
  public double getSurgeFlowRate();

  /**
   * isStoneWall.
   *
   * @return a boolean
   */
  public boolean isStoneWall();

  /**
   * Set CompressorChartType
   *
   * @param type a {@link java.lang.String} object
   */
  public void setCompressorChartType(String type);

  /**
   * isSurge.
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
