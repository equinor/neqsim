package neqsim.processSimulation.processEquipment.compressor;

/**
 * Compressor chart generator.
 */
public class CompressorChartGenerator {

  Compressor compressor = null;

  public CompressorChartGenerator(Compressor inpcompressor) {
    this.compressor = inpcompressor;
  }

  public CompressorChart generateCompressorChart(String string) {
    CompressorChart compChart = new CompressorChart();
    compChart.setUseCompressorChart(true);

    int refspeed = compressor.getSpeed();

    double refhead = compressor.getPolytropicHead("kJ/kg");
    double refflow = compressor.getInletStream().getFlowRate("m3/hr");
    double minFlow = refflow / 2.0;
    double maxFLow = refflow * 2.0;
    double minSpeed = refspeed / 2.0;
    double maxSpeed = refspeed * 2.0;

    double[] chartConditions = new double[3];
    chartConditions[0] = compressor.getOutletStream().getFluid().getMolarMass("kg/mol");


    double[] speed = new double[1];
    speed[0] = refspeed;

    double[][] flow = new double[1][1];
    flow[0][0] = refflow;

    double[][] head = new double[1][1];
    head[0][0] = refhead;

    double[][] polyEff = new double[1][1];
    polyEff[0][0] = compressor.getPolytropicEfficiency() * 100.0;

    compChart.setHeadUnit("kJ/kg");
    compChart.setCurves(chartConditions, speed, flow, head, polyEff);

    double minFlowSurgeFlow = 0.3 * refflow;
    double refSurgeFlow = 0.5 * refflow;
    double maxSurgeFlow = 0.8 * refflow;
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
