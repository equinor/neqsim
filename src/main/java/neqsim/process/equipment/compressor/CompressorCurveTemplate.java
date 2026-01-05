package neqsim.process.equipment.compressor;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Template class for storing normalized compressor curve data.
 *
 * <p>
 * This class stores compressor curves in a normalized (reduced) form that can be scaled to match
 * any compressor's operating conditions using fan laws. The curves are normalized relative to a
 * reference speed, so:
 * </p>
 * <ul>
 * <li>Reduced flow = flow / speed</li>
 * <li>Reduced head = head / speed²</li>
 * <li>Efficiency remains unchanged (approximately independent of speed)</li>
 * </ul>
 *
 * <p>
 * <strong>12 predefined templates</strong> are available in three categories:
 * </p>
 *
 * <p>
 * <em>Basic Centrifugal (3):</em>
 * </p>
 * <ul>
 * <li>{@link #CENTRIFUGAL_STANDARD} - Standard multi-stage, ~78% peak efficiency</li>
 * <li>{@link #CENTRIFUGAL_HIGH_FLOW} - Wide flow range, flatter head curve</li>
 * <li>{@link #CENTRIFUGAL_HIGH_HEAD} - High head, narrower operating range</li>
 * </ul>
 *
 * <p>
 * <em>Application-Based (6):</em>
 * </p>
 * <ul>
 * <li>{@link #PIPELINE} - Gas transmission, high capacity, ~85% efficiency</li>
 * <li>{@link #EXPORT} - Offshore gas export, high pressure, stable operation</li>
 * <li>{@link #INJECTION} - Gas injection/EOR, very high pressure ratio</li>
 * <li>{@link #GAS_LIFT} - Artificial lift, wide surge margin, robust design</li>
 * <li>{@link #REFRIGERATION} - LNG/process cooling, wide operating range</li>
 * <li>{@link #BOOSTER} - Process plant pressure boosting, balanced design</li>
 * </ul>
 *
 * <p>
 * <em>Compressor Type (4):</em>
 * </p>
 * <ul>
 * <li>{@link #SINGLE_STAGE} - Low pressure ratio (1.5-2.5), simple design</li>
 * <li>{@link #MULTISTAGE_INLINE} - High pressure ratio (5-15), barrel type</li>
 * <li>{@link #INTEGRALLY_GEARED} - Flexible staging, ~82% efficiency, air separation</li>
 * <li>{@link #OVERHUNG} - Cantilever design, simple maintenance</li>
 * </ul>
 *
 * <p>
 * <strong>Usage Example:</strong>
 * </p>
 * 
 * <pre>
 * // Get template by name (flexible naming)
 * CompressorCurveTemplate template = CompressorCurveTemplate.getTemplate("pipeline");
 * 
 * // Scale to design point
 * CompressorChartInterface chart = template.scaleToDesignPoint(10000, // designSpeed (RPM)
 *     5000, // designFlow (m³/hr)
 *     85.0, // designHead (kJ/kg)
 *     5 // numberOfSpeeds
 * );
 * 
 * // Or use with CompressorChartGenerator
 * CompressorChartGenerator generator = new CompressorChartGenerator(compressor);
 * CompressorChartInterface chart = generator.generateFromTemplate("EXPORT", 5);
 * 
 * // List available templates
 * String[] all = CompressorCurveTemplate.getAvailableTemplates();
 * String[] apps = CompressorCurveTemplate.getTemplatesByCategory("application");
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 * @see CompressorChartGenerator
 * @see CompressorChartInterface
 */
public class CompressorCurveTemplate implements Serializable {
  private static final long serialVersionUID = 1001L;

  /** Name/identifier for this template. */
  private final String name;

  /** Reference speed used for normalization (RPM). */
  private final double referenceSpeed;

  /** Speed values for each curve (RPM). */
  private final double[] speeds;

  /** Reduced flow values (flow/speed) for each curve. Each row corresponds to a speed. */
  private final double[][] reducedFlows;

  /** Reduced head values (head/speed²) for each curve. Each row corresponds to a speed. */
  private final double[][] reducedHeads;

  /** Polytropic efficiency values (%) for each curve. Each row corresponds to a speed. */
  private final double[][] polytropicEfficiencies;

  /**
   * Standard centrifugal compressor template.
   *
   * <p>
   * Based on typical multi-stage centrifugal compressor characteristics with:
   * </p>
   * <ul>
   * <li>9 speed curves from ~67% to 100% of max speed</li>
   * <li>5-7 operating points per curve</li>
   * <li>~25-30% surge margin</li>
   * <li>Peak efficiency ~78%</li>
   * </ul>
   */
  public static final CompressorCurveTemplate CENTRIFUGAL_STANDARD =
      createCentrifugalStandardTemplate();

  /**
   * High flow centrifugal compressor template.
   *
   * <p>
   * Compressor optimized for high volumetric flow with lower head per stage.
   * </p>
   */
  public static final CompressorCurveTemplate CENTRIFUGAL_HIGH_FLOW =
      createCentrifugalHighFlowTemplate();

  /**
   * High head centrifugal compressor template.
   *
   * <p>
   * Compressor optimized for high head with narrower operating range.
   * </p>
   */
  public static final CompressorCurveTemplate CENTRIFUGAL_HIGH_HEAD =
      createCentrifugalHighHeadTemplate();

  // ==================== Application-Based Templates ====================

  /**
   * Pipeline compressor template.
   *
   * <p>
   * Designed for natural gas transmission with high capacity, moderate pressure ratio, and wide
   * operating range. Typical for long-distance gas pipelines.
   * </p>
   * <ul>
   * <li>High volumetric flow capacity</li>
   * <li>Moderate head (pressure ratio 1.3-1.6 per stage)</li>
   * <li>Wide stable operating range (~40% turndown)</li>
   * <li>Peak efficiency ~82-85%</li>
   * </ul>
   */
  public static final CompressorCurveTemplate PIPELINE = createPipelineTemplate();

  /**
   * Export compressor template.
   *
   * <p>
   * Designed for offshore gas export with high discharge pressure and stable operation. Optimized
   * for efficiency at design point.
   * </p>
   * <ul>
   * <li>High pressure ratio</li>
   * <li>Multiple stages (typically 6-8)</li>
   * <li>Narrow but efficient operating range</li>
   * <li>Peak efficiency ~80%</li>
   * </ul>
   */
  public static final CompressorCurveTemplate EXPORT = createExportTemplate();

  /**
   * Injection compressor template.
   *
   * <p>
   * Designed for gas injection/reinjection with very high discharge pressure. Used for enhanced oil
   * recovery and reservoir pressure maintenance.
   * </p>
   * <ul>
   * <li>Very high pressure ratio (overall 50-200)</li>
   * <li>Lower volumetric capacity</li>
   * <li>Multiple stages with intercooling</li>
   * <li>Peak efficiency ~77%</li>
   * </ul>
   */
  public static final CompressorCurveTemplate INJECTION = createInjectionTemplate();

  /**
   * Gas lift compressor template.
   *
   * <p>
   * Designed for artificial lift applications with variable operating conditions. Must handle
   * fluctuating inlet conditions and some liquid carryover.
   * </p>
   * <ul>
   * <li>Variable pressure ratio capability</li>
   * <li>Robust design for liquid tolerance</li>
   * <li>Wide surge margin (~35%)</li>
   * <li>Peak efficiency ~75%</li>
   * </ul>
   */
  public static final CompressorCurveTemplate GAS_LIFT = createGasLiftTemplate();

  /**
   * Refrigeration compressor template.
   *
   * <p>
   * Designed for process cooling and LNG applications with refrigerants. Optimized for wide
   * operating range and varying loads.
   * </p>
   * <ul>
   * <li>Wide operating range for varying cooling loads</li>
   * <li>Adapted for high molecular weight refrigerants</li>
   * <li>Good part-load efficiency</li>
   * <li>Peak efficiency ~78%</li>
   * </ul>
   */
  public static final CompressorCurveTemplate REFRIGERATION = createRefrigerationTemplate();

  /**
   * Booster compressor template.
   *
   * <p>
   * Designed for moderate pressure boosting in process plants. General-purpose design with good
   * balance of capacity and head.
   * </p>
   * <ul>
   * <li>Moderate pressure ratio (2-4)</li>
   * <li>Medium capacity</li>
   * <li>Balanced operating range</li>
   * <li>Peak efficiency ~76%</li>
   * </ul>
   */
  public static final CompressorCurveTemplate BOOSTER = createBoosterTemplate();

  // ==================== Compressor Type Templates ====================

  /**
   * Single-stage centrifugal compressor template.
   *
   * <p>
   * Simple single impeller design for low pressure ratio applications. Common for utilities,
   * blowers, and simple compression duty.
   * </p>
   * <ul>
   * <li>Low pressure ratio (1.5-2.5 per stage)</li>
   * <li>Simple, cost-effective design</li>
   * <li>Wide flow range</li>
   * <li>Peak efficiency ~75%</li>
   * </ul>
   */
  public static final CompressorCurveTemplate SINGLE_STAGE = createSingleStageTemplate();

  /**
   * Multistage inline (barrel) compressor template.
   *
   * <p>
   * Multiple stages in series within a barrel casing for high pressure applications. Standard
   * design for oil and gas industry.
   * </p>
   * <ul>
   * <li>High pressure ratio (5-15 overall)</li>
   * <li>4-8 stages typical</li>
   * <li>Compact design, good for high pressure</li>
   * <li>Peak efficiency ~78%</li>
   * </ul>
   */
  public static final CompressorCurveTemplate MULTISTAGE_INLINE = createMultistageInlineTemplate();

  /**
   * Integrally geared compressor template.
   *
   * <p>
   * Multiple pinions with impellers driven by a bull gear. Flexible staging for air separation and
   * process applications.
   * </p>
   * <ul>
   * <li>Flexible pressure ratio per stage</li>
   * <li>Good for air separation, process air</li>
   * <li>High efficiency through optimized staging</li>
   * <li>Peak efficiency ~82%</li>
   * </ul>
   */
  public static final CompressorCurveTemplate INTEGRALLY_GEARED = createIntegrallyGearedTemplate();

  /**
   * Overhung impeller compressor template.
   *
   * <p>
   * Cantilever design with impeller mounted on extended shaft. Simple maintenance access, common
   * for smaller applications.
   * </p>
   * <ul>
   * <li>Low to medium pressure</li>
   * <li>Simple maintenance</li>
   * <li>Cost-effective for smaller duties</li>
   * <li>Peak efficiency ~74%</li>
   * </ul>
   */
  public static final CompressorCurveTemplate OVERHUNG = createOverhungTemplate();

  /**
   * Constructor for CompressorCurveTemplate.
   *
   * @param name Template name/identifier
   * @param referenceSpeed Reference speed used for normalization (RPM)
   * @param speeds Speed values for each curve (RPM)
   * @param flows Flow values for each curve (m³/hr). Will be normalized internally.
   * @param heads Head values for each curve (kJ/kg). Will be normalized internally.
   * @param efficiencies Polytropic efficiency values (%) for each curve
   */
  public CompressorCurveTemplate(String name, double referenceSpeed, double[] speeds,
      double[][] flows, double[][] heads, double[][] efficiencies) {
    this.name = name;
    this.referenceSpeed = referenceSpeed;
    this.speeds = Arrays.copyOf(speeds, speeds.length);

    // Normalize flows and heads
    int numSpeeds = speeds.length;
    this.reducedFlows = new double[numSpeeds][];
    this.reducedHeads = new double[numSpeeds][];
    this.polytropicEfficiencies = new double[numSpeeds][];

    for (int i = 0; i < numSpeeds; i++) {
      int numPoints = flows[i].length;
      this.reducedFlows[i] = new double[numPoints];
      this.reducedHeads[i] = new double[numPoints];
      this.polytropicEfficiencies[i] = Arrays.copyOf(efficiencies[i], efficiencies[i].length);

      double speedRatio = speeds[i] / referenceSpeed;
      for (int j = 0; j < numPoints; j++) {
        // Normalize: reduced_flow = flow / speed, reduced_head = head / speed²
        this.reducedFlows[i][j] = flows[i][j] / speeds[i];
        this.reducedHeads[i][j] = heads[i][j] / (speedRatio * speedRatio);
      }
    }
  }

  /**
   * Get the template name.
   *
   * @return the template name
   */
  public String getName() {
    return name;
  }

  /**
   * Get the reference speed used for normalization.
   *
   * @return reference speed in RPM
   */
  public double getReferenceSpeed() {
    return referenceSpeed;
  }

  /**
   * Get the number of speed curves in this template.
   *
   * @return number of speed curves
   */
  public int getNumberOfSpeedCurves() {
    return speeds.length;
  }

  /**
   * Get the speed ratios (speed/referenceSpeed) for all curves.
   *
   * @return array of speed ratios
   */
  public double[] getSpeedRatios() {
    double[] ratios = new double[speeds.length];
    for (int i = 0; i < speeds.length; i++) {
      ratios[i] = speeds[i] / referenceSpeed;
    }
    return ratios;
  }

  /**
   * Scale this template to match a specific compressor operating point.
   *
   * <p>
   * The scaling uses fan laws to generate curves that pass through the specified design point
   * (designFlow, designHead) at the design speed. Uses interpolate and extrapolate chart type by
   * default.
   * </p>
   *
   * @param designSpeed Design speed in RPM
   * @param designFlow Design flow rate in m³/hr
   * @param designHead Design polytropic head in kJ/kg
   * @param numberOfSpeeds Number of speed curves to generate (uses evenly spaced speeds from
   *        template range)
   * @return A new CompressorChart with scaled curves
   */
  public CompressorChartInterface scaleToDesignPoint(double designSpeed, double designFlow,
      double designHead, int numberOfSpeeds) {
    return scaleToDesignPoint(designSpeed, designFlow, designHead, numberOfSpeeds,
        "interpolate and extrapolate");
  }

  /**
   * Scale this template to match a specific compressor operating point with specified chart type.
   *
   * <p>
   * The scaling uses fan laws to generate curves that pass through the specified design point
   * (designFlow, designHead) at the design speed.
   * </p>
   *
   * @param designSpeed Design speed in RPM
   * @param designFlow Design flow rate in m³/hr
   * @param designHead Design polytropic head in kJ/kg
   * @param numberOfSpeeds Number of speed curves to generate
   * @param chartType Chart type: "simple", "interpolate", or "interpolate and extrapolate"
   * @return A new CompressorChartInterface with scaled curves
   */
  public CompressorChartInterface scaleToDesignPoint(double designSpeed, double designFlow,
      double designHead, int numberOfSpeeds, String chartType) {
    // Find template curve closest to 100% speed to get reference reduced values
    int refIndex = 0;
    double minDiff = Double.MAX_VALUE;
    for (int i = 0; i < speeds.length; i++) {
      double diff = Math.abs(speeds[i] / referenceSpeed - 1.0);
      if (diff < minDiff) {
        minDiff = diff;
        refIndex = i;
      }
    }

    // Find the design point on the reference curve (typically middle point)
    int designPointIndex = reducedFlows[refIndex].length / 2;
    double templateReducedFlow = reducedFlows[refIndex][designPointIndex];
    double templateReducedHead = reducedHeads[refIndex][designPointIndex];

    // Calculate scaling factors
    double flowScaleFactor = (designFlow / designSpeed) / templateReducedFlow;
    double headScaleFactor = designHead / templateReducedHead;

    // Determine which template speeds to use
    double[] speedRatios = getSpeedRatios();
    double minRatio = speedRatios[0];
    double maxRatio = speedRatios[speedRatios.length - 1];

    double[] targetSpeeds = new double[numberOfSpeeds];
    if (numberOfSpeeds == 1) {
      targetSpeeds[0] = designSpeed;
    } else {
      for (int i = 0; i < numberOfSpeeds; i++) {
        double ratio = minRatio + (maxRatio - minRatio) * i / (numberOfSpeeds - 1);
        targetSpeeds[i] = designSpeed * ratio;
      }
    }

    // Generate scaled curves
    double[][] scaledFlows = new double[numberOfSpeeds][];
    double[][] scaledHeads = new double[numberOfSpeeds][];
    double[][] scaledEfficiencies = new double[numberOfSpeeds][];

    for (int i = 0; i < numberOfSpeeds; i++) {
      // Find closest template curve for this speed ratio
      double targetRatio = targetSpeeds[i] / designSpeed;
      int templateIndex = findClosestSpeedIndex(targetRatio);

      int numPoints = reducedFlows[templateIndex].length;
      scaledFlows[i] = new double[numPoints];
      scaledHeads[i] = new double[numPoints];
      scaledEfficiencies[i] = Arrays.copyOf(polytropicEfficiencies[templateIndex], numPoints);

      double speedRatio = targetSpeeds[i] / designSpeed;
      for (int j = 0; j < numPoints; j++) {
        // Scale using fan laws
        scaledFlows[i][j] = reducedFlows[templateIndex][j] * flowScaleFactor * targetSpeeds[i];
        scaledHeads[i][j] =
            reducedHeads[templateIndex][j] * headScaleFactor * speedRatio * speedRatio;
      }
    }

    // Create and configure the chart based on type
    CompressorChartInterface chart = createChart(chartType);
    chart.setUseCompressorChart(true);
    chart.setHeadUnit("kJ/kg");
    chart.setCurves(new double[0], targetSpeeds, scaledFlows, scaledHeads, scaledEfficiencies);
    chart.generateSurgeCurve();
    chart.generateStoneWallCurve();

    return chart;
  }

  /**
   * Create a chart instance of the specified type.
   *
   * <p>
   * This method is package-private to allow reuse by CompressorChartGenerator.
   * </p>
   *
   * @param chartType The chart type: "simple", "fan law", "interpolate", or "interpolate and
   *        extrapolate"
   * @return A new chart instance
   */
  static CompressorChartInterface createChart(String chartType) {
    if ("simple".equals(chartType) || "fan law".equals(chartType)) {
      return new CompressorChart();
    } else if ("interpolate".equals(chartType)) {
      return new CompressorChartAlternativeMapLookup();
    } else {
      return new CompressorChartAlternativeMapLookupExtrapolate();
    }
  }

  /**
   * Create efficiency array with same values for all speeds.
   *
   * <p>
   * Helper method to reduce repetition in template definitions where efficiency curves are
   * identical across speeds.
   * </p>
   *
   * @param numSpeeds Number of speed curves
   * @param efficiencyProfile Single efficiency profile to repeat
   * @return 2D array with repeated efficiency profiles
   */
  private static double[][] repeatEfficiency(int numSpeeds, double[] efficiencyProfile) {
    double[][] result = new double[numSpeeds][];
    for (int i = 0; i < numSpeeds; i++) {
      result[i] = Arrays.copyOf(efficiencyProfile, efficiencyProfile.length);
    }
    return result;
  }

  /**
   * Find the index of the template curve closest to the specified speed ratio.
   *
   * @param targetRatio Target speed ratio (speed/designSpeed)
   * @return Index of the closest template curve
   */
  private int findClosestSpeedIndex(double targetRatio) {
    int closestIndex = 0;
    double minDiff = Double.MAX_VALUE;
    for (int i = 0; i < speeds.length; i++) {
      double ratio = speeds[i] / referenceSpeed;
      double diff = Math.abs(ratio - targetRatio);
      if (diff < minDiff) {
        minDiff = diff;
        closestIndex = i;
      }
    }
    return closestIndex;
  }

  /**
   * Create the standard centrifugal compressor template.
   *
   * @return the template
   */
  private static CompressorCurveTemplate createCentrifugalStandardTemplate() {
    // Based on the user-provided typical multi-speed curve data
    double refSpeed = 10500.0;
    double[] speeds = {7000, 7500, 8000, 8500, 9000, 9500, 9659, 10000, 10500};

    double[][] flows =
        {{4512.7, 5120.8, 5760.9, 6401, 6868.27}, {4862.47, 5486.57, 6172.39, 6858.21, 7550.89},
            {5237.84, 5852.34, 6583.88, 7315.43, 8046.97, 8266.43},
            {5642.94, 6218.11, 6995.38, 7772.64, 8549.9, 9000.72},
            {6221.77, 6583.88, 7406.87, 8229.85, 9052.84, 9768.84},
            {6888.85, 6949.65, 7818.36, 8687.07, 9555.77, 10424.5, 10546.1},
            {7109.83, 7948.87, 8832.08, 9715.29, 10598.5, 10801.6},
            {7598.9, 8229.85, 9144.28, 10058.7, 10973.1, 11338.9},
            {8334.1, 8641.35, 9601.5, 10561.6, 11521.8, 11963.5}};

    double[][] heads = {{61.885, 59.639, 56.433, 52.481, 49.132},
        {71.416, 69.079, 65.589, 61.216, 55.858}, {81.621, 79.311, 75.545, 70.727, 64.867, 62.879},
        {92.493, 90.312, 86.3, 81.079, 74.658, 70.216},
        {103.512, 102.073, 97.83, 92.254, 85.292, 77.638},
        {114.891, 114.632, 110.169, 104.221, 96.727, 87.002, 85.262},
        {118.595, 114.252, 108.203, 100.55, 90.532, 87.54},
        {126.747, 123.376, 117.113, 109.056, 98.369, 92.632},
        {139.082, 137.398, 130.867, 122.264, 110.548, 103.247}};

    double[][] efficiencies = {{78.3, 78.2, 77.2, 75.4, 73.4}, {78.3, 78.3, 77.5, 75.8, 73.0},
        {78.2, 78.4, 77.7, 76.1, 73.5, 72.5}, {78.2, 78.4, 77.9, 76.4, 74.0, 71.9},
        {78.3, 78.4, 78.0, 76.7, 74.5, 71.2}, {78.3, 78.4, 78.1, 77.0, 74.9, 71.3, 70.5},
        {78.4, 78.1, 77.1, 75.0, 71.4, 70.2}, {78.3, 78.2, 77.2, 75.2, 71.7, 69.5},
        {78.2, 78.2, 77.3, 75.5, 72.2, 69.6}};

    return new CompressorCurveTemplate("CENTRIFUGAL_STANDARD", refSpeed, speeds, flows, heads,
        efficiencies);
  }

  /**
   * Create the high flow centrifugal compressor template.
   *
   * @return the template
   */
  private static CompressorCurveTemplate createCentrifugalHighFlowTemplate() {
    // High flow variant - wider flow range, flatter head curve
    double refSpeed = 10000.0;
    double[] speeds = {7000, 8000, 9000, 10000};

    double[][] flows = {{5000, 6000, 7500, 9000, 10000}, {5700, 6850, 8550, 10250, 11400},
        {6400, 7700, 9600, 11500, 12800}, {7100, 8550, 10700, 12800, 14300}};

    double[][] heads =
        {{50, 48, 44, 38, 32}, {65, 63, 57, 49, 41}, {82, 79, 72, 62, 52}, {100, 96, 88, 76, 64}};

    double[][] efficiencies = repeatEfficiency(4, new double[] {77, 78, 77.5, 75, 72});

    return new CompressorCurveTemplate("CENTRIFUGAL_HIGH_FLOW", refSpeed, speeds, flows, heads,
        efficiencies);
  }

  /**
   * Create the high head centrifugal compressor template.
   *
   * @return the template
   */
  private static CompressorCurveTemplate createCentrifugalHighHeadTemplate() {
    // High head variant - steeper head curve, narrower stable range
    double refSpeed = 10000.0;
    double[] speeds = {7000, 8000, 9000, 10000};

    double[][] flows = {{3500, 4200, 5000, 5500, 5800}, {4000, 4800, 5700, 6300, 6600},
        {4500, 5400, 6400, 7100, 7500}, {5000, 6000, 7100, 7900, 8300}};

    double[][] heads = {{85, 82, 75, 65, 55}, {110, 107, 98, 85, 72}, {140, 135, 124, 108, 92},
        {170, 165, 152, 132, 112}};

    double[][] efficiencies = repeatEfficiency(4, new double[] {76, 78, 77, 74, 70});

    return new CompressorCurveTemplate("CENTRIFUGAL_HIGH_HEAD", refSpeed, speeds, flows, heads,
        efficiencies);
  }

  // ==================== Application-Based Template Implementations ====================

  /**
   * Create the pipeline compressor template.
   *
   * @return the template
   */
  private static CompressorCurveTemplate createPipelineTemplate() {
    // Pipeline compressor: high capacity, flat curves, wide operating range
    // Based on typical 30-50 MW gas transmission compressor
    double refSpeed = 5500.0; // Lower speed for large machines

    double[] speeds = {4000, 4500, 5000, 5500};

    // High flow capacity (30,000-60,000 m³/hr actual)
    double[][] flows =
        {{22000, 28000, 35000, 42000, 48000, 52000}, {24750, 31500, 39375, 47250, 54000, 58500},
            {27500, 35000, 43750, 52500, 60000, 65000}, {30250, 38500, 48125, 57750, 66000, 71500}};

    // Moderate head per stage (flatter curve for pipeline stability)
    double[][] heads = {{42, 41, 39, 36, 32, 28}, {53, 52, 49, 46, 41, 35},
        {65, 64, 61, 56, 50, 44}, {79, 77, 73, 68, 60, 53}};

    // High efficiency for large machines
    double[][] efficiencies = repeatEfficiency(4, new double[] {80, 83, 85, 84, 82, 78});

    return new CompressorCurveTemplate("PIPELINE", refSpeed, speeds, flows, heads, efficiencies);
  }

  /**
   * Create the export compressor template.
   *
   * @return the template
   */
  private static CompressorCurveTemplate createExportTemplate() {
    // Export compressor: high pressure, multiple stages, stable operation
    // Based on typical offshore export compressor (6-8 stages)
    double refSpeed = 9000.0;

    double[] speeds = {6500, 7500, 8250, 9000};

    // Medium flow capacity
    double[][] flows = {{4500, 5500, 6500, 7500, 8200}, {5200, 6350, 7500, 8650, 9500},
        {5700, 6970, 8230, 9500, 10400}, {6250, 7625, 9000, 10400, 11400}};

    // High head (multiple stages combined)
    double[][] heads = {{95, 92, 87, 78, 68}, {125, 122, 115, 103, 90}, {152, 148, 140, 125, 109},
        {180, 175, 165, 148, 129}};

    // Good efficiency for optimized design
    double[][] efficiencies = repeatEfficiency(4, new double[] {77, 79, 80, 78, 74});

    return new CompressorCurveTemplate("EXPORT", refSpeed, speeds, flows, heads, efficiencies);
  }

  /**
   * Create the injection compressor template.
   *
   * @return the template
   */
  private static CompressorCurveTemplate createInjectionTemplate() {
    // Injection compressor: very high pressure, lower capacity
    // Based on typical gas injection compressor for EOR
    double refSpeed = 11000.0;

    double[] speeds = {8000, 9000, 10000, 11000};

    // Lower flow capacity for high pressure
    double[][] flows = {{2500, 3000, 3600, 4100, 4400}, {2800, 3375, 4050, 4612, 4950},
        {3125, 3750, 4500, 5125, 5500}, {3438, 4125, 4950, 5638, 6050}};

    // Very high head (multiple stages with intercooling)
    double[][] heads = {{130, 125, 115, 100, 85}, {165, 158, 146, 127, 108},
        {203, 195, 180, 156, 133}, {245, 236, 218, 189, 161}};

    // Moderate efficiency (high pressure challenges)
    double[][] efficiencies = repeatEfficiency(4, new double[] {74, 76, 77, 75, 72});

    return new CompressorCurveTemplate("INJECTION", refSpeed, speeds, flows, heads, efficiencies);
  }

  /**
   * Create the gas lift compressor template.
   *
   * @return the template
   */
  private static CompressorCurveTemplate createGasLiftTemplate() {
    // Gas lift compressor: robust, wide surge margin, variable conditions
    // Based on typical gas lift compressor for offshore platforms
    double refSpeed = 8500.0;

    double[] speeds = {6000, 7000, 7750, 8500};

    // Medium capacity with wide operating range
    double[][] flows = {{3500, 4500, 5800, 7200, 8500, 9500}, {4100, 5250, 6770, 8400, 9920, 11080},
        {4530, 5810, 7490, 9290, 10970, 12260}, {4970, 6375, 8220, 10200, 12040, 13460}};

    // Moderate head with flatter curve for stability
    double[][] heads = {{48, 47, 45, 41, 35, 28}, {65, 64, 61, 56, 48, 38},
        {80, 78, 75, 68, 58, 47}, {96, 94, 90, 82, 70, 56}};

    // Slightly lower peak efficiency, but robust design
    double[][] efficiencies = repeatEfficiency(4, new double[] {72, 74, 75, 74, 71, 66});

    return new CompressorCurveTemplate("GAS_LIFT", refSpeed, speeds, flows, heads, efficiencies);
  }

  /**
   * Create the refrigeration compressor template.
   *
   * @return the template
   */
  private static CompressorCurveTemplate createRefrigerationTemplate() {
    // Refrigeration compressor: wide range, adapted for refrigerants
    // Based on typical LNG or process refrigeration compressor
    double refSpeed = 3600.0; // Often direct-drive from motor

    double[] speeds = {2700, 3000, 3300, 3600};

    // High flow for low-density refrigerants
    double[][] flows =
        {{12000, 15000, 19000, 23000, 26000, 28000}, {13350, 16650, 21100, 25550, 28900, 31100},
            {14650, 18300, 23200, 28050, 31750, 34200}, {16000, 20000, 25300, 30600, 34600, 37300}};

    // Lower head per stage (lighter gases)
    double[][] heads = {{28, 27, 25, 22, 19, 16}, {35, 33, 31, 27, 23, 20},
        {42, 40, 37, 33, 28, 24}, {50, 48, 45, 40, 34, 29}};

    // Good efficiency across operating range
    double[][] efficiencies = repeatEfficiency(4, new double[] {75, 77, 78, 77, 75, 72});

    return new CompressorCurveTemplate("REFRIGERATION", refSpeed, speeds, flows, heads,
        efficiencies);
  }

  /**
   * Create the booster compressor template.
   *
   * @return the template
   */
  private static CompressorCurveTemplate createBoosterTemplate() {
    // Booster compressor: moderate pressure ratio, balanced design
    // Based on typical process plant booster
    double refSpeed = 10000.0;

    double[] speeds = {7000, 8000, 9000, 10000};

    // Medium capacity
    double[][] flows = {{4000, 5000, 6200, 7400, 8300}, {4570, 5710, 7080, 8460, 9490},
        {5140, 6430, 7970, 9510, 10670}, {5710, 7140, 8850, 10570, 11860}};

    // Moderate head
    double[][] heads =
        {{55, 53, 49, 43, 36}, {72, 69, 64, 56, 47}, {91, 87, 81, 71, 60}, {112, 108, 100, 88, 74}};

    // Balanced efficiency
    double[][] efficiencies = repeatEfficiency(4, new double[] {74, 76, 76, 74, 70});

    return new CompressorCurveTemplate("BOOSTER", refSpeed, speeds, flows, heads, efficiencies);
  }

  // ==================== Compressor Type Template Implementations ====================

  /**
   * Create the single-stage compressor template.
   *
   * @return the template
   */
  private static CompressorCurveTemplate createSingleStageTemplate() {
    // Single-stage: simple design, low pressure ratio, wide flow range
    double refSpeed = 12000.0;

    double[] speeds = {8400, 9600, 10800, 12000};

    // Wide flow range
    double[][] flows = {{3000, 4000, 5500, 7000, 8500, 9500}, {3430, 4570, 6290, 8000, 9710, 10860},
        {3860, 5140, 7070, 9000, 10930, 12220}, {4290, 5710, 7860, 10000, 12140, 13580}};

    // Low head (single stage limitation)
    double[][] heads = {{25, 24, 22, 19, 15, 11}, {33, 32, 29, 25, 20, 15},
        {42, 40, 36, 31, 25, 19}, {52, 49, 45, 39, 31, 23}};

    // Moderate efficiency
    double[][] efficiencies = repeatEfficiency(4, new double[] {72, 74, 75, 73, 70, 65});

    return new CompressorCurveTemplate("SINGLE_STAGE", refSpeed, speeds, flows, heads,
        efficiencies);
  }

  /**
   * Create the multistage inline (barrel) compressor template.
   *
   * @return the template
   */
  private static CompressorCurveTemplate createMultistageInlineTemplate() {
    // Multistage inline/barrel: high pressure, compact, oil & gas standard
    double refSpeed = 10500.0;

    double[] speeds = {7500, 8500, 9500, 10500};

    // Medium-high capacity
    double[][] flows = {{4200, 5200, 6400, 7500, 8300}, {4760, 5890, 7250, 8500, 9410},
        {5320, 6590, 8110, 9500, 10520}, {5880, 7290, 8960, 10500, 11630}};

    // High head (multiple stages)
    double[][] heads = {{88, 85, 79, 70, 60}, {113, 109, 101, 90, 77}, {141, 136, 127, 112, 96},
        {172, 166, 155, 137, 118}};

    // Good efficiency for industrial design
    double[][] efficiencies = repeatEfficiency(4, new double[] {75, 77, 78, 76, 73});

    return new CompressorCurveTemplate("MULTISTAGE_INLINE", refSpeed, speeds, flows, heads,
        efficiencies);
  }

  /**
   * Create the integrally geared compressor template.
   *
   * @return the template
   */
  private static CompressorCurveTemplate createIntegrallyGearedTemplate() {
    // Integrally geared: flexible staging, high efficiency, air separation typical
    double refSpeed = 20000.0; // High speed pinions

    double[] speeds = {14000, 16000, 18000, 20000};

    // Variable capacity depending on application
    double[][] flows = {{6000, 7500, 9500, 11500, 13000}, {6860, 8570, 10860, 13140, 14860},
        {7710, 9640, 12210, 14790, 16710}, {8570, 10710, 13570, 16430, 18570}};

    // Moderate head per stage (optimized staging)
    double[][] heads =
        {{35, 34, 31, 27, 22}, {46, 44, 40, 35, 29}, {58, 56, 51, 44, 37}, {72, 69, 63, 55, 46}};

    // High efficiency through optimized design
    double[][] efficiencies = repeatEfficiency(4, new double[] {79, 81, 82, 80, 77});

    return new CompressorCurveTemplate("INTEGRALLY_GEARED", refSpeed, speeds, flows, heads,
        efficiencies);
  }

  /**
   * Create the overhung impeller compressor template.
   *
   * @return the template
   */
  private static CompressorCurveTemplate createOverhungTemplate() {
    // Overhung: cantilever design, simple maintenance, smaller duties
    double refSpeed = 8000.0;

    double[] speeds = {5600, 6400, 7200, 8000};

    // Smaller capacity typical
    double[][] flows = {{1800, 2300, 2900, 3500, 4000}, {2060, 2630, 3310, 4000, 4570},
        {2310, 2960, 3730, 4500, 5140}, {2570, 3290, 4140, 5000, 5710}};

    // Low-medium head
    double[][] heads =
        {{32, 31, 28, 24, 19}, {42, 40, 37, 31, 25}, {53, 51, 46, 40, 32}, {65, 63, 57, 49, 39}};

    // Moderate efficiency for simple design
    double[][] efficiencies = repeatEfficiency(4, new double[] {71, 73, 74, 72, 68});

    return new CompressorCurveTemplate("OVERHUNG", refSpeed, speeds, flows, heads, efficiencies);
  }

  /**
   * Get a template by name.
   *
   * <p>
   * Supports various naming conventions - spaces, hyphens, and underscores are interchangeable. The
   * search is case-insensitive.
   * </p>
   *
   * @param templateName Name of the template (e.g., "CENTRIFUGAL_STANDARD", "pipeline", "gas-lift")
   * @return The matching template, or CENTRIFUGAL_STANDARD if not found
   */
  public static CompressorCurveTemplate getTemplate(String templateName) {
    if (templateName == null) {
      return CENTRIFUGAL_STANDARD;
    }
    String upperName = templateName.toUpperCase().replace(" ", "_").replace("-", "_");

    // Basic centrifugal variants
    if (upperName.contains("HIGH_FLOW") || upperName.contains("HIGHFLOW")) {
      return CENTRIFUGAL_HIGH_FLOW;
    } else if (upperName.contains("HIGH_HEAD") || upperName.contains("HIGHHEAD")) {
      return CENTRIFUGAL_HIGH_HEAD;
    }

    // Application-based templates
    if (upperName.contains("PIPELINE")) {
      return PIPELINE;
    } else if (upperName.contains("EXPORT")) {
      return EXPORT;
    } else if (upperName.contains("INJECTION")) {
      return INJECTION;
    } else if (upperName.contains("GAS_LIFT") || upperName.contains("GASLIFT")
        || upperName.contains("LIFT")) {
      return GAS_LIFT;
    } else if (upperName.contains("REFRIGERAT") || upperName.contains("LNG")
        || upperName.contains("COOLING")) {
      return REFRIGERATION;
    } else if (upperName.contains("BOOSTER") || upperName.contains("BOOST")) {
      return BOOSTER;
    }

    // Compressor type templates
    if (upperName.contains("SINGLE_STAGE") || upperName.contains("SINGLESTAGE")
        || upperName.equals("SINGLE")) {
      return SINGLE_STAGE;
    } else if (upperName.contains("MULTISTAGE") || upperName.contains("MULTI_STAGE")
        || upperName.contains("BARREL") || upperName.contains("INLINE")) {
      return MULTISTAGE_INLINE;
    } else if (upperName.contains("INTEGRALLY") || upperName.contains("GEARED")
        || upperName.contains("IGC")) {
      return INTEGRALLY_GEARED;
    } else if (upperName.contains("OVERHUNG") || upperName.contains("CANTILEVER")) {
      return OVERHUNG;
    }

    // Default
    return CENTRIFUGAL_STANDARD;
  }

  /**
   * Get all available template names.
   *
   * @return Array of template names
   */
  public static String[] getAvailableTemplates() {
    return new String[] {
        // Basic variants
        "CENTRIFUGAL_STANDARD", "CENTRIFUGAL_HIGH_FLOW", "CENTRIFUGAL_HIGH_HEAD",
        // Application-based
        "PIPELINE", "EXPORT", "INJECTION", "GAS_LIFT", "REFRIGERATION", "BOOSTER",
        // Compressor types
        "SINGLE_STAGE", "MULTISTAGE_INLINE", "INTEGRALLY_GEARED", "OVERHUNG"};
  }

  /**
   * Get templates by category.
   *
   * @param category The category: "basic", "application", or "type"
   * @return Array of template names in that category
   */
  public static String[] getTemplatesByCategory(String category) {
    if (category == null) {
      return getAvailableTemplates();
    }
    String cat = category.toLowerCase();
    if (cat.equals("basic") || cat.equals("centrifugal")) {
      return new String[] {"CENTRIFUGAL_STANDARD", "CENTRIFUGAL_HIGH_FLOW",
          "CENTRIFUGAL_HIGH_HEAD"};
    } else if (cat.equals("application") || cat.equals("app")) {
      return new String[] {"PIPELINE", "EXPORT", "INJECTION", "GAS_LIFT", "REFRIGERATION",
          "BOOSTER"};
    } else if (cat.equals("type") || cat.equals("design")) {
      return new String[] {"SINGLE_STAGE", "MULTISTAGE_INLINE", "INTEGRALLY_GEARED", "OVERHUNG"};
    }
    return getAvailableTemplates();
  }

  /**
   * Get the original (unscaled) compressor chart from this template.
   *
   * <p>
   * This method returns a chart with the exact curve data stored in the template, without any
   * scaling. Useful for recreating the original reference curves.
   * </p>
   *
   * @return A new CompressorChartInterface with the original template curves
   */
  public CompressorChartInterface getOriginalChart() {
    return getOriginalChart("interpolate and extrapolate");
  }

  /**
   * Get the original (unscaled) compressor chart from this template with specified chart type.
   *
   * @param chartType Chart type: "simple", "interpolate", or "interpolate and extrapolate"
   * @return A new CompressorChartInterface with the original template curves
   */
  public CompressorChartInterface getOriginalChart(String chartType) {
    int numSpeeds = speeds.length;

    // Reconstruct original flows and heads from reduced values
    double[][] originalFlows = new double[numSpeeds][];
    double[][] originalHeads = new double[numSpeeds][];
    double[][] originalEfficiencies = new double[numSpeeds][];

    for (int i = 0; i < numSpeeds; i++) {
      int numPoints = reducedFlows[i].length;
      originalFlows[i] = new double[numPoints];
      originalHeads[i] = new double[numPoints];
      originalEfficiencies[i] = Arrays.copyOf(polytropicEfficiencies[i], numPoints);

      double speedRatio = speeds[i] / referenceSpeed;
      for (int j = 0; j < numPoints; j++) {
        // Reverse the normalization
        originalFlows[i][j] = reducedFlows[i][j] * speeds[i];
        originalHeads[i][j] = reducedHeads[i][j] * speedRatio * speedRatio;
      }
    }

    // Create and configure the chart
    CompressorChartInterface chart = createChart(chartType);
    chart.setUseCompressorChart(true);
    chart.setHeadUnit("kJ/kg");
    chart.setCurves(new double[0], speeds, originalFlows, originalHeads, originalEfficiencies);
    chart.generateSurgeCurve();
    chart.generateStoneWallCurve();

    return chart;
  }

  /**
   * Get the original speeds from the template.
   *
   * @return Array of speed values in RPM
   */
  public double[] getSpeeds() {
    return Arrays.copyOf(speeds, speeds.length);
  }

  /**
   * Scale the template curves to match a target speed, preserving the relative curve shapes.
   *
   * <p>
   * This scales all curves proportionally so that the reference speed maps to the target speed.
   * Uses fan laws: flow scales with speed, head scales with speed².
   * </p>
   *
   * @param targetReferenceSpeed The target speed that corresponds to the template's reference speed
   * @return A new CompressorChartInterface with scaled curves
   */
  public CompressorChartInterface scaleToSpeed(double targetReferenceSpeed) {
    return scaleToSpeed(targetReferenceSpeed, "interpolate and extrapolate");
  }

  /**
   * Scale the template curves to match a target speed with specified chart type.
   *
   * @param targetReferenceSpeed The target speed that corresponds to the template's reference speed
   * @param chartType Chart type: "simple", "interpolate", or "interpolate and extrapolate"
   * @return A new CompressorChartInterface with scaled curves
   */
  public CompressorChartInterface scaleToSpeed(double targetReferenceSpeed, String chartType) {
    double speedScaleFactor = targetReferenceSpeed / referenceSpeed;
    int numSpeeds = speeds.length;

    double[] scaledSpeeds = new double[numSpeeds];
    double[][] scaledFlows = new double[numSpeeds][];
    double[][] scaledHeads = new double[numSpeeds][];
    double[][] scaledEfficiencies = new double[numSpeeds][];

    for (int i = 0; i < numSpeeds; i++) {
      scaledSpeeds[i] = speeds[i] * speedScaleFactor;

      int numPoints = reducedFlows[i].length;
      scaledFlows[i] = new double[numPoints];
      scaledHeads[i] = new double[numPoints];
      scaledEfficiencies[i] = Arrays.copyOf(polytropicEfficiencies[i], numPoints);

      double speedRatio = speeds[i] / referenceSpeed;
      for (int j = 0; j < numPoints; j++) {
        // Scale flow linearly with speed, head with speed²
        scaledFlows[i][j] = reducedFlows[i][j] * scaledSpeeds[i];
        scaledHeads[i][j] =
            reducedHeads[i][j] * speedRatio * speedRatio * speedScaleFactor * speedScaleFactor;
      }
    }

    CompressorChartInterface chart = createChart(chartType);
    chart.setUseCompressorChart(true);
    chart.setHeadUnit("kJ/kg");
    chart.setCurves(new double[0], scaledSpeeds, scaledFlows, scaledHeads, scaledEfficiencies);
    chart.generateSurgeCurve();
    chart.generateStoneWallCurve();

    return chart;
  }
}
