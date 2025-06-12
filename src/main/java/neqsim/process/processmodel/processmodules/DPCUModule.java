package neqsim.process.processmodel.processmodules;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.distillation.Condenser;
import neqsim.process.equipment.distillation.DistillationColumn;
import neqsim.process.equipment.distillation.Reboiler;
import neqsim.process.equipment.expander.Expander;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.equipment.valve.ValveInterface;
import neqsim.process.processmodel.ProcessModuleBaseClass;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * DPCUModule class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class DPCUModule extends ProcessModuleBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ProcessModuleBaseClass.class);

  StreamInterface ethaneOvhComp;
  StreamInterface gasDistColumnExit;
  StreamInterface liquidDistColumnExit;
  StreamInterface feedStream;
  StreamInterface gasExitStream;
  StreamInterface oilExitStream;
  StreamInterface glycolFeedStream;
  StreamInterface glycolExitStream;

  Separator glycolScrubber;
  Separator inletSeparator;
  double inletSepTemperature = 50.00;
  double pressureAfterRedValve = 55.0;
  double gasScrubberTemperature = 30.00;
  double firstStageOutPressure = 110.0;
  double glycolScrubberTemperature = 20.0;
  double secondStageOutPressure = 200.0;
  double glycolInjectionRate = 10.0;
  double exportGasTemperature = 273.15 + 30.0;
  double liquidPumpPressure = 150.0;

  Separator LTseparator;
  HeatExchanger heatExchanger1;
  ThrottlingValve valve1;
  Expander expander;
  Compressor compressor1;
  Mixer mixer;
  DistillationColumn distColumn;

  /**
   * <p>
   * Constructor for DPCUModule.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public DPCUModule(String name) {
    super(name);
  }

  /** {@inheritDoc} */
  @Override
  public void addInputStream(String streamName, StreamInterface stream) {
    if (streamName.equals("feed stream")) {
      this.feedStream = stream;
    } else if (streamName.equals("glycol feed stream")) {
      this.glycolFeedStream = stream;
    } else {
      logger.info("Stream name " + streamName + "not used.");
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
    } else if (streamName.equals("gas from dist column")) {
      return this.gasDistColumnExit;
    } else if (streamName.equals("liquid from dist column")) {
      return this.liquidDistColumnExit;
    } else {
      return null;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void initializeModule() {
    isInitializedModule = true;
    // double inletPressure = feedStream.getPressure();

    ValveInterface inletValve = new ThrottlingValve("inlet valve", feedStream);
    inletValve.setOutletPressure(pressureAfterRedValve);

    heatExchanger1 = new HeatExchanger("heatExchanger1", inletValve.getOutletStream());
    // heatExchanger1.setOutTemperature(273.15 - 18);
    // heatExchanger1.setUAvalue(10000.0);
    // heatExchanger1.addInStream(feedStream2);

    Cooler heatExchanger2 = new Cooler("heatExchanger2", heatExchanger1.getOutStream(0));
    // heatExchanger2.setUAvalue(1000.0);
    heatExchanger1.setOutTemperature(273.15 - 21.0);
    // heatExchanger1.addInStream(feedStream2);

    expander = new Expander("expander", heatExchanger2.getOutletStream());
    expander.setOutletPressure(46.0);

    LTseparator = new Separator("LTseparator", expander.getOutletStream());

    Splitter splitter = new Splitter("LTsplitter", LTseparator.getGasOutStream(), 2);
    splitter.setSplitFactors(new double[] {0.9, 0.1});

    heatExchanger1.addInStream(splitter.getSplitStream(0));

    mixer = new Mixer("gasmixer");
    mixer.addStream(heatExchanger1.getOutStream(1));
    mixer.addStream(splitter.getSplitStream(1));

    compressor1 = new Compressor("Compressor 1", mixer.getOutletStream());
    compressor1.setOutletPressure(65.0);

    Recycle recycl = new Recycle("recycler");
    recycl.addStream(compressor1.getOutletStream());

    valve1 = new ThrottlingValve("valve1", LTseparator.getLiquidOutStream());
    valve1.setOutletPressure(30.0);

    distColumn = new DistillationColumn("distColumn", 10, true, true);
    distColumn.addFeedStream(valve1.getOutletStream(), 2);
    // distColumn.setCondenserTemperature(273.15 - 72.0);
    // distColumn.setReboilerTemperature(273.0+40.0);
    distColumn.getReboiler().setRefluxRatio(10.7);
    distColumn.getCondenser().setRefluxRatio(10.7);

    // heatExchanger2.addInStream(distColumn.getGasOutStream());
    getOperations().add(inletValve);
    getOperations().add(heatExchanger1);
    getOperations().add(heatExchanger2);
    getOperations().add(expander);
    getOperations().add(LTseparator);
    getOperations().add(splitter);
    getOperations().add(mixer);
    getOperations().add(compressor1);
    getOperations().add(recycl);
    getOperations().add(valve1);
    // getOperations().add(distColumn);

    /*
     * Cooler inletCooler = new Cooler("inlet well stream cooler", feedStream);
     * inletCooler.setOutTemperature(inletSepTemperature + 273.15);
     *
     * inletSeparator = new Separator("Inlet separator", inletCooler.getOutStream());
     *
     * Cooler gasCooler = new Cooler("separator gas cooler", inletSeparator.getGasOutStream());
     * gasCooler.setOutTemperature(gasScrubberTemperature + 273.15);
     *
     * oilPump = new Pump("liquid pump", inletSeparator.getLiquidOutStream());
     * oilPump.setOutletPressure(liquidPumpPressure);
     *
     * Separator gasScrubber = new Separator("HC dew point control scrubber",
     * gasCooler.getOutStream());
     *
     * Recycle HPliquidRecycle = new Recycle("Recycle"); double tolerance = 1e-2;
     * HPliquidRecycle.setTolerance(tolerance);
     * HPliquidRecycle.addStream(gasScrubber.getLiquidOutStream());
     * inletSeparator.addStream(HPliquidRecycle.getOutStream());
     *
     * Compressor firstStageCompressor = new Compressor("1st stage compressor",
     * gasScrubber.getGasOutStream());
     * firstStageCompressor.setOutletPressure(firstStageOutPressure);
     *
     * glycolFeedStream.getThermoSystem().setPressure(firstStageOutPressure);
     *
     * Mixer glycolMixer = new Mixer("glycol injection mixer");
     * glycolMixer.addStream(firstStageCompressor.getOutStream());
     * glycolMixer.addStream(glycolFeedStream);
     *
     * Cooler mixerAfterCooler = new Cooler("glycol mixer after cooler",
     * glycolMixer.getOutStream()); mixerAfterCooler.setOutTemperature(glycolScrubberTemperature +
     * 273.15);
     *
     * glycolScrubber = new Separator("Water dew point control scrubber",
     * mixerAfterCooler.getOutStream());
     *
     * secondStageCompressor = new Compressor("2nd stage compressor",
     * glycolScrubber.getGasOutStream());
     * secondStageCompressor.setOutletPressure(secondStageOutPressure);
     *
     * secondStageAfterCooler = new Cooler("second stage after cooler",
     * secondStageCompressor.getOutStream());
     * secondStageAfterCooler.setOutTemperature(exportGasTemperature + 273.15);
     *
     * getOperations().add(inletCooler); getOperations().add(inletSeparator);
     * getOperations().add(gasCooler); getOperations().add(oilPump);
     * getOperations().add(gasScrubber); getOperations().add(HPliquidRecycle);
     * getOperations().add(firstStageCompressor); getOperations().add(glycolMixer);
     * getOperations().add(mixerAfterCooler); getOperations().add(glycolScrubber);
     * getOperations().add(secondStageCompressor); getOperations().add(secondStageAfterCooler);
     */
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    if (!isInitializedModule) {
      initializeModule();
    }
    getOperations().run(id);

    gasExitStream = compressor1.getOutletStream();
    gasExitStream.setCalculationIdentifier(id);

    oilExitStream = LTseparator.getLiquidOutStream();
    oilExitStream.setCalculationIdentifier(id);

    gasDistColumnExit = distColumn.getGasOutStream();
    gasDistColumnExit.setCalculationIdentifier(id);

    liquidDistColumnExit = distColumn.getLiquidOutStream();
    liquidDistColumnExit.setCalculationIdentifier(id);
    setCalculationIdentifier(id);
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
    if (specificationName.equals("pressure after reduction valve")) {
      pressureAfterRedValve = value;
    } else if (specificationName.equals("gas scrubber temperature")) {
      gasScrubberTemperature = value;
    }
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    System.out.println("compressor power " + compressor1.getEnergy());
    System.out.println("expander power " + expander.getEnergy());
    valve1.displayResult();
  }

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos(273.15 + 7.5, 110.0);

    testSystem.addComponent("CO2", 0.0218295567233988);
    testSystem.addComponent("nitrogen", 0.00739237702184805);
    testSystem.addComponent("methane", 0.831767017569769);
    testSystem.addComponent("ethane", 0.0790893243389708);
    testSystem.addComponent("propane", 0.0378546917300062);
    testSystem.addComponent("i-butane", 0.00543253464081659);
    testSystem.addComponent("n-butane", 0.0095144918510181);
    testSystem.addComponent("i-pentane", 0.00207801067169675);
    testSystem.addComponent("n-pentane", 0.00211497210679094);
    testSystem.addComponent("n-hexane", 0.000512675581812218);
    testSystem.addComponent("n-heptane", 0.000205410042894726);
    testSystem.addComponent("n-octane", 0.000677697183000435);

    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);

    Stream feedStream = new Stream("Well stream", testSystem);
    feedStream.getThermoSystem().setTotalFlowRate(5, "MSm^3/hr");

    // feedStream.addAnalogMeasurement("Name", "type");
    // feedStream.addAlarm("type", );..
    DPCUModule dpcuModule = new DPCUModule("dpcuModule");
    dpcuModule.addInputStream("feed stream", feedStream);
    dpcuModule.setSpecification("pressure after reduction valve", 108.0);

    neqsim.process.processmodel.ProcessSystem operations =
        new neqsim.process.processmodel.ProcessSystem();
    operations.add(feedStream);
    operations.add(dpcuModule);
    operations.run();

    dpcuModule.getOutputStream("gas exit stream").displayResult();
    dpcuModule.getOutputStream("oil exit stream").displayResult();
    dpcuModule.getOutputStream("gas from dist column").displayResult();
    dpcuModule.getOutputStream("liquid from dist column").displayResult();

    dpcuModule.displayResult();
    // dpcuModule.getOutputStream("gasmixer").displayResult();
  }
}
