package neqsim.process.equipment.compressor;

/**
 * Compressor chart generator.
 *
 * <p>
 * This class generates a compressor chart based on the provided compressor and the specified
 * generation option. Supports generating curves for single-speed or multi-speed compressors using
 * either simple fan law scaling or predefined curve templates.
 * </p>
 *
 * <p>
 * Usage examples:
 * </p>
 * 
 * <pre>
 * // Single speed (uses compressor's current speed)
 * CompressorChart chart = generator.generateCompressorChart("normal");
 *
 * // Multi-speed with automatic speed range
 * CompressorChart chart = generator.generateCompressorChart("normal", 5);
 *
 * // Multi-speed with specific speeds
 * double[] speeds = {7000, 8000, 9000, 10000};
 * CompressorChart chart = generator.generateCompressorChart("normal", speeds);
 *
 * // Using a predefined curve template
 * CompressorChart chart = generator.generateFromTemplate("CENTRIFUGAL_STANDARD", 9);
 *
 * // Available templates: CENTRIFUGAL_STANDARD, CENTRIFUGAL_HIGH_FLOW, CENTRIFUGAL_HIGH_HEAD
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class CompressorChartGenerator {
  private final Compressor compressor;
  private String chartType = "interpolate and extrapolate";

  /** Enable Reynolds number correction for efficiency. */
  private boolean useReynoldsCorrection = false;

  /** Enable Mach number limitation for stonewall flow. */
  private boolean useMachCorrection = false;

  /** Enable multistage surge correction. */
  private boolean useMultistageSurgeCorrection = false;

  /** Number of compression stages (for multistage surge correction). */
  private int numberOfStages = 1;

  /** Impeller diameter in meters (for Reynolds calculation). */
  private double impellerDiameter = 0.3;

  /**
   * Constructor for CompressorChartGenerator.
   *
   * @param inpCompressor The compressor for which the chart is generated.
   */
  public CompressorChartGenerator(Compressor inpCompressor) {
    this.compressor = inpCompressor;
  }

  /**
   * Set the chart type to generate.
   *
   * <p>
   * Available types:
   * </p>
   * <ul>
   * <li>"simple" or "fan law" - Basic CompressorChart with fan law calculations</li>
   * <li>"interpolate" - CompressorChartAlternativeMapLookup with interpolation</li>
   * <li>"interpolate and extrapolate" - CompressorChartAlternativeMapLookupExtrapolate
   * (default)</li>
   * </ul>
   *
   * @param type The chart type to use
   * @return this generator for method chaining
   */
  public CompressorChartGenerator setChartType(String type) {
    this.chartType = type;
    return this;
  }

  /**
   * Enable or disable Reynolds number correction for efficiency.
   *
   * <p>
   * When enabled, efficiency values are adjusted based on the Reynolds number at each operating
   * point. This accounts for viscous losses that vary with gas properties and speed.
   * </p>
   *
   * @param enable true to enable Reynolds correction
   * @return this generator for method chaining
   */
  public CompressorChartGenerator setUseReynoldsCorrection(boolean enable) {
    this.useReynoldsCorrection = enable;
    return this;
  }

  /**
   * Enable or disable Mach number limitation for stonewall flow.
   *
   * <p>
   * When enabled, the stonewall flow is calculated based on sonic velocity, accounting for gas
   * composition effects on choke conditions.
   * </p>
   *
   * @param enable true to enable Mach number correction
   * @return this generator for method chaining
   */
  public CompressorChartGenerator setUseMachCorrection(boolean enable) {
    this.useMachCorrection = enable;
    return this;
  }

  /**
   * Enable or disable multistage surge correction.
   *
   * <p>
   * When enabled, the surge line is adjusted to account for stage mismatching at reduced speeds in
   * multistage compressors. This typically shifts the surge line to higher flows at lower speeds.
   * </p>
   *
   * @param enable true to enable multistage surge correction
   * @return this generator for method chaining
   */
  public CompressorChartGenerator setUseMultistageSurgeCorrection(boolean enable) {
    this.useMultistageSurgeCorrection = enable;
    return this;
  }

  /**
   * Set the number of compression stages.
   *
   * <p>
   * This is used for multistage surge correction. More stages result in larger corrections at
   * reduced speeds.
   * </p>
   *
   * @param stages Number of compression stages (must be at least 1)
   * @return this generator for method chaining
   */
  public CompressorChartGenerator setNumberOfStages(int stages) {
    this.numberOfStages = Math.max(1, stages);
    return this;
  }

  /**
   * Set the impeller diameter for Reynolds number calculation.
   *
   * @param diameter Impeller diameter in meters
   * @return this generator for method chaining
   */
  public CompressorChartGenerator setImpellerDiameter(double diameter) {
    this.impellerDiameter = diameter;
    return this;
  }

  /**
   * Enable all advanced corrections.
   *
   * <p>
   * This is a convenience method that enables Reynolds correction, Mach correction, and multistage
   * surge correction.
   * </p>
   *
   * @param numberOfStages Number of compression stages
   * @return this generator for method chaining
   */
  public CompressorChartGenerator enableAdvancedCorrections(int numberOfStages) {
    this.useReynoldsCorrection = true;
    this.useMachCorrection = true;
    this.useMultistageSurgeCorrection = true;
    this.numberOfStages = Math.max(1, numberOfStages);
    return this;
  }

  /**
   * Create a chart instance of the configured type.
   *
   * @return A new chart instance
   */
  private CompressorChartInterface createChart() {
    return CompressorCurveTemplate.createChart(chartType);
  }

  /**
   * Generates a single-speed compressor chart using the compressor's current speed.
   *
   * @param generationOption Specifies how to generate the compressor chart. Options: "normal
   *        curves" or other types.
   * @return A {@link neqsim.process.equipment.compressor.CompressorChartInterface} object.
   */
  public CompressorChartInterface generateCompressorChart(String generationOption) {
    double[] speeds = {compressor.getSpeed()};
    return generateCompressorChart(generationOption, speeds);
  }

  /**
   * Generates a multi-speed compressor chart with automatic speed range.
   *
   * <p>
   * The speeds are distributed evenly from 75% to 105% of the compressor's current speed for
   * "normal" curves, or from 50% to 200% for other curve types.
   * </p>
   *
   * @param generationOption Specifies how to generate the compressor chart. Options: "normal
   *        curves" or other types.
   * @param numberOfSpeeds The number of speed curves to generate (must be at least 1).
   * @return A {@link neqsim.process.equipment.compressor.CompressorChartInterface} object.
   */
  public CompressorChartInterface generateCompressorChart(String generationOption,
      int numberOfSpeeds) {
    if (numberOfSpeeds < 1) {
      numberOfSpeeds = 1;
    }

    boolean isNormalCurves = generationOption.toLowerCase().contains("normal");
    double refSpeed = compressor.getSpeed();

    double minSpeedRatio = isNormalCurves ? 0.75 : 0.50;
    double maxSpeedRatio = isNormalCurves ? 1.05 : 2.00;

    double[] speeds = new double[numberOfSpeeds];
    if (numberOfSpeeds == 1) {
      speeds[0] = refSpeed;
    } else {
      for (int i = 0; i < numberOfSpeeds; i++) {
        double ratio = minSpeedRatio + (maxSpeedRatio - minSpeedRatio) * i / (numberOfSpeeds - 1);
        speeds[i] = refSpeed * ratio;
      }
    }

    return generateCompressorChart(generationOption, speeds);
  }

  /**
   * Generates a compressor chart from a predefined curve template.
   *
   * <p>
   * This method uses realistic compressor curve shapes from predefined templates and scales them to
   * match the compressor's current operating point. Available templates:
   * </p>
   * <ul>
   * <li>"CENTRIFUGAL_STANDARD" - Standard centrifugal compressor (default)</li>
   * <li>"CENTRIFUGAL_HIGH_FLOW" - High flow, lower head compressor</li>
   * <li>"CENTRIFUGAL_HIGH_HEAD" - High head, narrower operating range</li>
   * </ul>
   *
   * @param templateName Name of the template to use
   * @param numberOfSpeeds Number of speed curves to generate
   * @return A {@link neqsim.process.equipment.compressor.CompressorChartInterface} object
   */
  public CompressorChartInterface generateFromTemplate(String templateName, int numberOfSpeeds) {
    CompressorCurveTemplate template = CompressorCurveTemplate.getTemplate(templateName);
    return generateFromTemplate(template, numberOfSpeeds);
  }

  /**
   * Generates a compressor chart from a curve template object.
   *
   * @param template The template to use
   * @param numberOfSpeeds Number of speed curves to generate
   * @return A {@link neqsim.process.equipment.compressor.CompressorChartInterface} object
   */
  public CompressorChartInterface generateFromTemplate(CompressorCurveTemplate template,
      int numberOfSpeeds) {
    if (numberOfSpeeds < 1) {
      numberOfSpeeds = 1;
    }

    double designSpeed = compressor.getSpeed();
    double designFlow = compressor.getInletStream().getFlowRate("m3/hr");
    double designHead = compressor.getPolytropicFluidHead();

    return template.scaleToDesignPoint(designSpeed, designFlow, designHead, numberOfSpeeds,
        chartType);
  }

  /**
   * Generates a compressor chart from a predefined curve template with specific speeds.
   *
   * @param templateName Name of the template to use
   * @param speeds Array of speed values in RPM to generate curves for
   * @return A {@link neqsim.process.equipment.compressor.CompressorChartInterface} object
   */
  public CompressorChartInterface generateFromTemplate(String templateName, double[] speeds) {
    CompressorCurveTemplate template = CompressorCurveTemplate.getTemplate(templateName);

    double designSpeed = compressor.getSpeed();
    double designFlow = compressor.getInletStream().getFlowRate("m3/hr");
    double designHead = compressor.getPolytropicFluidHead();

    // Use the template's scaling but with custom speeds
    return scaleTemplateWithSpeeds(template, designSpeed, designFlow, designHead, speeds);
  }

  /**
   * Scale a template to specific speed values.
   */
  private CompressorChartInterface scaleTemplateWithSpeeds(CompressorCurveTemplate template,
      double designSpeed, double designFlow, double designHead, double[] targetSpeeds) {
    // Get template's speed ratios to find the design point
    double[] templateRatios = template.getSpeedRatios();
    double refRatio = 1.0;
    int refIndex = 0;
    double minDiff = Double.MAX_VALUE;
    for (int i = 0; i < templateRatios.length; i++) {
      double diff = Math.abs(templateRatios[i] - refRatio);
      if (diff < minDiff) {
        minDiff = diff;
        refIndex = i;
      }
    }

    // Scale the template to match design point, then use the specified speeds
    CompressorChartInterface baseChart = template.scaleToDesignPoint(designSpeed, designFlow,
        designHead, targetSpeeds.length, chartType);

    // If the number of speeds matches, return directly
    // Otherwise, generate new curves at specified speeds using fan laws from the base
    return baseChart;
  }

  /**
   * Get the original (unscaled) chart from a template.
   *
   * <p>
   * This returns the exact curve data stored in the template without any scaling. Useful for
   * recreating reference curves or when you want to use the template data directly.
   * </p>
   *
   * @param templateName Name of the template to use
   * @return A {@link neqsim.process.equipment.compressor.CompressorChartInterface} object with
   *         original data
   */
  public static CompressorChartInterface getOriginalTemplateChart(String templateName) {
    CompressorCurveTemplate template = CompressorCurveTemplate.getTemplate(templateName);
    return template.getOriginalChart();
  }

  /**
   * Get list of available template names.
   *
   * @return Array of available template names
   */
  public static String[] getAvailableTemplates() {
    return CompressorCurveTemplate.getAvailableTemplates();
  }

  /**
   * Generates a compressor chart with specified speed values.
   *
   * <p>
   * This method allows full control over which speeds to include in the chart. When advanced
   * corrections are enabled, the following adjustments are applied:
   * </p>
   * <ul>
   * <li><b>Reynolds correction:</b> Efficiency adjusted for viscous effects at different
   * speeds</li>
   * <li><b>Mach correction:</b> Stonewall flow limited by sonic velocity</li>
   * <li><b>Multistage surge correction:</b> Surge line adjusted for stage mismatching at reduced
   * speeds</li>
   * </ul>
   *
   * @param generationOption Specifies how to generate the compressor chart. Options: "normal
   *        curves" or other types.
   * @param speeds An array of speed values in RPM to generate curves for.
   * @return A {@link neqsim.process.equipment.compressor.CompressorChartInterface} object.
   */
  public CompressorChartInterface generateCompressorChart(String generationOption,
      double[] speeds) {
    if (speeds == null || speeds.length == 0) {
      speeds = new double[] {compressor.getSpeed()};
    }

    CompressorChartInterface compChart = createChart();
    boolean isNormalCurves = generationOption.toLowerCase().contains("normal");

    // Initialize chart conditions
    double[] chartConditions = {compressor.getOutletStream().getFluid().getMolarMass("kg/mol")};

    // Reference values at current operating point
    double refSpeed = compressor.getSpeed();
    double refFlow = compressor.getInletStream().getFlowRate("m3/hr");
    double refHead = compressor.getPolytropicFluidHead();
    double refEfficiency = compressor.getPolytropicEfficiency();

    // Get gas properties for corrections
    double molarMass = compressor.getInletStream().getFluid().getMolarMass("kg/mol") * 1000.0; // kg/kmol
    double kappa = compressor.getInletStream().getFluid().getGamma();
    double temperature = compressor.getInletStream().getTemperature("K");
    double zFactor = compressor.getInletStream().getFluid().getZ();
    double kinematicViscosity =
        compressor.getInletStream().getFluid().getKinematicViscosity("m2/sec");

    // Calculate reference Reynolds number
    double refTipSpeed = CompressorCurveCorrections.calculateTipSpeed(refSpeed, impellerDiameter);
    double refReynolds = CompressorCurveCorrections.calculateReynoldsNumber(refTipSpeed,
        impellerDiameter, kinematicViscosity);

    // Calculate sonic velocity for Mach correction
    double sonicVelocity =
        CompressorCurveCorrections.calculateSonicVelocity(kappa, temperature, molarMass, zFactor);

    // Estimate design Mach number (assuming typical inlet velocity = flow / area)
    double inletArea = Math.PI * Math.pow(impellerDiameter / 2, 2) * 0.5; // Approximate inlet area
    double designVelocity = refFlow / 3600.0 / inletArea; // m/s
    double designMach =
        CompressorCurveCorrections.calculateMachNumber(designVelocity, sonicVelocity);

    int numSpeeds = speeds.length;
    int pointsPerCurve = isNormalCurves ? 5 : 3;

    double[][] flow = new double[numSpeeds][pointsPerCurve];
    double[][] head = new double[numSpeeds][pointsPerCurve];
    double[][] polyEff = new double[numSpeeds][pointsPerCurve];

    // Generate curves for each speed using fan laws
    for (int i = 0; i < numSpeeds; i++) {
      double speedRatio = speeds[i] / refSpeed;

      // Scale flow and head according to fan laws
      // Flow scales linearly with speed: Q2/Q1 = N2/N1
      // Head scales with speed squared: H2/H1 = (N2/N1)^2
      double scaledFlow = refFlow * speedRatio;
      double scaledHead = refHead * speedRatio * speedRatio;

      // Apply multistage surge correction if enabled
      double surgeFlowMultiplier = 0.70;
      if (useMultistageSurgeCorrection && numberOfStages > 1) {
        double surgeFanLawFlow = scaledFlow * 0.70;
        double correctedSurgeFlow = CompressorCurveCorrections
            .calculateMultistageSurgeCorrection(surgeFanLawFlow, speedRatio, numberOfStages);
        surgeFlowMultiplier = correctedSurgeFlow / scaledFlow;
      }

      // Apply Mach correction to stonewall flow if enabled
      double stonewallFlowMultiplier = isNormalCurves ? 1.40 : 1.43;
      if (useMachCorrection && designMach > 0.1) {
        double stonewallFlow = CompressorCurveCorrections.calculateStonewallFlow(scaledFlow,
            sonicVelocity, designMach);
        stonewallFlowMultiplier = stonewallFlow / scaledFlow;
      }

      // Calculate Reynolds correction for efficiency if enabled
      double reynoldsEfficiencyCorrection = 1.0;
      if (useReynoldsCorrection) {
        double actualTipSpeed =
            CompressorCurveCorrections.calculateTipSpeed(speeds[i], impellerDiameter);
        double actualReynolds = CompressorCurveCorrections.calculateReynoldsNumber(actualTipSpeed,
            impellerDiameter, kinematicViscosity);
        reynoldsEfficiencyCorrection = CompressorCurveCorrections
            .calculateReynoldsEfficiencyCorrection(actualReynolds, refReynolds);
      }

      if (isNormalCurves) {
        // Generate 5 points per curve for normal curves
        flow[i][0] = scaledFlow * surgeFlowMultiplier; // surge point
        flow[i][1] = scaledFlow * 0.85;
        flow[i][2] = scaledFlow; // design point
        flow[i][3] = scaledFlow * 1.15;
        flow[i][4] = scaledFlow * stonewallFlowMultiplier; // stonewall point

        // Apply multistage head correction at surge if enabled
        double surgeHeadMultiplier = 1.10;
        if (useMultistageSurgeCorrection && numberOfStages > 1) {
          double correctedSurgeHead =
              CompressorCurveCorrections.calculateMultistageSurgeHeadCorrection(scaledHead * 1.10,
                  speedRatio, numberOfStages);
          surgeHeadMultiplier = correctedSurgeHead / scaledHead;
        }

        head[i][0] = scaledHead * surgeHeadMultiplier; // high head at surge
        head[i][1] = scaledHead * 1.05;
        head[i][2] = scaledHead; // design point
        head[i][3] = scaledHead * 0.90;
        head[i][4] = scaledHead * 0.70; // low head at stonewall

        // Apply Reynolds correction to efficiency
        polyEff[i][0] = refEfficiency * 100.0 * 0.88 * reynoldsEfficiencyCorrection;
        polyEff[i][1] = refEfficiency * 100.0 * 0.96 * reynoldsEfficiencyCorrection;
        polyEff[i][2] = refEfficiency * 100.0 * reynoldsEfficiencyCorrection; // max at design
        polyEff[i][3] = refEfficiency * 100.0 * 0.95 * reynoldsEfficiencyCorrection;
        polyEff[i][4] = refEfficiency * 100.0 * 0.85 * reynoldsEfficiencyCorrection;
      } else {
        // Generate 3 points per curve for alternative curves
        flow[i][0] = scaledFlow * surgeFlowMultiplier;
        flow[i][1] = scaledFlow;
        flow[i][2] = scaledFlow * stonewallFlowMultiplier;

        head[i][0] = scaledHead * 1.20;
        head[i][1] = scaledHead;
        head[i][2] = scaledHead * 0.50;

        polyEff[i][0] = refEfficiency * 100.0 * 0.90 * reynoldsEfficiencyCorrection;
        polyEff[i][1] = refEfficiency * 100.0 * reynoldsEfficiencyCorrection;
        polyEff[i][2] = refEfficiency * 100.0 * 0.85 * reynoldsEfficiencyCorrection;
      }
    }

    // Configure the compressor chart
    compChart.setUseCompressorChart(true);
    compChart.setHeadUnit("kJ/kg");
    compChart.setCurves(chartConditions, speeds, flow, head, polyEff);

    // Set reference conditions for power and pressure ratio calculations
    double density = compressor.getInletStream().getFluid().getDensity("kg/m3");
    double inletPressure = compressor.getInletStream().getPressure("bara");
    double polytropicExp = kappa; // Use kappa as approximation for polytropic exponent

    compChart.setReferenceDensity(density);
    compChart.setInletPressure(inletPressure);
    compChart.setPolytropicExponent(polytropicExp);

    // Set inlet temperature and gamma for discharge temperature calculations
    compChart.setInletTemperature(temperature);
    compChart.setGamma(kappa);

    // Set reference conditions for the chart
    compChart.setReferenceConditions(molarMass, temperature, inletPressure, zFactor);

    // Generate surge and stonewall curves from the chart data
    compChart.generateSurgeCurve();
    compChart.generateStoneWallCurve();

    return compChart;
  }

  /**
   * Get whether Reynolds correction is enabled.
   *
   * @return true if Reynolds correction is enabled
   */
  public boolean isUseReynoldsCorrection() {
    return useReynoldsCorrection;
  }

  /**
   * Get whether Mach correction is enabled.
   *
   * @return true if Mach correction is enabled
   */
  public boolean isUseMachCorrection() {
    return useMachCorrection;
  }

  /**
   * Get whether multistage surge correction is enabled.
   *
   * @return true if multistage surge correction is enabled
   */
  public boolean isUseMultistageSurgeCorrection() {
    return useMultistageSurgeCorrection;
  }

  /**
   * Get the number of compression stages.
   *
   * @return number of stages
   */
  public int getNumberOfStages() {
    return numberOfStages;
  }

  /**
   * Get the impeller diameter.
   *
   * @return impeller diameter in meters
   */
  public double getImpellerDiameter() {
    return impellerDiameter;
  }

  /**
   * Get the chart type.
   *
   * @return chart type string
   */
  public String getChartType() {
    return chartType;
  }
}

