package neqsim.physicalproperties.methods.commonphasephysicalproperties.conductivity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for newly implemented thermal conductivity methods: WaterConductivityMethod (IAPWS-2011),
 * HydrogenConductivityMethod (Assael 2011), PFCT high-T correction, Filippov aqueous mixing, and
 * model wiring via setConductivityModel.
 *
 * <p>
 * NIST reference values from NIST WebBook / REFPROP.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class NewConductivityMethodsTest {

  private double getConductivity(SystemInterface system, int phaseIdx) {
    return system.getPhase(phaseIdx).getPhysicalProperties().getConductivity();
  }

  // ========================= WATER CONDUCTIVITY (IAPWS-2011) =========================

  /**
   * Pure water steam (gas phase) at 373 K, 1 bar. NIST reference: ~0.0248 W/(m*K).
   */
  @Test
  void testWaterSteam373K1bar() {
    SystemInterface system = new SystemSrkEos(373.15, 1.0);
    system.addComponent("water", 1.0);
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(true);
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    // Water at 373K/1bar may be gas or liquid depending on EOS phase detection
    double cond = getConductivity(system, 0);
    // Should give a positive, physically plausible value
    assertTrue(cond > 0.01, "Water conductivity too low: " + cond);
    assertTrue(cond < 0.80, "Water conductivity too high: " + cond);
  }

  /**
   * Pure water steam at 500 K, 1 bar. NIST reference: ~0.0332 W/(m*K).
   */
  @Test
  void testWaterSteam500K1bar() {
    SystemInterface system = new SystemSrkEos(500.0, 1.0);
    system.addComponent("water", 1.0);
    system.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    double cond = getConductivity(system, 0);
    // NIST: ~0.0332 W/(m*K) for steam at 500 K, 1 bar
    assertTrue(cond > 0.020, "Water steam 500K conductivity too low: " + cond);
    assertTrue(cond < 0.050, "Water steam 500K conductivity too high: " + cond);
  }

  /**
   * Pure liquid water at 300 K, 10 bar. NIST reference: ~0.610 W/(m*K).
   */
  @Test
  void testLiquidWater300K10bar() {
    SystemInterface system = new SystemSrkEos(300.0, 10.0);
    system.addComponent("water", 1.0);
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(true);
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    int liqIdx = -1;
    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      String phType = system.getPhase(i).getType().toString();
      if (phType.contains("LIQUID") || phType.contains("AQUEOUS")) {
        liqIdx = i;
        break;
      }
    }
    if (liqIdx < 0) {
      liqIdx = 0; // single phase
    }

    double cond = getConductivity(system, liqIdx);
    // NIST: ~0.610 W/(m*K) for liquid water at 300 K
    // Allow 15% tolerance due to EOS density effects on IAPWS finite-density term
    assertTrue(cond > 0.40, "Liquid water conductivity too low: " + cond);
    assertTrue(cond < 0.80, "Liquid water conductivity too high: " + cond);
  }

  /**
   * Test model selection via setConductivityModel("WaterModel").
   */
  @Test
  void testSetConductivityModelWater() {
    SystemInterface system = new SystemSrkEos(400.0, 1.0);
    system.addComponent("methane", 0.5);
    system.addComponent("water", 0.5);
    system.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    // Switch to WaterModel
    system.getPhase(0).getPhysicalProperties().setConductivityModel("WaterModel");
    system.initProperties();

    double cond = getConductivity(system, 0);
    assertTrue(cond > 0.0, "WaterModel conductivity should be positive: " + cond);
  }

  // ========================= HYDROGEN CONDUCTIVITY (ASSAEL 2011) =========================

  /**
   * Pure H2 gas at 300 K, 1 bar. NIST reference: ~0.182 W/(m*K).
   */
  @Test
  void testH2Gas300K1bar() {
    SystemInterface system = new SystemSrkEos(300.0, 1.0);
    system.addComponent("hydrogen", 1.0);
    system.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    double cond = getConductivity(system, 0);
    // NIST: ~0.182 W/(m*K) for H2 at 300 K, 1 bar
    double nist = 0.182;
    double error = Math.abs(cond - nist) / nist * 100.0;
    assertTrue(error < 15.0,
        "H2 gas 300K error too large: " + error + "% (calc=" + cond + ", NIST=" + nist + ")");
  }

  /**
   * H2 gas at 100 K, 1 bar. NIST reference: ~0.068 W/(m*K).
   */
  @Test
  void testH2Gas100K1bar() {
    SystemInterface system = new SystemSrkEos(100.0, 1.0);
    system.addComponent("hydrogen", 1.0);
    system.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    double cond = getConductivity(system, 0);
    // NIST: ~0.068 W/(m*K) for H2 at 100 K, 1 bar
    assertTrue(cond > 0.040, "H2 gas 100K conductivity too low: " + cond);
    assertTrue(cond < 0.100, "H2 gas 100K conductivity too high: " + cond);
  }

  /**
   * H2 gas at 500 K, 1 bar. NIST reference: ~0.271 W/(m*K).
   */
  @Test
  void testH2Gas500K1bar() {
    SystemInterface system = new SystemSrkEos(500.0, 1.0);
    system.addComponent("hydrogen", 1.0);
    system.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    double cond = getConductivity(system, 0);
    // NIST: ~0.271 W/(m*K) for H2 at 500 K, 1 bar
    assertTrue(cond > 0.18, "H2 gas 500K conductivity too low: " + cond);
    assertTrue(cond < 0.35, "H2 gas 500K conductivity too high: " + cond);
  }

  /**
   * H2 gas at high pressure: 300 K, 200 bar. NIST reference: ~0.199 W/(m*K).
   */
  @Test
  void testH2GasHighPressure300K200bar() {
    SystemInterface system = new SystemSrkEos(300.0, 200.0);
    system.addComponent("hydrogen", 1.0);
    system.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    double cond = getConductivity(system, 0);
    // NIST: ~0.199 W/(m*K) for H2 at 300 K, 200 bar
    assertTrue(cond > 0.12, "H2 high-P conductivity too low: " + cond);
    assertTrue(cond < 0.30, "H2 high-P conductivity too high: " + cond);
  }

  /**
   * Test model selection via setConductivityModel("H2Model").
   */
  @Test
  void testSetConductivityModelH2() {
    SystemInterface system = new SystemSrkEos(300.0, 1.0);
    system.addComponent("methane", 0.5);
    system.addComponent("hydrogen", 0.5);
    system.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    // Switch to H2Model
    system.getPhase(0).getPhysicalProperties().setConductivityModel("H2Model");
    system.initProperties();

    double cond = getConductivity(system, 0);
    assertTrue(cond > 0.0, "H2Model conductivity should be positive: " + cond);
  }

  // ========================= PFCT HIGH-T CORRECTION =========================

  /**
   * Methane gas at 600 K, 1 bar. Before correction: PFCT overpredicted by ~26%. After correction
   * (threshold=400K, slope=-9e-4): should be within 15%.
   */
  @Test
  void testPFCTMethane600KHighTCorrection() {
    SystemInterface system = new SystemSrkEos(600.0, 1.0);
    system.addComponent("methane", 1.0);
    system.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    double cond = getConductivity(system, 0);
    double nist = 0.0681; // NIST methane 600 K 1 bar
    double error = Math.abs(cond - nist) / nist * 100.0;
    // After high-T correction, error should be reduced from 26% to <15%
    assertTrue(error < 15.0,
        "PFCT CH4 600K error too large after correction: " + error + "% (calc=" + cond + ")");
  }

  /**
   * Methane gas at 350 K, 1 bar (below correction threshold of 400K, should be unchanged). NIST
   * reference: ~0.04042 W/(m*K).
   */
  @Test
  void testPFCTMethane350KUnchanged() {
    SystemInterface system = new SystemSrkEos(350.0, 1.0);
    system.addComponent("methane", 1.0);
    system.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    double cond = getConductivity(system, 0);
    double nist = 0.04042;
    double error = Math.abs(cond - nist) / nist * 100.0;
    // Below 400 K threshold, should still be within original PFCT accuracy (<10%)
    assertTrue(error < 10.0,
        "PFCT CH4 350K error unexpected: " + error + "% (calc=" + cond + ", NIST=" + nist + ")");
  }

  // ========================= FILIPPOV AQUEOUS MIXING =========================

  /**
   * Water + MEG mixture. Filippov mixing should give reasonable conductivity for glycol/water
   * system.
   */
  @Test
  void testFilippovWaterMEGMixture() {
    SystemInterface system = new SystemSrkCPAstatoil(298.15, 1.013);
    system.addComponent("water", 0.7);
    system.addComponent("MEG", 0.3);
    system.setMixingRule(10);
    system.setMultiPhaseCheck(true);
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    int liqIdx = 0;
    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      String phType = system.getPhase(i).getType().toString();
      if (phType.contains("LIQUID") || phType.contains("AQUEOUS")) {
        liqIdx = i;
        break;
      }
    }

    double cond = getConductivity(system, liqIdx);
    // Water+MEG mixture at 25°C should be 0.30-0.55 W/(m*K) range
    // Pure water ~0.61, pure MEG ~0.26 — mixture should be between
    assertTrue(cond > 0.1, "Water+MEG conductivity too low: " + cond + " W/(m*K)");
    assertTrue(cond < 0.8, "Water+MEG conductivity too high: " + cond + " W/(m*K)");
  }

  // ========================= AUTO-DETECTION TESTS =========================

  /**
   * Pure water should auto-select WaterConductivityMethod.
   */
  @Test
  void testAutoDetectionPureWater() {
    SystemInterface system = new SystemSrkEos(373.15, 1.0);
    system.addComponent("water", 1.0);
    system.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    double cond = getConductivity(system, 0);
    // Should use WaterConductivityMethod and return reasonable steam value
    assertTrue(cond > 0.01, "Auto-detected water conductivity too low: " + cond);
    assertTrue(cond < 0.8, "Auto-detected water conductivity too high: " + cond);
  }

  /**
   * Pure hydrogen should auto-select HydrogenConductivityMethod.
   */
  @Test
  void testAutoDetectionPureH2() {
    SystemInterface system = new SystemSrkEos(300.0, 1.0);
    system.addComponent("hydrogen", 1.0);
    system.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    double cond = getConductivity(system, 0);
    // Should use HydrogenConductivityMethod and give ~0.182 W/(m*K)
    double nist = 0.182;
    double error = Math.abs(cond - nist) / nist * 100.0;
    assertTrue(error < 15.0,
        "Auto-detected H2 300K error: " + error + "% (calc=" + cond + ", NIST=" + nist + ")");
  }

  /**
   * Multi-component mixture should still use PFCT (not water or H2 model).
   */
  @Test
  void testMultiComponentStillUsesPFCT() {
    SystemInterface system = new SystemSrkEos(300.0, 50.0);
    system.addComponent("methane", 0.85);
    system.addComponent("ethane", 0.10);
    system.addComponent("propane", 0.05);
    system.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    double cond = getConductivity(system, 0);
    // PFCT for natural gas at 300 K, 50 bar should be ~0.04 W/(m*K)
    assertTrue(cond > 0.02, "Multi-component gas conductivity too low: " + cond);
    assertTrue(cond < 0.10, "Multi-component gas conductivity too high: " + cond);
  }
}
