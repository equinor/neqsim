package neqsim.process.safety.vibration;

/**
 * Energy Institute AVIFF flow-induced vibration Likelihood Of Failure (LOF) bands.
 *
 * <p>
 * Per the Energy Institute "Guidelines for the Avoidance of Vibration Induced Fatigue Failure in Process Pipework", 2nd
 * Edition (AVIFF-2), a piping circuit is scored against a normalised energy index and assigned a LOF band that dictates
 * the level of further assessment.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public enum PipingFivLikelihood {

  /** LOF &lt; 0.3 - acceptable, no further assessment. */
  LOW,

  /** 0.3 &le; LOF &lt; 0.5 - basic mitigation (clamping, hold-down). */
  MEDIUM,

  /** 0.5 &le; LOF &lt; 1.0 - detailed dynamic assessment required. */
  HIGH,

  /** LOF &ge; 1.0 - redesign required. */
  VERY_HIGH
}
