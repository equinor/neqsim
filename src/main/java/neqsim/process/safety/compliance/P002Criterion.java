package neqsim.process.safety.compliance;

/**
 * NORSOK P-002 ("Process system design") compliance criteria.
 *
 * @author ESOL
 * @version 1.0
 */
public enum P002Criterion {

  /** Flare relief line gas velocity Mach number &le; 0.7 (NORSOK P-002 Section 12.5). */
  FLARE_LINE_MACH_07,

  /** Blowdown line momentum flux ρv² &le; 200 000 kg/(m·s²) (NORSOK P-002 Section 12.6). */
  BLOWDOWN_RHO_V2,

  /** Two-phase erosional velocity ρv² (or Cv API 14E equivalent) within material allowable. */
  EROSIONAL_VELOCITY,

  /** Gas vent / atmospheric vent velocity &le; 60 m/s (NORSOK P-002 Section 12.7). */
  VENT_GAS_VELOCITY,

  /** Liquid carry-over fraction from separator &le; 0.1 vol% (NORSOK P-100 quality). */
  LIQUID_CARRY_OVER,

  /** Depressurisation valve sized for fire case end pressure within design margins. */
  DEPRESSURISATION_VALVE_SIZE,

  /** Closed drain / liquid drain slope and capacity adequate. */
  DRAIN_SLOPE_CAPACITY
}
