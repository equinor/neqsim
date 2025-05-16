package neqsim.process.util.monitor;

import neqsim.process.equipment.expander.TurboExpanderCompressor;

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
    this.UCratiocompressor = turboExpanderCompressor.getUCratiocompressor();
    this.QNratioexpander = turboExpanderCompressor.getQNratioexpander();
    this.QNratiocompressor = turboExpanderCompressor.getQNratiocompressor();
  }

  private Double speed = 0.0;
  private Double powerExpander = 0.0;
  private Double powerCompressor = 0.0;
  private Double compressorPolytropicEfficiency = 0.0;
  private Double expanderIsentropicEfficiency = 0.0;
  private Double compressorPolytropicHead = 0.0;
  private Double UCratioexpander = 0.0;
  private Double UCratiocompressor = 0.0;
  private Double QNratioexpander = 0.0;
  private Double QNratiocompressor = 0.0;

  public Double getSpeed() {
    return speed;
  }

  public Double getPowerExpander() {
    return powerExpander;
  }

  public Double getPowerCompressor() {
    return powerCompressor;
  }

  public Double getCompressorPolytropicEfficiency() {
    return compressorPolytropicEfficiency;
  }

  public Double getExpanderIsentropicEfficiency() {
    return expanderIsentropicEfficiency;
  }

  public Double getCompressorPolytropicHead() {
    return compressorPolytropicHead;
  }

  public Double getUCratioexpander() {
    return UCratioexpander;
  }

  public Double getUCratiocompressor() {
    return UCratiocompressor;
  }

  public Double getQNratioexpander() {
    return QNratioexpander;
  }

  public Double getQNratiocompressor() {
    return QNratiocompressor;
  }
}
