package neqsim.processSimulation.util.monitor;

import neqsim.processSimulation.processEquipment.heatExchanger.Heater;

/**
 * <p>
 * HeaterResponse class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class HeaterResponse {
  public String name = "test";

  public Double feedTemperature;
  public Double dischargeTemperature;
  public Double duty;

  /**
   * <p>
   * Constructor for HeaterResponse.
   * </p>
   */
  public HeaterResponse() {}

  /**
   * <p>
   * Constructor for HeaterResponse.
   * </p>
   *
   * @param inputHeater a {@link neqsim.processSimulation.processEquipment.heatExchanger.Heater}
   *        object
   */
  public HeaterResponse(Heater inputHeater) {
    name = inputHeater.getName();

    feedTemperature = inputHeater.getInletStream().getTemperature("C");
    dischargeTemperature = inputHeater.getOutletStream().getTemperature("C");

    duty = inputHeater.getDuty();
  }
}
