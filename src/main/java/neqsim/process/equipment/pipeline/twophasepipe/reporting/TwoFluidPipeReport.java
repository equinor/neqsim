package neqsim.process.equipment.pipeline.twophasepipe.reporting;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import neqsim.process.equipment.pipeline.TwoFluidPipe;
import neqsim.process.equipment.pipeline.twophasepipe.HydrocarbonPoolTransfer;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
import neqsim.process.equipment.pipeline.twophasepipe.closure.OilWaterFlowRegimeDetector.OilWaterFlowRegime;
import neqsim.process.equipment.pipeline.twophasepipe.validation.TwoFluidBenchmarkHarness;
import neqsim.process.util.report.ReportConfig;
import neqsim.process.util.report.ReportConfig.DetailLevel;

/**
 * Reporting helpers for {@link TwoFluidPipe} profiles, summaries, and benchmark comparisons.
 */
public final class TwoFluidPipeReport {
  private TwoFluidPipeReport() {
  }

  /**
   * Immutable snapshot of the profile arrays at one simulation time.
   */
  public static final class ProfileSnapshot {
    private final double timeSeconds;
    private final double[] positionMeters;
    private final double[] pressurePa;
    private final double[] temperatureK;
    private final double[] liquidHoldup;
    private final double[] waterCut;
    private final double[] oilHoldup;
    private final double[] waterHoldup;
    private final double[] gasVelocity;
    private final double[] liquidVelocity;
    private final double[] oilVelocity;
    private final double[] waterVelocity;
    private final FlowRegime[] flowRegime;
    private final OilWaterFlowRegime[] oilWaterFlowRegime;
    private final boolean[] waterWetting;
    private final boolean[] waterDropoutRisk;
    private final double[] entrainmentFraction;
    private final double[] entrainedDropletDiameter;
    private final double[] inclinedSectionGasCarryoverNumber;
    private final boolean[] inclinedSectionLiquidFallbackPotential;
    private final boolean[] severeSlugPotential;

    private ProfileSnapshot(TwoFluidPipe pipe) {
      this.timeSeconds = pipe.getSimulationTime();
      this.positionMeters = pipe.getPositionProfile();
      this.pressurePa = pipe.getPressureProfile();
      this.temperatureK = pipe.getTemperatureProfile();
      this.liquidHoldup = pipe.getLiquidHoldupProfile();
      this.waterCut = pipe.getWaterCutProfile();
      this.oilHoldup = pipe.getOilHoldupProfile();
      this.waterHoldup = pipe.getWaterHoldupProfile();
      this.gasVelocity = pipe.getGasVelocityProfile();
      this.liquidVelocity = pipe.getLiquidVelocityProfile();
      this.oilVelocity = pipe.getOilVelocityProfile();
      this.waterVelocity = pipe.getWaterVelocityProfile();
      this.flowRegime = pipe.getFlowRegimeProfile();
      this.oilWaterFlowRegime = pipe.getOilWaterFlowRegimeProfile();
      this.waterWetting = pipe.getWaterWettingProfile();
      this.waterDropoutRisk = pipe.getWaterDropoutRiskProfile();
      this.entrainmentFraction = pipe.getEntrainmentFractionProfile();
      this.entrainedDropletDiameter = pipe.getEntrainedDropletDiameterProfile();
      this.inclinedSectionGasCarryoverNumber = pipe.getInclinedSectionGasCarryoverNumberProfile();
      this.inclinedSectionLiquidFallbackPotential = pipe.getInclinedSectionLiquidFallbackPotentialProfile();
      this.severeSlugPotential = pipe.getSevereSlugPotentialProfile();
    }

    public double getTimeSeconds() {
      return timeSeconds;
    }
  }

  /**
   * Immutable event-window pressure metrics without filtering or fitted oscillations.
   *
   * <p>
   * The reversal threshold is supplied by the caller in pascals. Total variation includes every finite sample
   * increment, while reversal counting ignores increments below that explicit threshold. This keeps measurement
   * resolution and numerical-noise policy outside the reporting helper.
   * </p>
   */
  public static final class PressureTraceMetrics {
    private final double minimumPressurePa;
    private final double maximumPressurePa;
    private final double timeOfMinimumSeconds;
    private final double timeOfMaximumSeconds;
    private final double peakToPeakPressurePa;
    private final double maximumAbsolutePressureRatePaPerSecond;
    private final double totalVariationPa;
    private final double excessVariationPa;
    private final int directionReversals;

