package neqsim.process.util.monitor;

import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;

/**
 * <p>
 * BaseResponse class.
 * </p>
 *
 * @author esol
 */
public class BaseResponse {
  public String tagName = "";
  public String name = "";

  /**
   * <p>
   * Constructor for BaseResponse.
   * </p>
   */
  public BaseResponse() {
    // Default constructor
  }

  /**
   * <p>
   * Constructor for BaseResponse.
   * </p>
   *
   * @param equipment a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   */
  public BaseResponse(ProcessEquipmentInterface equipment) {
    tagName = equipment.getTagName();
    name = equipment.getName();
  }

  /**
   * <p>
   * Constructor for BaseResponse.
   * </p>
   *
   * @param equipment a {@link neqsim.process.measurementdevice.MeasurementDeviceInterface} object
   */
  public BaseResponse(MeasurementDeviceInterface equipment) {
    tagName = equipment.getTagName();
    name = equipment.getName();
  }
}
