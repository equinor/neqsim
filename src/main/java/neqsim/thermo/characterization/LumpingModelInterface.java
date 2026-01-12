package neqsim.thermo.characterization;

/**
 * <p>
 * LumpingModelInterface interface.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public interface LumpingModelInterface {
  /**
   * <p>
   * setNumberOfLumpedComponents.
   * </p>
   *
   * @param numb a int
   */
  public void setNumberOfLumpedComponents(int numb);

  /**
   * <p>
   * getName.
   * </p>
   *
   * @return a {@link java.lang.String} object
   */
  public String getName();

  /**
   * <p>
   * generateLumpedComposition.
   * </p>
   *
   * @param charac a {@link neqsim.thermo.characterization.Characterise} object
   */
  public void generateLumpedComposition(Characterise charac);

  /**
   * <p>
   * getNumberOfLumpedComponents.
   * </p>
   *
   * @return a int
   */
  public int getNumberOfLumpedComponents();

  /**
   * <p>
   * getFractionOfHeavyEnd.
   * </p>
   *
   * @param i a int
   * @return a double
   */
  public double getFractionOfHeavyEnd(int i);

  /**
   * <p>
   * setNumberOfPseudoComponents.
   * </p>
   *
   * @param lumpedNumb a int
   */
  public void setNumberOfPseudoComponents(int lumpedNumb);

  /**
   * <p>
   * getNumberOfPseudoComponents.
   * </p>
   *
   * @return a int
   */
  public int getNumberOfPseudoComponents();

  /**
   * <p>
   * getLumpedComponentName.
   * </p>
   *
   * @param i a int
   * @return a {@link java.lang.String} object
   */
  public String getLumpedComponentName(int i);

  /**
   * <p>
   * getLumpedComponentNames.
   * </p>
   *
   * @return an array of {@link java.lang.String} objects
   */
  public String[] getLumpedComponentNames();

  /**
   * Set custom carbon number boundaries for lumping.
   *
   * <p>
   * This allows matching specific PVT lab report groupings. For example, boundaries [6, 10, 15, 20]
   * would create groups: C6-C9, C10-C14, C15-C19, C20+
   * </p>
   *
   * @param boundaries array of starting carbon numbers for each group
   */
  default void setCustomBoundaries(int[] boundaries) {
    // Default implementation does nothing - override in specific models
  }

  /**
   * Get the custom carbon number boundaries if set.
   *
   * @return array of carbon number boundaries, or null if using automatic calculation
   */
  default int[] getCustomBoundaries() {
    return null;
  }

  /**
   * Check if custom boundaries are being used.
   *
   * @return true if custom boundaries were set
   */
  default boolean hasCustomBoundaries() {
    return getCustomBoundaries() != null;
  }
}
