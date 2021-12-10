/*
 * LevenbergMarquardtBiasDev.java
 *
 * Created on 22. januar 2001, 23:00
 */

package neqsim.statistics.parameterFitting.nonLinearParameterFitting;

/**
 * @author  Even Solbraa
 * @version
 */
public class LevenbergMarquardtBiasDev extends LevenbergMarquardt {

    private static final long serialVersionUID = 1000;

    /** Creates new LevenbergMarquardt */
    public LevenbergMarquardtBiasDev() {
        super();
    }

    @Override
    public LevenbergMarquardtBiasDev clone() {
        LevenbergMarquardtBiasDev clonedClass = null;
        try {
            clonedClass = (LevenbergMarquardtBiasDev) super.clone();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }

        return clonedClass;
    }

    @Override
    public double calcChiSquare() {
        double chiSquare = 0;
        for (int i = 0; i < sampleSet.getLength(); i++) {

            chiSquare += (sampleSet.getSample(i).getSampleValue() - this.calcValue(sampleSet.getSample(i)))
                    / sampleSet.getSample(i).getStandardDeviation();
        }
        return chiSquare;
    }

    @Override
    public double[][] calcAlphaMatrix() {
        double[][] alpha = new double[sampleSet.getSample(0).getFunction().getFittingParams().length][sampleSet
                .getSample(0).getFunction().getFittingParams().length];
        for (int i = 0; i < alpha.length; i++) {
            for (int j = 0; j < alpha[0].length; j++) {
                alpha[i][j] = 0.0;
                for (int k = 0; k < sampleSet.getLength(); k++) {
                    alpha[i][j] += (dyda[k][i] * dyda[k][j]) / sampleSet.getSample(k).getStandardDeviation();
                }
                if (i == j) {
                    alpha[i][j] *= (1.0 + multiFactor);
                }
            }
        }
        return alpha;
    }

    @Override
    public double[] calcBetaMatrix() {
        double[] beta = new double[sampleSet.getSample(0).getFunction().getFittingParams().length];
        for (int i = 0; i < beta.length; i++) {
            beta[i] = 0.0;
            for (int j = 0; j < sampleSet.getLength(); j++) {
                beta[i] += (sampleSet.getSample(j).getSampleValue() - calcValue(sampleSet.getSample(j)))
                        / sampleSet.getSample(j).getStandardDeviation() * dyda[j][i];
            }
        }
        return beta;
    }
}
