package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Test reproducing MATLAB scrubber carry-over calculation with UMR-PRU EOS. Based on
 * scr_test23VG01.m which uses plotCarryOver_hc('umr', ...) with gas composition from cylinder
 * TS3249905 and oil composition from AVE249_246.
 *
 * @author copilot
 * @version 1.0
 */
public class SystemUMRPRUMCEosMatlabTest {

  /**
   * Test the gas-only fluid creation and TPflash with UMR-PRU. This reproduces what
   * readFluidFromExcel + plotCarryOver_hc does for the gas phase.
   */
  @Test
  void testGasFluidWithUMRPRU() {
    // P = 8.046 bara, T = 23.88 C (from scr_test23VG01.m)
    double P = 8.046;
    double T_C = 23.88;

    // Create UMR-PRU fluid (this is what thermo('umr', ...) does)
    SystemInterface gasFluid = new SystemUMRPRUMCEos(273.15 + T_C, P);

    // Gas composition from TS3249905 column in input_compSNA.xlsx
    // Components that are NOT TBP pseudo-components (not in PseudoOilComp sheet)
    gasFluid.addComponent("oxygen", 0.182063);
    gasFluid.addComponent("nitrogen", 1.28269);
    gasFluid.addComponent("CO2", 0.560637);
    gasFluid.addComponent("methane", 49.02189);
    gasFluid.addComponent("ethane", 18.5163);
    gasFluid.addComponent("propane", 19.78237);
    gasFluid.addComponent("i-butane", 2.10052);
    gasFluid.addComponent("n-butane", 5.88119);
    gasFluid.addComponent("i-pentane", 0.90393);
    gasFluid.addComponent("n-pentane", 1.02271);
    gasFluid.addComponent("2-m-C5", 0.21896);
    gasFluid.addComponent("3-m-C5", 0.06809);
    gasFluid.addComponent("n-hexane", 0.16557);
    gasFluid.addComponent("n-heptane", 0.0601);
    gasFluid.addComponent("c-hexane", 0.15366);
    gasFluid.addComponent("benzene", 0.02059);
    gasFluid.addComponent("n-octane", 0.007073);
    gasFluid.addComponent("c-C7", 0.03659);
    gasFluid.addComponent("toluene", 0.009303);
    gasFluid.addComponent("n-nonane", 0.001313);
    gasFluid.addComponent("c-C8", 0.00183);
    gasFluid.addComponent("m-Xylene", 0.001737);

    // C10 and C11 are TBP fractions (they exist in PseudoOilComp sheet)
    gasFluid.addTBPfraction("C10", 0.000777, 134.0 / 1000.0, 0.792);
    gasFluid.addTBPfraction("C11", 0.00017, 147.0 / 1000.0, 0.796);

    // The readFluidFromExcel sets mixing rule 2 first
    gasFluid.setMixingRule(2);
    gasFluid.setMultiPhaseCheck(true);
    gasFluid.useVolumeCorrection(true);
    gasFluid.setTemperature(T_C, "C");
    gasFluid.setPressure(P, "bara");

    // Then plotCarryOver_hc overrides with UMR mixing rule
    // This is where the error occurs: "No data is available [2000-197]"
    assertDoesNotThrow(() -> {
      gasFluid.setMixingRule("HV", "UNIFAC_UMRPRU");
    }, "Setting UMR-PRU mixing rule should not throw for gas fluid");

    gasFluid.setHydrateCheck(true);
    gasFluid.useVolumeCorrection(true);
    gasFluid.setTemperature(T_C, "C");
    gasFluid.setPressure(P, "bara");

    ThermodynamicOperations ops = new ThermodynamicOperations(gasFluid);
    assertDoesNotThrow(() -> ops.TPflash(), "TPflash should not throw for gas fluid with UMR-PRU");

    gasFluid.initProperties();
    assertTrue(gasFluid.getNumberOfPhases() >= 1, "Should have at least 1 phase");
  }

