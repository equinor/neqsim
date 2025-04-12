package neqsim.process.equipment.compressor;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class CompressorChartReader {

  private double[] speeds;
  private double[][] flowLines;
  private double[][] headLines;
  private double[][] polyEffLines;
  private double[] stonewallCurve;
  private double[] surgeCurve;

  private double[] surgeFlow;
  private double[] surgeHead;
  private double[] chokeFlow;
  private double[] chokeHead;

  private String headUnit = "kJ/kg";

  public CompressorChartReader(String csvFilePath) throws Exception {
    parseCSV(csvFilePath);
  }

  private void parseCSV(String csvFilePath) throws Exception {
    BufferedReader reader = new BufferedReader(new FileReader(csvFilePath));

    String headerLine = reader.readLine();
    String[] headers = headerLine.split(";");

    int speedIdx = Arrays.asList(headers).indexOf("speed");
    int flowIdx = Arrays.asList(headers).indexOf("flow");
    int headIdx = Arrays.asList(headers).indexOf("head");
    int polyEffIdx = Arrays.asList(headers).indexOf("polyEff");
    int stonewallIdx = Arrays.asList(headers).indexOf("stonewall");
    int surgeIdx = Arrays.asList(headers).indexOf("surge");

    Map<Double, List<String[]>> groupedData = new TreeMap<>();

    List<Double> stonewallList = new ArrayList<>();
    List<Double> surgeList = new ArrayList<>();

    String line;
    while ((line = reader.readLine()) != null) {
      String[] parts = line.split(";");
      Double speedVal = Double.parseDouble(parts[speedIdx]);
      groupedData.computeIfAbsent(speedVal, k -> new ArrayList<>()).add(parts);

      // stonewallList.add(Double.parseDouble(parts[stonewallIdx]));
      // surgeList.add(Double.parseDouble(parts[surgeIdx]));
    }

    speeds = new double[groupedData.size()];
    flowLines = new double[groupedData.size()][];
    headLines = new double[groupedData.size()][];
    polyEffLines = new double[groupedData.size()][];
    surgeFlow = new double[groupedData.size()];
    surgeHead = new double[groupedData.size()];
    chokeFlow = new double[groupedData.size()];
    chokeHead = new double[groupedData.size()];

    int i = 0;
    for (Double speed : groupedData.keySet()) {
      speeds[i] = speed;
      List<String[]> group = groupedData.get(speed);

      int groupSize = group.size();
      flowLines[i] = new double[groupSize];
      headLines[i] = new double[groupSize];
      polyEffLines[i] = new double[groupSize];

      for (int j = 0; j < groupSize; j++) {
        flowLines[i][j] = Double.parseDouble(group.get(j)[flowIdx]);
        headLines[i][j] = Double.parseDouble(group.get(j)[headIdx]);
        polyEffLines[i][j] = Double.parseDouble(group.get(j)[polyEffIdx]);
      }

      int idxMinFlow = minIndex(flowLines[i]);
      int idxMaxFlow = maxIndex(flowLines[i]);

      surgeFlow[i] = flowLines[i][idxMinFlow];
      surgeHead[i] = headLines[i][idxMinFlow];
      chokeFlow[i] = flowLines[i][idxMaxFlow];
      chokeHead[i] = headLines[i][idxMaxFlow];

      i++;
    }

    stonewallCurve = stonewallList.stream().mapToDouble(Double::doubleValue).toArray();
    surgeCurve = surgeList.stream().mapToDouble(Double::doubleValue).toArray();

    reader.close();
  }

  private int minIndex(double[] array) {
    int minIdx = 0;
    for (int i = 1; i < array.length; i++) {
      if (array[i] < array[minIdx])
        minIdx = i;
    }
    return minIdx;
  }

  private int maxIndex(double[] array) {
    int maxIdx = 0;
    for (int i = 1; i < array.length; i++) {
      if (array[i] > array[maxIdx])
        maxIdx = i;
    }
    return maxIdx;
  }

  public void setCurvesToCompressor(Compressor compressor) {
    compressor.getCompressorChart().setCurves(new double[0], speeds, flowLines, headLines,
        polyEffLines);

    compressor.getCompressorChart().setStoneWallCurve(new StoneWallCurve(chokeFlow, chokeHead));
    compressor.getCompressorChart().setSurgeCurve(new SurgeCurve(surgeFlow, surgeHead));
    compressor.getCompressorChart().setHeadUnit(headUnit);
  }

  public void setHeadUnit(String headUnit) {
    this.headUnit = headUnit;
  }

  public double[] getSurgeFlow() {
    return surgeFlow;
  }

  public double[] getSurgeHead() {
    return surgeHead;
  }

  public double[] getChokeFlow() {
    return chokeFlow;
  }

  public double[] getChokeHead() {
    return chokeHead;
  }

  public double[] getSpeeds() {
    return speeds;
  }

  public double[][] getFlowLines() {
    return flowLines;
  }

  public double[][] getHeadLines() {
    return headLines;
  }

  public double[][] getPolyEffLines() {
    return polyEffLines;
  }

  public double[] getStonewallCurve() {
    return stonewallCurve;
  }

  public double[] getSurgeCurve() {
    return surgeCurve;
  }
}
