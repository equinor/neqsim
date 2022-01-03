package neqsim.physicalProperties.physicalPropertyMethods.liquidPhysicalProperties.diffusivity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author Even Solbraa
 * @version
 */
abstract class Diffusivity extends
        neqsim.physicalProperties.physicalPropertyMethods.liquidPhysicalProperties.LiquidPhysicalPropertyMethod
        implements
        neqsim.physicalProperties.physicalPropertyMethods.methodInterface.DiffusivityInterface {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(Diffusivity.class);

    double[][] binaryDiffusionCoeffisients;
    double[] effectiveDiffusionCoefficient;

    /**
     * Creates new Conductivity
     */
    public Diffusivity() {}

    /**
     * <p>Constructor for Diffusivity.</p>
     *
     * @param liquidPhase a {@link neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface} object
     */
    public Diffusivity(
            neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface liquidPhase) {
        super(liquidPhase);
        binaryDiffusionCoeffisients = new double[liquidPhase.getPhase()
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

        properties.binaryDiffusionCoeffisients = this.binaryDiffusionCoeffisients.clone();
        for (int i = 0; i < liquidPhase.getPhase().getNumberOfComponents(); i++) {
            System.arraycopy(this.binaryDiffusionCoeffisients[i], 0,
                    properties.binaryDiffusionCoeffisients[i], 0,
                    liquidPhase.getPhase().getNumberOfComponents());
        }
        return properties;
    }

    /** {@inheritDoc} */
    @Override
    public double[][] calcDiffusionCoeffisients(int binaryDiffusionCoefficientMethod,
            int multicomponentDiffusionMethod) {
        double tempVar = 0, tempVar2 = 0;

        for (int i = 0; i < liquidPhase.getPhase().getNumberOfComponents(); i++) {
            for (int j = 0; j < liquidPhase.getPhase().getNumberOfComponents(); j++) {
                binaryDiffusionCoeffisients[i][j] =
                        calcBinaryDiffusionCoefficient(i, j, binaryDiffusionCoefficientMethod);
            }
        }

        // Vignes correlation
        for (int i = 0; i < liquidPhase.getPhase().getNumberOfComponents(); i++) {
            for (int j = 0; j < liquidPhase.getPhase().getNumberOfComponents(); j++) {
                if (i != j) {
                    binaryDiffusionCoeffisients[i][j] = Math.pow(binaryDiffusionCoeffisients[i][j],
                            liquidPhase.getPhase().getComponents()[j].getx())
                            * Math.pow(binaryDiffusionCoeffisients[j][i],
                                    liquidPhase.getPhase().getComponents()[i].getx());
                }
                // System.out.println("diff liq " + binaryDiffusionCoeffisients[i][j] );
            }
        }
        return binaryDiffusionCoeffisients;
    }

    /** {@inheritDoc} */
    @Override
    public void calcEffectiveDiffusionCoeffisients() {
        double sum = 0;

        for (int i = 0; i < liquidPhase.getPhase().getNumberOfComponents(); i++) {
            sum = 0;
            for (int j = 0; j < liquidPhase.getPhase().getNumberOfComponents(); j++) {
                if (i == j) {
                } else {
                    sum += liquidPhase.getPhase().getComponents()[j].getx()
                            / binaryDiffusionCoeffisients[i][j];
                }
            }
            effectiveDiffusionCoefficient[i] =
                    (1.0 - liquidPhase.getPhase().getComponents()[i].getx()) / sum;
        }
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
        double temp = (i == j) ? 1.0 : 0.0;
        double nonIdealCorrection = temp + liquidPhase.getPhase().getComponents()[i].getx()
                * liquidPhase.getPhase().getComponents()[i].getdfugdn(j)
                * liquidPhase.getPhase().getNumberOfMolesInPhase();
        if (Double.isNaN(nonIdealCorrection)) {
            nonIdealCorrection = 1.0;
        }
        return binaryDiffusionCoeffisients[i][j] * nonIdealCorrection; // shuld be divided by non
                                                                       // ideality factor
    }
}
