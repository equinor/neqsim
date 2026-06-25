package neqsim.process.equipment.valve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.alarm.AlarmConfig;
import neqsim.process.equipment.diffpressure.Orifice;
import neqsim.process.equipment.flare.Flare;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.measurementdevice.FireDetector;
import neqsim.process.measurementdevice.GasDetector;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Comprehensive test for ESD (Emergency Shutdown) system demonstrating fire alarm handling with voting logic.
 *
 * <p>
 * This test simulates a realistic ESD scenario where:
 * <ul>
 * <li>Multiple fire detectors are deployed (2-out-of-2 or 2-out-of-3 voting)</li>
 * <li>First fire alarm does NOT trigger ESD (requires confirmation)</li>
 * <li>Second fire alarm ACTIVATES ESD blowdown</li>
 * <li>Blowdown valve opens and routes gas to flare</li>
 * <li>Flare heat output and CO2 emissions are calculated</li>
 * <li>System tracks cumulative emissions during blowdown</li>
 * </ul>
 *
 * <p>
 * Test Scenario:
 * <ol>
 * <li>Normal operation - gas flows to process</li>
 * <li>First fire detector activates (t=5s) - ESD does NOT activate</li>
 * <li>Second fire detector activates (t=10s) - ESD ACTIVATES</li>
 * <li>Blowdown valve opens over 5 seconds</li>
 * <li>Gas flows through orifice to flare</li>
 * <li>Flare calculates heat release and CO2 emissions</li>
 * <li>Cumulative emissions tracked throughout blowdown</li>
 * </ol>
 *
 * @author ESOL
 * @version 1.0
 */
class ESDFireAlarmSystemTest {
  private static final Logger logger = LogManager.getLogger(ESDFireAlarmSystemTest.class);

  private SystemInterface testFluid;
  private Stream feedStream;
  private Separator separator;
  private Splitter gasSplitter;
  private BlowdownValve bdValve;
  private Flare flare;
  private FireDetector fireDetector1;
  private FireDetector fireDetector2;
  private FireDetector fireDetector3;

  @BeforeEach
  void setUp() {
    // Create test fluid - rich gas mixture
    testFluid = new SystemSrkEos(298.15, 50.0);
    testFluid.addComponent("nitrogen", 1.0);
    testFluid.addComponent("methane", 85.0);
    testFluid.addComponent("ethane", 10.0);
    testFluid.addComponent("propane", 3.0);
    testFluid.addComponent("n-butane", 1.0);
    testFluid.setMixingRule(2);

    feedStream = new Stream("Feed", testFluid);
    feedStream.setFlowRate(10000.0, "kg/hr");
    feedStream.setPressure(50.0, "bara");
    feedStream.setTemperature(25.0, "C");
  }

