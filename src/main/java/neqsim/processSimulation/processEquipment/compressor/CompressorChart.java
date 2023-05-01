package neqsim.processSimulation.processEquipment.compressor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.fitting.PolynomialCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoints;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * <p>
 * CompressorChart class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class CompressorChart implements CompressorChartInterface, java.io.Serializable {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(CompressorChart.class);

  ArrayList<CompressorCurve> chartValues = new ArrayList<CompressorCurve>();
  private SurgeCurve surgeCurve = new SurgeCurve();
  private StoneWallCurve stoneWallCurve = new StoneWallCurve();
  boolean isSurge = false;
  double maxSpeedCurve = 0;
  double minSpeedCurve = 1e10;
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
  double[] speed;
  double[][] flow;
  double[][] head;
  double[][] polytropicEfficiency;
  double[][] redflow;
  double[][] redhead;
  double[][] redpolytropicEfficiency;

  /**
   * <p>
   * Constructor for CompressorChart.
   * </p>
   */
  public CompressorChart() {}

  /** {@inheritDoc} */
  @Override
  public void addCurve(double speed, double[] flow, double[] head, double[] polytropicEfficiency) {
    CompressorCurve curve = new CompressorCurve(speed, flow, head, polytropicEfficiency);
    chartValues.add(curve);
  }

  /** {@inheritDoc} */
  @Override
  public void setCurves(double[] chartConditions, double[] speed, double[][] flow, double[][] head,
      double[][] polyEff) {
    this.speed = speed;
    this.head = head;
    this.polytropicEfficiency = polyEff;
    this.flow = flow;

    this.redhead = new double[head.length][head[0].length];
    this.redpolytropicEfficiency = new double[polyEff.length][polyEff[0].length];
    this.redflow = new double[flow.length][flow[0].length];

    for (int i = 0; i < speed.length; i++) {
      if (speed[i] > maxSpeedCurve) {
        maxSpeedCurve = speed[i];
      }
      if (speed[i] < minSpeedCurve) {
        minSpeedCurve = speed[i];
      }
      CompressorCurve curve = new CompressorCurve(speed[i], flow[i], head[i], polyEff[i]);
      chartValues.add(curve);
      for (int j = 0; j < flow[i].length; j++) {
        redflow[i][j] = flow[i][j] / speed[i];
        redpolytropicEfficiency[i][j] = polyEff[i][j];
        redhead[i][j] = head[i][j] / speed[i] / speed[i];
        reducedHeadFitter.add(redflow[i][j], redhead[i][j]);
        reducedPolytropicEfficiencyFitter.add(redflow[i][j], redpolytropicEfficiency[i][j]);
        double flowFanLaw = flow[i][j] * speed[i] / speed[0];
        // todo: MLLU: not correct. speed[0] should be the requested speed
        fanLawCorrectionFitter.add(speed[i] / speed[0], flow[i][j] / flowFanLaw);
      }
    }

    referenceSpeed = (maxSpeedCurve + minSpeedCurve) / 2.0;

    PolynomialCurveFitter fitter = PolynomialCurveFitter.create(2);

    reducedHeadFitterFunc = new PolynomialFunction(fitter.fit(reducedHeadFitter.toList()));
    reducedPolytropicEfficiencyFunc =
        new PolynomialFunction(fitter.fit(reducedPolytropicEfficiencyFitter.toList()));
    fanLawCorrectionFunc = new PolynomialFunction(fitter.fit(fanLawCorrectionFitter.toList()));
    setUseCompressorChart(true);
  }

  /**
   * <p>
   * fitReducedCurve.
   * </p>
   */
  public void fitReducedCurve() {}

  /** {@inheritDoc} */
  @Override
  public double getPolytropicHead(double flow, double speed) {
    // double flowCorrection = fanLawCorrectionFunc.value(speed/referenceSpeed);
    // System.out.println("flow correction " + flowCorrection);
    return reducedHeadFitterFunc.value(flow / speed) * speed * speed;
    // return reducedHeadFitterFunc.value(flowCorrection * flow / speed) * speed *
    // speed;
  }

  /** {@inheritDoc} */
  @Override
  public double getPolytropicEfficiency(double flow, double speed) {
    // double flowCorrection = fanLawCorrectionFunc.value(speed/referenceSpeed);
    return reducedPolytropicEfficiencyFunc.value(flow / speed);
    // return reducedPolytropicEfficiencyFunc.value(reducedHeadFitterFunc*flow /
    // speed);
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
        new neqsim.dataPresentation.JFreeChart.graph2b(flow, polytropicEfficiency,
            Arrays.stream(speed).mapToObj(String::valueOf).toArray(String[]::new), "eff vs flow",
            "flow", "eff");
    graph2.setVisible(true);
    neqsim.dataPresentation.JFreeChart.graph2b graph3 =
        new neqsim.dataPresentation.JFreeChart.graph2b(redflow, redhead,
            Arrays.stream(speed).mapToObj(String::valueOf).toArray(String[]::new),
            "red head vs red flow", "red flow", "red head");
    graph3.setVisible(true);
    neqsim.dataPresentation.JFreeChart.graph2b graph4 =
        new neqsim.dataPresentation.JFreeChart.graph2b(redflow, polytropicEfficiency,
            Arrays.stream(speed).mapToObj(String::valueOf).toArray(String[]::new),
            "red eff vs red dflow", "red flow", "red eff");
    graph4.setVisible(true);
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + Arrays.hashCode(chartConditions);
    result = prime * result + Arrays.deepHashCode(flow);
    result = prime * result + Arrays.deepHashCode(head);
    result = prime * result + Arrays.deepHashCode(polytropicEfficiency);
    result = prime * result + Arrays.deepHashCode(redflow);
    result = prime * result + Arrays.deepHashCode(redhead);
    result = prime * result + Arrays.deepHashCode(redpolytropicEfficiency);
    result = prime * result + Arrays.hashCode(speed);
    result = prime * result + Objects.hash(chartValues, fanLawCorrectionFunc, headUnit, isStoneWall,
        isSurge, maxSpeedCurve, minSpeedCurve, reducedHeadFitterFunc,
        reducedPolytropicEfficiencyFunc, refMW, refPressure, refTemperature, refZ, referenceSpeed,
        stoneWallCurve, surgeCurve, useCompressorChart, useRealKappa);
    // result = prime * result + Objects.hash(fanLawCorrectionFitter,
    // reducedFlowFitter,reducedHeadFitter,reducedPolytropicEfficiencyFitter )
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    CompressorChart other = (CompressorChart) obj;
    return Arrays.equals(chartConditions, other.chartConditions)
        && Objects.equals(chartValues, other.chartValues)
        && Objects.equals(fanLawCorrectionFunc, other.fanLawCorrectionFunc)
        && Arrays.deepEquals(flow, other.flow) && Arrays.deepEquals(head, other.head)
        && Objects.equals(headUnit, other.headUnit) && isStoneWall == other.isStoneWall
        && isSurge == other.isSurge
        && Double.doubleToLongBits(maxSpeedCurve) == Double.doubleToLongBits(other.maxSpeedCurve)
        && Double.doubleToLongBits(minSpeedCurve) == Double.doubleToLongBits(other.minSpeedCurve)
        && Arrays.deepEquals(polytropicEfficiency, other.polytropicEfficiency)
        && Arrays.deepEquals(redflow, other.redflow) && Arrays.deepEquals(redhead, other.redhead)
        && Arrays.deepEquals(redpolytropicEfficiency, other.redpolytropicEfficiency)
        && Objects.equals(reducedHeadFitterFunc, other.reducedHeadFitterFunc)
        && Objects.equals(reducedPolytropicEfficiencyFunc, other.reducedPolytropicEfficiencyFunc)
        && Double.doubleToLongBits(refMW) == Double.doubleToLongBits(other.refMW)
        && Double.doubleToLongBits(refPressure) == Double.doubleToLongBits(other.refPressure)
        && Double.doubleToLongBits(refTemperature) == Double.doubleToLongBits(other.refTemperature)
        && Double.doubleToLongBits(refZ) == Double.doubleToLongBits(other.refZ)
        && Double.doubleToLongBits(referenceSpeed) == Double.doubleToLongBits(other.referenceSpeed)
        && Arrays.equals(speed, other.speed) && Objects.equals(stoneWallCurve, other.stoneWallCurve)
        && Objects.equals(surgeCurve, other.surgeCurve)
        && useCompressorChart == other.useCompressorChart && useRealKappa == other.useRealKappa;
    // && Objects.equals(fanLawCorrectionFitter, other.fanLawCorrectionFitter)
    // && Objects.equals(reducedFlowFitter, other.reducedFlowFitter)
    // && Objects.equals(reducedHeadFitter, other.reducedHeadFitter)
    // && Objects.equals(reducedPolytropicEfficiencyFitter,
    // other.reducedPolytropicEfficiencyFitter)
  }
}
