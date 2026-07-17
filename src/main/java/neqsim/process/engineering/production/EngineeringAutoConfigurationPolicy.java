package neqsim.process.engineering.production;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import neqsim.process.engineering.design.modules.PipingNetworkDesignModule;
import neqsim.process.engineering.designcase.EngineeringMetric;
import neqsim.process.engineering.piping.PipingRulePack;

/** Explicit project policy used for automatic module selection without hidden engineering defaults. */
public final class EngineeringAutoConfigurationPolicy implements Serializable {
  private static final long serialVersionUID = 1000L;

  static final class SliceRule implements Serializable {
    private static final long serialVersionUID = 1000L;
    final String separatorTag;
    final String compressorTag;
    final String lineTag;
    final String valveTag;
    final String instrumentTag;
    final double separatorLiquidDensityKgM3;
    final double separatorSoudersBrownCoefficient;
    final double separatorLiquidRetentionTimeSeconds;
    final double maximumLineVelocityMPerS;
    final double maximumPressureGradientBarPerKm;
    final double compressorDriverMarginFraction;
    final double[] compressorDriverCandidatesKw;
    final boolean explicitDesignInputs;

    SliceRule(String separatorTag, String compressorTag, String lineTag, String valveTag, String instrumentTag,
        double separatorLiquidDensityKgM3, double separatorSoudersBrownCoefficient,
        double separatorLiquidRetentionTimeSeconds, double maximumLineVelocityMPerS,
        double maximumPressureGradientBarPerKm, double compressorDriverMarginFraction,
        double[] compressorDriverCandidatesKw, boolean explicitDesignInputs) {
      this.separatorTag = text(separatorTag, "separatorTag");
      this.compressorTag = text(compressorTag, "compressorTag");
      this.lineTag = text(lineTag, "lineTag");
      this.valveTag = optional(valveTag);
      this.instrumentTag = optional(instrumentTag);
      this.separatorLiquidDensityKgM3 = separatorLiquidDensityKgM3;
      this.separatorSoudersBrownCoefficient = separatorSoudersBrownCoefficient;
      this.separatorLiquidRetentionTimeSeconds = separatorLiquidRetentionTimeSeconds;
      this.maximumLineVelocityMPerS = maximumLineVelocityMPerS;
      this.maximumPressureGradientBarPerKm = maximumPressureGradientBarPerKm;
      this.compressorDriverMarginFraction = compressorDriverMarginFraction;
      this.compressorDriverCandidatesKw = compressorDriverCandidatesKw == null ? new double[0]
          : compressorDriverCandidatesKw.clone();
      this.explicitDesignInputs = explicitDesignInputs;
    }
  }

  static final class PumpRule implements Serializable {
    private static final long serialVersionUID = 1000L;
    final String tag;
    final double margin;
    final double minimumNpshMarginM;
    final double[] drivers;

    PumpRule(String tag, double margin, double minimumNpshMarginM, double[] drivers) {
      this.tag = text(tag, "pumpTag");
      this.margin = nonNegative(margin, "driverMarginFraction");
      this.minimumNpshMarginM = nonNegative(minimumNpshMarginM, "minimumNpshMarginM");
      this.drivers = positiveCandidates(drivers, "driverCandidatesKw");
    }
  }

  static final class CompressorEnvelopeRule implements Serializable {
    private static final long serialVersionUID = 1000L;
    final String tag;
    final double minimumSurgeMargin;
    final double minimumStonewallMargin;
    final double maximumDischargeTemperatureC;
    final double surgeControlMargin;

    CompressorEnvelopeRule(String tag, double minimumSurgeMargin, double minimumStonewallMargin,
        double maximumDischargeTemperatureC, double surgeControlMargin) {
      this.tag = text(tag, "compressorTag");
      this.minimumSurgeMargin = nonNegative(minimumSurgeMargin, "minimumSurgeMarginFraction");
      this.minimumStonewallMargin = nonNegative(minimumStonewallMargin, "minimumStonewallMarginFraction");
      this.maximumDischargeTemperatureC = positive(maximumDischargeTemperatureC, "maximumDischargeTemperatureC");
      this.surgeControlMargin = nonNegative(surgeControlMargin, "surgeControlMarginFraction");
    }
  }

