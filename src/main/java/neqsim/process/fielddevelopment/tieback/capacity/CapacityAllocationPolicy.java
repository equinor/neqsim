package neqsim.process.fielddevelopment.tieback.capacity;

/**
 * Allocation policy used when host capacity is insufficient for base and satellite production.
 *
 * @author ESOL
 * @version 1.0
 */
public enum CapacityAllocationPolicy {
  /** Preserve existing host production first and allocate remaining capacity to the satellite. */
  BASE_FIRST,

  /** Allocate host capacity to the satellite first, then hold back base production if required. */
  SATELLITE_FIRST,

  /** Scale base and satellite production by the same feasible capacity factor. */
  PRO_RATA,

  /** Allocate capacity first to the stream with the highest period value. */
  VALUE_WEIGHTED
}
