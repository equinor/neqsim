/*
 * PhysicalPropertiesInterface.java
 *
 * Created on 29. oktober 2000, 16:14
 */
package neqsim.physicalProperties.physicalPropertySystem;

import neqsim.thermo.phase.PhaseInterface;

/**
 * <p>
 * PhysicalPropertiesInterface interface.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface PhysicalPropertiesInterface extends Cloneable {
    /**
     * <p>
     * getPureComponentViscosity.
     * </p>
     *
     * @param i a int
     * @return a double
     */
    public double getPureComponentViscosity(int i);

    /**
     * <p>
     * setMixingRule.
     * </p>
     *
     * @param mixingRule a
     *        {@link neqsim.physicalProperties.mixingRule.PhysicalPropertyMixingRuleInterface}
     *        object
     */
    public void setMixingRule(
            neqsim.physicalProperties.mixingRule.PhysicalPropertyMixingRuleInterface mixingRule);

    /**
     * <p>
     * getMixingRule.
     * </p>
     *
     * @return a {@link neqsim.physicalProperties.mixingRule.PhysicalPropertyMixingRuleInterface}
     *         object
     */
    public neqsim.physicalProperties.mixingRule.PhysicalPropertyMixingRuleInterface getMixingRule();

    /**
     * <p>
     * getViscosity.
     * </p>
     *
     * @return a double
     */
    public double getViscosity();

    /**
     * <p>
     * setMixingRuleNull.
     * </p>
     */
    public void setMixingRuleNull();

    /**
     * <p>
     * getViscosityOfWaxyOil.
     * </p>
     *
     * @param waxVolumeFraction a double
     * @param shareRate a double
     * @return a double
     */
    public double getViscosityOfWaxyOil(double waxVolumeFraction, double shareRate);

    /**
     * <p>
     * getDiffusionCoefficient.
     * </p>
     *
     * @param comp1 a {@link java.lang.String} object
     * @param comp2 a {@link java.lang.String} object
     * @return a double
     */
    public double getDiffusionCoefficient(String comp1, String comp2);

    /**
     * <p>
     * getConductivity.
     * </p>
     *
     * @return a double
     */
    public double getConductivity();

    /**
     * <p>
     * getKinematicViscosity.
     * </p>
     *
     * @return a double
     */
    public double getKinematicViscosity();

    /**
     * <p>
     * getEffectiveDiffusionCoefficient.
     * </p>
     *
     * @param compName a {@link java.lang.String} object
     * @return a double
     */
    public double getEffectiveDiffusionCoefficient(String compName);

    /**
     * <p>
     * setViscosityModel.
     * </p>
     *
     * @param model a {@link java.lang.String} object
     */
    public void setViscosityModel(String model);

    /**
     * <p>
     * setConductivityModel.
     * </p>
     *
     * @param model a {@link java.lang.String} object
     */
    public void setConductivityModel(String model);

    /**
     * <p>
     * getDensity.
     * </p>
     *
     * @return a double
     */
    public double getDensity();

    /**
     * <p>
     * getPhase.
     * </p>
     *
     * @return a {@link neqsim.thermo.phase.PhaseInterface} object
     */
    public PhaseInterface getPhase();

    /**
     * <p>
     * setPhase.
     * </p>
     *
     * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
     */
    public void setPhase(PhaseInterface phase);

    /**
     * <p>
     * calcDensity.
     * </p>
     *
     * @return a double
     */
    public double calcDensity();

    /**
     * <p>
     * getEffectiveSchmidtNumber.
     * </p>
     *
     * @param i a int
     * @return a double
     */
    public double getEffectiveSchmidtNumber(int i);

    /**
     * <p>
     * getDiffusionCoefficient.
     * </p>
     *
     * @param i a int
     * @param j a int
     * @return a double
     */
    public double getDiffusionCoefficient(int i, int j);

    /**
     * <p>
     * getEffectiveDiffusionCoefficient.
     * </p>
     *
     * @param i a int
     * @return a double
     */
    public double getEffectiveDiffusionCoefficient(int i);

    /**
     * <p>
     * calcEffectiveDiffusionCoefficients.
     * </p>
     */
    public void calcEffectiveDiffusionCoefficients();

    /**
     * <p>
     * getFickDiffusionCoefficient.
     * </p>
     *
     * @param i a int
     * @param j a int
     * @return a double
     */
    public double getFickDiffusionCoefficient(int i, int j);

    /**
     * <p>
     * init.
     * </p>
     *
     * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
     */
    public void init(PhaseInterface phase);

    /**
     * <p>
     * init.
     * </p>
     *
     * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
     * @param type a {@link java.lang.String} object
     */
    public void init(PhaseInterface phase, String type);

    /**
     * <p>
     * clone.
     * </p>
     *
     * @return a
     *         {@link neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface}
     *         object
     */
    public PhysicalPropertiesInterface clone();

    /**
     * <p>
     * setBinaryDiffusionCoefficientMethod.
     * </p>
     *
     * @param i a int
     */
    public void setBinaryDiffusionCoefficientMethod(int i);

    /**
     * <p>
     * setMulticomponentDiffusionMethod.
     * </p>
     *
     * @param i a int
     */
    public void setMulticomponentDiffusionMethod(int i);

    /**
     * <p>
     * getViscosityModel.
     * </p>
     *
     * @return a
     *         {@link neqsim.physicalProperties.physicalPropertyMethods.methodInterface.ViscosityInterface}
     *         object
     */
    public neqsim.physicalProperties.physicalPropertyMethods.methodInterface.ViscosityInterface getViscosityModel();

    /**
     * <p>
     * getConductivityModel.
     * </p>
     *
     * @return a
     *         {@link neqsim.physicalProperties.physicalPropertyMethods.methodInterface.ConductivityInterface}
     *         object
     */
    public neqsim.physicalProperties.physicalPropertyMethods.methodInterface.ConductivityInterface getConductivityModel();
}
