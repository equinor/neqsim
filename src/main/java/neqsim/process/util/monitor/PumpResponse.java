package neqsim.process.util.monitor;

import java.util.ArrayList;
import neqsim.process.equipment.pump.Pump;

/**
 * <p>
 * PumpResponse class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class PumpResponse extends BaseResponse {
  public ArrayList<String[]> data = new ArrayList<String[]>();
  public Double suctionTemperature;
  public Double dischargeTemperature;
  public Double suctionPressure;
  public Double dischargePressure;
  // public Double polytropicHead;
  // public Double polytropicEfficiency;
  public Double power;
  public Double duty;
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
   * @param inputPump a {@link neqsim.process.equipment.pump.Pump} object
   */
  public PumpResponse(Pump inputPump) {
    super(inputPump);
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
    duty = power;
    // speed = inputPump.getSpeed();
    // if(inputCompressor.getAntiSurge().isActive()){
    // internalVolumeFlow =
    // inputCompressor.getCompressorChart().getSurgeCurve().getSurgeFlow(polytropicHead);
    // }

    data.add(new String[] {"suction temperature", Double.toString(suctionTemperature),
        neqsim.util.unit.Units.getSymbol("temperature")});
    data.add(new String[] {"discharge temperature", Double.toString(dischargeTemperature),
        neqsim.util.unit.Units.getSymbol("temperature")});
    data.add(new String[] {"suction pressure", Double.toString(suctionPressure),
        neqsim.util.unit.Units.getSymbol("pressure")});
    data.add(new String[] {"discharge pressure", Double.toString(dischargePressure),
        neqsim.util.unit.Units.getSymbol("pressure")});
    data.add(new String[] {"mass flow", Double.toString(massflow),
        neqsim.util.unit.Units.getSymbol("mass flow")});
    data.add(new String[] {"duty", Double.toString(duty), neqsim.util.unit.Units.getSymbol("duty")});
    data.add(new String[] {"power", Double.toString(power), "W"});
  }
}
