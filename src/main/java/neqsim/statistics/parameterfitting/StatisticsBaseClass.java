/*
 * StatisticsBaseClass.java
 *
 * Created on 22. januar 2001, 23:00
 */

package neqsim.statistics.parameterfitting;

import java.awt.Container;
import java.awt.FlowLayout;
import java.text.DecimalFormat;
import java.text.FieldPosition;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import Jama.Matrix;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * Abstract StatisticsBaseClass class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public abstract class StatisticsBaseClass implements Cloneable, StatisticsInterface {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(StatisticsBaseClass.class);

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
      logger.error(ex.getMessage());;
    }

    clonedClass.sampleSet = sampleSet.clone();

    return clonedClass;
  }

  /**
   * <p>
   * Setter for the field <code>sampleSet</code>.
   * </p>
   *
   * @param sampleSet a {@link neqsim.statistics.parameterfitting.SampleSet} object
   */
  public void setSampleSet(SampleSet sampleSet) {
    this.sampleSet = sampleSet;
  }

  /**
   * <p>
   * addSampleSet.
   * </p>
   *
   * @param sampleSet a {@link neqsim.statistics.parameterfitting.SampleSet} object
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
   * @param sample a {@link neqsim.statistics.parameterfitting.SampleValue} object
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
   * @param sample a {@link neqsim.statistics.parameterfitting.SampleValue} object
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
   * @param sample a {@link neqsim.statistics.parameterfitting.SampleValue} object
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
   * @param parameterVals an array of type double
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
   * @return a {@link neqsim.statistics.parameterfitting.SampleValue} object
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
   * @return an array of type double
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
   * @return an array of type double
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
   * @return an array of type double
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
    neqsim.statistics.montecarlosimulation.MonteCarloSimulation montCarlSim =
        new neqsim.statistics.montecarlosimulation.MonteCarloSimulation(this, 10);
    montCarlSim.runSimulation();
  }

  /** {@inheritDoc} */
  @Override
  public void runMonteCarloSimulation(int numRuns) {
    neqsim.statistics.montecarlosimulation.MonteCarloSimulation montCarlSim =
        new neqsim.statistics.montecarlosimulation.MonteCarloSimulation(this, numRuns);
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
  @ExcludeFromJacocoGeneratedReport
  public void displayValues() {
    DecimalFormat nf = new DecimalFormat();
    nf.setMaximumFractionDigits(5);
    nf.applyPattern("#.#####E0");
    JDialog dialog = new JDialog(new JFrame(), "Results from Parameter Fitting");
    Container dialogContentPane = dialog.getContentPane();
    dialogContentPane.setLayout(new FlowLayout());

    valTable = new String[sampleSet.getLength()
        + 10][sampleSet.getSample(0).getDependentValues().length + 7];
    String[] names = {"point", "x", "expY", "calcY", "abs dev [%]", "reference", "description"};
    valTable[0][0] = "";
    valTable[0][1] = "";
    valTable[0][2] = "";
    valTable[0][3] = "";
    StringBuffer buf = new StringBuffer();
    FieldPosition test = new FieldPosition(0);

    for (int j = 0; j < sampleSet.getLength(); j++) {
      valTable[j + 1][0] = "" + j;
      buf = new StringBuffer();
      valTable[j + 1][1] = nf.format(xVal[0][j], buf, test).toString();
      buf = new StringBuffer();
      valTable[j + 1][2] = nf.format(expVal[j], buf, test).toString();
      buf = new StringBuffer();
      valTable[j + 1][3] = nf.format(calcVal[j], buf, test).toString();
      buf = new StringBuffer();
      valTable[j + 1][4] = nf.format(absDev[j], buf, test).toString();
      buf = new StringBuffer();
      valTable[j + 1][5] = sampleSet.getSample(j).getReference();
      valTable[j + 1][6] = sampleSet.getSample(j).getDescription();
    }

    for (int j = 0; j < sampleSet.getLength(); j++) {
      for (int i = 0; i < sampleSet.getSample(j).getDependentValues().length; i++) {
        buf = new StringBuffer();
        valTable[j + 1][7 + i] =
            nf.format(sampleSet.getSample(j).getDependentValue(i), buf, test).toString();
      }
    }

    JTable Jtab = new JTable(valTable, names);
    JScrollPane scrollpane = new JScrollPane(Jtab);
    dialogContentPane.add(scrollpane);
    dialog.pack();
    dialog.setVisible(true);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    DecimalFormat nf = new DecimalFormat();
    nf.setMaximumFractionDigits(5);
    nf.applyPattern("#.###E0");
    JDialog dialog = new JDialog(new JFrame(), "Results from Parameter Fitting");
    Container dialogContentPane = dialog.getContentPane();
    dialogContentPane.setLayout(new FlowLayout());

    String[][] table = new String[15][5];
    String[] names = {"Parameter", "Value", "Standard deviation", "Uncertatnty ", "--"};
    table[0][0] = "";
    table[0][1] = "";
    table[0][2] = "";
    table[0][3] = "";
    StringBuffer buf = new StringBuffer();
    FieldPosition test = new FieldPosition(0);

    for (int j = 0; j < sampleSet.getSample(0).getFunction().getFittingParams().length; j++) {
      table[j + 1][0] = "parameter " + j;
      buf = new StringBuffer();
      table[j + 1][1] = nf
          .format(sampleSet.getSample(0).getFunction().getFittingParams()[j], buf, test).toString();
    }

    int numb = sampleSet.getSample(0).getFunction().getFittingParams().length;

    table[numb + 2][0] = "Number Of Data Points ";
    buf = new StringBuffer();
    nf.applyPattern("#");
    table[numb + 2][1] = nf.format(sampleSet.getLength(), buf, test).toString();

    table[numb + 3][0] = "Shi-Square ";
    buf = new StringBuffer();
    nf.applyPattern("#.###E0");
    table[numb + 3][1] = nf.format(chiSquare, buf, test).toString();
    table[numb + 4][0] = "Abs.rel.dev (%) ";
    buf = new StringBuffer();
    table[numb + 4][1] = nf.format(absStdDev, buf, test).toString();
    table[numb + 5][0] = "Bias.dev (%) ";
    buf = new StringBuffer();
    table[numb + 5][1] = nf.format(biasdev, buf, test).toString();

    JTable Jtab = new JTable(table, names);
    JScrollPane scrollpane = new JScrollPane(Jtab);
    dialogContentPane.add(scrollpane);
    dialog.pack();
    dialog.setVisible(true);
  }

  /**
   * <p>
   * displayResultWithDeviation.
   * </p>
   */
  @ExcludeFromJacocoGeneratedReport
  public void displayResultWithDeviation() {
    DecimalFormat nf = new DecimalFormat();
    nf.setMaximumFractionDigits(5);
    nf.applyPattern("#.###E0");

    calcDeviation();
    JDialog dialog = new JDialog(new JFrame(), "Results from Parameter Fitting");
    Container dialogContentPane = dialog.getContentPane();
    dialogContentPane.setLayout(new FlowLayout());

    String[][] table = new String[15][5];
    String[] names = {"Parameter", "Value", "Standard deviation", "Uncertatnty ", "--"};
    table[0][0] = "";
    table[0][1] = "";
    table[0][2] = "";
    table[0][3] = "";
    StringBuffer buf = new StringBuffer();
    FieldPosition test = new FieldPosition(0);

    for (int j = 0; j < sampleSet.getSample(0).getFunction().getFittingParams().length; j++) {
      table[j + 1][0] = "parameter " + j;
      buf = new StringBuffer();
      table[j + 1][1] = nf
          .format(sampleSet.getSample(0).getFunction().getFittingParams()[j], buf, test).toString();
      buf = new StringBuffer();
      table[j + 1][2] = nf.format(parameterStandardDeviation[j], buf, test).toString();
      buf = new StringBuffer();
      table[j + 1][3] = nf.format(parameterUncertainty[j], buf, test).toString();
      table[j + 1][4] = "[-]";
    }

    int numb = sampleSet.getSample(0).getFunction().getFittingParams().length;

    table[numb + 2][0] = "Number Of Data Points ";
    buf = new StringBuffer();
    nf.applyPattern("#");
    table[numb + 2][1] = nf.format(sampleSet.getLength(), buf, test).toString();

    table[numb + 3][0] = "Shi-Square ";
    buf = new StringBuffer();
    nf.applyPattern("#.###E0");
    table[numb + 3][1] = nf.format(chiSquare, buf, test).toString();
    table[numb + 4][0] = "Abs.rel.dev (%) ";
    buf = new StringBuffer();
    table[numb + 4][1] = nf.format(absStdDev, buf, test).toString();
    table[numb + 5][0] = "Bias.dev (%) ";
    buf = new StringBuffer();
    table[numb + 5][1] = nf.format(biasdev, buf, test).toString();
    table[numb + 6][0] = "Goodnes Of Fit (0-1)";
    buf = new StringBuffer();
    nf.applyPattern("#.###E0");
    table[numb + 6][1] = nf.format(incompleteGammaComplemented, buf, test).toString();

    JTable Jtab = new JTable(table, names);
    JScrollPane scrollpane = new JScrollPane(Jtab);
    dialogContentPane.add(scrollpane);
    dialog.pack();
    dialog.setVisible(true);
    // Matrix te = parameterStdDevMatrix.copy();
    // te.display("Std. Deviation" ,5);
    // coVarianceMatrix.display("CoVariance",5);
    // parameterCorrelationMatrix.display("Correlation",5);
    displayMatrix(coVarianceMatrix, "CoVariance", 5);
    displayMatrix(parameterCorrelationMatrix, "Correlation", 5);
    // parameterUncertaintyMatrix.display("Param. uncertatnty",5);
  }

  /**
   * <p>
   * displayMatrix.
   * </p>
   *
   * @param coVarianceMatrix a {@link Jama.Matrix} object
   * @param name a {@link java.lang.String} object
   * @param d a int
   */
  @ExcludeFromJacocoGeneratedReport
  public void displayMatrix(Matrix coVarianceMatrix, String name, int d) {
    int m = coVarianceMatrix.getRowDimension();
    int n = coVarianceMatrix.getColumnDimension();
    String[] names = new String[m];
    StringBuffer buf = new StringBuffer();
    DecimalFormat form = new DecimalFormat();
    form.setMinimumIntegerDigits(1);
    form.setMaximumFractionDigits(d);
    form.setMinimumFractionDigits(d);
    form.setGroupingUsed(false);
    form.applyPattern("#.##E0");
    FieldPosition test = new FieldPosition(0);
    String[][] X = new String[m][n];
    for (int i = 0; i < m; i++) {
      names[i] = name + " " + i;
      for (int j = 0; j < n; j++) {
        buf = new StringBuffer();
        X[i][j] = form.format(coVarianceMatrix.get(i, j), buf, test).toString();
      }
    }

    JDialog dialog = new JDialog(new JFrame(), name);
    Container dialogContentPane = dialog.getContentPane();
    dialogContentPane.setLayout(new FlowLayout());
    JTable Jtab = new JTable(X, names);
    JScrollPane scrollpane = new JScrollPane(Jtab);
    dialogContentPane.add(scrollpane);
    dialog.pack();
    dialog.setVisible(true);
  }

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
  public void writeToTextFile(String name) {
    neqsim.datapresentation.filehandling.TextFile tempfile =
        new neqsim.datapresentation.filehandling.TextFile();
    tempfile.setOutputFileName(name);
    tempfile.setValues(valTable);
    tempfile.createFile();
  }

  /**
   * <p>
   * displaySimple.
   * </p>
   */
  @ExcludeFromJacocoGeneratedReport
  public void displaySimple() {
    calcAbsDev();
    try {
      // displayGraph();
    } catch (Exception ex) {
      System.out.println("could not display graph");
      logger.error(ex.getMessage(), ex);
    }
    try {
      displayResult();
    } catch (Exception ex) {
      System.out.println("could not display graph");
      logger.error(ex.getMessage(), ex);
    }
    try {
      displayValues();
    } catch (Exception ex) {
      System.out.println("could not display graph");
      logger.error(ex.getMessage(), ex);
    }
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayCurveFit() {
    calcAbsDev();
    try {
      // displayGraph();
    } catch (Exception ex) {
      System.out.println("could not display graph");
      logger.error(ex.getMessage(), ex);
    }
    try {
      displayResult();
    } catch (Exception ex) {
      System.out.println("could not display graph");
      logger.error(ex.getMessage(), ex);
    }
    try {
      displayValues();
    } catch (Exception ex) {
      System.out.println("could not display graph");
      logger.error(ex.getMessage(), ex);
    }
    try {
      displayResultWithDeviation();
    } catch (Exception ex) {
      System.out.println("could not calc deviation");
      logger.error(ex.getMessage(), ex);
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
