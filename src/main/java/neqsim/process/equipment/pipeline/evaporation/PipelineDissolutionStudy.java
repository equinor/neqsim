package neqsim.process.equipment.pipeline.evaporation;

import neqsim.thermo.system.SystemInterface;

/**
 * Estimates the distance required for injected gas bubbles to dissolve into oil or water.
 *
 * <p>
 * Phase 0 is the tracked injected gas and phase 1 is the continuous oil, liquid, or aqueous phase. The calculation uses
 * the same coupled Maxwell-Stefan and heat-transfer boundary as {@link PipelineEvaporationStudy}, with bubble-flow
 * transport coefficients and an evolving Sauter mean bubble diameter.
 * </p>
 */
public class PipelineDissolutionStudy extends PipelineEvaporationStudy {
  /**
   * Constructor.
   *
   * @param inletSystem explicit two-phase inlet system, injected gas in phase 0 and liquid in phase 1
   * @param config geometry and numerical settings; initial bubble diameter is configured here
   */
  public PipelineDissolutionStudy(SystemInterface inletSystem, PipelineEvaporationConfig config) {
    super(inletSystem, config, true);
  }

  /** {@inheritDoc} */
  @Override
  public PipelineDissolutionResult run() {
    return new PipelineDissolutionResult(super.run());
  }
}
