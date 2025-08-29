package neqsim.process.mechanicaldesign.valve;

import java.io.Serializable;

/**
 * An interface for defining the flow characteristic of a valve. This allows for implementing
 * different valve behaviors like linear, equal percentage, etc.
 *
 * @author esol
 */
public interface ValveCharacteristic extends Serializable {

  /**
   * <p>
   * getActualKv.
   * </p>
   *
   * @param Kv a double
   * @param percentOpening a double
   * @return a double
   */
  public double getActualKv(double Kv, double percentOpening);

  /**
   * <p>
   * getOpeningFactor.
   * </p>
   *
   * @param percentOpening a double
   * @return a double
   */
  public double getOpeningFactor(double percentOpening);
}
