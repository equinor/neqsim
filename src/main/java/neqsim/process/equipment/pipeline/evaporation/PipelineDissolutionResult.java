package neqsim.process.equipment.pipeline.evaporation;

/** Result of an axial injected-gas dissolution study. */
public class PipelineDissolutionResult extends PipelineEvaporationResult {
  /** Constructor used by {@link PipelineDissolutionStudy}. */
  PipelineDissolutionResult(PipelineEvaporationResult result) {
    super(result.getProfile(), result.isCompleteTransfer(), result.getCompletionDistance(),
        result.getMaximumComponentMolarBalanceError(), result.getRelativeEnergyBalanceError(), result.getWarnings(),
        result.getOutletSystem(), true);
  }
}
