package neqsim.physicalProperties.physicalPropertyMethods.methodInterface;

import neqsim.thermo.ThermodynamicConstantsInterface;

/**
 * <p>DiffusivityInterface interface.</p>
 *
 * @author Even Solbraa
 */
public interface DiffusivityInterface extends ThermodynamicConstantsInterface,
        neqsim.physicalProperties.physicalPropertyMethods.PhysicalPropertyMethodInterface {
    /**
     * <p>calcBinaryDiffusionCoefficient.</p>
     *
     * @param i a int
     * @param j a int
     * @param method a int
     * @return a double
     */
    public double calcBinaryDiffusionCoefficient(int i, int j, int method);

    /**
     * <p>calcDiffusionCoeffisients.</p>
     *
     * @param binaryDiffusionCoefficientMethod a int
     * @param multicomponentDiffusionMethod a int
     * @return an array of {@link double} objects
     */
    public double[][] calcDiffusionCoeffisients(int binaryDiffusionCoefficientMethod,
            int multicomponentDiffusionMethod);

    /**
     * <p>getFickBinaryDiffusionCoefficient.</p>
     *
     * @param i a int
     * @param j a int
     * @return a double
     */
    public double getFickBinaryDiffusionCoefficient(int i, int j);

    /**
     * <p>getMaxwellStefanBinaryDiffusionCoefficient.</p>
     *
     * @param i a int
     * @param j a int
     * @return a double
     */
    public double getMaxwellStefanBinaryDiffusionCoefficient(int i, int j);

    /**
     * <p>getEffectiveDiffusionCoefficient.</p>
     *
     * @param i a int
     * @return a double
     */
    public double getEffectiveDiffusionCoefficient(int i);

    /**
     * <p>calcEffectiveDiffusionCoeffisients.</p>
     */
    public void calcEffectiveDiffusionCoeffisients();

    /** {@inheritDoc} */
    @Override
    public DiffusivityInterface clone();
}
