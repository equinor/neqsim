/*
 * PhysicalPropertiesInterface.java
 *
 * Created on 29. oktober 2000, 16:14
 */

package neqsim.physicalProperties.physicalPropertyMethods.methodInterface;

import neqsim.thermo.ThermodynamicConstantsInterface;

/**
 *
 * @author Even Solbraa
 * @version
 */
public interface DensityInterface extends ThermodynamicConstantsInterface,
        neqsim.physicalProperties.physicalPropertyMethods.PhysicalPropertyMethodInterface {
    public double calcDensity();

    @Override
	public Object clone();
}