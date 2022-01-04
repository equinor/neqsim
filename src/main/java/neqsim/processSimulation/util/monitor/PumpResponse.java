package neqsim.processSimulation.util.monitor;

import neqsim.processSimulation.processEquipment.pump.Pump;

/**
 * <p>
 * PumpResponse class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class PumpResponse {
    public String name = "test";

    public Double suctionTemperature;
    public Double dischargeTemperature;
    public Double suctionPressure;
    public Double dischargePressure;
    // public Double polytropicHead;
    // public Double polytropicEfficiency;
    public Double power;
    public Double suctionVolumeFlow;
    public Double internalVolumeFlow;
    public Double dischargeVolumeFlow;
    public Double molarMass;
    public Double suctionMassDensity;
    public Double dischargeMassDensity;
    public Double massflow;
    public Integer speed;

    /**
     * <p>
     * Constructor for PumpResponse.
     * </p>
     */
    public PumpResponse() {}

    /**
     * <p>
     * Constructor for PumpResponse.
     * </p>
     *
     * @param inputPump a {@link neqsim.processSimulation.processEquipment.pump.Pump} object
     */
    public PumpResponse(Pump inputPump) {
        name = inputPump.getName();
        molarMass = inputPump.getInStream().getFluid().getMolarMass();
        suctionMassDensity = inputPump.getInStream().getFluid().getDensity("kg/m3");
        dischargeMassDensity = inputPump.getOutStream().getFluid().getDensity("kg/m3");
        massflow = inputPump.getInStream().getFluid().getFlowRate("kg/hr");
        suctionVolumeFlow = inputPump.getInStream().getFluid().getFlowRate("m3/hr");
        dischargeVolumeFlow = inputPump.getOutStream().getFluid().getFlowRate("m3/hr");
        suctionPressure = inputPump.getInStream().getPressure("bara");
        suctionTemperature = inputPump.getInStream().getTemperature("C");
        dischargeTemperature = inputPump.getOutStream().getTemperature("C");
        dischargePressure = inputPump.getOutStream().getPressure("bara");

        // polytropicHead = inputCompressor.getPolytropicFluidHead();
        // polytropicEfficiency =inputCompressor.getPolytropicEfficiency();
        power = inputPump.getPower("W");// "kW");
        // speed = inputPump.getSpeed();
        // if(inputCompressor.getAntiSurge().isActive()){
        // internalVolumeFlow =
        // inputCompressor.getCompressorChart().getSurgeCurve().getSurgeFlow(polytropicHead);
        // }
    }
}
