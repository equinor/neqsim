package neqsim.process.equipment.compressor;

import java.io.FileReader;
import java.io.Reader;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Reader for compressor chart data from JSON files.
 *
 * <p>
 * Expected JSON format:
 * </p>
 * 
 * <pre>
 * {
 *   "compressorName": "Compressor Name",
 *   "headUnit": "kJ/kg",
 *   "maxDesignPower_kW": 16619.42,
 *   "speedCurves": [
 *     {
 *       "speed_rpm": 2000.0,
 *       "flow_m3h": [9598.75, 10892.21, ...],
 *       "head_kJkg": [33.36, 32.15, ...],
 *       "polytropicEfficiency_pct": [78.30, 78.20, ...]
 *     },
 *     ...
 *   ]
 * }
 * </pre>
 *
 * @author esol
 * @version 1.0
 */
public class CompressorChartJsonReader {
  private double[] speeds;
  private double[][] flowLines;
  private double[][] headLines;
  private double[][] polyEffLines;

  private double[] surgeFlow;
  private double[] surgeHead;
  private double[] chokeFlow;
  private double[] chokeHead;

  private String headUnit = "kJ/kg";
  private String compressorName = "";
  private double maxDesignPower = 0.0;

  /**
   * Constructor for CompressorChartJsonReader.
   *
   * @param jsonFilePath path to JSON file
   * @throws Exception if file cannot be read or parsed
   */
  public CompressorChartJsonReader(String jsonFilePath) throws Exception {
    try (Reader reader = new FileReader(jsonFilePath)) {
      parseJson(reader);
    }
  }

  /**
   * Constructor for CompressorChartJsonReader from Reader.
   *
   * @param reader a Reader containing JSON data
   * @throws Exception if JSON cannot be parsed
   */
  public CompressorChartJsonReader(Reader reader) throws Exception {
    parseJson(reader);
  }

  /**
   * Constructor for CompressorChartJsonReader from JSON string.
   *
   * @param jsonString JSON string containing chart data
   * @param isJsonString flag to indicate this is a JSON string (not file path)
   * @throws Exception if JSON cannot be parsed
   */
  public CompressorChartJsonReader(String jsonString, boolean isJsonString) throws Exception {
    if (isJsonString) {
      parseJsonString(jsonString);
    } else {
      try (Reader reader = new FileReader(jsonString)) {
        parseJson(reader);
      }
    }
  }

  private void parseJson(Reader reader) throws Exception {
    JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
    parseJsonObject(root);
  }

  private void parseJsonString(String jsonString) throws Exception {
    JsonObject root = JsonParser.parseString(jsonString).getAsJsonObject();
    parseJsonObject(root);
  }

  private void parseJsonObject(JsonObject root) throws Exception {
    // Read optional metadata
    if (root.has("compressorName")) {
      compressorName = root.get("compressorName").getAsString();
    }
    if (root.has("headUnit")) {
      headUnit = root.get("headUnit").getAsString();
    }
    if (root.has("maxDesignPower_kW")) {
      maxDesignPower = root.get("maxDesignPower_kW").getAsDouble();
    }

    // Read speed curves
    JsonArray speedCurves = root.getAsJsonArray("speedCurves");
    int numCurves = speedCurves.size();

    speeds = new double[numCurves];
    flowLines = new double[numCurves][];
    headLines = new double[numCurves][];
    polyEffLines = new double[numCurves][];
    surgeFlow = new double[numCurves];
    surgeHead = new double[numCurves];
    chokeFlow = new double[numCurves];
    chokeHead = new double[numCurves];

    for (int i = 0; i < numCurves; i++) {
      JsonObject curve = speedCurves.get(i).getAsJsonObject();

      speeds[i] = curve.get("speed_rpm").getAsDouble();

      // Read flow array
      JsonArray flowArray = curve.getAsJsonArray("flow_m3h");
      flowLines[i] = new double[flowArray.size()];
      for (int j = 0; j < flowArray.size(); j++) {
        flowLines[i][j] = flowArray.get(j).getAsDouble();
      }

      // Read head array (support both "head_kJkg" and "head" keys)
      JsonArray headArray =
          curve.has("head_kJkg") ? curve.getAsJsonArray("head_kJkg") : curve.getAsJsonArray("head");
      headLines[i] = new double[headArray.size()];
      for (int j = 0; j < headArray.size(); j++) {
        headLines[i][j] = headArray.get(j).getAsDouble();
      }

      // Read efficiency array
      JsonArray effArray = curve.getAsJsonArray("polytropicEfficiency_pct");
      polyEffLines[i] = new double[effArray.size()];
      for (int j = 0; j < effArray.size(); j++) {
        polyEffLines[i][j] = effArray.get(j).getAsDouble();
      }

      // Calculate surge (min flow) and choke (max flow) points for each speed
      int minFlowIdx = minIndex(flowLines[i]);
      int maxFlowIdx = maxIndex(flowLines[i]);

      surgeFlow[i] = flowLines[i][minFlowIdx];
      surgeHead[i] = headLines[i][minFlowIdx];
      chokeFlow[i] = flowLines[i][maxFlowIdx];
      chokeHead[i] = headLines[i][maxFlowIdx];
    }
  }

