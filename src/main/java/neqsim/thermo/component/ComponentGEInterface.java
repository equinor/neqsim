/*
 * ComponentGEInterface.java
 *
 * Created on 11. juli 2000, 19:58
 */

package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseInterface;

/**
 * <p>
 * ComponentGEInterface interface.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface ComponentGEInterface extends ComponentInterface {
    /**
     * <p>
     * getGamma.
     * </p>
     *
     * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
     * @param numberOfComponents a int
     * @param temperature a double
     * @param pressure a double
     * @param phasetype a int
     * @param HValpha an array of {@link double} objects
     * @param HVgij an array of {@link double} objects
     * @param intparam an array of {@link double} objects
     * @param mixRule an array of {@link java.lang.String} objects
     * @return a double
     */
    public double getGamma(PhaseInterface phase, int numberOfComponents, double temperature,
            double pressure, int phasetype, double[][] HValpha, double[][] HVgij,
            double[][] intparam, String[][] mixRule);

    /**
     * <p>
     * getGamma.
     * </p>
     *
     * @return a double
     */
    public double getGamma();

    /**
     * <p>
     * getlnGamma.
     * </p>
     *
     * @return a double
     */
    public double getlnGamma();

    /**
     * <p>
     * getGammaRefCor.
     * </p>
     *
     * @return a double
     */
    public double getGammaRefCor();

    /**
     * <p>
     * getlnGammadt.
     * </p>
     *
     * @return a double
     */
    public double getlnGammadt();

    /**
     * <p>
     * getlnGammadtdt.
     * </p>
     *
     * @return a double
     */
    public double getlnGammadtdt();

    /**
     * <p>
     * getlnGammadn.
     * </p>
     *
     * @param k a int
     * @return a double
     */
    public double getlnGammadn(int k);

    /**
     * <p>
     * setlnGammadn.
     * </p>
     *
     * @param k a int
     * @param val a double
     */
    public void setlnGammadn(int k, double val);
}
