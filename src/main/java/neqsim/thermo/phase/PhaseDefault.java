package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentInterface;

/**
 * <p>
 * PhaseDefault class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PhaseDefault extends Phase {
    private static final long serialVersionUID = 1000;

    protected ComponentInterface defComponent = null;

    /**
     * <p>
     * Constructor for PhaseDefault.
     * </p>
     */
    public PhaseDefault() {}

    /**
     * <p>
     * Constructor for PhaseDefault.
     * </p>
     *
     * @param comp a {@link neqsim.thermo.component.ComponentInterface} object
     */
    public PhaseDefault(ComponentInterface comp) {
        super();
        defComponent = comp;
    }

    /**
     * <p>
     * setComponentType.
     * </p>
     *
     * @param comp a {@link neqsim.thermo.component.ComponentInterface} object
     */
    public void setComponentType(ComponentInterface comp) {
        defComponent = comp;
    }

    /** {@inheritDoc} */
    @Override
    public void addcomponent(String componentName, double moles, double molesInPhase,
            int compNumber) {
        super.addcomponent(moles);
        try {
            componentArray[compNumber] =
                    defComponent.getClass().getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            logger.error("err " + e.toString());
        }
        componentArray[compNumber].createComponent(componentName, moles, molesInPhase, compNumber);
    }

    /** {@inheritDoc} */
    @Override
    public void init(double totalNumberOfMoles, int numberOfComponents, int type, int phase,
            double beta) {
        super.init(totalNumberOfMoles, numberOfComponents, type, phase, beta);
    }

    /** {@inheritDoc} */
    @Override
    public double molarVolume(double pressure, double temperature, double A, double B, int phase)
            throws neqsim.util.exception.IsNaNException,
            neqsim.util.exception.TooManyIterationsException {
        return 1.0;
    }

    /** {@inheritDoc} */
    @Override
    public void resetMixingRule(int type) {}

    /** {@inheritDoc} */
    @Override
    public double getMolarVolume() {
        return 1.0;
    }

    /** {@inheritDoc} */
    @Override
    public double getGibbsEnergy() {
        double val = 0.0;
        for (int i = 0; i < numberOfComponents; i++) {
            val += getComponent(i).getNumberOfMolesInPhase()
                    * (getComponent(i).getLogFugacityCoefficient());// +Math.log(getComponent(i).getx()*getComponent(i).getAntoineVaporPressure(temperature)));
        }
        return R * temperature * ((val) + Math.log(pressure) * numberOfMolesInPhase);
    }
}
