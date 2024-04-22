/*
 * StatisticsBaseClass.java
 *
 * Created on 22. januar 2001, 23:00
 */

package neqsim.statistics.parameterFitting;



import java.text.DecimalFormat;
import java.text.FieldPosition;






import Jama.Matrix;

/**
 * <p>
 * Abstract StatisticsBaseClass class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public abstract class StatisticsBaseClass implements Cloneable, StatisticsInterface {
  

  protected SampleSet sampleSet = new SampleSet();
  protected double chiSquare = 0;
  protected double[][] dyda;
  protected double[] beta;
  protected double[][] alpha;
  protected double[] parameterStandardDeviation;
  protected double[] parameterUncertainty;
  protected double multiFactor = 10.0;
  private int numberOfTuningParameters = 1;
  protected Matrix coVarianceMatrix;

  protected Matrix parameterCorrelationMatrix;

  protected double[][] xVal;
  protected double[] expVal;
  protected double[] absDev;

  protected double[] reldeviation;

  protected double[] calcVal;
  protected String[][] valTable;
  protected double absStdDev = 0.0;

  protected double biasdev = 0.0;

  protected double incompleteGammaComplemented = 0.0;

  /**
   * <p>
   * Constructor for StatisticsBaseClass.
   * </p>
   */
  public StatisticsBaseClass() {}

  /** {@inheritDoc} */
  @Override
  public StatisticsBaseClass clone() {
    StatisticsBaseClass clonedClass = null;
    try {
      clonedClass = (StatisticsBaseClass) super.clone();
    } catch (Exception ex) {
      ex.printStackTrace();
    }

    clonedClass.sampleSet = sampleSet.clone();

    return clonedClass;
  }

  /**
   * <p>
   * Setter for the field <code>sampleSet</code>.
   * </p>
   *
   * @param sampleSet a {@link neqsim.statistics.parameterFitting.SampleSet} object
   */
  public void setSampleSet(SampleSet sampleSet) {
    this.sampleSet = sampleSet;
  }

  /**
   * <p>
   * addSampleSet.
   * </p>
   *
   * @param sampleSet a {@link neqsim.statistics.parameterFitting.SampleSet} object
   */
  public void addSampleSet(SampleSet sampleSet) {
    this.sampleSet.addSampleSet(sampleSet);
  }

  /** {@inheritDoc} */
  @Override
  public StatisticsBaseClass createNewRandomClass() {
    StatisticsBaseClass newClass = this.clone();
    newClass.setSampleSet(this.sampleSet.createNewNormalDistributedSet());
    return newClass;
  }

  /**
   * <p>
   * calcValue.
   * </p>
   *
   * @param sample a {@link neqsim.statistics.parameterFitting.SampleValue} object
   * @return a double
   */
  public double calcValue(SampleValue sample) {
    return sample.getFunction().calcValue(sample.getDependentValues());
  }

  /**
   * <p>
   * checkBounds.
   * </p>
   *
   * @param newParameters a {@link Jama.Matrix} object
   */
  public void checkBounds(Matrix newParameters) {
    String okstring = "";
    int errors = 0;
    if (sampleSet.getSample(0).getFunction().getBounds() != null) {
      for (int i = 0; i < newParameters.getColumnDimension(); i++) {
        if (newParameters.get(0, i) < sampleSet.getSample(0).getFunction().getLowerBound(i)) {
          okstring += "parameter " + i + " lower than bound: " + newParameters.get(0, i) + "\n";
          errors++;
          newParameters.set(0, i, sampleSet.getSample(0).getFunction().getLowerBound(i));
        }
        if (newParameters.get(0, i) > sampleSet.getSample(0).getFunction().getUpperBound(i)) {
          okstring += "parameter " + i + " higher than bound: " + newParameters.get(0, i) + "\n";
          errors++;
          newParameters.set(0, i, sampleSet.getSample(0).getFunction().getUpperBound(i));
        }
      }
      System.out.println("bounds checked - errors: " + errors);
      System.out.println(okstring);
    }
  }

  /**
   * <p>
   * calcTrueValue.
   * </p>
   *
   * @param sample a {@link neqsim.statistics.parameterFitting.SampleValue} object
   * @return a double
   */
  public double calcTrueValue(SampleValue sample) {
    return sample.getFunction().calcTrueValue(calcValue(sample));
  }

  /**
   * <p>
   * calcTrueValue.
   * </p>
   *
   * @param val a double
   * @param sample a {@link neqsim.statistics.parameterFitting.SampleValue} object
   * @return a double
   */
  public double calcTrueValue(double val, SampleValue sample) {
    return sample.getFunction().calcTrueValue(val);
  }

  /**
   * <p>
   * setFittingParameters.
   * </p>
   *
   * @param parameterVals an array of {@link double} objects
   */
  public void setFittingParameters(double[] parameterVals) {
    for (int i = 0; i < sampleSet.getLength(); i++) {
      for (int k = 0; k < sampleSet.getSample(i).getFunction().getFittingParams().length; k++) {
        sampleSet.getSample(i).getFunction().setFittingParams(k, parameterVals[k]);
      }
    }
  }

  /**
   * <p>
   * setFittingParameter.
   * </p>
   *
   * @param parameterNumber a int
   * @param parameterVal a double
   */
  public void setFittingParameter(int parameterNumber, double parameterVal) {
    for (int i = 0; i < sampleSet.getLength(); i++) {
      sampleSet.getSample(i).getFunction().setFittingParams(parameterNumber, parameterVal);
    }
  }

  /**
   * <p>
   * getSample.
   * </p>
   *
   * @param i a int
   * @return a {@link neqsim.statistics.parameterFitting.SampleValue} object
   */
  public SampleValue getSample(int i) {
    return sampleSet.getSample(i);
  }

  /** {@inheritDoc} */
  @Override
  public SampleSet getSampleSet() {
    return sampleSet;
  }

  /**
   * <p>
   * calcChiSquare.
   * </p>
   *
   * @return a double
   */
  public double calcChiSquare() {
    calcVal = new double[sampleSet.getLength()];
    double chiSquare = 0;
    for (int i = 0; i < sampleSet.getLength(); i++) {
      calcVal[i] = this.calcValue(sampleSet.getSample(i));
      chiSquare += Math.pow((sampleSet.getSample(i).getSampleValue() - calcVal[i])
          / sampleSet.getSample(i).getStandardDeviation(), 2.0);
    }
    return chiSquare;
  }

  /**
   * <p>
   * calcAlphaMatrix.
   * </p>
   *
   * @return an array of {@link double} objects
   */
  public double[][] calcAlphaMatrix() {
    double[][] alpha = new double[sampleSet.getSample(0).getFunction()
        .getFittingParams().length][sampleSet.getSample(0).getFunction().getFittingParams().length];
    for (int i = 0; i < alpha.length; i++) {
      for (int j = 0; j < alpha[0].length; j++) {
        alpha[i][j] = 0.0;
        for (int k = 0; k < sampleSet.getLength(); k++) {
          alpha[i][j] += (dyda[k][i] * dyda[k][j])
              / Math.pow(sampleSet.getSample(k).getStandardDeviation(), 2.0);
        }
        if (i == j) {
          alpha[i][j] *= (1.0 + multiFactor);
        }
      }
    }
    return alpha;
  }

  /**
   * <p>
   * calcBetaMatrix.
   * </p>
   *
   * @return an array of {@link double} objects
   */
  public double[] calcBetaMatrix() {
    double[] beta = new double[sampleSet.getSample(0).getFunction().getFittingParams().length];
    for (int i = 0; i < beta.length; i++) {
      beta[i] = 0.0;
      for (int j = 0; j < sampleSet.getLength(); j++) {
        beta[i] += (sampleSet.getSample(j).getSampleValue() - calcVal[j])
            / Math.pow(sampleSet.getSample(j).getStandardDeviation(), 2.0) * dyda[j][i];
      }
    }
    return beta;
  }

  /**
   * <p>
   * calcDerivatives.
   * </p>
   *
   * @return an array of {@link double} objects
   */
  public double[][] calcDerivatives() {
    dyda = new double[sampleSet.getLength()][sampleSet.getSample(0).getFunction()
        .getNumberOfFittingParams()];

    for (int i = 0; i < sampleSet.getLength(); i++) {
      for (int j = 0; j < sampleSet.getSample(0).getFunction().getNumberOfFittingParams(); j++) {
        dyda[i][j] = NumericalDerivative.calcDerivative(this, i, j);
      }
    }
    return dyda;
  }

  // public void calcParameterStandardDeviation(){
  // parameterStandardDeviation = new
  // double[sampleSet.getSample(0).getFunction().getNumberOfFittingParams()];
  // for(int
  // j=0;j<sampleSet.getSample(0).getFunction().getNumberOfFittingParams();j++){
  // parameterStandardDeviation[j] = 0.0;
  // for(int i=0;i<sampleSet.getLength();i++){
  // parameterStandardDeviation[j] += Math.pow(1.0/dyda[i][j],2.0)*Math.pow(
  // sampleSet.getSample(i).getStandardDeviation(),2.0);
  // }
  // parameterStandardDeviation[j] = Math.sqrt(parameterStandardDeviation[j]);
  // }
  // }
  /**
   * <p>
   * calcParameterStandardDeviation.
   * </p>
   */
  public void calcParameterStandardDeviation() {
    parameterStandardDeviation =
        new double[sampleSet.getSample(0).getFunction().getNumberOfFittingParams()];
    for (int j = 0; j < sampleSet.getSample(0).getFunction().getNumberOfFittingParams(); j++) {
      parameterStandardDeviation[j] = Math.sqrt(coVarianceMatrix.get(j, j));
    }
  }

  /**
   * Calculates the confidence interval given by 95.4%. See Numerical Recepies in C. p. 697
   */
  public void calcParameterUncertainty() {
    parameterUncertainty =
        new double[sampleSet.getSample(0).getFunction().getNumberOfFittingParams()];
    for (int j = 0; j < sampleSet.getSample(0).getFunction().getNumberOfFittingParams(); j++) {
      parameterUncertainty[j] = Math.sqrt(4.0) * Math.sqrt(coVarianceMatrix.get(j, j));
    }
  }

  /**
   * <p>
   * calcCoVarianceMatrix.
   * </p>
   */
  public void calcCoVarianceMatrix() {
    double old = multiFactor;
    multiFactor = 0.0;
    calcAlphaMatrix();
    coVarianceMatrix = new Matrix(alpha).inverse();
    multiFactor = old;
  }

  /**
   * <p>
   * calcCorrelationMatrix.
   * </p>
   */
  public void calcCorrelationMatrix() {
    parameterCorrelationMatrix =
        new Matrix(sampleSet.getSample(0).getFunction().getNumberOfFittingParams(),
            sampleSet.getSample(0).getFunction().getNumberOfFittingParams());
    for (int i = 0; i < sampleSet.getSample(0).getFunction().getNumberOfFittingParams(); i++) {
      for (int j = 0; j < sampleSet.getSample(0).getFunction().getNumberOfFittingParams(); j++) {
        double temp = coVarianceMatrix.get(i, j)
            / Math.sqrt(coVarianceMatrix.get(j, j) * coVarianceMatrix.get(i, i));
        parameterCorrelationMatrix.set(i, j, temp);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public abstract void init();

  /** {@inheritDoc} */
  @Override
  public abstract void solve();

  /**
   * <p>
   * runMonteCarloSimulation.
   * </p>
   */
  public void runMonteCarloSimulation() {
    neqsim.statistics.monteCarloSimulation.MonteCarloSimulation montCarlSim =
        new neqsim.statistics.monteCarloSimulation.MonteCarloSimulation(this, 10);
    montCarlSim.runSimulation();
  }

  /** {@inheritDoc} */
  @Override
  public void runMonteCarloSimulation(int numRuns) {
    neqsim.statistics.monteCarloSimulation.MonteCarloSimulation montCarlSim =
        new neqsim.statistics.monteCarloSimulation.MonteCarloSimulation(this, numRuns);
    montCarlSim.runSimulation();
  }

  /**
   * <p>
   * calcAbsDev.
   * </p>
   */
  public void calcAbsDev() {
    setFittingParameters(sampleSet.getSample(0).getFunction().getFittingParams());
    xVal = new double[sampleSet.getSample(0).getDependentValues().length][sampleSet.getLength()];
    expVal = new double[sampleSet.getLength()];
    absDev = new double[sampleSet.getLength()];
    calcVal = new double[sampleSet.getLength()];

    double rmsDev = 0.0;
    double dev = 0;
    double dev2 = 0.0;
    double shiSq = 0.0;

    biasdev = 0.0;
    absStdDev = 0.0;

    for (int i = 0; i < sampleSet.getLength(); i++) {
      expVal[i] =
          this.calcTrueValue(sampleSet.getSample(i).getSampleValue(), sampleSet.getSample(i));
      calcVal[i] = this.calcTrueValue(sampleSet.getSample(i));
      shiSq +=
          Math.pow((calcVal[i] - expVal[i]) / sampleSet.getSample(i).getStandardDeviation(), 2.0);
      absDev[i] = Math.abs((calcVal[i] - expVal[i]) / expVal[i] * 100.0);
      dev = Math.abs((calcVal[i] - expVal[i]) / expVal[i] * 100.0);
      dev2 = Math.pow(calcVal[i] - expVal[i], 2.0);
      absStdDev += dev;
      rmsDev += dev2;
      System.out.println("x " + sampleSet.getSample(i).getDependentValue(0) + "  val: " + calcVal[i]
          + " exp val " + expVal[i] + "  deviation " + dev);
      for (int j = 0; j < sampleSet.getSample(0).getDependentValues().length; j++) {
        xVal[j][i] = sampleSet.getSample(i).getDependentValue(j);
      }
      biasdev += (calcVal[i] - expVal[i]) / expVal[i] * 100.0;
    }
    absStdDev /= sampleSet.getLength();
    rmsDev = Math.sqrt(rmsDev / sampleSet.getLength());
    biasdev /= sampleSet.getLength();
    chiSquare = shiSq;

    System.out.println("Shi-Square: " + shiSq);
    System.out.println("bias dev: " + biasdev);
    System.out.println("abs dev " + absStdDev);
    System.out.println("rms dev " + rmsDev * 100);
  }

  /**
   * <p>
   * displayValues.
   * </p>
   */
  public void displayValues() {}

  /** {@inheritDoc} */
  @Override
  public void displayResult() {}

  /**
   * <p>
   * displayResultWithDeviation.
   * </p>
   */
  public void displayResultWithDeviation() {}

  /**
   * <p>
   * displayMatrix.
   * </p>
   *
   * @param coVarianceMatrix a {@link Jama.Matrix} object
   * @param name a {@link java.lang.String} object
   * @param d a int
   */
  public void displayMatrix(Matrix coVarianceMatrix, String name, int d) {}

  /**
   * <p>
   * calcDeviation.
   * </p>
   */
  public void calcDeviation() {
    setFittingParameters(sampleSet.getSample(0).getFunction().getFittingParams());
    init();

    System.out.println("");
    System.out.println("Co-variance matrix : ");

    calcCoVarianceMatrix();
    // coVarianceMatrix.print(2,10);

    System.out.println("");
    System.out.println("Parameter uncertanty : ");
    calcParameterUncertainty();
    // parameterUncertaintyMatrix = new Matrix(parameterUncertainty,1);
    // parameterUncertaintyMatrix.print(2,10);

    System.out.println("");
    System.out.println("Parameter std deviation : ");
    calcParameterStandardDeviation();
    // parameterStdDevMatrix = new Matrix(parameterStandardDeviation,1);
    // parameterStdDevMatrix.print(2,10);
    calcCorrelationMatrix();

    incompleteGammaComplemented = cern.jet.stat.Gamma.incompleteGammaComplement(
        (sampleSet.getLength() - sampleSet.getSample(0).getFunction().getFittingParams().length)
            / 2.0,
        0.5 * chiSquare);
  }

  /** {@inheritDoc} */
  @Override
  public void writeToTextFile(String name) {}

  /**
   * <p>
   * displaySimple.
   * </p>
   */
  public void displaySimple() {
    calcAbsDev();
    try {
      // displayGraph();
    } catch (Exception ex) {
      System.out.println("could not display graph");
      
    }
    try {
      displayResult();
    } catch (Exception ex) {
      System.out.println("could not display graph");
      
    }
    try {
      displayValues();
    } catch (Exception ex) {
      System.out.println("could not display graph");
      
    }
  }

  /** {@inheritDoc} */
  @Override
  public void displayCurveFit() {
    calcAbsDev();
    try {
      // displayGraph();
    } catch (Exception ex) {
      System.out.println("could not display graph");
      
    }
    try {
      displayResult();
    } catch (Exception ex) {
      System.out.println("could not display graph");
      
    }
    try {
      displayValues();
    } catch (Exception ex) {
      System.out.println("could not display graph");
      
    }
    try {
      displayResultWithDeviation();
    } catch (Exception ex) {
      System.out.println("could not calc deviation");
      
    }
  }

  /** {@inheritDoc} */
  @Override
  public int getNumberOfTuningParameters() {
    return numberOfTuningParameters;
  }

  /** {@inheritDoc} */
  @Override
  public void setNumberOfTuningParameters(int numberOfTuningParameters) {
    this.numberOfTuningParameters = numberOfTuningParameters;
  }
}
