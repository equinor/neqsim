/*
 * EosMixingRulesInterface.java
 *
 * Created on 4. juni 2000, 12:38
 */
package neqsim.thermo.mixingRule;

import neqsim.thermo.phase.PhaseInterface;

/**
 *
 * @author Even Solbraa
 * @version
 */
public interface EosMixingRulesInterface extends Cloneable {
    double calcA(PhaseInterface phase, double temperature, double pressure, int numbcomp);

    double calcB(PhaseInterface phase, double temperature, double pressure, int numbcomp);

    double calcAi(int compnumb, PhaseInterface phase, double temperature, double pressure,
            int numbcomp);

    double calcBi(int compnumb, PhaseInterface phase, double temperature, double pressure,
            int numbcomp);

    double calcBij(int compnumb, int j, PhaseInterface phase, double temperature, double pressure,
            int numbcomp);

    double calcAij(int compnumb, int j, PhaseInterface phase, double temperature, double pressure,
            int numbcomp);

    public void setBinaryInteractionParameterji(int i, int j, double value);

    public double calcAT(PhaseInterface phase, double temperature, double pressure, int numbcomp);

    public double calcATT(PhaseInterface phase, double temperature, double pressure, int numbcomp);

    public double calcAiT(int compNumb, PhaseInterface phase, double temperature, double pressure,
            int numbcomp);

    public void setBinaryInteractionParameter(int i, int j, double value);

    public double getBinaryInteractionParameter(int i, int j);

    public void setBinaryInteractionParameterT1(int i, int j, double value);

    public double getBinaryInteractionParameterT1(int i, int j);

    public void setCalcEOSInteractionParameters(boolean CalcEOSInteractionParameters);

    public void setnEOSkij(double n);

    public java.lang.String getMixingRuleName();

    public void setMixingRuleGEModel(java.lang.String GEmodel);

    public void setBinaryInteractionParameterij(int i, int j, double value);

    public int getBmixType();

    public void setBmixType(int bmixType2);

    public PhaseInterface getGEPhase();

    // double calcA2(PhaseInterface phase, double temperature, double pressure, int
    // numbcomp);
    // double calcB2(PhaseInterface phase, double temperature, double pressure, int
    // numbcomp);
    // public double calcA(ComponentInterface[] te, double temperature, double
    // pressure, int numberOfComponents);
    // public double calcB(ComponentInterface[] te, double temperature, double
    // pressure, int numberOfComponents);
}