  /**
   * Test complete ESD fire alarm system with 2-out-of-2 voting logic.
   *
   * <p>
   * Demonstrates:
   * <ul>
   * <li>Fire detector configuration and alarm setup</li>
   * <li>Sequential fire alarm activation</li>
   * <li>ESD logic requiring two confirmed alarms</li>
   * <li>Blowdown valve activation and opening dynamics</li>
   * <li>Flare heat output calculations</li>
   * <li>CO2 emissions tracking</li>
   * </ul>
   */
  @Test
  void testESDWithTwoFireAlarmVoting() {
    logger.info("\n╔════════════════════════════════════════════════════════════════╗");
    logger.info("║   ESD FIRE ALARM SYSTEM TEST - 2-OUT-OF-2 VOTING LOGIC        ║");
    logger.info("╚════════════════════════════════════════════════════════════════╝\n");

    // Setup fire detectors
    fireDetector1 = new FireDetector("FD-101", "Separator Area - North");
    fireDetector2 = new FireDetector("FD-102", "Separator Area - South");

    // Configure alarm thresholds
    AlarmConfig fireAlarmConfig = AlarmConfig.builder().highLimit(0.5) // Fire detected at 0.5
        .delay(1.0) // 1 second confirmation delay
        .deadband(0.1).unit("binary").build();

    fireDetector1.setAlarmConfig(fireAlarmConfig);
    fireDetector2.setAlarmConfig(fireAlarmConfig);

    // Setup process equipment
    feedStream.run();

    separator = new Separator("HP Separator", feedStream);
    separator.setCalculateSteadyState(true);
    separator.run();

    Stream separatorGasOut = new Stream("Sep Gas Out", separator.getGasOutStream());
    separatorGasOut.run();

    // Splitter to direct flow to process or blowdown
    gasSplitter = new Splitter("Gas Splitter", separatorGasOut, 2);
    gasSplitter.setSplitFactors(new double[] { 1.0, 0.0 }); // Initially all to process
    gasSplitter.run();

    Stream processStream = new Stream("To Process", gasSplitter.getSplitStream(0));
    Stream blowdownStream = new Stream("To Blowdown", gasSplitter.getSplitStream(1));
    processStream.run();
    blowdownStream.run();

    // Blowdown valve (normally closed)
    bdValve = new BlowdownValve("BD-101", blowdownStream);
    bdValve.setOpeningTime(5.0); // 5 seconds to fully open
    bdValve.setCv(200.0);
    bdValve.run();

    Stream bdValveOutlet = new Stream("BD Valve Outlet", bdValve.getOutletStream());
    bdValveOutlet.run();

    // Blowdown orifice for flow control
    Orifice bdOrifice = new Orifice("BD Orifice", 0.45, 0.18, 50.0, 1.5, 0.61);
    bdOrifice.setInletStream(bdValveOutlet);

    Stream toFlare = new Stream("To Flare", bdOrifice.getOutletStream());

    // Flare (connect directly without mixer for simplicity)
    flare = new Flare("Blowdown Flare", toFlare);
    flare.setFlameHeight(50.0);
    flare.setRadiantFraction(0.20);
    flare.setTipDiameter(0.8);
    flare.resetCumulative();
    flare.run();

    logger.info("═══ SYSTEM CONFIGURATION ═══");
    logger.info("Separator: HP Separator at 50 bara");
    logger.info("Gas flow rate: 10000 kg/hr");
    logger.info("Fire Detector 1: FD-101 @ Separator Area - North");
    logger.info("Fire Detector 2: FD-102 @ Separator Area - South");
    logger.info("ESD Logic: 2-out-of-2 voting (both detectors must activate)");
    logger.info("Blowdown valve: BD-101 (normally closed, 5s opening time)");
    logger.info("Flare header pressure: 1.5 bara\n");

    // PHASE 1: Normal Operation - No fire alarms
    logger.info("═══ PHASE 1: NORMAL OPERATION (t=0-5s) ═══");
    assertFalse(fireDetector1.isFireDetected(), "FD-101 should not detect fire initially");
    assertFalse(fireDetector2.isFireDetected(), "FD-102 should not detect fire initially");
    assertFalse(bdValve.isActivated(), "BD valve should not be activated");
    assertEquals(10000.0, processStream.getFlowRate("kg/hr"), 100.0, "Gas flows to process");
    assertEquals(0.0, blowdownStream.getFlowRate("kg/hr"), 1.0, "No flow to blowdown");
    logger.info("Process flow: " + String.format("%.1f kg/hr", processStream.getFlowRate("kg/hr")));
    logger.info("Blowdown flow: " + String.format("%.1f kg/hr", blowdownStream.getFlowRate("kg/hr")));
    logger.info("FD-101 State: NO FIRE");
    logger.info("FD-102 State: NO FIRE");
    logger.info("ESD Status: NORMAL OPERATION");
    logger.info("BD Valve: NOT ACTIVATED\n");

    // PHASE 2: First fire alarm activates (t=5s) - ESD should NOT activate yet
    logger.info("═══ PHASE 2: FIRST FIRE ALARM (t=5s) ═══");
    logger.info(">>> FIRE DETECTOR FD-101 ACTIVATES <<<");
    fireDetector1.detectFire();

    assertTrue(fireDetector1.isFireDetected(), "FD-101 should detect fire");
    assertFalse(fireDetector2.isFireDetected(), "FD-102 should still be normal");

    // Check voting logic - need 2 detectors for ESD
    int fireAlarmsActive = (fireDetector1.isFireDetected() ? 1 : 0) + (fireDetector2.isFireDetected() ? 1 : 0);
    boolean esdShouldActivate = (fireAlarmsActive >= 2);

    logger.info("FD-101 State: FIRE DETECTED");
    logger.info("FD-102 State: NO FIRE");
    logger.info("Active fire alarms: " + fireAlarmsActive + " of 2");
    System.out.println("ESD Logic: " + (esdShouldActivate ? "ACTIVATE ESD" : "WAITING FOR CONFIRMATION"));

    assertFalse(esdShouldActivate, "ESD should NOT activate with only 1 fire alarm");
    assertFalse(bdValve.isActivated(), "BD valve should still be inactive");
    logger.info("BD Valve: NOT ACTIVATED (awaiting second alarm)\n");

    // PHASE 3: Second fire alarm activates (t=10s) - ESD ACTIVATES
    logger.info("═══ PHASE 3: SECOND FIRE ALARM - ESD ACTIVATION (t=10s) ═══");
    logger.info(">>> FIRE DETECTOR FD-102 ACTIVATES <<<");
    logger.info(">>> TWO FIRE ALARMS CONFIRMED - ACTIVATING ESD <<<");
    fireDetector2.detectFire();

    assertTrue(fireDetector1.isFireDetected(), "FD-101 should still detect fire");
    assertTrue(fireDetector2.isFireDetected(), "FD-102 should now detect fire");

    fireAlarmsActive = (fireDetector1.isFireDetected() ? 1 : 0) + (fireDetector2.isFireDetected() ? 1 : 0);
    esdShouldActivate = (fireAlarmsActive >= 2);

    logger.info("FD-101 State: FIRE DETECTED");
    logger.info("FD-102 State: FIRE DETECTED");
    logger.info("Active fire alarms: " + fireAlarmsActive + " of 2");
    logger.info("ESD Logic: ACTIVATE ESD");

    assertTrue(esdShouldActivate, "ESD should activate with 2 fire alarms");

    // Activate ESD system
    bdValve.activate();
    gasSplitter.setSplitFactors(new double[] { 0.0, 1.0 }); // Redirect to blowdown
    separator.setCalculateSteadyState(false); // Switch to dynamic mode

    assertTrue(bdValve.isActivated(), "BD valve should be activated");
    logger.info("BD Valve: ACTIVATED");
    logger.info("Splitter: Flow redirected to blowdown\n");

    // PHASE 4: Dynamic blowdown simulation with flare heat and emissions tracking
    logger.info("═══ PHASE 4: BLOWDOWN SIMULATION WITH FLARE EMISSIONS ═══");
    logger.info(
        "Time (s) | FD-101 | FD-102 | Alarms | BD Open (%) | BD Flow (kg/hr) | Flare Heat (MW) | CO2 Rate (kg/s) | Cumul Heat (GJ) | Cumul CO2 (kg)");
    logger.info(
        "---------|--------|--------|--------|-------------|-----------------|-----------------|-----------------|-----------------|----------------");

    double timeStep = 1.0;
    double totalTime = 20.0;
    double esdActivationTime = 10.0;

    for (double time = 0.0; time <= totalTime; time += timeStep) {
      // Run equipment
      if (separator.getCalculateSteadyState()) {
        separator.run();
      } else {
        separator.runTransient(timeStep, java.util.UUID.randomUUID());
      }
      separatorGasOut.run();
      gasSplitter.run();
      blowdownStream.run();

      if (time >= esdActivationTime && bdValve.isActivated()) {
        bdValve.runTransient(timeStep, java.util.UUID.randomUUID());
      } else {
        bdValve.run();
      }

      bdValveOutlet.run();

      // Run orifice transient for flow calculation based on pressure differential
      bdOrifice.runTransient(timeStep, java.util.UUID.randomUUID());

      toFlare.run();
      flare.run();
      flare.updateCumulative(timeStep);

      // Count active alarms
      int activeAlarms = (fireDetector1.isFireDetected() ? 1 : 0) + (fireDetector2.isFireDetected() ? 1 : 0);

      // Print status every 2 seconds or at key events
      if (time % 2.0 == 0.0 || time == 5.0 || time == 10.0) {
        logger.printf(org.apache.logging.log4j.Level.INFO,
            "%8.1f | %6s | %6s | %6d | %11.1f | %15.1f | %15.2f | %15.3f | %15.2f | %14.1f%n", time,
            fireDetector1.isFireDetected() ? "FIRE" : "OK", fireDetector2.isFireDetected() ? "FIRE" : "OK",
            activeAlarms, bdValve.getPercentValveOpening(), toFlare.getFlowRate("kg/hr"), flare.getHeatDuty("MW"),
            flare.getCO2Emission("kg/sec"), flare.getCumulativeHeatReleased("GJ"),
            flare.getCumulativeCO2Emission("kg"));
      }
    }

    // PHASE 5: Summary and verification
    logger.info("═══ BLOWDOWN SUMMARY ═══");
    logger.printf(org.apache.logging.log4j.Level.INFO, "Final BD valve opening: %.1f%%%n",
        bdValve.getPercentValveOpening());
    logger.printf(org.apache.logging.log4j.Level.INFO, "Total gas blown down: %.1f kg%n",
        flare.getCumulativeGasBurned("kg"));
    logger.printf(org.apache.logging.log4j.Level.INFO, "Total heat released: %.2f GJ%n",
        flare.getCumulativeHeatReleased("GJ"));
    logger.printf(org.apache.logging.log4j.Level.INFO, "Total CO2 emissions: %.1f kg%n",
        flare.getCumulativeCO2Emission("kg"));

    logger.info("═══ FIRE ALARM STATUS ═══");
    logger.info(fireDetector1.toString());
    logger.info(fireDetector2.toString());

    // Verification assertions
    assertTrue(fireDetector1.isFireDetected(), "FD-101 should be in fire state");
    assertTrue(fireDetector2.isFireDetected(), "FD-102 should be in fire state");
    assertTrue(bdValve.isActivated(), "BD valve should be activated");
    assertTrue(bdValve.getPercentValveOpening() > 90.0, "BD valve should be fully open");
    assertTrue(flare.getCumulativeGasBurned("kg") > 0, "Flare should have burned gas");
    assertTrue(flare.getCumulativeHeatReleased("GJ") > 0, "Flare should have released heat");
    assertTrue(flare.getCumulativeCO2Emission("kg") > 0, "Flare should have emitted CO2");

    logger.info("✓ Two fire alarms successfully triggered ESD");
    logger.info("✓ BD valve activated and opened");
    logger.info("✓ Gas routed to flare");
    logger.info("✓ Flare heat output calculated: " + String.format("%.2f GJ", flare.getCumulativeHeatReleased("GJ")));
    logger.info("✓ CO2 emissions calculated: " + String.format("%.1f kg", flare.getCumulativeCO2Emission("kg")));

    logger.info("╔════════════════════════════════════════════════════════════════╗");
    logger.info("║            ESD FIRE ALARM TEST COMPLETED                       ║");
    logger.info("╚════════════════════════════════════════════════════════════════╝\n");
  }

