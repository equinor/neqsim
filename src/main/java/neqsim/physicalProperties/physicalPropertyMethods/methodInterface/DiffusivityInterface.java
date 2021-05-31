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
public interface DiffusivityInterface extends ThermodynamicConstantsInterface,
        neqsim.physicalProperties.physicalPropertyMethods.PhysicalPropertyMethodInterface {
    public double calcBinaryDiffusionCoefficient(int i, int j, int method);

    public double[][] calcDiffusionCoeffisients(int binaryDiffusionCoefficientMethod,
            int multicomponentDiffusionMethod);

    public double getFickBinaryDiffusionCoefficient(int i, int j);

    public double getMaxwellStefanBinaryDiffusionCoefficient(int i, int j);

    public double getEffectiveDiffusionCoefficient(int i);

    public void calcEffectiveDiffusionCoeffisients();

    @Override
	public Object clone();
}