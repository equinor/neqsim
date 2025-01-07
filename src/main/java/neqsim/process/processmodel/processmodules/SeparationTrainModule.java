package neqsim.process.processmodel.processmodules;

import java.util.UUID;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessModuleBaseClass;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * SeparationTrainModule class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class SeparationTrainModule extends ProcessModuleBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  protected StreamInterface feedStream = null;

  protected StreamInterface gasExitStream = null;

  protected StreamInterface oilExitStream = null;

  // ThreePhaseSeparator thirdStageSeparator = null;
  Separator gasInletScrubber = null;
  Cooler oilCooler;
  double secondstagePressure = 15.00; // bar'
  double thirdstagePressure = 1.50; // bar
  double heatedOilTemperature = 273.15 + 50;
  double exitGasScrubberTemperature = 273.15 + 30;
  double firstStageCompressorAfterCoolerTemperature = 273.15 + 30;
  double exportOilTemperature = 273.15 + 30;

  /**
   * <p>
   * Constructor for SeparationTrainModule.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public SeparationTrainModule(String name) {
    super(name);
  }

  /** {@inheritDoc} */
  @Override
  public void addInputStream(String streamName, StreamInterface stream) {
    if (streamName.equals("feed stream")) {
      this.feedStream = stream;
    }
  }

  /** {@inheritDoc} */
  @Override
  public StreamInterface getOutputStream(String streamName) {
    if (!isInitializedStreams) {
      initializeStreams();
    }
    if (streamName.equals("gas exit stream")) {
      return this.gasExitStream;
    } else if (streamName.equals("oil exit stream")) {
      return this.oilExitStream;
    } else {
      return null;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    if (!isInitializedModule) {
      initializeModule();
    }
    getOperations().run(id);

    gasExitStream = gasInletScrubber.getGasOutStream();
    gasExitStream.setCalculationIdentifier(id);
    oilExitStream = oilCooler.getOutletStream();
    oilExitStream.setCalculationIdentifier(id);

    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  public void initializeModule() {
    isInitializedModule = true;
    double inletPressure = feedStream.getPressure();
    Separator inletSeparator = new Separator("Inlet separator", feedStream);

    Heater liquidOutHeater = new Heater("oil/water heater", inletSeparator.getLiquidOutStream());
    liquidOutHeater.setOutTemperature(heatedOilTemperature);

    ThreePhaseSeparator firstStageSeparator =
        new ThreePhaseSeparator("1st stage separator", liquidOutHeater.getOutletStream());

    ThrottlingValve valve1 =
        new ThrottlingValve("1stTo2ndStageOilValve", firstStageSeparator.getOilOutStream());
    valve1.setOutletPressure(secondstagePressure);

    ThreePhaseSeparator secondStageSeparator =
        new ThreePhaseSeparator("2nd stage Separator", valve1.getOutletStream());

    ThrottlingValve thirdStageValve =
        new ThrottlingValve("2-3stageOilValve", secondStageSeparator.getLiquidOutStream());
    thirdStageValve.setOutletPressure(thirdstagePressure);

    ThreePhaseSeparator thirdStageSeparator =
        new ThreePhaseSeparator("3rd stage Separator", thirdStageValve.getOutletStream());

    oilCooler = new Cooler("export oil cooler", thirdStageSeparator.getLiquidOutStream());
    oilCooler.setOutTemperature(exportOilTemperature);

    Compressor thirdStageCompressor =
        new Compressor("3rd stage recompressor", thirdStageSeparator.getGasOutStream());
    thirdStageCompressor.setOutletPressure(secondstagePressure);

    Cooler thirdSstageCoooler =
        new Cooler("3rd stage cooler", thirdStageCompressor.getOutletStream());
    thirdSstageCoooler.setOutTemperature(firstStageCompressorAfterCoolerTemperature);

    Mixer thirdStageMixer = new Mixer("1st and 2nd stage gas mixer");
    thirdStageMixer.addStream(thirdSstageCoooler.getOutletStream());
    thirdStageMixer.addStream(secondStageSeparator.getGasOutStream());

    Separator thirdStageScrubber =
        new Separator("recompression scrubber", thirdStageMixer.getOutletStream());
    secondStageSeparator.addStream(thirdStageScrubber.getLiquidOutStream());

    Compressor secondStageCompressor =
        new Compressor("2nd stage recompressor", thirdStageScrubber.getGasOutStream());
    secondStageCompressor.setOutletPressure(inletPressure);

    Mixer HPgasMixer = new Mixer("HPgas mixer");
    HPgasMixer.addStream(firstStageSeparator.getGasOutStream());
    HPgasMixer.addStream(secondStageCompressor.getOutletStream());
    HPgasMixer.addStream(inletSeparator.getGasOutStream());

    Cooler inletGasCooler = new Cooler("HP gas cooler", HPgasMixer.getOutletStream());
    inletGasCooler.setOutTemperature(exitGasScrubberTemperature);

    gasInletScrubber = new Separator("HP gas scrubber", inletGasCooler.getOutletStream());

    Recycle HPliquidRecycle = new Recycle("Recycle");
    double tolerance = 1e-10;
    HPliquidRecycle.setTolerance(tolerance);
    HPliquidRecycle.addStream(gasInletScrubber.getLiquidOutStream());
    inletSeparator.addStream(HPliquidRecycle.getOutletStream());

    getOperations().add(inletSeparator);
    getOperations().add(liquidOutHeater);
    getOperations().add(firstStageSeparator);
    getOperations().add(valve1);
    getOperations().add(secondStageSeparator);
    getOperations().add(thirdStageValve);
    getOperations().add(thirdStageSeparator);
    getOperations().add(thirdStageCompressor);
    getOperations().add(thirdStageMixer);
    getOperations().add(thirdSstageCoooler);
    getOperations().add(thirdStageScrubber);
    getOperations().add(HPliquidRecycle);
    getOperations().add(secondStageCompressor);
    getOperations().add(oilCooler);
    getOperations().add(HPgasMixer);
    getOperations().add(inletGasCooler);
    getOperations().add(gasInletScrubber);

    // gasExitStream = gasInletScrubber.getGasOutStream();
    // oilExitStream = thirdStageSeparator.getOilOutStream();
  }

  /** {@inheritDoc} */
  @Override
  public void initializeStreams() {
    isInitializedStreams = true;
  }

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    // design is done here
  }

  /** {@inheritDoc} */
  @Override
  public void setDesign() {
    // set design is done here
  }

  /** {@inheritDoc} */
  @Override
  public void setSpecification(String specificationName, double value) {
    if (specificationName.equals("Second stage pressure")) {
      secondstagePressure = value;
    }
    if (specificationName.equals("heated oil temperature")) {
      heatedOilTemperature = value;
    }
    if (specificationName.equals("Third stage pressure")) {
      thirdstagePressure = value;
    }
    if (specificationName.equals("Gas exit temperature")) {
      exitGasScrubberTemperature = value;
    }
    if (specificationName.equals("First stage compressor after cooler temperature")) {
      firstStageCompressorAfterCoolerTemperature = value;
    }
    if (specificationName.equals("Export oil temperature")) {
      exportOilTemperature = value;
    }
  }

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @SuppressWarnings("unused")
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos(273.15 + 50, 65);

    // testSystem.addComponent("CO2", 1);
    // testSystem.addComponent("nitrogen", 1);
    testSystem.addComponent("methane", 95);
    // testSystem.addComponent("ethane", 1);
    // testSystem.addTBPfraction("C7", 1.0, 187.0 / 1000.0, 0.84738);

    // testSystem.addComponent("propane", 5);
    // testSystem.addComponent("n-octane", 2);
    testSystem.addComponent("nC10", 6);
    // testSystem.setHeavyTBPfractionAsPlusFraction();
    // testSystem.getCharacterization().characterisePlusFraction();

    testSystem.addComponent("water", 12);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    testSystem.setMultiPhaseCheck(true);
    testSystem.init(0);
    testSystem.init(3);
    double a = testSystem.getTotalNumberOfMoles();

    Stream wellStream = new Stream("Well stream", testSystem);
    // wellStream.getThermoSystem().setTotalFlowRate(5.0, "MSm^3/day");

    SeparationTrainModule separationModule = new SeparationTrainModule("separationModule");
    separationModule.addInputStream("feed stream", wellStream);
    separationModule.setSpecification("Second stage pressure", 15.0);
    separationModule.setSpecification("heated oil temperature", 273.15 + 55.0);
    separationModule.setSpecification("Third stage pressure", 1.0);
    separationModule.setSpecification("Gas exit temperature", 273.15 + 25.0);
    separationModule.setSpecification("First stage compressor after cooler temperature",
        273.15 + 25.0);
    separationModule.setSpecification("Export oil temperature", 273.15 + 25.0);

    neqsim.process.processmodel.ProcessSystem operations =
        new neqsim.process.processmodel.ProcessSystem();

    operations.add(wellStream);
    operations.add(separationModule);
    // separationModule.getUnit("")
    // ((Recycle) operations.getUnit("Recycle")).setTolerance(1e-9);

    operations.run();

    // ArrayList names2 = operations.getAllUnitNames();
    // processSimulation.processEquipment.ProcessEquipmentInterface tempStr =
    // (ProcessEquipmentBaseClass) operations.getUnit("2nd stage recompressor");
    // tempStr.displayResult();
    // wellStream.displayResult();
    ((ProcessEquipmentInterface) separationModule.getOperations().getUnit("Inlet separator"))
        .getMechanicalDesign().calcDesign();
    ((ProcessEquipmentInterface) separationModule.getOperations().getUnit("Inlet separator"))
        .getMechanicalDesign().displayResults();

    ((ProcessEquipmentInterface) separationModule.getOperations().getUnit("1st stage separator"))
        .getMechanicalDesign().calcDesign();
    ((ProcessEquipmentInterface) separationModule.getOperations().getUnit("1st stage separator"))
        .getMechanicalDesign().displayResults();

    ((ProcessEquipmentInterface) separationModule.getOperations().getUnit("2nd stage Separator"))
        .getMechanicalDesign().calcDesign();
    ((ProcessEquipmentInterface) separationModule.getOperations().getUnit("2nd stage Separator"))
        .getMechanicalDesign().displayResults();

    ((ProcessEquipmentInterface) separationModule.getOperations().getUnit("3rd stage Separator"))
        .getMechanicalDesign().calcDesign();
    ((ProcessEquipmentInterface) separationModule.getOperations().getUnit("3rd stage Separator"))
        .getMechanicalDesign().displayResults();

    ((ProcessEquipmentInterface) separationModule.getOperations().getUnit("2nd stage recompressor"))
        .getMechanicalDesign().calcDesign();
    ((ProcessEquipmentInterface) separationModule.getOperations().getUnit("2nd stage recompressor"))
        .getMechanicalDesign().displayResults();

    // operations.getSystemMechanicalDesign().runDesignCalculation();
    // operations.getSystemMechanicalDesign().getTotalPlotSpace();
    // System.out.println("Modules " + operations.getSystemMechanicalDesign().getTotalVolume());

    // System.out
    // .println("Modules " + operations.getSystemMechanicalDesign().getTotalNumberOfModules());
    // System.out.println("Weight " + operations.getSystemMechanicalDesign().getTotalWeight());
    // System.out.println("Plot space " +
    // operations.getSystemMechanicalDesign().getTotalPlotSpace());
    // System.out.println("CAPEX " + operations.getCostEstimator().getWeightBasedCAPEXEstimate());
    // System.out.println("CAPEX " + operations.getCostEstimator().getCAPEXestimate());

    /*
     * separationModule.getOutputStream("Inlet separator").displayResult();
     * separationModule.getOutputStream("oil exit stream").displayResult();
     * System.out.println("third stage compressor power " + ((Compressor)
     * separationModule.getOperations().getUnit("3rd stage recompressor")).getPower( ) + " W");
     * System.out.println("secondstage compressor  power " + ((Compressor)
     * separationModule.getOperations().getUnit("2nd stage recompressor")).getPower( ) + " W");
     * System.out.println("third stage cooler duty " + ((Cooler)
     * separationModule.getOperations().getUnit("3rd stage cooler")).getEnergyInput( ) + " W");
     * System.out.println("HP gas cooler duty " + ((Cooler)
     * separationModule.getOperations().getUnit("HP gas cooler")).getEnergyInput() + " W");
     * System.out.println("Export oil flow " +
     * separationModule.getOutputStream("oil exit stream").getThermoSystem().
     * getTotalNumberOfMoles() *
     * separationModule.getOutputStream("oil exit stream").getThermoSystem(). getMolarMass() /
     * separationModule.getOutputStream("oil exit stream").getThermoSystem().
     * getPhase(0).getPhysicalProperties().getDensity() * 3600.0 + " m^3/hr");
     * System.out.println("Export gas flow " +
     * separationModule.getOutputStream("gas exit stream").getThermoSystem().
     * getTotalNumberOfMoles() *
     * separationModule.getOutputStream("gas exit stream").getThermoSystem(). getMolarMass() /
     * separationModule.getOutputStream("gas exit stream").getThermoSystem().
     * getPhase(0).getPhysicalProperties().getDensity() * 3600.0 + " m^3/hr");
     * System.out.println("Export gas flow " +
     * separationModule.getOutputStream("gas exit stream").getThermoSystem().
     * getTotalNumberOfMoles() * ThermodynamicConstantsInterface.R * (273.15 + 15.0)
     * /ThermodynamicConstantsInterface.atm * 3600.0 * 24 / 1.0e6 + " MSm^3/day");
     * System.out.println("oil/water heater duty " + ((Heater)
     * separationModule.getOperations().getUnit("oil/water heater")).getEnergyInput( ) + " W");
     * System.out.println("Export oil cooler duty " + ((Cooler)
     * separationModule.getOperations().getUnit("export oil cooler")).getEnergyInput () + " W");
     */
  }
}
