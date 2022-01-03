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

}
