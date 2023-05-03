package neqsim.processSimulation.processEquipment.pump;

import java.util.ArrayList;
import java.util.Arrays;
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.fitting.PolynomialCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoints;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * <p>
 * PumpChart class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class PumpChart implements PumpChartInterface, java.io.Serializable {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(PumpChart.class);

  ArrayList<PumpCurve> chartValues = new ArrayList<PumpCurve>();
  boolean isSurge = false;
  double maxSpeedCurve = 0;
  double minSpeedCurve = 1e10;
  double refMW;
  private String headUnit = "meter";
  private boolean usePumpChart = false;
  double refTemperature;
  double refPressure;
  double referenceSpeed = 1000.0;
  double refZ;
  private boolean useRealKappa = false;
  double[] chartConditions = null;
  final WeightedObservedPoints reducedHeadFitter = new WeightedObservedPoints();
  final WeightedObservedPoints reducedFlowFitter = new WeightedObservedPoints();
  final WeightedObservedPoints fanLawCorrectionFitter = new WeightedObservedPoints();
  final WeightedObservedPoints reducedEfficiencyFitter = new WeightedObservedPoints();
  PolynomialFunction reducedHeadFitterFunc = null;
  PolynomialFunction reducedEfficiencyFunc = null;
  PolynomialFunction fanLawCorrectionFunc = null;
  double[] speed;
  double[][] flow;
  double[][] head;
  double[][] efficiency;
  double[][] redflow;
  double[][] redhead;
  double[][] redEfficiency;

  /**
   * <p>
   * Constructor for PumpChart.
   * </p>
   */
  public PumpChart() {}

  /** {@inheritDoc} */
  @Override
  public void addCurve(double speed, double[] flow, double[] head, double[] efficiency) {
    PumpCurve curve = new PumpCurve(speed, flow, head, efficiency);
    chartValues.add(curve);
  }

  /** {@inheritDoc} */
  @Override
  public void setCurves(double[] chartConditions, double[] speed, double[][] flow, double[][] head,
      double[][] efficiency) {
    this.speed = speed;
    this.head = head;
    this.efficiency = efficiency;
    this.flow = flow;

    this.redhead = new double[head.length][head[0].length];
    this.redEfficiency = new double[efficiency.length][efficiency[0].length];
    this.redflow = new double[flow.length][flow[0].length];

    for (int i = 0; i < speed.length; i++) {
      if (speed[i] > maxSpeedCurve) {
        maxSpeedCurve = speed[i];
      }
      if (speed[i] < minSpeedCurve) {
        minSpeedCurve = speed[i];
      }
      PumpCurve curve = new PumpCurve(speed[i], flow[i], head[i], efficiency[i]);
      chartValues.add(curve);
      for (int j = 0; j < flow[i].length; j++) {
        redflow[i][j] = flow[i][j] / speed[i];
        redEfficiency[i][j] = efficiency[i][j];
        redhead[i][j] = head[i][j] / speed[i] / speed[i];
        reducedHeadFitter.add(redflow[i][j], redhead[i][j]);
        reducedEfficiencyFitter.add(redflow[i][j], redEfficiency[i][j]);
        // todo: MLLU: not correct. speed[0] should be the requested speed
        double flowFanLaw = flow[i][j] * speed[i] / speed[0];
        fanLawCorrectionFitter.add(speed[i] / speed[0], flow[i][j] / flowFanLaw);
      }
    }

    referenceSpeed = (maxSpeedCurve + minSpeedCurve) / 2.0;

    PolynomialCurveFitter fitter = PolynomialCurveFitter.create(2);

    reducedHeadFitterFunc = new PolynomialFunction(fitter.fit(reducedHeadFitter.toList()));
    reducedEfficiencyFunc = new PolynomialFunction(fitter.fit(reducedEfficiencyFitter.toList()));
    fanLawCorrectionFunc = new PolynomialFunction(fitter.fit(fanLawCorrectionFitter.toList()));
    setUsePumpChart(true);
  }

  /**
   * <p>
   * fitReducedCurve.
   * </p>
   */
  public void fitReducedCurve() {}

  /** {@inheritDoc} */
  @Override
  public double getHead(double flow, double speed) {
    return reducedHeadFitterFunc.value(flow / speed) * speed * speed;
  }

  /** {@inheritDoc} */
  @Override
  public double getEfficiency(double flow, double speed) {
    return reducedEfficiencyFunc.value(flow / speed);
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
    double oldhead = getHead(flow, oldspeed);
    double olderror = oldhead - head;
    do {
      iter++;
      newhead = getHead(flow, newspeed);
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
   * efficiency.
   * </p>
   *
   * @param flow a double
   * @param speed a double
   * @return a double
   */
  public double efficiency(double flow, double speed) {
    return 100.0;
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

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  public static void main(String[] args) {
    SystemInterface testFluid = new SystemSrkEos(298.15, 50.0);
    testFluid.addComponent("water", 1.0);
    testFluid.setTemperature(20.0, "C");
    testFluid.setPressure(1.0, "bara");
    testFluid.setTotalFlowRate(1000.0, "kg/hr");

    Stream stream_1 = new Stream("Stream1", testFluid);

    Pump pump1 = new Pump("pump1", stream_1);
    pump1.setOutletPressure(100.0);
    // comp1.getAntiSurge().setActive(true);
    pump1.setSpeed(12918);

    double[] chartConditions = new double[] {0.3, 1.0, 1.0, 1.0};
    // double[] speed = new double[] { 1000.0, 2000.0, 3000.0, 4000.0 };
    // double[][] flow = new double[][] { { 453.2, 600.0, 750.0, 800.0 }, { 453.2,
    // 600.0, 750.0, 800.0
    // }, { 453.2, 600.0, 750.0, 800.0 }, { 453.2, 600.0, 750.0, 800.0 } };
    // double[][] head = new double[][] { { 10000.0, 9000.0, 8000.0, 7500.0 }, {
    // 10000.0, 9000.0, 8000.0, 7500.0 }, { 10000.0, 9000.0, 8000.0, 7500.0 }, {
    // 10000.0, 9000.0, 8000.0, 7500.0 } };
    // double[][] polyEff = new double[][] { {
    // 90.0, 91.0, 89.0, 88.0 }, { 90.0, 91.0, 89.0, 88.0 }, { 90.0, 91.0, 89.0,
    // 88.1 }, { 90.0, 91.0, 89.0, 88.1 } };

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
            {32.192, 31.1756, 29.1329, 26.833, 23.8909, 21.3324, 18.7726, 16.3403},};
    double[][] efficiency = new double[][] {
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

    pump1.getPumpChart().setCurves(chartConditions, speed, flow, head, efficiency);
    pump1.getPumpChart().setHeadUnit("kJ/kg");

    neqsim.processSimulation.processSystem.ProcessSystem operations =
        new neqsim.processSimulation.processSystem.ProcessSystem();
    operations.add(stream_1);
    operations.add(pump1);
    operations.run();

    System.out.println("pump power " + pump1.getPower());
  }

  /** {@inheritDoc} */
  @Override
  public boolean isUsePumpChart() {
    return usePumpChart;
  }

  /** {@inheritDoc} */
  @Override
  public void setUsePumpChart(boolean usePumpChart) {
    this.usePumpChart = usePumpChart;
  }

  /** {@inheritDoc} */
  @Override
  public String getHeadUnit() {
    return headUnit;
  }

  /** {@inheritDoc} */
  @Override
  public void setHeadUnit(String headUnit) {
    this.headUnit = headUnit;
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

  /** {@inheritDoc} */
  @Override
  public void plot() {
    neqsim.dataPresentation.JFreeChart.graph2b graph =
        new neqsim.dataPresentation.JFreeChart.graph2b(flow, head,
            Arrays.stream(speed).mapToObj(String::valueOf).toArray(String[]::new), "head vs flow",
            "flow", "head");
    graph.setVisible(true);
    neqsim.dataPresentation.JFreeChart.graph2b graph2 =
        new neqsim.dataPresentation.JFreeChart.graph2b(flow, efficiency,
            Arrays.stream(speed).mapToObj(String::valueOf).toArray(String[]::new), "eff vs flow",
            "flow", "eff");
    graph2.setVisible(true);
    neqsim.dataPresentation.JFreeChart.graph2b graph3 =
        new neqsim.dataPresentation.JFreeChart.graph2b(redflow, redhead,
            Arrays.stream(speed).mapToObj(String::valueOf).toArray(String[]::new),
            "red head vs red flow", "red flow", "red head");
    graph3.setVisible(true);
    neqsim.dataPresentation.JFreeChart.graph2b graph4 =
        new neqsim.dataPresentation.JFreeChart.graph2b(redflow, efficiency,
            Arrays.stream(speed).mapToObj(String::valueOf).toArray(String[]::new),
            "red eff vs red dflow", "red flow", "red eff");
    graph4.setVisible(true);
  }
}