    private PressureTraceMetrics(double minimumPressurePa, double maximumPressurePa, double timeOfMinimumSeconds,
        double timeOfMaximumSeconds, double maximumAbsolutePressureRatePaPerSecond, double totalVariationPa,
        double excessVariationPa, int directionReversals) {
      this.minimumPressurePa = minimumPressurePa;
      this.maximumPressurePa = maximumPressurePa;
      this.timeOfMinimumSeconds = timeOfMinimumSeconds;
      this.timeOfMaximumSeconds = timeOfMaximumSeconds;
      this.peakToPeakPressurePa = maximumPressurePa - minimumPressurePa;
      this.maximumAbsolutePressureRatePaPerSecond = maximumAbsolutePressureRatePaPerSecond;
      this.totalVariationPa = totalVariationPa;
      this.excessVariationPa = excessVariationPa;
      this.directionReversals = directionReversals;
    }

    /** @return minimum sampled pressure in Pa */
    public double getMinimumPressurePa() {
      return minimumPressurePa;
    }

    /** @return maximum sampled pressure in Pa */
    public double getMaximumPressurePa() {
      return maximumPressurePa;
    }

    /** @return time of the first minimum-pressure sample in s */
    public double getTimeOfMinimumSeconds() {
      return timeOfMinimumSeconds;
    }

    /** @return time of the first maximum-pressure sample in s */
    public double getTimeOfMaximumSeconds() {
      return timeOfMaximumSeconds;
    }

    /** @return sampled maximum minus minimum pressure in Pa */
    public double getPeakToPeakPressurePa() {
      return peakToPeakPressurePa;
    }

    /** @return maximum absolute adjacent-sample pressure rate in Pa/s */
    public double getMaximumAbsolutePressureRatePaPerSecond() {
      return maximumAbsolutePressureRatePaPerSecond;
    }

    /** @return sum of absolute adjacent pressure increments in Pa */
    public double getTotalVariationPa() {
      return totalVariationPa;
    }

    /** @return total variation minus absolute end-to-end pressure change in Pa */
    public double getExcessVariationPa() {
      return excessVariationPa;
    }

    /** @return signed increment reversals above the caller-supplied pressure threshold */
    public int getDirectionReversals() {
      return directionReversals;
    }

    /** @return excess variation divided by peak-to-peak pressure, or zero for a constant trace */
    public double getRingingRatio() {
      return peakToPeakPressurePa == 0.0 ? 0.0 : excessVariationPa / peakToPeakPressurePa;
    }
  }

  /**
   * Immutable per-terrain-basin liquid-pool snapshot at one simulation time.
   *
   * <p>
   * Pool identifiers are one-based for reporting. Cell and basin indices are zero-based and refer to the physical
   * {@link TwoFluidSection} array. A basin is reported only for a strict internal terrain minimum; an exactly flat
   * minimum is represented by its midpoint cell. Horizontal and monotonic profiles therefore produce an empty snapshot
   * instead of manufacturing a pool location.
   * </p>
   */
  public static final class LowPointPoolSnapshot {
    private final double timeSeconds;
    private final int[] poolId;
    private final int[] lowPointCell;
    private final int[] basinStartCell;
    private final int[] basinEndCell;
    private final double[] lowPointPositionMeters;
    private final double[] lowPointElevationMeters;
    private final double[] oilInventoryKilograms;
    private final double[] oilInventoryCubicMeters;
    private final double[] liquidInventoryCubicMeters;
    private final double[] lowPointLiquidDepthMeters;
    private final double[] maximumLocalLiquidDepthMeters;
    private final double[] maximumAbsoluteGasVelocityMetersPerSecond;
    private final double[] maximumAbsoluteOilVelocityMetersPerSecond;
    private final double[] interfaceAreaSquareMeters;
    private final double[] interphaseOilMassRateKilogramsPerSecond;
    private final double[] minimumHeatScale;
    private final boolean[] traceAreaFallback;