  /**
   * Test the oil fluid creation and TPflash with UMR-PRU. Oil composition from AVE249_246.
   */
  @Test
  void testOilFluidWithUMRPRU() {
    double P = 8.046;
    double T_C = 23.88;

    SystemInterface oilFluid = new SystemUMRPRUMCEos(273.15 + T_C, P);

    // Oil composition from AVE249_246 column
    // Regular components (not in PseudoOilComp)
    oilFluid.addComponent("nitrogen", 0.02334);
    oilFluid.addComponent("CO2", 0.03121);
    oilFluid.addComponent("methane", 2.10824);
    oilFluid.addComponent("ethane", 4.45781);
    oilFluid.addComponent("propane", 15.89813);
    oilFluid.addComponent("i-butane", 4.05642);
    oilFluid.addComponent("n-butane", 15.94103);
    oilFluid.addComponent("i-pentane", 6.79843);
    oilFluid.addComponent("n-pentane", 10.15133);
    oilFluid.addComponent("n-hexane", 5.891019);
    oilFluid.addComponent("2-m-C5", 5.629377);
    oilFluid.addComponent("3-m-C5", 1.08754);
    oilFluid.addComponent("n-heptane", 5.9489);
    oilFluid.addComponent("c-hexane", 7.53208);
    oilFluid.addComponent("benzene", 0.6950);
    oilFluid.addComponent("n-octane", 3.10352);
    oilFluid.addComponent("c-C7", 5.02568);
    oilFluid.addComponent("toluene", 1.14571);
    oilFluid.addComponent("n-nonane", 1.45578);
    oilFluid.addComponent("c-C8", 0.91188);
    oilFluid.addComponent("m-Xylene", 0.51776);

    // TBP fractions from PseudoOilComp
    oilFluid.addTBPfraction("C10", 1.1006, 134.0 / 1000.0, 0.792);
    oilFluid.addTBPfraction("C11", 0.35833, 147.0 / 1000.0, 0.796);
    oilFluid.addTBPfraction("C12", 0.10807, 161.0 / 1000.0, 0.810);
    oilFluid.addTBPfraction("C13", 0.02233, 175.0 / 1000.0, 0.825);
    oilFluid.addTBPfraction("C14", 0.000702, 190.0 / 1000.0, 0.836);

    // Set UMR-PRU mixing rule
    assertDoesNotThrow(() -> {
      oilFluid.setMixingRule("HV", "UNIFAC_UMRPRU");
    }, "Setting UMR-PRU mixing rule should not throw for oil fluid");

    oilFluid.setMultiPhaseCheck(true);
    oilFluid.useVolumeCorrection(true);
    oilFluid.setTemperature(T_C, "C");
    oilFluid.setPressure(P, "bara");

    ThermodynamicOperations ops = new ThermodynamicOperations(oilFluid);
    assertDoesNotThrow(() -> ops.TPflash(), "TPflash should not throw for oil fluid with UMR-PRU");

    oilFluid.initProperties();
    assertTrue(oilFluid.getNumberOfPhases() >= 1, "Should have at least 1 phase");
  }

