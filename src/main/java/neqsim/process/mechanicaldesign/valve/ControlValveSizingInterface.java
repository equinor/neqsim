package neqsim.process.mechanicaldesign.valve;

import java.util.Map;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * ControlValveSizingInterface interface.
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
   * calculateFlowRateFromValveOpening.
   *
   * @param ActualKv a double
   * @param inletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   * @param outletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   * @return a double
   */
  public double calculateFlowRateFromValveOpening(double ActualKv, StreamInterface inletStream,
      StreamInterface outletStream);

  /**
   * calculateValveOpeningFromFlowRate.
   *
   * @param Q a double
   * @param ActualActualKv a double
   * @param inletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   * @param outletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   * @return a double
   */
  public double calculateValveOpeningFromFlowRate(double Q, double ActualActualKv, StreamInterface inletStream,
      StreamInterface outletStream);

  /**
   * findOutletPressureForFixedKv.
   *
   * @param ActualKv a double
   * @param inletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   * @return a double
   */
  public double findOutletPressureForFixedKv(double ActualKv, StreamInterface inletStream);

  /**
   * isAllowChoked.
   *
   * @return a boolean
   */
  public boolean isAllowChoked();

  /**
   * setAllowChoked.
   *
   * @param allowChoked a boolean
   */
  public void setAllowChoked(boolean allowChoked);

  /**
   * getxT.
   *
   * @return a double
   */
  public double getxT();

  /**
   * setxT.
   *
   * @param xT a double
   */
  public void setxT(double xT);
}
