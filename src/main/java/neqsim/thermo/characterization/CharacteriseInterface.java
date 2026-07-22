package neqsim.thermo.characterization;

/**
 * CharacteriseInterface interface.
 *
 * @author ESOL
 * @version $Id: $Id
 */
public interface CharacteriseInterface {
  /** Constant <code>PVTsimMolarMass</code>. */
  public double[] PVTsimMolarMass = { 80.0, 96.0, 107, 121, 134, 147, 161, 175, 190, 206, 222, 237, 251, 263, 275, 291,
      305, 318, 331, 345, 359, 374, 388, 402, 416, 430, 444, 458, 472, 486, 500, 514, 528, 542, 556, 570, 584, 598, 612,
      626, 640, 654, 668, 682, 696, 710, 724, 738, 752, 766, 780, 794, 808, 822, 836, 850, 864, 878, 892, 906, 920, 934,
      948, 962, 976, 990, 1004, 1018, 1032, 1046, 1060, 1074, 1088, 1102, 1116 };

  /**
   * solve.
   */
  public void solve();

  /**
   * generatePlusFractions.
   *
   * @param start a int
   * @param end a int
   * @param zplus a double
   * @param Mplus a double
   */
  public void generatePlusFractions(int start, int end, double zplus, double Mplus);

  /**
   * generateTBPFractions.
   */
  public void generateTBPFractions();

  /**
   * groupTBPfractions.
   *
   * @return a boolean
   */
  public boolean groupTBPfractions();

  /**
   * hasPlusFraction.
   *
   * @return a boolean
   */
  public boolean hasPlusFraction();

  /**
   * isPseudocomponents.
   *
   * @return a boolean
   */
  public boolean isPseudocomponents();

  /**
   * setPseudocomponents.
   *
   * @param pseudocomponents a boolean
   */
  public void setPseudocomponents(boolean pseudocomponents);

  /**
   * setNumberOfPseudocomponents.
   *
   * @param numberOfPseudocomponents a int
   */
  public void setNumberOfPseudocomponents(int numberOfPseudocomponents);

  /**
   * addCharacterizedPlusFraction.
   */
  public void addCharacterizedPlusFraction();

  /**
   * removeTBPfraction.
   */
  public void removeTBPfraction();

  /**
   * addHeavyEnd.
   */
  public void addHeavyEnd();

  /**
   * addTBPFractions.
   */
  public void addTBPFractions();

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

  /**
   * getFirstPlusFractionNumber.
   *
   * @return a int
   */
  public int getFirstPlusFractionNumber();

  /**
   * getLastPlusFractionNumber.
   *
   * @return a int
   */
  public int getLastPlusFractionNumber();

  /**
   * setZPlus.
   *
   * @param zPlus a double
   */
  public void setZPlus(double zPlus);

  /**
   * getPlusCoefs.
   *
   * @return an array of type double
   */
  public double[] getPlusCoefs();

  /**
   * getPlusCoefs.
   *
   * @param i a int
   * @return a double
   */
  public double getPlusCoefs(int i);

  /**
   * setPlusCoefs.
   *
   * @param plusCoefs an array of type double
   */
  public void setPlusCoefs(double[] plusCoefs);

  /**
   * getDensPlus.
   *
   * @return a double
   */
  public double getDensPlus();

  /**
   * getZPlus.
   *
   * @return a double
   */
  public double getZPlus();

  /**
   * getMPlus.
   *
   * @return a double
   */
  public double getMPlus();

  /**
   * setMPlus.
   *
   * @param MPlus a double
   */
  public void setMPlus(double MPlus);

  /**
   * getDensLastTBP.
   *
   * @return a double
   */
  public double getDensLastTBP();

  /**
   * setCoefs.
   *
   * @param coefs an array of type double
   */
  public void setCoefs(double[] coefs);

  /**
   * setCoefs.
   *
   * @param coef a double
   * @param i a int
   */
  public void setCoefs(double coef, int i);

  /**
   * setDensLastTBP.
   *
   * @param densLastTBP a double
   */
  public void setDensLastTBP(double densLastTBP);
}
