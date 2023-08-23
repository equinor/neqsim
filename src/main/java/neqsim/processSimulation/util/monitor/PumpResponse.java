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
     *
     * @param inputPump a {@link neqsim.processSimulation.processEquipment.pump.Pump} object
     */
    public PumpResponse(Pump inputPump) {
        name = inputPump.getName();
        molarMass = inputPump.getInletStream().getFluid().getMolarMass();
        suctionMassDensity = inputPump.getInletStream().getFluid().getDensity("kg/m3");
        dischargeMassDensity = inputPump.getOutletStream().getFluid().getDensity("kg/m3");
        massflow = inputPump.getInletStream().getFluid().getFlowRate("kg/hr");
        suctionVolumeFlow = inputPump.getInletStream().getFluid().getFlowRate("m3/hr");
        dischargeVolumeFlow = inputPump.getOutletStream().getFluid().getFlowRate("m3/hr");
        suctionPressure = inputPump.getInletStream().getPressure("bara");
        suctionTemperature = inputPump.getInletStream().getTemperature("C");
        dischargeTemperature = inputPump.getOutletStream().getTemperature("C");
        dischargePressure = inputPump.getOutletStream().getPressure("bara");

        // polytropicHead = inputCompressor.getPolytropicFluidHead();
        // polytropicEfficiency =inputCompressor.getPolytropicEfficiency();
        power = inputPump.getPower("W"); // "kW");
        // speed = inputPump.getSpeed();
        // if(inputCompressor.getAntiSurge().isActive()){
        // internalVolumeFlow =
        // inputCompressor.getCompressorChart().getSurgeCurve().getSurgeFlow(polytropicHead);
        // }
    }
}
