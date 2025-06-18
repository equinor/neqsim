package neqsim.process.util.monitor;

import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;

/**
 * <p>
 * PumpResponse class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class PipeBeggsBrillsResponse extends BaseResponse {

  public Double inletPressure;
  public Double outletPressure;
  public Double inletTemperature;
  public Double outletTemperature;
  public Double inletDensity;
  public Double outletDensity;
  public Double inletVolumeFlow;
  public Double outletVolumeFlow;
  public Double inletMassFlow;
  public Double outletMassFlow;

  /**
   * <p>
   * Constructor for PumpResponse.
   * </p>
   *
   * @param pipe the pipe to set for the response.
   */
  public PipeBeggsBrillsResponse(PipeBeggsAndBrills pipe) {
    super(pipe);
    inletPressure = pipe.getInletStream().getPressure("bara");
    outletPressure = pipe.getOutletStream().getPressure("bara");
    inletTemperature = pipe.getInletStream().getTemperature("C");
    outletTemperature = pipe.getOutletStream().getTemperature("C");
    inletDensity = pipe.getInletStream().getFluid().getDensity("kg/m3");
    outletDensity = pipe.getOutletStream().getFluid().getDensity("kg/m3");
    inletVolumeFlow = pipe.getInletStream().getFluid().getFlowRate("m3/hr");
    outletVolumeFlow = pipe.getOutletStream().getFluid().getFlowRate("m3/hr");
    inletMassFlow = pipe.getInletStream().getFluid().getFlowRate("kg/hr");
    outletMassFlow = pipe.getOutletStream().getFluid().getFlowRate("kg/hr");
  }
}