    private LowPointPoolSnapshot(double timeSeconds, int[] lowPointCell, int[] basinStartCell, int[] basinEndCell,
        TwoFluidSection[] sections, double[][][] sources, double[] interfaceAreas, double[] heatScale,
        boolean[] traceAreaFallback) {
      this.timeSeconds = timeSeconds;
      this.lowPointCell = lowPointCell.clone();
      this.basinStartCell = basinStartCell.clone();
      this.basinEndCell = basinEndCell.clone();
      int pools = lowPointCell.length;
      this.poolId = new int[pools];
      this.lowPointPositionMeters = new double[pools];
      this.lowPointElevationMeters = new double[pools];
      this.oilInventoryKilograms = new double[pools];
      this.oilInventoryCubicMeters = new double[pools];
      this.liquidInventoryCubicMeters = new double[pools];
      this.lowPointLiquidDepthMeters = new double[pools];
      this.maximumLocalLiquidDepthMeters = new double[pools];
      this.maximumAbsoluteGasVelocityMetersPerSecond = new double[pools];
      this.maximumAbsoluteOilVelocityMetersPerSecond = new double[pools];
      this.interfaceAreaSquareMeters = new double[pools];
      this.interphaseOilMassRateKilogramsPerSecond = new double[pools];
      this.minimumHeatScale = new double[pools];
      this.traceAreaFallback = new boolean[pools];
      Arrays.fill(this.interfaceAreaSquareMeters, Double.NaN);
      Arrays.fill(this.interphaseOilMassRateKilogramsPerSecond, Double.NaN);
      Arrays.fill(this.minimumHeatScale, Double.NaN);

      for (int pool = 0; pool < pools; pool++) {
        this.poolId[pool] = pool + 1;
        TwoFluidSection lowPoint = sections[lowPointCell[pool]];
        this.lowPointPositionMeters[pool] = lowPoint.getPosition();
        this.lowPointElevationMeters[pool] = lowPoint.getElevation();
        this.lowPointLiquidDepthMeters[pool] = lowPoint.getHydrostaticCellGeometry().getMaximumDepth();
        double areaSum = 0.0;
        double transferRate = 0.0;
        double heatScaleMinimum = Double.POSITIVE_INFINITY;
        for (int cell = basinStartCell[pool]; cell <= basinEndCell[pool]; cell++) {
          TwoFluidSection section = sections[cell];
          double length = section.getLength();
          double volume = section.getArea() * length;
          this.oilInventoryKilograms[pool] += section.getOilMassPerLength() * length;
          this.oilInventoryCubicMeters[pool] += section.getOilHoldup() * volume;
          this.liquidInventoryCubicMeters[pool] += section.getLiquidHoldup() * volume;
          this.maximumLocalLiquidDepthMeters[pool] = Math.max(this.maximumLocalLiquidDepthMeters[pool],
              section.getHydrostaticCellGeometry().getMaximumDepth());
          this.maximumAbsoluteGasVelocityMetersPerSecond[pool] = Math
              .max(this.maximumAbsoluteGasVelocityMetersPerSecond[pool], Math.abs(section.getGasVelocity()));
          this.maximumAbsoluteOilVelocityMetersPerSecond[pool] = Math
              .max(this.maximumAbsoluteOilVelocityMetersPerSecond[pool], Math.abs(section.getOilVelocity()));
          if (sources != null) {
            for (int component = 0; component < sources[cell][1].length; component++) {
              transferRate += sources[cell][1][component] * length;
            }
            areaSum += interfaceAreas[cell];
            heatScaleMinimum = Math.min(heatScaleMinimum, heatScale[cell]);
            this.traceAreaFallback[pool] |= traceAreaFallback[cell];
          }
        }
        if (sources != null) {
          this.interfaceAreaSquareMeters[pool] = areaSum;
          this.interphaseOilMassRateKilogramsPerSecond[pool] = transferRate;
          this.minimumHeatScale[pool] = heatScaleMinimum;
        }
      }
    }

    /** @return simulation time in seconds */
    public double getTimeSeconds() {
      return timeSeconds;
    }

    /** @return one-based pool identifiers */
    public int[] getPoolId() {
      return poolId.clone();
    }

    /** @return zero-based low-point cell indices */
    public int[] getLowPointCell() {
      return lowPointCell.clone();
    }

    /** @return zero-based inclusive basin-start cell indices */
    public int[] getBasinStartCell() {
      return basinStartCell.clone();
    }

    /** @return zero-based inclusive basin-end cell indices */
    public int[] getBasinEndCell() {
      return basinEndCell.clone();
    }

    /** @return low-point positions in metres */
    public double[] getLowPointPositionMeters() {
      return lowPointPositionMeters.clone();
    }

    /** @return low-point elevations in metres */
    public double[] getLowPointElevationMeters() {
      return lowPointElevationMeters.clone();
    }

    /** @return oil inventory by basin in kilograms */
    public double[] getOilInventoryKilograms() {
      return oilInventoryKilograms.clone();
    }

    /** @return oil inventory by basin in cubic metres */
    public double[] getOilInventoryCubicMeters() {
      return oilInventoryCubicMeters.clone();
    }

    /** @return total liquid inventory by basin in cubic metres */
    public double[] getLiquidInventoryCubicMeters() {
      return liquidInventoryCubicMeters.clone();
    }

    /** @return static local liquid depth at each low-point cell in metres */
    public double[] getLowPointLiquidDepthMeters() {
      return lowPointLiquidDepthMeters.clone();
    }

    /** @return maximum static local liquid depth by basin in metres */
    public double[] getMaximumLocalLiquidDepthMeters() {
      return maximumLocalLiquidDepthMeters.clone();
    }

    /** @return maximum absolute gas speed by basin in metres per second */
    public double[] getMaximumAbsoluteGasVelocityMetersPerSecond() {
      return maximumAbsoluteGasVelocityMetersPerSecond.clone();
    }

