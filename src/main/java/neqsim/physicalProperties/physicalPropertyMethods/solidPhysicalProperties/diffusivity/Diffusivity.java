/*
 * Conductivity.java
 *
 * Created on 1. november 2000, 19:00
 */
package neqsim.physicalProperties.physicalPropertyMethods.solidPhysicalProperties.diffusivity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author Even Solbraa
 * @version
 */
public class Diffusivity extends
        neqsim.physicalProperties.physicalPropertyMethods.solidPhysicalProperties.SolidPhysicalPropertyMethod
        implements
        neqsim.physicalProperties.physicalPropertyMethods.methodInterface.DiffusivityInterface {
    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(Diffusivity.class);

    double[][] binaryDiffusionCoeffisients;
    double[] effectiveDiffusionCoefficient;

    /** Creates new Conductivity */

    public Diffusivity() {}

    public Diffusivity(
            neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface solidPhase) {
        super(solidPhase);
        binaryDiffusionCoeffisients = new double[solidPhase.getPhase()
                .getNumberOfComponents()][solidPhase.getPhase().getNumberOfComponents()];
        effectiveDiffusionCoefficient = new double[solidPhase.getPhase().getNumberOfComponents()];
    }

    @Override
    public Object clone() {
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

    @Override
    public double[][] calcDiffusionCoeffisients(int binaryDiffusionCoefficientMethod,
            int multicomponentDiffusionMethod) {
        return binaryDiffusionCoeffisients;
    }

    @Override
    public void calcEffectiveDiffusionCoeffisients() {}

    @Override
    public double getMaxwellStefanBinaryDiffusionCoefficient(int i, int j) {
        return binaryDiffusionCoeffisients[i][j];
    }

    @Override
    public double getEffectiveDiffusionCoefficient(int i) {
        return effectiveDiffusionCoefficient[i];
    }

    @Override
    public double getFickBinaryDiffusionCoefficient(int i, int j) {
        double nonIdealCorrection = 1.0;
        return binaryDiffusionCoeffisients[i][j] * nonIdealCorrection; // shuld be divided by non
                                                                       // ideality factor
    }

    @Override
    public double calcBinaryDiffusionCoefficient(int i, int j, int method) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
