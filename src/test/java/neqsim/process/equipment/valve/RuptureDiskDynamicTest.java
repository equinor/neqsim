package neqsim.process.equipment.valve;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Dynamic test for rupture disk behavior in blocked outlet scenarios. Tests the one-time bursting
 * behavior of rupture disks compared to reseating safety valves.
 *
 * @author Even Solbraa
 */
class RuptureDiskDynamicTest extends neqsim.NeqSimTest {

  /**
   * Dynamic test for rupture disk with blocked outlet scenario.
   * 
   * Scenario: - Gas from a separator flows through a splitter - Split stream 1 goes to a pressure
   * control valve (PCV) for normal operation - Split stream 2 goes to a rupture disk for
   * overpressure protection - At time t=50s, the PCV outlet becomes blocked (valve closes to 1%) -
   * Pressure in separator rises - Rupture disk bursts when burst pressure is exceeded - Unlike PSV,
   * rupture disk remains fully open even after pressure drops
   */
  @Test
  void testRuptureDiskWithBlockedOutletDynamic() {
    // Create gas system for separator feed
    SystemInterface feedFluid = new SystemSrkEos(273.15 + 40.0, 50.0);
    feedFluid.addComponent("nitrogen", 1.0);
    feedFluid.addComponent("CO2", 2.5);
    feedFluid.addComponent("methane", 85.0);
    feedFluid.addComponent("ethane", 8.0);
    feedFluid.addComponent("propane", 3.0);
    feedFluid.addComponent("i-butane", 0.3);
    feedFluid.addComponent("n-butane", 0.2);
    feedFluid.setMixingRule(2);
    feedFluid.setMultiPhaseCheck(true);

    // Create feed stream to separator
    Stream feedStream = new Stream("Feed to Separator", feedFluid);
    feedStream.setFlowRate(5000.0, "kg/hr");
    feedStream.setPressure(50.0, "bara");
    feedStream.setTemperature(40.0, "C");
    feedStream.run();

    // Create separator
    Separator separator = new Separator("HP Separator", feedStream);
    separator.setInternalDiameter(1.5);
    separator.setSeparatorLength(4.0);
    separator.setLiquidLevel(0.5);

    // Run separator in steady state mode to initialize
    separator.setCalculateSteadyState(true);
    separator.run();

    // Switch separator to dynamic mode
    separator.setCalculateSteadyState(false);

    // Create splitter for gas outlet - splits to control valve and rupture disk
    Splitter gasSplitter = new Splitter("Gas Splitter", separator.getGasOutStream(), 2);
    // Initial split: 99.9% to control valve, 0.1% to rupture disk
    gasSplitter.setSplitFactors(new double[] {0.999, 0.001});
    gasSplitter.setCalculateSteadyState(false);

    // Create pressure control valve (PCV) for normal operation
    ThrottlingValve pressureControlValve =
        new ThrottlingValve("PCV-001", gasSplitter.getSplitStream(0));
    pressureControlValve.setOutletPressure(5.0, "bara");
    pressureControlValve.setPercentValveOpening(50.0);
    pressureControlValve.setCalculateSteadyState(false);
    pressureControlValve.setMinimumValveOpening(0.1);

    // Create rupture disk
    RuptureDisk ruptureDisk = new RuptureDisk("RD-001", gasSplitter.getSplitStream(1));
    double burstPressure = 55.0; // bara - Rupture disk burst pressure
    double fullOpenPressure = 57.75; // bara - Fully open at 105% of burst (rapid opening)
    ruptureDisk.setBurstPressure(burstPressure);
    ruptureDisk.setFullOpenPressure(fullOpenPressure);
    ruptureDisk.setOutletPressure(1.0, "bara");
    ruptureDisk.setPercentValveOpening(0.0); // Initially closed
    ruptureDisk.setCalculateSteadyState(false);
    ruptureDisk.setCv(150.0); // Set adequate Cv for relief

    // Run initial steady state
    separator.run();
    gasSplitter.run();
    pressureControlValve.run();
    ruptureDisk.run();

    // Data collection for analysis
    List<Double> timePoints = new ArrayList<>();
    List<Double> separatorPressures = new ArrayList<>();
    List<Double> pcvFlowRates = new ArrayList<>();
    List<Double> diskFlowRates = new ArrayList<>();
    List<Double> diskOpenings = new ArrayList<>();
    List<Double> pcvOpenings = new ArrayList<>();

    double dt = 0.5; // Time step in seconds
    double currentTime = 0.0;
    UUID id = UUID.randomUUID();

    int numSteps = 600; // Total simulation time: 300 seconds

    // Dynamic simulation
    for (int i = 0; i < numSteps; i++) {
      currentTime = i * dt;

      // Simulate blocked outlet at t=50s (PCV closes)
      if (currentTime >= 50.0 && currentTime < 51.0) {
        // Sudden closure of control valve (blocked outlet scenario)
        pressureControlValve.setPercentValveOpening(1.0);
      }

      // Simulate recovery at t=200s (PCV reopens) - disk should stay open!
      if (currentTime >= 200.0 && currentTime < 201.0) {
        // Reopen control valve to allow pressure to drop
        pressureControlValve.setPercentValveOpening(50.0);
      }

      // Run transient calculations
      // Rupture disk opening is calculated automatically in RuptureDisk.runTransient()
      separator.runTransient(dt, id);
      gasSplitter.runTransient(dt, id);
      pressureControlValve.runTransient(dt, id);
      ruptureDisk.runTransient(dt, id); // Disk bursts automatically based on pressure

      // Collect data after transient run
      double separatorPressure = separator.getGasOutStream().getPressure("bara");
      timePoints.add(currentTime);
      separatorPressures.add(separatorPressure);
      pcvFlowRates.add(pressureControlValve.getOutletStream().getFlowRate("kg/hr"));
      diskFlowRates.add(ruptureDisk.getOutletStream().getFlowRate("kg/hr"));
      diskOpenings.add(ruptureDisk.getPercentValveOpening());
      pcvOpenings.add(pressureControlValve.getPercentValveOpening());

      // Optional: Print progress for key time points
      if (i % 40 == 0 || (currentTime >= 49.5 && currentTime <= 100.0 && i % 4 == 0)
          || (currentTime >= 199.5 && currentTime <= 250.0 && i % 4 == 0)) {
        System.out.printf("Time: %6.1f s | Sep Press: %6.2f bara | PCV Opening: %5.1f %% | "
            + "Disk Opening: %5.1f %% | PCV Flow: %7.1f kg/hr | Disk Flow: %7.1f kg/hr | Ruptured: %s%n",
            currentTime, separatorPressure, pressureControlValve.getPercentValveOpening(),
            ruptureDisk.getPercentValveOpening(), pcvFlowRates.get(i), diskFlowRates.get(i),
            ruptureDisk.hasRuptured() ? "YES" : "NO");
      }
    }

    // Verify rupture disk behavior
    // 1. Initial pressure should be below burst pressure
    Assertions.assertTrue(separatorPressures.get(0) < burstPressure,
        "Initial pressure should be below rupture disk burst pressure");

    // 2. Disk should be closed initially
    Assertions.assertEquals(0.0, diskOpenings.get(0), 0.1, "Disk should be initially closed");

    // 3. Find maximum pressure during transient
    double maxPressure =
        separatorPressures.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);