    /** @return maximum absolute oil speed by basin in metres per second */
    public double[] getMaximumAbsoluteOilVelocityMetersPerSecond() {
      return maximumAbsoluteOilVelocityMetersPerSecond.clone();
    }

    /** @return closure-used gas/oil interface area by basin in square metres, or NaN without transfer diagnostics */
    public double[] getInterfaceAreaSquareMeters() {
      return interfaceAreaSquareMeters.clone();
    }

    /** @return signed oil interphase rate by basin in kilograms per second, or NaN without transfer diagnostics */
    public double[] getInterphaseOilMassRateKilogramsPerSecond() {
      return interphaseOilMassRateKilogramsPerSecond.clone();
    }

    /** @return minimum heat-limit scale by basin, or NaN without transfer diagnostics */
    public double[] getMinimumHeatScale() {
      return minimumHeatScale.clone();
    }

    /** @return whether any cell in each basin used the unresolved-trace-area fallback */
    public boolean[] getTraceAreaFallback() {
      return traceAreaFallback.clone();
    }
  }

  /**
   * Capture all reportable pipe profiles at the current simulation time.
   *
   * @param pipe solved pipe
   * @return profile snapshot
   */
  public static ProfileSnapshot capture(TwoFluidPipe pipe) {
    return new ProfileSnapshot(pipe);
  }

  /**
   * Analyze a sampled pressure trace over one declared event window.
   *
   * <p>
   * Samples are not interpolated, filtered, detrended or assigned a dominant period. Use a sufficiently resolved
   * sampling interval and report it with the result. A monotonic trace has zero excess variation and zero direction
   * reversals. An oscillatory or ringing trace has positive excess variation; reversal counting additionally requires
   * adjacent changes at least as large as {@code minimumReversalExcursionPa}.
   * </p>
   *
   * @param timesSeconds strictly increasing sample times in seconds
   * @param pressuresPa finite pressure samples in pascals
   * @param minimumReversalExcursionPa nonnegative absolute adjacent change required to count a direction
   * @return immutable pressure metrics
   */
  public static PressureTraceMetrics analyzePressureTrace(double[] timesSeconds, double[] pressuresPa,
      double minimumReversalExcursionPa) {
    if (timesSeconds == null || pressuresPa == null || timesSeconds.length < 2
        || timesSeconds.length != pressuresPa.length || !Double.isFinite(minimumReversalExcursionPa)
        || minimumReversalExcursionPa < 0.0) {
      throw new IllegalArgumentException("Pressure analysis requires matching finite samples and a nonnegative gate");
    }
    double minimum = pressuresPa[0];
    double maximum = pressuresPa[0];
    double timeOfMinimum = timesSeconds[0];
    double timeOfMaximum = timesSeconds[0];
    double totalVariation = 0.0;
    double maximumRate = 0.0;
    int previousDirection = 0;
    int reversals = 0;
    if (!Double.isFinite(timesSeconds[0]) || !Double.isFinite(pressuresPa[0])) {
      throw new IllegalArgumentException("Pressure analysis requires finite samples");
    }
    for (int sample = 1; sample < timesSeconds.length; sample++) {
      double interval = timesSeconds[sample] - timesSeconds[sample - 1];
      double pressure = pressuresPa[sample];
      if (!Double.isFinite(timesSeconds[sample]) || !Double.isFinite(pressure) || !(interval > 0.0)) {
        throw new IllegalArgumentException("Pressure analysis requires finite samples at increasing times");
      }
      if (pressure < minimum) {
        minimum = pressure;
        timeOfMinimum = timesSeconds[sample];
      }
      if (pressure > maximum) {
        maximum = pressure;
        timeOfMaximum = timesSeconds[sample];
      }
      double change = pressure - pressuresPa[sample - 1];
      totalVariation += Math.abs(change);
      maximumRate = Math.max(maximumRate, Math.abs(change / interval));
      int direction = Math.abs(change) >= minimumReversalExcursionPa && change != 0.0 ? (change > 0.0 ? 1 : -1) : 0;
      if (direction != 0) {
        if (previousDirection != 0 && direction != previousDirection) {
          reversals++;
        }
        previousDirection = direction;
      }
    }
    double netChange = Math.abs(pressuresPa[pressuresPa.length - 1] - pressuresPa[0]);
    return new PressureTraceMetrics(minimum, maximum, timeOfMinimum, timeOfMaximum, maximumRate, totalVariation,
        Math.max(0.0, totalVariation - netChange), reversals);
  }

