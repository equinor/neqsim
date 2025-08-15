package neqsim.process.util.monitor;

import neqsim.process.equipment.expander.TurboExpanderCompressor;

/**
 * <p>
 * TurboExpanderCompressorResponse class.
 * </p>
 *
 * @author esol
 */
public class TurboExpanderCompressorResponse extends BaseResponse {
  /**
   * Constructs a TurboExpanderCompressorResponse from a TurboExpanderCompressor.
   *
   * @param turboExpanderCompressor a
   *        {@link neqsim.process.equipment.expander.TurboExpanderCompressor} object
   */
  public TurboExpanderCompressorResponse(TurboExpanderCompressor turboExpanderCompressor) {
    super(turboExpanderCompressor);
    this.speed = turboExpanderCompressor.getSpeed();
    this.powerExpander = turboExpanderCompressor.getPowerExpander();
    this.powerCompressor = turboExpanderCompressor.getPowerCompressor();
    this.compressorPolytropicEfficiency =
        turboExpanderCompressor.getCompressorPolytropicEfficiency();
    this.expanderIsentropicEfficiency = turboExpanderCompressor.getExpanderIsentropicEfficiency();
    this.compressorPolytropicHead = turboExpanderCompressor.getCompressorPolytropicHead();
    this.UCratioexpander = turboExpanderCompressor.getUCratioexpander();
    this.QNratiocompressor = turboExpanderCompressor.getQNratiocompressor();

    this.expanderFeedTemperature =
        turboExpanderCompressor.getExpanderFeedStream().getTemperature("C");
    this.expanderFeedFlow = turboExpanderCompressor.getExpanderFeedStream().getFlowRate("kg/hr");
    this.expanderFeedPressure = turboExpanderCompressor.getExpanderFeedStream().getPressure("bara");
    this.expanderDischargeTemperature =
        turboExpanderCompressor.getExpanderOutletStream().getTemperature("C");
    this.expanderDischargePressure =
        turboExpanderCompressor.getExpanderOutletStream().getPressure("bara");
    this.compressorFeedFlow =
        turboExpanderCompressor.getCompressorFeedStream().getFlowRate("kg/hr");
    this.compresorFeedPressure =
        turboExpanderCompressor.getCompressorFeedStream().getPressure("bara");
    this.compresorFeedTemperature =
        turboExpanderCompressor.getCompressorFeedStream().getTemperature("C");
    this.compresorDischargePressure =
        turboExpanderCompressor.getCompressorOutletStream().getPressure("bara");
    this.compresorDischargeTemperature =
        turboExpanderCompressor.getCompressorOutletStream().getTemperature("C");
  }

  private Double speed = 0.0;
  private Double powerExpander = 0.0;
  private Double powerCompressor = 0.0;
  private Double compressorPolytropicEfficiency = 0.0;
  private Double expanderIsentropicEfficiency = 0.0;
  private Double compressorPolytropicHead = 0.0;
  private Double UCratioexpander = 0.0;
  private Double QNratiocompressor = 0.0;
  private Double expanderFeedTemperature = 0.0;
  private Double expanderFeedFlow = 0.0;
  private Double expanderFeedPressure = 0.0;
  private Double expanderDischargeTemperature = 0.0;
  private Double expanderDischargePressure = 0.0;
  private Double compressorFeedFlow = 0.0;
  private Double compresorFeedPressure = 0.0;
  private Double compresorFeedTemperature = 0.0;
  private Double compresorDischargePressure = 0.0;
  private Double compresorDischargeTemperature = 0.0;
}
