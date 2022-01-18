package neqsim.physicalProperties.physicalPropertyMethods.commonPhasePhysicalProperties.diffusivity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalProperties.physicalPropertyMethods.commonPhasePhysicalProperties.CommonPhysicalPropertyMethod;

/**
 * <p>
 * Diffusivity class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class Diffusivity extends CommonPhysicalPropertyMethod implements
        neqsim.physicalProperties.physicalPropertyMethods.methodInterface.DiffusivityInterface {
    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(Diffusivity.class);

    double[][] binaryDiffusionCoefficients, binaryLennardJonesOmega;
    double[] effectiveDiffusionCoefficient;

    /**
     * <p>
     * Constructor for Diffusivity.
     * </p>
     */
    public Diffusivity() {}

    /**
     * <p>
     * Constructor for Diffusivity.
     * </p>
     *
     * @param phase a
     *        {@link neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface}
     *        object
     */
    public Diffusivity(
            neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface phase) {
        super(phase);
        binaryDiffusionCoefficients = new double[phase.getPhase().getNumberOfComponents()][phase
                .getPhase().getNumberOfComponents()];
        binaryLennardJonesOmega = new double[phase.getPhase().getNumberOfComponents()][phase
                .getPhase().getNumberOfComponents()];
        effectiveDiffusionCoefficient = new double[phase.getPhase().getNumberOfComponents()];
    }

    /** {@inheritDoc} */
    @Override
    public Diffusivity clone() {
        Diffusivity properties = null;

        try {
            properties = (Diffusivity) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return properties;
    }

    /** {@inheritDoc} */
    @Override
    public double calcBinaryDiffusionCoefficient(int i, int j, int method) {
        return 1.0e-6;
    }

    /** {@inheritDoc} */
    @Override
    public double[][] calcDiffusionCoefficients(int binaryDiffusionCoefficientMethod,
            int multicomponentDiffusionMethod) {
        for (int i = 0; i < phase.getPhase().getNumberOfComponents(); i++) {
            for (int j = 0; j < phase.getPhase().getNumberOfComponents(); j++) {
                binaryDiffusionCoefficients[i][j] =
                        calcBinaryDiffusionCoefficient(i, j, binaryDiffusionCoefficientMethod);
                // System.out.println("diff gas " + binaryDiffusionCoefficients[i][j]);
            }
        }

        if (multicomponentDiffusionMethod == 0) {
            // ok use full matrix
        } else if (multicomponentDiffusionMethod == 0) {
            // calcEffectiveDiffusionCoefficients();
        }
        return binaryDiffusionCoefficients;
    }

    /** {@inheritDoc} */
    @Override
    public void calcEffectiveDiffusionCoefficients() {
        double sum = 0;

        for (int i = 0; i < phase.getPhase().getNumberOfComponents(); i++) {
            sum = 0;
            for (int j = 0; j < phase.getPhase().getNumberOfComponents(); j++) {
                if (i == j) {
                } else {
                    sum += phase.getPhase().getComponents()[j].getx()
                            / binaryDiffusionCoefficients[i][j];
                }
            }
            effectiveDiffusionCoefficient[i] =
                    (1.0 - phase.getPhase().getComponents()[i].getx()) / sum;
        }
    }

    /** {@inheritDoc} */
    @Override
    public double getFickBinaryDiffusionCoefficient(int i, int j) {
        return binaryDiffusionCoefficients[i][j];
    }

    /** {@inheritDoc} */
    @Override
    public double getEffectiveDiffusionCoefficient(int i) {
        return effectiveDiffusionCoefficient[i];
    }

    /** {@inheritDoc} */
    @Override
    public double getMaxwellStefanBinaryDiffusionCoefficient(int i, int j) {
        /*
         * double temp = (i==j)? 1.0: 0.0; double nonIdealCorrection = temp +
         * gasPhase.getPhase().getComponents()[i].getx() *
         * gasPhase.getPhase().getComponents()[i].getdfugdn(j) *
         * gasPhase.getPhase().getNumberOfMolesInPhase(); if (Double.isNaN(nonIdealCorrection))
         * nonIdealCorrection=1.0; return binaryDiffusionCoefficients[i][j]/nonIdealCorrection; //
         * shuld be divided by non ideality factor
         */
        return binaryDiffusionCoefficients[i][j];
    }
}
