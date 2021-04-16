/*
 * ComponentGEInterface.java
 *
 * Created on 11. juli 2000, 19:58
 */
package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseInterface;

/**
 *
 * @author Even Solbraa
 * @version
 */
public interface ComponentGEInterface extends ComponentInterface {

    public double getGamma(PhaseInterface phase, int numberOfComponents, double temperature, double pressure,
            int phasetype, double[][] HValpha, double[][] HVgij, double[][] intparam, String[][] mixRule);

    public double getGamma();

    public double getlnGamma();

    public double getGammaRefCor();

    public double getlnGammadt();

    public double getlnGammadtdt();

    public double getlnGammadn(int k);

    public void setlnGammadn(int k, double val);
}
