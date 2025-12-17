package neqsim.fluidmechanics.flownode;

/**
 * Enumeration of flow pattern prediction models for two-phase pipe flow.
 *
 * <p>
 * These models predict flow pattern transitions based on flow conditions and fluid properties.
 * </p>
 *
 * @author ASMF
 * @version 1.0
 */
public enum FlowPatternModel {
  /**
   * No automatic flow pattern detection - use user-specified pattern.
   */
  MANUAL,

  /**
   * Baker chart - empirical flow pattern map based on superficial mass fluxes. Applicable to
   * horizontal pipes. Reference: Baker, O. (1954).
   */
  BAKER_CHART,

  /**
   * Taitel-Dukler model - mechanistic model based on physical transition mechanisms. Applicable to
   * horizontal and slightly inclined pipes. Reference: Taitel, Y., &amp; Dukler, A.E. (1976).
   */
  TAITEL_DUKLER,

  /**
   * Barnea model - extended Taitel-Dukler model for all pipe inclinations. Reference: Barnea, D.
   * (1987).
   */
  BARNEA,

  /**
   * Beggs-Brill model - empirical correlation for all pipe inclinations. Reference: Beggs, H.D.,
   * &amp; Brill, J.P. (1973).
   */
  BEGGS_BRILL
}
