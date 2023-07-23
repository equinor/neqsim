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
    return compChart;
  }

}
