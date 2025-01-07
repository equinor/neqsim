package neqsim.process.equipment.distillation;

import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * DistillationColumn class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class DistillationColumn extends ProcessEquipmentBaseClass implements DistillationInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(DistillationColumn.class);

  private boolean doInitializion = true;
  boolean hasReboiler = false;
  boolean hasCondenser = false;
  protected ArrayList<SimpleTray> trays = new ArrayList<SimpleTray>(0);
  double condenserCoolingDuty = 10.0;
  private double reboilerTemperature = 273.15;
  private double condenserTemperature = 270.15;
  double topTrayPressure = -1.0;
  double bottomTrayPressure = -1.0;
  int numberOfTrays = 1;
  int maxNumberOfIterations = 10;
  private int feedTrayNumber = 1;
  StreamInterface stream_3 = new Stream("stream_3");
  StreamInterface gasOutStream = new Stream("gasOutStream");
  StreamInterface liquidOutStream = new Stream("liquidOutStream");
  StreamInterface feedStream = null;
  boolean stream_3isset = false;
  private double internalDiameter = 1.0;
  neqsim.process.processmodel.ProcessSystem distoperations;
  Heater heater;
  Separator separator2;
  private double err = 1.0e10;

  /**
   * <p>
   * Constructor for DistillationColumn.
   * </p>
   *
   * @param name Name of distillation column
   * @param numberOfTraysLocal Number of SimpleTrays to add
   * @param hasReboiler Set true to add reboiler
   * @param hasCondenser Set true to add Condenser
   */
  public DistillationColumn(String name, int numberOfTraysLocal, boolean hasReboiler,
      boolean hasCondenser) {
    super(name);
    this.hasReboiler = hasReboiler;
    this.hasCondenser = hasCondenser;
    distoperations = new neqsim.process.processmodel.ProcessSystem();
    this.numberOfTrays = numberOfTraysLocal;
    if (hasReboiler) {
      trays.add(new Reboiler("Reboiler"));
      this.numberOfTrays++;
    }
    for (int i = 0; i < numberOfTraysLocal; i++) {
      trays.add(new SimpleTray("SimpleTray" + i + 1));
    }
    if (hasCondenser) {
      trays.add(new Condenser("Condenser"));
      this.numberOfTrays++;
    }
    for (int i = 0; i < this.numberOfTrays; i++) {
      distoperations.add(trays.get(i));
    }
  }

  /**
   * <p>
   * addFeedStream.
   * </p>
   *
   * @param inputStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   * @param feedTrayNumber a int
   */
  public void addFeedStream(StreamInterface inputStream, int feedTrayNumber) {
    feedStream = inputStream;
    getTray(feedTrayNumber).addStream(inputStream);
    setFeedTrayNumber(feedTrayNumber);
    double moles = inputStream.getThermoSystem().getTotalNumberOfMoles();
    gasOutStream.setThermoSystem(inputStream.getThermoSystem().clone());
    gasOutStream.getThermoSystem().setTotalNumberOfMoles(moles / 2.0);
    liquidOutStream.setThermoSystem(inputStream.getThermoSystem().clone());
    liquidOutStream.getThermoSystem().setTotalNumberOfMoles(moles / 2.0);
  }

  /**
   * <p>
   * init.
   * </p>
   */
  public void init() {
    if (!isDoInitializion()) {
      return;
    }
    setDoInitializion(false);
    trays.get(feedTrayNumber).run();
    if (getTray(feedTrayNumber).getFluid().getNumberOfPhases() == 1) {
      for (int i = 0; i < numberOfTrays; i++) {
        if (getTray(i).getNumberOfInputStreams() > 0 && i != feedTrayNumber) {
          getTray(feedTrayNumber).addStream(trays.get(i).getStream(0));
          getTray(feedTrayNumber).run();
          getTray(feedTrayNumber)
              .removeInputStream(getTray(feedTrayNumber).getNumberOfInputStreams() - 1);
          if (getTray(feedTrayNumber).getThermoSystem().getNumberOfPhases() > 1) {
            break;
          }
        } else if (i == feedTrayNumber && getTray(i).getNumberOfInputStreams() > 1) {
          getTray(feedTrayNumber).addStream(trays.get(i).getStream(1));
          trays.get(feedTrayNumber).run();
          getTray(feedTrayNumber)
              .removeInputStream(getTray(feedTrayNumber).getNumberOfInputStreams() - 1);
          if (getTray(feedTrayNumber).getThermoSystem().getNumberOfPhases() > 1) {
            break;
          }
        }
      }
    }

    if (getTray(feedTrayNumber).getFluid().getNumberOfPhases() == 1) {
      getTray(feedTrayNumber).getThermoSystem().init(0);
      getTray(feedTrayNumber).getThermoSystem().init(3);
    }

    // ((Mixer) trays.get(numberOfTrays - 1)).getStream(0).getThermoSystem()
    // .setTotalNumberOfMoles(((Mixer) trays.get(numberOfTrays -
    // 1)).getStream(0).getThermoSystem()
    // .getTotalNumberOfMoles() * (1.0));
    trays.get(0).addStream(trays.get(feedTrayNumber).getLiquidOutStream().clone());
    // ((Mixer)
    // trays.get(0)).getStream(streamNumbReboil).getThermoSystem().setTotalNumberOfMoles(
    // ((Mixer)
    // trays.get(0)).getStream(streamNumbReboil).getThermoSystem().getTotalNumberOfMoles()
    // * (1.0));

    // ((Runnable) trays.get(numberOfTrays - 1)).run();
    trays.get(0).run();

    double feedTrayTemperature = getTray(getFeedTrayNumber()).getTemperature();

    if (trays.get(numberOfTrays - 1).getNumberOfInputStreams() > 0) {
      condenserTemperature = trays.get(numberOfTrays - 1).getThermoSystem().getTemperature();
    } else {
      condenserTemperature = feedTrayTemperature - 1.0;
    }

    reboilerTemperature = trays.get(0).getThermoSystem().getTemperature();

    double deltaTempCondenser =
        (feedTrayTemperature - condenserTemperature) / (numberOfTrays * 1.0 - feedTrayNumber - 1);
    double deltaTempReboiler = (reboilerTemperature - feedTrayTemperature) / (feedTrayNumber * 1.0);

    double delta = 0;
    for (int i = feedTrayNumber + 1; i < numberOfTrays; i++) {
      delta += deltaTempCondenser;
      trays.get(i)
          .setTemperature(getTray(getFeedTrayNumber()).getThermoSystem().getTemperature() - delta);
    }
    delta = 0;
    for (int i = feedTrayNumber - 1; i >= 0; i--) {
      delta += deltaTempReboiler;
      trays.get(i)
          .setTemperature(getTray(getFeedTrayNumber()).getThermoSystem().getTemperature() + delta);
    }

    for (int i = 1; i < numberOfTrays; i++) {
      trays.get(i).addStream(trays.get(i - 1).getGasOutStream());
      trays.get(i).init();
      trays.get(i).run();
    }

    for (int i = numberOfTrays - 2; i >= 1; i--) {
      trays.get(i).addStream(trays.get(i + 1).getLiquidOutStream());
      trays.get(i).init();
      trays.get(i).run();
    }
    int streamNumb = (trays.get(0)).getNumberOfInputStreams() - 1;
    trays.get(0).replaceStream(streamNumb, trays.get(1).getLiquidOutStream());
    trays.get(0).init();
    trays.get(0).run();

    // massBalanceCheck();
  }

  /**
   * <p>
   * Getter for the field <code>gasOutStream</code>.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public StreamInterface getGasOutStream() {
    return gasOutStream;
  }

  /**
   * <p>
   * Getter for the field <code>liquidOutStream</code>.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public StreamInterface getLiquidOutStream() {
    return liquidOutStream;
  }

  /**
   * <p>
   * getTray.
   * </p>
   *
   * @param trayNumber a int
   * @return a {@link neqsim.process.equipment.distillation.SimpleTray} object
   */
  public SimpleTray getTray(int trayNumber) {
    return trays.get(trayNumber);
  }

  /** {@inheritDoc} */
  @Override
  public void setNumberOfTrays(int number) {
    int oldNumberOfTrays = numberOfTrays;

    int tempNumberOfTrays = number;

    if (hasReboiler) {
      tempNumberOfTrays++;
    }
    if (hasCondenser) {
      tempNumberOfTrays++;
    }

    int change = tempNumberOfTrays - oldNumberOfTrays;

    if (change > 0) {
      for (int i = 0; i < change; i++) {
        trays.add(1, new SimpleTray("SimpleTray" + oldNumberOfTrays + i + 1));
      }
    } else if (change < 0) {
      for (int i = 0; i > change; i--) {
        trays.remove(1);
      }
    }
    numberOfTrays = tempNumberOfTrays;

    setDoInitializion(true);
    init();
  }

  /**
   * <p>
   * setTopCondenserDuty.
   * </p>
   *
   * @param duty a double
   */
  public void setTopCondenserDuty(double duty) {
    condenserCoolingDuty = duty;
  }

  /**
   * <p>
   * setTopPressure.
   * </p>
   *
   * @param topPressure a double
   */
  public void setTopPressure(double topPressure) {
    topTrayPressure = topPressure;
  }

  /**
   * <p>
   * setBottomPressure.
   * </p>
   *
   * @param bottomPressure a double
   */
  public void setBottomPressure(double bottomPressure) {
    bottomTrayPressure = bottomPressure;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    if (bottomTrayPressure < 0) {
      bottomTrayPressure = getTray(feedTrayNumber).getStream(0).getPressure();
    }
    if (topTrayPressure < 0) {
      topTrayPressure = getTray(feedTrayNumber).getStream(0).getPressure();
    }

    double dp = 0.0;
    if (numberOfTrays > 1) {
      dp = (bottomTrayPressure - topTrayPressure) / (numberOfTrays - 1.0);
    }
    for (int i = 0; i < numberOfTrays; i++) {
      trays.get(i).setPressure(bottomTrayPressure - i * dp);
    }
    SystemInterface inpS = feedStream.getThermoSystem().clone();
    getTray(feedTrayNumber).getStream(0).setThermoSystem(inpS);

    if (numberOfTrays == 1) {
      trays.get(0).run(id);
      gasOutStream.setThermoSystem(trays.get(0).getGasOutStream().getThermoSystem().clone());
      liquidOutStream.setThermoSystem(trays.get(0).getLiquidOutStream().getThermoSystem().clone());
    } else {
      if (isDoInitializion()) {
        this.init();
      }
      err = 1.0e10;
      double errOld;
      int iter = 0;
      double[] oldtemps = new double[numberOfTrays];
      trays.get(feedTrayNumber).run(id);

      do {
        iter++;
        errOld = err;
        err = 0.0;
        for (int i = 0; i < numberOfTrays; i++) {
          oldtemps[i] = trays.get(i).getThermoSystem().getTemperature();
        }

        for (int i = feedTrayNumber; i > 1; i--) {
          int replaceStream1 = trays.get(i - 1).getNumberOfInputStreams() - 1;
          trays.get(i - 1).replaceStream(replaceStream1, trays.get(i).getLiquidOutStream());
          trays.get(i - 1).setPressure(bottomTrayPressure - (i - 1) * dp);
          trays.get(i - 1).run(id);
        }
        int streamNumb = trays.get(0).getNumberOfInputStreams() - 1;
        trays.get(0).setPressure(bottomTrayPressure);
        trays.get(0).replaceStream(streamNumb, trays.get(1).getLiquidOutStream());
        trays.get(0).run(id);

        for (int i = 1; i <= numberOfTrays - 1; i++) {
          int replaceStream = trays.get(i).getNumberOfInputStreams() - 2;
          if (i == (numberOfTrays - 1)) {
            replaceStream = trays.get(i).getNumberOfInputStreams() - 1;
          }
          trays.get(i).replaceStream(replaceStream, trays.get(i - 1).getGasOutStream());
          trays.get(i).run(id);
        }

        for (int i = numberOfTrays - 2; i >= feedTrayNumber; i--) {
          int replaceStream = trays.get(i).getNumberOfInputStreams() - 1;
          trays.get(i).replaceStream(replaceStream, trays.get(i + 1).getLiquidOutStream());
          trays.get(i).run(id);
        }
        for (int i = 0; i < numberOfTrays; i++) {
          err += Math.abs(oldtemps[i] - trays.get(i).getThermoSystem().getTemperature());
        }
        logger.info("error iter " + err + " iteration " + iter);
        // System.out.println("error iter " + err + " iteration " + iter);
        // massBalanceCheck();
      } while (err > 1e-4 && err < errOld && iter < maxNumberOfIterations); // &&
      // !massBalanceCheck());
      // massBalanceCheck();
      // componentMassBalanceCheck("water");
      gasOutStream.setThermoSystem(
          trays.get(numberOfTrays - 1).getGasOutStream().getThermoSystem().clone());
      gasOutStream.setCalculationIdentifier(id);
      liquidOutStream.setThermoSystem(trays.get(0).getLiquidOutStream().getThermoSystem().clone());
      liquidOutStream.setCalculationIdentifier(id);

      for (int i = 0; i < numberOfTrays; i++) {
        // TODO: set calculation ids of child elements of trays
        trays.get(i).setCalculationIdentifier(id);
      }
    }
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    distoperations.displayResult();
  }

  /**
   * <p>
   * component componentMassBalanceCheck.
   * </p>
   *
   * @param componentName component name to heck mass balance for
   * @return a boolean
   */
  public boolean componentMassBalanceCheck(String componentName) {
    double[] massInput = new double[numberOfTrays];
    double[] massOutput = new double[numberOfTrays];
    double[] massBalance = new double[numberOfTrays];
    double[] massFeedInput = new double[numberOfTrays];
    for (int i = 0; i < numberOfTrays; i++) {
      int numberOfInputStreams = trays.get(i).getNumberOfInputStreams() - 2;
      if (i == 0 || i == numberOfTrays - 1) {
        numberOfInputStreams = trays.get(i).getNumberOfInputStreams() - 1;
      }

      for (int j = 0; j < numberOfInputStreams; j++) {
        for (int k = 0; k < trays.get(i).getStream(j).getFluid().getNumberOfPhases(); k++) {
          massFeedInput[i] += trays.get(i).getStream(j).getFluid().getPhase(k)
              .getComponent(componentName).getFlowRate("kg/hr");
        }
      }
    }

    for (int i = 0; i < numberOfTrays; i++) {
      int numberOfInputStreams = trays.get(i).getNumberOfInputStreams();
      for (int j = 0; j < numberOfInputStreams; j++) {
        for (int k = 0; k < trays.get(i).getStream(j).getFluid().getNumberOfPhases(); k++) {
          massInput[i] += trays.get(i).getStream(j).getFluid().getPhase(k)
              .getComponent(componentName).getFlowRate("kg/hr");
        }
      }
      massOutput[i] += trays.get(i).getGasOutStream().getFluid().getComponent(componentName)
          .getFlowRate("kg/hr");
      massOutput[i] += trays.get(i).getLiquidOutStream().getFluid().getComponent(componentName)
          .getFlowRate("kg/hr");
      massBalance[i] = massInput[i] - massOutput[i];

      System.out.println("component " + componentName + " tray " + i + " number of input streams "
          + numberOfInputStreams + " massinput " + massInput[i] + " massoutput " + massOutput[i]
          + " massbalance " + massBalance[i] + " gasout "
          + trays.get(i).getGasOutStream().getFluid().getComponent("water").getFlowRate("kg/hr")
          + " liquidout "
          + trays.get(i).getLiquidOutStream().getFluid().getComponent("water").getFlowRate("kg/hr")
          + " pressure " + trays.get(i).getGasOutStream().getPressure() + " temperature "
          + trays.get(i).getGasOutStream().getTemperature("C") + " feedtotray " + massFeedInput[i]);
    }

    double massError = 0.0;
    for (int i = 0; i < numberOfTrays; i++) {
      massError += Math.abs(massBalance[i]);
    }
    if (massError > 1e-6) {
      return false;
    } else {
      return true;
    }
  }

  /**
   * <p>
   * massBalanceCheck.
   * </p>
   *
   * @return a boolean
   */
  public boolean massBalanceCheck() {
    double[] massInput = new double[numberOfTrays];
    double[] massOutput = new double[numberOfTrays];
    double[] massBalance = new double[numberOfTrays];
    // System.out.println("water in feed "
    // +
    // feedStream.getFluid().getPhase(0).getComponent("water").getNumberOfmoles());
    // System.out.println("water in strip gas feed " +
    // trays.get(0).getStream(0).getFluid().getPhase(0)
    // .getComponent("water").getNumberOfmoles());

    for (int i = 0; i < numberOfTrays; i++) {
      int numberOfInputStreams = trays.get(i).getNumberOfInputStreams();
      for (int j = 0; j < numberOfInputStreams; j++) {
        massInput[i] += trays.get(i).getStream(j).getFluid().getFlowRate("kg/hr");
      }
      massOutput[i] += trays.get(i).getGasOutStream().getFlowRate("kg/hr");
      massOutput[i] += trays.get(i).getLiquidOutStream().getFlowRate("kg/hr");
      massBalance[i] = massInput[i] - massOutput[i];
      System.out.println("tray " + i + " number of input streams " + numberOfInputStreams
          + " massinput " + massInput[i] + " massoutput " + massOutput[i] + " massbalance "
          + massBalance[i] + " gasout " + trays.get(i).getGasOutStream().getFlowRate("kg/hr")
          + " liquidout " + trays.get(i).getLiquidOutStream().getFlowRate("kg/hr") + " pressure "
          + trays.get(i).getGasOutStream().getPressure() + " temperature "
          + trays.get(i).getGasOutStream().getTemperature("C"));
      /*
       * System.out.println( "tray " + i + " number of input streams " + numberOfInputStreams +
       * " water in gasout " +
       * trays.get(i).getGasOutStream().getFluid().getPhase(0).getComponent("water")
       * .getNumberOfmoles() + " water in liquidout " +
       * trays.get(i).getLiquidOutStream().getFluid().getPhase(0).getComponent( "water")
       * .getNumberOfmoles() + " pressure " + trays.get(i).getGasOutStream().getPressure() +
       * " temperature " + trays.get(i).getGasOutStream().getTemperature("C"));
       */
    }

    double massError = 0.0;
    for (int i = 0; i < numberOfTrays; i++) {
      massError += Math.abs(massBalance[i]);
    }
    if (massError > 1e-6) {
      return false;
    } else {
      return true;
    }
  }

  /**
   * <p>
   * energyBalanceCheck.
   * </p>
   */
  public void energyBalanceCheck() {
    double[] energyInput = new double[numberOfTrays];
    double[] energyOutput = new double[numberOfTrays];
    double[] energyBalance = new double[numberOfTrays];
    for (int i = 0; i < numberOfTrays; i++) {
      int numberOfInputStreams = trays.get(i).getNumberOfInputStreams();
      for (int j = 0; j < numberOfInputStreams; j++) {
        energyInput[i] += trays.get(i).getStream(j).getFluid().getEnthalpy();
      }
      energyOutput[i] += trays.get(i).getGasOutStream().getFluid().getEnthalpy();
      energyOutput[i] += trays.get(i).getLiquidOutStream().getFluid().getEnthalpy();
      energyBalance[i] = energyInput[i] - energyOutput[i];
      System.out.println(
          "tray " + i + " number of input streams " + numberOfInputStreams + " energyinput "
              + energyInput[i] + " energyoutput " + energyOutput[i] + " energybalance "
              + energyBalance[i] + " gasout " + trays.get(i).getGasOutStream().getFlowRate("kg/hr")
              + " liquidout " + trays.get(i).getLiquidOutStream().getFlowRate("kg/hr")
              + " pressure " + trays.get(i).getGasOutStream().getPressure() + " temperature "
              + trays.get(i).getGasOutStream().getTemperature("C"));
    }
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
        new neqsim.thermo.system.SystemSrkEos((273.15 - 0.0), 15.000);
    // testSystem.addComponent("methane", 10.00);
    testSystem.addComponent("ethane", 10.0);
    testSystem.addComponent("CO2", 10.0);
    testSystem.addComponent("propane", 20.0);
    // testSystem.addComponent("i-butane", 5.0);
    // testSystem.addComponent("n-hexane", 15.0);
    // testSystem.addComponent("n-heptane", 30.0);
    // testSystem.addComponent("n-octane", 4.0);
    // testSystem.addComponent("n-nonane", 3.0);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
    ops.TPflash();
    testSystem.display();
    Stream stream_1 = new Stream("Stream1", testSystem);

    DistillationColumn column = new DistillationColumn("distColumn", 5, true, true);
    column.addFeedStream(stream_1, 3);
    // column.getReboiler().setHeatInput(520000.0);
    ((Reboiler) column.getReboiler()).setRefluxRatio(0.5);
    // column.getCondenser().setHeatInput(-70000.0);
    // ((Condenser) column.getCondenser()).setRefluxRatio(0.2);

    neqsim.process.processmodel.ProcessSystem operations =
        new neqsim.process.processmodel.ProcessSystem();
    operations.add(stream_1);
    operations.add(column);
    operations.run();

    column.displayResult();
    System.out.println("reboiler duty" + ((Reboiler) column.getReboiler()).getDuty());
    System.out.println("condenser duty" + ((Condenser) column.getCondenser()).getDuty());
  }

  /**
   * <p>
   * getReboiler.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.distillation.SimpleTray} object
   */
  public SimpleTray getReboiler() {
    return trays.get(0);
  }

  /**
   * <p>
   * getCondenser.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.distillation.SimpleTray} object
   */
  public SimpleTray getCondenser() {
    return trays.get(trays.size() - 1);
  }

  /**
   * <p>
   * Getter for the field <code>reboilerTemperature</code>.
   * </p>
   *
   * @return the reboilerTemperature
   */
  public double getReboilerTemperature() {
    return reboilerTemperature;
  }

  /**
   * <p>
   * Setter for the field <code>reboilerTemperature</code>.
   * </p>
   *
   * @param reboilerTemperature the reboilerTemperature to set
   */
  public void setReboilerTemperature(double reboilerTemperature) {
    this.reboilerTemperature = reboilerTemperature;
  }

  /**
   * <p>
   * getCondenserTemperature.
   * </p>
   *
   * @return the condenserTemperature
   */
  public double getCondenserTemperature() {
    return condenserTemperature;
  }

  /**
   * <p>
   * setCondenserTemperature.
   * </p>
   *
   * @param condenserTemperature the condenserTemperature to set
   */
  public void setCondenserTemperature(double condenserTemperature) {
    this.condenserTemperature = condenserTemperature;
  }

  /**
   * <p>
   * Getter for the field <code>feedTrayNumber</code>.
   * </p>
   *
   * @return the feedTrayNumber
   */
  public int getFeedTrayNumber() {
    return feedTrayNumber;
  }

  /**
   * <p>
   * Setter for the field <code>feedTrayNumber</code>.
   * </p>
   *
   * @param feedTrayNumber the feedTrayNumber to set
   */
  public void setFeedTrayNumber(int feedTrayNumber) {
    this.feedTrayNumber = feedTrayNumber;
  }

  /**
   * <p>
   * isDoInitializion.
   * </p>
   *
   * @return a boolean
   */
  public boolean isDoInitializion() {
    return doInitializion;
  }

  /**
   * <p>
   * Setter for the field <code>doInitializion</code>.
   * </p>
   *
   * @param doInitializion a boolean
   */
  public void setDoInitializion(boolean doInitializion) {
    this.doInitializion = doInitializion;
  }

  /**
   * <p>
   * getFsFactor.
   * </p>
   *
   * @return a double
   */
  public double getFsFactor() {
    double intArea = 3.14 * getInternalDiameter() * getInternalDiameter() / 4.0;
    return getGasOutStream().getThermoSystem().getFlowRate("m3/sec") / intArea
        * Math.sqrt(getGasOutStream().getThermoSystem().getDensity("kg/m3"));
  }

  /**
   * <p>
   * Getter for the field <code>internalDiameter</code>.
   * </p>
   *
   * @return a double
   */
  public double getInternalDiameter() {
    return internalDiameter;
  }

  /** {@inheritDoc} */
  @Override
  public boolean solved() {
    return (err < 1e-4);
  }

  /**
   * <p>
   * Setter for the field <code>maxNumberOfIterations</code>.
   * </p>
   *
   * @param maxIter a double
   */
  public void setMaxNumberOfIterations(int maxIter) {
    this.maxNumberOfIterations = maxIter;
  }

  /**
   * <p>
   * Setter for the field <code>internalDiameter</code>.
   * </p>
   *
   * @param internalDiameter a double
   */
  public void setInternalDiameter(double internalDiameter) {
    this.internalDiameter = internalDiameter;
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result
        + Objects.hash(bottomTrayPressure, condenserCoolingDuty, condenserTemperature,
            distoperations, doInitializion, feedStream, feedTrayNumber, gasOutStream, hasCondenser,
            hasReboiler, heater, internalDiameter, liquidOutStream, numberOfTrays,
            reboilerTemperature, separator2, stream_3, stream_3isset, topTrayPressure, trays);
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!super.equals(obj)) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    DistillationColumn other = (DistillationColumn) obj;
    return Double.doubleToLongBits(bottomTrayPressure) == Double
        .doubleToLongBits(other.bottomTrayPressure)
        && Double.doubleToLongBits(condenserCoolingDuty) == Double
            .doubleToLongBits(other.condenserCoolingDuty)
        && Double.doubleToLongBits(condenserTemperature) == Double
            .doubleToLongBits(other.condenserTemperature)
        && Objects.equals(distoperations, other.distoperations)
        && doInitializion == other.doInitializion && Objects.equals(feedStream, other.feedStream)
        && feedTrayNumber == other.feedTrayNumber
        && Objects.equals(gasOutStream, other.gasOutStream) && hasCondenser == other.hasCondenser
        && hasReboiler == other.hasReboiler && Objects.equals(heater, other.heater)
        && Double.doubleToLongBits(internalDiameter) == Double
            .doubleToLongBits(other.internalDiameter)
        && Objects.equals(liquidOutStream, other.liquidOutStream)
        && numberOfTrays == other.numberOfTrays
        && Double.doubleToLongBits(reboilerTemperature) == Double
            .doubleToLongBits(other.reboilerTemperature)
        && Objects.equals(separator2, other.separator2) && Objects.equals(stream_3, other.stream_3)
        && stream_3isset == other.stream_3isset && Double
            .doubleToLongBits(topTrayPressure) == Double.doubleToLongBits(other.topTrayPressure)
        && Objects.equals(trays, other.trays);
  }
}