  /**
   * Test the combined scrubber fluid (gas + oil carry-over) and subcooling loop. This is the main
   * calculation in plotCarryOver_hc that was failing.
   */
  @Test
  void testScrubberFluidSubcoolingWithUMRPRU() {
    double P = 8.046;
    double T_C = 23.88;

    // --- Create gas fluid ---
    SystemInterface gasFluid = new SystemUMRPRUMCEos(273.15 + T_C, P);
    gasFluid.addComponent("oxygen", 0.182063);
    gasFluid.addComponent("nitrogen", 1.28269);
    gasFluid.addComponent("CO2", 0.560637);
    gasFluid.addComponent("methane", 49.02189);
    gasFluid.addComponent("ethane", 18.5163);
    gasFluid.addComponent("propane", 19.78237);
    gasFluid.addComponent("i-butane", 2.10052);
    gasFluid.addComponent("n-butane", 5.88119);
    gasFluid.addComponent("i-pentane", 0.90393);
    gasFluid.addComponent("n-pentane", 1.02271);
    gasFluid.addComponent("2-m-C5", 0.21896);
    gasFluid.addComponent("3-m-C5", 0.06809);
    gasFluid.addComponent("n-hexane", 0.16557);
    gasFluid.addComponent("n-heptane", 0.0601);
    gasFluid.addComponent("c-hexane", 0.15366);
    gasFluid.addComponent("benzene", 0.02059);
    gasFluid.addComponent("n-octane", 0.007073);
    gasFluid.addComponent("c-C7", 0.03659);
    gasFluid.addComponent("toluene", 0.009303);
    gasFluid.addComponent("n-nonane", 0.001313);
    gasFluid.addComponent("c-C8", 0.00183);
    gasFluid.addComponent("m-Xylene", 0.001737);
    gasFluid.addTBPfraction("C10", 0.000777, 134.0 / 1000.0, 0.792);
    gasFluid.addTBPfraction("C11", 0.00017, 147.0 / 1000.0, 0.796);

    gasFluid.setMixingRule("HV", "UNIFAC_UMRPRU");
    gasFluid.setMultiPhaseCheck(true);
    gasFluid.setHydrateCheck(true);
    gasFluid.useVolumeCorrection(true);
    gasFluid.setTemperature(T_C, "C");
    gasFluid.setPressure(P, "bara");

    ThermodynamicOperations gasOps = new ThermodynamicOperations(gasFluid);
    gasOps.TPflash();
    gasFluid.initProperties();

    // --- Create oil fluid ---
    SystemInterface oilFluid = new SystemUMRPRUMCEos(273.15 + T_C, P);
    oilFluid.addComponent("nitrogen", 0.02334);
    oilFluid.addComponent("CO2", 0.03121);
    oilFluid.addComponent("methane", 2.10824);
    oilFluid.addComponent("ethane", 4.45781);
    oilFluid.addComponent("propane", 15.89813);
    oilFluid.addComponent("i-butane", 4.05642);
    oilFluid.addComponent("n-butane", 15.94103);
    oilFluid.addComponent("i-pentane", 6.79843);
    oilFluid.addComponent("n-pentane", 10.15133);
    oilFluid.addComponent("n-hexane", 5.891019);
    oilFluid.addComponent("2-m-C5", 5.629377);
    oilFluid.addComponent("3-m-C5", 1.08754);
    oilFluid.addComponent("n-heptane", 5.9489);
    oilFluid.addComponent("c-hexane", 7.53208);
    oilFluid.addComponent("benzene", 0.6950);
    oilFluid.addComponent("n-octane", 3.10352);
    oilFluid.addComponent("c-C7", 5.02568);
    oilFluid.addComponent("toluene", 1.14571);
    oilFluid.addComponent("n-nonane", 1.45578);
    oilFluid.addComponent("c-C8", 0.91188);
    oilFluid.addComponent("m-Xylene", 0.51776);
    oilFluid.addTBPfraction("C10", 1.1006, 134.0 / 1000.0, 0.792);
    oilFluid.addTBPfraction("C11", 0.35833, 147.0 / 1000.0, 0.796);
    oilFluid.addTBPfraction("C12", 0.10807, 161.0 / 1000.0, 0.810);
    oilFluid.addTBPfraction("C13", 0.02233, 175.0 / 1000.0, 0.825);
    oilFluid.addTBPfraction("C14", 0.000702, 190.0 / 1000.0, 0.836);

    oilFluid.setMixingRule("HV", "UNIFAC_UMRPRU");
    oilFluid.setMultiPhaseCheck(true);
    oilFluid.useVolumeCorrection(true);
    oilFluid.setTemperature(T_C, "C");
    oilFluid.setPressure(P, "bara");

    ThermodynamicOperations oilOps = new ThermodynamicOperations(oilFluid);
    oilOps.TPflash();
    oilFluid.initProperties();

    // --- Combine gas + oil (scrubber fluid) ---
    // addFluid adds TBP components from oil that don't exist in gas (C12_PC, C13_PC, C14_PC).
    // This previously caused NullPointerException in setAcentricFactor when adding to hydrate
    // phase.
    SystemInterface scrubberFluid = gasFluid.clone();
    assertDoesNotThrow(() -> scrubberFluid.addFluid(oilFluid),
        "addFluid should not throw when combining UMR-PRU fluids with different TBP components");

    ThermodynamicOperations scrubOps = new ThermodynamicOperations(scrubberFluid);
    scrubberFluid.setMultiPhaseCheck(true);
    scrubberFluid.useVolumeCorrection(true);
    scrubberFluid.setTemperature(T_C, "C");
    scrubberFluid.setPressure(P, "bara");
    scrubOps.TPflash();
    scrubberFluid.initProperties();

    assertTrue(scrubberFluid.getNumberOfPhases() >= 1, "Should have at least 1 phase");

    // --- Subcooling sweep (this is the core operation) ---
    double[] temperatures = {21.0, 22.0, 23.0, 24.0, 25.0, 26.0, 27.0, 28.0, 29.0, 30.0};
    for (double subcoolT : temperatures) {
      SystemInterface subcooledFluid = scrubberFluid.clone();
      subcooledFluid.setTemperature(subcoolT, "C");
      subcooledFluid.setPressure(P, "bara");

      ThermodynamicOperations subOps = new ThermodynamicOperations(subcooledFluid);
      assertDoesNotThrow(() -> subOps.TPflash(),
          "TPflash should not throw at T=" + subcoolT + " C");

      if (subcooledFluid.hasPhaseType("oil")) {
        double oilVolFrac = subcooledFluid.getPhase("oil").getVolume("litre");
        assertTrue(oilVolFrac >= 0, "Oil volume should be non-negative at T=" + subcoolT);
      }
    }
  }

