package neqsim.process.equipment.compressor;

/**
 * Compressor chart generator.
 *
 * This class generates a compressor chart based on the provided compressor and the specified
 * generation option.
 *
 * Supports generating normal curves and alternative curves.
 *
 * @author Even Solbraa
 */
public class CompressorChartGenerator {
  private final Compressor compressor;

  /**
   * Constructor for CompressorChartGenerator.
   *
   * @param inpCompressor The compressor for which the chart is generated.
   */
  public CompressorChartGenerator(Compressor inpCompressor) {
    this.compressor = inpCompressor;
  }

  /**
   * Generates a compressor chart.
   *
   * @param generationOption Specifies how to generate the compressor chart. Options: "normal
   *        curves" or other types.
   * @return A {@link neqsim.process.equipment.compressor.CompressorChart} object.
   */
  public CompressorChart generateCompressorChart(String generationOption) {
    CompressorChart compChart = new CompressorChart();
    boolean isNormalCurves = "normal curves".equalsIgnoreCase(generationOption);

    // Initialize chart conditions
    double[] chartConditions = {compressor.getOutletStream().getFluid().getMolarMass("kg/mol")};

    // Initialize speed parameters
    double refSpeed = compressor.getSpeed();
    double[] speed = {refSpeed};
    double minSpeed = isNormalCurves ? refSpeed * 0.75 : refSpeed / 2.0;
    double maxSpeed = isNormalCurves ? refSpeed * 1.05 : refSpeed * 2.0;

    // Initialize flow parameters
    double refFlow = compressor.getInletStream().getFlowRate("m3/hr");
    double[][] flow = generateFlowData(refFlow, isNormalCurves);
    double minFlow = refFlow / 2.0;
    double maxFlow = refFlow * 2.0;

    // Initialize head parameters
    double refHead = compressor.getPolytropicFluidHead();
    double[][] head = generateHeadData(refHead, isNormalCurves);

    // Initialize efficiency parameters
    double[][] polyEff = generateEfficiencyData(compressor.getPolytropicEfficiency());

    // Configure the compressor chart
    compChart.setUseCompressorChart(true);
    compChart.setHeadUnit("kJ/kg");
    compChart.setCurves(chartConditions, speed, flow, head, polyEff);

    // Generate and set the surge curve
    SurgeCurve surgeCurve = generateSurgeCurve(compChart, refFlow, refSpeed, minFlow, maxFlow,
        minSpeed, maxSpeed, isNormalCurves);
    compChart.setSurgeCurve(surgeCurve);

    return compChart;
  }

  /**
   * Generates flow data based on the reference flow and the generation option.
   *
   * @param refFlow the reference flow rate
   * @param isNormalCurves whether to generate normal curves
   * @return a 2D array representing flow data
   */
  private double[][] generateFlowData(double refFlow, boolean isNormalCurves) {
    double[][] flow = new double[1][3];
    if (isNormalCurves) {
      flow[0][0] = refFlow / 1.3;
      flow[0][1] = refFlow;
      flow[0][2] = refFlow * 2.5 / 1.3;
    } else {
      flow[0][0] = refFlow * 0.7;
      flow[0][1] = refFlow;
      flow[0][2] = refFlow * 1.43;
    }
    return flow;
  }

  /**
   * Generates head data based on the reference head and the generation option.
   *
   * @param refHead the reference head value
   * @param isNormalCurves whether to generate normal curves
   * @return a 2D array representing head data
   */
  private double[][] generateHeadData(double refHead, boolean isNormalCurves) {
    double[][] head = new double[1][3];
    if (isNormalCurves) {
      head[0][0] = refHead * 1.2;
      head[0][1] = refHead;
      head[0][2] = refHead * 1.2 * 0.4;
    } else {
      head[0][0] = refHead * 1.5;
      head[0][1] = refHead;
      head[0][2] = refHead * 0.5;
    }
    return head;
  }

  /**
   * Generates efficiency data based on the reference efficiency.
   *
   * @param refEfficiency the reference efficiency value
   * @return a 2D array representing efficiency data
   */
  private double[][] generateEfficiencyData(double refEfficiency) {
    double[][] polyEff = new double[1][3];
    polyEff[0][0] = refEfficiency * 100.0 * 0.9;
    polyEff[0][1] = refEfficiency * 100.0;
    polyEff[0][2] = refEfficiency * 100.0 * 0.85;
    return polyEff;
  }

  /**
   * Generates the surge curve based on the provided parameters.
   *
   * @param compChart the compressor chart object
   * @param refFlow the reference flow rate
   * @param refSpeed the reference speed
   * @param minFlow the minimum flow rate
   * @param maxFlow the maximum flow rate
   * @param minSpeed the minimum speed
   * @param maxSpeed the maximum speed
   * @param isNormalCurves whether to generate normal curves
   * @return a {@link SurgeCurve} object representing the surge curve
   */
  private SurgeCurve generateSurgeCurve(CompressorChart compChart, double refFlow, double refSpeed,
      double minFlow, double maxFlow, double minSpeed, double maxSpeed, boolean isNormalCurves) {
    double minSurgeFlow = 0.7 * refFlow;
    double refSurgeFlow = isNormalCurves ? refFlow / 1.3 : 0.8 * refFlow;
    double maxSurgeFlow = 0.9 * refFlow;

    double headSurgeMin = compChart.getPolytropicHead(minFlow, minSpeed);
    double headSurgeRef = compChart.getPolytropicHead(refSurgeFlow, refSpeed);
    double headSurgeMax = compChart.getPolytropicHead(maxSurgeFlow, maxSpeed);

    SurgeCurve surgeCurve = new SurgeCurve();
    surgeCurve.setCurve(new double[3], new double[] {minSurgeFlow, refSurgeFlow, maxSurgeFlow},
        new double[] {headSurgeMin, headSurgeRef, headSurgeMax});
    return surgeCurve;
  }
}
