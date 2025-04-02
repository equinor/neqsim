package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseInterface;

public interface ComponentFundamentalEOSInterface {

    /**
   * <p>
   * dFdN.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double dFdN(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure);

  /**
   * <p>
   * dFdNdT.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double dFdNdT(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure);

  /**
   * <p>
   * dFdNdV.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double dFdNdV(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure);

  /**
   * <p>
   * dFdNdN.
   * </p>
   *
   * @param j a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double dFdNdN(int j, PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure);
  

  
  /**
   * 
   * <p>
   * ndAlphaResdN.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * 
   */
  public double ndAlphaResdN(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure);
}

