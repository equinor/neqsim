package neqsim.process.engineering.verticalslice;

import java.io.Serializable;
import neqsim.process.engineering.EngineeringEvidenceRecord;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.NorsokOffshoreEngineeringBuilder;
import neqsim.process.engineering.ReliefDeviceDesignInput;
import neqsim.process.engineering.ReliefScenarioBasis;
import neqsim.process.engineering.ShutdownSequence;
import neqsim.process.engineering.design.modules.PipingNetworkDesignModule;
import neqsim.process.engineering.designcase.EngineeringDesignCase;
import neqsim.process.engineering.designcase.EngineeringMetric;
import neqsim.process.engineering.piping.PipingRulePack;
import neqsim.process.engineering.production.EngineeringAutoConfigurationPolicy;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.compressor.SafeSplineStoneWallCurve;
import neqsim.process.equipment.compressor.SafeSplineSurgeCurve;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.pipeline.AdiabaticPipe;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.valve.BlowdownValve;
import neqsim.process.equipment.valve.ESDValve;
import neqsim.process.equipment.valve.SafetyReliefValve;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.depressurization.CoupledReliefBlowdownFlareInput;
import neqsim.process.safety.depressurization.DynamicBlowdownFlareStudyDataSource;
import neqsim.process.safety.overpressure.BlockedOutletRelief;
import neqsim.process.safety.overpressure.OverpressureProtectionStudy;
import neqsim.process.safety.overpressure.ProtectedItem;
import neqsim.process.safety.overpressure.ReliefCause;
import neqsim.process.safety.overpressure.ReliefScenario;
import neqsim.process.util.fire.ReliefValveSizing;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Builds the executable inlet-separation, compression, cooling and export reference facility.
 *
 * <p>
 * The values are an explicit synthetic regression basis, not hidden project defaults or construction data. Projects
 * should reproduce this object shape with their controlled case, vendor, HAZOP, SRS, relief, piping and materials
 * evidence.
 * </p>
 */
public final class InletCompressionExportReferenceFacility {
  public static final String FEED = "FEED";
  public static final String SEPARATOR = "INLET-SEP";
  public static final String SUCTION_ESD = "ESDV-SUCTION";
  public static final String COMPRESSOR = "EXPORT-COMP";
  public static final String COOLER = "AFTERCOOLER";
  public static final String DISCHARGE_ESD = "ESDV-DISCHARGE";
  public static final String PCV = "PCV";
  public static final String EXPORT_LINE = "EXPORT-LINE";
  public static final String ASV = "ASV";
  public static final String ASV_RECYCLE = "ASV-RECYCLE";
  public static final String LCV = "LCV";
  public static final String PSV = "PSV";
  public static final String BDV = "BDV";
  public static final String FLARE_CONNECTION = "FLARE-CONNECTION";
  public static final String DYNAMIC_SCENARIO = "compressor-trip-esd";
  public static final String DESIGN_BASIS_EVIDENCE = "REF-DESIGN-BASIS";
  public static final String HAZOP_EVIDENCE = "REF-HAZOP";
  public static final String MAP_EVIDENCE = "REF-COMPRESSOR-MAP";
  public static final String SRS_EVIDENCE = "REF-SRS-CAUSE-EFFECT";
  public static final String RELIEF_EVIDENCE = "REF-RELIEF-FLARE";
  public static final String MATERIALS_EVIDENCE = "REF-MATERIALS";

  private InletCompressionExportReferenceFacility() {
  }