  /**
   * Capture liquid inventory and transfer diagnostics for every strict internal terrain low point.
   *
   * <p>
   * Adjacent low points are separated at the highest intervening ridge; ties use the first ridge cell. The first and
   * last basins extend to the pipe ends. Metrics are calculated from defensive section and transfer-result snapshots,
   * so reporting cannot mutate the solved pipe.
   * </p>
   *
   * @param pipe solved pipe
   * @return immutable low-point pool snapshot
   */
  public static LowPointPoolSnapshot captureLowPointPools(TwoFluidPipe pipe) {
    HydrocarbonPoolTransfer.Result result = pipe.getLastHydrocarbonPoolTransferResult();
    return captureLowPointPools(pipe.getSimulationTime(), pipe.getSectionSnapshots(), result);
  }

  static LowPointPoolSnapshot captureLowPointPools(double timeSeconds, TwoFluidSection[] sections,
      HydrocarbonPoolTransfer.Result result) {
    if (result == null) {
      return captureLowPointPools(timeSeconds, sections, null, null, null, null);
    }
    return captureLowPointPools(timeSeconds, sections, result.getSourcesKgPerMetreSecond(),
        result.getInterfaceAreasSquareMetres(), result.getHeatScale(), result.getUnresolvedTraceAreaFallback());
  }

  static LowPointPoolSnapshot captureLowPointPools(double timeSeconds, TwoFluidSection[] sections, double[][][] sources,
      double[] interfaceAreas, double[] heatScale, boolean[] traceAreaFallback) {
    if (!Double.isFinite(timeSeconds) || sections == null) {
      throw new IllegalArgumentException("Pool reporting requires finite time and physical sections");
    }
    for (TwoFluidSection section : sections) {
      if (section == null || !Double.isFinite(section.getElevation())) {
        throw new IllegalArgumentException("Pool reporting requires finite terrain for every physical section");
      }
    }
    validateTransferDiagnostics(sections.length, sources, interfaceAreas, heatScale, traceAreaFallback);
    int[] lowPoints = findStrictInternalLowPoints(sections);
    int[] basinStarts = new int[lowPoints.length];
    int[] basinEnds = new int[lowPoints.length];
    if (lowPoints.length > 0) {
      basinStarts[0] = 0;
      basinEnds[lowPoints.length - 1] = sections.length - 1;
      for (int pool = 0; pool + 1 < lowPoints.length; pool++) {
        int ridge = highestRidge(sections, lowPoints[pool], lowPoints[pool + 1]);
        basinEnds[pool] = ridge;
        basinStarts[pool + 1] = ridge + 1;
      }
    }
    return new LowPointPoolSnapshot(timeSeconds, lowPoints, basinStarts, basinEnds, sections, sources, interfaceAreas,
        heatScale, traceAreaFallback);
  }

  private static int[] findStrictInternalLowPoints(TwoFluidSection[] sections) {
    int[] candidates = new int[Math.max(0, sections.length - 2)];
    int count = 0;
    int start = 1;
    while (start + 1 < sections.length) {
      double elevation = sections[start].getElevation();
      int end = start;
      while (end + 1 < sections.length && Double.compare(sections[end + 1].getElevation(), elevation) == 0) {
        end++;
      }
      if (end + 1 < sections.length && elevation < sections[start - 1].getElevation()
          && elevation < sections[end + 1].getElevation()) {
        candidates[count++] = start + (end - start) / 2;
      }
      start = end + 1;
    }
    return Arrays.copyOf(candidates, count);
  }

  private static int highestRidge(TwoFluidSection[] sections, int leftLowPoint, int rightLowPoint) {
    int ridge = leftLowPoint + 1;
    for (int cell = ridge + 1; cell < rightLowPoint; cell++) {
      if (sections[cell].getElevation() > sections[ridge].getElevation()) {
        ridge = cell;
      }
    }
    return ridge;
  }

  private static void validateTransferDiagnostics(int cells, double[][][] sources, double[] interfaceAreas,
      double[] heatScale, boolean[] traceAreaFallback) {
    if (sources == null) {
      if (interfaceAreas != null || heatScale != null || traceAreaFallback != null) {
        throw new IllegalArgumentException("Pool-transfer diagnostics must be supplied together");
      }
      return;
    }
    if (sources.length != cells || interfaceAreas == null || interfaceAreas.length != cells || heatScale == null
        || heatScale.length != cells || traceAreaFallback == null || traceAreaFallback.length != cells) {
      throw new IllegalArgumentException("Pool-transfer diagnostics must match the physical section count");
    }
    for (double[][] cell : sources) {
      if (cell == null || cell.length < 2 || cell[1] == null) {
        throw new IllegalArgumentException("Pool-transfer sources require an oil phase for every cell");
      }
    }
  }

  /**
   * Export the current pipe profile as CSV.
   *
   * @param pipe solved pipe
   * @return CSV text
   */
  public static String toSteadyStateProfileCsv(TwoFluidPipe pipe) {
    return toProfileCsvRows(capture(pipe), false);
  }

