package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Unit tests for Rhone-Poulenc maximum velocity calculations in gas pipes.
 */
public class RhonePoulencVelocityTest {

  @Nested
  @DisplayName("RhonePoulencVelocity standalone calculator tests")
  class CalculatorTests {

    @Test
    @DisplayName("Non-corrosive gas: power-law formula at various densities")
    void testNonCorrosivePowerLaw() {
      RhonePoulencVelocity calc =
          new RhonePoulencVelocity(RhonePoulencVelocity.ServiceType.NON_CORROSIVE_GAS);

      // At density=1 kg/m3: V = 60 * 1^(-0.44) = 60 m/s (upper limit)
      assertEquals(60.0, calc.getMaxVelocity(1.0), 0.1);

      // At density=10 kg/m3: V = 60 * 10^(-0.44) ≈ 21.8 m/s
      double v10 = calc.getMaxVelocity(10.0);
      assertTrue(v10 > 20.0 && v10 < 25.0,
          "At 10 kg/m3, expected ~22 m/s but got " + v10);

      // At density=100 kg/m3: V = 60 * 100^(-0.44) ≈ 7.9 m/s
      double v100 = calc.getMaxVelocity(100.0);
      assertTrue(v100 > 6.0 && v100 < 10.0,
          "At 100 kg/m3, expected ~8 m/s but got " + v100);

      // At density=500 kg/m3: V = 60 * 500^(-0.44) ≈ 3.5 m/s, min is 3 m/s
      double v500 = calc.getMaxVelocity(500.0);
      assertTrue(v500 >= 3.0, "Should respect minimum velocity limit of 3 m/s");
    }

    @Test
    @DisplayName("Corrosive gas: lower velocity limits")
    void testCorrosivePowerLaw() {
      RhonePoulencVelocity calc =
          new RhonePoulencVelocity(RhonePoulencVelocity.ServiceType.CORROSIVE_GAS);

      // At density=1 kg/m3: V = 30 * 1^(-0.44) = 30 m/s (upper limit)
      assertEquals(30.0, calc.getMaxVelocity(1.0), 0.1);

      // Corrosive should always be less than non-corrosive
      RhonePoulencVelocity nonCorrosive =
          new RhonePoulencVelocity(RhonePoulencVelocity.ServiceType.NON_CORROSIVE_GAS);
      double[] densities = {1.0, 5.0, 10.0, 50.0, 100.0, 300.0};
      for (double rho : densities) {
        assertTrue(calc.getMaxVelocity(rho) <= nonCorrosive.getMaxVelocity(rho),
            "Corrosive velocity should be <= non-corrosive at density=" + rho);
      }
    }

    @Test
    @DisplayName("Velocity decreases monotonically with increasing density")
    void testMonotonicDecrease() {
      RhonePoulencVelocity calc =
          new RhonePoulencVelocity(RhonePoulencVelocity.ServiceType.NON_CORROSIVE_GAS);

      double prevVelocity = Double.MAX_VALUE;
      double[] densities = {0.5, 1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 500.0, 1000.0};
      for (double rho : densities) {
        double v = calc.getMaxVelocity(rho);
        assertTrue(v <= prevVelocity,
            "Velocity should decrease with density: at rho=" + rho + " got v=" + v);
        prevVelocity = v;
      }
    }

    @Test
    @DisplayName("Log-log interpolation matches tabulated data points")
    void testInterpolationMatchesTable() {
      RhonePoulencVelocity calc =
          new RhonePoulencVelocity(RhonePoulencVelocity.ServiceType.NON_CORROSIVE_GAS);
      calc.setUseInterpolation(true);

      // Exact table points should return exact values
      assertEquals(60.0, calc.getMaxVelocity(1.0), 0.01);
      assertEquals(45.0, calc.getMaxVelocity(2.0), 0.01);
      assertEquals(30.0, calc.getMaxVelocity(5.0), 0.01);
      assertEquals(22.0, calc.getMaxVelocity(10.0), 0.01);
      assertEquals(16.0, calc.getMaxVelocity(20.0), 0.01);
      assertEquals(11.0, calc.getMaxVelocity(50.0), 0.01);
      assertEquals(8.0, calc.getMaxVelocity(100.0), 0.01);
      assertEquals(5.5, calc.getMaxVelocity(200.0), 0.01);
      assertEquals(3.5, calc.getMaxVelocity(500.0), 0.01);
    }

