/*
 * PhysicalPropertiesInterface.java
 *
 * Created on 29. oktober 2000, 16:14
 */
package neqsim.physicalProperties.physicalPropertySystem;

import neqsim.thermo.phase.PhaseInterface;

/**
 *
 * @author Even Solbraa
 * @version
 */
public interface PhysicalPropertiesInterface extends Cloneable {

    public double getPureComponentViscosity(int i);

    public double getViscosity();
    
      public void setMixingRuleNull();

    public double getViscosityOfWaxyOil(double waxVolumeFraction, double shareRate);

    public double getConductivity();

    public double getKinematicViscosity();

    public void setViscosityModel(String model);

    public void setConductivityModel(String model);

    public double getDensity();

    public PhaseInterface getPhase();

    public double calcDensity();

    public double getEffectiveSchmidtNumber(int i);

    public double getDiffusionCoeffisient(int i, int j);

    public double getEffectiveDiffusionCoefficient(int i);

    public void calcEffectiveDiffusionCoefficients();

    public double getFickDiffusionCoeffisient(int i, int j);

    public void init(PhaseInterface phase);

    public void init(PhaseInterface phase, String type);

    public neqsim.physicalProperties.mixingRule.PhysicalPropertyMixingRuleInterface getMixingRule();

    public Object clone();

    public void setBinaryDiffusionCoefficientMethod(int i);

    public void setMulticomponentDiffusionMethod(int i);

    public neqsim.physicalProperties.physicalPropertyMethods.methodInterface.ViscosityInterface getViscosityModel();

    public neqsim.physicalProperties.physicalPropertyMethods.methodInterface.ConductivityInterface getConductivityModel();
}
