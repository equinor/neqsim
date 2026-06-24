/*
 * ComponentGEInterface.java
 *
 * Created on 11. juli 2000, 19:58
 */

package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;

/**
 * ComponentGEInterface interface.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface ComponentGEInterface extends ComponentInterface {
  /**
   * getGamma.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @param pt the PhaseType of the phase
   * @param HValpha an array of type double
   * @param HVgij an array of type double
   * @param intparam an array of type double
   * @param mixRule an array of {@link java.lang.String} objects
   * @return a double
   */
  public double getGamma(PhaseInterface phase, int numberOfComponents, double temperature, double pressure,
      PhaseType pt, double[][] HValpha, double[][] HVgij, double[][] intparam, String[][] mixRule);

  /**
   * getGamma.
   *
   * @return a double
   */
  public double getGamma();

  /**
   * getLnGamma.
   *
   * @return a double
   */
  public double getLnGamma();

  /**
   * getGammaRefCor.
   *
   * @return a double
   */
  public double getGammaRefCor();

  /**
   * getLnGammadt.
   *
   * @return a double
   */
  public double getLnGammadt();

  /**
   * getLnGammadtdt.
   *
   * @return a double
   */
  public double getLnGammadtdt();

  /**
   * getLnGammadn.
   *
   * @param k a int
   * @return a double
   */
  public double getLnGammadn(int k);

  /**
   * setLnGammadn.
   *
   * @param k a int
   * @param val a double
   */
  public void setLnGammadn(int k, double val);
}
