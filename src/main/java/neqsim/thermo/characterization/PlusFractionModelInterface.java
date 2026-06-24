package neqsim.thermo.characterization;

/**
 * PlusFractionModelInterface interface.
 *
 * @author ESOL
 * @version $Id: $Id
 */
public interface PlusFractionModelInterface extends java.io.Serializable {
  /**
   * hasPlusFraction.
   *
   * @return a boolean
   */
  public boolean hasPlusFraction();

  /**
   * characterizePlusFraction.
   *
   * @param model a {@link neqsim.thermo.characterization.TBPModelInterface} object
   * @return a boolean
   */
  public boolean characterizePlusFraction(TBPModelInterface model);

  /**
   * getFirstTBPFractionNumber.
   *
   * @return a int
   */
  public int getFirstTBPFractionNumber();

  /**
   * getFirstPlusFractionNumber.
   *
   * @return a int
   */
  public int getFirstPlusFractionNumber();

  /**
   * getMaxPlusMolarMass.
   *
   * @return a double
   */
  public double getMaxPlusMolarMass();

  /**
   * getName.
   *
   * @return a {@link java.lang.String} object
   */
  public String getName();

  /**
   * getLastPlusFractionNumber.
   *
   * @return a int
   */
  public int getLastPlusFractionNumber();

  /**
   * setLastPlusFractionNumber.
   *
   * @param fract a int
   */
  public void setLastPlusFractionNumber(int fract);

  /**
   * getPlusComponentNumber.
   *
   * @return a int
   */
  public int getPlusComponentNumber();

  /**
   * getNumberOfPlusPseudocomponents.
   *
   * @return a double
   */
  public double getNumberOfPlusPseudocomponents();

  /**
   * getMPlus.
   *
   * @return a double
   */
  public double getMPlus();

  /**
   * getZPlus.
   *
   * @return a double
   */
  public double getZPlus();

  /**
   * getDensPlus.
   *
   * @return a double
   */
  public double getDensPlus();

  /**
   * getZ.
   *
   * @return an array of type double
   */
  public double[] getZ();

  /**
   * getM.
   *
   * @return an array of type double
   */
  public double[] getM();

  /**
   * getDens.
   *
   * @return an array of type double
   */
  public double[] getDens();

  /**
   * getCoefs.
   *
   * @return an array of type double
   */
  public double[] getCoefs();

  /**
   * getCoef.
   *
   * @param i a int
   * @return a double
   */
  public double getCoef(int i);
}
