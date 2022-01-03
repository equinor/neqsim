package neqsim.processSimulation.util.monitor;

import neqsim.processSimulation.processEquipment.heatExchanger.HeatExchanger;

/**
 * <p>HXResponse class.</p>
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

    

    /**
     * <p>Constructor for HXResponse.</p>
     */
    public HXResponse(){

    }


    /**
     * <p>Constructor for HXResponse.</p>
     *
     * @param inputHeatExchenger a {@link neqsim.processSimulation.processEquipment.heatExchanger.HeatExchanger} object
     */
    public HXResponse(HeatExchanger inputHeatExchenger){
        name = inputHeatExchenger.getName();

        feedTemperature1 = inputHeatExchenger.getInStream(0).getTemperature("C");
        dischargeTemperature1 = inputHeatExchenger.getOutStream(0).getTemperature("C");

        feedTemperature2 = inputHeatExchenger.getInStream(1).getTemperature("C");
        dischargeTemperature2 = inputHeatExchenger.getOutStream(1).getTemperature("C");

        HXthermalEfectiveness = inputHeatExchenger.getThermalEffectiveness();
        
        dutyBalance = inputHeatExchenger.getHotColdDutyBalance();
    }
}
