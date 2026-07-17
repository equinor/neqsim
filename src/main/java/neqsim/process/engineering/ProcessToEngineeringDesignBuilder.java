package neqsim.process.engineering;

import java.util.ArrayList;
import java.util.List;
import neqsim.process.engineering.design.modules.CompressorPackageDesignModule;
import neqsim.process.engineering.design.modules.CompressorOperatingEnvelopeDesignModule;
import neqsim.process.engineering.design.modules.ControlValveDesignModule;
import neqsim.process.engineering.design.modules.InstrumentRangeAndResponseDesignModule;
import neqsim.process.engineering.design.modules.HeatExchangerPreliminaryDesignModule;
import neqsim.process.engineering.design.modules.InventoryEquipmentDesignModule;
import neqsim.process.engineering.design.modules.LineHydraulicDesignModule;
import neqsim.process.engineering.design.modules.MaterialSelectionDesignModule;
import neqsim.process.engineering.design.modules.PressureEquipmentMechanicalDesignModule;
import neqsim.process.engineering.design.modules.PipingNetworkDesignModule;
import neqsim.process.engineering.design.modules.ProcessSafetyDesignModule;
import neqsim.process.engineering.design.modules.ReliefDeviceDesignModule;
import neqsim.process.engineering.design.modules.PumpPackageDesignModule;
import neqsim.process.engineering.design.modules.RatedCapacityDesignModule;
import neqsim.process.engineering.design.modules.SeparatorProcessDesignModule;
import neqsim.process.engineering.designcase.EngineeringMetric;
import neqsim.process.engineering.piping.PipingRulePack;
import neqsim.process.processmodel.ProcessModel;

/** Configures repeatable process-to-engineering design workflows on governed projects. */
public final class ProcessToEngineeringDesignBuilder {
  private final EngineeringProject project;
  private double separatorLiquidDensityKgM3 = 800.0;
  private double separatorGasLoadFactorMPerS = 0.107;
  private double separatorRetentionTimeSeconds = 120.0;
  private double maximumExportVelocityMPerS = 20.0;
  private double maximumExportPressureGradientBarPerKm = 0.5;
  private double driverMarginFraction = 0.10;
  private double[] driverCandidatesKw = new double[] { 500.0, 1000.0, 2000.0, 3000.0, 5000.0, 7500.0, 10000.0, 15000.0,
      20000.0, 30000.0, 50000.0 };

  private ProcessToEngineeringDesignBuilder(EngineeringProject project) {
    if (project == null) {
      throw new IllegalArgumentException("project must not be null");
    }
    this.project = project;
  }

  public static ProcessToEngineeringDesignBuilder on(EngineeringProject project) {
    return new ProcessToEngineeringDesignBuilder(project);
  }

  /** Creates one governed project per process area, ready for discipline-specific slice configuration. */
  public static List<EngineeringProject> fromProcessModel(String name, ProcessModel model,
      boolean registerProposedInstruments) {
    return new ArrayList<EngineeringProject>(
        NorsokOffshoreEngineeringBuilder.fromProcessModel(name, model, registerProposedInstruments));
  }

  public ProcessToEngineeringDesignBuilder separatorBasis(double liquidDensityKgM3, double gasLoadFactorMPerS,
      double retentionTimeSeconds) {
    separatorLiquidDensityKgM3 = liquidDensityKgM3;
    separatorGasLoadFactorMPerS = gasLoadFactorMPerS;
    separatorRetentionTimeSeconds = retentionTimeSeconds;
    return this;
  }

  public ProcessToEngineeringDesignBuilder exportLineLimits(double maximumVelocityMPerS,
      double maximumPressureGradientBarPerKm) {
    this.maximumExportVelocityMPerS = maximumVelocityMPerS;
    this.maximumExportPressureGradientBarPerKm = maximumPressureGradientBarPerKm;
    return this;
  }

  public ProcessToEngineeringDesignBuilder compressorDrivers(double marginFraction, double... candidatesKw) {
    driverMarginFraction = marginFraction;
    driverCandidatesKw = candidatesKw.clone();
    return this;
  }

