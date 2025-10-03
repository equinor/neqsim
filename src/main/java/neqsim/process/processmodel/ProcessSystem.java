package neqsim.process.processmodel;

// Reorganized imports into proper groups and order
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.SimulationBaseClass;
import neqsim.process.conditionmonitor.ConditionMonitor;
import neqsim.process.equipment.EquipmentEnum;
import neqsim.process.equipment.EquipmentFactory;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.util.Adjuster;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.util.RecycleController;
import neqsim.process.equipment.util.Setter;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;
import neqsim.process.util.report.Report;
import neqsim.thermo.system.SystemInterface;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * Represents a process system containing unit operations.
 *
 * @author esol
 */
public class ProcessSystem extends SimulationBaseClass {
  /**
   * Serialization version UID.
   */
  private static final long serialVersionUID = 1000;

  /**
   * Logger object for class.
   */
  static Logger logger = LogManager.getLogger(ProcessSystem.class);

  transient Thread thisThread;
  String[][] signalDB = new String[10000][100];
  private double surroundingTemperature = 288.15;
  private int timeStepNumber = 0;
  /**
   * List of unit operations in the process system.
   */
  private List<ProcessEquipmentInterface> unitOperations = new ArrayList<>();
  List<MeasurementDeviceInterface> measurementDevices =
      new ArrayList<MeasurementDeviceInterface>(0);
  RecycleController recycleController = new RecycleController();
  private double timeStep = 1.0;
  private boolean runStep = false;

  private final Map<String, Integer> equipmentCounter = new HashMap<>();
  private ProcessEquipmentInterface lastAddedUnit = null;

  /**
   * <p>
   * Constructor for ProcessSystem.
   * </p>
   */
  public ProcessSystem() {
    this("Process system");
  }

  /**
   * Constructor for ProcessSystem.
   *
   * @param name name of process
   */
  public ProcessSystem(String name) {
    super(name);
  }

  /**
   * <p>
   * Add to end.
   * </p>
   *
   * @param operation a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   */
  public void add(ProcessEquipmentInterface operation) {
    // Add to end
    add(this.getUnitOperations().size(), operation);
  }

  /**
   * <p>
   * Add to specific position.
   * </p>
   *
   * @param position 0-based position
   * @param operation a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   */
  public void add(int position, ProcessEquipmentInterface operation) {
    List<ProcessEquipmentInterface> units = this.getUnitOperations();

    for (ProcessEquipmentInterface unit : units) {
      if (unit == operation) {
        logger.info("Equipment " + operation.getName() + " is already included in ProcessSystem");
        return;
      }
    }

    if (getAllUnitNames().contains(operation.getName())) {
      ProcessEquipmentInterface existing = this.getUnit(operation.getName());
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException("ProcessSystem",
          "add", "operation", "- Process equipment of type " + existing.getClass().getSimpleName()
              + " named " + operation.getName() + " already included in ProcessSystem"));
    }

