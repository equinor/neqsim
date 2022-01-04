/*
 * Conductivity.java
 *
 * Created on 1. november 2000, 19:00
 */
package neqsim.physicalProperties.physicalPropertyMethods.solidPhysicalProperties.diffusivity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * <p>
 * Diffusivity class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class Diffusivity extends
        neqsim.physicalProperties.physicalPropertyMethods.solidPhysicalProperties.SolidPhysicalPropertyMethod
        implements
        neqsim.physicalProperties.physicalPropertyMethods.methodInterface.DiffusivityInterface {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(Diffusivity.class);

    double[][] binaryDiffusionCoeffisients;
    double[] effectiveDiffusionCoefficient;

    /**
     * <p>Constructor for Diffusivity.</p>
     */
    public Diffusivity() {}

    /**
     * <p>
     * Constructor for Diffusivity.
     * </p>
     *
     * @param solidPhase a
     *        {@link neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface}
     *        object
     */
    public Diffusivity(
            neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface solidPhase) {
        super(solidPhase);
        binaryDiffusionCoeffisients = new double[solidPhase.getPhase()
                .getNumberOfComponents()][solidPhase.getPhase().getNumberOfComponents()];
        effectiveDiffusionCoefficient = new double[solidPhase.getPhase().getNumberOfComponents()];

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

        properties.binaryDiffusionCoeffisients = this.binaryDiffusionCoeffisients.clone();
        for (int i = 0; i < solidPhase.getPhase().getNumberOfComponents(); i++) {
            System.arraycopy(this.binaryDiffusionCoeffisients[i], 0,
                    properties.binaryDiffusionCoeffisients[i], 0,
                    solidPhase.getPhase().getNumberOfComponents());
        }
        return properties;
    }

    /** {@inheritDoc} */
    @Override
    public double[][] calcDiffusionCoeffisients(int binaryDiffusionCoefficientMethod,
            int multicomponentDiffusionMethod) {

        return binaryDiffusionCoeffisients;
    }

    /** {@inheritDoc} */
    @Override
    public void calcEffectiveDiffusionCoeffisients() {

    }

    /** {@inheritDoc} */
    @Override
    public double getMaxwellStefanBinaryDiffusionCoefficient(int i, int j) {
        return binaryDiffusionCoeffisients[i][j];
    }

    /** {@inheritDoc} */
    @Override
    public double getEffectiveDiffusionCoefficient(int i) {
        return effectiveDiffusionCoefficient[i];
    }

    /** {@inheritDoc} */
    @Override
    public double getFickBinaryDiffusionCoefficient(int i, int j) {
        double nonIdealCorrection = 1.0;
        return binaryDiffusionCoeffisients[i][j] * nonIdealCorrection; // shuld be divided by non
                                                                       // ideality factor
    }

    /** {@inheritDoc} */
    @Override
    public double calcBinaryDiffusionCoefficient(int i, int j, int method) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
