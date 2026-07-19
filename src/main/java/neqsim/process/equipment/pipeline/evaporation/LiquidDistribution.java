package neqsim.process.equipment.pipeline.evaporation;

/** Geometry used to represent injected liquid in a gas pipeline. */
public enum LiquidDistribution {
  /** Spherical droplets represented by a Sauter mean diameter. */
  DROPLETS,

  /** A wetted wall film represented by its thickness and wetted perimeter. */
  WALL_FILM
}
