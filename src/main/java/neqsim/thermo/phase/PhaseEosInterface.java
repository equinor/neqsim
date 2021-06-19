/*
 * PhaseEosInterface.java
 *
 * Created on 5. juni 2000, 19:20
 */
package neqsim.thermo.phase;

import neqsim.thermo.mixingRule.EosMixingRulesInterface;

/**
 * @author  Even Solbraa
 * @version
 */
public interface PhaseEosInterface extends PhaseInterface {

    @Override
    double getMolarVolume();

    public EosMixingRulesInterface getMixingRule();

    public java.lang.String getMixingRuleName();

    public double calcPressure();

    public double calcPressuredV();

    public double getPressureRepulsive();

    public double getPressureAtractive();

    public void displayInteractionCoefficients(String intType);
    // public double getA();
    // public double getB();
    // double calcA(ComponentEosInterface[] compArray, double temperature, double
    // pressure, int numbcomp);
    // double calcB(ComponentEosInterface[] compArray, double temperature, double
    // pressure, int numbcomp);
    // double calcA(ComponentEosInterface[] compArray, double temperature, double
    // pressure, int numbcomp);
    // double calcB(ComponentEosInterface[] compArray, double temperature, double
    // pressure, int numbcomp);

    public double F();

    public double dFdN(int i);

    public double dFdNdN(int i, int j);

    public double dFdNdV(int i);

    public double dFdNdT(int i);

    public double getAresTV();

    public double getSresTV();
}