  private int minIndex(double[] array) {
    int minIdx = 0;
    for (int i = 1; i < array.length; i++) {
      if (array[i] < array[minIdx]) {
        minIdx = i;
      }
    }
    return minIdx;
  }

  private int maxIndex(double[] array) {
    int maxIdx = 0;
    for (int i = 1; i < array.length; i++) {
      if (array[i] > array[maxIdx]) {
        maxIdx = i;
      }
    }
    return maxIdx;
  }

  /**
   * Apply the loaded chart data to a compressor.
   *
   * @param compressor the compressor to configure
   */
  public void setCurvesToCompressor(Compressor compressor) {
    compressor.getCompressorChart().setCurves(new double[0], speeds, flowLines, headLines,
        polyEffLines);

    compressor.getCompressorChart()
        .setStoneWallCurve(new SafeSplineStoneWallCurve(chokeFlow, chokeHead));
    compressor.getCompressorChart().setSurgeCurve(new SafeSplineSurgeCurve(surgeFlow, surgeHead));
    compressor.getCompressorChart().setHeadUnit(headUnit);

    // Set max and min speed from chart curves
    double chartMaxSpeed = compressor.getCompressorChart().getMaxSpeedCurve();
    double chartMinSpeed = compressor.getCompressorChart().getMinSpeedCurve();
    if (chartMaxSpeed > 0) {
      compressor.setMaximumSpeed(chartMaxSpeed);
    }
    if (chartMinSpeed > 0) {
      compressor.setMinimumSpeed(chartMinSpeed);
    }

    // Optionally set max design power if available
    if (maxDesignPower > 0) {
      compressor.getMechanicalDesign().setMaxDesignPower(maxDesignPower);
    }
  }

  /**
   * Get the compressor name from JSON.
   *
   * @return compressor name
   */
  public String getCompressorName() {
    return compressorName;
  }

  /**
   * Get the max design power from JSON.
   *
   * @return max design power in kW
   */
  public double getMaxDesignPower() {
    return maxDesignPower;
  }

  /**
   * Get the head unit from JSON.
   *
   * @return head unit string
   */
  public String getHeadUnit() {
    return headUnit;
  }

  /**
   * Get the speed values.
   *
   * @return array of speeds in RPM
   */
  public double[] getSpeeds() {
    return speeds;
  }

  /**
   * Get the flow lines for each speed curve.
   *
   * @return 2D array of flow values in m3/h
   */
  public double[][] getFlowLines() {
    return flowLines;
  }

  /**
   * Get the head lines for each speed curve.
   *
   * @return 2D array of head values
   */
  public double[][] getHeadLines() {
    return headLines;
  }

  /**
   * Get the polytropic efficiency lines for each speed curve.
   *
   * @return 2D array of efficiency values in %
   */
  public double[][] getPolyEffLines() {
    return polyEffLines;
  }

  /**
   * Get surge flow for each speed curve.
   *
   * @return array of surge flow values
   */
  public double[] getSurgeFlow() {
    return surgeFlow;
  }

  /**
   * Get surge head for each speed curve.
   *
   * @return array of surge head values
   */
  public double[] getSurgeHead() {
    return surgeHead;
  }

  /**
   * Get choke flow for each speed curve.
   *
   * @return array of choke flow values
   */
  public double[] getChokeFlow() {
    return chokeFlow;
  }

  /**
   * Get choke head for each speed curve.
   *
   * @return array of choke head values
   */
  public double[] getChokeHead() {
    return chokeHead;
  }
}
