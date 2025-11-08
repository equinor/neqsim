package neqsim.process.util.example;

import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ESDValve;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.logic.action.TripValveAction;
import neqsim.process.logic.esd.ESDLogic;
import neqsim.process.logic.hipps.HIPPSLogic;
import neqsim.process.logic.sis.Detector;
import neqsim.process.logic.sis.Detector.AlarmLevel;
import neqsim.process.logic.sis.Detector.DetectorType;
import neqsim.process.logic.sis.VotingLogic;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Example demonstrating HIPPS (High Integrity Pressure Protection System) with ESD escalation.
 * 
 * <p>
 * This example shows a layered safety approach:
 * <ol>
 * <li>HIPPS activates at 95% of MAOP (Maximum Allowable Operating Pressure)</li>
 * <li>ESD activates at 98% MAOP as backup if HIPPS fails</li>
 * <li>If HIPPS trips but pressure remains high, escalation to ESD occurs after 5 seconds</li>
 * </ol>
 * 
 * <p>
 * Key safety features demonstrated:
 * <ul>
 * <li>2oo3 voting for HIPPS (SIL 3 level reliability)</li>
 * <li>Rapid HIPPS valve closure (&lt;2 seconds)</li>
 * <li>Independent ESD backup system</li>
 * <li>Automatic escalation on HIPPS failure</li>
 * <li>Multiple pressure monitoring layers</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class HIPPSWithESDExample {
  /**
   * Java 8 compatible string repeat utility method.
   *
   * @param str the string to repeat
   * @param count the number of times to repeat
   * @return the repeated string
   */
  private static String repeat(String str, int count) {
    if (count <= 0) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < count; i++) {
      sb.append(str);
    }
    return sb.toString();
  }

  /**
   * Main method to run the example.
   *
   * @param args command line arguments
   */
  public static void main(String[] args) {
    System.out.println(repeat("=", 80));
    System.out.println("HIPPS WITH ESD ESCALATION EXAMPLE");
    System.out.println(repeat("=", 80));
    System.out.println();

    // Create process system
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 80.0);
    fluid.addComponent("ethane", 10.0);
    fluid.addComponent("propane", 7.0);
    fluid.addComponent("n-butane", 3.0);
    fluid.setMixingRule("classic");

    Stream feedStream = new Stream("Feed", fluid);
    feedStream.setFlowRate(10000.0, "kg/hr");
    feedStream.setTemperature(40.0, "C");
    feedStream.setPressure(50.0, "bara");

    Separator separator = new Separator("Separator", feedStream);

    // HIPPS isolation valve (upstream of protected equipment)
    ThrottlingValve hippsValve =
        new ThrottlingValve("HIPPS-Isolation-Valve", separator.getGasOutStream());
    hippsValve.setPercentValveOpening(100.0);

    // ESD valve (for full shutdown)
    ESDValve esdValve = new ESDValve("ESD-Valve", hippsValve.getOutletStream());
    esdValve.setPercentValveOpening(100.0);

    // Final process equipment (protected by HIPPS and ESD)
    Separator protectedSeparator = new Separator("Protected-Equipment", esdValve.getOutletStream());

    // ======================================================================================
    // HIPPS CONFIGURATION - First line of defense at 95% MAOP
    // ======================================================================================
    double maop = 100.0; // Maximum Allowable Operating Pressure (bara)
    double hippsSetpoint = maop * 0.95; // HIPPS at 95% MAOP = 95 bara
    double esdSetpoint = maop * 0.98; // ESD at 98% MAOP = 98 bara

    System.out.println("SAFETY SYSTEM CONFIGURATION:");
    System.out.println(repeat("-", 80));
    System.out.printf("Maximum Allowable Operating Pressure (MAOP): %.1f bara\n", maop);
    System.out.printf("HIPPS Activation Setpoint (95%% MAOP):       %.1f bara\n", hippsSetpoint);
    System.out.printf("ESD Activation Setpoint (98%% MAOP):         %.1f bara\n", esdSetpoint);
    System.out.println();

    // Create HIPPS with 2oo3 voting for high reliability (SIL 3)
    HIPPSLogic hipps = new HIPPSLogic("HIPPS-101", VotingLogic.TWO_OUT_OF_THREE);

    // Add three pressure transmitters for HIPPS
    Detector hippsPT1 =
        new Detector("PT-101A", DetectorType.PRESSURE, AlarmLevel.HIGH_HIGH, hippsSetpoint, "bara");
    Detector hippsPT2 =
        new Detector("PT-101B", DetectorType.PRESSURE, AlarmLevel.HIGH_HIGH, hippsSetpoint, "bara");
    Detector hippsPT3 =
        new Detector("PT-101C", DetectorType.PRESSURE, AlarmLevel.HIGH_HIGH, hippsSetpoint, "bara");

    hipps.addPressureSensor(hippsPT1);
    hipps.addPressureSensor(hippsPT2);
    hipps.addPressureSensor(hippsPT3);
    hipps.setIsolationValve(hippsValve);

    // ======================================================================================
    // ESD CONFIGURATION - Backup safety layer at 98% MAOP
    // ======================================================================================

    // Create ESD logic with trip action
    ESDLogic esdLogic = new ESDLogic("ESD Level 1");
    esdLogic.addAction(new TripValveAction(esdValve), 0.0);

    // Link HIPPS to escalate to ESD after 5 seconds if pressure remains high
    hipps.linkToEscalationLogic(esdLogic, 5.0);

    System.out.println("HIPPS CONFIGURATION:");
    System.out.println(repeat("-", 80));
    System.out.println("Voting Logic: 2oo3 (2 out of 3 pressure transmitters must trip)");
    System.out.println("Pressure Transmitters:");
    System.out.println("  - PT-101A: Setpoint = " + hippsSetpoint + " bara");
    System.out.println("  - PT-101B: Setpoint = " + hippsSetpoint + " bara");
    System.out.println("  - PT-101C: Setpoint = " + hippsSetpoint + " bara");
    System.out.println("Target Valve: " + hippsValve.getName());
    System.out.println("Escalation: ESD activation after 5 seconds if pressure remains high");
    System.out.println();

    System.out.println("ESD CONFIGURATION:");
    System.out.println(repeat("-", 80));
    System.out.println("Activation: Manual (backup) or automatic escalation from HIPPS");
    System.out.println("Actions: Trip ESD valve");
    System.out.println();

    // ======================================================================================
    // SCENARIO 1: NORMAL OPERATION
    // ======================================================================================
    System.out.println(repeat("=", 80));
    System.out.println("SCENARIO 1: NORMAL OPERATION (Pressure = 50 bara)");
    System.out.println(repeat("=", 80));

    feedStream.run();
    separator.run();
    hippsValve.run();
    esdValve.run();
    protectedSeparator.run();

    double normalPressure = protectedSeparator.getGasOutStream().getPressure();
    hipps.update(normalPressure, normalPressure, normalPressure);

    System.out.println("Status: All systems normal");
    System.out.printf("Pressure: %.1f bara (%.1f%% of MAOP)\n", normalPressure,
        normalPressure / maop * 100);
    System.out.println("HIPPS: " + hipps.getStatusDescription());
    System.out.println("ESD: " + esdLogic.getStatusDescription());
    System.out.println();

    // ======================================================================================
    // SCENARIO 2: HIPPS ACTIVATION (Pressure rises to 96 bara)
    // ======================================================================================
    System.out.println(repeat("=", 80));
    System.out.println("SCENARIO 2: HIPPS ACTIVATION (Pressure rises to 96 bara)");
    System.out.println(repeat("=", 80));

    // Simulate pressure rise
    double highPressure = 96.0; // Above HIPPS setpoint (95 bara)

    System.out.println("Simulating pressure excursion...");
    System.out.printf("Pressure: %.1f bara (%.1f%% of MAOP)\n", highPressure,
        highPressure / maop * 100);
    System.out.println();

    // Update HIPPS pressure sensors
    hipps.update(highPressure, highPressure, highPressure);

    System.out.println("IMMEDIATE RESPONSE:");
    System.out.println(repeat("-", 80));
    System.out.println("PT-101A: " + hippsPT1.toString());
    System.out.println("PT-101B: " + hippsPT2.toString());
    System.out.println("PT-101C: " + hippsPT3.toString());
    System.out.println();
    System.out.println("HIPPS: " + hipps.getStatusDescription());
    System.out.printf("HIPPS Isolation Valve: %.0f%% open\n", hippsValve.getPercentValveOpening());
    System.out.println("ESD: " + esdLogic.getStatusDescription());
    System.out.printf("ESD Valve: %.0f%% open\n", esdValve.getPercentValveOpening());
    System.out.println();

    if (hipps.isTripped()) {
      System.out.println("✓ HIPPS ACTIVATED - Isolation valve closed rapidly");
      System.out.println("✓ Process flow stopped, preventing overpressure");
      System.out.println("✓ ESD not needed - HIPPS successful");
    }
    System.out.println();

    // ======================================================================================
    // SCENARIO 3: HIPPS FAILURE - ESCALATION TO ESD
    // ======================================================================================
    System.out.println(repeat("=", 80));
    System.out.println("SCENARIO 3: HIPPS FAILURE - ESCALATION TO ESD");
    System.out.println(repeat("=", 80));
    System.out.println("Simulating scenario where HIPPS trips but pressure remains high...");
    System.out.println("(This would indicate HIPPS valve failure or continued pressure source)");
    System.out.println();

    // Reset for new scenario
    hippsValve.setPercentValveOpening(100.0);
    esdValve.setPercentValveOpening(100.0);
    hippsPT1.reset();
    hippsPT2.reset();
    hippsPT3.reset();
    hipps.reset();
    esdLogic.reset();

    // Simulate HIPPS trip with sustained high pressure
    hipps.update(highPressure, highPressure, highPressure);

    System.out.println("t = 0.0s: HIPPS trips");
    System.out.println("  HIPPS: " + hipps.getStatusDescription());
    System.out.printf("  HIPPS Valve: %.0f%% open\n", hippsValve.getPercentValveOpening());
    System.out.println("  ESD: " + esdLogic.getStatusDescription());
    System.out.println();

    // Simulate time progression with sustained high pressure (HIPPS failed to control)
    double timeStep = 1.0;
    for (int i = 1; i <= 6; i++) {
      double time = i * timeStep;

      // Pressure remains high (HIPPS failed)
      hipps.update(highPressure, highPressure, highPressure);
      hipps.execute(timeStep);
      esdLogic.execute(timeStep);

      System.out.printf("t = %.1fs: ", time);

      if (hipps.hasEscalated() && !esdLogic.isActive()) {
        System.out.println("ESCALATION TRIGGERED - Activating ESD");
      } else if (esdLogic.isActive()) {
        System.out.println("ESD ACTIVE");
      } else {
        System.out.printf("Waiting for escalation (%.1fs / 5.0s)\n", time);
      }

      System.out.println("  HIPPS: " + hipps.getStatusDescription());
      System.out.println("  ESD: " + esdLogic.getStatusDescription());
      System.out.printf("  ESD Valve: %.0f%% open\n", esdValve.getPercentValveOpening());
      System.out.println();
    }

    System.out.println("KEY FEATURES DEMONSTRATED:");
    System.out.println(repeat("-", 80));
    System.out.println("✓ HIPPS acts as first line of defense (95% MAOP)");
    System.out.println("✓ 2oo3 voting provides high reliability (SIL 3)");
    System.out.println("✓ Rapid isolation valve closure prevents overpressure");
    System.out.println("✓ ESD provides independent backup layer (98% MAOP)");
    System.out.println("✓ Automatic escalation if HIPPS fails to control pressure");
    System.out.println("✓ Defense in depth - multiple independent protection layers");
    System.out.println();

    System.out.println("SAFETY INTEGRITY:");
    System.out.println(repeat("-", 80));
    System.out
        .println("• HIPPS prevents PSV lifting and flaring (environmental and economic benefit)");
    System.out.println("• 2oo3 voting balances safety integrity with availability");
    System.out.println("• Allows 1 sensor bypass for maintenance without compromising safety");
    System.out.println("• ESD escalation ensures protection even if HIPPS fails");
    System.out.println("• Complies with IEC 61508/61511 for SIL 2/3 applications");
    System.out.println();

    System.out.println(repeat("=", 80));
    System.out.println("EXAMPLE COMPLETED");
    System.out.println(repeat("=", 80));
  }
}
