package neqsim.process.safety.release;

import java.io.Serializable;

/**
 * Model-explicit instantaneous release calculation. Implementations must leave requests unchanged, return diagnostic
 * failures instead of substituted properties, and declare their assumptions.
 */
public interface ReleaseFlowModel extends Serializable {
  /** @return stable model identifier */
  String getModelId();

  /** @return semantic version of the equations and numerical algorithm */
  default String getModelVersion() {
    return "1.0.0";
  }

  /**
   * Calculates one instantaneous boundary source term.
   *
   * @param request immutable upstream state and opening definition
   * @return immutable result; inspect status before using physical quantities
   */
  ReleaseFlowResult calculate(ReleaseFlowRequest request);
}