    getUnitOperations().add(position, operation);
    if (operation instanceof ModuleInterface) {
      ((ModuleInterface) operation).initializeModule();
    }
  }

  /**
   * <p>
   * Add measurementdevice.
   * </p>
   *
   * @param measurementDevice a {@link neqsim.process.measurementdevice.MeasurementDeviceInterface}
   *        object
   */
  public void add(MeasurementDeviceInterface measurementDevice) {
    measurementDevices.add(measurementDevice);
  }

  /**
   * <p>
   * Add multiple process equipment to end.
   * </p>
   *
   * @param operations an array of {@link neqsim.process.equipment.ProcessEquipmentInterface}
   *        objects
   */
  public void add(ProcessEquipmentInterface[] operations) {
    getUnitOperations().addAll(Arrays.asList(operations));
  }

  /**
   * <p>
   * Replace a unitoperation.
   * </p>
   *
   * @param name Name of the object to replace
   * @param newObject the object to replace it with
   * @return a {@link java.lang.Boolean} object
   */
  public boolean replaceUnit(String name, ProcessEquipmentInterface newObject) {
    try {
      ProcessEquipmentInterface unit = getUnit(name);
      unit = newObject;
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
    }
    return true;
  }

  /**
   * <p>
   * Get process equipmen by name.
   * </p>
   *
   * @param name Name of
   * @return a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   */
  public ProcessEquipmentInterface getUnit(String name) {
    for (int i = 0; i < getUnitOperations().size(); i++) {
      if (getUnitOperations().get(i) instanceof ModuleInterface) {
        for (int j = 0; j < ((ModuleInterface) getUnitOperations().get(i)).getOperations()
            .getUnitOperations().size(); j++) {
          if (((ModuleInterface) getUnitOperations().get(i)).getOperations().getUnitOperations()
              .get(j).getName().equals(name)) {
            return ((ModuleInterface) getUnitOperations().get(i)).getOperations()
                .getUnitOperations().get(j);
          }
        }
      } else if (getUnitOperations().get(i).getName().equals(name)) {
        return getUnitOperations().get(i);
      }
    }
    return null;
  }

  /**
   * <p>
   * hasUnitName.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @return a boolean
   */
  public boolean hasUnitName(String name) {
    if (getUnit(name) == null) {
      return false;
    } else {
      return true;
    }
  }

  /**
   * <p>
   * Get MeasureDevice by name.
   * </p>
   *
   * @param name Name of measurement device
   * @return a {@link neqsim.process.measurementdevice.MeasurementDeviceInterface} object
   */
  public MeasurementDeviceInterface getMeasurementDevice(String name) {
    for (int i = 0; i < measurementDevices.size(); i++) {
      if (measurementDevices.get(i).getName().equals(name)) {
        return measurementDevices.get(i);
      }
    }
    return null;
  }

  /**
   * <p>
   * getUnitNumber.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @return a int
   */
  public int getUnitNumber(String name) {
    for (int i = 0; i < getUnitOperations().size(); i++) {
      if (getUnitOperations().get(i) instanceof ModuleInterface) {
        for (int j = 0; j < ((ModuleInterface) getUnitOperations().get(i)).getOperations()
            .getUnitOperations().size(); j++) {
          if (((ModuleInterface) getUnitOperations().get(i)).getOperations().getUnitOperations()
              .get(j).getName().equals(name)) {
            return j;
          }
        }
      } else if (getUnitOperations().get(i).getName().equals(name)) {
        return i;
      }
    }
    return 0;
  }

  /**
   * <p>
   * replaceObject.
   * </p>
   *
   * @param unitName a {@link java.lang.String} object
   * @param operation a {@link neqsim.process.equipment.ProcessEquipmentBaseClass} object
   */
  public void replaceObject(String unitName, ProcessEquipmentBaseClass operation) {
    unitOperations.set(getUnitNumber(name), operation);
  }

  /**
   * <p>
   * getAllUnitNames.
   * </p>
   *
   * @return a {@link java.util.ArrayList} object
   */
  public ArrayList<String> getAllUnitNames() {
    ArrayList<String> unitNames = new ArrayList<String>();
    for (int i = 0; i < getUnitOperations().size(); i++) {
      if (getUnitOperations().get(i) instanceof ModuleInterface) {
        for (int j = 0; j < ((ModuleInterface) getUnitOperations().get(i)).getOperations()
            .getUnitOperations().size(); j++) {
          unitNames.add(((ModuleInterface) getUnitOperations().get(i)).getOperations()
              .getUnitOperations().get(j).getName());
        }
      }
      unitNames.add(unitOperations.get(i).getName());
    }
    return unitNames;
  }

  /**
   * <p>
   * Gets the list of unit operations.
   * </p>
   *
   * @return the list of unit operations
   */
  public List<ProcessEquipmentInterface> getUnitOperations() {
    return unitOperations;
  }

  /**
   * <p>
   * removeUnit.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public void removeUnit(String name) {
    for (int i = 0; i < unitOperations.size(); i++) {
      if (unitOperations.get(i).getName().equals(name)) {
        unitOperations.remove(i);
      }
    }
  }

  /**
   * <p>
   * clearAll.
   * </p>
   */
  public void clearAll() {
    unitOperations.clear();
  }

  /**
   * <p>
   * clear.
   * </p>
   */
  public void clear() {
    unitOperations = new ArrayList<ProcessEquipmentInterface>(0);
  }

  /**
   * <p>
   * setFluid.
   * </p>
   *
   * @param fluid1 a {@link neqsim.thermo.system.SystemInterface} object
   * @param fluid2 a {@link neqsim.thermo.system.SystemInterface} object
   * @param addNewComponents a boolean
   */
  public void setFluid(SystemInterface fluid1, SystemInterface fluid2, boolean addNewComponents) {
    fluid1.setEmptyFluid();
    boolean addedComps = false;
    for (int i = 0; i < fluid2.getNumberOfComponents(); i++) {
      if (fluid1.getPhase(0).hasComponent(fluid2.getComponent(i).getName())) {
        fluid1.addComponent(fluid2.getComponent(i).getName(),
            fluid2.getComponent(i).getNumberOfmoles());
      } else {
        if (addNewComponents) {
          addedComps = true;
          if (fluid2.getComponent(i).isIsTBPfraction()
              || fluid2.getComponent(i).isIsPlusFraction()) {
            fluid1.addTBPfraction(fluid2.getComponent(i).getName(),
                fluid2.getComponent(i).getNumberOfmoles(), fluid2.getComponent(i).getMolarMass(),
                fluid2.getComponent(i).getNormalLiquidDensity());
          } else {
            fluid1.addComponent(fluid2.getComponent(i).getName(),
                fluid2.getComponent(i).getNumberOfmoles());
          }
        }
      }
    }
    if (addedComps) {
      fluid1.createDatabase(true);
    }
    fluid1.init(0);
    fluid1.setTemperature(fluid2.getTemperature());
    fluid1.setPressure(fluid2.getPressure());
  }

  /**
   * <p>
   * setFluid.
   * </p>
   *
   * @param fluid1 a {@link neqsim.thermo.system.SystemInterface} object
   * @param fluid2 a {@link neqsim.thermo.system.SystemInterface} object
   */
  public void setFluid(SystemInterface fluid1, SystemInterface fluid2) {
    fluid1.setEmptyFluid();
    boolean addedComps = false;
    for (int i = 0; i < fluid2.getNumberOfComponents(); i++) {
      if (fluid1.getPhase(0).hasComponent(fluid2.getComponent(i).getName())) {
        fluid1.addComponent(fluid2.getComponent(i).getName(),
            fluid2.getComponent(i).getNumberOfmoles());
      } else {
        addedComps = true;
        if (fluid2.getComponent(i).isIsTBPfraction() || fluid2.getComponent(i).isIsPlusFraction()) {
          fluid1.addTBPfraction(fluid2.getComponent(i).getName(),
              fluid2.getComponent(i).getNumberOfmoles(), fluid2.getComponent(i).getMolarMass(),
              fluid2.getComponent(i).getNormalLiquidDensity());
        } else {
          fluid1.addComponent(fluid2.getComponent(i).getName(),
              fluid2.getComponent(i).getNumberOfmoles());
        }
      }
    }
    if (addedComps) {
      fluid1.createDatabase(true);
    }
    fluid1.init(0);
    fluid1.setTemperature(fluid2.getTemperature());
    fluid1.setPressure(fluid2.getPressure());
  }

  /**
   * <p>
   * runAsThread.
   * </p>
   *
   * @return a {@link java.lang.Thread} object
   */
  public Thread runAsThread() {
    Thread processThread = new Thread(this);
    processThread.start();
    return processThread;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    // Run setters first to set conditions
    for (int i = 0; i < unitOperations.size(); i++) {
      ProcessEquipmentInterface unit = unitOperations.get(i);
      if (unit instanceof Setter) {
        unit.run(id);
      }
    }

    boolean hasRecycle = false;
    // boolean hasAdjuster = false;

    // Initializing recycle controller
    recycleController.clear();
    for (int i = 0; i < unitOperations.size(); i++) {
      ProcessEquipmentInterface unit = unitOperations.get(i);
      if (unit instanceof Recycle) {
        hasRecycle = true;
        recycleController.addRecycle((Recycle) unit);
      }
      if (unit instanceof Adjuster) {
        // hasAdjuster = true;
      }
    }
    recycleController.init();

    boolean isConverged = true;
    int iter = 0;
    do {
      iter++;
      isConverged = true;
      for (int i = 0; i < unitOperations.size(); i++) {
        ProcessEquipmentInterface unit = unitOperations.get(i);
        if (Thread.currentThread().isInterrupted()) {
          logger.debug("Process simulation was interrupted, exiting run()..." + getName());
          break;
        }
        if (!(unit instanceof Recycle)) {
          try {
            if (iter == 1 || unit.needRecalculation()) {
              unit.run(id);
            }
          } catch (Exception ex) {
            // String error = ex.getMessage();
            logger.error("error running unit uperation " + unit.getName() + " "
                + ex.getMessage(), ex);
            ex.printStackTrace();
          }
        }
        if (unit instanceof Recycle && recycleController.doSolveRecycle((Recycle) unit)) {
          try {
            unit.run(id);
          } catch (Exception ex) {
            // String error = ex.getMessage();
            logger.error(ex.getMessage(), ex);
          }
        }
      }
      if (!recycleController.solvedAll() || recycleController.hasHigherPriorityLevel()) {
        isConverged = false;
      }

      if (recycleController.solvedCurrentPriorityLevel()) {
        recycleController.nextPriorityLevel();
      } else if (recycleController.hasLoverPriorityLevel() && !recycleController.solvedAll()) {
        recycleController.resetPriorityLevel();
        // isConverged=true;
      }

      for (int i = 0; i < unitOperations.size(); i++) {
        ProcessEquipmentInterface unit = unitOperations.get(i);
        if (unit instanceof Adjuster) {
          if (!((Adjuster) unit).solved()) {
            isConverged = false;
            break;
          }
        }
      }

      /*
       * signalDB = new String[1000][1 + 3 * measurementDevices.size()];
       *
       * signalDB[timeStepNumber] = new String[1 + 3 * measurementDevices.size()]; for (int i = 0; i
       * < measurementDevices.size(); i++) { signalDB[timeStepNumber][0] = Double.toString(time);
       * signalDB[timeStepNumber][3 * i + 1] = ((MeasurementDeviceInterface)
       * measurementDevices.get(i)) .getName(); signalDB[timeStepNumber][3 * i + 2] = Double
       * .toString(((MeasurementDeviceInterface) measurementDevices.get(i)).getMeasuredValue());
       * signalDB[timeStepNumber][3 * i + 3] = ((MeasurementDeviceInterface)
       * measurementDevices.get(i)) .getUnit(); }
       */
    } while (((!isConverged || (iter < 2 && hasRecycle)) && iter < 100) && !runStep
        && !Thread.currentThread().isInterrupted());

    for (int i = 0; i < unitOperations.size(); i++) {
      unitOperations.get(i).setCalculationIdentifier(id);
    }

    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  public void run_step(UUID id) {
    for (int i = 0; i < unitOperations.size(); i++) {
      try {
        if (Thread.currentThread().isInterrupted()) {
          logger.debug("Process simulation was interrupted, exiting run()..." + getName());
          break;
        }
        unitOperations.get(i).run(id);
        // }
      } catch (Exception ex) {
        // String error = ex.getMessage();
        logger.error(
            "equipment: " + unitOperations.get(i).getName() + " errror: " + ex.getMessage(), ex);
      }
    }
    for (int i = 0; i < unitOperations.size(); i++) {
      unitOperations.get(i).setCalculationIdentifier(id);
    }
    setCalculationIdentifier(id);
  }

  /*
   * signalDB = new String[1000][1 + 3 * measurementDevices.size()];
   *
   * signalDB[timeStepNumber] = new String[1 + 3 * measurementDevices.size()]; for (int i = 0; i <
   * measurementDevices.size(); i++) { signalDB[timeStepNumber][0] = Double.toString(time);
   * signalDB[timeStepNumber][3 * i + 1] = ((MeasurementDeviceInterface) measurementDevices.get(i))
   * .getName(); signalDB[timeStepNumber][3 * i + 2] = Double
   * .toString(((MeasurementDeviceInterface) measurementDevices.get(i)).getMeasuredValue());
   * signalDB[timeStepNumber][3 * i + 3] = ((MeasurementDeviceInterface) measurementDevices.get(i))
   * .getUnit(); }
   */

  /**
   * <p>
   * runTransient.
   * </p>
   */
  public void runTransient() {
    runTransient(getTimeStep(), UUID.randomUUID());
  }

  /** {@inheritDoc} */
  @Override
  public void runTransient(double dt, UUID id) {
    for (int i = 0; i < unitOperations.size(); i++) {
      ProcessEquipmentInterface unit = unitOperations.get(i);
      if (unit instanceof Setter) {
        unit.run(id);
      }
    }

    setTimeStep(dt);
    increaseTime(dt);

    for (int i = 0; i < unitOperations.size(); i++) {
      unitOperations.get(i).runTransient(dt, id);
    }

    timeStepNumber++;
    signalDB[timeStepNumber] = new String[1 + 3 * measurementDevices.size()];
    for (int i = 0; i < measurementDevices.size(); i++) {
      signalDB[timeStepNumber][0] = Double.toString(time);
      signalDB[timeStepNumber][3 * i + 1] = measurementDevices.get(i).getName();
      signalDB[timeStepNumber][3 * i + 2] =
          Double.toString(measurementDevices.get(i).getMeasuredValue());
      signalDB[timeStepNumber][3 * i + 3] = measurementDevices.get(i).getUnit();
    }
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  public boolean solved() {
    /* */
    if (recycleController.solvedAll()) {
      for (int i = 0; i < unitOperations.size(); i++) {
        logger.info("unit " + unitOperations.get(i).getName() + " solved: "
            + unitOperations.get(i).solved());
        if (!unitOperations.get(i).solved()) {
          return false;
        }
      }
    } else {
      return false;
    }
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public double getTime() {
    return time;
  }

  /**
   * <p>
   * Getter for the field <code>time</code>.
   * </p>
   *
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public double getTime(String unit) {
    if (unit.equals("sec")) {
    } else if (unit.equals("hr")) {
      return time / 3600.0;
    } else if (unit.equals("day")) {
      return time / (3600.0 * 24);
    } else if (unit.equals("year")) {
      return time / (3600.0 * 24 * 365);
    }
    return time;
  }

  /**
   * <p>
   * size.
   * </p>
   *
   * @return a int
   */
  public int size() {
    return unitOperations.size();
  }

  /**
   * <p>
   * view.
   * </p>
   */
  public void view() {
    this.displayResult();
  }

  /**
   * <p>
   * displayResult.
   * </p>
   */
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    try {
      thisThread.join();
    } catch (Exception ex) {
      logger.error("Thread did not finish", ex);
    }
    for (int i = 0; i < unitOperations.size(); i++) {
      unitOperations.get(i).displayResult();
    }

    /*
     * JFrame frame = new JFrame(); frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
     * frame.setLayout(new GridLayout(1, 0, 5, 5)); JTextArea area1 = new JTextArea(10, 10); JTable
     * Jtab = new JTable(reportResults(), reportResults()[0]); frame.add(area1); frame.pack();
     * frame.setLocationRelativeTo(null); frame.setVisible(true);
     */
  }

  /**
   * <p>
   * reportMeasuredValues.
   * </p>
   */
  public void reportMeasuredValues() {
    try {
      thisThread.join();
    } catch (Exception ex) {
      logger.error("Thread did not finish", ex);
    }
    for (int i = 0; i < measurementDevices.size(); i++) {
      System.out.println("Measurements Device Name: " + measurementDevices.get(i).getName());
      System.out.println("Value: " + measurementDevices.get(i).getMeasuredValue() + " "
          + measurementDevices.get(i).getUnit());
      if (measurementDevices.get(i).isOnlineSignal()) {
        System.out.println("Online value: " + measurementDevices.get(i).getOnlineSignal().getValue()
            + " " + measurementDevices.get(i).getOnlineSignal().getUnit());
      }
    }
  }

  /**
   * <p>
   * save.
   * </p>
   *
   * @param filePath a {@link java.lang.String} object
   */
  public void save(String filePath) {
    try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(filePath, false))) {
      out.writeObject(this);
      logger.info("process file saved to:  " + filePath);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }

  /**
   * <p>
   * open.
   * </p>
   *
   * @param filePath a {@link java.lang.String} object
   * @return a {@link neqsim.process.processmodel.ProcessSystem} object
   */
  public static ProcessSystem open(String filePath) {
    try (ObjectInputStream objectinputstream =
        new ObjectInputStream(new FileInputStream(filePath))) {
      return (ProcessSystem) objectinputstream.readObject();
      // logger.info("process file open ok: " + filePath);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    return null;
  }

  /**
   * <p>
   * reportResults.
   * </p>
   *
   * @return an array of {@link java.lang.String} objects
   */
  public String[][] reportResults() {
    String[][] text = new String[200][];

    int numb = 0;
    for (int i = 0; i < unitOperations.size(); i++) {
      for (int k = 0; k < unitOperations.get(i).reportResults().length; k++) {
        text[numb++] = unitOperations.get(i).reportResults()[k];
      }
    }
    return text;
  }

  /**
   * <p>
   * printLogFile.
   * </p>
   *
   * @param filename a {@link java.lang.String} object
   */
  public void printLogFile(String filename) {
    neqsim.datapresentation.filehandling.TextFile tempFile =
        new neqsim.datapresentation.filehandling.TextFile();
    tempFile.setOutputFileName(filename);
    tempFile.setValues(signalDB);
    tempFile.createFile();
  }

  /**
   * <p>
   * Getter for the field <code>timeStep</code>.
   * </p>
   *
   * @return a double
   */
  public double getTimeStep() {
    return timeStep;
  }

  /**
   * <p>
   * Setter for the field <code>timeStep</code>.
   * </p>
   *
   * @param timeStep a double
   */
  public void setTimeStep(double timeStep) {
    this.timeStep = timeStep;
  }

  /** {@inheritDoc} */
  @Override
  public String getName() {
    return name;
  }

  /** {@inheritDoc} */
  @Override
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Setter for the field <code>runStep</code>.
   *
   * @param runStep A <code>boolean</code> value if run only one iteration
   */
  public void setRunStep(boolean runStep) {
    this.runStep = runStep;
  }

  /**
   * Getter for the field <code>runStep</code>.
   *
   * @return A <code>boolean</code> value if run only one iteration
   */
  public boolean isRunStep() {
    return runStep;
  }

  /**
   * <p>
   * getEntropyProduction.
   * </p>
   *
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public double getEntropyProduction(String unit) {
    double entropyProduction = 0.0;
    for (int i = 0; i < unitOperations.size(); i++) {
      entropyProduction += unitOperations.get(i).getEntropyProduction(unit);
      System.out.println("unit " + unitOperations.get(i).getName() + " entropy production "
          + unitOperations.get(i).getEntropyProduction(unit));
    }
    return entropyProduction;
  }

  /**
   * <p>
   * getExergyChange.
   * </p>
   *
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public double getExergyChange(String unit) {
    double exergyChange = 0.0;
    for (int i = 0; i < unitOperations.size(); i++) {
      exergyChange += unitOperations.get(i).getExergyChange("J", getSurroundingTemperature());
      System.out.println("unit " + unitOperations.get(i).getName() + " exergy change  "
          + unitOperations.get(i).getExergyChange("J", getSurroundingTemperature()));
    }
    return exergyChange;
  }

  /**
   * <p>
   * getPower.
   * </p>
   *
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public double getPower(String unit) {
    double power = 0.0;
    for (int i = 0; i < unitOperations.size(); i++) {
      ProcessEquipmentInterface unitOp = unitOperations.get(i);
      if (unitOp instanceof Compressor) {
        power += ((Compressor) unitOp).getPower();
      } else if (unitOp instanceof Pump) {
        power += ((Pump) unitOp).getPower();
      }
    }
    if (unit.equals("MW")) {
      return power / 1.0e6;
    } else if (unit.equals("kW")) {
      return power / 1.0e3;
    } else {
      return power;
    }
  }

  /**
   * <p>
   * getCoolerDuty.
   * </p>
   *
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public double getCoolerDuty(String unit) {
    double heat = 0.0;
    for (int i = 0; i < unitOperations.size(); i++) {
      ProcessEquipmentInterface unitOp = unitOperations.get(i);
      if (unitOp instanceof Cooler) {
        heat += ((Cooler) unitOp).getDuty();
      }
    }
    if (unit.equals("MW")) {
      return heat / 1.0e6;
    } else if (unit.equals("kW")) {
      return heat / 1.0e3;
    } else {
      return heat;
    }
  }

  /**
   * <p>
   * getHeaterDuty.
   * </p>
   *
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public double getHeaterDuty(String unit) {
    double heat = 0.0;
    for (int i = 0; i < unitOperations.size(); i++) {
      ProcessEquipmentInterface unitOp = unitOperations.get(i);
      if (unitOp instanceof Heater) {
        heat += ((Heater) unitOp).getDuty();
      }
    }
    if (unit.equals("MW")) {
      return heat / 1.0e6;
    } else if (unit.equals("kW")) {
      return heat / 1.0e3;
    } else {
      return heat;
    }
  }

  /**
   * <p>
   * Getter for the field <code>surroundingTemperature</code>.
   * </p>
   *
   * @return a double
   */
  public double getSurroundingTemperature() {
    return surroundingTemperature;
  }

  /**
   * <p>
   * Setter for the field <code>surroundingTemperature</code>.
   * </p>
   *
   * @param surroundingTemperature a double
   */
  public void setSurroundingTemperature(double surroundingTemperature) {
    this.surroundingTemperature = surroundingTemperature;
  }

  /**
   * <p>
   * Create deep copy.
   * </p>
   *
   * @return a {@link neqsim.process.processmodel.ProcessSystem} object
   */
  public ProcessSystem copy() {
    try {
      ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
      ObjectOutputStream out = new ObjectOutputStream(byteOut);
      out.writeObject(this);
      out.flush();
      ByteArrayInputStream byteIn = new ByteArrayInputStream(byteOut.toByteArray());
      ObjectInputStream in = new ObjectInputStream(byteIn);
      return (ProcessSystem) in.readObject();
    } catch (Exception e) {
      throw new RuntimeException("Failed to copy ProcessSystem", e);
    }
  }

  /**
   * <p>
   * getConditionMonitor.
   * </p>
   *
   * @return a {@link neqsim.process.conditionmonitor.ConditionMonitor} object
   */
  public ConditionMonitor getConditionMonitor() {
    return new ConditionMonitor(this);
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + Arrays.deepHashCode(signalDB);
    result = prime * result + Objects.hash(measurementDevices, name, recycleController,
        surroundingTemperature, time, timeStep, timeStepNumber, unitOperations);
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    ProcessSystem other = (ProcessSystem) obj;
    return Objects.equals(measurementDevices, other.measurementDevices)
        && Objects.equals(name, other.name)
        && Objects.equals(recycleController, other.recycleController)
        && Arrays.deepEquals(signalDB, other.signalDB)
        && Double.doubleToLongBits(surroundingTemperature) == Double
            .doubleToLongBits(other.surroundingTemperature)
        && Double.doubleToLongBits(time) == Double.doubleToLongBits(other.time)
        && Double.doubleToLongBits(timeStep) == Double.doubleToLongBits(other.timeStep)
        && timeStepNumber == other.timeStepNumber
        && Objects.equals(unitOperations, other.unitOperations);
  }

  /** {@inheritDoc} */
  @Override
  public String getReport_json() {
    return new Report(this).generateJsonReport();
  }

  /**
   * <p>
   * addUnit.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param equipmentType a {@link java.lang.String} object
   * @param <T> a T class
   * @return a T object
   */
  @SuppressWarnings("unchecked")
  public <T extends ProcessEquipmentInterface> T addUnit(String name, String equipmentType) {
    ProcessEquipmentInterface unit = EquipmentFactory.createEquipment(name, equipmentType);

    // If the provided name is null or empty, generate a unique name based on the equipment type.
    if (name == null || name.trim().isEmpty()) {
      name = generateUniqueName(equipmentType);
    }

    unit.setName(name);

    // Auto-connect streams if possible.
    autoConnect(lastAddedUnit, unit);

    this.add(unit);
    lastAddedUnit = unit; // Update last added unit
    return (T) unit;
  }

  /**
   * <p>
   * addUnit.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param equipmentEnum a {@link neqsim.process.equipment.EquipmentEnum} object
   * @param <T> a T class
   * @return a T object
   */
  @SuppressWarnings("unchecked")
  public <T extends ProcessEquipmentInterface> T addUnit(String name, EquipmentEnum equipmentEnum) {
    return (T) addUnit(name, equipmentEnum.name());
  }

  // New overload: addUnit only with equipmentType String
  /**
   * <p>
   * addUnit.
   * </p>
   *
   * @param equipmentType a {@link java.lang.String} object
   * @param <T> a T class
   * @return a T object
   */
  @SuppressWarnings("unchecked")
  public <T extends ProcessEquipmentInterface> T addUnit(String equipmentType) {
    return (T) addUnit(null, equipmentType);
  }

  // New overload: addUnit only with EquipmentEnum
  /**
   * <p>
   * addUnit.
   * </p>
   *
   * @param equipmentEnum a {@link neqsim.process.equipment.EquipmentEnum} object
   * @param <T> a T class
   * @return a T object
   */
  @SuppressWarnings("unchecked")
  public <T extends ProcessEquipmentInterface> T addUnit(EquipmentEnum equipmentEnum) {
    return (T) addUnit(null, equipmentEnum);
  }

  /**
   * Adds a new process equipment unit of the specified type and name, and sets its inlet stream.
   *
   * @param <T> the type of process equipment
   * @param name the name of the equipment (if null or empty, a unique name is generated)
   * @param equipmentType the type of equipment to create (as a String)
   * @param stream the inlet stream to set for the new equipment
   * @return the created and added process equipment unit
   */
  public <T extends ProcessEquipmentInterface> T addUnit(String name, String equipmentType,
      neqsim.process.equipment.stream.StreamInterface stream) {
    ProcessEquipmentInterface unit = EquipmentFactory.createEquipment(name, equipmentType);

    if (name == null || name.trim().isEmpty()) {
      name = generateUniqueName(equipmentType);
    }
    unit.setName(name);

    // Set the inlet stream if possible
    try {
      java.lang.reflect.Method setInlet = unit.getClass().getMethod("setInletStream",
          neqsim.process.equipment.stream.StreamInterface.class);
      setInlet.invoke(unit, stream);
    } catch (NoSuchMethodException ignored) {
      // If the method does not exist, do nothing
    } catch (Exception e) {
      e.printStackTrace();
    }

    this.add(unit);
    lastAddedUnit = unit;
    return (T) unit;
  }

  /**
   * <p>
   * addUnit.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param equipment a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   * @return a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   */
  public ProcessEquipmentInterface addUnit(String name, ProcessEquipmentInterface equipment) {
    unitOperations.add(equipment);
    equipment.setName(name);
    lastAddedUnit = equipment;
    equipment.run();
    return equipment;
  }

  /**
   * <p>
   * addUnit.
   * </p>
   *
   * @param equipment a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   * @return a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   */
  public ProcessEquipmentInterface addUnit(ProcessEquipmentInterface equipment) {
    String generatedName = generateUniqueName(equipment.getClass().getSimpleName());
    return addUnit(generatedName, equipment);
  }

  private String generateUniqueName(String equipmentType) {
    int count = equipmentCounter.getOrDefault(equipmentType, 0) + 1;
    equipmentCounter.put(equipmentType, count);
    String formatted = equipmentType.substring(0, 1).toLowerCase() + equipmentType.substring(1);
    return formatted + "_" + count;
  }

  // --- Auto Connection (Outlet -> Inlet) ---

  private void autoConnect(ProcessEquipmentInterface fromUnit, ProcessEquipmentInterface toUnit) {
    if (fromUnit == null) {
      return;
    }
    fromUnit.run();
    try {
      java.lang.reflect.Method getOutlet = fromUnit.getClass().getMethod("getOutletStream");
      Object outletStream = getOutlet.invoke(fromUnit);

      if (outletStream != null) {
        java.lang.reflect.Method setInlet = toUnit.getClass().getMethod("setInletStream",
            neqsim.process.equipment.stream.StreamInterface.class);
        setInlet.invoke(toUnit, outletStream);
      }
    } catch (NoSuchMethodException ignored) {
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * <p>
   * exportToGraphviz.
   * </p>
   *
   * @param filename a {@link java.lang.String} object
   */
  public void exportToGraphviz(String filename) {
    new ProcessSystemGraphvizExporter().export(this, filename);
  }

  /**
   * Export the process to Graphviz with configurable stream annotations.
   *
   * @param filename the Graphviz output file
   * @param options export options controlling stream annotations and table output
   */
  public void exportToGraphviz(String filename,
      ProcessSystemGraphvizExporter.GraphvizExportOptions options) {
    new ProcessSystemGraphvizExporter().export(this, filename, options);
  }

  /**
   * Load a process from a YAML file.
   *
   * @param yamlFile the YAML file to load
   */
  public void loadProcessFromYaml(File yamlFile) {
    try {
      neqsim.process.processmodel.ProcessLoader.loadProcessFromYaml(yamlFile, this);
    } catch (Exception e) {
      logger.error("Error loading process from YAML file", e);
    }
  }

  /*
   * @XmlRootElement private class Report extends Object{ public Double name; public
   * ArrayList<ReportInterface> unitOperationsReports = new ArrayList<ReportInterface>();
   *
   * Report(){ name= getName();
   *
   * for (int i = 0; i < unitOperations.size(); i++) {
   * unitOperationsReports.add(unitOperations.getReport()); } } }
   *
   * public Report getReport(){ return this.new Report(); }
   */
}
