package neqsim.processSimulation.processSystem.processModules;

import neqsim.processSimulation.processEquipment.heatExchanger.Cooler;
import neqsim.processSimulation.processEquipment.heatExchanger.Heater;
import neqsim.processSimulation.processEquipment.mixer.Mixer;
import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.separator.ThreePhaseSeparator;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.processSimulation.processEquipment.valve.ThrottlingValve;
import neqsim.processSimulation.processSystem.ProcessModuleBaseClass;

/**
 * <p>
 * WellFluidModule class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class WellFluidModule extends ProcessModuleBaseClass {
  private static final long serialVersionUID = 1000;

  protected StreamInterface feedStream = null, outStream = null;
  // ThreePhaseSeparator thirdStageSeparator = null;
  Cooler oilCooler;
  double secondstagePressure = 15.00; // bar'
  double inletPressure = 55.0, gasfactor = 0.1;
  double thirdstagePressure = 1.01325;
  double separationTemperature = 273.15 + 15;
  double exitGasScrubberTemperature = 273.15 + 30;
  double firstStageCompressorAfterCoolerTemperature = 273.15 + 30;
  double exportOilTemperature = 273.15 + 30;

  public WellFluidModule(String name) {
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
    return this.outStream;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    if (!isInitializedModule) {
      initializeModule();
    }
    getOperations().run();

    double volGas = ((Mixer) getOperations().getUnit("gas mixer")).getOutletStream()
        .getThermoSystem().getVolume();
    double volOil = ((ThreePhaseSeparator) getOperations().getUnit("3rd stage Separator"))
        .getOilOutStream().getThermoSystem().getVolume();

    double GOR = volGas / volOil;
    System.out.println("GOR " + GOR);
    outStream = ((Mixer) getOperations().getUnit("well mixer")).getOutletStream();

    // ((Heater) getOperations().getUnit("gas heater")).displayResult();

    Stream gasStream =
        (Stream) ((Heater) getOperations().getUnit("gas heater")).getOutletStream().clone();
    gasStream.getThermoSystem().setPressure(inletPressure);
    Stream oilStream =
        (Stream) ((ThreePhaseSeparator) getOperations().getUnit("3rd stage Separator"))
            .getOilOutStream().clone();
    oilStream.getThermoSystem().setPressure(inletPressure);

    ((Separator) getOperations().getUnit("Inlet separator")).addStream(gasStream);
    ((Separator) getOperations().getUnit("Inlet separator")).addStream(oilStream);
    getOperations().run();

    volGas = ((Mixer) getOperations().getUnit("gas mixer")).getOutletStream().getThermoSystem()
        .getVolume();
    volOil = ((ThreePhaseSeparator) getOperations().getUnit("3rd stage Separator"))
        .getOilOutStream().getThermoSystem().getVolume();

    GOR = volGas / volOil;
    System.out.println("GOR " + GOR);
    outStream = ((Mixer) getOperations().getUnit("well mixer")).getOutletStream();
  }

  /** {@inheritDoc} */
  @Override
  public void initializeModule() {
    isInitializedModule = true;
    inletPressure = feedStream.getPressure();
    Separator inletSeparator = new Separator("Inlet separator", feedStream);

    Heater liquidOutHeater = new Heater("oil/water heater", inletSeparator.getLiquidOutStream());
    liquidOutHeater.setOutTemperature(separationTemperature);

    ThreePhaseSeparator firstStageSeparator =
        new ThreePhaseSeparator("1st stage separator", liquidOutHeater.getOutletStream());

    ThrottlingValve valve1 =
        new ThrottlingValve("1stTo2ndStageOilValve", firstStageSeparator.getOilOutStream());
    valve1.setOutletPressure(secondstagePressure);

    Heater liquidOutHeater2 = new Heater("oil/water heater2", valve1.getOutletStream());
    liquidOutHeater2.setOutTemperature(separationTemperature);

    ThreePhaseSeparator secondStageSeparator =
        new ThreePhaseSeparator("2nd stage Separator", liquidOutHeater2.getOutletStream());

    ThrottlingValve thirdStageValve =
        new ThrottlingValve("2-3stageOilValve", secondStageSeparator.getLiquidOutStream());
    thirdStageValve.setOutletPressure(thirdstagePressure);

    Heater liquidOutHeater3 = new Heater("oil/water heater3", thirdStageValve.getOutletStream());
    liquidOutHeater3.setOutTemperature(separationTemperature);

    ThreePhaseSeparator thirdStageSeparator =
        new ThreePhaseSeparator("3rd stage Separator", liquidOutHeater3.getOutletStream());

    Mixer gasMixer = new Mixer("gas mixer");

    gasMixer.addStream(inletSeparator.getGasOutStream());
    gasMixer.addStream(firstStageSeparator.getGasOutStream());
    gasMixer.addStream(secondStageSeparator.getGasOutStream());
    gasMixer.addStream(thirdStageSeparator.getGasOutStream());

    Heater gasHeater = new Heater("gas heater", gasMixer.getOutletStream());
    gasHeater.setOutTemperature(separationTemperature);

    Mixer wellStreamMixer = new Mixer("well mixer");
    wellStreamMixer.addStream(thirdStageSeparator.getOilOutStream());
    wellStreamMixer.addStream(gasHeater.getOutletStream());

    getOperations().add(inletSeparator);
    getOperations().add(liquidOutHeater);
    getOperations().add(firstStageSeparator);
    getOperations().add(valve1);
    getOperations().add(liquidOutHeater2);
    getOperations().add(secondStageSeparator);
    getOperations().add(thirdStageValve);
    getOperations().add(liquidOutHeater3);
    getOperations().add(thirdStageSeparator);
    getOperations().add(gasMixer);
    getOperations().add(gasHeater);
    getOperations().add(wellStreamMixer);

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
    if (specificationName.equals("separation temperature")) {
      separationTemperature = value;
    }
    if (specificationName.equals("Third stage pressure")) {
      thirdstagePressure = value;
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
  public static void main(String[] args) {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos(273.15 + 50, 65);

    // testSystem.addComponent("CO2", 1);
    // testSystem.addComponent("nitrogen", 1);
    testSystem.addComponent("methane", 195);
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

    WellFluidModule separationModule = new WellFluidModule("separationModule");
    separationModule.addInputStream("feed stream", wellStream);
    separationModule.setSpecification("Second stage pressure", 15.0);
    separationModule.setSpecification("separation temperature", 273.15 + 15.0);
    separationModule.setSpecification("Third stage pressure", 1.01325);

    neqsim.processSimulation.processSystem.ProcessSystem operations =
        new neqsim.processSimulation.processSystem.ProcessSystem();

    operations.add(wellStream);
    operations.add(separationModule);
    // separationModule.getUnit("")
    // ((Recycle) operations.getUnit("Resycle")).setTolerance(1e-9);

    operations.run();
  }
}
