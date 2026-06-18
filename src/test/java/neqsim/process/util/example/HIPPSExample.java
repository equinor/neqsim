package neqsim.process.util.example;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
  private static final Logger logger = LogManager.getLogger(HIPPSExample.class);

  /**
   * Main method to run the HIPPS example.
   *
   * @param args command line arguments (not used)
   */
  public static void main(String[] args) {
    logger.info("╔════════════════════════════════════════════════════════════════╗");
    logger.info("║     HIPPS (High Integrity Pressure Protection System)         ║");
    logger.info("║              Blocked Outlet Protection Example                ║");
    logger.info("╚════════════════════════════════════════════════════════════════╝\n");

    // ========================================
    // 1. CREATE PROCESS SYSTEM
    // ========================================
    logger.info("═══ SYSTEM CONFIGURATION ═══");

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

    logger.printf(org.apache.logging.log4j.Level.INFO,
        "Feed Stream: %.0f kg/hr @ %.1f bara, %.1f °C%n", feedStream.getFlowRate("kg/hr"),
        feedStream.getPressure("bara"), feedStream.getTemperature("C"));

    // Create separator (MAWP = 100 bara)
    Separator separator = new Separator("HP Separator", feedStream);
    separator.setInternalDiameter(1.5); // 1.5m ID
    separator.setSeparatorLength(4.0); // 4m length
    separator.run();

    logger.printf(org.apache.logging.log4j.Level.INFO, "Separator MAWP: 100.0 bara%n");
    logger.printf(org.apache.logging.log4j.Level.INFO, "Normal operating pressure: %.1f bara%n",
        separator.getPressure("bara"));

    // ========================================
    // 2. CONFIGURE REDUNDANT PRESSURE TRANSMITTERS
    // ========================================
    logger.info("\n═══ PRESSURE TRANSMITTER CONFIGURATION ═══");

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

    logger.info("Redundant transmitters configured: PT-101A, PT-101B, PT-101C");
    logger.info("HIHI Alarm Setpoint: 90.0 bara (90% of MAWP)");
    logger.info("Alarm Confirmation Delay: 500 ms");
    logger.info("Deadband: 2.0 bara");

    // ========================================
    // 3. CREATE HIPPS VALVE
    // ========================================
    logger.info("\n═══ HIPPS VALVE CONFIGURATION ═══");

    HIPPSValve hippsValve = new HIPPSValve("HIPPS-XV-001", separator.getGasOutStream());
    hippsValve.addPressureTransmitter(PT1);
    hippsValve.addPressureTransmitter(PT2);
    hippsValve.addPressureTransmitter(PT3);
    hippsValve.setVotingLogic(HIPPSValve.VotingLogic.TWO_OUT_OF_THREE); // 2oo3 for SIL 3
    hippsValve.setClosureTime(3.0); // 3 second fast-acting valve
    hippsValve.setSILRating(3);
    hippsValve.setProofTestInterval(8760.0); // Annual proof test
    hippsValve.setCv(200.0);

    logger.info("HIPPS Valve: HIPPS-XV-001");
    logger.info("SIL Rating: SIL 3");
    logger.info("Voting Logic: 2oo3 (2 out of 3 transmitters)");
    logger.info("Closure Time: 3.0 seconds");
    logger.info("Trip Point: 90.0 bara");

    // ========================================
    // 4. CREATE PSV AS BACKUP
    // ========================================
    logger.info("\n═══ PSV BACKUP PROTECTION ═══");

    SafetyValve psv = new SafetyValve("PSV-001", separator.getGasOutStream());
    psv.setPressureSpec(100.0); // Set at MAWP
    psv.setFullOpenPressure(110.0); // Full open at 10% overpressure
    psv.setBlowdown(7.0); // 7% blowdown

    logger.info("PSV: PSV-001 (backup protection)");
    logger.info("PSV Set Pressure: 100.0 bara (at MAWP)");
    logger.info("Full Open Pressure: 110.0 bara");
    logger.info("Blowdown: 7%");

    // ========================================
    // 5. DYNAMIC SIMULATION - BLOCKED OUTLET SCENARIO
    // ========================================
    logger.info("\n═══ DYNAMIC SIMULATION - BLOCKED OUTLET SCENARIO ═══");
    logger.info("t=0-5s:    Normal operation at 70 bara");
    logger.info("t=5s:      Downstream valve closes (blocked outlet)");
    logger.info("t=5-20s:   Pressure ramps at 2 bara/sec");
    logger.info("Expected:  HIPPS trips at 90 bara, PSV never lifts\n");

    logger.info(
        "Time (s) | Sep Press | Active PT | HIPPS Open | PSV Open | HIPPS Status | PSV Status");
    logger.info(
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
        logger.printf(org.apache.logging.log4j.Level.INFO,
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
    logger.info("\n╔════════════════════════════════════════════════════════════════╗");
    logger.info("║                         RESULTS SUMMARY                        ║");
    logger.info("╚════════════════════════════════════════════════════════════════╝");

    logger.printf(org.apache.logging.log4j.Level.INFO, "%nHIPPS Performance:%n");
    logger.printf(org.apache.logging.log4j.Level.INFO, "  Trip Time: %.1f seconds%n", tripTime);
    logger.printf(org.apache.logging.log4j.Level.INFO, "  Trip Pressure: %.2f bara%n",
        tripPressure);
    logger.printf(org.apache.logging.log4j.Level.INFO, "  Active Transmitters at Trip: %d/3%n",
        hippsValve.getActiveTransmitterCount());
    logger.printf(org.apache.logging.log4j.Level.INFO, "  Voting Logic: %s%n",
        hippsValve.getVotingLogic().getNotation());
    logger.printf(org.apache.logging.log4j.Level.INFO, "  Closure Time: %.1f seconds%n",
        hippsValve.getClosureTime());

    logger.printf(org.apache.logging.log4j.Level.INFO, "%nPSV Status:%n");
    logger.printf(org.apache.logging.log4j.Level.INFO, "  PSV Lifted: %s%n",
        psvLifted ? "YES" : "NO");
    logger.printf(org.apache.logging.log4j.Level.INFO, "  PSV Opening: %.1f%%%n",
        psv.getPercentValveOpening());

    logger.printf(org.apache.logging.log4j.Level.INFO, "%nSafety Analysis:%n");
    if (!psvLifted) {
      logger.info("  ✓ HIPPS successfully prevented overpressure");
      logger.info("  ✓ PSV did not lift - NO FLARING occurred");
      logger.info("  ✓ Environmental emissions prevented");
      logger.info("  ✓ Pressure remained below MAWP (100 bara)");
    } else {
      logger.info("  ✗ PSV lifted - HIPPS may have failed");
      logger.info("  ✗ Flaring occurred");
    }

    logger.printf(org.apache.logging.log4j.Level.INFO, "%nHIPPS vs PSV Comparison:%n");
    logger.info("  HIPPS Action:  Stopped flow BEFORE overpressure (90 bara)");
    logger.info("  PSV Action:    Would relieve AT overpressure (100 bara)");
    logger.info("  Result:        HIPPS prevented flaring and emissions");

    // Display comprehensive diagnostics
    logger.info("\n" + hippsValve.getDiagnostics());

    logger.info("\n╔════════════════════════════════════════════════════════════════╗");
    logger.info("║                    SIMULATION COMPLETE                         ║");
    logger.info("╚════════════════════════════════════════════════════════════════╝");
  }
}