  static final class HeatExchangerRule implements Serializable {
    private static final long serialVersionUID = 1000L;
    final String tag;
    final double overallU;
    final double lmtd;
    final double margin;
    final double[] areas;

    HeatExchangerRule(String tag, double overallU, double lmtd, double margin, double[] areas) {
      this.tag = text(tag, "equipmentTag");
      this.overallU = positive(overallU, "overallHeatTransferCoefficientWPerM2K");
      this.lmtd = positive(lmtd, "correctedLmtdK");
      this.margin = nonNegative(margin, "marginFraction");
      this.areas = positiveCandidates(areas, "areaCandidatesM2");
    }
  }

  static final class InventoryRule implements Serializable {
    private static final long serialVersionUID = 1000L;
    final String tag;
    final double workingTime;
    final double usableFraction;
    final double[] volumes;

    InventoryRule(String tag, double workingTime, double usableFraction, double[] volumes) {
      this.tag = text(tag, "equipmentTag");
      this.workingTime = positive(workingTime, "workingTimeSeconds");
      if (!Double.isFinite(usableFraction) || usableFraction <= 0.0 || usableFraction >= 1.0) {
        throw new IllegalArgumentException("usableVolumeFraction must be between zero and one");
      }
      this.usableFraction = usableFraction;
      this.volumes = positiveCandidates(volumes, "volumeCandidatesM3");
    }
  }

  static final class ControlValveRule implements Serializable {
    private static final long serialVersionUID = 1000L;
    final String tag;
    final double designOpening;
    final double maximumOpening;
    final double[] cvCandidates;

    ControlValveRule(String tag, double designOpening, double maximumOpening, double[] cvCandidates) {
      this.tag = text(tag, "valveTag");
      this.designOpening = positive(designOpening, "designOpeningPercent");
      this.maximumOpening = positive(maximumOpening, "maximumOpeningPercent");
      if (designOpening > maximumOpening || maximumOpening > 100.0) {
        throw new IllegalArgumentException("Valve openings must satisfy design <= maximum <= 100 percent");
      }
      this.cvCandidates = positiveCandidates(cvCandidates, "cvCandidates");
    }
  }

  static final class RatedCapacityRule implements Serializable {
    private static final long serialVersionUID = 1000L;
    final String tag;
    final EngineeringMetric metric;
    final String capacityName;
    final String unit;
    final double margin;
    final double[] candidates;

    RatedCapacityRule(String tag, EngineeringMetric metric, String capacityName, String unit, double margin,
        double[] candidates) {
      this.tag = text(tag, "equipmentTag");
      if (metric == null) {
        throw new IllegalArgumentException("metric must not be null");
      }
      this.metric = metric;
      this.capacityName = text(capacityName, "capacityName");
      this.unit = text(unit, "unit");
      this.margin = nonNegative(margin, "marginFraction");
      this.candidates = positiveCandidates(candidates, "capacityCandidates");
    }
  }

  static final class ReliefDeviceRule implements Serializable {
    private static final long serialVersionUID = 1000L;
    final String deviceTag;
    final String protectedEquipmentTag;
    final EngineeringMetric requiredAreaMetric;
    final double[] orificeCandidatesIn2;

    ReliefDeviceRule(String deviceTag, String protectedEquipmentTag, EngineeringMetric requiredAreaMetric,
        double[] orificeCandidatesIn2) {
      this.deviceTag = text(deviceTag, "deviceTag");
      this.protectedEquipmentTag = text(protectedEquipmentTag, "protectedEquipmentTag");
      if (requiredAreaMetric == null) {
        throw new IllegalArgumentException("requiredAreaMetric must not be null");
      }
      this.requiredAreaMetric = requiredAreaMetric;
      this.orificeCandidatesIn2 = positiveCandidates(orificeCandidatesIn2, "orificeCandidatesIn2");
    }
  }

