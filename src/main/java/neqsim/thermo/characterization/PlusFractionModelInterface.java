package neqsim.thermo.characterization;

/**
 * <p>
 * PlusFractionModelInterface interface.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public interface PlusFractionModelInterface extends java.io.Serializable {
  /**
   * <p>
   * hasPlusFraction.
   * </p>
   *
   * @return a boolean
   */
  public boolean hasPlusFraction();

  /**
   * <p>
   * characterizePlusFraction.
   * </p>
   *
   * @param model a {@link neqsim.thermo.characterization.TBPModelInterface} object
   */
  public boolean characterizePlusFraction(TBPModelInterface model);

  /**
   * <p>
   * getFirstTBPFractionNumber.
   * </p>
   *
   * @return a int
   */
  public int getFirstTBPFractionNumber();

  /**
   * <p>
   * getFirstPlusFractionNumber.
   * </p>
   *
   * @return a int
   */
  public int getFirstPlusFractionNumber();

  /**
   * <p>
   * getMaxPlusMolarMass.
   * </p>
   *
   * @return a double
   */
  public double getMaxPlusMolarMass();

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
   * getLastPlusFractionNumber.
   * </p>
   *
   * @return a int
   */
  public int getLastPlusFractionNumber();

  /**
   * <p>
   * setLastPlusFractionNumber.
   * </p>
   *
   * @param fract a int
   */
  public void setLastPlusFractionNumber(int fract);

  /**
   * <p>
   * getPlusComponentNumber.
   * </p>
   *
   * @return a int
   */
  public int getPlusComponentNumber();

  /**
   * <p>
   * getNumberOfPlusPseudocomponents.
   * </p>
   *
   * @return a double
   */
  public double getNumberOfPlusPseudocomponents();

  /**
   * <p>
   * getMPlus.
   * </p>
   *
   * @return a double
   */
  public double getMPlus();

  /**
   * <p>
   * getZPlus.
   * </p>
   *
   * @return a double
   */
  public double getZPlus();

  /**
   * <p>
   * getDensPlus.
   * </p>
   *
   * @return a double
   */
  public double getDensPlus();

  /**
   * <p>
   * getZ.
   * </p>
   *
   * @return an array of type double
   */
  public double[] getZ();

  /**
   * <p>
   * getM.
   * </p>
   *
   * @return an array of type double
   */
  public double[] getM();

  /**
   * <p>
   * getDens.
   * </p>
   *
   * @return an array of type double
   */
  public double[] getDens();

  /**
   * <p>
   * getCoefs.
   * </p>
   *
   * @return an array of type double
   */
  public double[] getCoefs();

  /**
   * <p>
   * getCoef.
   * </p>
   *
   * @param i a int
   * @return a double
   */
  public double getCoef(int i);
}
