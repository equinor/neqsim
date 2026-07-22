package neqsim.process.util.monitor;

import neqsim.process.equipment.heatexchanger.HeatExchanger;

/**
 * HXResponse class.
 *
 * @author asmund
 * @version $Id: $Id
 */
public class HXResponse extends BaseResponse {
  public Double feedTemperature1;
  public Double dischargeTemperature1;
  public Double HXthermalEfectiveness;

  public Double feedTemperature2;
  public Double dischargeTemperature2;

  public Double dutyBalance;
  public Double duty;
  public Double UAvalue;

  /**
   * Constructor for HXResponse.
   */
  public HXResponse() {
  }

  /**
   * Constructor for HXResponse.
   *
   * @param inputHeatExchanger a {@link neqsim.process.equipment.heatexchanger.HeatExchanger} object
   */
  public HXResponse(HeatExchanger inputHeatExchanger) {
    super(inputHeatExchanger);
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
