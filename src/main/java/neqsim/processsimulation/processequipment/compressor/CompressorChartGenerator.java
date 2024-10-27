package neqsim.processsimulation.processequipment.compressor;

import neqsim.physicalproperties.PhysicalPropertyType;

/**
 * Compressor chart generator.
 *
 * @author Even Solbraa
 */
public class CompressorChartGenerator {
  Compressor compressor = null;

  /**
   * <p>
   * Constructor for CompressorChartGenerator.
   * </p>
   *
   * @param inpcompressor a {@link neqsim.processsimulation.processequipment.compressor.Compressor}
   *        object
   */
  public CompressorChartGenerator(Compressor inpcompressor) {
    this.compressor = inpcompressor;
  }

  /**
   * <p>
   * generateCompressorChart.
   * </p>
   *
   * @param generationOption string to specify how to generate the compressor chart
   * @return a {@link neqsim.processsimulation.processequipment.compressor.CompressorChart} object
   */
  public CompressorChart generateCompressorChart(String generationOption) {
    // Generation compressor chart
    double[] chartConditions = new double[3];
    chartConditions[0] = compressor.getOutletStream().getFluid().getMolarMass("kg/mol");

    double refspeed = compressor.getSpeed();
    double[] speed = new double[1];
    speed[0] = refspeed;
    double minSpeed = refspeed / 2.0;
    double maxSpeed = refspeed * 2.0;

    compressor.getInletStream().getFluid()
        .initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
    double refflow = compressor.getInletStream().getFlowRate("m3/hr");
    double[][] flow = new double[1][3];
    flow[0][0] = refflow * 0.7;
    flow[0][1] = refflow * 1.0;
    flow[0][2] = refflow * 1.43;
    double minFlow = refflow / 2.0;
    double maxFlow = refflow * 2.0;

    double refhead = compressor.getPolytropicFluidHead();
    double[][] head = new double[1][3];
    head[0][0] = refhead * 1.5;
    head[0][1] = refhead;
    head[0][2] = refhead * 0.5;

    double[][] polyEff = new double[1][3];
    polyEff[0][0] = compressor.getPolytropicEfficiency() * 100.0 * 0.9;
    polyEff[0][1] = compressor.getPolytropicEfficiency() * 100.0;
    polyEff[0][2] = compressor.getPolytropicEfficiency() * 100.0 * 0.85;
    CompressorChart compChart = new CompressorChart();
    compChart.setUseCompressorChart(true);
    compChart.setHeadUnit("kJ/kg");
    compChart.setCurves(chartConditions, speed, flow, head, polyEff);

    // Generating surge curve
    double minFlowSurgeFlow = 0.7 * refflow;
    double refSurgeFlow = 0.8 * refflow;
    double maxSurgeFlow = 0.9 * refflow;
    double headSurgeRef = compChart.getPolytropicHead(refSurgeFlow, refspeed);
    double headSurgeMin = compChart.getPolytropicHead(minFlow, minSpeed);
    double headSurgeMax = compChart.getPolytropicHead(maxSurgeFlow, maxSpeed);

    SurgeCurve surgecurve = new SurgeCurve();

    surgecurve.setCurve(new double[3], new double[] {minFlowSurgeFlow, refSurgeFlow, maxSurgeFlow},
        new double[] {headSurgeMin, headSurgeRef, headSurgeMax});

    compChart.setSurgeCurve(surgecurve);
    return compChart;
  }
}