  /**
   * Test dew point temperature calculation with UMR-PRU. The dewt() function in MATLAB calls
   * SaturationTemperature which internally resets the mixing rule.
   */
  @Test
  void testDewPointTempWithUMRPRU() {
    double P = 8.046;
    double T_C = 23.88;

    SystemInterface gasFluid = new SystemUMRPRUMCEos(273.15 + T_C, P);
    gasFluid.addComponent("oxygen", 0.182063);
    gasFluid.addComponent("nitrogen", 1.28269);
    gasFluid.addComponent("CO2", 0.560637);
    gasFluid.addComponent("methane", 49.02189);
    gasFluid.addComponent("ethane", 18.5163);
    gasFluid.addComponent("propane", 19.78237);
    gasFluid.addComponent("i-butane", 2.10052);
    gasFluid.addComponent("n-butane", 5.88119);
    gasFluid.addComponent("i-pentane", 0.90393);
    gasFluid.addComponent("n-pentane", 1.02271);
    gasFluid.addComponent("2-m-C5", 0.21896);
    gasFluid.addComponent("3-m-C5", 0.06809);
    gasFluid.addComponent("n-hexane", 0.16557);
    gasFluid.addComponent("n-heptane", 0.0601);
    gasFluid.addComponent("c-hexane", 0.15366);
    gasFluid.addComponent("benzene", 0.02059);
    gasFluid.addComponent("n-octane", 0.007073);
    gasFluid.addComponent("c-C7", 0.03659);
    gasFluid.addComponent("toluene", 0.009303);
    gasFluid.addComponent("n-nonane", 0.001313);
    gasFluid.addComponent("c-C8", 0.00183);
    gasFluid.addComponent("m-Xylene", 0.001737);
    gasFluid.addTBPfraction("C10", 0.000777, 134.0 / 1000.0, 0.792);
    gasFluid.addTBPfraction("C11", 0.00017, 147.0 / 1000.0, 0.796);

    gasFluid.setMixingRule("HV", "UNIFAC_UMRPRU");
    gasFluid.setMultiPhaseCheck(true);
    gasFluid.useVolumeCorrection(true);
    gasFluid.setTemperature(T_C, "C");
    gasFluid.setPressure(P, "bara");

    ThermodynamicOperations ops = new ThermodynamicOperations(gasFluid);
    ops.TPflash();
    gasFluid.initProperties();

    // Try dew point calculation
    assertDoesNotThrow(() -> {
      ops.dewPointTemperatureFlash();
    }, "Dew point temperature flash should not throw with UMR-PRU");

    double dewT = gasFluid.getTemperature() - 273.15;
    System.out.println("Dew point temperature (UMR-PRU): " + dewT + " C");
    assertTrue(dewT > -50 && dewT < 50, "Dew point should be in reasonable range, got: " + dewT);
  }
}
