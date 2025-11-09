package neqsim.process.util.example;

import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.logic.LogicAction;
import neqsim.process.logic.LogicCondition;
import neqsim.process.logic.esd.ESDLogic;
import neqsim.process.logic.startup.StartupLogic;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

import java.util.*;

/**
 * Example demonstrating how to create process logic from external configuration without any
 * pre-compilation - logic is loaded from text/JSON-like format at runtime.
 * 
 * <p>
 * This demonstrates:
 * </p>
 * <ul>
 * <li>Loading logic sequences from configuration files</li>
 * <li>Creating actions and conditions from string descriptions</li>
 * <li>Complete runtime flexibility for logic definition</li>
 * <li>No need to recompile when logic changes</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class ConfigurableLogicExample {

  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    System.out.println("╔════════════════════════════════════════════════════════════════╗");
    System.out.println("║         CONFIGURABLE PROCESS LOGIC EXAMPLE                     ║");
    System.out.println("║         (Logic Loaded from Configuration at Runtime)           ║");
    System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

    // Create a simple process system
    ProcessSystem system = createSimpleProcess();

    // Get equipment references for logic factory
    Map<String, ProcessEquipmentInterface> equipment = createEquipmentMap(system);

    // Create logic factory
    LogicFactory factory = new LogicFactory(equipment);

    // Example 1: Load logic from configuration strings
    demonstrateConfigStringLogic(factory);

    // Example 2: Load logic from simulated configuration file
    demonstrateConfigFileLogic(factory);

    // Example 3: Create logic from user input (simulated)
    demonstrateUserDefinedLogic(factory);

    System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
    System.out.println("║      CONFIGURABLE LOGIC CREATION COMPLETED SUCCESSFULLY        ║");
    System.out.println("╚════════════════════════════════════════════════════════════════╝");
  }

  /**
   * Creates a simple process for demonstration.
   */
  private static ProcessSystem createSimpleProcess() {
    ProcessSystem system = new ProcessSystem();

    // Create feed
    SystemInterface feedGas = new SystemSrkEos(298.15, 10.0);
    feedGas.addComponent("methane", 90.0);
    feedGas.addComponent("ethane", 10.0);
    feedGas.setMixingRule(2);

    Stream feed = new Stream("Feed", feedGas);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.setPressure(10.0, "bara");
    feed.setTemperature(25.0, "C");

    // Control valve
    ThrottlingValve valve1 = new ThrottlingValve("Control Valve", feed);
    valve1.setPercentValveOpening(50.0);
    valve1.setCv(100.0);

    // Second valve for more complex examples
    ThrottlingValve valve2 = new ThrottlingValve("Backup Valve", valve1.getOutletStream());
    valve2.setPercentValveOpening(100.0);
    valve2.setCv(80.0);

    // Test separator
    Separator separator = new Separator("Test Separator", valve2.getOutletStream());
    separator.setCalculateSteadyState(true);

    system.add(feed);
    system.add(valve1);
    system.add(valve2);
    system.add(separator);

    System.out
        .println("Process system created with " + system.getUnitOperations().size() + " units");
    return system;
  }

  /**
   * Creates equipment name-to-object mapping for logic factory.
   */
  private static Map<String, ProcessEquipmentInterface> createEquipmentMap(ProcessSystem system) {
    Map<String, ProcessEquipmentInterface> equipmentMap = new HashMap<>();

    for (ProcessEquipmentInterface unit : system.getUnitOperations()) {
      equipmentMap.put(unit.getName(), unit);
    }

    System.out.println("Equipment map created with " + equipmentMap.size() + " units");
    return equipmentMap;
  }

  /**
   * Demonstrates loading logic from configuration strings.
   */
  private static void demonstrateConfigStringLogic(LogicFactory factory) {
    System.out.println("\n=== EXAMPLE 1: CONFIGURATION STRING LOGIC ===");

    // Configuration string format: ACTION_TYPE:EQUIPMENT:PARAMETER:DELAY
    String[] esdConfig = {"VALVE_CLOSE:Control Valve:0:0.0", "VALVE_SET:Backup Valve:25.0:0.5",
        "SEPARATOR_MODE:Test Separator:transient:1.0"};

    ESDLogic configuredESD = factory.createESDFromConfig("Configured ESD", esdConfig);
    System.out.println("✓ Created ESD logic from configuration strings:");
    System.out.println("  - " + configuredESD.getActionCount() + " actions loaded");

    // Configuration string format: CONDITION_TYPE:EQUIPMENT:VALUE:OPERATOR
    String[] startupConfig = {"VALVE_POSITION:Control Valve:10.0:<",
        "VALVE_POSITION:Backup Valve:95.0:>", "TIMER:none:5.0:>"};

    String[] startupActions = {"VALVE_OPEN:Control Valve:100:0.0",
        "VALVE_SET:Control Valve:75.0:2.0", "SEPARATOR_MODE:Test Separator:steady:5.0"};

    StartupLogic configuredStartup =
        factory.createStartupFromConfig("Configured Startup", startupConfig, startupActions);
    System.out.println("✓ Created startup logic from configuration strings:");
    System.out.println("  - Name: " + configuredStartup.getName());
    System.out.println("  - Conditions: " + startupConfig.length);
    System.out.println("  - Actions: " + startupActions.length);
  }

  /**
   * Demonstrates loading logic from simulated configuration file.
   */
  private static void demonstrateConfigFileLogic(LogicFactory factory) {
    System.out.println("\n=== EXAMPLE 2: CONFIGURATION FILE LOGIC ===");

    // Simulate reading from configuration file
    String configFileContent = "# Emergency Shutdown Logic Configuration\n" + "LOGIC_TYPE=ESD\n"
        + "LOGIC_NAME=File Based ESD\n" + "ACTION_1=VALVE_CLOSE:Control Valve:0:0.0\n"
        + "ACTION_2=VALVE_CLOSE:Backup Valve:0:0.2\n"
        + "ACTION_3=SEPARATOR_MODE:Test Separator:transient:0.5\n" + "\n"
        + "# Startup Logic Configuration  \n" + "LOGIC_TYPE=STARTUP\n"
        + "LOGIC_NAME=File Based Startup\n" + "CONDITION_1=VALVE_POSITION:Control Valve:5.0:<\n"
        + "CONDITION_2=VALVE_POSITION:Backup Valve:5.0:<\n"
        + "ACTION_1=VALVE_SET:Control Valve:50.0:0.0\n"
        + "ACTION_2=VALVE_SET:Backup Valve:80.0:1.0\n"
        + "ACTION_3=SEPARATOR_MODE:Test Separator:steady:3.0\n";

    List<ProcessLogicConfig> configs = parseConfigFile(configFileContent);

    System.out.println("✓ Parsed " + configs.size() + " logic configurations from file");

    for (ProcessLogicConfig config : configs) {
      if ("ESD".equals(config.type)) {
        ESDLogic esdFromFile =
            factory.createESDFromConfig(config.name, config.actions.toArray(new String[0]));
        System.out.println("  - Created ESD: " + esdFromFile.getName() + " ("
            + esdFromFile.getActionCount() + " actions)");
      } else if ("STARTUP".equals(config.type)) {
        StartupLogic startupFromFile = factory.createStartupFromConfig(config.name,
            config.conditions.toArray(new String[0]), config.actions.toArray(new String[0]));
        System.out.println("  - Created Startup: " + startupFromFile.getName() + " (conditions: "
            + config.conditions.size() + ", actions: " + config.actions.size() + ")");
      }
    }
  }

  /**
   * Demonstrates creating logic from user input.
   */
  private static void demonstrateUserDefinedLogic(LogicFactory factory) {
    System.out.println("\n=== EXAMPLE 3: USER-DEFINED LOGIC ===");

    // Simulate user input (in real app, this might come from a GUI or command line)
    Scanner simulatedUserInput = new Scanner("My Custom Logic\n" + "3\n"
        + "Set Control Valve to 25%\n" + "VALVE_SET:Control Valve:25.0:0.0\n"
        + "Close Backup Valve\n" + "VALVE_CLOSE:Backup Valve:0:1.0\n"
        + "Switch separator to transient\n" + "SEPARATOR_MODE:Test Separator:transient:2.0\n");

    System.out.println("Collecting user input for custom logic...");

    System.out.print("Enter logic name: ");
    String logicName = simulatedUserInput.nextLine();

    System.out.print("Enter number of actions: ");
    int numActions = simulatedUserInput.nextInt();
    simulatedUserInput.nextLine(); // consume newline

    List<String> userActions = new ArrayList<>();
    for (int i = 0; i < numActions; i++) {
      System.out.print("Enter description for action " + (i + 1) + ": ");
      String description = simulatedUserInput.nextLine();

      System.out.print("Enter configuration for action " + (i + 1) + ": ");
      String config = simulatedUserInput.nextLine();

      userActions.add(config);
      System.out.println("  ✓ Added: " + description);
    }

    ESDLogic userDefinedLogic =
        factory.createESDFromConfig(logicName, userActions.toArray(new String[0]));

    System.out.println("✓ Created user-defined logic: " + userDefinedLogic.getName());
    System.out.println("  - Total actions: " + userDefinedLogic.getActionCount());
    simulatedUserInput.close();
  }

  /**
   * Parses configuration file content into logic configurations.
   */
  private static List<ProcessLogicConfig> parseConfigFile(String content) {
    List<ProcessLogicConfig> configs = new ArrayList<>();
    ProcessLogicConfig currentConfig = null;

    String[] lines = content.split("\n");
    for (String line : lines) {
      line = line.trim();
      if (line.isEmpty() || line.startsWith("#"))
        continue;

      if (line.startsWith("LOGIC_TYPE=")) {
        if (currentConfig != null) {
          configs.add(currentConfig);
        }
        currentConfig = new ProcessLogicConfig();
        currentConfig.type = line.split("=")[1];
      } else if (line.startsWith("LOGIC_NAME=") && currentConfig != null) {
        currentConfig.name = line.split("=")[1];
      } else if (line.startsWith("ACTION_") && currentConfig != null) {
        currentConfig.actions.add(line.split("=")[1]);
      } else if (line.startsWith("CONDITION_") && currentConfig != null) {
        currentConfig.conditions.add(line.split("=")[1]);
      }
    }

    if (currentConfig != null) {
      configs.add(currentConfig);
    }

    return configs;
  }

  /**
   * Factory class for creating logic from configurations.
   */
  private static class LogicFactory {
    private final Map<String, ProcessEquipmentInterface> equipment;

    public LogicFactory(Map<String, ProcessEquipmentInterface> equipment) {
      this.equipment = equipment;
    }

    public ESDLogic createESDFromConfig(String name, String[] actionConfigs) {
      ESDLogic logic = new ESDLogic(name);

      for (String config : actionConfigs) {
        LogicAction action = createActionFromConfig(config);
        double delay = extractDelayFromConfig(config);
        logic.addAction(action, delay);
      }

      return logic;
    }

    public StartupLogic createStartupFromConfig(String name, String[] conditionConfigs,
        String[] actionConfigs) {
      StartupLogic logic = new StartupLogic(name);

      // Add conditions
      for (String config : conditionConfigs) {
        LogicCondition condition = createConditionFromConfig(config);
        logic.addPermissive(condition);
      }

      // Add actions
      for (String config : actionConfigs) {
        LogicAction action = createActionFromConfig(config);
        double delay = extractDelayFromConfig(config);
        logic.addAction(action, delay);
      }

      return logic;
    }

    private LogicAction createActionFromConfig(String config) {
      String[] parts = config.split(":");
      String actionType = parts[0];
      String equipmentName = parts[1];
      String parameter = parts[2];

      ProcessEquipmentInterface targetEquipment = equipment.get(equipmentName);

      switch (actionType) {
        case "VALVE_CLOSE":
          return createValveCloseAction((ThrottlingValve) targetEquipment);
        case "VALVE_OPEN":
          return createValveOpenAction((ThrottlingValve) targetEquipment);
        case "VALVE_SET":
          return createValveSetAction((ThrottlingValve) targetEquipment,
              Double.parseDouble(parameter));
        case "SEPARATOR_MODE":
          return createSeparatorModeAction((Separator) targetEquipment, "steady".equals(parameter));
        default:
          throw new IllegalArgumentException("Unknown action type: " + actionType);
      }
    }

    private LogicCondition createConditionFromConfig(String config) {
      String[] parts = config.split(":");
      String conditionType = parts[0];
      String equipmentName = parts[1];
      String parameter = parts[2];
      String operator = parts[3];

      switch (conditionType) {
        case "VALVE_POSITION":
          ProcessEquipmentInterface targetEquipment = equipment.get(equipmentName);
          return createValvePositionCondition((ThrottlingValve) targetEquipment,
              Double.parseDouble(parameter), operator);
        case "TIMER":
          return createTimerCondition(Double.parseDouble(parameter));
        default:
          throw new IllegalArgumentException("Unknown condition type: " + conditionType);
      }
    }

    private double extractDelayFromConfig(String config) {
      String[] parts = config.split(":");
      return Double.parseDouble(parts[3]);
    }

    // Action factory methods
    private LogicAction createValveCloseAction(ThrottlingValve valve) {
      return new LogicAction() {
        private boolean executed = false;

        @Override
        public void execute() {
          if (!executed) {
            valve.setPercentValveOpening(0.0);
            executed = true;
          }
        }

        @Override
        public String getDescription() {
          return "Close valve " + valve.getName();
        }

        @Override
        public boolean isComplete() {
          return executed && valve.getPercentValveOpening() < 1.0;
        }

        @Override
        public String getTargetName() {
          return valve.getName();
        }
      };
    }

    private LogicAction createValveOpenAction(ThrottlingValve valve) {
      return new LogicAction() {
        private boolean executed = false;

        @Override
        public void execute() {
          if (!executed) {
            valve.setPercentValveOpening(100.0);
            executed = true;
          }
        }

        @Override
        public String getDescription() {
          return "Open valve " + valve.getName();
        }

        @Override
        public boolean isComplete() {
          return executed && valve.getPercentValveOpening() > 99.0;
        }

        @Override
        public String getTargetName() {
          return valve.getName();
        }
      };
    }

    private LogicAction createValveSetAction(ThrottlingValve valve, double targetOpening) {
      return new LogicAction() {
        private boolean executed = false;

        @Override
        public void execute() {
          if (!executed) {
            valve.setPercentValveOpening(targetOpening);
            executed = true;
          }
        }

        @Override
        public String getDescription() {
          return String.format("Set valve %s to %.1f%%", valve.getName(), targetOpening);
        }

        @Override
        public boolean isComplete() {
          return executed && Math.abs(valve.getPercentValveOpening() - targetOpening) < 1.0;
        }

        @Override
        public String getTargetName() {
          return valve.getName();
        }
      };
    }

    private LogicAction createSeparatorModeAction(Separator separator, boolean steadyState) {
      return new LogicAction() {
        private boolean executed = false;

        @Override
        public void execute() {
          if (!executed) {
            separator.setCalculateSteadyState(steadyState);
            executed = true;
          }
        }

        @Override
        public String getDescription() {
          return String.format("Set separator %s to %s mode", separator.getName(),
              steadyState ? "steady-state" : "transient");
        }

        @Override
        public boolean isComplete() {
          return executed;
        }

        @Override
        public String getTargetName() {
          return separator.getName();
        }
      };
    }

    // Condition factory methods
    private LogicCondition createValvePositionCondition(ThrottlingValve valve, double value,
        String operator) {
      return new LogicCondition() {
        @Override
        public boolean evaluate() {
          double currentPosition = valve.getPercentValveOpening();
          switch (operator) {
            case "<":
              return currentPosition < value;
            case "<=":
              return currentPosition <= value;
            case ">":
              return currentPosition > value;
            case ">=":
              return currentPosition >= value;
            case "==":
              return Math.abs(currentPosition - value) < 1.0;
            case "!=":
              return Math.abs(currentPosition - value) >= 1.0;
            default:
              return false;
          }
        }

        @Override
        public String getDescription() {
          return String.format("Valve %s position %s %.1f%%", valve.getName(), operator, value);
        }

        @Override
        public ProcessEquipmentInterface getTargetEquipment() {
          return valve;
        }

        @Override
        public String getCurrentValue() {
          return String.format("%.1f%%", valve.getPercentValveOpening());
        }

        @Override
        public String getExpectedValue() {
          return operator + value + "%";
        }
      };
    }

    private LogicCondition createTimerCondition(double seconds) {
      return new LogicCondition() {
        private final long startTime = System.currentTimeMillis();

        @Override
        public boolean evaluate() {
          double elapsed = (System.currentTimeMillis() - startTime) / 1000.0;
          return elapsed > seconds;
        }

        @Override
        public String getDescription() {
          return String.format("Timer > %.1f seconds", seconds);
        }

        @Override
        public ProcessEquipmentInterface getTargetEquipment() {
          return null;
        }

        @Override
        public String getCurrentValue() {
          double elapsed = (System.currentTimeMillis() - startTime) / 1000.0;
          return String.format("%.1fs", elapsed);
        }

        @Override
        public String getExpectedValue() {
          return ">" + seconds + "s";
        }
      };
    }
  }

  /**
   * Configuration container for parsed logic.
   */
  private static class ProcessLogicConfig {
    String type;
    String name;
    List<String> conditions = new ArrayList<>();
    List<String> actions = new ArrayList<>();
  }
}