  /**
   * Test ESD system with 2-out-of-3 voting logic for fire alarms.
   *
   * <p>
   * Demonstrates redundancy with three fire detectors where any two alarms will trigger ESD. This is a more robust
   * configuration used in critical safety applications.
   * </p>
   */
  @Test
  void testESDWith2OutOf3FireAlarmVoting() {
    logger.info("\n╔════════════════════════════════════════════════════════════════╗");
    logger.info("║   ESD FIRE ALARM SYSTEM TEST - 2-OUT-OF-3 VOTING LOGIC        ║");
    logger.info("╚════════════════════════════════════════════════════════════════╝\n");

    // Setup three fire detectors
    fireDetector1 = new FireDetector("FD-101", "Separator Area - North");
    fireDetector2 = new FireDetector("FD-102", "Separator Area - South");
    fireDetector3 = new FireDetector("FD-103", "Separator Area - East");

    // Configure alarm thresholds
    AlarmConfig fireAlarmConfig = AlarmConfig.builder().highLimit(0.5).delay(0.5).deadband(0.1).unit("binary").build();

    fireDetector1.setAlarmConfig(fireAlarmConfig);
    fireDetector2.setAlarmConfig(fireAlarmConfig);
    fireDetector3.setAlarmConfig(fireAlarmConfig);

    // Setup minimal process equipment for test
    feedStream.run();
    separator = new Separator("HP Separator", feedStream);
    separator.run();

    bdValve = new BlowdownValve("BD-101");
    bdValve.setOpeningTime(3.0);

    logger.info("═══ SYSTEM CONFIGURATION ═══");
    logger.info("Fire Detector 1: FD-101 @ Separator Area - North");
    logger.info("Fire Detector 2: FD-102 @ Separator Area - South");
    logger.info("Fire Detector 3: FD-103 @ Separator Area - East");
    logger.info("ESD Logic: 2-out-of-3 voting (any 2 detectors trigger ESD)\n");

    // Test various combinations
    logger.info("═══ TESTING VOTING COMBINATIONS ═══\n");

    // No alarms
    logger.info("Test 1: No fire alarms");
    int alarmCount = countActiveAlarms(fireDetector1, fireDetector2, fireDetector3);
    assertEquals(0, alarmCount);
    assertFalse(alarmCount >= 2, "Should not trigger ESD");
    logger.info("  Active alarms: " + alarmCount + " → ESD: NO\n");

    // One alarm
    logger.info("Test 2: One fire alarm (FD-101)");
    fireDetector1.detectFire();
    alarmCount = countActiveAlarms(fireDetector1, fireDetector2, fireDetector3);
    assertEquals(1, alarmCount);
    assertFalse(alarmCount >= 2, "Should not trigger ESD with only 1 alarm");
    logger.info("  Active alarms: " + alarmCount + " → ESD: NO\n");

    // Two alarms (should trigger)
    logger.info("Test 3: Two fire alarms (FD-101 + FD-102)");
    fireDetector2.detectFire();
    alarmCount = countActiveAlarms(fireDetector1, fireDetector2, fireDetector3);
    assertEquals(2, alarmCount);
    assertTrue(alarmCount >= 2, "Should trigger ESD with 2 alarms");
    logger.info("  Active alarms: " + alarmCount + " → ESD: YES");
    logger.info("  >>> ESD ACTIVATED <<<\n");

    // Activate ESD
    bdValve.activate();
    assertTrue(bdValve.isActivated(), "BD valve should be activated");

    // All three alarms
    logger.info("Test 4: All three fire alarms");
    fireDetector3.detectFire();
    alarmCount = countActiveAlarms(fireDetector1, fireDetector2, fireDetector3);
    assertEquals(3, alarmCount);
    assertTrue(alarmCount >= 2, "Should maintain ESD with 3 alarms");
    logger.info("  Active alarms: " + alarmCount + " → ESD: YES\n");

    // Reset one detector (still 2 active - ESD should remain)
    logger.info("Test 5: Reset one detector (FD-103)");
    fireDetector3.reset();
    alarmCount = countActiveAlarms(fireDetector1, fireDetector2, fireDetector3);
    assertEquals(2, alarmCount);
    assertTrue(alarmCount >= 2, "Should maintain ESD with 2 remaining alarms");
    logger.info("  Active alarms: " + alarmCount + " → ESD: MAINTAINED\n");

    // Reset another detector (only 1 active - but ESD stays latched)
    logger.info("Test 6: Reset another detector (FD-102)");
    fireDetector2.reset();
    alarmCount = countActiveAlarms(fireDetector1, fireDetector2, fireDetector3);
    assertEquals(1, alarmCount);
    logger.info("  Active alarms: " + alarmCount);
    logger.info("  Note: BD valve stays activated (latched) until manual reset\n");
    assertTrue(bdValve.isActivated(), "BD valve remains activated even with alarms cleared (safety latch)");

    logger.info("✓ 2-out-of-3 voting logic verified");
    logger.info("✓ ESD activates with any 2 detectors");
    logger.info("✓ Safety latch prevents automatic reset");

    logger.info("╔════════════════════════════════════════════════════════════════╗");
    logger.info("║         2-OUT-OF-3 VOTING TEST COMPLETED                       ║");
    logger.info("╚════════════════════════════════════════════════════════════════╝\n");
  }

