package neqsim.fluidmechanics.flowsolver;

import java.io.Serializable;

/**
 * Physical axial-dispersion model for conservative one-phase component transport.
 *
 * <p>
 * Implementations return a physical coefficient in m2/s. This API is deliberately separate from
 * {@link SpeciesAdvectionScheme}: numerical spreading from an advection discretization must not be fitted or reported
 * as physical dispersion.
 * </p>
 */
public interface AxialDispersionModel extends Serializable {
  /**
   * Get the physical axial-dispersion coefficient for a cell.
   *
   * @param cellIndex zero-based physical-cell index
   * @param cellLengthM physical cell length in m
   * @param cellMassKg reference gas mass in the cell in kg
   * @param massFlowKgPerSecond reference positive mass flow in kg/s
   * @return finite non-negative physical coefficient in m2/s
   */
  double getCoefficientM2PerSecond(int cellIndex, double cellLengthM, double cellMassKg, double massFlowKgPerSecond);

  /** @return stable human-readable model name for diagnostics */
  String getName();

  /** @return true when the model can contribute a non-zero physical dispersive flux */
  boolean isEnabled();
}