  static final class NetworkRule implements Serializable {
    private static final long serialVersionUID = 1000L;
    final String id;
    final PipingRulePack rulePack;
    final PipingNetworkDesignModule.SegmentDefinition[] segments;

    NetworkRule(String id, PipingRulePack rulePack, PipingNetworkDesignModule.SegmentDefinition[] segments) {
      this.id = text(id, "networkId");
      if (rulePack == null || segments == null || segments.length == 0) {
        throw new IllegalArgumentException("rulePack and network segments are required");
      }
      this.rulePack = rulePack;
      this.segments = segments.clone();
    }
  }

  private final String id;
  private final String revision;
  private final List<SliceRule> slices = new ArrayList<SliceRule>();
  private final List<CompressorEnvelopeRule> compressorEnvelopes = new ArrayList<CompressorEnvelopeRule>();
  private final List<PumpRule> pumps = new ArrayList<PumpRule>();
  private final List<HeatExchangerRule> exchangers = new ArrayList<HeatExchangerRule>();
  private final List<InventoryRule> inventories = new ArrayList<InventoryRule>();
  private final List<ControlValveRule> controlValves = new ArrayList<ControlValveRule>();
  private final List<RatedCapacityRule> ratedCapacities = new ArrayList<RatedCapacityRule>();
  private final List<ReliefDeviceRule> reliefDevices = new ArrayList<ReliefDeviceRule>();
  private final List<NetworkRule> networks = new ArrayList<NetworkRule>();

  public EngineeringAutoConfigurationPolicy(String id, String revision) {
    this.id = text(id, "id");
    this.revision = text(revision, "revision");
  }

  public EngineeringAutoConfigurationPolicy addInletCompressionExportSlice(String separatorTag, String compressorTag,
      String lineTag, String valveTag, String instrumentTag) {
    slices.add(new SliceRule(separatorTag, compressorTag, lineTag, valveTag, instrumentTag, Double.NaN, Double.NaN,
        Double.NaN, Double.NaN, Double.NaN, Double.NaN, null, false));
    return this;
  }

  /** Adds a slice with all project-specific calculation limits and discrete driver candidates stated explicitly. */
  public EngineeringAutoConfigurationPolicy addInletCompressionExportSlice(String separatorTag, String compressorTag,
      String lineTag, String valveTag, String instrumentTag, double separatorLiquidDensityKgM3,
      double separatorSoudersBrownCoefficient, double separatorLiquidRetentionTimeSeconds,
      double maximumLineVelocityMPerS, double maximumPressureGradientBarPerKm, double compressorDriverMarginFraction,
      double... compressorDriverCandidatesKw) {
    slices.add(new SliceRule(separatorTag, compressorTag, lineTag, valveTag, instrumentTag,
        positive(separatorLiquidDensityKgM3, "separatorLiquidDensityKgM3"),
        positive(separatorSoudersBrownCoefficient, "separatorSoudersBrownCoefficient"),
        positive(separatorLiquidRetentionTimeSeconds, "separatorLiquidRetentionTimeSeconds"),
        positive(maximumLineVelocityMPerS, "maximumLineVelocityMPerS"),
        positive(maximumPressureGradientBarPerKm, "maximumPressureGradientBarPerKm"),
        nonNegative(compressorDriverMarginFraction, "compressorDriverMarginFraction"),
        positiveCandidates(compressorDriverCandidatesKw, "compressorDriverCandidatesKw"), true));
    return this;
  }

