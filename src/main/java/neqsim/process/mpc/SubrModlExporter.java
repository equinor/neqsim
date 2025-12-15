package neqsim.process.mpc;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.processmodel.ProcessSystem;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Exports NeqSim process models in SubrModl format for nonlinear MPC integration.
 *
 * <p>
 * This class generates configuration files compatible with industrial nonlinear MPC systems that
 * use programmed model objects (SubrModl). The export includes:
 * </p>
 * <ul>
 * <li>Model parameters (constants like volume, height, density)</li>
 * <li>SubrXvr definitions with DtaIx mappings for variable linking</li>
 * <li>State variables (SVR) for internal model states</li>
 * <li>Initial values and engineering unit specifications</li>
 * </ul>
 *
 * <p>
 * The SubrModl format supports nonlinear dynamic equations with commands like INIT, STEP, STDSTEP
 * for steady-state, UPDT for state updates, and SAVE for persistence.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 * 
 * <pre>
 * {@code
 * ProcessSystem process = new ProcessSystem();
 * // ... add equipment ...
 * 
 * SubrModlExporter exporter = new SubrModlExporter(process);
 * exporter.setModelName("WellModel");
 * exporter.addParameter("Volume", 100.0, "m3");
 * exporter.addParameter("Height", 2000.0, "m");
 * 
 * // Add SubrXvr definitions
 * exporter.addSubrXvr("Pdownhole", "pdh", "Downhole pressure", 147.7);
 * exporter.addSubrXvr("Pwellhead", "pwh", "Wellhead pressure", 10.4);
 * 
 * // Export configuration
 * exporter.exportConfiguration("wellmodel_config.txt");
 * }
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 * @since 3.0
 */
