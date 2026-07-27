package neqsim.process.equipment.filter;

/**
 * Hydraulic models available for calculating filter differential pressure.
 *
 * @author esol
 * @version 1.0
 */
public enum FilterPressureDropModel {
  /** Preserve the configured clean differential pressure independently of flow. */
  FIXED,

  /** Scale a reference differential pressure with actual volumetric flow. */
  FLOW_SCALED,

  /** Interpolate a user-supplied differential-pressure versus flow test curve. */
  TABULATED,

  /** Calculate pressure drop through granular media with the Ergun equation. */
  ERGUN
}