  /**
   * Helper method to count active fire alarms.
   *
   * @param detectors variable number of fire detectors to check
   * @return count of detectors in fire state
   */
  private int countActiveAlarms(FireDetector... detectors) {
    int count = 0;
    for (FireDetector detector : detectors) {
      if (detector.isFireDetected()) {
        count++;
      }
    }
    return count;
  }

  /**
   * Test ESD system with gas detectors (hydrocarbon detection).
   *
   * <p>
   * Demonstrates:
   * <ul>
   * <li>Gas detector configuration for combustible gas (%LEL)</li>
   * <li>Two-level alarm (20% LEL warning, 60% LEL high alarm)</li>
   * <li>Combined fire and gas detection logic</li>
   * <li>ESD activation on high gas concentration</li>
   * <li>Blowdown and flare emissions tracking</li>
   * </ul>
   */
  @Test
  void testESDWithGasDetectors() {
    logger.info("\n╔════════════════════════════════════════════════════════════════╗");
    logger.info("║     ESD GAS DETECTION SYSTEM TEST - HYDROCARBON DETECTION     ║");
    logger.info("╚════════════════════════════════════════════════════════════════╝\n");

    // Setup gas detectors for hydrocarbon (combustible gas) detection
    GasDetector gasDetector1 = new GasDetector("GD-101", GasDetector.GasType.COMBUSTIBLE, "Separator Area - East");
    GasDetector gasDetector2 = new GasDetector("GD-102", GasDetector.GasType.COMBUSTIBLE, "Separator Area - West");

    // Configure for methane detection
    gasDetector1.setGasSpecies("methane");
    gasDetector1.setLowerExplosiveLimit(50000.0); // Methane LEL = 5% = 50,000 ppm
    gasDetector1.setResponseTime(10.0); // 10 second response time

    gasDetector2.setGasSpecies("methane");
    gasDetector2.setLowerExplosiveLimit(50000.0);
    gasDetector2.setResponseTime(10.0);

    // Configure two-level alarms: 20% LEL (warning) and 60% LEL (high alarm)
    AlarmConfig gasAlarmConfig = AlarmConfig.builder().highLimit(20.0) // 20% LEL - warning level
        .highHighLimit(60.0) // 60% LEL - high alarm, triggers ESD
        .delay(2.0) // 2 second confirmation delay
        .deadband(2.0) // 2% deadband
        .unit("% LEL").build();

    gasDetector1.setAlarmConfig(gasAlarmConfig);
    gasDetector2.setAlarmConfig(gasAlarmConfig);

    // Setup process equipment (simplified version)
    feedStream.run();

    separator = new Separator("HP Separator", feedStream);
    separator.setCalculateSteadyState(true);
    separator.run();

    Stream separatorGasOut = new Stream("Sep Gas Out", separator.getGasOutStream());
    separatorGasOut.run();

    gasSplitter = new Splitter("Gas Splitter", separatorGasOut, 2);
    gasSplitter.setSplitFactors(new double[] { 1.0, 0.0 }); // Initially all to process
    gasSplitter.run();

    Stream processStream = new Stream("To Process", gasSplitter.getSplitStream(0));
    Stream blowdownStream = new Stream("To Blowdown", gasSplitter.getSplitStream(1));
    processStream.run();
    blowdownStream.run();

    bdValve = new BlowdownValve("BD-101", blowdownStream);
    bdValve.setOpeningTime(5.0);
    bdValve.setCv(200.0);
    bdValve.run();

    Stream bdValveOutlet = new Stream("BD Valve Outlet", bdValve.getOutletStream());
    bdValveOutlet.run();

    Orifice bdOrifice = new Orifice("BD Orifice", 0.45, 0.18, 50.0, 1.5, 0.61);
    bdOrifice.setInletStream(bdValveOutlet);

    Stream toFlare = new Stream("To Flare", bdOrifice.getOutletStream());

    flare = new Flare("Blowdown Flare", toFlare);
    flare.setFlameHeight(50.0);
    flare.setRadiantFraction(0.20);
    flare.setTipDiameter(0.8);
    flare.resetCumulative();
    flare.run();

    logger.info("═══ SYSTEM CONFIGURATION ═══");
    System.out.println("Gas Detector 1: " + gasDetector1.getName() + " @ " + gasDetector1.getLocation());
    logger.info("  Type: Combustible Gas (%LEL)");
    logger.info("  Gas Species: " + gasDetector1.getGasSpecies());
    System.out.println("  LEL: " + String.format("%.0f ppm", gasDetector1.getLowerExplosiveLimit()));
    logger.info("  Warning: 20% LEL");
    logger.info("  High Alarm: 60% LEL (triggers ESD)");

    System.out.println("Gas Detector 2: " + gasDetector2.getName() + " @ " + gasDetector2.getLocation());
    logger.info("  Type: Combustible Gas (%LEL)");
    logger.info("  Gas Species: " + gasDetector2.getGasSpecies());

    // PHASE 1: Normal Operation
    logger.info("═══ PHASE 1: NORMAL OPERATION ═══");
    assertFalse(gasDetector1.isGasDetected(20.0), "GD-101 should not detect gas");
    assertFalse(gasDetector2.isGasDetected(20.0), "GD-102 should not detect gas");
    assertEquals(0.0, gasDetector1.getGasConcentration(), 0.01);
    assertEquals(0.0, gasDetector2.getGasConcentration(), 0.01);
    logger.info("GD-101: " + String.format("%.1f %% LEL", gasDetector1.getGasConcentration()) + " - NORMAL");
    logger.info("GD-102: " + String.format("%.1f %% LEL", gasDetector2.getGasConcentration()) + " - NORMAL");
    logger.info("Process flow: " + String.format("%.1f kg/hr", processStream.getFlowRate("kg/hr")));

    // PHASE 2: Gas leak detected - Warning level (25% LEL)
    logger.info("═══ PHASE 2: GAS LEAK DETECTED - WARNING LEVEL ═══");
    logger.info(">>> GAS DETECTOR GD-101 DETECTS 25% LEL <<<");
    gasDetector1.setGasConcentration(25.0); // 25% LEL

    assertTrue(gasDetector1.isGasDetected(20.0), "GD-101 should detect gas above warning");
    assertFalse(gasDetector1.isHighAlarm(60.0), "GD-101 should not be in high alarm yet");
    assertFalse(gasDetector2.isGasDetected(20.0), "GD-102 should still be normal");

    logger.info("GD-101: " + String.format("%.1f %% LEL", gasDetector1.getGasConcentration()) + " - WARNING");
    logger.info("GD-102: " + String.format("%.1f %% LEL", gasDetector2.getGasConcentration()) + " - NORMAL");
    logger.info("Action: Investigate gas source, prepare for evacuation");
    logger.info("ESD Status: NOT ACTIVATED (waiting for high alarm confirmation)");

    // PHASE 3: High gas concentration - ESD activation (65% LEL on both detectors)
    logger.info("═══ PHASE 3: HIGH GAS CONCENTRATION - ESD ACTIVATION ═══");
    logger.info(">>> BOTH DETECTORS DETECT >60% LEL <<<");
    logger.info(">>> EXPLOSIVE ATMOSPHERE - ACTIVATING ESD <<<");
    gasDetector1.setGasConcentration(65.0); // 65% LEL
    gasDetector2.setGasConcentration(62.0); // 62% LEL

    assertTrue(gasDetector1.isHighAlarm(60.0), "GD-101 should be in high alarm");
    assertTrue(gasDetector2.isHighAlarm(60.0), "GD-102 should be in high alarm");

    // Check if ESD should activate (both detectors > 60% LEL)
    boolean esdShouldActivate = gasDetector1.isHighAlarm(60.0) && gasDetector2.isHighAlarm(60.0);

    logger.info("GD-101: " + String.format("%.1f %% LEL", gasDetector1.getGasConcentration()) + " - HIGH ALARM");
    logger.info("GD-102: " + String.format("%.1f %% LEL", gasDetector2.getGasConcentration()) + " - HIGH ALARM");
    logger.info("ESD Logic: " + (esdShouldActivate ? "ACTIVATE ESD (2-out-of-2 high alarms)" : "NO ESD"));

    assertTrue(esdShouldActivate, "ESD should activate with both detectors in high alarm");

    // Activate ESD
    bdValve.activate();
    gasSplitter.setSplitFactors(new double[] { 0.0, 1.0 }); // Redirect to blowdown
    separator.setCalculateSteadyState(false); // Switch to dynamic mode

    assertTrue(bdValve.isActivated(), "BD valve should be activated");
    logger.info("BD Valve: ACTIVATED");
    logger.info("Flow: Redirected to blowdown and flare");

    // PHASE 4: Brief blowdown simulation
    logger.info("═══ PHASE 4: BLOWDOWN SIMULATION ═══");
    logger.info("Time (s) | GD-101  | GD-102  | BD%   | Flare Heat (MW) | Cumul CO2 (kg)");
    logger.info("---------|---------|---------|-------|-----------------|----------------");

    double timeStep = 2.0;
    double totalTime = 10.0;

    for (double time = 0.0; time <= totalTime; time += timeStep) {
      if (separator.getCalculateSteadyState()) {
        separator.run();
      } else {
        separator.runTransient(timeStep, java.util.UUID.randomUUID());
      }
      separatorGasOut.run();
      gasSplitter.run();
      blowdownStream.run();

      if (bdValve.isActivated()) {
        bdValve.runTransient(timeStep, java.util.UUID.randomUUID());
      } else {
        bdValve.run();
      }

      bdValveOutlet.run();
      bdOrifice.runTransient(timeStep, java.util.UUID.randomUUID());
      toFlare.run();
      flare.run();
      flare.updateCumulative(timeStep);

      logger.printf(org.apache.logging.log4j.Level.INFO, "%8.0f | %6.1f%% | %6.1f%% | %5.0f | %15.2f | %14.1f%n", time,
          gasDetector1.getGasConcentration(), gasDetector2.getGasConcentration(), bdValve.getPercentValveOpening(),
          flare.getHeatDuty("MW"), flare.getCumulativeCO2Emission("kg"));
    }

    logger.info("═══ SUMMARY ═══");
    logger.info("Gas Detection System: FUNCTIONAL ✓");
    logger.info("  Warning level (20% LEL): Detected and reported");
    logger.info("  High alarm level (60% LEL): Triggered ESD activation");
    logger.info("  ESD Response: Blowdown valve activated");

    logger.info("Blowdown Results:");
    logger.info("  Total gas burned: " + String.format("%.1f kg", flare.getCumulativeGasBurned("kg")));
    logger.info("  Total heat released: " + String.format("%.2f GJ", flare.getCumulativeHeatReleased("GJ")));
    logger.info("  Total CO2 emissions: " + String.format("%.1f kg", flare.getCumulativeCO2Emission("kg")));

    logger.info(gasDetector1.toString());
    logger.info(gasDetector2.toString());

    // Verifications
    assertTrue(gasDetector1.isHighAlarm(60.0), "GD-101 should be in high alarm state");
    assertTrue(gasDetector2.isHighAlarm(60.0), "GD-102 should be in high alarm state");
    assertTrue(bdValve.isActivated(), "BD valve should be activated");
    assertTrue(flare.getCumulativeGasBurned("kg") > 0, "Flare should have burned gas");

    logger.info("✓ Gas detection system functional");
    logger.info("✓ Two-level alarm (warning + high) verified");
    logger.info("✓ ESD activation on high gas concentration");
    logger.info("✓ Emissions tracking operational");

    logger.info("╔════════════════════════════════════════════════════════════════╗");
    logger.info("║          GAS DETECTION SYSTEM TEST COMPLETED                   ║");
    logger.info("╚════════════════════════════════════════════════════════════════╝\n");
  }

