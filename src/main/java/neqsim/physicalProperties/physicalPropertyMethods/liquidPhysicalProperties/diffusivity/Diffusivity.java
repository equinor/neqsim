package neqsim.physicalProperties.physicalPropertyMethods.liquidPhysicalProperties.diffusivity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author Even Solbraa
 */
abstract class Diffusivity extends
        neqsim.physicalProperties.physicalPropertyMethods.liquidPhysicalProperties.LiquidPhysicalPropertyMethod
        implements
        neqsim.physicalProperties.physicalPropertyMethods.methodInterface.DiffusivityInterface {
    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(Diffusivity.class);

    double[][] binaryDiffusionCoefficients;
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
     * @param liquidPhase a
     *        {@link neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface}
     *        object
     */
    public Diffusivity(
            neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface liquidPhase) {
        super(liquidPhase);
        binaryDiffusionCoefficients = new double[liquidPhase.getPhase()
                .getNumberOfComponents()][liquidPhase.getPhase().getNumberOfComponents()];
        effectiveDiffusionCoefficient = new double[liquidPhase.getPhase().getNumberOfComponents()];
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

        properties.binaryDiffusionCoefficients = this.binaryDiffusionCoefficients.clone();
        for (int i = 0; i < liquidPhase.getPhase().getNumberOfComponents(); i++) {
            System.arraycopy(this.binaryDiffusionCoefficients[i], 0,
                    properties.binaryDiffusionCoefficients[i], 0,
                    liquidPhase.getPhase().getNumberOfComponents());
        }
        return properties;
    }

    /** {@inheritDoc} */
    @Override
    public double[][] calcDiffusionCoefficients(int binaryDiffusionCoefficientMethod,
            int multicomponentDiffusionMethod) {
        for (int i = 0; i < liquidPhase.getPhase().getNumberOfComponents(); i++) {
            for (int j = 0; j < liquidPhase.getPhase().getNumberOfComponents(); j++) {
                binaryDiffusionCoefficients[i][j] =
                        calcBinaryDiffusionCoefficient(i, j, binaryDiffusionCoefficientMethod);
            }
        }

        // Vignes correlation
        for (int i = 0; i < liquidPhase.getPhase().getNumberOfComponents(); i++) {
            for (int j = 0; j < liquidPhase.getPhase().getNumberOfComponents(); j++) {
                if (i != j) {
                    binaryDiffusionCoefficients[i][j] = Math.pow(binaryDiffusionCoefficients[i][j],
                            liquidPhase.getPhase().getComponents()[j].getx())
                            * Math.pow(binaryDiffusionCoefficients[j][i],
                                    liquidPhase.getPhase().getComponents()[i].getx());
                }
                // System.out.println("diff liq " + binaryDiffusionCoefficients[i][j] );
            }
        }
        return binaryDiffusionCoefficients;
    }

    /** {@inheritDoc} */
    @Override
    public void calcEffectiveDiffusionCoefficients() {
        double sum = 0;

        for (int i = 0; i < liquidPhase.getPhase().getNumberOfComponents(); i++) {
            sum = 0;
            for (int j = 0; j < liquidPhase.getPhase().getNumberOfComponents(); j++) {
                if (i == j) {
                } else {
                    sum += liquidPhase.getPhase().getComponents()[j].getx()
                            / binaryDiffusionCoefficients[i][j];
                }
            }
            effectiveDiffusionCoefficient[i] =
                    (1.0 - liquidPhase.getPhase().getComponents()[i].getx()) / sum;
        }
    }

    /** {@inheritDoc} */
    @Override
    public double getMaxwellStefanBinaryDiffusionCoefficient(int i, int j) {
        return binaryDiffusionCoefficients[i][j];
    }

    /** {@inheritDoc} */
    @Override
    public double getEffectiveDiffusionCoefficient(int i) {
        return effectiveDiffusionCoefficient[i];
    }

    /** {@inheritDoc} */
    @Override
    public double getFickBinaryDiffusionCoefficient(int i, int j) {
        double temp = (i == j) ? 1.0 : 0.0;
        double nonIdealCorrection = temp + liquidPhase.getPhase().getComponents()[i].getx()
                * liquidPhase.getPhase().getComponents()[i].getdfugdn(j)
                * liquidPhase.getPhase().getNumberOfMolesInPhase();
        if (Double.isNaN(nonIdealCorrection)) {
            nonIdealCorrection = 1.0;
        }
        return binaryDiffusionCoefficients[i][j] * nonIdealCorrection; // shuld be divided by non
                                                                       // ideality factor
    }
}