    @Test
    @DisplayName("Interpolated values between table points are reasonable")
    void testInterpolationBetweenPoints() {
      RhonePoulencVelocity calc =
          new RhonePoulencVelocity(RhonePoulencVelocity.ServiceType.NON_CORROSIVE_GAS);
      calc.setUseInterpolation(true);

      // Between 10 and 20 kg/m3: should be between 16 and 22 m/s
      double v15 = calc.getMaxVelocity(15.0);
      assertTrue(v15 > 16.0 && v15 < 22.0,
          "At 15 kg/m3, expected between 16-22 m/s but got " + v15);

      // Between 50 and 100 kg/m3: should be between 8 and 11 m/s
      double v75 = calc.getMaxVelocity(75.0);
      assertTrue(v75 > 8.0 && v75 < 11.0,
          "At 75 kg/m3, expected between 8-11 m/s but got " + v75);
    }

    @Test
    @DisplayName("Invalid density throws exception")
    void testInvalidDensity() {
      RhonePoulencVelocity calc = new RhonePoulencVelocity();
      assertThrows(IllegalArgumentException.class, () -> calc.getMaxVelocity(0.0));
      assertThrows(IllegalArgumentException.class, () -> calc.getMaxVelocity(-5.0));
    }

    @Test
    @DisplayName("Custom C-factor and exponent override service type defaults")
    void testCustomParameters() {
      RhonePoulencVelocity calc = new RhonePoulencVelocity();
      calc.setCustomCFactor(40.0);
      calc.setCustomExponent(0.5);

      // V_max = 40 / sqrt(rho), bounded by service type limits
      double v10 = calc.getMaxVelocity(10.0);
      // 40 / sqrt(10) ≈ 12.65
      assertEquals(40.0 / Math.sqrt(10.0), v10, 0.1);
    }

    @Test
    @DisplayName("Static convenience methods work correctly")
    void testStaticMethods() {
      double vNonCorrosive = RhonePoulencVelocity.getMaxVelocityNonCorrosive(10.0);
      double vCorrosive = RhonePoulencVelocity.getMaxVelocityCorrosive(10.0);

      assertTrue(vNonCorrosive > 0, "Non-corrosive velocity should be positive");
      assertTrue(vCorrosive > 0, "Corrosive velocity should be positive");
      assertTrue(vNonCorrosive > vCorrosive,
          "Non-corrosive should be higher than corrosive");
    }

    @Test
    @DisplayName("Description string contains key information")
    void testDescription() {
      RhonePoulencVelocity calc =
          new RhonePoulencVelocity(RhonePoulencVelocity.ServiceType.CORROSIVE_GAS);
      String desc = calc.getDescription();

      assertTrue(desc.contains("Rhone-Poulenc"), "Should mention Rhone-Poulenc");
      assertTrue(desc.contains("CORROSIVE_GAS"), "Should mention service type");
    }

    @Test
    @DisplayName("Default constructor creates non-corrosive gas calculator")
    void testDefaultConstructor() {
      RhonePoulencVelocity calc = new RhonePoulencVelocity();
      assertEquals(RhonePoulencVelocity.ServiceType.NON_CORROSIVE_GAS,
          calc.getServiceType());
      assertFalse(calc.isUseInterpolation());
    }
  }

  @Nested
  @DisplayName("Integration with AdiabaticPipe")
  class AdiabaticPipeTests {

    @Test
    @DisplayName("Enable/disable Rhone-Poulenc on AdiabaticPipe")
    void testEnableDisableOnPipe() {
      SystemInterface gas = new SystemSrkEos(278.15, 50.0);
      gas.addComponent("methane", 0.95);
      gas.addComponent("ethane", 0.05);
      gas.setMixingRule("classic");
      gas.setTotalFlowRate(100000.0, "kg/hr");

      ThermodynamicOperations ops = new ThermodynamicOperations(gas);
      ops.TPflash();
      gas.initPhysicalProperties();

      Stream inlet = new Stream("inlet", gas);
      AdiabaticPipe pipe = new AdiabaticPipe("gas pipe", inlet);
      pipe.setLength(10000.0);
      pipe.setDiameter(0.5);
      pipe.setPipeWallRoughness(5e-6);

      // By default, Rhone-Poulenc should be off
      assertFalse(pipe.isRhonePoulencEnabled());
      assertEquals("API_RP_14E", pipe.getMaxVelocityMethod());
      assertNull(pipe.getRhonePoulencCalculator());

      // Enable Rhone-Poulenc
      pipe.useRhonePoulencVelocity();
      assertTrue(pipe.isRhonePoulencEnabled());
      assertEquals("RHONE_POULENC", pipe.getMaxVelocityMethod());
      assertNotNull(pipe.getRhonePoulencCalculator());

      // Disable
      pipe.disableRhonePoulencVelocity();
      assertFalse(pipe.isRhonePoulencEnabled());
      assertEquals("API_RP_14E", pipe.getMaxVelocityMethod());
    }