  public EngineeringAutoConfigurationPolicy addPump(String tag, double driverMarginFraction, double minimumNpshMarginM,
      double... driverCandidatesKw) {
    pumps.add(new PumpRule(tag, driverMarginFraction, minimumNpshMarginM, driverCandidatesKw));
    return this;
  }

  /** Adds governed compressor-map and anti-surge envelope limits for every configured design case. */
  public EngineeringAutoConfigurationPolicy addCompressorOperatingEnvelope(String compressorTag,
      double minimumSurgeMarginFraction, double minimumStonewallMarginFraction, double maximumDischargeTemperatureC,
      double surgeControlMarginFraction) {
    compressorEnvelopes.add(new CompressorEnvelopeRule(compressorTag, minimumSurgeMarginFraction,
        minimumStonewallMarginFraction, maximumDischargeTemperatureC, surgeControlMarginFraction));
    return this;
  }

  public EngineeringAutoConfigurationPolicy addHeatExchanger(String tag, double overallU, double correctedLmtdK,
      double marginFraction, double... areaCandidatesM2) {
    exchangers.add(new HeatExchangerRule(tag, overallU, correctedLmtdK, marginFraction, areaCandidatesM2));
    return this;
  }

  public EngineeringAutoConfigurationPolicy addInventory(String tag, double workingTimeSeconds,
      double usableVolumeFraction, double... volumeCandidatesM3) {
    inventories.add(new InventoryRule(tag, workingTimeSeconds, usableVolumeFraction, volumeCandidatesM3));
    return this;
  }

  public EngineeringAutoConfigurationPolicy addControlValve(String tag, double designOpeningPercent,
      double maximumOpeningPercent, double... cvCandidates) {
    controlValves.add(new ControlValveRule(tag, designOpeningPercent, maximumOpeningPercent, cvCandidates));
    return this;
  }

  public EngineeringAutoConfigurationPolicy addRatedCapacity(String tag, EngineeringMetric metric, String capacityName,
      String unit, double marginFraction, double... capacityCandidates) {
    ratedCapacities.add(new RatedCapacityRule(tag, metric, capacityName, unit, marginFraction, capacityCandidates));
    return this;
  }

  /** Adds governed API-style discrete PSV-orifice selection to the automatic design loop. */
  public EngineeringAutoConfigurationPolicy addReliefDevice(String deviceTag, String protectedEquipmentTag,
      EngineeringMetric requiredAreaMetric, double... apiOrificeCandidatesIn2) {
    reliefDevices
        .add(new ReliefDeviceRule(deviceTag, protectedEquipmentTag, requiredAreaMetric, apiOrificeCandidatesIn2));
    return this;
  }

  public EngineeringAutoConfigurationPolicy addPipingNetwork(String networkId, PipingRulePack rulePack,
      PipingNetworkDesignModule.SegmentDefinition... segments) {
    networks.add(new NetworkRule(networkId, rulePack, segments));
    return this;
  }

  public String getId() {
    return id;
  }

  public String getRevision() {
    return revision;
  }

  List<SliceRule> getSlices() {
    return Collections.unmodifiableList(slices);
  }

  List<PumpRule> getPumps() {
    return Collections.unmodifiableList(pumps);
  }

  List<CompressorEnvelopeRule> getCompressorEnvelopes() {
    return Collections.unmodifiableList(compressorEnvelopes);
  }

  List<HeatExchangerRule> getExchangers() {
    return Collections.unmodifiableList(exchangers);
  }

  List<InventoryRule> getInventories() {
    return Collections.unmodifiableList(inventories);
  }

  List<ControlValveRule> getControlValves() {
    return Collections.unmodifiableList(controlValves);
  }

  List<RatedCapacityRule> getRatedCapacities() {
    return Collections.unmodifiableList(ratedCapacities);
  }

  List<ReliefDeviceRule> getReliefDevices() {
    return Collections.unmodifiableList(reliefDevices);
  }

  List<NetworkRule> getNetworks() {
    return Collections.unmodifiableList(networks);
  }

