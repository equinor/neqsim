package neqsim.process.equipment.compressor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * Compressor chart with multiple performance maps at different molecular weights.
 *
 * <p>
 * This class allows storing multiple compressor maps, each measured at a different molecular weight
 * (or gas composition), and interpolates between them based on the actual operating molecular
 * weight.
 * </p>
 *
 * <p>
 * Use case: When compressor maps are measured at several discrete MW values (e.g., 18, 20, 22
 * g/mol), this class interpolates the performance parameters (head, efficiency) for an actual MW
 * that falls between the measured values.
 * </p>
 *
 * <p>
 * Interpolation method: Linear interpolation between the two nearest MW maps. Extrapolation uses
 * the nearest boundary map.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class CompressorChartMWInterpolation extends CompressorChart {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(CompressorChartMWInterpolation.class);

  /** List of MW values for which maps are defined, sorted in ascending order. */
  private List<Double> mapMolecularWeights = new ArrayList<Double>();

  /** List of compressor charts corresponding to each MW. */
  private List<CompressorChart> mapCharts = new ArrayList<CompressorChart>();

  /** Current operating molecular weight for interpolation. */
  private double operatingMW = Double.NaN;

  /** Flag indicating if interpolation is needed. */
  private boolean interpolationEnabled = true;

  /** Flag to auto-generate surge curves when maps are added. */
  private boolean autoGenerateSurgeCurves = false;

  /** Flag to auto-generate stone wall curves when maps are added. */
  private boolean autoGenerateStoneWallCurves = false;

  /** Flag to allow extrapolation outside the MW range. */
  private boolean allowExtrapolation = false;

  /** Flag to automatically use the inlet stream's molecular weight. */
  private boolean useActualMW = true;

  /** Reference to inlet stream for automatic MW detection. */
  private transient StreamInterface inletStream = null;

  /**
   * Default constructor for CompressorChartMWInterpolation.
   */
  public CompressorChartMWInterpolation() {
    super();
  }

  /**
   * Add a compressor map at a specific molecular weight.
   *
   * <p>
   * Multiple maps can be added at different MW values. The maps are stored sorted by MW.
   * </p>
   *
   * @param molecularWeight the molecular weight (g/mol) at which this map was measured
   * @param chartConditions reference conditions [temp °C, pres bara, density kg/m³, MW g/mol]
   * @param speed array of speed values (RPM)
   * @param flow 2D array of flow values for each speed curve (m³/hr)
   * @param head 2D array of head values for each speed curve (kJ/kg or meter)
   * @param polyEff 2D array of polytropic efficiency values for each speed curve (%)
   */
  public void addMapAtMW(double molecularWeight, double[] chartConditions, double[] speed,
      double[][] flow, double[][] head, double[][] polyEff) {
    addMapAtMW(molecularWeight, chartConditions, speed, flow, head, flow, polyEff);
  }

  /**
   * Add a compressor map at a specific molecular weight.
   *
   * <p>
   * Multiple maps can be added at different MW values. The maps are stored sorted by MW.
   * </p>
   *
   * @param molecularWeight the molecular weight (g/mol) at which this map was measured
   * @param chartConditions reference conditions [temp °C, pres bara, density kg/m³, MW g/mol]
   * @param speed array of speed values (RPM)
   * @param flow 2D array of flow values for each speed curve (m³/hr)
   * @param head 2D array of head values for each speed curve (kJ/kg or meter)
   * @param flowPolyEff 2D array of flow values for efficiency curves (m³/hr)
   * @param polyEff 2D array of polytropic efficiency values for each speed curve (%)
   */
  public void addMapAtMW(double molecularWeight, double[] chartConditions, double[] speed,
      double[][] flow, double[][] head, double[][] flowPolyEff, double[][] polyEff) {
    // Create a new chart for this MW
    CompressorChart chart = new CompressorChart();
    chart.setCurves(chartConditions, speed, flow, head, flowPolyEff, polyEff);
    chart.setHeadUnit(getHeadUnit());

    // Find insertion point to keep list sorted
    int insertIndex = 0;
    for (int i = 0; i < mapMolecularWeights.size(); i++) {
      if (molecularWeight > mapMolecularWeights.get(i).doubleValue()) {
        insertIndex = i + 1;
      }
    }

    mapMolecularWeights.add(insertIndex, Double.valueOf(molecularWeight));
    mapCharts.add(insertIndex, chart);

    // Auto-generate surge and stone wall curves if enabled
    if (autoGenerateSurgeCurves) {
      chart.generateSurgeCurve();
    }
    if (autoGenerateStoneWallCurves) {
      chart.generateStoneWallCurve();
    }

    // If this is the first map, set it as the base chart
    if (mapCharts.size() == 1) {
      super.setCurves(chartConditions, speed, flow, head, flowPolyEff, polyEff);
      if (autoGenerateSurgeCurves) {
        super.generateSurgeCurve();
      }
      if (autoGenerateStoneWallCurves) {
        super.generateStoneWallCurve();
      }
    }

    logger.debug("Added compressor map at MW = {} g/mol. Total maps: {}", molecularWeight,
        mapCharts.size());
  }

  /**
   * Set the operating molecular weight for interpolation.
   *
   * <p>
   * The polytropic head and efficiency will be interpolated between maps based on this MW.
   * </p>
   *
   * @param mw the operating molecular weight in g/mol
   */
  public void setOperatingMW(double mw) {
    this.operatingMW = mw;
  }

  /**
   * Get the operating molecular weight.
   *
   * @return the operating molecular weight in g/mol
   */
  public double getOperatingMW() {
    return operatingMW;
  }

  /**
   * Enable or disable MW interpolation.
   *
   * <p>
   * When disabled, the chart behaves like a standard CompressorChart using the first added map.
   * </p>
   *
   * @param enabled true to enable interpolation, false to disable
   */
  public void setInterpolationEnabled(boolean enabled) {
    this.interpolationEnabled = enabled;
  }

  /**
   * Check if MW interpolation is enabled.
   *
   * @return true if interpolation is enabled
   */
  public boolean isInterpolationEnabled() {
    return interpolationEnabled;
  }

  /**
   * Enable auto-generation of surge curves when maps are added.
   *
   * @param autoGenerate true to auto-generate surge curves
   */
  public void setAutoGenerateSurgeCurves(boolean autoGenerate) {
    this.autoGenerateSurgeCurves = autoGenerate;
  }

  /**
   * Check if auto-generation of surge curves is enabled.
   *
   * @return true if auto-generation is enabled
   */
  public boolean isAutoGenerateSurgeCurves() {
    return autoGenerateSurgeCurves;
  }

  /**
   * Enable auto-generation of stone wall curves when maps are added.
   *
   * @param autoGenerate true to auto-generate stone wall curves
   */
  public void setAutoGenerateStoneWallCurves(boolean autoGenerate) {
    this.autoGenerateStoneWallCurves = autoGenerate;
  }

  /**
   * Check if auto-generation of stone wall curves is enabled.
   *
   * @return true if auto-generation is enabled
   */
  public boolean isAutoGenerateStoneWallCurves() {
    return autoGenerateStoneWallCurves;
  }

  /**
   * Get the number of MW maps defined.
   *
   * @return number of MW maps
   */
  public int getNumberOfMaps() {
    return mapCharts.size();
  }

  /**
   * Get the list of molecular weights for which maps are defined.
   *
   * @return unmodifiable list of MW values in ascending order
   */
  public List<Double> getMapMolecularWeights() {
    return Collections.unmodifiableList(mapMolecularWeights);
  }

  /**
   * Enable or disable extrapolation outside the MW range.
   *
   * <p>
   * When enabled, linear extrapolation is used for MW values outside the range of defined maps.
   * When disabled (default), the nearest boundary map is used.
   * </p>
   *
   * @param allow true to enable extrapolation, false to use boundary maps
   */
  public void setAllowExtrapolation(boolean allow) {
    this.allowExtrapolation = allow;
  }

  /**
   * Check if extrapolation outside the MW range is allowed.
   *
   * @return true if extrapolation is allowed
   */
  public boolean isAllowExtrapolation() {
    return allowExtrapolation;
  }

  /**
   * Set whether to automatically use the inlet stream's molecular weight.
   *
   * <p>
   * When enabled (default), the operating MW is automatically updated from the inlet stream's fluid
   * molecular weight before each calculation. This ensures the compressor chart uses the actual gas
   * composition.
   * </p>
   *
   * @param useActual true to use inlet stream's MW automatically
   */
  public void setUseActualMW(boolean useActual) {
    this.useActualMW = useActual;
  }

  /**
   * Check if automatic MW detection from inlet stream is enabled.
   *
   * @return true if using inlet stream's MW automatically
   */
  public boolean isUseActualMW() {
    return useActualMW;
  }

  /**
   * Set the inlet stream reference for automatic MW detection.
   *
   * @param stream the inlet stream
   */
  public void setInletStream(StreamInterface stream) {
    this.inletStream = stream;
  }

  /**
   * Get the inlet stream reference.
   *
   * @return the inlet stream, or null if not set
   */
  public StreamInterface getInletStream() {
    return inletStream;
  }

  /**
   * Update the operating MW from the inlet stream's fluid.
   *
   * <p>
   * Called automatically before calculations if useActualMW is enabled.
   * </p>
   */
  private void updateOperatingMWFromStream() {
    if (useActualMW && inletStream != null && inletStream.getFluid() != null) {
      // getMolarMass returns kg/mol, convert to g/mol for consistency with chart data
      double fluidMW = inletStream.getFluid().getMolarMass() * 1000.0;
      this.operatingMW = fluidMW;
    }
  }

  /**
   * Find the two nearest MW maps and their interpolation weight.
   *
   * @param mw the target molecular weight
   * @return InterpolationIndices containing lower index, upper index, and interpolation fraction
   */
  private InterpolationIndices findInterpolationIndices(double mw) {
    if (mapMolecularWeights.isEmpty()) {
      return new InterpolationIndices(-1, -1, 0.0);
    }

    if (mapMolecularWeights.size() == 1) {
      return new InterpolationIndices(0, 0, 0.0);
    }

    // Find bounding indices
    int lowerIdx = -1;
    int upperIdx = -1;

    for (int i = 0; i < mapMolecularWeights.size(); i++) {
      double mapMW = mapMolecularWeights.get(i).doubleValue();
      if (mapMW <= mw) {
        lowerIdx = i;
      }
      if (mapMW >= mw && upperIdx == -1) {
        upperIdx = i;
      }
    }

    // Handle extrapolation cases
    if (lowerIdx == -1) {
      // MW is below all maps
      if (allowExtrapolation && mapMolecularWeights.size() >= 2) {
        // Extrapolate using first two maps
        double mw0 = mapMolecularWeights.get(0).doubleValue();
        double mw1 = mapMolecularWeights.get(1).doubleValue();
        double fraction = (mw - mw0) / (mw1 - mw0); // Will be negative for extrapolation below
        return new InterpolationIndices(0, 1, fraction);
      }
      // Use lowest map (no extrapolation)
      return new InterpolationIndices(0, 0, 0.0);
    }
    if (upperIdx == -1) {
      // MW is above all maps
      int lastIdx = mapMolecularWeights.size() - 1;
      if (allowExtrapolation && mapMolecularWeights.size() >= 2) {
        // Extrapolate using last two maps
        double mwLast = mapMolecularWeights.get(lastIdx).doubleValue();
        double mwSecondLast = mapMolecularWeights.get(lastIdx - 1).doubleValue();
        double fraction = (mw - mwSecondLast) / (mwLast - mwSecondLast); // Will be > 1 for
                                                                         // extrapolation above
        return new InterpolationIndices(lastIdx - 1, lastIdx, fraction);
      }
      // Use highest map (no extrapolation)
      return new InterpolationIndices(lastIdx, lastIdx, 0.0);
    }

    // Normal interpolation case
    if (lowerIdx == upperIdx) {
      return new InterpolationIndices(lowerIdx, upperIdx, 0.0);
    }

    double lowerMW = mapMolecularWeights.get(lowerIdx).doubleValue();
    double upperMW = mapMolecularWeights.get(upperIdx).doubleValue();
    double fraction = (mw - lowerMW) / (upperMW - lowerMW);

    return new InterpolationIndices(lowerIdx, upperIdx, fraction);
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Returns the polytropic head interpolated between MW maps based on the operating MW.
   * </p>
   */
  @Override
  public double getPolytropicHead(double flow, double speed) {
    updateOperatingMWFromStream();
    if (!interpolationEnabled || mapCharts.size() < 2 || Double.isNaN(operatingMW)) {
      return super.getPolytropicHead(flow, speed);
    }

    InterpolationIndices indices = findInterpolationIndices(operatingMW);

    if (indices.lowerIndex == indices.upperIndex) {
      return mapCharts.get(indices.lowerIndex).getPolytropicHead(flow, speed);
    }

    double headLower = mapCharts.get(indices.lowerIndex).getPolytropicHead(flow, speed);
    double headUpper = mapCharts.get(indices.upperIndex).getPolytropicHead(flow, speed);

    return linearInterpolate(headLower, headUpper, indices.fraction);
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Returns the polytropic efficiency interpolated between MW maps based on the operating MW.
   * </p>
   */
  @Override
  public double getPolytropicEfficiency(double flow, double speed) {
    updateOperatingMWFromStream();
    if (!interpolationEnabled || mapCharts.size() < 2 || Double.isNaN(operatingMW)) {
      return super.getPolytropicEfficiency(flow, speed);
    }

    InterpolationIndices indices = findInterpolationIndices(operatingMW);

    if (indices.lowerIndex == indices.upperIndex) {
      return mapCharts.get(indices.lowerIndex).getPolytropicEfficiency(flow, speed);
    }

    double effLower = mapCharts.get(indices.lowerIndex).getPolytropicEfficiency(flow, speed);
    double effUpper = mapCharts.get(indices.upperIndex).getPolytropicEfficiency(flow, speed);

    return linearInterpolate(effLower, effUpper, indices.fraction);
  }

  /**
   * Get the surge flow at a specific speed, interpolated between MW maps.
   *
   * @param speed the compressor speed in RPM
   * @return the surge flow interpolated for the operating MW
   */
  @Override
  public double getSurgeFlowAtSpeed(double speed) {
    updateOperatingMWFromStream();
    if (!interpolationEnabled || mapCharts.size() < 2 || Double.isNaN(operatingMW)) {
      return super.getSurgeFlowAtSpeed(speed);
    }

    InterpolationIndices indices = findInterpolationIndices(operatingMW);

    if (indices.lowerIndex == indices.upperIndex) {
      return mapCharts.get(indices.lowerIndex).getSurgeFlowAtSpeed(speed);
    }

    double surgeLower = mapCharts.get(indices.lowerIndex).getSurgeFlowAtSpeed(speed);
    double surgeUpper = mapCharts.get(indices.upperIndex).getSurgeFlowAtSpeed(speed);

    return linearInterpolate(surgeLower, surgeUpper, indices.fraction);
  }

  /**
   * Get the stone wall flow at a specific speed, interpolated between MW maps.
   *
   * @param speed the compressor speed in RPM
   * @return the stone wall flow interpolated for the operating MW
   */
  @Override
  public double getStoneWallFlowAtSpeed(double speed) {
    updateOperatingMWFromStream();
    if (!interpolationEnabled || mapCharts.size() < 2 || Double.isNaN(operatingMW)) {
      return super.getStoneWallFlowAtSpeed(speed);
    }

    InterpolationIndices indices = findInterpolationIndices(operatingMW);

    if (indices.lowerIndex == indices.upperIndex) {
      return mapCharts.get(indices.lowerIndex).getStoneWallFlowAtSpeed(speed);
    }

    double stoneLower = mapCharts.get(indices.lowerIndex).getStoneWallFlowAtSpeed(speed);
    double stoneUpper = mapCharts.get(indices.upperIndex).getStoneWallFlowAtSpeed(speed);

    return linearInterpolate(stoneLower, stoneUpper, indices.fraction);
  }

  /**
   * Get the surge head at a specific speed, interpolated between MW maps.
   *
   * @param speed the compressor speed in RPM
   * @return the surge head interpolated for the operating MW
   */
  @Override
  public double getSurgeHeadAtSpeed(double speed) {
    updateOperatingMWFromStream();
    if (!interpolationEnabled || mapCharts.size() < 2 || Double.isNaN(operatingMW)) {
      return super.getSurgeHeadAtSpeed(speed);
    }

    InterpolationIndices indices = findInterpolationIndices(operatingMW);

    if (indices.lowerIndex == indices.upperIndex) {
      return mapCharts.get(indices.lowerIndex).getSurgeHeadAtSpeed(speed);
    }

    double headLower = mapCharts.get(indices.lowerIndex).getSurgeHeadAtSpeed(speed);
    double headUpper = mapCharts.get(indices.upperIndex).getSurgeHeadAtSpeed(speed);

    return linearInterpolate(headLower, headUpper, indices.fraction);
  }

  /**
   * Get the stone wall head at a specific speed, interpolated between MW maps.
   *
   * @param speed the compressor speed in RPM
   * @return the stone wall head interpolated for the operating MW
   */
  @Override
  public double getStoneWallHeadAtSpeed(double speed) {
    updateOperatingMWFromStream();
    if (!interpolationEnabled || mapCharts.size() < 2 || Double.isNaN(operatingMW)) {
      return super.getStoneWallHeadAtSpeed(speed);
    }

    InterpolationIndices indices = findInterpolationIndices(operatingMW);

    if (indices.lowerIndex == indices.upperIndex) {
      return mapCharts.get(indices.lowerIndex).getStoneWallHeadAtSpeed(speed);
    }

    double headLower = mapCharts.get(indices.lowerIndex).getStoneWallHeadAtSpeed(speed);
    double headUpper = mapCharts.get(indices.upperIndex).getStoneWallHeadAtSpeed(speed);

    return linearInterpolate(headLower, headUpper, indices.fraction);
  }

  /**
   * Check if operating point is in surge, using interpolated surge curve.
   *
   * @param head the polytropic head
   * @param flow the volumetric flow
   * @return true if the operating point is below the surge curve (in surge)
   */
  public boolean isSurge(double head, double flow) {
    updateOperatingMWFromStream();
    if (!interpolationEnabled || mapCharts.size() < 2 || Double.isNaN(operatingMW)) {
      return getSurgeCurve().isSurge(head, flow);
    }

    InterpolationIndices indices = findInterpolationIndices(operatingMW);

    if (indices.lowerIndex == indices.upperIndex) {
      return mapCharts.get(indices.lowerIndex).getSurgeCurve().isSurge(head, flow);
    }

    // Get surge flow at the given head from both maps
    double surgeFlowLower = mapCharts.get(indices.lowerIndex).getSurgeCurve().getFlow(head);
    double surgeFlowUpper = mapCharts.get(indices.upperIndex).getSurgeCurve().getFlow(head);
    double interpolatedSurgeFlow =
        linearInterpolate(surgeFlowLower, surgeFlowUpper, indices.fraction);

    return flow < interpolatedSurgeFlow;
  }

  /**
   * Check if operating point is at stone wall (choke), using interpolated stone wall curve.
   *
   * @param head the polytropic head
   * @param flow the volumetric flow
   * @return true if the operating point is beyond the stone wall curve (choked)
   */
  public boolean isStoneWall(double head, double flow) {
    updateOperatingMWFromStream();
    if (!interpolationEnabled || mapCharts.size() < 2 || Double.isNaN(operatingMW)) {
      if (getStoneWallCurve() instanceof SafeSplineStoneWallCurve) {
        return ((SafeSplineStoneWallCurve) getStoneWallCurve()).isStoneWall(head, flow);
      }
      return false;
    }

    InterpolationIndices indices = findInterpolationIndices(operatingMW);

    if (indices.lowerIndex == indices.upperIndex) {
      StoneWallCurve curve = mapCharts.get(indices.lowerIndex).getStoneWallCurve();
      if (curve instanceof SafeSplineStoneWallCurve) {
        return ((SafeSplineStoneWallCurve) curve).isStoneWall(head, flow);
      }
      return false;
    }

    // Get stone wall flow at the given head from both maps
    StoneWallCurve curveLower = mapCharts.get(indices.lowerIndex).getStoneWallCurve();
    StoneWallCurve curveUpper = mapCharts.get(indices.upperIndex).getStoneWallCurve();

    if (curveLower instanceof SafeSplineStoneWallCurve
        && curveUpper instanceof SafeSplineStoneWallCurve) {
      double stoneFlowLower = ((SafeSplineStoneWallCurve) curveLower).getFlow(head);
      double stoneFlowUpper = ((SafeSplineStoneWallCurve) curveUpper).getFlow(head);
      double interpolatedStoneFlow =
          linearInterpolate(stoneFlowLower, stoneFlowUpper, indices.fraction);
      return flow > interpolatedStoneFlow;
    }

    return false;
  }

  /**
   * Get interpolated surge flow at a given head.
   *
   * @param head the polytropic head
   * @return the surge flow interpolated for the operating MW
   */
  public double getSurgeFlow(double head) {
    updateOperatingMWFromStream();
    if (!interpolationEnabled || mapCharts.size() < 2 || Double.isNaN(operatingMW)) {
      return getSurgeCurve().getFlow(head);
    }

    InterpolationIndices indices = findInterpolationIndices(operatingMW);

    if (indices.lowerIndex == indices.upperIndex) {
      return mapCharts.get(indices.lowerIndex).getSurgeCurve().getFlow(head);
    }

    double surgeFlowLower = mapCharts.get(indices.lowerIndex).getSurgeCurve().getFlow(head);
    double surgeFlowUpper = mapCharts.get(indices.upperIndex).getSurgeCurve().getFlow(head);

    return linearInterpolate(surgeFlowLower, surgeFlowUpper, indices.fraction);
  }

  /**
   * Get interpolated stone wall flow at a given head.
   *
   * @param head the polytropic head
   * @return the stone wall flow interpolated for the operating MW
   */
  public double getStoneWallFlow(double head) {
    updateOperatingMWFromStream();
    if (!interpolationEnabled || mapCharts.size() < 2 || Double.isNaN(operatingMW)) {
      if (getStoneWallCurve() instanceof SafeSplineStoneWallCurve) {
        return ((SafeSplineStoneWallCurve) getStoneWallCurve()).getFlow(head);
      }
      return Double.NaN;
    }

    InterpolationIndices indices = findInterpolationIndices(operatingMW);

    if (indices.lowerIndex == indices.upperIndex) {
      StoneWallCurve curve = mapCharts.get(indices.lowerIndex).getStoneWallCurve();
      if (curve instanceof SafeSplineStoneWallCurve) {
        return ((SafeSplineStoneWallCurve) curve).getFlow(head);
      }
      return Double.NaN;
    }

    StoneWallCurve curveLower = mapCharts.get(indices.lowerIndex).getStoneWallCurve();
    StoneWallCurve curveUpper = mapCharts.get(indices.upperIndex).getStoneWallCurve();

    if (curveLower instanceof SafeSplineStoneWallCurve
        && curveUpper instanceof SafeSplineStoneWallCurve) {
      double stoneFlowLower = ((SafeSplineStoneWallCurve) curveLower).getFlow(head);
      double stoneFlowUpper = ((SafeSplineStoneWallCurve) curveUpper).getFlow(head);
      return linearInterpolate(stoneFlowLower, stoneFlowUpper, indices.fraction);
    }

    return Double.NaN;
  }

  /**
   * Get the distance to surge as a ratio, using interpolated surge curve.
   *
   * @param head the polytropic head
   * @param flow the current volumetric flow
   * @return ratio (current flow / surge flow) - 1; positive means above surge
   */
  public double getDistanceToSurge(double head, double flow) {
    double surgeFlow = getSurgeFlow(head);
    if (Double.isNaN(surgeFlow) || surgeFlow <= 0) {
      return Double.NaN;
    }
    return (flow / surgeFlow) - 1.0;
  }

  /**
   * Get the distance to stone wall as a ratio, using interpolated stone wall curve.
   *
   * @param head the polytropic head
   * @param flow the current volumetric flow
   * @return ratio (stone wall flow / current flow) - 1; positive means below stone wall
   */
  public double getDistanceToStoneWall(double head, double flow) {
    double stoneWallFlow = getStoneWallFlow(head);
    if (Double.isNaN(stoneWallFlow) || flow <= 0) {
      return Double.NaN;
    }
    return (stoneWallFlow / flow) - 1.0;
  }

  /**
   * Linear interpolation between two values.
   *
   * @param valueLower the value at the lower bound
   * @param valueUpper the value at the upper bound
   * @param fraction the interpolation fraction (0.0 = lower, 1.0 = upper)
   * @return the interpolated value
   */
  private double linearInterpolate(double valueLower, double valueUpper, double fraction) {
    return valueLower + fraction * (valueUpper - valueLower);
  }

  /**
   * Set surge curve for a specific MW map.
   *
   * @param molecularWeight the MW of the map to update
   * @param chartConditions reference conditions
   * @param surgeFlow array of surge flow values
   * @param surgeHead array of surge head values
   */
  public void setSurgeCurveAtMW(double molecularWeight, double[] chartConditions,
      double[] surgeFlow, double[] surgeHead) {
    int idx = findMapIndex(molecularWeight);
    if (idx >= 0) {
      mapCharts.get(idx).getSurgeCurve().setCurve(chartConditions, surgeFlow, surgeHead);
    } else {
      logger.warn("No map found at MW = {} g/mol. Surge curve not set.", molecularWeight);
    }
  }

  /**
   * Set stone wall curve for a specific MW map.
   *
   * @param molecularWeight the MW of the map to update
   * @param chartConditions reference conditions
   * @param stoneWallFlow array of stone wall flow values
   * @param stoneWallHead array of stone wall head values
   */
  public void setStoneWallCurveAtMW(double molecularWeight, double[] chartConditions,
      double[] stoneWallFlow, double[] stoneWallHead) {
    int idx = findMapIndex(molecularWeight);
    if (idx >= 0) {
      if (mapCharts.get(idx).getStoneWallCurve() instanceof SafeSplineStoneWallCurve) {
        ((SafeSplineStoneWallCurve) mapCharts.get(idx).getStoneWallCurve())
            .setCurve(chartConditions, stoneWallFlow, stoneWallHead);
      }
    } else {
      logger.warn("No map found at MW = {} g/mol. Stone wall curve not set.", molecularWeight);
    }
  }

  /**
   * Generate surge curves for all MW maps.
   */
  public void generateAllSurgeCurves() {
    for (CompressorChart chart : mapCharts) {
      chart.generateSurgeCurve();
    }
    super.generateSurgeCurve();
  }

  /**
   * Generate stone wall curves for all MW maps.
   */
  public void generateAllStoneWallCurves() {
    for (CompressorChart chart : mapCharts) {
      chart.generateStoneWallCurve();
    }
    super.generateStoneWallCurve();
  }

  /**
   * Find the index of a map by molecular weight.
   *
   * @param molecularWeight the MW to find
   * @return the index, or -1 if not found
   */
  private int findMapIndex(double molecularWeight) {
    for (int i = 0; i < mapMolecularWeights.size(); i++) {
      if (Math.abs(mapMolecularWeights.get(i).doubleValue() - molecularWeight) < 0.001) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Get the chart at a specific MW.
   *
   * @param molecularWeight the MW to find
   * @return the CompressorChart at that MW, or null if not found
   */
  public CompressorChart getChartAtMW(double molecularWeight) {
    int idx = findMapIndex(molecularWeight);
    if (idx >= 0) {
      return mapCharts.get(idx);
    }
    return null;
  }

  /**
   * Set the head unit for all maps.
   *
   * @param headUnit the unit of head (e.g., "kJ/kg" or "meter")
   */
  @Override
  public void setHeadUnit(String headUnit) {
    super.setHeadUnit(headUnit);
    for (CompressorChart chart : mapCharts) {
      chart.setHeadUnit(headUnit);
    }
  }

  /**
   * Internal class to hold interpolation indices and fraction.
   */
  private static class InterpolationIndices {
    final int lowerIndex;
    final int upperIndex;
    final double fraction;

    InterpolationIndices(int lowerIndex, int upperIndex, double fraction) {
      this.lowerIndex = lowerIndex;
      this.upperIndex = upperIndex;
      this.fraction = fraction;
    }
  }
}