    @Test
    @DisplayName("Rhone-Poulenc max velocity calculation after pipe run")
    void testRhonePoulencAfterRun() {
      SystemInterface gas = new SystemSrkEos(278.15, 50.0);
      gas.addComponent("methane", 0.95);
      gas.addComponent("ethane", 0.05);
      gas.setMixingRule("classic");
      gas.setTotalFlowRate(50000.0, "kg/hr");

      ThermodynamicOperations ops = new ThermodynamicOperations(gas);
      ops.TPflash();
      gas.initPhysicalProperties();

      Stream inlet = new Stream("inlet", gas);
      AdiabaticPipe pipe = new AdiabaticPipe("gas pipe", inlet);
      pipe.setLength(50000.0);
      pipe.setDiameter(0.4);
      pipe.setPipeWallRoughness(5e-6);
      pipe.setRhonePoulencServiceType(
          RhonePoulencVelocity.ServiceType.NON_CORROSIVE_GAS);

      ProcessSystem process = new ProcessSystem();
      process.add(inlet);
      process.add(pipe);
      process.run();

      // After running, Rhone-Poulenc max velocity should be calculated
      double rpMaxVel = pipe.getRhonePoulencMaxVelocity();
      assertTrue(rpMaxVel > 0, "Rhone-Poulenc max velocity should be positive after run");

      // Max allowable should use Rhone-Poulenc
      double maxAllowable = pipe.getMaxAllowableVelocity();
      assertEquals(rpMaxVel, maxAllowable, 0.001,
          "Max allowable should equal Rhone-Poulenc velocity when enabled");

      // FIV analysis should contain Rhone-Poulenc info
      java.util.Map<String, Object> fivAnalysis = pipe.getFIVAnalysis();
      assertEquals("RHONE_POULENC", fivAnalysis.get("maxVelocityMethod"));
      assertTrue(fivAnalysis.containsKey("rhonePoulencMaxVelocity_m_s"));
      assertTrue(fivAnalysis.containsKey("rhonePoulencServiceType"));
    }

    @Test
    @DisplayName("Corrosive service gives lower max velocity than non-corrosive")
    void testCorrosiveVsNonCorrosive() {
      SystemInterface gas = new SystemSrkEos(278.15, 50.0);
      gas.addComponent("methane", 0.95);
      gas.addComponent("ethane", 0.05);
      gas.setMixingRule("classic");
      gas.setTotalFlowRate(50000.0, "kg/hr");

      ThermodynamicOperations ops = new ThermodynamicOperations(gas);
      ops.TPflash();
      gas.initPhysicalProperties();

      Stream inlet = new Stream("inlet", gas);
      AdiabaticPipe pipe = new AdiabaticPipe("gas pipe", inlet);
      pipe.setLength(50000.0);
      pipe.setDiameter(0.4);
      pipe.setPipeWallRoughness(5e-6);

      ProcessSystem process = new ProcessSystem();
      process.add(inlet);
      process.add(pipe);
      process.run();

      // Non-corrosive
      pipe.setRhonePoulencServiceType(
          RhonePoulencVelocity.ServiceType.NON_CORROSIVE_GAS);
      double nonCorrosiveVel = pipe.getRhonePoulencMaxVelocity();

      // Corrosive
      pipe.setRhonePoulencServiceType(
          RhonePoulencVelocity.ServiceType.CORROSIVE_GAS);
      double corrosiveVel = pipe.getRhonePoulencMaxVelocity();

      assertTrue(nonCorrosiveVel > corrosiveVel,
          "Non-corrosive max velocity (" + nonCorrosiveVel
              + ") should be higher than corrosive (" + corrosiveVel + ")");
    }
  }

  @Nested
  @DisplayName("Integration with PipeBeggsAndBrills")
  class BeggsAndBrillsTests {

