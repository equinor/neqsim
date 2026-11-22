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
  private String thermalModel;
  private Double configuredSteadyUAvalue;
  private java.util.Map<String, Object> thermalBalance;

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
    boolean thermal = inputHeatExchanger.isDynamicModelEnabled();
    thermalModel = thermal ? "SINGLE_PHASE_FIXED_PRESSURE_TWO_MIXED_FLUIDS_AND_WALL" : "STEADY_SPECIFICATION";
    HXthermalEfectiveness = !thermal && Double.isFinite(inputHeatExchanger.getThermalEffectiveness())
        ? inputHeatExchanger.getThermalEffectiveness()
        : null;
    dutyBalance = thermal ? null : inputHeatExchanger.getHotColdDutyBalance();
    duty = inputHeatExchanger.getDuty();
    UAvalue = thermal ? null : inputHeatExchanger.getUAvalue();
    configuredSteadyUAvalue = thermal && Double.isFinite(inputHeatExchanger.getUAvalue())
        ? inputHeatExchanger.getUAvalue()
        : null;
    thermalBalance = thermal && inputHeatExchanger.getLastThermalBalance() != null
        ? inputHeatExchanger.getLastThermalBalance().toMap()
        : null;
  }

  /**
   * Identifies the active thermal formulation.
   *
   * @return active thermal model identifier
   */
  public String getThermalModel() {
    return thermalModel;
  }

  /**
   * Reads the retained steady UA while thermal mode uses the film/area formulation.
   *
   * @return configured steady UA in W/K, or null outside thermal mode
   */
  public Double getConfiguredSteadyUAvalue() {
    return configuredSteadyUAvalue;
  }

  /**
   * Reads a detached copy of the accepted interval's thermal receipt.
   *
   * @return thermal balance receipt, or null before a transient interval
   */
  public java.util.Map<String, Object> getThermalBalance() {
    return thermalBalance == null ? null : new java.util.LinkedHashMap<String, Object>(thermalBalance);
  }

}