public class SubrModlExporter implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** The process system to export. */
  private final ProcessSystem processSystem;

  /** Model name for SubrModl/SubrProc. */
  private String modelName = "ProcessModel";

  /** Application name for the configuration. */
  private String applicationName = "NeqSimModel";

  /** Model parameters (constants). */
  private final List<ModelParameter> parameters;

  /** SubrXvr definitions. */
  private final List<SubrXvr> subrXvrs;

  /** State variables (SVR). */
  private final List<StateVariable> stateVariables;

  /** Index table entries. */
  private final List<String> indexTable;

  /** Sample time for the model (seconds). */
  private double sampleTime = 1.0;

  /**
   * Model parameter definition.
   */
  public static class ModelParameter implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String name;
    private final double value;
    private final String unit;
    private final String description;

    /**
     * Create a model parameter.
     *
     * @param name parameter name
     * @param value parameter value
     * @param unit engineering unit
     * @param description parameter description
     */
    public ModelParameter(String name, double value, String unit, String description) {
      this.name = name;
      this.value = value;
      this.unit = unit;
      this.description = description;
    }

    public String getName() {
      return name;
    }

    public double getValue() {
      return value;
    }

    public String getUnit() {
      return unit;
    }

    public String getDescription() {
      return description;
    }
  }

  /**
   * SubrXvr definition for variable linking.
   */
  public static class SubrXvr implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String name;
    private final String dtaIx;
    private final String text1;
    private final String text2;
    private double init;
    private String unit;

    /**
     * Create a SubrXvr definition.
     *
     * @param name variable name
     * @param dtaIx data index for C++ linking
     * @param text1 primary description
     * @param init initial value
     */
    public SubrXvr(String name, String dtaIx, String text1, double init) {
      this.name = name;
      this.dtaIx = dtaIx;
      this.text1 = text1;
      this.text2 = "";
      this.init = init;
      this.unit = "";
    }

    public String getName() {
      return name;
    }

    public String getDtaIx() {
      return dtaIx;
    }

    public String getText1() {
      return text1;
    }

    public String getText2() {
      return text2;
    }

    public double getInit() {
      return init;
    }

    public String getUnit() {
      return unit;
    }

    public SubrXvr setInit(double init) {
      this.init = init;
      return this;
    }

    public SubrXvr setUnit(String unit) {
      this.unit = unit;
      return this;
    }
  }

  /**
   * State variable (SVR) definition.
   */
  public static class StateVariable implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String name;
    private final String dtaIx;
    private final String description;
    private double modelValue;
    private double measValue;
    private boolean measured;

    /**
     * Create a state variable.
     *
     * @param name variable name
     * @param dtaIx data index
     * @param description variable description
     * @param modelValue initial model value
     */
    public StateVariable(String name, String dtaIx, String description, double modelValue) {
      this.name = name;
      this.dtaIx = dtaIx;
      this.description = description;
      this.modelValue = modelValue;
      this.measValue = modelValue;
      this.measured = false;
    }

    public String getName() {
      return name;
    }

    public String getDtaIx() {
      return dtaIx;
    }

    public String getDescription() {
      return description;
    }

    public double getModelValue() {
      return modelValue;
    }

    public double getMeasValue() {
      return measValue;
    }

    public boolean isMeasured() {
      return measured;
    }

    public StateVariable setMeasValue(double measValue) {
      this.measValue = measValue;
      this.measured = true;
      return this;
    }
  }

  /**
   * Construct a SubrModl exporter.
   *
   * @param processSystem the process system to export
   */
  public SubrModlExporter(ProcessSystem processSystem) {
    this.processSystem = processSystem;
    this.parameters = new ArrayList<>();
    this.subrXvrs = new ArrayList<>();
    this.stateVariables = new ArrayList<>();
    this.indexTable = new ArrayList<>();
    // Always start with Nsecs
    this.indexTable.add("Nsecs");
  }

  /**
   * Set the model name.
   *
   * @param modelName the model name
   * @return this exporter for chaining
   */
  public SubrModlExporter setModelName(String modelName) {
    this.modelName = modelName;
    return this;
  }

  /**
   * Set the application name.
   *
   * @param applicationName the application name
   * @return this exporter for chaining
   */
  public SubrModlExporter setApplicationName(String applicationName) {
    this.applicationName = applicationName;
    return this;
  }

  /**
   * Set the sample time.
   *
   * @param sampleTime sample time in seconds
   * @return this exporter for chaining
   */
  public SubrModlExporter setSampleTime(double sampleTime) {
    this.sampleTime = sampleTime;
    return this;
  }

  /**
   * Add a model parameter (constant).
   *
   * @param name parameter name
   * @param value parameter value
   * @param unit engineering unit
   * @return this exporter for chaining
   */
  public SubrModlExporter addParameter(String name, double value, String unit) {
    parameters.add(new ModelParameter(name, value, unit, ""));
    return this;
  }

  /**
   * Add a model parameter with description.
   *
   * @param name parameter name
   * @param value parameter value
   * @param unit engineering unit
   * @param description parameter description
   * @return this exporter for chaining
   */
  public SubrModlExporter addParameter(String name, double value, String unit, String description) {
    parameters.add(new ModelParameter(name, value, unit, description));
    return this;
  }

  /**
   * Add a SubrXvr definition.
   *
   * @param name variable name
   * @param dtaIx data index for C++ linking
   * @param description variable description
   * @param init initial value
   * @return this exporter for chaining
   */
  public SubrModlExporter addSubrXvr(String name, String dtaIx, String description, double init) {
    subrXvrs.add(new SubrXvr(name, dtaIx, description, init));
    if (!indexTable.contains(dtaIx)) {
      indexTable.add(dtaIx);
    }
    return this;
  }

  /**
   * Add a state variable (SVR).
   *
   * @param name variable name
   * @param dtaIx data index
   * @param description variable description
   * @param modelValue initial model value
   * @return this exporter for chaining
   */
  public SubrModlExporter addStateVariable(String name, String dtaIx, String description,
      double modelValue) {
    stateVariables.add(new StateVariable(name, dtaIx, description, modelValue));
    if (!indexTable.contains(dtaIx)) {
      indexTable.add(dtaIx);
    }
    return this;
  }

  /**
   * Add an index table entry.
   *
   * @param dtaIx data index to add
   * @return this exporter for chaining
   */
  public SubrModlExporter addIndexEntry(String dtaIx) {
    if (!indexTable.contains(dtaIx)) {
      indexTable.add(dtaIx);
    }
    return this;
  }

  /**
   * Export the model configuration in SubrModl format.
   *
   * <p>
   * This generates a configuration file compatible with industrial nonlinear MPC systems using the
   * SubrProc/SubrModl pattern.
   * </p>
   *
   * @param filename the output filename
   * @throws IOException if writing fails
   */
  public void exportConfiguration(String filename) throws IOException {
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
      // Write model section (for simulation)
      writer.write("# " + modelName + " Configuration");
      writer.newLine();
      writer.write("# Generated by NeqSim SubrModlExporter");
      writer.newLine();
      writer.newLine();

      // Process section (for simulation)
      writer.write(String.format("%12s:    %s", modelName + "Proc", applicationName));
      writer.newLine();

      for (ModelParameter param : parameters) {
        writer.write(String.format("%16s=  %s", param.getName(), formatValue(param.getValue())));
        writer.newLine();
      }
      writer.newLine();

      // SubrXvr definitions
      for (SubrXvr xvr : subrXvrs) {
        writer.write(String.format("  SubrXvr:       %s", xvr.getName()));
        writer.newLine();
        writer.write(String.format("         Text1=  \"%s\"", xvr.getText1()));
        writer.newLine();
        writer.write(String.format("         Text2=  \"%s\"", xvr.getText2()));
        writer.newLine();
        writer.write(String.format("         DtaIx=  \"%s\"", xvr.getDtaIx()));
        writer.newLine();
        writer.write(String.format("          Init=  %s", formatValue(xvr.getInit())));
        writer.newLine();
        writer.newLine();
      }

      // Model section (for MPC)
      writer.write("# Model definition for MPC");
      writer.newLine();
      writer.write(String.format("%12s:     %s", modelName, applicationName + "_model"));
      writer.newLine();

      for (ModelParameter param : parameters) {
        writer.write(String.format("%16s=  %s", param.getName(), formatValue(param.getValue())));
        writer.newLine();
      }
      writer.newLine();

      // State variables
      if (!stateVariables.isEmpty()) {
        writer.write("# State Variables (SVR)");
        writer.newLine();
        for (StateVariable svr : stateVariables) {
          writer.write(String.format("  SubrXvr:       %s", svr.getName()));
          writer.newLine();
          writer.write(String.format("         Text1=  \"%s\"", svr.getDescription()));
          writer.newLine();
          writer.write(String.format("         DtaIx=  \"%s\"", svr.getDtaIx()));
          writer.newLine();
          writer.write(String.format("          Init=  %s", formatValue(svr.getModelValue())));
          writer.newLine();
          writer.newLine();
        }
      }
    }
  }

  /**
   * Export MPC configuration with nonlinear parameters.
   *
   * <p>
   * This generates the SmpcAppl configuration section with tuning parameters for nonlinear MPC.
   * </p>
   *
   * @param filename the output filename
   * @param useNonlinear whether to use nonlinear (SQP) or linear (QP) solver
   * @throws IOException if writing fails
   */
  public void exportMPCConfiguration(String filename, boolean useNonlinear) throws IOException {
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
      writer.write("# MPC Configuration");
      writer.newLine();
      writer.write("# Generated by NeqSim SubrModlExporter");
      writer.newLine();
      writer.newLine();

      // SmpcAppl section
      writer.write("SmpcAppl:    " + applicationName);
      writer.newLine();
      writer.write(String.format("     UpdFilt=  %s", "0.0"));
      writer.newLine();
      writer.write(String.format("    OpenFlag=  %s", "OPTMVR"));
      writer.newLine();
      writer.write(String.format("     FailMax=  %s", "0"));
      writer.newLine();

      if (useNonlinear) {
        // Nonlinear MPC parameters
        writer.write(String.format("     IterOpt=  %s", "ON"));
        writer.newLine();
        writer.write(String.format(" IterNewSens=  %s", "ON"));
        writer.newLine();
        writer.write(String.format("   IterQpMax=  %s", "10"));
        writer.newLine();
        writer.write(String.format(" IterLineMax=  %s", "10"));
        writer.newLine();
        writer.write(String.format("SteadySolver=  %s", "SQP"));
        writer.newLine();
        writer.write(String.format("    MajItLim=  %s", "200"));
        writer.newLine();
        writer.write(String.format("    MajPrint=  %s", "0"));
        writer.newLine();
        writer.write(String.format(" VerifyGrads=  %s", "OFF"));
        writer.newLine();
        writer.write(String.format("    FuncPrec=  %s", "1e-08"));
        writer.newLine();
        writer.write(String.format("       FeTol=  %s", "1e-03"));
        writer.newLine();
        writer.write(String.format("    OptimTol=  %s", "1e-05"));
        writer.newLine();
        writer.write(String.format("    FdifIntv=  %s", "0.001"));
        writer.newLine();
        writer.write(String.format("  MaxSeconds=  %s", "10"));
        writer.newLine();
        writer.write(String.format("      LmPrio=  %s", "100"));
        writer.newLine();
        writer.write(String.format("  MaxPrioSQP=  %s", "100"));
        writer.newLine();
        writer.write(String.format("     RelPert=  %s", "0.2"));
        writer.newLine();
        writer.write(String.format("   EachParam=  %s", "ON"));
        writer.newLine();
        writer.write(String.format(" LinErrorLim=  %s", "0.2"));
        writer.newLine();
        writer.write(String.format("   SensLimSS=  %s", "1e-06"));
        writer.newLine();
        writer.write(String.format("  SensLimDyn=  %s", "1e-06"));
        writer.newLine();
      } else {
        // Linear MPC parameters
        writer.write(String.format("SteadySolver=  %s", "QP"));
        writer.newLine();
        writer.write(String.format("     IterOpt=  %s", "OFF"));
        writer.newLine();
      }
      writer.newLine();
    }
  }

  /**
   * Export the index table (IxId) for C++ code generation.
   *
   * @param filename the output filename
   * @throws IOException if writing fails
   */
  public void exportIndexTable(String filename) throws IOException {
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
      writer.write("// Index table for " + modelName);
      writer.newLine();
      writer.write("// Generated by NeqSim SubrModlExporter");
      writer.newLine();
      writer.newLine();

      writer.write("char* " + modelName + "::IxId[] = {");
      writer.newLine();
      writer.write("    ");

      for (int i = 0; i < indexTable.size(); i++) {
        writer.write("\"" + indexTable.get(i) + "\"");
        if (i < indexTable.size() - 1) {
          writer.write(", ");
        }
        if ((i + 1) % 8 == 0 && i < indexTable.size() - 1) {
          writer.newLine();
          writer.write("    ");
        }
      }
      writer.write(", \"\"};");
      writer.newLine();
    }
  }

  /**
   * Export as JSON for programmatic use.
   *
   * @param filename the output filename
   * @throws IOException if writing fails
   */
  public void exportJSON(String filename) throws IOException {
    Map<String, Object> model = new LinkedHashMap<>();
    model.put("modelName", modelName);
    model.put("applicationName", applicationName);
    model.put("sampleTime", sampleTime);

    // Parameters
    List<Map<String, Object>> paramList = new ArrayList<>();
    for (ModelParameter param : parameters) {
      Map<String, Object> p = new LinkedHashMap<>();
      p.put("name", param.getName());
      p.put("value", param.getValue());
      p.put("unit", param.getUnit());
      p.put("description", param.getDescription());
      paramList.add(p);
    }
    model.put("parameters", paramList);

    // SubrXvrs
    List<Map<String, Object>> xvrList = new ArrayList<>();
    for (SubrXvr xvr : subrXvrs) {
      Map<String, Object> x = new LinkedHashMap<>();
      x.put("name", xvr.getName());
      x.put("dtaIx", xvr.getDtaIx());
      x.put("text1", xvr.getText1());
      x.put("text2", xvr.getText2());
      x.put("init", xvr.getInit());
      x.put("unit", xvr.getUnit());
      xvrList.add(x);
    }
    model.put("subrXvrs", xvrList);

    // State variables
    List<Map<String, Object>> svrList = new ArrayList<>();
    for (StateVariable svr : stateVariables) {
      Map<String, Object> s = new LinkedHashMap<>();
      s.put("name", svr.getName());
      s.put("dtaIx", svr.getDtaIx());
      s.put("description", svr.getDescription());
      s.put("modelValue", svr.getModelValue());
      s.put("measValue", svr.getMeasValue());
      s.put("measured", svr.isMeasured());
      svrList.add(s);
    }
    model.put("stateVariables", svrList);

    // Index table
    model.put("indexTable", indexTable);

    Gson gson =
        new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
      writer.write(gson.toJson(model));
    }
  }

  /**
   * Auto-populate from ProcessLinkedMPC controller.
   *
   * <p>
   * This automatically extracts MVs, CVs, DVs from the MPC controller and creates corresponding
   * SubrXvr definitions.
   * </p>
   *
   * @param controller the MPC controller
   * @return this exporter for chaining
   */
  public SubrModlExporter populateFromMPC(ProcessLinkedMPC controller) {
    // Add MVRs as SubrXvrs
    for (ManipulatedVariable mv : controller.getManipulatedVariables()) {
      String dtaIx = generateDtaIx(mv.getName());
      addSubrXvr(mv.getName(), dtaIx, mv.getDescription(), mv.getCurrentValue());
    }

    // Add CVRs as SubrXvrs
    for (ControlledVariable cv : controller.getControlledVariables()) {
      String dtaIx = generateDtaIx(cv.getName());
      addSubrXvr(cv.getName(), dtaIx, cv.getDescription(), cv.getCurrentValue());
    }

    // Add DVRs as SubrXvrs
    for (DisturbanceVariable dv : controller.getDisturbanceVariables()) {
      String dtaIx = generateDtaIx(dv.getName());
      addSubrXvr(dv.getName(), dtaIx, dv.getDescription(), dv.getCurrentValue());
    }

    return this;
  }

  /**
   * Generate a data index from a variable name.
   *
   * @param name the variable name
   * @return the data index (lowercase, no spaces)
   */
  private String generateDtaIx(String name) {
    return name.toLowerCase().replaceAll("[^a-z0-9]", "_");
  }

  /**
   * Format a numeric value for output.
   *
   * @param value the value
   * @return formatted string
   */
  private String formatValue(double value) {
    if (value == Math.floor(value) && value < 1e10) {
      return String.format("%.0f", value);
    } else {
      return String.format("%.6g", value);
    }
  }

  /**
   * Get the model parameters.
   *
   * @return list of parameters
   */
  public List<ModelParameter> getParameters() {
    return new ArrayList<>(parameters);
  }

  /**
   * Get the SubrXvr definitions.
   *
   * @return list of SubrXvrs
   */
  public List<SubrXvr> getSubrXvrs() {
    return new ArrayList<>(subrXvrs);
  }

  /**
   * Get the state variables.
   *
   * @return list of state variables
   */
  public List<StateVariable> getStateVariables() {
    return new ArrayList<>(stateVariables);
  }

  /**
   * Get the index table.
   *
   * @return list of index entries
   */
  public List<String> getIndexTable() {
    return new ArrayList<>(indexTable);
  }
}
