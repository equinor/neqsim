package neqsim.physicalProperties.physicalPropertyMethods.methodInterface;

import neqsim.thermo.ThermodynamicConstantsInterface;

/**
 *
 * @author Even Solbraa
 * @version
 */
public interface ConductivityInterface extends ThermodynamicConstantsInterface,
        neqsim.physicalProperties.physicalPropertyMethods.PhysicalPropertyMethodInterface {
    public double calcConductivity();

    @Override
    public ConductivityInterface clone();
}
