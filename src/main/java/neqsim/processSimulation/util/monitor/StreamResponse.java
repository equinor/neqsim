package neqsim.processSimulation.util.monitor;

import neqsim.processSimulation.processEquipment.stream.StreamInterface;

public class StreamResponse {
    public String name;
    public Fluid fluid;
    public Double temperature;
    public Double pressure;
    public Double volumeFlow;
    public Double molarMass;
    public Double massDensity;
    public Double massflow;
    public Double massflowGas;
    public Double massflowOil;
    public Double massflowAqueous;

    public StreamResponse() {
    }

    public StreamResponse(StreamInterface inputStream){
        name = inputStream.getName();
        fluid = new Fluid(inputStream.getFluid());
        temperature = inputStream.getTemperature("C");
        pressure = inputStream.getPressure("bara");
        molarMass = inputStream.getFluid().getMolarMass();
        massDensity =  inputStream.getFluid().getDensity("kg/m3");
        massflow =  inputStream.getFluid().getFlowRate("kg/hr");
        volumeFlow =  inputStream.getFluid().getFlowRate("m3/hr");
        
        if(inputStream.getFluid().hasPhaseType("gas")){
        	massflowGas = inputStream.getFluid().getPhase("gas").getFlowRate("kg/hr");
        }
        else {
        	massflowGas = 0.0;
        }
        if(inputStream.getFluid().hasPhaseType("aqueous")){
        	massflowAqueous = inputStream.getFluid().getPhase("aqueous").getFlowRate("kg/hr");
        }
        else {
        	massflowAqueous = 0.0;
        }
        if(inputStream.getFluid().hasPhaseType("oil")){
        	massflowOil = inputStream.getFluid().getPhase("oil").getFlowRate("kg/hr");
        }
        else {
        	massflowOil = 0.0;
        }
    }

    public void print(){
    }
}