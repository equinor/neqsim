package neqsim.processSimulation.util.monitor;

import neqsim.processSimulation.processEquipment.stream.StreamInterface;

public class StreamResponse {
    public String name;
    public Fluid fluid;

    public Double volumeFlow;
    public Double molarMass;
    public Double massDensity;
    public Double massflow;

    public StreamResponse() {}

    public StreamResponse() {
    }

    public StreamResponse(StreamInterface inputStream){
        name = inputStream.getName();
        fluid = new Fluid(inputStream.getFluid());

        molarMass = inputStream.getFluid().getMolarMass();
        massDensity = inputStream.getFluid().getDensity("kg/m3");
        massflow = inputStream.getFluid().getFlowRate("kg/hr");
        volumeFlow = inputStream.getFluid().getFlowRate("m3/hr");
    }

    public void print(){
    }
}