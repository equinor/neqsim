package neqsim.process.safety.release;

import java.util.Map;

/**
 * Process equipment that owns both a physical release boundary and the inventory feeding it.
 *
 * <p>
 * Implementations advance their inventory during native process transient execution. A {@link SourceTermSession}
 * therefore samples the already committed release result instead of performing a second hypothetical withdrawal.
 */
public interface CoupledReleaseSource {

  /** @return immutable request describing the current release-boundary state and geometry */
  ReleaseFlowRequest getReleaseRequest();

  /** @return model identity associated with the committed source result */
  ReleaseFlowModel getReleaseModel();

  /** @return current committed result at the equipment clock */
  ReleaseFlowResult getReleaseResult();

  /** @return whether the physical release boundary is open */
  boolean isReleaseEnabled();

  /** @return stable release-basis code for frame provenance */
  String getReleaseBasis();

  /** @return immutable implementation-specific SI accounting and numerical provenance */
  Map<String, String> getReleaseProvenance();
}
