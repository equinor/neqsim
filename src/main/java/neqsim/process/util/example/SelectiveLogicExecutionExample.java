package neqsim.process.util.example;

import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ControlValve;
import neqsim.process.equipment.valve.ESDValve;
import neqsim.process.equipment.valve.HIPPSValve;
import neqsim.process.logic.ProcessLogic;
import neqsim.process.logic.action.CloseValveAction;
import neqsim.process.logic.action.EnergizeESDValveAction;
import neqsim.process.logic.esd.ESDLogic;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.ProcessSafetyScenario;
import neqsim.process.util.scenario.ProcessScenarioRunner;
import neqsim.process.util.scenario.ScenarioExecutionSummary;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * Example demonstrating selective logic execution in ProcessScenarioRunner.
 * 
 * <p>
 * Shows how to:
 * <ul>
 * <li>Run scenarios with only specific logic sequences enabled</li>
 * <li>Test safety systems independently (HIPPS vs ESD)</li>
 * <li>Dynamically add/remove logic between scenarios</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class SelectiveLogicExecutionExample {

  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    System.out.println("╔════════════════════════════════════════════════════════════════╗");
    System.out.println("║    SELECTIVE LOGIC EXECUTION EXAMPLE                           ║");
    System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

    // Build simple process system
    ProcessSystem system = buildSimpleSystem();

    // Create logic sequences
    HIPPSValve hippsValve = (HIPPSValve) system.getUnit("HIPPS-001");
    ControlValve inletValve = (ControlValve) system.getUnit("Inlet Valve");
    ESDValve esdValve = (ESDValve) system.getUnit("ESD Valve");

    ESDLogic hippsLogic = new ESDLogic("HIPPS Protection");
    hippsLogic.addAction(new CloseValveAction(hippsValve), 0.0);

    ESDLogic esdLogic = new ESDLogic("ESD Level 1");
    esdLogic.addAction(new CloseValveAction(inletValve), 0.0);
    esdLogic.addAction(new EnergizeESDValveAction(esdValve, 100.0), 0.5);

    // Create scenario runner and register ALL logic
    ProcessScenarioRunner runner = new ProcessScenarioRunner(system);
    runner.addLogic(hippsLogic);
    runner.addLogic(esdLogic);

    System.out.println("Registered logic sequences:");
    for (ProcessLogic logic : runner.getLogicSequences()) {
      System.out.println("  - " + logic.getName());
    }
    System.out.println();

    // Scenario 1: Test HIPPS independently
    String separator = new String(new char[70]).replace("\0", "=");
    System.out.println("\n" + separator);
    System.out.println("SCENARIO 1: HIPPS PROTECTION ONLY");
    System.out.println(separator);

    ProcessSafetyScenario highPressure =
        ProcessSafetyScenario.builder("High Pressure").customManipulator("Feed", equipment -> {
          if (equipment instanceof Stream) {
            ((Stream) equipment).setPressure(75.0, "bara");
          }
        }).build();

    runner.activateLogic("HIPPS Protection");
    ScenarioExecutionSummary summary1 = runner.runScenarioWithLogic("HIPPS Only Test", highPressure,
        15.0, 1.0, java.util.Collections.singletonList("HIPPS Protection"));
    summary1.printResults();
    runner.reset();

    // Scenario 2: Test ESD independently
    System.out.println("\n" + separator);
    System.out.println("SCENARIO 2: ESD SYSTEM ONLY");
    System.out.println(separator);

    runner.activateLogic("ESD Level 1");
    ScenarioExecutionSummary summary2 = runner.runScenarioWithLogic("ESD Only Test", null, 15.0,
        1.0, java.util.Collections.singletonList("ESD Level 1"));
    summary2.printResults();
    runner.reset();

    // Scenario 3: Test both systems together
    System.out.println("\n" + separator);
    System.out.println("SCENARIO 3: BOTH HIPPS AND ESD");
    System.out.println(separator);

    runner.activateLogic("HIPPS Protection");
    runner.activateLogic("ESD Level 1");
    ScenarioExecutionSummary summary3 =
        runner.runScenario("Combined Test", highPressure, 15.0, 1.0); // null = all logic
    summary3.printResults();

    System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
    System.out.println("║    SELECTIVE LOGIC EXECUTION DEMO COMPLETED                    ║");
    System.out.println("╚════════════════════════════════════════════════════════════════╝");
  }

  private static ProcessSystem buildSimpleSystem() {
    ProcessSystem system = new ProcessSystem();

    // Create feed
    SystemInterface feedGas = new SystemSrkEos(298.15, 55.0);
    feedGas.addComponent("methane", 90.0);
    feedGas.addComponent("ethane", 10.0);
    feedGas.setMixingRule(2);

    Stream feed = new Stream("Feed", feedGas);
    feed.setFlowRate(10000.0, "kg/hr");
    feed.setPressure(55.0, "bara");
    feed.setTemperature(25.0, "C");

    // Inlet valve
    ControlValve inletValve = new ControlValve("Inlet Valve", feed);
    inletValve.setPercentValveOpening(80.0);
    inletValve.setCv(400.0);

    // HIPPS valve
    HIPPSValve hippsValve = new HIPPSValve("HIPPS-001", inletValve.getOutletStream());
    hippsValve.setPercentValveOpening(100.0);
    hippsValve.setCv(300.0);
    hippsValve.setSILRating(2);

    // Separator
    Separator separator = new Separator("Separator", hippsValve.getOutletStream());
    separator.setCalculateSteadyState(false);

    // ESD valve
    ESDValve esdValve = new ESDValve("ESD Valve", separator.getGasOutStream());
    esdValve.setPercentValveOpening(0.0);
    esdValve.deEnergize();

    system.add(feed);
    system.add(inletValve);
    system.add(hippsValve);
    system.add(separator);
    system.add(esdValve);

    System.out.println(
        "Built simple process system with " + system.getUnitOperations().size() + " units\n");

    return system;
  }
}
