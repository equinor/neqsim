package neqsim.processSimulation.util.monitor;

import neqsim.processSimulation.processEquipment.heatExchanger.Heater;

public class HeaterResponse {

    public String name = "test";

    
    public Double feedTemperature;
    public Double dischargeTemperature;
    public Double duty;

    

    public HeaterResponse(){

    }


    public HeaterResponse(Heater inputHeater){
        name = inputHeater.getName();

        feedTemperature = inputHeater.getInStream().getTemperature("C");
        dischargeTemperature = inputHeater.getOutStream().getTemperature("C");

        duty = inputHeater.getDuty();

        }

    }