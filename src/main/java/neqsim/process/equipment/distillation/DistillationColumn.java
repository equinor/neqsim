package neqsim.process.equipment.distillation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.util.monitor.DistillationColumnResponse;
import neqsim.thermo.system.SystemInterface;
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

  Mixer feedmixer = new Mixer("temp mixer");
  double bottomTrayPressure = -1.0;
  int numberOfTrays = 1;
  int maxNumberOfIterations = 10;
  StreamInterface stream_3 = new Stream("stream_3");
  StreamInterface gasOutStream = new Stream("gasOutStream");
  StreamInterface liquidOutStream = new Stream("liquidOutStream");
  boolean stream_3isset = false;
  private double internalDiameter = 1.0;
  neqsim.process.processmodel.ProcessSystem distoperations;
  Heater heater;
  Separator separator2;

  /**
   * Error measure used in solver to check convergence in run().
   */
  private double err = 1.0e10;

  /**
   * Instead of Map&lt;Integer,StreamInterface&gt;, we store a list of feed streams per tray number.
   * This allows multiple feeds to the same tray.
   */
  private Map<Integer, List<StreamInterface>> feedStreams = new HashMap<>();

  /**
   * <p>
   * Constructor for DistillationColumn.
   * </p>
   *
   * @param name Name of distillation column
   * @param numberOfTraysLocal Number of SimpleTrays to add (excluding reboiler/condenser)
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

    // If user sets hasReboiler, put that in as the first tray in 'trays' list
    if (hasReboiler) {
      trays.add(new Reboiler("Reboiler"));
      this.numberOfTrays++;
    }

    // Then the middle "simple" trays
    for (int i = 0; i < numberOfTraysLocal; i++) {
      trays.add(new SimpleTray("SimpleTray" + (i + 1)));
    }

    // If user sets hasCondenser, add it at the top
    if (hasCondenser) {
      trays.add(new Condenser("Condenser"));
      this.numberOfTrays++;
    }

    // Add them all to the process system
    for (int i = 0; i < this.numberOfTrays; i++) {
      distoperations.add(trays.get(i));
    }
  }

  /**
   * <p>
   * Add a feed stream to the specified tray. (Now allows multiple streams on the same trayNumber,
   * using a list.)
   * </p>
   *
   * @param inputStream the feed stream
   * @param feedTrayNumber the tray number (0-based in the code) to which this feed goes
   */
  public void addFeedStream(StreamInterface inputStream, int feedTrayNumber) {
    // Put this feed into our feedStreams list for that trayNumber
    feedStreams.computeIfAbsent(feedTrayNumber, k -> new ArrayList<>()).add(inputStream);

    // Also attach it to the tray itself
    getTray(feedTrayNumber).addStream(inputStream);

    // If your design is that *all* feed streams get combined in feedmixer:
    feedmixer.addStream(inputStream);
    feedmixer.run();

    // Then you optionally split the feedmixer output into dummy streams_3,
    // gasOutStream, liquidOutStream (the existing pattern).
    double moles = feedmixer.getOutStream().getThermoSystem().getTotalNumberOfMoles();
    stream_3 = feedmixer.getOutStream(); // combined
    gasOutStream.setThermoSystem(stream_3.getThermoSystem().clone());
    gasOutStream.getThermoSystem().setTotalNumberOfMoles(moles / 2.0);

    liquidOutStream.setThermoSystem(stream_3.getThermoSystem().clone());
    liquidOutStream.getThermoSystem().setTotalNumberOfMoles(moles / 2.0);

    // Mark that we need to re-initialize if new feeds are added
    setDoInitializion(true);
  }

  /**
   * Initialize column temperature guesses, pressures, etc. Tries to ensure each tray has two-phase
   * conditions.
   */
  public void init() {
    if (!isDoInitializion()) {
      return;
    }
    setDoInitializion(false);

    // If feedStreams is empty, nothing to do
    if (feedStreams.isEmpty()) {
      return;
    }

    // Grab the tray with the lowest index among the feed trays
    int firstFeedTrayNumber = feedStreams.keySet().stream().min(Integer::compareTo).get();

    // We run the first feed tray to see its temperature:
    getTray(firstFeedTrayNumber).run();

    // If that tray ended up single-phase, see if adding some other feed helps
    if (getTray(firstFeedTrayNumber).getFluid().getNumberOfPhases() == 1) {
      for (int i = 0; i < numberOfTrays; i++) {
        if (getTray(i).getNumberOfInputStreams() > 0 && i != firstFeedTrayNumber) {
          getTray(firstFeedTrayNumber).addStream(trays.get(i).getStream(0));
          getTray(firstFeedTrayNumber).run();
          // remove it again
          getTray(firstFeedTrayNumber)
              .removeInputStream(getTray(firstFeedTrayNumber).getNumberOfInputStreams() - 1);
          if (getTray(firstFeedTrayNumber).getThermoSystem().getNumberOfPhases() > 1) {
            break;
          }
        } else if (i == firstFeedTrayNumber && getTray(i).getNumberOfInputStreams() > 1) {
          getTray(firstFeedTrayNumber).addStream(trays.get(i).getStream(1));
          trays.get(firstFeedTrayNumber).run();
          getTray(firstFeedTrayNumber)
              .removeInputStream(getTray(firstFeedTrayNumber).getNumberOfInputStreams() - 1);
          if (getTray(firstFeedTrayNumber).getThermoSystem().getNumberOfPhases() > 1) {
            break;
          }
        }
      }
    }

    // Just in case it’s still single-phase, do an init(0), init(3).
    if (getTray(firstFeedTrayNumber).getFluid().getNumberOfPhases() == 1) {
      getTray(firstFeedTrayNumber).getThermoSystem().init(0);
      getTray(firstFeedTrayNumber).getThermoSystem().init(3);
    }

    // Set up reboiler tray’s temperature
    trays.get(0).addStream(trays.get(firstFeedTrayNumber).getLiquidOutStream().clone());
    trays.get(0).run();

    double feedTrayTemperature = getTray(firstFeedTrayNumber).getTemperature();

    if (trays.get(numberOfTrays - 1).getNumberOfInputStreams() > 0) {
      condenserTemperature = trays.get(numberOfTrays - 1).getThermoSystem().getTemperature();
    } else {
      condenserTemperature = feedTrayTemperature - 1.0;
    }
    reboilerTemperature = trays.get(0).getThermoSystem().getTemperature();

    // Rough guess for temperature steps
    double deltaTempCondenser = (feedTrayTemperature - condenserTemperature)
        / (numberOfTrays * 1.0 - firstFeedTrayNumber - 1);
    double deltaTempReboiler =
        (reboilerTemperature - feedTrayTemperature) / (firstFeedTrayNumber * 1.0);

    // set temperature from feed tray up
    double delta = 0;
    for (int i = firstFeedTrayNumber + 1; i < numberOfTrays; i++) {
      delta += deltaTempCondenser;
      trays.get(i)
          .setTemperature(getTray(firstFeedTrayNumber).getThermoSystem().getTemperature() - delta);
    }

    // set temperature from feed tray down
    delta = 0;
    for (int i = firstFeedTrayNumber - 1; i >= 0; i--) {
      delta += deltaTempReboiler;
      trays.get(i)
          .setTemperature(getTray(firstFeedTrayNumber).getThermoSystem().getTemperature() + delta);
    }

    // Link upward
    for (int i = 1; i < numberOfTrays; i++) {
      trays.get(i).addStream(trays.get(i - 1).getGasOutStream());
      trays.get(i).init();
      trays.get(i).run();
    }

    // Link downward
    for (int i = numberOfTrays - 2; i >= 1; i--) {
      trays.get(i).addStream(trays.get(i + 1).getLiquidOutStream());
      trays.get(i).init();
      trays.get(i).run();
    }

    int streamNumb = (trays.get(0)).getNumberOfInputStreams() - 1;
    trays.get(0).replaceStream(streamNumb, trays.get(1).getLiquidOutStream());
    trays.get(0).init();
    trays.get(0).run();
  }

  @Override
  public void run(UUID id) {
    if (feedStreams.isEmpty()) {
      // no feeds, nothing to do
      return;
    }

    // Find the *lowest* tray number among feed trays, for reference.
    int firstFeedTrayNumber = feedStreams.keySet().stream().min(Integer::compareTo).get();

    if (bottomTrayPressure < 0) {
      bottomTrayPressure = getTray(firstFeedTrayNumber).getStream(0).getPressure();
    }
    if (topTrayPressure < 0) {
      topTrayPressure = getTray(firstFeedTrayNumber).getStream(0).getPressure();
    }

    double dp = 0.0;
    if (numberOfTrays > 1) {
      dp = (bottomTrayPressure - topTrayPressure) / (numberOfTrays - 1.0);
    }
    // Set tray pressures linearly from bottom to top
    for (int i = 0; i < numberOfTrays; i++) {
      trays.get(i).setPressure(bottomTrayPressure - i * dp);
    }

    // numberOfFeedsUsed[i] will track how many streams we have assigned
    // to tray i so we call getStream(...) index properly
    int[] numeroffeeds = new int[numberOfTrays];

    // For each tray in feedStreams, pass each feed into that tray
    for (Entry<Integer, List<StreamInterface>> entry : feedStreams.entrySet()) {
      int feedTrayNumber = entry.getKey();
      List<StreamInterface> trayFeeds = entry.getValue();
      for (StreamInterface feedStream : trayFeeds) {
        numeroffeeds[feedTrayNumber]++;
        SystemInterface inpS = feedStream.getThermoSystem().clone();
        trays.get(feedTrayNumber).getStream(numeroffeeds[feedTrayNumber] - 1).setThermoSystem(inpS);
      }
    }

    // If there's only one tray total, just run it
    if (numberOfTrays == 1) {
      trays.get(0).run(id);
      gasOutStream.setThermoSystem(trays.get(0).getGasOutStream().getThermoSystem().clone());
      liquidOutStream.setThermoSystem(trays.get(0).getLiquidOutStream().getThermoSystem().clone());
      gasOutStream.setCalculationIdentifier(id);
      liquidOutStream.setCalculationIdentifier(id);
      setCalculationIdentifier(id);
      return;
    }

    // If we haven't done an init or we added feeds, do it now
    if (isDoInitializion()) {
      this.init();
    }

    err = 1.0e10;
    double errOld;
    int iter = 0;

    // We'll use this array to measure temperature changes in each iteration
    double[] oldtemps = new double[numberOfTrays];

    // Make sure feed tray is up to date
    trays.get(firstFeedTrayNumber).run(id);

    // Start the iterative solution
    do {
      iter++;
      errOld = err;
      err = 0.0;
      // Snapshot old temperatures
      for (int i = 0; i < numberOfTrays; i++) {
        oldtemps[i] = trays.get(i).getThermoSystem().getTemperature();
      }

      // Move upward (bottom to feed tray)
      for (int i = firstFeedTrayNumber; i > 1; i--) {
        int replaceStream1 = trays.get(i - 1).getNumberOfInputStreams() - 1;
        trays.get(i - 1).replaceStream(replaceStream1, trays.get(i).getLiquidOutStream());
        trays.get(i - 1).setPressure(bottomTrayPressure - (i - 1) * dp);
        trays.get(i - 1).run(id);
      }

      // reboiler tray hooking up to tray 1
      int streamNumb = trays.get(0).getNumberOfInputStreams() - 1;
      trays.get(0).setPressure(bottomTrayPressure);
      trays.get(0).replaceStream(streamNumb, trays.get(1).getLiquidOutStream());
      trays.get(0).run(id);

      // Move downward (feed tray to top)
      for (int i = 1; i <= numberOfTrays - 1; i++) {
        // The top tray has 1 input less
        int replaceStream = trays.get(i).getNumberOfInputStreams() - 2;
        if (i == (numberOfTrays - 1)) {
          replaceStream = trays.get(i).getNumberOfInputStreams() - 1;
        }
        trays.get(i).replaceStream(replaceStream, trays.get(i - 1).getGasOutStream());
        trays.get(i).run(id);
      }

      // Then from top minus 1 down to feed tray
      for (int i = numberOfTrays - 2; i >= firstFeedTrayNumber; i--) {
        int replaceStream = trays.get(i).getNumberOfInputStreams() - 1;
        trays.get(i).replaceStream(replaceStream, trays.get(i + 1).getLiquidOutStream());
        trays.get(i).run(id);
      }

      // Sum up changes in temperature
      for (int i = 0; i < numberOfTrays; i++) {
        err += Math.abs(oldtemps[i] - trays.get(i).getThermoSystem().getTemperature());
      }

      logger.info("error iteration = " + iter + "   err = " + err);
    } while (err > 1e-4 && err < errOld && iter < maxNumberOfIterations);

    // Once converged, fill final gasOut/liquidOut streams
    gasOutStream
        .setThermoSystem(trays.get(numberOfTrays - 1).getGasOutStream().getThermoSystem().clone());
    gasOutStream.setCalculationIdentifier(id);
    liquidOutStream.setThermoSystem(trays.get(0).getLiquidOutStream().getThermoSystem().clone());
    liquidOutStream.setCalculationIdentifier(id);

    // Mark everything as solved
    for (int i = 0; i < numberOfTrays; i++) {
      trays.get(i).setCalculationIdentifier(id);
    }
    setCalculationIdentifier(id);
  }

  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    distoperations.displayResult();
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
        trays.add(1, new SimpleTray("SimpleTray" + (oldNumberOfTrays + i + 1)));
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

  public void setTopCondenserDuty(double duty) {
    condenserCoolingDuty = duty;
  }

  public void setTopPressure(double topPressure) {
    topTrayPressure = topPressure;
  }

  public void setBottomPressure(double bottomPressure) {
    bottomTrayPressure = bottomPressure;
  }

  @Override
  public boolean solved() {
    return (err < 1e-4);
  }

  public void setMaxNumberOfIterations(int maxIter) {
    this.maxNumberOfIterations = maxIter;
  }

  public void setInternalDiameter(double internalDiameter) {
    this.internalDiameter = internalDiameter;
  }

  public double getInternalDiameter() {
    return internalDiameter;
  }


  /**
   * Calculates the Fs factor for the distillation column. The Fs factor is a measure of the gas
   * flow rate through the column relative to the cross-sectional area and the density of the gas.
   *
   * @return the Fs factor as a double value
   */
  public double getFsFactor() {
    double intArea = Math.PI * getInternalDiameter() * getInternalDiameter() / 4.0;
    return getGasOutStream().getThermoSystem().getFlowRate("m3/sec") / intArea
        * Math.sqrt(getGasOutStream().getThermoSystem().getDensity("kg/m3"));
  }

  public StreamInterface getGasOutStream() {
    return gasOutStream;
  }

  public StreamInterface getLiquidOutStream() {
    return liquidOutStream;
  }

  public Reboiler getReboiler() {
    return (Reboiler) trays.get(0);
  }

  public Condenser getCondenser() {
    return (Condenser) trays.get(trays.size() - 1);
  }

  public double getReboilerTemperature() {
    return reboilerTemperature;
  }

  public void setReboilerTemperature(double reboilerTemperature) {
    this.reboilerTemperature = reboilerTemperature;
  }

  public double getCondenserTemperature() {
    return condenserTemperature;
  }

  public void setCondenserTemperature(double condenserTemperature) {
    this.condenserTemperature = condenserTemperature;
  }

  public boolean isDoInitializion() {
    return doInitializion;
  }

  public void setDoInitializion(boolean doInitializion) {
    this.doInitializion = doInitializion;
  }

  /**
   * Check mass balance for all components.
   *
   * @return true if mass balance is within 1e-6
   */
  public boolean massBalanceCheck() {
    double[] massInput = new double[numberOfTrays];
    double[] massOutput = new double[numberOfTrays];
    double[] massBalance = new double[numberOfTrays];

    for (int i = 0; i < numberOfTrays; i++) {
      int numberOfInputStreams = trays.get(i).getNumberOfInputStreams();
      for (int j = 0; j < numberOfInputStreams; j++) {
        massInput[i] += trays.get(i).getStream(j).getFluid().getFlowRate("kg/hr");
      }
      massOutput[i] += trays.get(i).getGasOutStream().getFlowRate("kg/hr");
      massOutput[i] += trays.get(i).getLiquidOutStream().getFlowRate("kg/hr");
      massBalance[i] = massInput[i] - massOutput[i];

      System.out.println("Tray " + i + ": #in=" + numberOfInputStreams + ", massIn=" + massInput[i]
          + ", massOut=" + massOutput[i] + ", balance=" + massBalance[i]);
    }
    double massError = 0.0;
    for (int i = 0; i < numberOfTrays; i++) {
      massError += Math.abs(massBalance[i]);
    }
    return (massError <= 1e-6);
  }

  /**
   * Check mass balance for a specific component.
   *
   * @param componentName the component name
   * @return true if mass balance is within 1e-6
   */
  public boolean componentMassBalanceCheck(String componentName) {
    double[] massInput = new double[numberOfTrays];
    double[] massOutput = new double[numberOfTrays];
    double[] massBalance = new double[numberOfTrays];

    for (int i = 0; i < numberOfTrays; i++) {
      int numberOfInputStreams = trays.get(i).getNumberOfInputStreams();
      for (int j = 0; j < numberOfInputStreams; j++) {
        for (int k = 0; k < trays.get(i).getStream(j).getFluid().getNumberOfPhases(); k++) {
          massInput[i] += trays.get(i).getStream(j).getFluid().getPhase(k)
              .getComponent(componentName).getFlowRate("kg/hr");
        }
      }
      // outputs
      for (int k = 0; k < trays.get(i).getGasOutStream().getFluid().getNumberOfPhases(); k++) {
        massOutput[i] += trays.get(i).getGasOutStream().getFluid().getPhase(k)
            .getComponent(componentName).getFlowRate("kg/hr");
      }
      for (int k = 0; k < trays.get(i).getLiquidOutStream().getFluid().getNumberOfPhases(); k++) {
        massOutput[i] += trays.get(i).getLiquidOutStream().getFluid().getPhase(k)
            .getComponent(componentName).getFlowRate("kg/hr");
      }

      massBalance[i] = massInput[i] - massOutput[i];
      System.out.println(
          "Tray " + i + ", comp=" + componentName + ", #in=" + numberOfInputStreams + ", massIn="
              + massInput[i] + ", massOut=" + massOutput[i] + ", balance=" + massBalance[i]);
    }

    double massError = 0.0;
    for (int i = 0; i < numberOfTrays; i++) {
      massError += Math.abs(massBalance[i]);
    }
    return (massError < 1e-6);
  }

  /**
   * Optional: energy balance check.
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

      System.out.println("Tray " + i + ", #in=" + numberOfInputStreams + ", eIn=" + energyInput[i]
          + ", eOut=" + energyOutput[i] + ", balance=" + energyBalance[i]);
    }
  }

  /**
   * The main method demonstrates the creation and operation of a distillation column using the
   * NeqSim library. It performs the following steps:
   * 
   * 1. Creates a test thermodynamic system with methane, ethane, and propane components. 2.
   * Performs a TP flash calculation on the test system. 3. Creates two separate feed streams from
   * the test system. 4. Constructs a distillation column with 5 trays, a reboiler, and a condenser.
   * 5. Adds the two feed streams to the distillation column at tray 3. 6. Builds and runs the
   * process system. 7. Displays the results of the distillation column, including the gas and
   * liquid output streams.
   * 
   *
   * @param args command line arguments (not used)
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    // Create a test system
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos(273.15 + 25.0, 15.0);
    testSystem.addComponent("methane", 10.00);
    testSystem.addComponent("ethane", 10.0);
    testSystem.addComponent("propane", 10.0);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    neqsim.thermodynamicoperations.ThermodynamicOperations ops =
        new neqsim.thermodynamicoperations.ThermodynamicOperations(testSystem);
    ops.TPflash();
    testSystem.display();

    // Make two separate feed streams
    Stream feed1 = new Stream("Feed1", testSystem.clone());
    Stream feed2 = new Stream("Feed2", testSystem.clone());

    // Create the column with 5 "simple" trays, reboiler, condenser
    DistillationColumn column = new DistillationColumn("distColumn", 5, true, true);

    // Add feed1 to tray 3, feed2 also to tray 3
    column.addFeedStream(feed1, 3);
    column.addFeedStream(feed2, 3);

    // Build process
    neqsim.process.processmodel.ProcessSystem operations =
        new neqsim.process.processmodel.ProcessSystem();
    operations.add(feed1);
    operations.add(feed2);
    operations.add(column);
    operations.run();

    // Display
    column.displayResult();
    System.out.println("Gas out:");
    column.getGasOutStream().getThermoSystem().display();
    System.out.println("Liquid out:");
    column.getLiquidOutStream().getThermoSystem().display();
  }

  /**
   * Calculates the total number of sections in the distillation column. The total number of
   * sections is determined by adding the number of trays to any additional sections contributed by
   * the presence of a reboiler and/or a condenser.
   *
   * @return the total number of sections in the distillation column, including trays, reboiler (if
   *         present), and condenser (if present).
   */
  public int getNumberOfSections() {

    return numberOfTrays;
  }

  public boolean isHasReboiler() {
    return hasReboiler;
  }

  public boolean isHasCondenser() {
    return hasCondenser;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new GsonBuilder().create().toJson(new DistillationColumnResponse(this));
  }

}