  /**
   * Test combined fire and gas detection system with voting logic.
   *
   * <p>
   * Demonstrates realistic F&amp;G (Fire &amp; Gas) system where ESD activates when EITHER:
   * <ul>
   * <li>2 fire detectors activate (2oo2), OR</li>
   * <li>2 gas detectors show high alarm (2oo2)</li>
   * </ul>
   */
  @Test
  void testCombinedFireAndGasDetection() {
    logger.info("\n╔════════════════════════════════════════════════════════════════╗");
    logger.info("║       COMBINED FIRE & GAS DETECTION SYSTEM TEST                ║");
    logger.info("╚════════════════════════════════════════════════════════════════╝\n");

    // Setup fire detectors
    FireDetector fd1 = new FireDetector("FD-101", "Separator North");
    FireDetector fd2 = new FireDetector("FD-102", "Separator South");

    AlarmConfig fireAlarmConfig = AlarmConfig.builder().highLimit(0.5).delay(1.0).unit("binary").build();
    fd1.setAlarmConfig(fireAlarmConfig);
    fd2.setAlarmConfig(fireAlarmConfig);

    // Setup gas detectors
    GasDetector gd1 = new GasDetector("GD-101", GasDetector.GasType.COMBUSTIBLE, "Separator East");
    GasDetector gd2 = new GasDetector("GD-102", GasDetector.GasType.COMBUSTIBLE, "Separator West");

    gd1.setGasSpecies("methane");
    gd2.setGasSpecies("methane");

    AlarmConfig gasAlarmConfig = AlarmConfig.builder().highLimit(20.0).highHighLimit(60.0).delay(2.0).unit("% LEL")
        .build();
    gd1.setAlarmConfig(gasAlarmConfig);
    gd2.setAlarmConfig(gasAlarmConfig);

    // Create blowdown valve
    BlowdownValve valve = new BlowdownValve("BD-201");
    valve.setOpeningTime(5.0);

    logger.info("═══ SYSTEM CONFIGURATION ═══");
    logger.info("Fire Detectors: FD-101, FD-102 (2-out-of-2 voting)");
    logger.info("Gas Detectors: GD-101, GD-102 (2-out-of-2 voting for high alarm)");
    logger.info("ESD Logic: Activate if (2 fire alarms) OR (2 gas high alarms)");

    // Test scenarios
    logger.info("═══ SCENARIO TESTING ═══\n");

    // Scenario 1: Normal conditions
    logger.info("Scenario 1: Normal operation");
    boolean fireESD = fd1.isFireDetected() && fd2.isFireDetected();
    boolean gasESD = gd1.isHighAlarm(60.0) && gd2.isHighAlarm(60.0);
    boolean esdActive = fireESD || gasESD;

    logger.info("  Fire alarms: 0/2");
    logger.info("  Gas high alarms: 0/2");
    logger.info("  ESD: " + (esdActive ? "ACTIVE" : "NORMAL") + " ✓");
    assertFalse(esdActive, "ESD should not be active");

    // Scenario 2: One fire alarm only
    logger.info("Scenario 2: One fire detector activates");
    fd1.detectFire();
    fireESD = fd1.isFireDetected() && fd2.isFireDetected();
    gasESD = gd1.isHighAlarm(60.0) && gd2.isHighAlarm(60.0);
    esdActive = fireESD || gasESD;

    logger.info("  Fire alarms: 1/2");
    logger.info("  Gas high alarms: 0/2");
    logger.info("  ESD: " + (esdActive ? "ACTIVE" : "NORMAL") + " ✓");
    assertFalse(esdActive, "ESD should not activate with only 1 fire alarm");

    // Scenario 3: One gas warning (not high alarm)
    logger.info("Scenario 3: One gas detector shows warning (25% LEL)");
    gd1.setGasConcentration(25.0); // Warning level, not high alarm
    fireESD = fd1.isFireDetected() && fd2.isFireDetected();
    gasESD = gd1.isHighAlarm(60.0) && gd2.isHighAlarm(60.0);
    esdActive = fireESD || gasESD;

    logger.info("  Fire alarms: 1/2");
    logger.info("  Gas warnings: 1/2 (25% LEL)");
    logger.info("  Gas high alarms: 0/2");
    logger.info("  ESD: " + (esdActive ? "ACTIVE" : "NORMAL") + " ✓");
    assertFalse(esdActive, "ESD should not activate on gas warning alone");

    // Scenario 4: Two fire alarms - ESD should activate
    logger.info("Scenario 4: Two fire detectors activate");
    fd2.detectFire();
    fireESD = fd1.isFireDetected() && fd2.isFireDetected();
    gasESD = gd1.isHighAlarm(60.0) && gd2.isHighAlarm(60.0);
    esdActive = fireESD || gasESD;

    logger.info("  Fire alarms: 2/2 ← TRIGGER");
    logger.info("  Gas high alarms: 0/2");
    logger.info("  ESD: " + (esdActive ? "ACTIVE" : "NORMAL") + " ✓");
    assertTrue(esdActive, "ESD should activate with 2 fire alarms");

    if (esdActive && !valve.isActivated()) {
      valve.activate();
    }
    assertTrue(valve.isActivated(), "Valve should be activated");
    logger.info("  >>> ESD ACTIVATED VIA FIRE DETECTION <<<");

    // Reset for next scenario
    valve.reset();
    fd1.reset();
    fd2.reset();
    gd1.reset();

    // Scenario 5: Two gas high alarms - ESD should activate
    logger.info("Scenario 5: Two gas detectors show high alarm (>60% LEL)");
    gd1.setGasConcentration(65.0);
    gd2.setGasConcentration(70.0);
    fireESD = fd1.isFireDetected() && fd2.isFireDetected();
    gasESD = gd1.isHighAlarm(60.0) && gd2.isHighAlarm(60.0);
    esdActive = fireESD || gasESD;

    logger.info("  Fire alarms: 0/2");
    logger.info("  Gas high alarms: 2/2 (65%, 70% LEL) ← TRIGGER");
    logger.info("  ESD: " + (esdActive ? "ACTIVE" : "NORMAL") + " ✓");
    assertTrue(esdActive, "ESD should activate with 2 gas high alarms");

    if (esdActive && !valve.isActivated()) {
      valve.activate();
    }
    assertTrue(valve.isActivated(), "Valve should be activated");
    logger.info("  >>> ESD ACTIVATED VIA GAS DETECTION <<<");

    logger.info("═══ SUMMARY ═══");
    logger.info("✓ Fire detection voting (2oo2) verified");
    logger.info("✓ Gas detection two-level alarms verified");
    logger.info("✓ Gas high alarm voting (2oo2) verified");
    logger.info("✓ Combined F&G logic operational");
    logger.info("✓ ESD activates on EITHER fire OR gas high alarms");

    logger.info("╔════════════════════════════════════════════════════════════════╗");
    logger.info("║     COMBINED F&G DETECTION SYSTEM TEST COMPLETED               ║");
    logger.info("╚════════════════════════════════════════════════════════════════╝\n");
  }
}