    // 4. Find when disk ruptured (first time opening > 0)
    int ruptureIndex = -1;
    for (int i = 0; i < diskOpenings.size(); i++) {
      if (diskOpenings.get(i) > 1.0) {
        ruptureIndex = i;
        break;
      }
    }
    Assertions.assertTrue(ruptureIndex > 0, "Disk should have ruptured during simulation");

    // 5. Verify disk ruptured when pressure exceeded burst pressure
    if (ruptureIndex > 0) {
      double pressureAtRupture = separatorPressures.get(ruptureIndex);
      Assertions.assertTrue(pressureAtRupture >= burstPressure,
          "Disk should rupture at or above burst pressure. Ruptured at: " + pressureAtRupture
              + " bara, Burst pressure: " + burstPressure + " bara");
    }

    // 6. KEY TEST: Verify disk does NOT close after pressure drops
    // Find a point after recovery where pressure has dropped below burst pressure
    int recoveryIndex = (int) (200.0 / dt);
    int postRecoveryIndex = recoveryIndex + 100; // 50 seconds after recovery

    if (postRecoveryIndex < separatorPressures.size()) {
      double postRecoveryPressure = separatorPressures.get(postRecoveryIndex);
      double postRecoveryOpening = diskOpenings.get(postRecoveryIndex);

      // Pressure should have dropped significantly
      Assertions.assertTrue(postRecoveryPressure < burstPressure,
          "Pressure should drop below burst pressure after recovery. Pressure: "
              + postRecoveryPressure + " bara, Burst: " + burstPressure + " bara");

      // BUT disk should still be fully open (100%)
      Assertions.assertEquals(100.0, postRecoveryOpening, 1.0,
          "Rupture disk should remain fully open even after pressure drops below burst pressure. "
              + "This is the key difference from a safety valve!");
    }

    // 8. Verify disk relieved significant flow
    double maxDiskFlow = diskFlowRates.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
    Assertions.assertTrue(maxDiskFlow > 100.0,
        "Disk should relieve significant flow during overpressure event. Max flow: " + maxDiskFlow
            + " kg/hr");

