package neqsim.process.mechanicaldesign.valve;

import java.util.Map;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * <p>
 * ControlValveSizingInterface interface.
 * </p>
 *
 * @author esol
 */
public interface ControlValveSizingInterface {

  /**
   * Calculates the valve size based on the fluid properties and operating conditions.
   *
   * @param percentOpening a double
   * @return a {@link java.util.Map} object
   */
  public Map<String, Object> calcValveSize(double percentOpening);

  /**
   * <p>
   * calculateFlowRateFromValveOpening.
   * </p>
   *
   * @param ActualKv a double
   * @param inletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   * @param outletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   * @return a double
   */
  public double calculateFlowRateFromValveOpening(double ActualKv, StreamInterface inletStream,
      StreamInterface outletStream);

  /**
   * <p>
   * calculateValveOpeningFromFlowRate.
   * </p>
   *
   * @param Q a double
   * @param ActualActualKv a double
   * @param inletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   * @param outletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   * @return a double
   */
  public double calculateValveOpeningFromFlowRate(double Q, double ActualActualKv,
      StreamInterface inletStream, StreamInterface outletStream);

  /**
   * <p>
   * findOutletPressureForFixedKv.
   * </p>
   *
   * @param ActualKv a double
   * @param inletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   * @return a double
   */
  public double findOutletPressureForFixedKv(double ActualKv, StreamInterface inletStream);

  /**
   * <p>
   * isAllowChoked.
   * </p>
   *
   * @return a boolean
   */
  public boolean isAllowChoked();

  /**
   * <p>
   * setAllowChoked.
   * </p>
   *
   * @param allowChoked a boolean
   */
  public void setAllowChoked(boolean allowChoked);

  /**
   * <p>
   * getxT.
   * </p>
   *
   * @return a double
   */
  public double getxT();

  /**
   * <p>
   * setxT.
   * </p>
   *
   * @param xT a double
   */
  public void setxT(double xT);
}

