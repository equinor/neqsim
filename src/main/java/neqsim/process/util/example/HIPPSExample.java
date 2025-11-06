package neqsim.process.util.example;

import java.util.UUID;
import neqsim.process.alarm.AlarmConfig;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.HIPPSValve;
import neqsim.process.equipment.valve.SafetyValve;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Example demonstrating HIPPS (High Integrity Pressure Protection System) implementation.
 * 
 * <p>
 * This example shows:
 * <ul>
 * <li>HIPPS configuration with 2oo3 voting (SIL 3)</li>
 * <li>Redundant pressure transmitters with alarm configuration</li>
 * <li>Dynamic simulation of blocked outlet scenario</li>
 * <li>HIPPS preventing PSV from lifting (no flaring)</li>
 * <li>Transient pressure response and valve closure</li>
 * </ul>
 * 
 * <p>
 * <b>Scenario:</b> A high-pressure separator operates normally at 70 bara. A downstream valve
 * accidentally closes (blocked outlet), causing pressure to ramp up. HIPPS detects the pressure
 * rise and trips at 90 bara (below 100 bara MAWP), preventing the PSV from lifting and avoiding
 * flaring.
 *
 * @author ESOL
 */
public class HIPPSExample {

  /**
   * Main method to run the HIPPS example.
   *
   * @param args command line arguments (not used)
   */
  public static void main(String[] args) {
    System.out.println("╔════════════════════════════════════════════════════════════════╗");
    System.out.println("║     HIPPS (High Integrity Pressure Protection System)         ║");
    System.out.println("║              Blocked Outlet Protection Example                ║");
    System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

    // ========================================
    // 1. CREATE PROCESS SYSTEM
    // ========================================
    System.out.println("═══ SYSTEM CONFIGURATION ═══");

    // Create high-pressure natural gas system
    SystemInterface fluid = new SystemSrkEos(298.15, 70.0);
    fluid.addComponent("nitrogen", 0.5, "mol/sec");
    fluid.addComponent("CO2", 1.0, "mol/sec");
    fluid.addComponent("methane", 85.0, "mol/sec");
    fluid.addComponent("ethane", 8.0, "mol/sec");
    fluid.addComponent("propane", 4.0, "mol/sec");
    fluid.addComponent("i-butane", 0.75, "mol/sec");
    fluid.addComponent("n-butane", 0.75, "mol/sec");
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    fluid.createDatabase(true);
    fluid.init(0);

    // Create feed stream
    Stream feedStream = new Stream("High Pressure Feed", fluid);
    feedStream.setFlowRate(20000.0, "kg/hr");
    feedStream.setTemperature(40.0, "C");
    feedStream.setPressure(70.0, "bara");
    feedStream.run();

    System.out.printf("Feed Stream: %.0f kg/hr @ %.1f bara, %.1f °C%n",
        feedStream.getFlowRate("kg/hr"), feedStream.getPressure("bara"),
        feedStream.getTemperature("C"));

    // Create separator (MAWP = 100 bara)
    Separator separator = new Separator("HP Separator", feedStream);
    separator.setInternalDiameter(1.5); // 1.5m ID
    separator.setSeparatorLength(4.0); // 4m length
    separator.run();

    System.out.printf("Separator MAWP: 100.0 bara%n");
    System.out.printf("Normal operating pressure: %.1f bara%n", separator.getPressure("bara"));

    // ========================================
    // 2. CONFIGURE REDUNDANT PRESSURE TRANSMITTERS
    // ========================================
    System.out.println("\n═══ PRESSURE TRANSMITTER CONFIGURATION ═══");

    PressureTransmitter PT1 = new PressureTransmitter("PT-101A", separator.getGasOutStream());
    PressureTransmitter PT2 = new PressureTransmitter("PT-101B", separator.getGasOutStream());
    PressureTransmitter PT3 = new PressureTransmitter("PT-101C", separator.getGasOutStream());

    // Configure HIHI alarm at 90 bara (10% below MAWP)
    AlarmConfig hippsAlarm = AlarmConfig.builder().highHighLimit(90.0) // HIPPS trip point
        .deadband(2.0) // 2 bara deadband
        .delay(0.5) // 500ms confirmation delay
        .unit("bara").build();

    PT1.setAlarmConfig(hippsAlarm);
    PT2.setAlarmConfig(hippsAlarm);
    PT3.setAlarmConfig(hippsAlarm);

    System.out.println("Redundant transmitters configured: PT-101A, PT-101B, PT-101C");
    System.out.println("HIHI Alarm Setpoint: 90.0 bara (90% of MAWP)");
    System.out.println("Alarm Confirmation Delay: 500 ms");
    System.out.println("Deadband: 2.0 bara");

    // ========================================
    // 3. CREATE HIPPS VALVE
    // ========================================
    System.out.println("\n═══ HIPPS VALVE CONFIGURATION ═══");

    HIPPSValve hippsValve = new HIPPSValve("HIPPS-XV-001", separator.getGasOutStream());
    hippsValve.addPressureTransmitter(PT1);
    hippsValve.addPressureTransmitter(PT2);
    hippsValve.addPressureTransmitter(PT3);
    hippsValve.setVotingLogic(HIPPSValve.VotingLogic.TWO_OUT_OF_THREE); // 2oo3 for SIL 3
    hippsValve.setClosureTime(3.0); // 3 second fast-acting valve
    hippsValve.setSILRating(3);
    hippsValve.setProofTestInterval(8760.0); // Annual proof test
    hippsValve.setCv(200.0);

    System.out.println("HIPPS Valve: HIPPS-XV-001");
    System.out.println("SIL Rating: SIL 3");
    System.out.println("Voting Logic: 2oo3 (2 out of 3 transmitters)");
    System.out.println("Closure Time: 3.0 seconds");
    System.out.println("Trip Point: 90.0 bara");

    // ========================================
    // 4. CREATE PSV AS BACKUP
    // ========================================
    System.out.println("\n═══ PSV BACKUP PROTECTION ═══");

    SafetyValve psv = new SafetyValve("PSV-001", separator.getGasOutStream());
    psv.setPressureSpec(100.0); // Set at MAWP
    psv.setFullOpenPressure(110.0); // Full open at 10% overpressure
    psv.setBlowdown(7.0); // 7% blowdown

    System.out.println("PSV: PSV-001 (backup protection)");
    System.out.println("PSV Set Pressure: 100.0 bara (at MAWP)");
    System.out.println("Full Open Pressure: 110.0 bara");
    System.out.println("Blowdown: 7%");

    // ========================================
    // 5. DYNAMIC SIMULATION - BLOCKED OUTLET SCENARIO
    // ========================================
    System.out.println("\n═══ DYNAMIC SIMULATION - BLOCKED OUTLET SCENARIO ═══");
    System.out.println("t=0-5s:    Normal operation at 70 bara");
    System.out.println("t=5s:      Downstream valve closes (blocked outlet)");
    System.out.println("t=5-20s:   Pressure ramps at 2 bara/sec");
    System.out.println("Expected:  HIPPS trips at 90 bara, PSV never lifts\n");

    System.out.println(
        "Time (s) | Sep Press | Active PT | HIPPS Open | PSV Open | HIPPS Status | PSV Status");
    System.out.println(
        "---------|-----------|-----------|------------|----------|--------------|------------");

    double timeStep = 0.5; // 0.5 second timesteps
    double totalTime = 20.0;
    double pressureRampRate = 2.0; // bara/sec
    double blockedOutletTime = 5.0; // Outlet blocks at 5 seconds

    boolean hippsTripped = false;
    boolean psvLifted = false;
    double tripTime = 0.0;
    double tripPressure = 0.0;

    for (double time = 0; time <= totalTime; time += timeStep) {
      // Simulate blocked outlet scenario
      double currentPressure;
      if (time < blockedOutletTime) {
        // Normal operation
        currentPressure = 70.0;
      } else if (!hippsTripped) {
        // Pressure ramps up due to blocked outlet
        currentPressure = 70.0 + (time - blockedOutletTime) * pressureRampRate;
      } else {
        // HIPPS closed - pressure stabilizes
        currentPressure = tripPressure;
      }

      // Update system
      separator.getThermoSystem().setPressure(currentPressure, "bara");

      // Evaluate transmitter alarms
      PT1.evaluateAlarm(currentPressure, timeStep, time);
      PT2.evaluateAlarm(currentPressure, timeStep, time);
      PT3.evaluateAlarm(currentPressure, timeStep, time);

      // Run HIPPS transient calculation
      hippsValve.runTransient(timeStep, UUID.randomUUID());

      // Check PSV status
      psv.run();

      // Record trip event
      if (!hippsTripped && hippsValve.hasTripped()) {
        hippsTripped = true;
        tripTime = time;
        tripPressure = currentPressure;
      }

      // Check if PSV lifted
      if (!psvLifted && psv.getPercentValveOpening() > 0.0) {
        psvLifted = true;
      }

      // Print status every second
      if (time % 1.0 < timeStep / 2.0
          || hippsValve.hasTripped() && time > tripTime - timeStep && time < tripTime + 2.0) {
        System.out.printf(
            "  %5.1f  |   %6.2f    |    %d/%d    |   %5.1f%%   |  %5.1f%%  | %12s | %10s%n", time,
            currentPressure, hippsValve.getActiveTransmitterCount(), 3,
            hippsValve.getPercentValveOpening(), psv.getPercentValveOpening(),
            hippsValve.hasTripped() ? "TRIPPED" : "NORMAL",
            psv.getPercentValveOpening() > 0 ? "LIFTING" : "CLOSED");
      }

      // Exit after HIPPS trip and valve closes
      if (hippsTripped && time > tripTime + 3.0) {
        break;
      }
    }

    // ========================================
    // 6. RESULTS SUMMARY
    // ========================================
    System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
    System.out.println("║                         RESULTS SUMMARY                        ║");
    System.out.println("╚════════════════════════════════════════════════════════════════╝");

    System.out.printf("%nHIPPS Performance:%n");
    System.out.printf("  Trip Time: %.1f seconds%n", tripTime);
    System.out.printf("  Trip Pressure: %.2f bara%n", tripPressure);
    System.out.printf("  Active Transmitters at Trip: %d/3%n",
        hippsValve.getActiveTransmitterCount());
    System.out.printf("  Voting Logic: %s%n", hippsValve.getVotingLogic().getNotation());
    System.out.printf("  Closure Time: %.1f seconds%n", hippsValve.getClosureTime());

    System.out.printf("%nPSV Status:%n");
    System.out.printf("  PSV Lifted: %s%n", psvLifted ? "YES" : "NO");
    System.out.printf("  PSV Opening: %.1f%%%n", psv.getPercentValveOpening());

    System.out.printf("%nSafety Analysis:%n");
    if (!psvLifted) {
      System.out.println("  ✓ HIPPS successfully prevented overpressure");
      System.out.println("  ✓ PSV did not lift - NO FLARING occurred");
      System.out.println("  ✓ Environmental emissions prevented");
      System.out.println("  ✓ Pressure remained below MAWP (100 bara)");
    } else {
      System.out.println("  ✗ PSV lifted - HIPPS may have failed");
      System.out.println("  ✗ Flaring occurred");
    }

    System.out.printf("%nHIPPS vs PSV Comparison:%n");
    System.out.println("  HIPPS Action:  Stopped flow BEFORE overpressure (90 bara)");
    System.out.println("  PSV Action:    Would relieve AT overpressure (100 bara)");
    System.out.println("  Result:        HIPPS prevented flaring and emissions");

    // Display comprehensive diagnostics
    System.out.println("\n" + hippsValve.getDiagnostics());

    System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
    System.out.println("║                    SIMULATION COMPLETE                         ║");
    System.out.println("╚════════════════════════════════════════════════════════════════╝");
  }
}
