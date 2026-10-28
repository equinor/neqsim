package neqsim.process.util.monitor;

import neqsim.process.equipment.expander.TurboExpanderCompressor;

/**
 * TurboExpanderCompressorResponse class.
 *
 * @author esol
 */
public class TurboExpanderCompressorResponse extends BaseResponse {
  /**
   * Constructs a TurboExpanderCompressorResponse from a TurboExpanderCompressor.
   *
   * @param turboExpanderCompressor a {@link neqsim.process.equipment.expander.TurboExpanderCompressor} object
   */
  public TurboExpanderCompressorResponse(TurboExpanderCompressor turboExpanderCompressor) {
    super(turboExpanderCompressor);
    this.speed = turboExpanderCompressor.getSpeed();
    this.powerExpander = turboExpanderCompressor.getPowerExpander();
    this.powerCompressor = turboExpanderCompressor.getPowerCompressor();
    this.compressorPolytropicEfficiency = turboExpanderCompressor.getCompressorPolytropicEfficiency();
    this.expanderIsentropicEfficiency = turboExpanderCompressor.getExpanderIsentropicEfficiency();
    this.compressorPolytropicHead = turboExpanderCompressor.getCompressorPolytropicHead();
    this.UCratioexpander = turboExpanderCompressor.getUCratioexpander();
    this.QNratiocompressor = turboExpanderCompressor.getQNratiocompressor();

    this.expanderFeedTemperature = turboExpanderCompressor.getExpanderFeedStream().getTemperature("C");
    this.expanderFeedFlow = turboExpanderCompressor.getExpanderFeedStream().getFlowRate("kg/hr");
    this.expanderFeedPressure = turboExpanderCompressor.getExpanderFeedStream().getPressure("bara");
    this.expanderDischargeTemperature = turboExpanderCompressor.getExpanderOutletStream().getTemperature("C");
    this.expanderDischargePressure = turboExpanderCompressor.getExpanderOutletStream().getPressure("bara");
    this.compressorFeedFlow = turboExpanderCompressor.getCompressorFeedStream().getFlowRate("kg/hr");
    this.compresorFeedPressure = turboExpanderCompressor.getCompressorFeedStream().getPressure("bara");
    this.compresorFeedTemperature = turboExpanderCompressor.getCompressorFeedStream().getTemperature("C");
    this.compresorDischargePressure = turboExpanderCompressor.getCompressorOutletStream().getPressure("bara");
    this.compresorDischargeTemperature = turboExpanderCompressor.getCompressorOutletStream().getTemperature("C");
    this.nozzleCapacityEnabled = turboExpanderCompressor.isNozzleCapacityEnabled();
    this.nozzleCapacity = turboExpanderCompressor.getNozzleCapacity();
    this.nozzleCapacityFullOpen = turboExpanderCompressor.getNozzleCapacityFullOpen();
    this.nozzleCapacityUtilisation = turboExpanderCompressor.getNozzleCapacityUtilisation();
    this.fullOpenCapacityUtilisation = turboExpanderCompressor.getFullOpenCapacityUtilisation();
    this.capacityLimited = turboExpanderCompressor.isCapacityLimited();
    this.igvOpening = turboExpanderCompressor.getIGVopening();
    this.jtBypassEnabled = turboExpanderCompressor.isJtBypassEnabled();
    this.jtBypassFlow = turboExpanderCompressor.getJtBypassFlow();
    this.jtBypassFraction = turboExpanderCompressor.getJtBypassFraction();
    this.speedRatioUC0 = turboExpanderCompressor.getSpeedRatioUC0();
    this.speedUtilisation = turboExpanderCompressor.getSpeedUtilisation();
    this.speedStatus = turboExpanderCompressor.getSpeedStatus();
    this.shaftClosureFactor = turboExpanderCompressor.getShaftClosureFactor();
    this.bearingLoss = turboExpanderCompressor.getBearingLoss();
    this.shaftPowerResidual = turboExpanderCompressor.getShaftPowerResidual();
    this.expanderWheelOutletTemperature = turboExpanderCompressor.getExpanderWheelOutletTemperature("C");
  }

  private Boolean nozzleCapacityEnabled = false;
  private Double nozzleCapacity = 0.0;
  private Double nozzleCapacityFullOpen = 0.0;
  private Double nozzleCapacityUtilisation = 0.0;
  private Double fullOpenCapacityUtilisation = 0.0;
  private Boolean capacityLimited = false;
  private Double igvOpening = 0.0;
  private Boolean jtBypassEnabled = false;
  private Double jtBypassFlow = 0.0;
  private Double jtBypassFraction = 0.0;
  private Double speedRatioUC0 = 0.0;
  private Double speedUtilisation = 0.0;
  private String speedStatus = "ok";
  private Double shaftClosureFactor = 1.0;
  private Double bearingLoss = 0.0;
  private Double shaftPowerResidual = 0.0;
  private Double expanderWheelOutletTemperature = 0.0;

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
