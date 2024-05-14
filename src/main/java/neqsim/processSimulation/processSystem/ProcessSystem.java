package neqsim.processSimulation.processSystem;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import org.apache.commons.lang.SerializationUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.processSimulation.SimulationBaseClass;
import neqsim.processSimulation.conditionMonitor.ConditionMonitor;
import neqsim.processSimulation.measurementDevice.MeasurementDeviceInterface;
import neqsim.processSimulation.processEquipment.ProcessEquipmentBaseClass;
import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processEquipment.util.Recycle;
import neqsim.processSimulation.processEquipment.util.RecycleController;
import neqsim.processSimulation.util.report.Report;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * ProcessSystem class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ProcessSystem extends SimulationBaseClass {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(ProcessSystem.class);

  transient Thread thisThread;
  String[][] signalDB = new String[10000][100];
  private double surroundingTemperature = 288.15;
  private int timeStepNumber = 0;
  private ArrayList<ProcessEquipmentInterface> unitOperations =
      new ArrayList<ProcessEquipmentInterface>(0);
  ArrayList<MeasurementDeviceInterface> measurementDevices =
      new ArrayList<MeasurementDeviceInterface>(0);
  RecycleController recycleController = new RecycleController();
  private double timeStep = 1.0;

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
   * add.
   * </p>
   *
   * @param operation a {@link neqsim.processSimulation.processEquipment.ProcessEquipmentInterface}
   *        object
   */
  public void add(ProcessEquipmentInterface operation) {
    ArrayList<ProcessEquipmentInterface> units = this.getUnitOperations();

    for (ProcessEquipmentInterface unit : units) {
      if (unit == operation) {
        return;
      }
    }

    if (getAllUnitNames().contains(operation.getName())) {
      String currClass = operation.getClass().getSimpleName();
      int num = 1;
      for (ProcessEquipmentInterface unit : units) {
        if (unit.getClass().getSimpleName().equals(currClass)) {
          num++;
        }
      }
      operation.setName(currClass + Integer.toString(num));
    }

    getUnitOperations().add(operation);
    if (operation instanceof ModuleInterface) {
      ((ModuleInterface) operation).initializeModule();
    }
  }

  /**
   * <p>
   * add.
   * </p>
   *
   * @param position a int
   * @param operation a {@link neqsim.processSimulation.processEquipment.ProcessEquipmentInterface}
   *        object
   */
  public void add(int position, ProcessEquipmentInterface operation) {
    ArrayList<ProcessEquipmentInterface> units = this.getUnitOperations();

    for (ProcessEquipmentInterface unit : units) {
      if (unit == operation) {
        return;
      }
    }

    if (getAllUnitNames().contains(operation.getName())) {
      String currClass = operation.getClass().getSimpleName();
      int num = 1;
      for (ProcessEquipmentInterface unit : units) {
        if (unit.getClass().getSimpleName().equals(currClass)) {
          num++;
        }
      }
      operation.setName(currClass + Integer.toString(num));
    }

    getUnitOperations().add(position, operation);
    if (operation instanceof ModuleInterface) {
      ((ModuleInterface) operation).initializeModule();
    }
  }

  /**
   * <p>
   * add.
   * </p>
   *
   * @param measurementDevice a
   *        {@link neqsim.processSimulation.measurementDevice.MeasurementDeviceInterface} object
   */
  public void add(MeasurementDeviceInterface measurementDevice) {
    measurementDevices.add(measurementDevice);
  }

  /**
   * <p>
   * add.
   * </p>
   *
   * @param operations an array of
   *        {@link neqsim.processSimulation.processEquipment.ProcessEquipmentInterface} objects
   */
  public void add(ProcessEquipmentInterface[] operations) {
    getUnitOperations().addAll(Arrays.asList(operations));
  }

  /**
   * <p>
   * getUnit.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @return a {@link java.lang.Object} object
   */
  public Object getUnit(String name) {
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
   * getMeasurementDevice.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @return a {@link java.lang.Object} object
   */
  public Object getMeasurementDevice(String name) {
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
   * @param operation a {@link neqsim.processSimulation.processEquipment.ProcessEquipmentBaseClass}
   *        object
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
   * Getter for the field <code>unitOperations</code>.
   * </p>
   *
   * @return the unitOperations
   */
  public ArrayList<ProcessEquipmentInterface> getUnitOperations() {
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
    unitOperations = new ArrayList<ProcessEquipmentInterface>(0);
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
    boolean hasResycle = false;
    // boolean hasAdjuster = false;

    // Initializing recycle controller
    recycleController.clear();
    for (int i = 0; i < unitOperations.size(); i++) {
      if (unitOperations.get(i).getClass().getSimpleName().equals("Recycle")) {
        hasResycle = true;
        recycleController.addRecycle((Recycle) unitOperations.get(i));
      }
      if (unitOperations.get(i).getClass().getSimpleName().equals("Adjuster")) {
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
        if (!unitOperations.get(i).getClass().getSimpleName().equals("Recycle")) {
          try {
            if (iter == 1 || unitOperations.get(i).needRecalculation()) {
              unitOperations.get(i).run(id);
            }
          } catch (Exception ex) {
            // String error = ex.getMessage();
            logger.error(ex.getMessage(), ex);
          }
        }
        if (unitOperations.get(i).getClass().getSimpleName().equals("Recycle")
            && recycleController.doSolveRecycle((Recycle) unitOperations.get(i))) {
          try {
            unitOperations.get(i).run(id);
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
        if (unitOperations.get(i).getClass().getSimpleName().equals("Adjuster")) {
          if (!((neqsim.processSimulation.processEquipment.util.Adjuster) unitOperations.get(i))
              .solved()) {
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
    } while ((!isConverged || (iter < 2 && hasResycle)) && iter < 100);

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
        // if (unitOperations.get(i).needRecalculation()) {
        unitOperations.get(i).run(id);
        // }
      } catch (Exception ex) {
        // String error = ex.getMessage();
        logger.error(ex.getMessage(), ex);
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

  /**
   * {@inheritDoc}
   *
   * <p>
   * runTransient.
   * </p>
   */
  @Override
  public void runTransient(double dt, UUID id) {
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
    return true;
  }

  /**
   * <p>
   * Getter for the field <code>time</code>.
   * </p>
   *
   * @return a double
   */
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
      return time;
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
   * @return a {@link neqsim.processSimulation.processSystem.ProcessSystem} object
   */
  public static ProcessSystem open(String filePath) {
    try (ObjectInputStream objectinputstream =
        new ObjectInputStream(new FileInputStream(filePath))) {
      return (ProcessSystem) objectinputstream.readObject();
      // logger.info("process file open ok: " + filePath);
    } catch (Exception ex) {
      // logger.error(ex.toString());
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
    neqsim.dataPresentation.fileHandeling.createTextFile.TextFile tempFile =
        new neqsim.dataPresentation.fileHandeling.createTextFile.TextFile();
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

  /**
   * <p>
   * Getter for the field <code>name</code>.
   * </p>
   *
   * @return the name
   */
  @Override
  public String getName() {
    return name;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Setter for the field <code>name</code>.
   * </p>
   */
  @Override
  public void setName(String name) {
    this.name = name;
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
      if (unitOperations.get(i).getClass().getSimpleName().equals("Compressor")) {
        power += ((neqsim.processSimulation.processEquipment.compressor.Compressor) unitOperations
            .get(i)).getPower();
      } else if (unitOperations.get(i).getClass().getSimpleName().equals("Pump")) {
        power += ((neqsim.processSimulation.processEquipment.pump.Pump) unitOperations.get(i))
            .getPower();
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
      if (unitOperations.get(i).getClass().getSimpleName().equals("Cooler")) {
        heat +=
            ((neqsim.processSimulation.processEquipment.heatExchanger.Cooler) unitOperations.get(i))
                .getDuty();
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
      if (unitOperations.get(i).getClass().getSimpleName().equals("Heater")) {
        heat +=
            ((neqsim.processSimulation.processEquipment.heatExchanger.Heater) unitOperations.get(i))
                .getDuty();
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
   * @return a {@link neqsim.processSimulation.processSystem.ProcessSystem} object
   */
  public ProcessSystem copy() {
    byte[] bytes = SerializationUtils.serialize(this);
    ProcessSystem copyOperation = (ProcessSystem) SerializationUtils.deserialize(bytes);
    return copyOperation;
  }

  /**
   * <p>
   * getConditionMonitor.
   * </p>
   *
   * @return a {@link neqsim.processSimulation.conditionMonitor.ConditionMonitor} object
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

  /**
   * {@inheritDoc}
   * 
   * @return a String
   */
  public String getReport_json() {
    return new Report(this).generateJsonReport();
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
