package neqsim.process.mechanicaldesign.designstandards;

/**
 * Evidence level for a standard listed by the NeqSim mechanical-design framework.
 *
 * <p>
 * The level describes NeqSim's implementation evidence. It does not describe the authority of the published standard
 * and must not be interpreted as design certification.
 * </p>
 */
public enum StandardSupportLevel {
  /** Metadata and equipment applicability are catalogued, but no standard calculation is exposed. */
  CATALOGUED,

  /** A preliminary engineering calculation is available with stated limitations. */
  SCREENING,

  /** The stated calculation range has independent numerical validation. */
  VALIDATED,

  /** A controlled implementation and evidence package has been released for a stated use. */
  QUALIFIED
}