  /**
   * Adds the complete inlet-separator, compressor, export-line, safeguarding, instrument, material, and mechanical
   * design chain. Blank optional valve or instrument tags omit those modules.
   */
  public EngineeringProject addInletCompressionExportSlice(String separatorTag, String compressorTag,
      String exportLineTag, String controlValveTag, String pressureInstrumentTag) {
    requireUnit(separatorTag);
    requireUnit(compressorTag);
    requireUnit(exportLineTag);
    String separatorPressure = separatorTag + ".pressure";
    String separatorGasFlow = separatorTag + ".gasOutletVolumeFlow";
    String separatorLiquidFlow = separatorTag + ".liquidOutletVolumeFlow";
    String compressorDensity = compressorTag + ".inletDensity";
    String compressorFlow = compressorTag + ".inletVolumeFlow";
    String compressorPower = compressorTag + ".power";
    String lineFlow = exportLineTag + ".inletVolumeFlow";
    String linePressureDrop = exportLineTag + ".pressureDrop";

    addMetricIfMissing(EngineeringMetric.equipmentPressure(separatorTag));
    addMetricIfMissing(EngineeringMetric.equipmentOutletVolumeFlow(separatorTag, 0, "gasOutlet"));
    addMetricIfMissing(EngineeringMetric.equipmentOutletVolumeFlow(separatorTag, 1, "liquidOutlet"));
    addMetricIfMissing(EngineeringMetric.equipmentInletDensity(compressorTag));
    addMetricIfMissing(EngineeringMetric.equipmentInletVolumeFlow(compressorTag));
    addMetricIfMissing(EngineeringMetric.compressorPower(compressorTag));
    addMetricIfMissing(EngineeringMetric.equipmentInletVolumeFlow(exportLineTag));
    addMetricIfMissing(EngineeringMetric.equipmentPressureDrop(exportLineTag));

    project.addEngineeringDesignModule(
        new SeparatorProcessDesignModule(separatorTag, separatorGasFlow, separatorLiquidFlow, compressorDensity,
            separatorLiquidDensityKgM3, separatorGasLoadFactorMPerS, separatorRetentionTimeSeconds));
    project.addEngineeringDesignModule(new LineHydraulicDesignModule(exportLineTag, lineFlow, linePressureDrop,
        maximumExportVelocityMPerS, maximumExportPressureGradientBarPerKm));
    project.addEngineeringDesignModule(new CompressorPackageDesignModule(compressorTag, compressorPower, compressorFlow,
        driverMarginFraction, driverCandidatesKw));
    project.addEngineeringDesignModule(
        new ProcessSafetyDesignModule(separatorTag, separatorPressure, 1.10, 2.0, 0.90, 6.9));
    project.addEngineeringDesignModule(
        new MaterialSelectionDesignModule(separatorTag, true, false, false, 25.0, 3.0, -46.0));
    project.addEngineeringDesignModule(new PressureEquipmentMechanicalDesignModule(separatorTag, separatorPressure,
        separatorTag + ".insideDiameter", separatorTag + ".tangentLength", 138.0, 0.85, 3.0));

    if (hasText(controlValveTag)) {
      requireUnit(controlValveTag);
      EngineeringMetric valveMetric = EngineeringMetric.controlValveRequiredCv(controlValveTag, 70.0);
      addMetricIfMissing(valveMetric);
      project
          .addEngineeringDesignModule(new ControlValveDesignModule(controlValveTag, valveMetric.getId(), 70.0, 80.0));
    }
    if (hasText(pressureInstrumentTag)) {
      project.addEngineeringDesignModule(
          new InstrumentRangeAndResponseDesignModule(pressureInstrumentTag, separatorTag, separatorPressure, "bara",
              0.20, 0.005, 10.0, 5.0, new double[] { 10.0, 16.0, 25.0, 40.0, 60.0, 100.0, 160.0, 250.0, 400.0 }));
    }
    return project;
  }

  /** Adds governing-duty and preliminary heat-transfer-area design for a heater, cooler, or exchanger. */
  public ProcessToEngineeringDesignBuilder addHeatExchangerDesign(String equipmentTag,
      double overallHeatTransferCoefficientWPerM2K, double correctedLmtdK, double marginFraction,
      double... areaCandidatesM2) {
    requireUnit(equipmentTag);
    EngineeringMetric duty = EngineeringMetric.equipmentDuty(equipmentTag);
    addMetricIfMissing(duty);
    project.addEngineeringDesignModule(new HeatExchangerPreliminaryDesignModule(equipmentTag, duty.getId(),
        overallHeatTransferCoefficientWPerM2K, correctedLmtdK, marginFraction, areaCandidatesM2));
    return this;
  }

  /** Adds compressor-map, surge, stonewall, anti-surge and discharge-temperature verification across every case. */
  public ProcessToEngineeringDesignBuilder addCompressorOperatingEnvelopeDesign(String compressorTag,
      double minimumSurgeMarginFraction, double minimumStonewallMarginFraction,
      double maximumDischargeTemperatureC, double surgeControlMarginFraction) {
    requireUnit(compressorTag);
    addMetricIfMissing(EngineeringMetric.compressorPolytropicHead(compressorTag));
    addMetricIfMissing(EngineeringMetric.compressorSpeed(compressorTag));
    addMetricIfMissing(EngineeringMetric.compressorPolytropicEfficiency(compressorTag));
    addMetricIfMissing(EngineeringMetric.compressorSurgeMargin(compressorTag));
    addMetricIfMissing(EngineeringMetric.compressorStonewallMargin(compressorTag));
    addMetricIfMissing(EngineeringMetric.compressorControlLineMargin(compressorTag));
    addMetricIfMissing(EngineeringMetric.compressorRequiredRecycleFraction(compressorTag));
    addMetricIfMissing(EngineeringMetric.compressorRecycleCoolerDuty(compressorTag));
    addMetricIfMissing(EngineeringMetric.compressorDischargeTemperature(compressorTag));
    addMetricIfMissing(EngineeringMetric.compressorChartExtrapolationFlag(compressorTag));
    project.addEngineeringDesignModule(new CompressorOperatingEnvelopeDesignModule(compressorTag,
        minimumSurgeMarginFraction, minimumStonewallMarginFraction, maximumDischargeTemperatureC,
        surgeControlMarginFraction));
    return this;
  }