  /** Builds a fully connected process plus its controlled project policies and study inputs. */
  public static Definition build() {
    ProcessSystem process = process();
    InletCompressionExportSlicePolicy qualificationPolicy = qualificationPolicy();
    EngineeringProject project = NorsokOffshoreEngineeringBuilder
        .from("Qualified engineering reference facility", process)
        .projectId("neqsim-qualified-inlet-compression-export-reference").registerProposedInstruments(true).build();
    addEvidence(project);
    addCases(project, qualificationPolicy);
    project.addDynamicSafetyScenario(VerticalSliceDynamicScenarioFactory.emergencyShutdown(DYNAMIC_SCENARIO,
        qualificationPolicy, 24.0, 1.0, 1.0, 12.0, 8.0, SRS_EVIDENCE));
    addReliefAndFlare(project, process);
    addShutdownSequence(project);
    project.addReliefDeviceDesignInput(new ReliefDeviceDesignInput(PSV, COOLER).setSelectedOrificeAreaIn2(0.785)
        .setInletPiping(0.1023, 5.0, 3.0).setOutletPiping(0.1541, 35.0, 8.0).setAllowableInletLossPercent(3.0)
        .setAllowableBuiltUpBackPressurePercent(10.0).setConcurrencyGroup("FIRE-ZONE-A").setFireZone("FIRE-ZONE-A")
        .setTwoPhaseMethod("API 520 OMEGA SCREEN").setEvidenceReference(RELIEF_EVIDENCE));
    project.addReliefScenarioBasis(new ReliefScenarioBasis(COOLER).require(ReliefCause.BLOCKED_OUTLET)
        .require(ReliefCause.FIRE).setHazardReviewReference(HAZOP_EVIDENCE).addEvidenceReference(RELIEF_EVIDENCE));
    return new Definition(project, autoConfigurationPolicy(), qualificationPolicy);
  }

  private static ProcessSystem process() {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.88);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("propane", 0.02);
    fluid.addComponent("n-heptane", 0.05);
    fluid.setMixingRule("classic");

    Stream feed = new Stream(FEED, fluid);
    feed.setFlowRate(8000.0, "kg/hr");
    Separator separator = new Separator(SEPARATOR, feed);
    ESDValve suctionEsd = new ESDValve(SUCTION_ESD, separator.getGasOutStream());
    suctionEsd.setCv(1200.0);
    suctionEsd.setStrokeTime(8.0);
    suctionEsd.setOutletPressure(44.0, "bara");

    Stream recycleReturn = new Stream("ASV-RETURN", fluid.clone());
    recycleReturn.setFlowRate(1.0, "kg/hr");
    Mixer suctionMixer = new Mixer("SUCTION-MIXER");
    suctionMixer.addStream(suctionEsd.getOutletStream());
    suctionMixer.addStream(recycleReturn);

    Compressor compressor = new Compressor(COMPRESSOR, suctionMixer.getOutletStream());
    compressor.setOutletPressure(80.0, "bara");
    compressor.setPolytropicEfficiency(0.78);
    compressor.setSpeed(10000.0);
    configureCompressorMap(compressor);

    Cooler cooler = new Cooler(COOLER, compressor.getOutletStream());
    cooler.setOutTemperature(35.0, "C");
    Splitter dischargeSplitter = new Splitter("DISCHARGE-SPLITTER", cooler.getOutletStream(), 2);
    dischargeSplitter.setSplitFactors(new double[] { 0.95, 0.05 });

    ESDValve dischargeEsd = new ESDValve(DISCHARGE_ESD, dischargeSplitter.getSplitStream(0));
    dischargeEsd.setCv(1200.0);
    dischargeEsd.setStrokeTime(8.0);
    ThrottlingValve pressureControl = new ThrottlingValve(PCV, dischargeEsd.getOutletStream());
    pressureControl.setCv(800.0);
    pressureControl.setPercentValveOpening(70.0);
    pressureControl.setOutletPressure(40.0, "bara");
    AdiabaticPipe exportLine = new AdiabaticPipe(EXPORT_LINE, pressureControl.getOutletStream());
    exportLine.setLength(2000.0);
    exportLine.setDiameter(0.2027);
    exportLine.setPipeWallRoughness(4.6e-5);

    ThrottlingValve antiSurgeValve = new ThrottlingValve(ASV, dischargeSplitter.getSplitStream(1));
    antiSurgeValve.setCv(500.0);
    antiSurgeValve.setPercentValveOpening(20.0);
    antiSurgeValve.setOutletPressure(50.0, "bara");
    Cooler recycleCooler = new Cooler("ASV-COOLER", antiSurgeValve.getOutletStream());
    recycleCooler.setOutTemperature(30.0, "C");
    Recycle recycle = new Recycle(ASV_RECYCLE);
    recycle.addStream(recycleCooler.getOutletStream());
    recycle.setOutletStream(recycleReturn);
    recycle.setTolerance(0.01);

