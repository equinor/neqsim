/*
 * PhaseEosInterface.java
 *
 * Created on 5. juni 2000, 19:20
 */

package neqsim.thermo.phase;

import neqsim.thermo.mixingRule.EosMixingRulesInterface;

/**
 *
 * @author Even Solbraa
 * @version
 */
public interface PhaseEosInterface_1 extends PhaseInterface {

    double getMolarVolume();

    public EosMixingRulesInterface getMixingRule();

    public java.lang.String getMixingRuleName();

    public double calcPressure();

    public double getPressureRepulsive();

    public double getPressureAtractive();
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

}
