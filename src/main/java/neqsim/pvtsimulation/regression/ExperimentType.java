package neqsim.pvtsimulation.regression;

/**
 * PVT experiment identifiers, including reserved protocols without regression implementations. Evaluator and
 * data-ingestion support currently cover CCE, CVD, DLE, separator and viscosity targets.
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
   * Reserved saturation-pressure target; regression data ingestion and evaluation are not implemented.
   */
  SATURATION_PRESSURE,

  /**
   * Reserved swelling target; regression data ingestion and evaluation are not implemented.
   */
  SWELLING
}