  /**
   * Export transient snapshots as time-position CSV.
   *
   * @param snapshots profile snapshots collected during transient simulation
   * @return CSV text
   */
  public static String toTransientProfileCsv(List<ProfileSnapshot> snapshots) {
    StringBuilder csv = new StringBuilder();
    boolean headerWritten = false;
    for (ProfileSnapshot snapshot : snapshots) {
      String rows = toProfileCsvRows(snapshot, true);
      if (!headerWritten) {
        csv.append(rows);
        headerWritten = true;
      } else {
        int firstNewline = rows.indexOf('\n');
        if (firstNewline >= 0 && firstNewline + 1 < rows.length()) {
          csv.append(rows.substring(firstNewline + 1));
        }
      }
    }
    return csv.toString();
  }

  /**
   * Export low-point pool snapshots as one time-pool row per identified terrain basin.
   *
   * @param snapshots low-point snapshots collected during a transient
   * @return CSV text, including only the header when no low points exist
   */
  public static String toLowPointPoolCsv(List<LowPointPoolSnapshot> snapshots) {
    StringBuilder csv = new StringBuilder();
    csv.append("time_s,pool_id,low_point_cell,low_point_position_m,low_point_elevation_m,")
        .append("basin_start_cell,basin_end_cell,oil_inventory_kg,oil_inventory_m3,")
        .append("liquid_inventory_m3,low_point_liquid_depth_m,max_local_liquid_depth_m,")
        .append("max_abs_gas_velocity_m_s,")
        .append("max_abs_oil_velocity_m_s,interface_area_m2,interphase_oil_mass_rate_kg_s,")
        .append("minimum_heat_scale,trace_area_fallback\n");
    for (LowPointPoolSnapshot snapshot : snapshots) {
      for (int pool = 0; pool < snapshot.poolId.length; pool++) {
        csv.append(format(snapshot.timeSeconds)).append(',').append(snapshot.poolId[pool]).append(',')
            .append(snapshot.lowPointCell[pool]).append(',').append(format(snapshot.lowPointPositionMeters[pool]))
            .append(',').append(format(snapshot.lowPointElevationMeters[pool])).append(',')
            .append(snapshot.basinStartCell[pool]).append(',').append(snapshot.basinEndCell[pool]).append(',')
            .append(format(snapshot.oilInventoryKilograms[pool])).append(',')
            .append(format(snapshot.oilInventoryCubicMeters[pool])).append(',')
            .append(format(snapshot.liquidInventoryCubicMeters[pool])).append(',')
            .append(format(snapshot.lowPointLiquidDepthMeters[pool])).append(',')
            .append(format(snapshot.maximumLocalLiquidDepthMeters[pool])).append(',')
            .append(format(snapshot.maximumAbsoluteGasVelocityMetersPerSecond[pool])).append(',')
            .append(format(snapshot.maximumAbsoluteOilVelocityMetersPerSecond[pool])).append(',')
            .append(format(snapshot.interfaceAreaSquareMeters[pool])).append(',')
            .append(format(snapshot.interphaseOilMassRateKilogramsPerSecond[pool])).append(',')
            .append(format(snapshot.minimumHeatScale[pool])).append(',').append(snapshot.traceAreaFallback[pool])
            .append('\n');
      }
    }
    return csv.toString();
  }

  /**
   * Build a concise text summary of the solved pipe.
   *
   * @param pipe solved pipe
   * @return text summary
   */
  public static String toSummaryText(TwoFluidPipe pipe) {
    StringBuilder sb = new StringBuilder();
    sb.append("TwoFluidPipe summary: ").append(pipe.getName()).append(System.lineSeparator());
    sb.append(String.format(Locale.ROOT, "Simulation time: %.3f s%n", pipe.getSimulationTime()));
    sb.append(String.format(Locale.ROOT, "Inlet pressure: %.3f bara%n", pipe.getInletPressure()));
    sb.append(String.format(Locale.ROOT, "Outlet pressure: %.3f bara%n", pipe.getOutletPressure()));
    sb.append(String.format(Locale.ROOT, "Average liquid holdup: %.5f%n", pipe.getAverageLiquidHoldup()));
    sb.append("Dominant flow regime: ").append(pipe.getDominantFlowRegime()).append(System.lineSeparator());
    sb.append(String.format(Locale.ROOT, "Liquid inventory: %.5f m3%n", pipe.getLiquidInventory("m3")));
    sb.append(String.format(Locale.ROOT, "Maximum mixture velocity: %.5f m/s%n", pipe.getMaxMixtureVelocity()));
    sb.append(String.format(Locale.ROOT, "Erosional velocity margin: %.5f%n", pipe.getErosionalVelocityMargin(122.0)));
    sb.append(String.format(Locale.ROOT, "Hydrate risk sections: %d%n", pipe.getHydrateRiskSectionCount()));
    sb.append("Wax risk: ").append(pipe.hasWaxRisk()).append(System.lineSeparator());
    sb.append(String.format(Locale.ROOT, "Outlet slug count: %d%n", pipe.getOutletSlugCount()));
    sb.append(String.format(Locale.ROOT, "Total outlet slug volume: %.5f m3%n", pipe.getTotalSlugVolumeAtOutlet()));
    return sb.toString();
  }

