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
   * Returns the model's explicit applicability and validation-evidence manifest.
   *
   * <p>
   * Custom models that do not override this method fail closed to an unqualified, undeclared manifest. Software tests
   * and analytical checks do not imply engineering qualification.
   *
   * @return immutable evidence manifest
   */
  default ReleaseModelEvidence getEvidence() {
    return ReleaseModelEvidence.undeclared(getModelId(), getModelVersion());
  }

  /**
   * Calculates one instantaneous boundary source term.
   *
   * @param request immutable upstream state and opening definition
   * @return immutable result; inspect status before using physical quantities
   */
  ReleaseFlowResult calculate(ReleaseFlowRequest request);
}
