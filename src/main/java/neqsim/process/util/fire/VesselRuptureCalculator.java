package neqsim.process.util.fire;

/**
 * Implements a Scandpower-style thin-wall rupture check using von Mises stress.
 *
 * <p>The utility is intended for integrating rupture progression checks into dynamic blowdown and
 * fire scenarios. It provides a deterministic stress estimate and a safety margin relative to the
 * allowable tensile strength of the vessel material.
 */
public final class VesselRuptureCalculator {

  private VesselRuptureCalculator() {}

  /**
   * Calculates the von Mises equivalent stress for a cylindrical thin-walled vessel.
   *
   * <p>Hoop stress = P * r / t, axial stress = P * r / (2 * t). The von Mises stress combines these
   * principal stresses to represent yielding under multiaxial loading.
   *
   * @param internalPressurePa Internal pressure [Pa]
   * @param innerRadiusM Vessel inner radius [m]
   * @param wallThicknessM Wall thickness [m]
   * @return von Mises stress in Pascals
   */
  public static double vonMisesStress(double internalPressurePa, double innerRadiusM,
      double wallThicknessM) {
    if (internalPressurePa < 0.0) {
      throw new IllegalArgumentException("Internal pressure cannot be negative");
    }
    if (innerRadiusM <= 0.0 || wallThicknessM <= 0.0) {
      throw new IllegalArgumentException("Radius and wall thickness must be positive");
    }

    double hoopStress = internalPressurePa * innerRadiusM / wallThicknessM;
    double axialStress = internalPressurePa * innerRadiusM / (2.0 * wallThicknessM);

    return Math.sqrt(Math.pow(hoopStress, 2) + Math.pow(axialStress, 2) - hoopStress * axialStress);
  }

  /**
   * Calculates the safety margin relative to allowable tensile strength.
   *
   * @param vonMisesStressPa Calculated von Mises stress [Pa]
   * @param allowableTensileStrengthPa Allowable tensile strength from material data [Pa]
   * @return Positive value indicates remaining margin before rupture; negative indicates failure
   */
  public static double ruptureMargin(double vonMisesStressPa, double allowableTensileStrengthPa) {
    if (vonMisesStressPa < 0.0 || allowableTensileStrengthPa <= 0.0) {
      throw new IllegalArgumentException("Stress values must be non-negative and strength positive");
    }
    return allowableTensileStrengthPa - vonMisesStressPa;
  }

  /**
   * Indicates whether rupture is expected under current stresses.
   *
   * @param vonMisesStressPa Calculated von Mises stress [Pa]
   * @param allowableTensileStrengthPa Allowable tensile strength [Pa]
   * @return {@code true} if von Mises stress exceeds allowable strength
   */
  public static boolean isRuptureLikely(double vonMisesStressPa,
      double allowableTensileStrengthPa) {
    return vonMisesStressPa >= allowableTensileStrengthPa;
  }
}
