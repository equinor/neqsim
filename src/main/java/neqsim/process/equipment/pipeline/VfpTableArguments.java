package neqsim.process.equipment.pipeline;

import java.io.Serializable;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * Explicit, serializable surface-volume definition for a VFP table. Implementations must not mutate the inlet stream
 * and must match the table provider's separation route and reference conditions.
 *
 * @author ESOL
 * @version 1.0
 */
public interface VfpTableArguments extends Serializable {
  /**
   * Calculates surface-volume arguments for the total stream before parallel-line splitting.
   *
   * @param stream inlet stream, with its flow in mol/s internally
   * @return exactly {gas rate [Sm3/day], water/gas [Sm3/Sm3], gas/oil [Sm3/Sm3]}; positive infinity denotes GOR when no
   * oil exists
   */
  double[] calculate(StreamInterface stream);
}