  /**
   * Build a JSON summary of key pipe results.
   *
   * @param pipe solved pipe
   * @return JSON text
   */
  public static String toSummaryJson(TwoFluidPipe pipe) {
    return pipe.toJson(new ReportConfig(DetailLevel.SUMMARY));
  }

  /**
   * Export slug and flow-assurance events as CSV.
   *
   * @param pipe solved pipe
   * @return CSV text
   */
  public static String toSlugAndFlowAssuranceCsv(TwoFluidPipe pipe) {
    StringBuilder csv = new StringBuilder();
    csv.append("event_type,position_m,value,unit,description\n");
    csv.append(csvRow("slug_count", "", pipe.getOutletSlugCount(), "count", "Outlet slug count"));
    csv.append(csvRow("slug_volume_total", "", pipe.getTotalSlugVolumeAtOutlet(), "m3", "Total outlet slug volume"));
    csv.append(csvRow("slug_length_max", "", pipe.getMaxSlugLengthAtOutlet(), "m", "Maximum outlet slug length"));
    csv.append(csvRow("slug_volume_max", "", pipe.getMaxSlugVolumeAtOutlet(), "m3", "Maximum outlet slug volume"));

    double hydrateDistance = pipe.getDistanceToHydrateRisk();
    if (hydrateDistance >= 0.0) {
      csv.append(
          csvRow("hydrate_risk_first", hydrateDistance, 1.0, "flag", "First section below hydrate risk temperature"));
    }
    csv.append(csvRow("hydrate_risk_count", "", pipe.getHydrateRiskSectionCount(), "count",
        "Number of hydrate risk sections"));
    csv.append(csvRow("wax_risk", "", pipe.hasWaxRisk() ? 1.0 : 0.0, "flag",
        "At least one section below wax appearance temperature"));
    csv.append(csvRow("erosion_margin", "", pipe.getErosionalVelocityMargin(122.0), "ratio",
        "Maximum mixture velocity divided by API 14E erosional velocity"));
    return csv.toString();
  }

  /**
   * Export benchmark comparison results as CSV.
   *
   * @param comparison benchmark comparison
   * @return CSV text
   */
  public static String toComparisonCsv(TwoFluidBenchmarkHarness.Comparison comparison) {
    StringBuilder csv = new StringBuilder();
    csv.append("case,time_s,position_m,variable,reference,model,abs_error,rel_error,passed,source\n");
    for (TwoFluidBenchmarkHarness.ComparisonRow row : comparison.getRows()) {
      TwoFluidBenchmarkHarness.BenchmarkPoint point = row.getReference();
      csv.append(escape(point.getCaseName())).append(',').append(format(point.getTimeSeconds())).append(',')
          .append(format(point.getPositionMeters())).append(',').append(escape(point.getVariable())).append(',')
          .append(format(point.getValue())).append(',').append(format(row.getModelValue())).append(',')
          .append(format(row.getAbsoluteError())).append(',').append(format(row.getRelativeError())).append(',')
          .append(row.isPassed()).append(',').append(escape(point.getSource())).append('\n');
    }
    return csv.toString();
  }

  public static void writeSteadyStateProfileCsv(TwoFluidPipe pipe, Path path) throws IOException {
    write(path, toSteadyStateProfileCsv(pipe));
  }

  public static void writeTransientProfileCsv(List<ProfileSnapshot> snapshots, Path path) throws IOException {
    write(path, toTransientProfileCsv(snapshots));
  }

  /**
   * Write low-point pool time series as UTF-8 CSV.
   *
   * @param snapshots low-point snapshots collected during a transient
   * @param path destination path
   * @throws IOException if the report cannot be written
   */
  public static void writeLowPointPoolCsv(List<LowPointPoolSnapshot> snapshots, Path path) throws IOException {
    write(path, toLowPointPoolCsv(snapshots));
  }

  public static void writeSummaryText(TwoFluidPipe pipe, Path path) throws IOException {
    write(path, toSummaryText(pipe));
  }

  public static void writeSummaryJson(TwoFluidPipe pipe, Path path) throws IOException {
    write(path, toSummaryJson(pipe));
  }

  public static void writeSlugAndFlowAssuranceCsv(TwoFluidPipe pipe, Path path) throws IOException {
    write(path, toSlugAndFlowAssuranceCsv(pipe));
  }