  String fingerprintMaterial() {
    StringBuilder value = new StringBuilder(id).append('|').append(revision);
    for (SliceRule rule : slices) {
      value.append("|slice:").append(rule.separatorTag).append(':').append(rule.compressorTag).append(':')
          .append(rule.lineTag).append(':').append(rule.valveTag).append(':').append(rule.instrumentTag).append(':')
          .append(rule.separatorLiquidDensityKgM3).append(':').append(rule.separatorSoudersBrownCoefficient).append(':')
          .append(rule.separatorLiquidRetentionTimeSeconds).append(':').append(rule.maximumLineVelocityMPerS)
          .append(':').append(rule.maximumPressureGradientBarPerKm).append(':')
          .append(rule.compressorDriverMarginFraction).append(':')
          .append(java.util.Arrays.toString(rule.compressorDriverCandidatesKw)).append(':')
          .append(rule.explicitDesignInputs);
    }
    for (PumpRule rule : pumps) {
      value.append("|pump:").append(rule.tag).append(':').append(rule.margin).append(':')
          .append(rule.minimumNpshMarginM).append(':').append(java.util.Arrays.toString(rule.drivers));
    }
    for (CompressorEnvelopeRule rule : compressorEnvelopes) {
      value.append("|compressor-envelope:").append(rule.tag).append(':').append(rule.minimumSurgeMargin).append(':')
          .append(rule.minimumStonewallMargin).append(':').append(rule.maximumDischargeTemperatureC).append(':')
          .append(rule.surgeControlMargin);
    }
    for (HeatExchangerRule rule : exchangers) {
      value.append("|exchanger:").append(rule.tag).append(':').append(rule.overallU).append(':').append(rule.lmtd)
          .append(':').append(rule.margin).append(':').append(java.util.Arrays.toString(rule.areas));
    }
    for (InventoryRule rule : inventories) {
      value.append("|inventory:").append(rule.tag).append(':').append(rule.workingTime).append(':')
          .append(rule.usableFraction).append(':').append(java.util.Arrays.toString(rule.volumes));
    }
    for (ControlValveRule rule : controlValves) {
      value.append("|valve:").append(rule.tag).append(':').append(rule.designOpening).append(':')
          .append(rule.maximumOpening).append(':').append(java.util.Arrays.toString(rule.cvCandidates));
    }
    for (RatedCapacityRule rule : ratedCapacities) {
      value.append("|capacity:").append(rule.tag).append(':').append(rule.metric.getId()).append(':')
          .append(rule.capacityName).append(':').append(rule.unit).append(':').append(rule.margin).append(':')
          .append(java.util.Arrays.toString(rule.candidates));
    }
    for (ReliefDeviceRule rule : reliefDevices) {
      value.append("|relief:").append(rule.deviceTag).append(':').append(rule.protectedEquipmentTag).append(':')
          .append(rule.requiredAreaMetric.getId()).append(':')
          .append(java.util.Arrays.toString(rule.orificeCandidatesIn2));
    }
    for (NetworkRule rule : networks) {
      value.append("|network:").append(rule.id).append(':').append(rule.rulePack.toMap()).append(':');
      for (PipingNetworkDesignModule.SegmentDefinition segment : rule.segments) {
        value.append(segment.getLineTag()).append(',');
      }
    }
    return value.toString();
  }

  private static String optional(String value) {
    return value == null ? "" : value.trim();
  }

  private static double[] positiveCandidates(double[] values, String field) {
    if (values == null || values.length == 0) {
      throw new IllegalArgumentException(field + " must contain at least one candidate");
    }
    double[] result = values.clone();
    for (double value : result) {
      positive(value, field);
    }
    return result;
  }

  private static double positive(double value, String field) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(field + " must be finite and positive");
    }
    return value;
  }

  private static double nonNegative(double value, String field) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(field + " must be finite and non-negative");
    }
    return value;
  }

  private static String text(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }
}
