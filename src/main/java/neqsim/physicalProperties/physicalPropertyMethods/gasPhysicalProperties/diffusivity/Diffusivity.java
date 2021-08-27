/*
 * Conductivity.java
 *
 * Created on 1. november 2000, 19:00
 */

package neqsim.physicalProperties.physicalPropertyMethods.gasPhysicalProperties.diffusivity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author  Even Solbraa
 * @version
 */
public class Diffusivity
        extends neqsim.physicalProperties.physicalPropertyMethods.gasPhysicalProperties.GasPhysicalPropertyMethod
        implements neqsim.physicalProperties.physicalPropertyMethods.methodInterface.DiffusivityInterface {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(Diffusivity.class);

    double[][] binaryDiffusionCoeffisients, binaryLennardJonesOmega;
    double[] effectiveDiffusionCoefficient;

    /** Creates new Conductivity */
    public Diffusivity() {
    }

    public Diffusivity(neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface gasPhase) {
        super(gasPhase);
        binaryDiffusionCoeffisients = new double[gasPhase.getPhase().getNumberOfComponents()][gasPhase.getPhase()
                .getNumberOfComponents()];
        binaryLennardJonesOmega = new double[gasPhase.getPhase().getNumberOfComponents()][gasPhase.getPhase()
                .getNumberOfComponents()];
        effectiveDiffusionCoefficient = new double[gasPhase.getPhase().getNumberOfComponents()];
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
        for (int i = 0; i < gasPhase.getPhase().getNumberOfComponents(); i++) {
            System.arraycopy(this.binaryDiffusionCoeffisients[i], 0, properties.binaryDiffusionCoeffisients[i], 0,
                    gasPhase.getPhase().getNumberOfComponents());
        }
        return properties;
    }

    @Override
    public double calcBinaryDiffusionCoefficient(int i, int j, int method) {
        // method - estimation method
        // if(method==? then)
        double A2 = 1.06036, B2 = 0.15610, C2 = 0.19300, D2 = 0.47635, E2 = 1.03587, F2 = 1.52996, G2 = 1.76474,
                H2 = 3.89411;
        double tempVar2 = gasPhase.getPhase().getTemperature() / binaryEnergyParameter[i][j];
        binaryLennardJonesOmega[i][j] = A2 / Math.pow(tempVar2, B2) + C2 / Math.exp(D2 * tempVar2)
                + E2 / Math.exp(F2 * tempVar2) + G2 / Math.exp(H2 * tempVar2);
        binaryDiffusionCoeffisients[i][j] = 0.00266 * Math.pow(gasPhase.getPhase().getTemperature(), 1.5)
                / (gasPhase.getPhase().getPressure() * Math.sqrt(binaryMolecularMass[i][j])
                        * Math.pow(binaryMolecularDiameter[i][j], 2) * binaryLennardJonesOmega[i][j]);
        return binaryDiffusionCoeffisients[i][j] *= 1e-4;
    }

    @Override
    public double[][] calcDiffusionCoeffisients(int binaryDiffusionCoefficientMethod,
            int multicomponentDiffusionMethod) {

        for (int i = 0; i < gasPhase.getPhase().getNumberOfComponents(); i++) {
            for (int j = i; j < gasPhase.getPhase().getNumberOfComponents(); j++) {
                binaryDiffusionCoeffisients[i][j] = calcBinaryDiffusionCoefficient(i, j,
                        binaryDiffusionCoefficientMethod);
                binaryDiffusionCoeffisients[j][i] = binaryDiffusionCoeffisients[i][j];
            }
        }

        if (multicomponentDiffusionMethod == 0) {
            // ok use full matrix
        } else if (multicomponentDiffusionMethod == 0) {
            // calcEffectiveDiffusionCoeffisients();
        }
        return binaryDiffusionCoeffisients;
    }

    @Override
    public void calcEffectiveDiffusionCoeffisients() {
        double sum = 0;

        for (int i = 0; i < gasPhase.getPhase().getNumberOfComponents(); i++) {
            sum = 0;
            for (int j = 0; j < gasPhase.getPhase().getNumberOfComponents(); j++) {
                if (i == j) {
                } else {
                    sum += gasPhase.getPhase().getComponents()[j].getx() / binaryDiffusionCoeffisients[i][j];
                }
            }
            effectiveDiffusionCoefficient[i] = (1.0 - gasPhase.getPhase().getComponents()[i].getx()) / sum;
        }
    }

    @Override
    public double getFickBinaryDiffusionCoefficient(int i, int j) {
        return binaryDiffusionCoeffisients[i][j];
    }

    @Override
    public double getEffectiveDiffusionCoefficient(int i) {
        return effectiveDiffusionCoefficient[i];
    }

    @Override
    public double getMaxwellStefanBinaryDiffusionCoefficient(int i, int j) {
        /*
         * double temp = (i==j)? 1.0: 0.0; double nonIdealCorrection = temp +
         * gasPhase.getPhase().getComponents()[i].getx() *
         * gasPhase.getPhase().getComponents()[i].getdfugdn(j) *
         * gasPhase.getPhase().getNumberOfMolesInPhase(); if
         * (Double.isNaN(nonIdealCorrection)) nonIdealCorrection=1.0; return
         * binaryDiffusionCoeffisients[i][j]/nonIdealCorrection; // shuld be divided by
         * non ideality factor
         */
        return binaryDiffusionCoeffisients[i][j];
    }

}
