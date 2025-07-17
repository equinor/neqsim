package neqsim.thermo.characterization;

import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * TBPModelInterface interface.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public interface TBPModelInterface {
  /**
   * <p>
   * calcTC.
   * </p>
   *
   * @param molarMass a double
   * @param density a double
   * @return a double
   */
  public double calcTC(double molarMass, double density);

  /**
   * <p>
   * calcPC.
   * </p>
   *
   * @param molarMass a double
   * @param density a double
   * @return a double
   */
  public double calcPC(double molarMass, double density);

  /**
   * <p>
   * calcm.
   * </p>
   *
   * @param molarMass a double
   * @param density a double
   * @return a double
   */
  public double calcm(double molarMass, double density);

  /**
   * <p>
   * calcTB.
   * </p>
   *
   * @param molarMass a double
   * @param density a double
   * @return a double
   */
  public double calcTB(double molarMass, double density);

  /**
   * <p>
   * calcAcentricFactorKeslerLee.
   * </p>
   *
   * @param molarMass a double
   * @param density a double
   * @return a double
   */
  public double calcAcentricFactorKeslerLee(double molarMass, double density);

  /**
   * <p>
   * calcAcentricFactor.
   * </p>
   *
   * @param molarMass a double
   * @param density a double
   * @return a double
   */
  public double calcAcentricFactor(double molarMass, double density);

  /**
   * <p>
   * calcRacketZ.
   * </p>
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   * @param molarMass a double
   * @param density a double
   * @return a double
   */
  public double calcRacketZ(SystemInterface thermoSystem, double molarMass, double density);

  /**
   * <p>
   * calcCriticalVolume.
   * </p>
   *
   * @param molarMass a double
   * @param density a double
   * @return a double
   */
  public double calcCriticalVolume(double molarMass, double density);

  /**
   * <p>
   * calcParachorParameter.
   * </p>
   *
   * @param molarMass a double
   * @param density a double
   * @return a double
   */
  public double calcParachorParameter(double molarMass, double density);

  /**
   * <p>
   * calcCriticalViscosity.
   * </p>
   *
   * @param molarMass a double
   * @param density a double
   * @return a double
   */
  public double calcCriticalViscosity(double molarMass, double density);

  /**
   * <p>
   * isCalcm.
   * </p>
   *
   * @return a boolean
   */
  public boolean isCalcm();

  /**
   * <p>
   * calcWatsonCharacterizationFactor.
   * </p>
   *
   * @param molarMass a double
   * @param density a double
   * @return a double
   */
  public double calcWatsonCharacterizationFactor(double molarMass, double density);

  /**
   * <p>
   * getName.
   * </p>
   *
   * @return a {@link java.lang.String} object
   */
  public String getName();

  public void setBoilingPoint(double boilingPoint);
}
