package neqsim.processSimulation.processEquipment.compressor;

import java.util.ArrayList;
import java.util.Arrays;
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;
import org.apache.commons.math3.fitting.WeightedObservedPoints;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * This class is an implementation of the compressor chart class that uses Fan laws and "double"
 * interpolation to navigate the compressor map (as opposed to the standard class using reduced
 * variables according to Fan laws).
 *
 * @author asmund
 * @version $Id: $Id
 */
public class CompressorChartAlternativeMapLookup
    implements CompressorChartInterface, java.io.Serializable {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(CompressorChart.class);

  ArrayList<CompressorCurve> chartValues = new ArrayList<CompressorCurve>();
  ArrayList<Double> chartSpeeds = new ArrayList<Double>();
  private SurgeCurve surgeCurve = new SurgeCurve();
  private StoneWallCurve stoneWallCurve = new StoneWallCurve();
  boolean isSurge = false;
  boolean isStoneWall = false;
  double refMW;
  private String headUnit = "meter";
  private boolean useCompressorChart = false;
  double refTemperature;
  double refPressure;
  double referenceSpeed = 1000.0;
  double refZ;
  private boolean useRealKappa = false;
  double[] chartConditions = null;
  final WeightedObservedPoints reducedHeadFitter = new WeightedObservedPoints();
  final WeightedObservedPoints reducedFlowFitter = new WeightedObservedPoints();
  final WeightedObservedPoints fanLawCorrectionFitter = new WeightedObservedPoints();
  final WeightedObservedPoints reducedPolytropicEfficiencyFitter = new WeightedObservedPoints();
  PolynomialFunction reducedHeadFitterFunc = null;
  PolynomialFunction reducedPolytropicEfficiencyFunc = null;
  PolynomialFunction fanLawCorrectionFunc = null;
  double gearRatio = 1.0;

  /**
   * <p>
   * Constructor for CompressorChartAlternativeMapLookup.
   * </p>
   */
  public CompressorChartAlternativeMapLookup() {}

  /** {@inheritDoc} */
  @Override
  public void addCurve(double speed, double[] flow, double[] head, double[] polytropicEfficiency) {
    CompressorCurve curve = new CompressorCurve(speed, flow, head, polytropicEfficiency);
    chartValues.add(curve);
    chartSpeeds.add(speed);
  }

  /** {@inheritDoc} */
  @Override
  public void setCurves(double[] chartConditions, double[] speed, double[][] flow, double[][] head,
      double[][] polyEff) {
    for (int i = 0; i < speed.length; i++) {
      CompressorCurve curve = new CompressorCurve(speed[i], flow[i], head[i], polyEff[i]);
      chartValues.add(curve);
      chartSpeeds.add(speed[i]);
    }

    setUseCompressorChart(true);
  }

  /**
   * <p>
   * getClosestRefSpeeds.
   * </p>
   *
   * @param speed a double
   * @return a {@link java.util.ArrayList} object
   */
  public ArrayList<Double> getClosestRefSpeeds(double speed) {
    ArrayList<Double> closestRefSpeeds = new ArrayList<Double>();
    Double[] speedArray = new Double[chartSpeeds.size()];
    speedArray = chartSpeeds.toArray(speedArray);
    Arrays.sort(speedArray);
    boolean speedOnRef = false;

    for (int i = 0; i < chartSpeeds.size(); i++) {
      double s = chartValues.get(i).speed;
      if (speed == s) { // speed is equal to a reference speed
        closestRefSpeeds.add(s);
        speedOnRef = true;
      }
    }

    if (!speedOnRef) {
      int pos = bisect_left(speedArray, speed);
      if (pos == 0) { // speed is lower than the lowest reference speed
        closestRefSpeeds.add(speedArray[0]);
      } else if (pos == chartSpeeds.size()) {
        // speed is higher than the highest reference speed
        closestRefSpeeds.add(speedArray[speedArray.length - 1]);
      } else { // speed is in between two reference speeds
        closestRefSpeeds.add(speedArray[pos - 1]);
        closestRefSpeeds.add(speedArray[pos]);
      }
    }
    return closestRefSpeeds;
  }

  /** {@inheritDoc} */
  @Override
  public double getPolytropicHead(double flow, double speed) {
    ArrayList<Double> closestRefSpeeds = new ArrayList<Double>();
    closestRefSpeeds = getClosestRefSpeeds(speed);
    double s;
    // double speedRatio;
    ArrayList<Double> tempHeads = new ArrayList<Double>();
    SplineInterpolator asi = new SplineInterpolator();

    for (int i = 0; i < closestRefSpeeds.size(); i++) {
      s = closestRefSpeeds.get(i);
      // speedRatio = speed * gearRatio / s;
      PolynomialSplineFunction psf =
          asi.interpolate(getCurveAtRefSpeed(s).flow, getCurveAtRefSpeed(s).head);
      tempHeads.add(psf.value(flow));
    }

    double sum = 0.0;
    for (int i = 0; i < tempHeads.size(); i++) {
      sum += tempHeads.get(i);
    }
    return sum / tempHeads.size();
  }

  /** {@inheritDoc} */
  @Override
  public double getPolytropicEfficiency(double flow, double speed) {
    ArrayList<Double> closestRefSpeeds = new ArrayList<Double>();
    closestRefSpeeds = getClosestRefSpeeds(speed);
    double s;
    ArrayList<Double> tempEffs = new ArrayList<Double>();
    SplineInterpolator asi = new SplineInterpolator();

    for (int i = 0; i < closestRefSpeeds.size(); i++) {
      s = closestRefSpeeds.get(i);
      PolynomialSplineFunction psf =
          asi.interpolate(getCurveAtRefSpeed(s).flow, getCurveAtRefSpeed(s).polytropicEfficiency);
      tempEffs.add(psf.value(flow));
    }

    double sum = 0.0;
    for (int i = 0; i < tempEffs.size(); i++) {
      sum += tempEffs.get(i);
    }
    return sum / tempEffs.size();
  }

  /**
   * <p>
   * addSurgeCurve.
   * </p>
   *
   * @param flow an array of {@link double} objects
   * @param head an array of {@link double} objects
   */
  public void addSurgeCurve(double[] flow, double[] head) {
    surgeCurve = new SurgeCurve(flow, head);
  }

  /**
   * <p>
   * getCurveAtRefSpeed.
   * </p>
   *
   * @param refSpeed a double
   * @return a {@link neqsim.processSimulation.processEquipment.compressor.CompressorCurve} object
   */
  public CompressorCurve getCurveAtRefSpeed(double refSpeed) {
    for (int i = 0; i < chartValues.size(); i++) {
      CompressorCurve c = chartValues.get(i);
      if (c.speed == refSpeed) {
        return c;
      }
    }
    String msg = "Does not match any speed in the chart.";
    neqsim.util.exception.InvalidInputException ex =
        new neqsim.util.exception.InvalidInputException(this, "getCurveAtRefSpeed", "refSpeed",
            msg);
    throw new RuntimeException(ex);
  }

  /**
   * <p>
   * Getter for the field <code>gearRatio</code>.
   * </p>
   *
   * @return a double
   */
  public double getGearRatio() {
    return gearRatio;
  }

  /**
   * <p>
   * Setter for the field <code>gearRatio</code>.
   * </p>
   *
   * @param GR a double
   */
  public void setGearRatio(double GR) {
    gearRatio = GR;
  }

  /**
   * <p>
   * polytropicEfficiency.
   * </p>
   *
   * @param flow a double
   * @param speed a double
   * @return a double
   */
  public double polytropicEfficiency(double flow, double speed) {
    return 100.0;
  }

  /** {@inheritDoc} */
  @Override
  public int getSpeed(double flow, double head) {
    int iter = 1;
    double error = 1.0;
    double derrordspeed = 1.0;
    double newspeed = referenceSpeed;
    double newhead = 0.0;
    double oldspeed = newspeed + 1.0;
    double oldhead = getPolytropicHead(flow, oldspeed);
    double olderror = oldhead - head;
    do {
      iter++;
      newhead = getPolytropicHead(flow, newspeed);
      error = newhead - head;
      derrordspeed = (error - olderror) / (newspeed - oldspeed);
      newspeed -= error / derrordspeed;
      // System.out.println("speed " + newspeed);
    } while (Math.abs(error) > 1e-6 && iter < 100);

    // change speed to minimize
    // Math.abs(head - reducedHeadFitterFunc.value(flow / speed) * speed * speed);
    return (int) Math.round(newspeed);
  }

  /**
   * <p>
   * checkSurge1.
   * </p>
   *
   * @param flow a double
   * @param head a double
   * @return a boolean
   */
  public boolean checkSurge1(double flow, double head) {
    return false;
  }

  /**
   * <p>
   * checkSurge2.
   * </p>
   *
   * @param flow a double
   * @param speed a double
   * @return a boolean
   */
  public boolean checkSurge2(double flow, double speed) {
    return false;
  }

  /**
   * <p>
   * checkStoneWall.
   * </p>
   *
   * @param flow a double
   * @param speed a double
   * @return a boolean
   */
  public boolean checkStoneWall(double flow, double speed) {
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public void setReferenceConditions(double refMW, double refTemperature, double refPressure,
      double refZ) {
    this.refMW = refMW;
    this.refTemperature = refTemperature;
    this.refPressure = refPressure;
    this.refZ = refZ;
  }

  /** {@inheritDoc} */
  @Override
  public SurgeCurve getSurgeCurve() {
    return surgeCurve;
  }

  /** {@inheritDoc} */
  @Override
  public void setSurgeCurve(SurgeCurve surgeCurve) {
    this.surgeCurve = surgeCurve;
  }

  /** {@inheritDoc} */
  @Override
  public StoneWallCurve getStoneWallCurve() {
    return stoneWallCurve;
  }

  /** {@inheritDoc} */
  @Override
  public void setStoneWallCurve(StoneWallCurve stoneWallCurve) {
    this.stoneWallCurve = stoneWallCurve;
  }

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  public static void main(String[] args) {
    SystemInterface testFluid = new SystemSrkEos(298.15, 50.0);

    // testFluid.addComponent("methane", 1.0);
    // testFluid.setMixingRule(2);
    // testFluid.setTotalFlowRate(0.635, "MSm3/day");

    testFluid.addComponent("nitrogen", 1.205);
    testFluid.addComponent("CO2", 1.340);
    testFluid.addComponent("methane", 87.974);
    testFluid.addComponent("ethane", 5.258);
    testFluid.addComponent("propane", 3.283);
    testFluid.addComponent("i-butane", 0.082);
    testFluid.addComponent("n-butane", 0.487);
    testFluid.addComponent("i-pentane", 0.056);
    testFluid.addComponent("n-pentane", 0.053);
    testFluid.setMixingRule(2);
    testFluid.setMultiPhaseCheck(true);

    testFluid.setTemperature(24.0, "C");
    testFluid.setPressure(48.0, "bara");
    // testFluid.setTotalFlowRate(3.635, "MSm3/day");
    testFluid.setTotalFlowRate(5.4, "MSm3/day");

    Stream stream_1 = new Stream("Stream1", testFluid);
    Compressor comp1 = new Compressor("cmp1", true);
    comp1.setInletStream(stream_1);
    comp1.setUsePolytropicCalc(true);
    // comp1.getAntiSurge().setActive(true);
    comp1.setSpeed(11918);

    double[] chartConditions = new double[] {0.3, 1.0, 1.0, 1.0};
    /*
     * double[] speed = new double[] { 1000.0, 2000.0, 3000.0, 4000.0 }; double[][] flow = new
     * double[][] { { 453.2, 600.0, 750.0, 800.0 }, { 453.2, 600.0, 750.0, 800.0 }, { 453.2, 600.0,
     * 750.0, 800.0 }, { 453.2, 600.0, 750.0, 800.0 } }; double[][] head = new double[][] { {
     * 10000.0, 9000.0, 8000.0, 7500.0 }, { 10000.0, 9000.0, 8000.0, 7500.0 }, { 10000.0, 9000.0,
     * 8000.0, 7500.0 }, { 10000.0, 9000.0, 8000.0, 7500.0 } }; double[][] polyEff = new double[][]
     * { { 90.0, 91.0, 89.0, 88.0 }, { 90.0, 91.0, 89.0, 88.0 }, { 90.0, 91.0, 89.0, 88.1 }, { 90.0,
     * 91.0, 89.0, 88.1 } };
     */

    double[] speed = new double[] {12913, 12298, 11683, 11098, 10453, 9224, 8609, 8200};
    double[][] flow = new double[][] {
        {2789.1285, 3174.0375, 3689.2288, 4179.4503, 4570.2768, 4954.7728, 5246.0329, 5661.0331},
        {2571.1753, 2943.7254, 3440.2675, 3837.4448, 4253.0898, 4668.6643, 4997.1926, 5387.4952},
        {2415.3793, 2763.0706, 3141.7095, 3594.7436, 4047.6467, 4494.1889, 4853.7353, 5138.7858},
        {2247.2043, 2799.7342, 3178.3428, 3656.1551, 4102.778, 4394.1591, 4648.3224, 4840.4998},
        {2072.8397, 2463.9483, 2836.4078, 3202.5266, 3599.6333, 3978.0203, 4257.0022, 4517.345},
        {1835.9552, 2208.455, 2618.1322, 2940.8034, 3244.7852, 3530.1279, 3753.3738, 3895.9746},
        {1711.3386, 1965.8848, 2356.9431, 2685.9247, 3008.5154, 3337.2855, 3591.5092},
        {1636.5807, 2002.8708, 2338.0319, 2642.1245, 2896.4894, 3113.6264, 3274.8764, 3411.2977}};
    double[][] head =
        new double[][] {{80.0375, 78.8934, 76.2142, 71.8678, 67.0062, 60.6061, 53.0499, 39.728},
            {72.2122, 71.8369, 68.9009, 65.8341, 60.7167, 54.702, 47.2749, 35.7471},
            {65.1576, 64.5253, 62.6118, 59.1619, 54.0455, 47.0059, 39.195, 31.6387},
            {58.6154, 56.9627, 54.6647, 50.4462, 44.4322, 38.4144, 32.9084, 28.8109},
            {52.3295, 51.0573, 49.5283, 46.3326, 42.3685, 37.2502, 31.4884, 25.598},
            {40.6578, 39.6416, 37.6008, 34.6603, 30.9503, 27.1116, 23.2713, 20.4546},
            {35.2705, 34.6359, 32.7228, 31.0645, 27.0985, 22.7482, 18.0113},
            {32.192, 31.1756, 29.1329, 26.833, 23.8909, 21.3324, 18.7726, 16.3403}};
    double[][] polyEff = new double[][] {
        {77.2452238409573, 79.4154186459363, 80.737960012489, 80.5229826589649, 79.2210931638144,
            75.4719133864634, 69.6034181197298, 58.7322388482707},
        {77.0107837113504, 79.3069974136389, 80.8941189021135, 80.7190194665918, 79.5313242980328,
            75.5912622896367, 69.6846136362097, 60.0043057990909},
        {77.0043065299874, 79.1690958847856, 80.8038169975675, 80.6543975614197, 78.8532389102705,
            73.6664774270613, 66.2735600426727, 57.671664571658},
        {77.0716623789093, 80.4629750233093, 81.1390811169072, 79.6374242667478, 75.380928428817,
            69.5332969549779, 63.7997587622339, 58.8120614497758},
        {76.9705872525642, 79.8335492585324, 80.9468133671171, 80.5806471927835, 78.0462158225426,
            73.0403707523258, 66.5572286338589, 59.8624822515064},
        {77.5063036680357, 80.2056198362559, 81.0339108025933, 79.6085962687939, 76.3814534404405,
            70.8027503005902, 64.6437367160571, 60.5299349982342},
        {77.8175271586685, 80.065165942218, 81.0631362122632, 79.8955051771299, 76.1983240929369,
            69.289982774309, 60.8567149372229},
        {78.0924334304045, 80.9353551568667, 80.7904437766234, 78.8639325223295, 75.2170936751143,
            70.3105081673411, 65.5507568533569, 61.0391468300337}};

    // double[] chartConditions = new double[] { 0.3, 1.0, 1.0, 1.0 };
    // double[] speed = new double[] { 13402.0 };
    // double[][] flow = new double[][] { { 1050.0, 1260.0, 1650.0, 1950.0 } };
    // double[][] head = new double[][] { { 8555.0, 8227.0, 6918.0, 5223.0 } };
    // double[][] head = new double[][] { { 85.0, 82.0, 69.0, 52.0 } };
    // double[][] polyEff = new double[][] { { 66.8, 69.0, 66.4, 55.6 } };
    comp1.getCompressorChart().setCurves(chartConditions, speed, flow, head, polyEff);
    // comp1.getCompressorChart().setHeadUnit("kJ/kg");
    /*
     * double[] surgeflow = new double[] { 453.2, 550.0, 700.0, 800.0 }; double[] surgehead = new
     * double[] { 6000.0, 7000.0, 8000.0, 10000.0 };
     * comp1.getCompressorChart().getSurgeCurve().setCurve(chartConditions, surgeflow, surgehead);
     * 
     * double[] stoneWallflow = new double[] { 923.2, 950.0, 980.0, 1000.0 }; double[] stoneWallHead
     * = new double[] { 6000.0, 7000.0, 8000.0, 10000.0 };
     * comp1.getCompressorChart().getStoneWallCurve().setCurve(chartConditions, stoneWallflow,
     * stoneWallHead);
     */
    neqsim.processSimulation.processSystem.ProcessSystem operations =
        new neqsim.processSimulation.processSystem.ProcessSystem();
    operations.add(stream_1);
    operations.add(comp1);
    operations.run();
    operations.displayResult();

    System.out.println("power " + comp1.getPower());
    System.out
        .println("fraction in anti surge line " + comp1.getAntiSurge().getCurrentSurgeFraction());
    System.out.println("Polytropic head from curve:" + comp1.getPolytropicHead());
    System.out.println("Polytropic eff from curve:" + comp1.getPolytropicEfficiency() * 100.0);
    System.out.println("flow " + stream_1.getThermoSystem().getFlowRate("m3/hr"));
  }

  /** {@inheritDoc} */
  @Override
  public boolean isUseCompressorChart() {
    return useCompressorChart;
  }

  /** {@inheritDoc} */
  @Override
  public void setUseCompressorChart(boolean useCompressorChart) {
    this.useCompressorChart = useCompressorChart;
  }

  /** {@inheritDoc} */
  @Override
  public String getHeadUnit() {
    return headUnit;
  }

  /** {@inheritDoc} */
  @Override
  public void setHeadUnit(String headUnit) {
    if (headUnit.equals("meter") || headUnit.equals("kJ/kg")) {
      this.headUnit = headUnit;
    } else {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this,
          "setHeadUnit", "headUnit", "does not support value " + headUnit));
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean useRealKappa() {
    return useRealKappa;
  }

  /** {@inheritDoc} */
  @Override
  public void setUseRealKappa(boolean useRealKappa) {
    this.useRealKappa = useRealKappa;
  }

  /**
   * <p>
   * bisect_left.
   * </p>
   *
   * @param A an array of {@link java.lang.Double} objects
   * @param x a double
   * @return a int
   */
  public static int bisect_left(Double[] A, double x) {
    return bisect_left(A, x, 0, A.length);
  }

  /**
   * <p>
   * bisect_left.
   * </p>
   *
   * @param A an array of {@link java.lang.Double} objects
   * @param x a double
   * @param lo a int
   * @param hi a int
   * @return a int
   */
  public static int bisect_left(Double[] A, double x, int lo, int hi) {
    int N = A.length;
    if (N == 0) {
      return 0;
    }
    if (x < A[lo]) {
      return lo;
    }
    if (x > A[hi - 1]) {
      return hi;
    }
    for (;;) {
      if (lo + 1 == hi) {
        return x == A[lo] ? lo : (lo + 1);
      }
      int mi = (hi + lo) / 2;
      if (x <= A[mi]) {
        hi = mi;
      } else {
        lo = mi;
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void plot() {}
}
