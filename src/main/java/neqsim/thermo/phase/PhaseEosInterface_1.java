/*
 * PhaseEosInterface.java
 *
 * Created on 5. juni 2000, 19:20
 */

package neqsim.thermo.phase;

import neqsim.thermo.mixingRule.EosMixingRulesInterface;

/**
 * <p>
 * PhaseEosInterface_1 interface.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface PhaseEosInterface_1 extends PhaseInterface {
    /** {@inheritDoc} */
    @Override
    double getMolarVolume();

    /**
     * <p>
     * getMixingRule.
     * </p>
     *
     * @return a {@link neqsim.thermo.mixingRule.EosMixingRulesInterface} object
     */
    public EosMixingRulesInterface getMixingRule();

    /**
     * <p>
     * getMixingRuleName.
     * </p>
     *
     * @return a {@link java.lang.String} object
     */
    public java.lang.String getMixingRuleName();

    /**
     * <p>
     * calcPressure.
     * </p>
     *
     * @return a double
     */
    public double calcPressure();

    /**
     * <p>
     * getPressureRepulsive.
     * </p>
     *
     * @return a double
     */
    public double getPressureRepulsive();

    /**
     * <p>
     * getPressureAtractive.
     * </p>
     *
     * @return a double
     */
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
