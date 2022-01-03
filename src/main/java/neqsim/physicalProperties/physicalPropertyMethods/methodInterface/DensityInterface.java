package neqsim.physicalProperties.physicalPropertyMethods.methodInterface;

import neqsim.thermo.ThermodynamicConstantsInterface;

/**
 * <p>DensityInterface interface.</p>
 *
 * @author Even Solbraa
 */
public interface DensityInterface extends ThermodynamicConstantsInterface,
        neqsim.physicalProperties.physicalPropertyMethods.PhysicalPropertyMethodInterface {
    /**
     * <p>calcDensity.</p>
     *
     * @return a double
     */
    public double calcDensity();

    /** {@inheritDoc} */
    @Override
    public DensityInterface clone();
}
