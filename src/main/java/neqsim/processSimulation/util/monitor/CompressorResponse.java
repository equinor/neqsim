package neqsim.processSimulation.util.monitor;

import neqsim.processSimulation.processEquipment.compressor.Compressor;

public class CompressorResponse {
    public String name = "test";

    public Double suctionTemperature;
    public Double dischargeTemperature;
    public Double suctionPressure;
    public Double dischargePressure;
    public Double polytropicHead;
    public Double polytropicEfficiency;
    public Double power;
    public Double suctionVolumeFlow;
    public Double internalVolumeFlow;
    public Double dischargeVolumeFlow;
    public Double molarMass;
    public Double suctionMassDensity;
    public Double dischargeMassDensity;
    public Double massflow;
    public Integer speed;

    public CompressorResponse() {
    }

    public CompressorResponse(Compressor inputCompressor){
        name = inputCompressor.getName();
        molarMass = inputCompressor.getInStream().getFluid().getMolarMass();
        suctionMassDensity = inputCompressor.getInStream().getFluid().getDensity("kg/m3");
        dischargeMassDensity = inputCompressor.getOutStream().getFluid().getDensity("kg/m3");
        massflow = inputCompressor.getInStream().getFluid().getFlowRate("kg/hr");
        suctionVolumeFlow = inputCompressor.getInStream().getFluid().getFlowRate("m3/hr");
        dischargeVolumeFlow = inputCompressor.getOutStream().getFluid().getFlowRate("m3/hr");
        suctionPressure = inputCompressor.getInStream().getPressure("bara");
        suctionTemperature = inputCompressor.getInStream().getTemperature("C");
        dischargeTemperature = inputCompressor.getOutStream().getTemperature("C");
        dischargePressure = inputCompressor.getOutStream().getPressure("bara");
        polytropicHead = inputCompressor.getPolytropicFluidHead();
        polytropicEfficiency = inputCompressor.getPolytropicEfficiency();
        power = inputCompressor.getPower("kW");
        speed = inputCompressor.getSpeed();
        if(inputCompressor.getAntiSurge().isActive()){
            internalVolumeFlow = inputCompressor.getCompressorChart().getSurgeCurve().getSurgeFlow(polytropicHead);
        }   
    }
}
