package neqsim.process.engineering.calculation;

import java.io.Serializable;
import neqsim.process.mechanicaldesign.designstandards.StandardApplicability;
import neqsim.process.mechanicaldesign.designstandards.StandardEdition;
import neqsim.process.mechanicaldesign.designstandards.StandardSupportLevel;
import neqsim.process.mechanicaldesign.designstandards.StandardType;

/**
 * Pure, typed adapter contract for an equipment design calculation associated with a standard.
 *
 * <p>
 * Implementations must not mutate the supplied input or a {@code ProcessSystem}. Legacy mutable
 * calculators are connected through adapters that copy their configuration before execution.
 * </p>
 *
 * @param <I> immutable or defensively copied calculation input
 * @param <O> typed calculation output
 */
public interface EquipmentDesignKernel<I, O> extends EngineeringCalculationModule<I, O>, Serializable {
  /** @return catalogued standard implemented by this kernel */
  StandardType standard();

  /** @return audited implementation maturity */
  StandardSupportLevel maturity();

  /**
   * Check whether this kernel implements the requested edition and amendment basis.
   *
   * @param edition explicit standard edition
   * @return {@code true} when the kernel can execute that edition basis
   */
  boolean supports(StandardEdition edition);

  /**
   * Assess whether the kernel's standard applies to the supplied equipment input.
   *
   * @param input calculation input
   * @return structured applicability decision
   */
  StandardApplicability applicability(I input);
}