  /** Adds pump driver selection and NPSH-margin verification across all configured cases. */
  public ProcessToEngineeringDesignBuilder addPumpDesign(String pumpTag, double driverMarginFraction,
      double minimumNpshMarginM, double... driverCandidatesKw) {
    requireUnit(pumpTag);
    EngineeringMetric power = EngineeringMetric.pumpPower(pumpTag);
    EngineeringMetric npsh = EngineeringMetric.pumpNpshMargin(pumpTag);
    addMetricIfMissing(power);
    addMetricIfMissing(npsh);
    project.addEngineeringDesignModule(new PumpPackageDesignModule(pumpTag, power.getId(), npsh.getId(),
        driverMarginFraction, minimumNpshMarginM, driverCandidatesKw));
    return this;
  }

  /** Adds case-envelope IEC 60534 Cv selection for a standalone control valve. */
  public ProcessToEngineeringDesignBuilder addControlValveDesign(String valveTag, double designOpeningPercent,
      double maximumOpeningPercent, double... cvCandidates) {
    requireUnit(valveTag);
    EngineeringMetric requiredCv = EngineeringMetric.controlValveRequiredCv(valveTag, designOpeningPercent);
    addMetricIfMissing(requiredCv);
    project.addEngineeringDesignModule(new ControlValveDesignModule(valveTag, requiredCv.getId(), designOpeningPercent,
        maximumOpeningPercent, cvCandidates));
    return this;
  }

  /** Adds residence/surge inventory sizing for tanks, drums, and column sumps. */
  public ProcessToEngineeringDesignBuilder addInventoryDesign(String equipmentTag, double workingTimeSeconds,
      double usableVolumeFraction, double... volumeCandidatesM3) {
    requireUnit(equipmentTag);
    EngineeringMetric flow = EngineeringMetric.equipmentInletVolumeFlow(equipmentTag);
    addMetricIfMissing(flow);
    project.addEngineeringDesignModule(new InventoryEquipmentDesignModule(equipmentTag, flow.getId(),
        workingTimeSeconds, usableVolumeFraction, volumeCandidatesM3));
    return this;
  }

  /** Adds a general discrete rating for any case-envelope metric, including columns and utility packages. */
  public ProcessToEngineeringDesignBuilder addRatedCapacity(String equipmentTag, EngineeringMetric metric,
      String capacityName, String unit, double marginFraction, double... candidates) {
    requireUnit(equipmentTag);
    addMetricIfMissing(metric);
    project.addEngineeringDesignModule(
        new RatedCapacityDesignModule(equipmentTag, metric.getId(), capacityName, unit, marginFraction, candidates));
    return this;
  }

  /** Adds discrete PSV-orifice selection to the converged physical design state. */
  public ProcessToEngineeringDesignBuilder addReliefDeviceDesign(String deviceTag, String protectedEquipmentTag,
      EngineeringMetric requiredAreaMetric, double... apiOrificeCandidatesIn2) {
    if (!hasText(deviceTag)) {
      throw new IllegalArgumentException("deviceTag must not be blank");
    }
    requireUnit(protectedEquipmentTag);
    addMetricIfMissing(requiredAreaMetric);
    project.addEngineeringDesignModule(new ReliefDeviceDesignModule(deviceTag, protectedEquipmentTag,
        requiredAreaMetric.getId(), apiOrificeCandidatesIn2));
    return this;
  }

  /** Adds network-level line sizing and applies every selected diameter within the common design loop. */
  public ProcessToEngineeringDesignBuilder addPipingNetworkDesign(String networkId, PipingRulePack rulePack,
      PipingNetworkDesignModule.SegmentDefinition... segments) {
    if (!hasText(networkId) || rulePack == null || segments == null || segments.length == 0) {
      throw new IllegalArgumentException("networkId, rulePack and at least one segment are required");
    }
    for (PipingNetworkDesignModule.SegmentDefinition segment : segments) {
      if (segment == null) {
        throw new IllegalArgumentException("network segment must not be null");
      }
      requireUnit(segment.getLineTag());
      addMetricIfMissing(EngineeringMetric.equipmentInletVolumeFlow(segment.getLineTag()));
      addMetricIfMissing(EngineeringMetric.equipmentPressureDrop(segment.getLineTag()));
    }
    project.addEngineeringDesignModule(
        new PipingNetworkDesignModule(networkId, rulePack, java.util.Arrays.asList(segments)));
    return this;
  }

  private void addMetricIfMissing(EngineeringMetric metric) {
    for (EngineeringMetric existing : project.getEngineeringMetrics()) {
      if (existing.getId().equals(metric.getId())) {
        return;
      }
    }
    project.addEngineeringMetric(metric);
  }

  private void requireUnit(String tag) {
    if (!hasText(tag) || project.getProcessSystem().getUnit(tag) == null) {
      throw new IllegalArgumentException("Unknown or blank process equipment tag " + tag);
    }
  }

  private static boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }
}
