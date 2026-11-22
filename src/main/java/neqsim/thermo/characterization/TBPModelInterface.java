package neqsim.thermo.characterization;

import neqsim.thermo.system.SystemInterface;

/**
 * TBPModelInterface interface.
 *
 * @author ESOL
 * @version $Id: $Id
 */
public interface TBPModelInterface {
  /**
   * calcTC.
   *
   * @param molarMass a double
   * @param density a double
   * @return a double
   */
  public double calcTC(double molarMass, double density);

  /**
   * calcPC.
   *
   * @param molarMass a double
   * @param density a double
   * @return a double
   */
  public double calcPC(double molarMass, double density);

  /**
   * calcm.
   *
   * @param molarMass a double
   * @param density a double
   * @return a double
   */
  public double calcm(double molarMass, double density);

  /**
   * calcTB.
   *
   * @param molarMass a double
   * @param density a double
   * @return a double
   */
  public double calcTB(double molarMass, double density);

  /**
   * calcAcentricFactorKeslerLee.
   *
   * @param molarMass a double
   * @param density a double
   * @return a double
   */
  public double calcAcentricFactorKeslerLee(double molarMass, double density);

  /**
   * calcAcentricFactor.
   *
   * @param molarMass a double
   * @param density a double
   * @return a double
   */
  public double calcAcentricFactor(double molarMass, double density);

  /**
   * calcRacketZ.
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   * @param molarMass a double
   * @param density a double
   * @return a double
   */
  public double calcRacketZ(SystemInterface thermoSystem, double molarMass, double density);

  /**
   * calcCriticalVolume.
   *
   * @param molarMass a double
   * @param density a double
   * @return a double
   */
  public double calcCriticalVolume(double molarMass, double density);

  /**
   * calcParachorParameter.
   *
   * @param molarMass a double
   * @param density a double
   * @return a double
   */
  public double calcParachorParameter(double molarMass, double density);

  /**
   * calcCriticalViscosity.
   *
   * @param molarMass a double
   * @param density a double
   * @return a double
   */
  public double calcCriticalViscosity(double molarMass, double density);

  /**
   * isCalcm.
   *
   * @return a boolean
   */
  public boolean isCalcm();

  /**
   * calcWatsonCharacterizationFactor.
   *
   * @param molarMass a double
   * @param density a double
   * @return a double
   */
  public double calcWatsonCharacterizationFactor(double molarMass, double density);

  /**
   * getName.
   *
   * @return a {@link java.lang.String} object
   */
  public String getName();

  /**
   * Pin the normal boiling point used by {@link #calcTB(double, double)}.
   *
   * <p>
   * While a positive value is set, <code>calcTB</code> returns it verbatim instead of correlating it from molar mass
   * and density. Callers must reset it to 0.0 afterwards, otherwise the value leaks into every subsequent fraction
   * characterized with the same model instance.
   * </p>
   *
   * @param boilingPoint normal boiling point in K, or 0.0 to use the model correlation
   */
  public void setBoilingPoint(double boilingPoint);

  /**
   * Get the pinned normal boiling point.
   *
   * @return normal boiling point in K, or 0.0 when the model correlation is in use
   */
  public double getBoilingPoint();
}
