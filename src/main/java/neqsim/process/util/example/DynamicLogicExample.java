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

/**
 * Example demonstrating how to create process logic completely dynamically at runtime without any
 * pre-compiled logic sequences.
 * 
 * <p>
 * This shows:
 * </p>
 * <ul>
 * <li>Creating custom actions with lambda expressions</li>
 * <li>Creating custom conditions with anonymous classes</li>
 * <li>Building complex logic sequences programmatically</li>
 * <li>Runtime logic modification and adaptation</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class DynamicLogicExample {

  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    System.out.println("╔════════════════════════════════════════════════════════════════╗");
    System.out.println("║           DYNAMIC PROCESS LOGIC CREATION EXAMPLE               ║");
    System.out.println("║           (No Pre-Compilation Required)                        ║");
    System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

    // Create a simple process system
    ProcessSystem system = createSimpleProcess();

    // Get equipment references
    ThrottlingValve valve = (ThrottlingValve) system.getUnit("Control Valve");
    Separator separator = (Separator) system.getUnit("Test Separator");

    // Example 1: Create custom actions using anonymous classes
    demonstrateCustomActions(valve, separator);

    // Example 2: Create custom conditions
    demonstrateCustomConditions(valve, separator);

    // Example 3: Build complex logic sequences dynamically
    demonstrateDynamicLogicSequences(valve, separator);

    // Example 4: Runtime logic modification
    demonstrateRuntimeModification(valve, separator);

    System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
    System.out.println("║        DYNAMIC LOGIC CREATION COMPLETED SUCCESSFULLY           ║");
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
    ThrottlingValve valve = new ThrottlingValve("Control Valve", feed);
    valve.setPercentValveOpening(50.0);
    valve.setCv(100.0);

    // Test separator
    Separator separator = new Separator("Test Separator", valve.getOutletStream());
    separator.setCalculateSteadyState(true);

    system.add(feed);
    system.add(valve);
    system.add(separator);

    System.out
        .println("Simple process created with " + system.getUnitOperations().size() + " units");
    return system;
  }

  /**
   * Demonstrates creating custom actions at runtime.
   */
  private static void demonstrateCustomActions(ThrottlingValve valve, Separator separator) {
    System.out.println("\n=== EXAMPLE 1: CUSTOM ACTIONS ===");

    // Custom action using anonymous class
    LogicAction customAction1 = new LogicAction() {
      private boolean executed = false;

      @Override
      public void execute() {
        if (!executed) {
          valve.setPercentValveOpening(75.0);
          System.out.println("  Custom Action 1: Set valve to 75% opening");
          executed = true;
        }
      }

      @Override
      public String getDescription() {
        return "Custom throttle to 75%";
      }

      @Override
      public boolean isComplete() {
        return executed && Math.abs(valve.getPercentValveOpening() - 75.0) < 1.0;
      }

      @Override
      public String getTargetName() {
        return valve.getName();
      }
    };

    // Another custom action with more complex behavior
    LogicAction customAction2 = new LogicAction() {
      private boolean executed = false;
      private double startTime = -1;

      @Override
      public void execute() {
        if (!executed) {
          separator.setCalculateSteadyState(false); // Switch to transient
          startTime = System.currentTimeMillis() / 1000.0;
          System.out.println("  Custom Action 2: Switch separator to transient mode");
          executed = true;
        }
      }

      @Override
      public String getDescription() {
        return "Switch to transient calculation mode";
      }

      @Override
      public boolean isComplete() {
        if (!executed)
          return false;
        // Consider complete after 2 seconds (simulating transient startup time)
        double currentTime = System.currentTimeMillis() / 1000.0;
        return (currentTime - startTime) > 2.0;
      }

      @Override
      public String getTargetName() {
        return separator.getName();
      }
    };

    // Create logic sequence with custom actions
    ESDLogic customLogic = new ESDLogic("Custom Runtime Logic");
    customLogic.addAction(customAction1, 0.0);
    customLogic.addAction(customAction2, 1.0);

    System.out
        .println("✓ Created custom logic with " + customLogic.getActionCount() + " custom actions");
  }

  /**
   * Demonstrates creating custom conditions at runtime.
   */
  private static void demonstrateCustomConditions(ThrottlingValve valve, Separator separator) {
    System.out.println("\n=== EXAMPLE 2: CUSTOM CONDITIONS ===");

    // Custom condition using anonymous class
    LogicCondition customCondition1 = new LogicCondition() {
      @Override
      public boolean evaluate() {
        double opening = valve.getPercentValveOpening();
        return opening >= 70.0 && opening <= 80.0; // Valve in acceptable range
      }

      @Override
      public String getDescription() {
        return "Valve opening between 70-80%";
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
        return "70-80%";
      }
    };

    // Custom condition with time-based logic
    LogicCondition customCondition2 = new LogicCondition() {
      private final double creationTime = System.currentTimeMillis() / 1000.0;

      @Override
      public boolean evaluate() {
        double currentTime = System.currentTimeMillis() / 1000.0;
        return (currentTime - creationTime) > 5.0; // 5 seconds have passed
      }

      @Override
      public String getDescription() {
        return "5 seconds elapsed since creation";
      }

      @Override
      public ProcessEquipmentInterface getTargetEquipment() {
        return null; // Time-based, not equipment specific
      }

      @Override
      public String getCurrentValue() {
        double elapsed = System.currentTimeMillis() / 1000.0 - creationTime;
        return String.format("%.1fs", elapsed);
      }

      @Override
      public String getExpectedValue() {
        return ">5.0s";
      }
    };

    // Create startup logic with custom conditions
    StartupLogic startupWithCustomConditions = new StartupLogic("Startup with Custom Conditions");
    startupWithCustomConditions.addPermissive(customCondition1);
    startupWithCustomConditions.addPermissive(customCondition2);

    System.out.println("✓ Created startup logic with custom permissive conditions");
    System.out.println("  - Condition 1: " + customCondition1.getDescription());
    System.out.println("  - Condition 2: " + customCondition2.getDescription());
  }

  /**
   * Demonstrates building complex logic sequences completely at runtime.
   *
   * @param valve the throttling valve to control
   * @param separator the separator to monitor
   */
  private static void demonstrateDynamicLogicSequences(ThrottlingValve valve, Separator separator) {
    System.out.println("\n=== EXAMPLE 3: DYNAMIC LOGIC SEQUENCES ===");

    // Create a dynamic configuration for logic
    String[] scenarios = {"Normal Operation", "High Flow", "Emergency"};
    double[] valveSettings = {60.0, 85.0, 10.0};

    for (int i = 0; i < scenarios.length; i++) {
      final String scenario = scenarios[i];
      final double setting = valveSettings[i];

      // Create scenario-specific logic
      ESDLogic dynamicLogic = new ESDLogic("Dynamic " + scenario + " Logic");

      // Add dynamic action based on scenario
      LogicAction dynamicAction = new LogicAction() {
        private boolean executed = false;

        @Override
        public void execute() {
          if (!executed) {
            valve.setPercentValveOpening(setting);
            System.out.println("    " + scenario + ": Set valve to " + setting + "%");
            executed = true;
          }
        }

        @Override
        public String getDescription() {
          return scenario + " valve setting: " + setting + "%";
        }

        @Override
        public boolean isComplete() {
          return executed;
        }

        @Override
        public String getTargetName() {
          return valve.getName();
        }
      };

      dynamicLogic.addAction(dynamicAction, 0.0);
      System.out.println("✓ Created: " + dynamicLogic.getName());
    }
  }

  /**
   * Demonstrates modifying logic sequences at runtime.
   */
  private static void demonstrateRuntimeModification(ThrottlingValve valve, Separator separator) {
    System.out.println("\n=== EXAMPLE 4: RUNTIME LOGIC MODIFICATION ===");

    // Create base logic
    ESDLogic modifiableLogic = new ESDLogic("Modifiable Logic");

    // Add initial action
    LogicAction initialAction = new LogicAction() {
      private boolean executed = false;

      @Override
      public void execute() {
        if (!executed) {
          valve.setPercentValveOpening(50.0);
          System.out.println("    Initial: Set valve to 50%");
          executed = true;
        }
      }

      @Override
      public String getDescription() {
        return "Initial valve setting";
      }

      @Override
      public boolean isComplete() {
        return executed;
      }

      @Override
      public String getTargetName() {
        return valve.getName();
      }
    };

    modifiableLogic.addAction(initialAction, 0.0);
    System.out.println("✓ Created base logic with " + modifiableLogic.getActionCount() + " action");

    // Simulate runtime decision to add more actions
    boolean addEmergencyAction = true; // This could be based on runtime conditions

    if (addEmergencyAction) {
      LogicAction emergencyAction = new LogicAction() {
        private boolean executed = false;

        @Override
        public void execute() {
          if (!executed) {
            valve.setPercentValveOpening(5.0);
            separator.setCalculateSteadyState(false);
            System.out.println("    Emergency: Valve to 5%, separator to transient");
            executed = true;
          }
        }

        @Override
        public String getDescription() {
          return "Emergency shutdown sequence";
        }

        @Override
        public boolean isComplete() {
          return executed;
        }

        @Override
        public String getTargetName() {
          return valve.getName() + " & " + separator.getName();
        }
      };

      modifiableLogic.addAction(emergencyAction, 2.0);
      System.out.println("✓ Runtime modification: Added emergency action");
      System.out.println("  Final logic has " + modifiableLogic.getActionCount() + " actions");
    }

    // You could also create entirely new logic based on runtime conditions
    String runtimeScenario = determineRuntimeScenario(); // Simulate runtime decision
    ESDLogic adaptiveLogic = createAdaptiveLogic(runtimeScenario, valve, separator);
    System.out.println("✓ Created adaptive logic for scenario: " + runtimeScenario);
    System.out.println("  Adaptive logic has " + adaptiveLogic.getActionCount() + " actions");
  }

  /**
   * Simulates determining what scenario to use at runtime.
   */
  private static String determineRuntimeScenario() {
    // In real implementation, this might check:
    // - Process conditions
    // - Equipment status
    // - External signals
    // - Time of day
    // - Safety system status
    return "High Pressure Response";
  }

  /**
   * Creates adaptive logic based on runtime scenario.
   */
  private static ESDLogic createAdaptiveLogic(String scenario, ThrottlingValve valve,
      Separator separator) {
    ESDLogic adaptiveLogic = new ESDLogic("Adaptive " + scenario);

    // Different logic based on scenario
    switch (scenario) {
      case "High Pressure Response":
        adaptiveLogic.addAction(createAdaptiveAction("Close valve rapidly", valve, 5.0), 0.0);
        adaptiveLogic.addAction(createAdaptiveAction("Switch to transient", separator, -1), 0.5);
        break;
      case "Fire Emergency":
        adaptiveLogic.addAction(createAdaptiveAction("Emergency valve closure", valve, 0.0), 0.0);
        break;
      default:
        adaptiveLogic.addAction(createAdaptiveAction("Standard response", valve, 25.0), 0.0);
    }

    return adaptiveLogic;
  }

  /**
   * Factory method for creating adaptive actions.
   */
  private static LogicAction createAdaptiveAction(String description,
      ProcessEquipmentInterface equipment, double parameter) {
    return new LogicAction() {
      private boolean executed = false;

      @Override
      public void execute() {
        if (!executed) {
          if (equipment instanceof ThrottlingValve && parameter >= 0) {
            ((ThrottlingValve) equipment).setPercentValveOpening(parameter);
          } else if (equipment instanceof Separator) {
            ((Separator) equipment).setCalculateSteadyState(false);
          }
          System.out.println("    Adaptive Action: " + description);
          executed = true;
        }
      }

      @Override
      public String getDescription() {
        return description;
      }

      @Override
      public boolean isComplete() {
        return executed;
      }

      @Override
      public String getTargetName() {
        return equipment.getName();
      }
    };
  }
}
