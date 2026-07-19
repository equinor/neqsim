package neqsim.process.fielddevelopment.lifecycle;

import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.reservoir.SimpleReservoir;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.fielddevelopment.concept.FieldConcept;
import neqsim.process.fielddevelopment.concept.InfrastructureInput;
import neqsim.process.fielddevelopment.concept.ReservoirInput;
import neqsim.process.fielddevelopment.concept.WellsInput;
import neqsim.process.fielddevelopment.reservoir.InjectionWellModel;
import neqsim.process.fielddevelopment.reservoir.InjectionWellModel.InjectionType;
import neqsim.process.mechanicaldesign.subsea.SURFCostEstimator;
import neqsim.process.mechanicaldesign.subsea.SubseaCostEstimator;
import neqsim.process.mechanicaldesign.subsea.WellCostEstimator;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;

/**
 * Reference Norwegian Continental Shelf oil development for lifecycle concept studies.
 *
 * <p>
 * The case is synthetic and must not be interpreted as data for a named field. It represents a medium-sized subsea oil
 * development tied to an FPSO in approximately 300 m water depth. The model includes a compositional PR-EOS PVT
 * description, tank material balance, aggregate production wells, multiphase tubing and flowline hydraulics,
 * three-phase separation, oil export pumping, produced-gas export and two-stage gas-injection compression.
 * </p>
 *
 * <p>
 * Well and SURF CAPEX are calculated with NeqSim engineering estimators. Topsides and project costs use a documented
 * Class-4 parametric allowance, making the case appropriate for DG1/DG2 comparison rather than project sanction.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class NorwegianOilFieldCase {
  private static final int PRODUCERS = 6;
  private static final int GAS_INJECTORS = 3;
  private static final double WATER_DEPTH_M = 300.0;

  private NorwegianOilFieldCase() {
  }

  /** Creates the reference gas-injection concept. */
  public static FieldLifecycleConcept createGasInjectionCase() {
    return createCase("NCS subsea oil with gas injection", 0.85, 5.0e6);
  }

  /** Creates an otherwise identical natural-depletion comparison concept. */
  public static FieldLifecycleConcept createNaturalDepletionCase() {
    return createCase("NCS subsea oil natural depletion", 0.0, 0.0);
  }

  /**
   * Creates a reference case with a selectable produced-gas recycle strategy.
   *
   * @param name concept name
   * @param recycleFraction produced-gas fraction sent to injection
   * @param maximumInjectionRateSm3d surface gas-injection capacity
   * @return executable field concept
   */
  public static FieldLifecycleConcept createCase(String name, double recycleFraction, double maximumInjectionRateSm3d) {
    FieldConcept fieldConcept = createScreeningConcept(name);
    FieldLifecycleModel model = createModel(name);
    double injectionWellCapacity = estimateGasInjectionCapacity();
    double gasInjectionCapacity = Math.min(maximumInjectionRateSm3d, injectionWellCapacity);
    double totalCapexMusd = estimateDevelopmentCapexMusd();
    int firstOilYear = 2029;

    FieldLifecycleConfiguration configuration = FieldLifecycleConfiguration.builder().startYear(firstOilYear)
        .projectYears(25.0).timeStepDays(365.25).availability(0.92).producers(PRODUCERS, 55.0)
        .minimumBottomHolePressure(250.0).plateauOilRate(23000.0).facilityCapacities(42000.0, 5.5e6, 28000.0)
        .economicLimitOilRate(600.0).waterCut(0.04, 0.78, 3.0, 15.0).standardDensities(835.0, 1025.0)
        .gasInjection(0.0, recycleFraction, gasInjectionCapacity).gridEmissionFactor(0.018).prices(75.0, 0.28)
        .discountRate(0.08).opex(135.0, 8.5).tariffs(2.0, 0.015).capex(firstOilYear - 3, totalCapexMusd * 0.20)
        .capex(firstOilYear - 2, totalCapexMusd * 0.50).capex(firstOilYear - 1, totalCapexMusd * 0.30).build();

    return new FieldLifecycleConcept(fieldConcept, model, configuration);
  }

  private static FieldConcept createScreeningConcept(String name) {
    ReservoirInput reservoir = ReservoirInput.blackOil().gor(180.0, "Sm3/Sm3").waterCut(0.04).reservoirPressure(330.0)
        .reservoirTemperature(90.0).apiGravity(34.0).resourceUncertainty(550.0, 700.0, 850.0, "MMbbl")
        .recoveryFactor(0.48).build();
    WellsInput wells = WellsInput.builder().producerCount(PRODUCERS).injectorCount(GAS_INJECTORS).tubeheadPressure(85.0)
        .ratePerWell(23000.0 / PRODUCERS, "Sm3/day").productivityIndex(55.0).build();
    InfrastructureInput infrastructure = InfrastructureInput.builder()
        .processingLocation(InfrastructureInput.ProcessingLocation.FPSO).waterDepth(WATER_DEPTH_M).tiebackLength(12.0)
        .exportType(InfrastructureInput.ExportType.STABILIZED_OIL)
        .powerSupply(InfrastructureInput.PowerSupply.POWER_FROM_SHORE).exportPressure(180.0).build();
    return FieldConcept.builder(name)
        .description("Synthetic NCS subsea oil development with FPSO processing and optional produced-gas injection")
        .reservoir(reservoir).wells(wells).infrastructure(infrastructure).build();
  }

  private static FieldLifecycleModel createModel(String name) {
    SystemInterface reservoirFluid = createReservoirFluid();
    SimpleReservoir reservoir = new SimpleReservoir(name + " reservoir");
    reservoir.setReservoirFluid(reservoirFluid, 45.0e6, 180.0e6, 90.0e6);
    reservoir.setLowPressureLimit(180.0, "bara");

    StreamInterface oilProducer = reservoir.addOilProducer("PROD-bank oil");
    StreamInterface waterProducer = reservoir.addWaterProducer("PROD-bank water");
    StreamInterface reservoirGasInjector = reservoir.addGasInjector("GI-bank");

    Mixer wellstreamMixer = new Mixer("Production wellstream mixer");
    wellstreamMixer.addStream(oilProducer);
    wellstreamMixer.addStream(waterProducer);

    PipeBeggsAndBrills aggregateTubing = new PipeBeggsAndBrills("Aggregate production tubing",
        wellstreamMixer.getOutletStream());
    aggregateTubing.setLength(2800.0);
    aggregateTubing.setElevation(2200.0);
    aggregateTubing.setDiameter(0.305);
    aggregateTubing.setPipeWallRoughness(2.5e-5);
    aggregateTubing.setNumberOfIncrements(5);

    PipeBeggsAndBrills productionFlowline = new PipeBeggsAndBrills("Production flowline",
        aggregateTubing.getOutletStream());
    productionFlowline.setLength(12000.0);
    productionFlowline.setElevation(WATER_DEPTH_M);
    productionFlowline.setDiameter(0.356);
    productionFlowline.setPipeWallRoughness(1.5e-5);
    productionFlowline.setNumberOfIncrements(5);

    ThrottlingValve inletChoke = new ThrottlingValve("FPSO inlet choke", productionFlowline.getOutletStream());
    inletChoke.setOutletPressure(55.0, "bara");
    ThreePhaseSeparator hpSeparator = new ThreePhaseSeparator("HP separator", inletChoke.getOutletStream());

    ThrottlingValve lpValve = new ThrottlingValve("LP separator letdown", hpSeparator.getOilOutStream());
    lpValve.setOutletPressure(5.0, "bara");
    ThreePhaseSeparator lpSeparator = new ThreePhaseSeparator("LP separator", lpValve.getOutletStream());
    Pump oilExportPump = new Pump("Oil export pump", lpSeparator.getOilOutStream());
    oilExportPump.setOutletPressure(25.0, "bara");

    Compressor lpGasCompressor = new Compressor("LP gas compressor", lpSeparator.getGasOutStream());
    lpGasCompressor.setOutletPressure(55.0, "bara");
    lpGasCompressor.setPolytropicEfficiency(0.76);
    Cooler lpGasCooler = new Cooler("LP gas cooler", lpGasCompressor.getOutletStream());
    lpGasCooler.setOutletTemperature(273.15 + 40.0);

    Mixer recoveredGasMixer = new Mixer("Recovered gas mixer");
    recoveredGasMixer.addStream(hpSeparator.getGasOutStream());
    recoveredGasMixer.addStream(lpGasCooler.getOutletStream());
    Splitter gasAllocation = new Splitter("Gas export and injection allocation", recoveredGasMixer.getOutletStream(),
        2);
    gasAllocation.setSplitFactors(new double[] { 0.15, 0.85 });

    Compressor gasExportCompressor = new Compressor("Gas export compressor", gasAllocation.getSplitStream(0));
    gasExportCompressor.setOutletPressure(180.0, "bara");
    gasExportCompressor.setPolytropicEfficiency(0.78);
    Cooler gasExportCooler = new Cooler("Gas export cooler", gasExportCompressor.getOutletStream());
    gasExportCooler.setOutletTemperature(273.15 + 35.0);

    Compressor injectionStageOne = new Compressor("Gas injection compressor stage 1", gasAllocation.getSplitStream(1));
    injectionStageOne.setOutletPressure(135.0, "bara");
    injectionStageOne.setPolytropicEfficiency(0.78);
    Cooler injectionIntercooler = new Cooler("Gas injection intercooler", injectionStageOne.getOutletStream());
    injectionIntercooler.setOutletTemperature(273.15 + 35.0);
    Compressor injectionStageTwo = new Compressor("Gas injection compressor stage 2",
        injectionIntercooler.getOutletStream());
    injectionStageTwo.setOutletPressure(350.0, "bara");
    injectionStageTwo.setPolytropicEfficiency(0.78);
    Cooler injectionAftercooler = new Cooler("Gas injection aftercooler", injectionStageTwo.getOutletStream());
    injectionAftercooler.setOutletTemperature(273.15 + 35.0);

    ProcessSystem process = new ProcessSystem();
    process.setName(name + " reservoir-to-export process");
    process.add(reservoir);
    process.add(wellstreamMixer);
    process.add(aggregateTubing);
    process.add(productionFlowline);
    process.add(inletChoke);
    process.add(hpSeparator);
    process.add(lpValve);
    process.add(lpSeparator);
    process.add(oilExportPump);
    process.add(lpGasCompressor);
    process.add(lpGasCooler);
    process.add(recoveredGasMixer);
    process.add(gasAllocation);
    process.add(gasExportCompressor);
    process.add(gasExportCooler);
    process.add(injectionStageOne);
    process.add(injectionIntercooler);
    process.add(injectionStageTwo);
    process.add(injectionAftercooler);

    return new FieldLifecycleModel(name, reservoir, process, oilProducer, waterProducer, reservoirGasInjector,
        recoveredGasMixer.getOutletStream(), gasAllocation, oilExportPump.getOutletStream(),
        gasExportCooler.getOutletStream(), injectionAftercooler.getOutletStream());
  }

  /** Creates the compositional PVT model used by the reference case. */
  public static SystemInterface createReservoirFluid() {
    SystemInterface fluid = new SystemPrEos(273.15 + 90.0, 330.0);
    fluid.addComponent("nitrogen", 0.5);
    fluid.addComponent("CO2", 1.5);
    fluid.addComponent("methane", 100.0);
    fluid.addComponent("ethane", 5.0);
    fluid.addComponent("propane", 4.0);
    fluid.addComponent("i-butane", 1.0);
    fluid.addComponent("n-butane", 2.0);
    fluid.addComponent("i-pentane", 1.0);
    fluid.addComponent("n-pentane", 1.5);
    fluid.addComponent("n-hexane", 3.0);
    fluid.addTBPfraction("C7-C10", 8.0, 0.125, 0.78);
    fluid.addTBPfraction("C11-C15", 12.0, 0.180, 0.825);
    fluid.addTBPfraction("C16-C20", 10.0, 0.260, 0.865);
    fluid.addTBPfraction("C21+", 10.0, 0.450, 0.920);
    fluid.addComponent("water", 10.0);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  private static double estimateGasInjectionCapacity() {
    InjectionWellModel injectionWell = new InjectionWellModel(InjectionType.GAS_INJECTOR)
        .setReservoirPressure(330.0, "bara").setReservoirTemperature(90.0, "C").setFormationPermeability(180.0, "mD")
        .setFormationThickness(45.0, "m").setSkinFactor(2.0).setWellDepth(3100.0, "m").setTubingID(0.127, "m")
        .setMaxBHP(390.0, "bara").setFracturePressure(410.0, "bara").setSurfaceInjectionPressure(350.0, "bara");
    return GAS_INJECTORS * injectionWell.calculateMaximumRate().getAchievableRate();
  }

  private static double estimateDevelopmentCapexMusd() {
    WellCostEstimator producerCost = new WellCostEstimator(SubseaCostEstimator.Region.NORWAY);
    producerCost.calculateWellCost("OIL_PRODUCER", "SEMI_SUBMERSIBLE", "SMART_COMPLETION", 3600.0, WATER_DEPTH_M, 75.0,
        35.0, 0.0, true, 5);
    WellCostEstimator injectorCost = new WellCostEstimator(SubseaCostEstimator.Region.NORWAY);
    injectorCost.calculateWellCost("GAS_INJECTOR", "SEMI_SUBMERSIBLE", "STANDARD", 3400.0, WATER_DEPTH_M, 65.0, 25.0,
        0.0, true, 5);
    double wellsMusd = (PRODUCERS * producerCost.getTotalCost() + GAS_INJECTORS * injectorCost.getTotalCost()) / 1.0e6;

    SURFCostEstimator surf = new SURFCostEstimator(PRODUCERS + GAS_INJECTORS, WATER_DEPTH_M,
        SubseaCostEstimator.Region.NORWAY);
    surf.setInfieldFlowlineLengthKm(72.0);
    surf.setInfieldFlowlineDiameterInches(12.0);
    surf.setUmbilicalLengthKm(18.0);
    surf.setRiserLengthM(450.0);
    surf.setNumberOfProductionRisers(2);
    surf.setExportPipelineLengthKm(0.0);
    double surfMusd = surf.calculate() / 1.0e6;

    double fpsoAndTopsidesMusd = 3200.0;
    double gasInjectionAndExportMusd = 650.0;
    double projectAndContingencyMusd = 900.0;
    return wellsMusd + surfMusd + fpsoAndTopsidesMusd + gasInjectionAndExportMusd + projectAndContingencyMusd;
  }
}