    @Test
    @DisplayName("Rhone-Poulenc on PipeBeggsAndBrills")
    void testRhonePoulencOnBeggsAndBrills() {
      SystemInterface gas = new SystemSrkEos(298.15, 60.0);
      gas.addComponent("methane", 0.90);
      gas.addComponent("ethane", 0.07);
      gas.addComponent("propane", 0.03);
      gas.setMixingRule("classic");
      gas.setTotalFlowRate(30000.0, "kg/hr");

      ThermodynamicOperations ops = new ThermodynamicOperations(gas);
      ops.TPflash();
      gas.initPhysicalProperties();

      Stream inlet = new Stream("inlet", gas);
      PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("gas pipe", inlet);
      pipe.setDiameter(0.3);
      pipe.setThickness(0.012);
      pipe.setLength(5000.0);
      pipe.setElevation(0.0);
      pipe.setPipeWallRoughness(1.0e-5);
      pipe.setNumberOfIncrements(5);

      pipe.setRhonePoulencServiceType(
          RhonePoulencVelocity.ServiceType.NON_CORROSIVE_GAS);

      ProcessSystem process = new ProcessSystem();
      process.add(inlet);
      process.add(pipe);
      process.run();

      // Both velocities should be available
      double erosionalVel = pipe.getErosionalVelocity();
      double rpMaxVel = pipe.getRhonePoulencMaxVelocity();
      double maxAllowable = pipe.getMaxAllowableVelocity();

      assertTrue(erosionalVel > 0, "Erosional velocity should be positive");
      assertTrue(rpMaxVel > 0, "Rhone-Poulenc max velocity should be positive");
      assertEquals(rpMaxVel, maxAllowable, 0.001,
          "Max allowable should use Rhone-Poulenc when enabled");
      assertEquals("RHONE_POULENC", pipe.getMaxVelocityMethod());

      // FIV analysis should include Rhone-Poulenc info
      java.util.Map<String, Object> fiv = pipe.getFIVAnalysis();
      assertNotNull(fiv.get("rhonePoulencMaxVelocity_m_s"));
      assertEquals("NON_CORROSIVE_GAS", fiv.get("rhonePoulencServiceType"));
    }

    @Test
    @DisplayName("Without Rhone-Poulenc, max allowable equals erosional velocity")
    void testDefaultUsesErosional() {
      SystemInterface gas = new SystemSrkEos(298.15, 60.0);
      gas.addComponent("methane", 0.90);
      gas.addComponent("ethane", 0.07);
      gas.addComponent("propane", 0.03);
      gas.setMixingRule("classic");
      gas.setTotalFlowRate(30000.0, "kg/hr");

      ThermodynamicOperations ops = new ThermodynamicOperations(gas);
      ops.TPflash();
      gas.initPhysicalProperties();

      Stream inlet = new Stream("inlet", gas);
      PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("gas pipe", inlet);
      pipe.setDiameter(0.3);
      pipe.setThickness(0.012);
      pipe.setLength(5000.0);
      pipe.setElevation(0.0);
      pipe.setPipeWallRoughness(1.0e-5);
      pipe.setNumberOfIncrements(5);

      ProcessSystem process = new ProcessSystem();
      process.add(inlet);
      process.add(pipe);
      process.run();

      // Without Rhone-Poulenc, max allowable should default to erosional
      double erosionalVel = pipe.getErosionalVelocity();
      double maxAllowable = pipe.getMaxAllowableVelocity();

      assertEquals(erosionalVel, maxAllowable, 0.001,
          "Without Rhone-Poulenc, max allowable should equal erosional velocity");
      assertEquals("API_RP_14E", pipe.getMaxVelocityMethod());
    }

    @Test
    @DisplayName("Tabulated interpolation mode on PipeBeggsAndBrills")
    void testInterpolationModeOnBeggsAndBrills() {
      SystemInterface gas = new SystemSrkEos(298.15, 60.0);
      gas.addComponent("methane", 0.90);
      gas.addComponent("ethane", 0.07);
      gas.addComponent("propane", 0.03);
      gas.setMixingRule("classic");
      gas.setTotalFlowRate(30000.0, "kg/hr");

      ThermodynamicOperations ops = new ThermodynamicOperations(gas);
      ops.TPflash();
      gas.initPhysicalProperties();

      Stream inlet = new Stream("inlet", gas);
      PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("gas pipe", inlet);
      pipe.setDiameter(0.3);
      pipe.setThickness(0.012);
      pipe.setLength(5000.0);
      pipe.setElevation(0.0);
      pipe.setPipeWallRoughness(1.0e-5);
      pipe.setNumberOfIncrements(5);

      // Enable with interpolation
      pipe.setRhonePoulencServiceType(
          RhonePoulencVelocity.ServiceType.NON_CORROSIVE_GAS, true);

      ProcessSystem process = new ProcessSystem();
      process.add(inlet);
      process.add(pipe);
      process.run();

      double rpMaxVel = pipe.getRhonePoulencMaxVelocity();
      assertTrue(rpMaxVel > 0, "Interpolated Rhone-Poulenc velocity should be positive");
      assertTrue(pipe.getRhonePoulencCalculator().isUseInterpolation(),
          "Interpolation mode should be active");
    }
  }
}
