/*
 * CompressorInterface.java
 *
 * Created on 22. august 2001, 17:20
 */

package neqsim.processSimulation.processEquipment.compressor;

import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processEquipment.TwoPortInterface;

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
   * @return a {@link neqsim.processSimulation.processEquipment.compressor.AntiSurge} object
   */
  public AntiSurge getAntiSurge();

  public double getDistanceToSurge();

  public void setMaximumSpeed(double maxSpeed);

  public void setMinimumSpeed(double minspeed);

  public double getMaximumSpeed();

  public double getMinimumSpeed();
}