    ThrottlingValve levelControl = new ThrottlingValve(LCV, separator.getLiquidOutStream());
    levelControl.setCv(50.0);
    levelControl.setPercentValveOpening(50.0);
    levelControl.setOutletPressure(10.0, "bara");

    SafetyReliefValve reliefValve = new SafetyReliefValve(PSV, cooler.getOutletStream());
    reliefValve.setSetPressureBar(90.0);
    reliefValve.setRatedCv(250.0);
    reliefValve.setOutletPressure(1.5, "bara");
    BlowdownValve blowdownValve = new BlowdownValve(BDV, cooler.getOutletStream());
    blowdownValve.setCv(150.0);
    blowdownValve.setOpeningTime(5.0);
    blowdownValve.setOutletPressure(1.5, "bara");
    Mixer flareConnection = new Mixer(FLARE_CONNECTION);
    flareConnection.addStream(reliefValve.getOutletStream());
    flareConnection.addStream(blowdownValve.getOutletStream());

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(separator);
    process.add(suctionEsd);
    process.add(recycleReturn);
    process.add(suctionMixer);
    process.add(compressor);
    process.add(cooler);
    process.add(dischargeSplitter);
    process.add(dischargeEsd);
    process.add(pressureControl);
    process.add(exportLine);
    process.add(antiSurgeValve);
    process.add(recycleCooler);
    process.add(recycle);
    process.add(levelControl);
    process.add(reliefValve);
    process.add(blowdownValve);
    process.add(flareConnection);
    process.run();
    return process;
  }

  private static void configureCompressorMap(Compressor compressor) {
    double[] conditions = new double[] { 19.0, 300.0, 50.0, 0.90 };
    double[] speeds = new double[] { 9000.0, 11000.0 };
    double[][] flows = new double[][] { { 20.0, 80.0, 145.0 }, { 28.0, 115.0, 205.0 } };
    double[][] heads = new double[][] { { 78.0, 68.0, 52.0 }, { 115.0, 98.0, 74.0 } };
    double[][] efficiencies = new double[][] { { 70.0, 79.0, 73.0 }, { 71.0, 80.0, 74.0 } };
    compressor.getCompressorChart().setCurves(conditions, speeds, flows, heads, efficiencies);
    compressor.getCompressorChart()
        .setSurgeCurve(new SafeSplineSurgeCurve(new double[] { 16.0, 25.0, 38.0 }, new double[] { 115.0, 95.0, 70.0 }));
    compressor.getCompressorChart().setStoneWallCurve(
        new SafeSplineStoneWallCurve(new double[] { 160.0, 190.0, 220.0 }, new double[] { 115.0, 95.0, 70.0 }));
    compressor.getCompressorChart().setHeadUnit("kJ/kg");
    compressor.getCompressorChart().setUseCompressorChart(true);
    compressor.getAntiSurge().setActive(true);
    compressor.getAntiSurge().setSurgeControlFactor(1.10);
  }

  private static InletCompressionExportSlicePolicy qualificationPolicy() {
    return InletCompressionExportSlicePolicy.builder("qualified-reference-facility", "1.0")
        .processTags(SEPARATOR, COMPRESSOR, COOLER, EXPORT_LINE).controlTags(ASV, ASV_RECYCLE, PCV, LCV)
        .safetyTags(PSV, BDV, SUCTION_ESD, DISCHARGE_ESD, FLARE_CONNECTION).addRequiredDynamicScenario(DYNAMIC_SCENARIO)
        .addEvidenceReference(DESIGN_BASIS_EVIDENCE).addEvidenceReference(HAZOP_EVIDENCE)
        .addEvidenceReference(MAP_EVIDENCE).addEvidenceReference(SRS_EVIDENCE).addEvidenceReference(RELIEF_EVIDENCE)
        .addEvidenceReference(MATERIALS_EVIDENCE).build();
  }

  private static void addEvidence(EngineeringProject project) {
    project.addEvidenceRecord(
        evidence(DESIGN_BASIS_EVIDENCE, "PROCESS_DESIGN_BASIS", "Synthetic case matrix basis", SEPARATOR));
    project.addEvidenceRecord(evidence(HAZOP_EVIDENCE, "HAZOP", "Synthetic scenario credibility record", SEPARATOR));
    project.addEvidenceRecord(
        evidence(MAP_EVIDENCE, "VENDOR_MAP", "Synthetic compressor map regression basis", COMPRESSOR));
    project.addEvidenceRecord(evidence(SRS_EVIDENCE, "SRS_CAUSE_AND_EFFECT",
        "Synthetic trip, isolation and depressurization sequence", COMPRESSOR));
    project.addEvidenceRecord(evidence(RELIEF_EVIDENCE, "RELIEF_AND_FLARE_CALCULATION",
        "Synthetic coupled relief, blowdown and flare basis", PSV));
    project.addEvidenceRecord(evidence(MATERIALS_EVIDENCE, "MATERIALS_SELECTION",
        "Synthetic preliminary materials and MDMT basis", SEPARATOR));
  }

  private static EngineeringEvidenceRecord evidence(String id, String type, String title, String tag) {
    return new EngineeringEvidenceRecord(id, type, "1.0").setTitle(title).setSourceOrganization("NeqSim regression")
        .setChecksum("synthetic-reference-" + id).linkEquipment(tag);
  }

  private static void addCases(EngineeringProject project, InletCompressionExportSlicePolicy policy) {
    VerticalSliceCaseMatrixFactory.Builder cases = VerticalSliceCaseMatrixFactory.builder(FEED, COMPRESSOR);
    cases.addCase("normal", "Normal operation", EngineeringDesignCase.Type.NORMAL, 8000.0, 50.0, 25.0, 80.0,
        DESIGN_BASIS_EVIDENCE, "REVIEW_REQUIRED");
    cases.addCase("turndown", "Minimum turndown", EngineeringDesignCase.Type.MINIMUM_TURNDOWN, 5000.0, 48.0, 20.0, 76.0,
        DESIGN_BASIS_EVIDENCE, "REVIEW_REQUIRED");
    cases.addCase("maximum", "Maximum production", EngineeringDesignCase.Type.MAXIMUM_PRODUCTION, 10000.0, 52.0, 30.0,
        82.0, DESIGN_BASIS_EVIDENCE, "REVIEW_REQUIRED");
    cases.addCase("startup", "Startup", EngineeringDesignCase.Type.STARTUP, 6000.0, 45.0, 20.0, 70.0,
        DESIGN_BASIS_EVIDENCE, "REVIEW_REQUIRED");
    cases.addCase("shutdown", "Controlled shutdown", EngineeringDesignCase.Type.SHUTDOWN, 3000.0, 45.0, 25.0, 65.0,
        SRS_EVIDENCE, "REVIEW_REQUIRED");
    cases.addCase("trip", "Compressor trip initial state", EngineeringDesignCase.Type.EQUIPMENT_TRIP, 4000.0, 48.0,
        25.0, 75.0, SRS_EVIDENCE, "REVIEW_REQUIRED");
    cases.addCase("settle-out", "Compressor settle-out", EngineeringDesignCase.Type.SETTLE_OUT, 4000.0, 50.0, 30.0,
        70.0, SRS_EVIDENCE, "REVIEW_REQUIRED");
    cases.addCase("blocked-outlet", "Blocked export outlet", EngineeringDesignCase.Type.BLOCKED_OUTLET, 10000.0, 52.0,
        30.0, 90.0, RELIEF_EVIDENCE, "REVIEW_REQUIRED");
    cases.addCase("fire", "Fire exposure", EngineeringDesignCase.Type.FIRE, 8000.0, 50.0, 45.0, 90.0, RELIEF_EVIDENCE,
        "REVIEW_REQUIRED");
    cases.addCase("blowdown", "Emergency depressurization initial state", EngineeringDesignCase.Type.BLOWDOWN, 5000.0,
        50.0, 25.0, 75.0, RELIEF_EVIDENCE, "REVIEW_REQUIRED");
    cases.applyTo(project, policy);
  }

  private static EngineeringAutoConfigurationPolicy autoConfigurationPolicy() {
    EngineeringMetric reliefArea = blockedOutletReliefAreaMetric();
    PipingRulePack pipingRules = PipingRulePack.builder("reference-norsok-p002-2023-ac2024").standard("NORSOK P-002")
        .edition("2023+AC:2024").velocityLimits(20.0, 5.0, 0.5).maximumPressureGradientBarPerKm(5.0)
        .maximumReliefInletLossFraction(0.03).build();
    return new EngineeringAutoConfigurationPolicy("qualified-reference-facility", "1.0")
        .addInletCompressionExportSlice(SEPARATOR, COMPRESSOR, EXPORT_LINE, PCV, "PIT-100", 750.0, 0.107, 180.0, 20.0,
            5.0, 0.10, 500.0, 1000.0, 2000.0, 5000.0, 10000.0)
        .addCompressorOperatingEnvelope(COMPRESSOR, 0.02, 0.02, 180.0, 0.10)
        .addHeatExchanger(COOLER, 500.0, 25.0, 0.15, 10.0, 25.0, 50.0, 100.0, 200.0)
        .addHeatExchanger("ASV-COOLER", 400.0, 20.0, 0.15, 5.0, 10.0, 25.0, 50.0)
        .addControlValve(ASV, 70.0, 90.0, 50.0, 100.0, 250.0, 500.0, 1000.0)
        .addControlValve(LCV, 50.0, 80.0, 5.0, 10.0, 25.0, 50.0, 100.0)
        .addControlValve(PSV, 100.0, 100.0, 50.0, 100.0, 250.0, 500.0)
        .addControlValve(BDV, 100.0, 100.0, 50.0, 100.0, 250.0, 500.0)
        .addControlValve(SUCTION_ESD, 100.0, 100.0, 250.0, 500.0, 1000.0, 2000.0)
        .addControlValve(DISCHARGE_ESD, 100.0, 100.0, 250.0, 500.0, 1000.0, 2000.0)
        .addReliefDevice(PSV, COOLER, reliefArea, ReliefValveSizing.STANDARD_ORIFICE_AREAS_IN2).addPipingNetwork(
            "export-network", pipingRules, PipingNetworkDesignModule.SegmentDefinition.processLine(EXPORT_LINE, true));
  }

  private static EngineeringMetric blockedOutletReliefAreaMetric() {
    return new EngineeringMetric(PSV + ".requiredOrificeArea", PSV, "Blocked-outlet required PSV area", "in2",
        EngineeringMetric.GoverningDirection.MAXIMUM, new EngineeringMetric.Extractor() {
          private static final long serialVersionUID = 1000L;

          @Override
          public double extract(ProcessSystem process) {
            Separator separator = (Separator) process.getUnit(SEPARATOR);
            StreamInterface gas = separator.getGasOutStream();
            SystemInterface fluid = gas.getFluid();
            fluid.initProperties();
            double massFlowKgPerS = Math.max(gas.getFlowRate("kg/sec"), 1.0e-9);
            return ReliefValveSizing
                .calculateRequiredArea(massFlowKgPerS, 90.0e5, 0.10, 1.5e5, fluid.getTemperature(),
                    fluid.getMolarMass(), Math.max(fluid.getZ(), 0.1), Math.max(fluid.getGamma(), 1.01), false, false)
                .getRequiredAreaIn2();
          }
        });
  }

  private static void addReliefAndFlare(EngineeringProject project, ProcessSystem process) {
    SystemInterface fluid = ((Separator) process.getUnit(SEPARATOR)).getGasOutStream().getFluid().clone();
    ReliefScenario blockedOutlet = new BlockedOutletRelief().setName("Blocked export outlet")
        .setInflowRateKgPerHr(10000.0).setReliefPressureBara(90.0).setReliefTemperatureC(45.0).setFluid(fluid)
        .calculate();
    OverpressureProtectionStudy reliefStudy = new OverpressureProtectionStudy(
        new ProtectedItem(COOLER, 90.0).setReliefSetPressureBara(90.0).setBackPressureBara(1.5))
        .addScenario(blockedOutlet);
    DynamicBlowdownFlareStudyDataSource.BlowdownSource source = DynamicBlowdownFlareStudyDataSource.BlowdownSource
        .builder(COOLER, fluid).equipmentTag(COOLER).vesselVolumeM3(5.0).orificeDiameterM(0.025)
        .dischargeCoefficient(0.72).backPressureBara(1.5).stopPressureBara(1.5).api521FireCase(20.0, true, true)
        .psvBasis(90.0, 0.21, false, false).build();
    DynamicBlowdownFlareStudyDataSource dynamic = DynamicBlowdownFlareStudyDataSource.builder("reference-flare-study")
        .addSource(source).flareHeader(0.50, 1.5, 288.15, 0.020, 1.30).maxAllowableHeaderMach(0.50)
        .flareGeometry(0.60, 40.0, 0.20).flareDesignCapacity(1.0e9, 100.0, 50000.0).sourceDiagramsReviewed(true)
        .pidTopologyVerified(true).lineEquipmentListsReviewed(true).pipingSpecificationRowsReviewed(true)
        .vesselInventoryReviewed(true).valveSizingBasisReviewed(true).psvBasisReviewed(true)
        .flareSystemBasisReviewed(true).fireCaseReviewed(true).standardsReviewed(true).humanReviewRequired(true)
        .build();
    project.addOverpressureStudy(reliefStudy).addBlowdownFlareStudy(dynamic)
        .addCoupledReliefBlowdownFlareStudy(CoupledReliefBlowdownFlareInput.builder("reference-coupled-safety")
            .addReliefStudy(reliefStudy, "FIRE-ZONE-A").dynamicStudy(dynamic).scenarioSelectionReviewed(true)
            .addEvidenceReference(RELIEF_EVIDENCE).addEvidenceReference(HAZOP_EVIDENCE).build());
  }

  private static void addShutdownSequence(EngineeringProject project) {
    project.addShutdownSequence(new ShutdownSequence("ESD-COMPRESSOR", "Compressor trip or process ESD")
        .setProtectedEquipmentTag(COMPRESSOR).setSafeState("Isolated, recycle open and depressurizing")
        .setHazopReference(HAZOP_EVIDENCE).setSrsReference(SRS_EVIDENCE).setResponseTimeBudgetSeconds(15.0)
        .setResetAndRestartDefined(true).addRequirementId(COMPRESSOR + "-ISOLATION-BLOWDOWN")
        .addAction(new ShutdownSequence.Action(ASV, "OPEN", "FAIL_OPEN", 0.0, 2.0))
        .addAction(new ShutdownSequence.Action(SUCTION_ESD, "CLOSE", "FAIL_CLOSED", 0.0, 8.0))
        .addAction(new ShutdownSequence.Action(DISCHARGE_ESD, "CLOSE", "FAIL_CLOSED", 0.0, 8.0))
        .addAction(new ShutdownSequence.Action(BDV, "OPEN", "FAIL_OPEN", 0.0, 5.0)));
  }

  /** Complete controlled inputs required to execute the reference facility. */
  public static final class Definition implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final EngineeringProject project;
    private final EngineeringAutoConfigurationPolicy autoConfigurationPolicy;
    private final InletCompressionExportSlicePolicy qualificationPolicy;

    Definition(EngineeringProject project, EngineeringAutoConfigurationPolicy autoConfigurationPolicy,
        InletCompressionExportSlicePolicy qualificationPolicy) {
      this.project = project;
      this.autoConfigurationPolicy = autoConfigurationPolicy;
      this.qualificationPolicy = qualificationPolicy;
    }

    public EngineeringProject getProject() {
      return project;
    }

    public EngineeringAutoConfigurationPolicy getAutoConfigurationPolicy() {
      return autoConfigurationPolicy;
    }

    public InletCompressionExportSlicePolicy getQualificationPolicy() {
      return qualificationPolicy;
    }
  }
}
