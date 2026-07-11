package neqsim.process.equipment.compressor;

import java.io.Serializable;

/**
 * A source of solid deposit onto a compressor, computed from process thermodynamics.
 *
 * <p>
 * Implementations turn a physical precipitation calculation (elemental sulfur drop-out, salt from evaporating entrained
 * water, wax/condensate drop-out, mineral scale) into a mass-deposition <em>rate</em> that can be accumulated into a
 * {@link CompressorDeposit} over an operating interval. This is the bridge between flow-assurance / thermodynamic
 * precipitation and compressor performance degradation, so the deposit amount is obtained <em>as part of the process
 * calculation</em> rather than supplied by hand.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public interface DepositSource extends Serializable {

  /**
   * The deposit mechanism this source contributes (used to pick the deposit density).
   *
   * @return deposit mechanism
   */
  DepositMechanism getMechanism();

  /**
   * The current mass-deposition rate onto the machine (precipitated solids that stick to the impeller), evaluated from
   * the underlying stream at its current conditions.
   *
   * @param flowUnit mass-flow unit, for example "kg/hr" or "kg/day"
   * @return deposition rate in the requested unit (0 if nothing precipitates or on error)
   */
  double getDepositRate(String flowUnit);
}
