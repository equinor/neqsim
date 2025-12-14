package neqsim.pvtsimulation.regression;

/**
 * Enumeration of PVT experiment types supported by the regression framework.
 *
 * @author ESOL
 * @version 1.0
 */
public enum ExperimentType {
  /**
   * Constant Composition Expansion.
   */
  CCE,

  /**
   * Constant Volume Depletion.
   */
  CVD,

  /**
   * Differential Liberation Expansion.
   */
  DLE,

  /**
   * Separator Test.
   */
  SEPARATOR,

  /**
   * Viscosity measurements.
   */
  VISCOSITY,

  /**
   * Saturation pressure.
   */
  SATURATION_PRESSURE,

  /**
   * Swelling test.
   */
  SWELLING
}
