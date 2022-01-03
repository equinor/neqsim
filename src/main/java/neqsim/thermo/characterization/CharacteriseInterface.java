/*
 * Characterize.java
 *
 * Created on 17. juli 2003, 12:04
 */
package neqsim.thermo.characterization;

/**
 * <p>CharacteriseInterface interface.</p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public interface CharacteriseInterface {

    /** Constant <code>PVTsimMolarMass</code> */
    public double[] PVTsimMolarMass = { 80.0, 96.0, 107, 121, 134, 147, 161, 175, 190, 206, 222, 237, 251, 263, 275,
            291, 305, 318, 331, 345, 359, 374, 388, 402, 416, 430, 444, 458, 472, 486, 500, 514, 528, 542, 556, 570,
            584, 598, 612, 626, 640, 654, 668, 682, 696, 710, 724, 738, 752, 766, 780, 794, 808, 822, 836, 850, 864,
            878, 892, 906, 920, 934, 948, 962, 976, 990, 1004, 1018, 1032, 1046, 1060, 1074, 1088, 1102, 1116 };

    /**
     * <p>solve.</p>
     */
    public void solve();

    /**
     * <p>generatePlusFractions.</p>
     *
     * @param start a int
     * @param end a int
     * @param zplus a double
     * @param Mplus a double
     */
    public void generatePlusFractions(int start, int end, double zplus, double Mplus);

    /**
     * <p>generateTBPFractions.</p>
     */
    public void generateTBPFractions();

    /**
     * <p>groupTBPfractions.</p>
     *
     * @return a boolean
     */
    public boolean groupTBPfractions();

    /**
     * <p>hasPlusFraction.</p>
     *
     * @return a boolean
     */
    public boolean hasPlusFraction();

    /**
     * <p>isPseudocomponents.</p>
     *
     * @return a boolean
     */
    public boolean isPseudocomponents();

    /**
     * <p>setPseudocomponents.</p>
     *
     * @param pseudocomponents a boolean
     */
    public void setPseudocomponents(boolean pseudocomponents);

    /**
     * <p>setNumberOfPseudocomponents.</p>
     *
     * @param numberOfPseudocomponents a int
     */
    public void setNumberOfPseudocomponents(int numberOfPseudocomponents);

    /**
     * <p>addCharacterizedPlusFraction.</p>
     */
    public void addCharacterizedPlusFraction();

    /**
     * <p>removeTBPfraction.</p>
     */
    public void removeTBPfraction();

    /**
     * <p>addHeavyEnd.</p>
     */
    public void addHeavyEnd();

    /**
     * <p>addTBPFractions.</p>
     */
    public void addTBPFractions();

    /**
     * <p>getCoefs.</p>
     *
     * @return an array of {@link double} objects
     */
    public double[] getCoefs();

    /**
     * <p>getCoef.</p>
     *
     * @param i a int
     * @return a double
     */
    public double getCoef(int i);

    /**
     * <p>getFirstPlusFractionNumber.</p>
     *
     * @return a int
     */
    public int getFirstPlusFractionNumber();

    /**
     * <p>getLastPlusFractionNumber.</p>
     *
     * @return a int
     */
    public int getLastPlusFractionNumber();

    /**
     * <p>setZPlus.</p>
     *
     * @param zPlus a double
     */
    public void setZPlus(double zPlus);

    /**
     * <p>getPlusCoefs.</p>
     *
     * @return an array of {@link double} objects
     */
    public double[] getPlusCoefs();

    /**
     * <p>getPlusCoefs.</p>
     *
     * @param i a int
     * @return a double
     */
    public double getPlusCoefs(int i);

    /**
     * <p>setPlusCoefs.</p>
     *
     * @param plusCoefs an array of {@link double} objects
     */
    public void setPlusCoefs(double[] plusCoefs);

    /**
     * <p>getDensPlus.</p>
     *
     * @return a double
     */
    public double getDensPlus();

    /**
     * <p>getZPlus.</p>
     *
     * @return a double
     */
    public double getZPlus();

    /**
     * <p>getMPlus.</p>
     *
     * @return a double
     */
    public double getMPlus();

    /**
     * <p>setMPlus.</p>
     *
     * @param MPlus a double
     */
    public void setMPlus(double MPlus);

    /**
     * <p>getDensLastTBP.</p>
     *
     * @return a double
     */
    public double getDensLastTBP();

    /**
     * <p>setCoefs.</p>
     *
     * @param coefs an array of {@link double} objects
     */
    public void setCoefs(double[] coefs);

    /**
     * <p>setCoefs.</p>
     *
     * @param coef a double
     * @param i a int
     */
    public void setCoefs(double coef, int i);

    /**
     * <p>setDensLastTBP.</p>
     *
     * @param densLastTBP a double
     */
    public void setDensLastTBP(double densLastTBP);
}
