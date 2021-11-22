/*
 * ComponentEosInterface.java
 *
 * Created on 4. juni 2000, 13:35
 */
package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseInterface;

/**
 *
 * @author Even Solbraa
 * @version
 */
public interface ComponentEosInterface extends ComponentInterface {
    double aT(double temperature);

    public double diffaT(double temperature);

    public double diffdiffaT(double temperature);

    public double getb();

    public double getAiT();

    public double geta();

    public double getaDiffT();

    public double getaDiffDiffT();

    public double getaT();

    public double getBij(int j);

    public double getAij(int j);

    public double getBi();

    public double getAi();

    public double calca();

    public double calcb();

    public double getAder();

    public void setAder(double val);

    public double[] getDeltaEosParameters();

    public double getdAdndn(int j);

    public void setdAdndn(int jComp, double val);

    public double getdAdT();

    public void setdAdT(double val);

    public void setdAdTdT(double val);

    public double getBder();

    public void setBder(double val);

    public double getdBdndn(int j);

    public void setdBdndn(int jComp, double val);

    public double getdBdT();

    public void setdBdTdT(double val);

    public double getdBdndT();

    public void setdBdndT(double val);

    public void setdAdTdn(double val);

    public double getdAdTdn();

    public double dFdN(PhaseInterface phase, int numberOfComponents, double temperature,
            double pressure);

    public double dFdNdT(PhaseInterface phase, int numberOfComponents, double temperature,
            double pressure);

    public double dFdNdV(PhaseInterface phase, int numberOfComponents, double temperature,
            double pressure);

    public double dFdNdN(int j, PhaseInterface phase, int numberOfComponents, double temperature,
            double pressure);
}
