package neqsim.process.util.monitor;

import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.util.report.ReportConfig;
import neqsim.process.util.report.ReportConfig.DetailLevel;

/**
 * <p>
 * CompressorResponse class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class CompressorResponse extends BaseResponse {
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
  public Double stdFlow;
  public Double speed;

  /**
   * <p>
   * Constructor for CompressorResponse.
   * </p>
   */
  public CompressorResponse() {}

  /**
   * <p>
   * Constructor for CompressorResponse.
   * </p>
   *
   * @param inputCompressor a {@link neqsim.process.equipment.compressor.Compressor} object
   */
  public CompressorResponse(Compressor inputCompressor) {
    super(inputCompressor);
    molarMass = inputCompressor.getInletStream().getFluid().getMolarMass();
    suctionMassDensity = inputCompressor.getInletStream().getFluid().getDensity("kg/m3");
    dischargeMassDensity = inputCompressor.getOutletStream().getFluid().getDensity("kg/m3");
    massflow = inputCompressor.getInletStream().getFluid().getFlowRate("kg/hr");
    stdFlow = inputCompressor.getInletStream().getFluid().getFlowRate("Sm3/hr");
    suctionVolumeFlow = inputCompressor.getInletStream().getFluid().getFlowRate("m3/hr");
    dischargeVolumeFlow = inputCompressor.getOutletStream().getFluid().getFlowRate("m3/hr");
    suctionPressure = inputCompressor.getInletStream().getPressure("bara");
    suctionTemperature = inputCompressor.getInletStream().getTemperature("C");
    dischargeTemperature = inputCompressor.getOutletStream().getTemperature("C");
    dischargePressure = inputCompressor.getOutletStream().getPressure("bara");
    polytropicHead = inputCompressor.getPolytropicFluidHead();
    polytropicEfficiency = inputCompressor.getPolytropicEfficiency();
    power = inputCompressor.getPower("kW");
    speed = inputCompressor.getSpeed();
    if (inputCompressor.getAntiSurge().isActive()) {
      internalVolumeFlow =
          inputCompressor.getCompressorChart().getSurgeCurve().getSurgeFlow(polytropicHead);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void applyConfig(ReportConfig cfg) {
    DetailLevel level = getDetailLevel(cfg);
    if (level == DetailLevel.SUMMARY) {
      polytropicHead = null;
      polytropicEfficiency = null;
      internalVolumeFlow = null;
      speed = null;
    } else if (level == DetailLevel.MINIMUM) {
      polytropicHead = null;
      polytropicEfficiency = null;
      internalVolumeFlow = null;
      speed = null;
      suctionTemperature = null;
      dischargeTemperature = null;
      suctionVolumeFlow = null;
      dischargeVolumeFlow = null;
      molarMass = null;
      suctionMassDensity = null;
      dischargeMassDensity = null;
      massflow = null;
      stdFlow = null;
    }
  }
}
