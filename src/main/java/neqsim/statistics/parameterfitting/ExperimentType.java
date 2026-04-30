package neqsim.statistics.parameterfitting;

import java.io.Serializable;

/**
 * Experimental data category used by parameter fitting studies and reports.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public enum ExperimentType implements Serializable {
  /** Generic regression data. */
  GENERIC,
  /** Vapor-liquid equilibrium data. */
  VLE,
  /** Liquid-liquid equilibrium data. */
  LLE,
  /** Vapor-liquid-liquid equilibrium data. */
  VLLE,
  /** Saturation pressure or bubble/dew point data. */
  SATURATION_PRESSURE,
  /** Density measurements. */
  DENSITY,
  /** Viscosity measurements. */
  VISCOSITY,
  /** Heat capacity measurements. */
  HEAT_CAPACITY,
  /** PVT laboratory experiment data. */
  PVT,
  /** Transport-property correlation data. */
  TRANSPORT_PROPERTY;
}
