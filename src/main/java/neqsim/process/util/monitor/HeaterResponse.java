package neqsim.process.util.monitor;

import neqsim.process.equipment.heatexchanger.Heater;

/**
 * <p>
 * HeaterResponse class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class HeaterResponse extends BaseResponse {

  public Double feedTemperature;
  public Double dischargeTemperature;
  public Double duty;

  /**
   * <p>
   * Constructor for HeaterResponse.
   * </p>
   *
   * @param inputHeater a {@link neqsim.process.equipment.heatexchanger.Heater} object
   */
  public HeaterResponse(Heater inputHeater) {
    super(inputHeater);

    feedTemperature = inputHeater.getInletStream().getTemperature("C");
    dischargeTemperature = inputHeater.getOutletStream().getTemperature("C");

    duty = inputHeater.getDuty();
  }
}