    // Print summary
    System.out.println("\n===== RUPTURE DISK TEST SUMMARY =====");
    System.out.printf("Feed flow rate: %.1f kg/hr%n", feedStream.getFlowRate("kg/hr"));
    System.out.printf("Rupture disk burst pressure: %.1f bara%n", burstPressure);
    System.out.printf("Rupture disk full open pressure: %.1f bara%n", fullOpenPressure);
    System.out.printf("Maximum separator pressure: %.2f bara%n", maxPressure);
    System.out.printf("Maximum disk relief flow: %.1f kg/hr%n", maxDiskFlow);
    System.out.printf("Disk ruptured: %s%n", ruptureDisk.hasRuptured() ? "YES" : "NO");
    System.out.printf("Final disk opening: %.1f %%%n", diskOpenings.get(diskOpenings.size() - 1));
    System.out.printf("Final pressure: %.2f bara%n",
        separatorPressures.get(separatorPressures.size() - 1));
    System.out.println(
        "Key behavior: Disk remains FULLY OPEN even though pressure dropped below burst pressure!");
    System.out.println("======================================");
  }

  /**
   * Test comparing rupture disk vs safety valve behavior.
   * 
   * This test demonstrates the key difference: - Safety valve: reseats when pressure drops -
   * Rupture disk: remains open once burst
   */
  @Test
  void testRuptureDiskVsSafetyValveComparison() {
    // Create simple gas system
    SystemInterface gasFluid = new SystemSrkEos(273.15 + 30.0, 45.0);
    gasFluid.addComponent("methane", 90.0);
    gasFluid.addComponent("ethane", 10.0);
    gasFluid.setMixingRule(2);

    Stream gasStream = new Stream("Gas Stream", gasFluid);
    gasStream.setFlowRate(2000.0, "kg/hr");
    gasStream.setPressure(45.0, "bara");
    gasStream.setTemperature(30.0, "C");
    gasStream.run();

    // Create rupture disk
    RuptureDisk disk = new RuptureDisk("RD-Test", gasStream);
    double burstPressure = 50.0; // bara
    disk.setBurstPressure(burstPressure);
    disk.setFullOpenPressure(52.5); // bara
    disk.setOutletPressure(1.0, "bara");
    disk.setCalculateSteadyState(false);

    // Test rupture disk behavior at different pressures
    System.out.println("\n===== RUPTURE DISK BEHAVIOR TEST =====");

    // Test sequence: pressure rises, then falls
    double[] testPressures = {45.0, 49.0, 50.0, 52.5, 55.0, 52.0, 50.0, 48.0, 45.0};
    UUID id = UUID.randomUUID();

    for (int i = 0; i < testPressures.length; i++) {
      gasStream.setPressure(testPressures[i], "bara");
      gasStream.run();

      disk.runTransient(0.5, id);

      System.out.printf("Pressure: %5.1f bara | Disk Opening: %6.1f %% | Has Ruptured: %s%n",
          testPressures[i], disk.getPercentValveOpening(), disk.hasRuptured() ? "YES" : "NO");

      // Verify behavior
      if (i < 2) {
        // Before burst pressure
        Assertions.assertEquals(0.0, disk.getPercentValveOpening(), 1.0,
            "Disk should be closed below burst pressure");
        Assertions.assertFalse(disk.hasRuptured(), "Disk should not be ruptured yet");
      } else if (i == 2) {
        // At burst pressure - just starting to rupture
        Assertions.assertTrue(disk.hasRuptured(), "Disk should be ruptured at burst pressure");
      } else {
        // After burst pressure exceeded - disk should be fully open forever
        Assertions.assertEquals(100.0, disk.getPercentValveOpening(), 1.0,
            "Disk should be fully open after rupturing at pressure: " + testPressures[i]);
        Assertions.assertTrue(disk.hasRuptured(), "Disk should be marked as ruptured");
      }
    }

    System.out.println("\nKey observation: Disk remained 100% open even when");
    System.out.println("pressure dropped from 55 bara back down to 45 bara.");
    System.out.println("A safety valve would have reseated at ~93% of set pressure.");
    System.out.println("========================================");
  }

  /**
   * Test rupture disk reset functionality (for simulation purposes).
   */
  @Test
  void testRuptureDiskReset() {
    SystemInterface gasFluid = new SystemSrkEos(273.15 + 30.0, 60.0);
    gasFluid.addComponent("methane", 100.0);
    gasFluid.setMixingRule(2);

    Stream gasStream = new Stream("Gas Stream", gasFluid);
    gasStream.setFlowRate(1000.0, "kg/hr");
    gasStream.setPressure(60.0, "bara");
    gasStream.setTemperature(30.0, "C");
    gasStream.run();

    RuptureDisk disk = new RuptureDisk("RD-Reset-Test", gasStream);
    disk.setBurstPressure(50.0);
    disk.setOutletPressure(1.0, "bara");
    disk.setCalculateSteadyState(false);

    UUID id = UUID.randomUUID();

    // Cause rupture
    disk.runTransient(0.1, id);

    Assertions.assertTrue(disk.hasRuptured(), "Disk should be ruptured");
    Assertions.assertEquals(100.0, disk.getPercentValveOpening(), 1.0, "Disk should be fully open");

    // Reset disk (simulates replacing the disk in real life)
    disk.reset();

    Assertions.assertFalse(disk.hasRuptured(), "Disk should not be ruptured after reset");
    Assertions.assertEquals(0.0, disk.getPercentValveOpening(), 0.1,
        "Disk should be closed after reset");

    // Lower pressure and verify it stays closed
    gasStream.setPressure(45.0, "bara");
    gasStream.run();
    disk.runTransient(0.1, id);

    Assertions.assertFalse(disk.hasRuptured(), "Disk should remain unruptured");
    Assertions.assertEquals(0.0, disk.getPercentValveOpening(), 0.1, "Disk should stay closed");
  }
}