  public static void writeComparisonCsv(TwoFluidBenchmarkHarness.Comparison comparison, Path path) throws IOException {
    write(path, toComparisonCsv(comparison));
  }

  private static String toProfileCsvRows(ProfileSnapshot snapshot, boolean includeTime) {
    validateProfileLengths(snapshot);
    StringBuilder csv = new StringBuilder();
    if (includeTime) {
      csv.append("time_s,");
    }
    csv.append("position_m,pressure_bara,temperature_C,liquid_holdup,water_cut,oil_holdup,")
        .append("water_holdup,gas_velocity_m_s,liquid_velocity_m_s,oil_velocity_m_s,")
        .append("water_velocity_m_s,flow_regime,oil_water_flow_regime,water_wetting,")
        .append("water_dropout_risk,entrainment_fraction,entrained_droplet_diameter_m,")
        .append("inclined_section_gas_carryover_number,").append("inclined_section_liquid_fallback_potential,")
        .append("severe_slugging_number,severe_slug_potential\n");

    for (int i = 0; i < snapshot.positionMeters.length; i++) {
      if (includeTime) {
        csv.append(format(snapshot.timeSeconds)).append(',');
      }
      csv.append(format(snapshot.positionMeters[i])).append(',').append(format(snapshot.pressurePa[i] * 1.0e-5))
          .append(',').append(format(snapshot.temperatureK[i] - 273.15)).append(',')
          .append(format(snapshot.liquidHoldup[i])).append(',').append(format(valueAt(snapshot.waterCut, i)))
          .append(',').append(format(valueAt(snapshot.oilHoldup, i))).append(',')
          .append(format(valueAt(snapshot.waterHoldup, i))).append(',').append(format(valueAt(snapshot.gasVelocity, i)))
          .append(',').append(format(valueAt(snapshot.liquidVelocity, i))).append(',')
          .append(format(valueAt(snapshot.oilVelocity, i))).append(',')
          .append(format(valueAt(snapshot.waterVelocity, i))).append(',')
          .append(snapshot.flowRegime.length > i && snapshot.flowRegime[i] != null ? snapshot.flowRegime[i].name() : "")
          .append(',')
          .append(snapshot.oilWaterFlowRegime.length > i && snapshot.oilWaterFlowRegime[i] != null
              ? snapshot.oilWaterFlowRegime[i].name()
              : "")
          .append(',').append(valueAt(snapshot.waterWetting, i)).append(',')
          .append(valueAt(snapshot.waterDropoutRisk, i)).append(',')
          .append(format(valueAt(snapshot.entrainmentFraction, i))).append(',')
          .append(format(valueAt(snapshot.entrainedDropletDiameter, i))).append(',')
          .append(format(valueAt(snapshot.inclinedSectionGasCarryoverNumber, i))).append(',')
          .append(valueAt(snapshot.inclinedSectionLiquidFallbackPotential, i)).append(',')
          .append(format(valueAt(snapshot.inclinedSectionGasCarryoverNumber, i))).append(',')
          .append(valueAt(snapshot.severeSlugPotential, i)).append('\n');
    }
    return csv.toString();
  }

  private static void validateProfileLengths(ProfileSnapshot snapshot) {
    int n = snapshot.positionMeters.length;
    if (snapshot.pressurePa.length != n || snapshot.temperatureK.length != n || snapshot.liquidHoldup.length != n) {
      throw new IllegalStateException("Core TwoFluidPipe profile lengths differ");
    }
  }

  private static double valueAt(double[] values, int index) {
    return values.length > index ? values[index] : Double.NaN;
  }

  private static boolean valueAt(boolean[] values, int index) {
    return values.length > index && values[index];
  }

  private static String csvRow(String type, Object position, double value, String unit, String description) {
    return escape(type) + "," + escape(String.valueOf(position)) + "," + format(value) + "," + escape(unit) + ","
        + escape(description) + "\n";
  }

  private static String escape(String value) {
    if (value == null) {
      return "";
    }
    if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
      return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    return value;
  }

  private static String format(double value) {
    return String.format(Locale.ROOT, "%.10g", value);
  }

  private static void write(Path path, String content) throws IOException {
    Path parent = path.toAbsolutePath().getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Files.write(path, content.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Convenience container for collecting transient snapshots.
   *
   * @return mutable snapshot list
   */
  public static List<ProfileSnapshot> newSnapshotList() {
    return new ArrayList<>();
  }

  /**
   * Convenience container for collecting low-point pool snapshots.
   *
   * @return mutable low-point snapshot list
   */
  public static List<LowPointPoolSnapshot> newLowPointPoolSnapshotList() {
    return new ArrayList<>();
  }
}
