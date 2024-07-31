package neqsim.processSimulation.util.monitor;

import neqsim.processSimulation.processEquipment.heatExchanger.HeatExchanger;

/**
 * <p>
 * HXResponse class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class HXResponse {
  public String name = "test";

  public Double feedTemperature1;
  public Double dischargeTemperature1;
  public Double HXthermalEfectiveness;

  public Double feedTemperature2;
  public Double dischargeTemperature2;

  public Double dutyBalance;
  public Double duty;
  public Double UAvalue;

  /**
   * <p>
   * Constructor for HXResponse.
   * </p>
   */
  public HXResponse() {}

  /**
   * <p>
   * Constructor for HXResponse.
   * </p>
   *
   * @param inputHeatExchanger a
   *        {@link neqsim.processSimulation.processEquipment.heatExchanger.HeatExchanger} object
   */
  public HXResponse(HeatExchanger inputHeatExchanger) {
    name = inputHeatExchanger.getName();
    feedTemperature1 = inputHeatExchanger.getInStream(0).getTemperature("C");
    dischargeTemperature1 = inputHeatExchanger.getOutStream(0).getTemperature("C");
    feedTemperature2 = inputHeatExchanger.getInStream(1).getTemperature("C");
    dischargeTemperature2 = inputHeatExchanger.getOutStream(1).getTemperature("C");
    HXthermalEfectiveness = inputHeatExchanger.getThermalEffectiveness();
    dutyBalance = inputHeatExchanger.getHotColdDutyBalance();
    duty = inputHeatExchanger.getDuty();
    UAvalue = inputHeatExchanger.getUAvalue();
  }
}
