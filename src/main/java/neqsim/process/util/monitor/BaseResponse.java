package neqsim.process.util.monitor;

import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;

public class BaseResponse {

  public String tagName = "";
  public String name = "";

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

  public BaseResponse(MeasurementDeviceInterface equipment) {
    tagName = equipment.getTagName();
    name = equipment.getName();
  }
}
