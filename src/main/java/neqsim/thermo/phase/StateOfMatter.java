package neqsim.thermo.phase;

/**
 * States of matter, a way of relating the PhaseTypes to classical states of matter.
 *
 * @author ASMF
 */
public enum StateOfMatter {
  GAS, LIQUID, SOLID;

  /**
   * Get StateOfMatter value from Phasetype object.
   *
   * @param pt PhaseType object
   * @return StateOfMatter object
   */
  public static StateOfMatter fromPhaseType(PhaseType pt) {
    switch (pt) {
      case GAS:
        return StateOfMatter.GAS;
      case LIQUID:
      case OIL:
      case AQUEOUS:
      case LIQUID_ASPHALTENE:
        return StateOfMatter.LIQUID;
      case SOLID:
      case SOLIDCOMPLEX:
      case WAX:
      case HYDRATE:
      case ASPHALTENE:
        return StateOfMatter.SOLID;
      default:
        throw new RuntimeException(new neqsim.util.exception.InvalidInputException(
            StateOfMatter.class, "fromPhaseType", "pt", "Conversion not configured for"));
    }
  }

  /**
   * Check if PhaseType object is a gas state of matter.
   *
   * @param pt PhaseType object to check.
   * @return True if pt converts to StateOfMatter.GAS
   */
  public static boolean isGas(PhaseType pt) {
    return StateOfMatter.fromPhaseType(pt) == StateOfMatter.GAS;
  }

  /**
   * Check if PhaseType object is a liquid state of matter.
   *
   * @param pt PhaseType object to check.
   * @return True if pt converts to StateOfMatter.LIQUID
   */
  public static boolean isLiquid(PhaseType pt) {
    return StateOfMatter.fromPhaseType(pt) == StateOfMatter.LIQUID;
  }

  /**
   * Check if PhaseType object is a solid state of matter.
   *
   * @param pt PhaseType object to check.
   * @return True if pt converts to StateOfMatter.SOLID
   */
  public static boolean isSolid(PhaseType pt) {
    return StateOfMatter.fromPhaseType(pt) == StateOfMatter.SOLID;
  }

  /**
   * Check if PhaseType object is an asphaltene phase (either solid or liquid).
   *
   * <p>
   * Returns true for both ASPHALTENE (solid asphaltene precipitate) and LIQUID_ASPHALTENE
   * (asphaltene-rich heavy liquid phase in Pedersen's approach).
   * </p>
   *
   * @param pt PhaseType object to check.
   * @return True if pt is ASPHALTENE or LIQUID_ASPHALTENE
   */
  public static boolean isAsphaltene(PhaseType pt) {
    return pt == PhaseType.ASPHALTENE || pt == PhaseType.LIQUID_ASPHALTENE;
  }
}
