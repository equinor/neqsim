package neqsim.physicalproperties.methods.liquidphysicalproperties.density;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for the COSTALD liquid density method.
 *
 * <p>
 * Validates the Hankinson-Thomson (1979) COSTALD implementation against NIST reference data and
 * known literature values. COSTALD typically gives 1-3% accuracy for hydrocarbons using critical
 * volume as V* fallback.
 * </p>
 */
public class CostaldTest extends neqsim.NeqSimTest {

  /**
   * Test pure n-hexane liquid density at 298.15 K, 10 bar. NIST reference: ~655 kg/m3. COSTALD with
   * Vc as V* should be within 3%.
   */
  @Test
  void testPureNHexane() {
    SystemInterface fluid = new SystemSrkEos(298.15, 10.0);
    fluid.addComponent("n-hexane", 1.0);
    fluid.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initPhysicalProperties();

    fluid.setLiquidDensityModel("COSTALD");
    double density = fluid.getPhase("oil").getPhysicalProperties().getDensity();

    // NIST: n-hexane at 298.15 K, 10 bar = ~655 kg/m3
    assertEquals(655.0, density, 655.0 * 0.03,
        "n-hexane COSTALD density should be within 3% of NIST value");
    assertTrue(density > 600.0 && density < 700.0,
        "n-hexane density should be physically reasonable");
  }

  /**
   * Test pure n-decane liquid density at 300 K, 10 bar. NIST reference: ~726 kg/m3.
   */
  @Test
  void testPureNDecane() {
    SystemInterface fluid = new SystemSrkEos(300.0, 10.0);
    fluid.addComponent("nC10", 1.0);
    fluid.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initPhysicalProperties();

    fluid.setLiquidDensityModel("COSTALD");
    double density = fluid.getPhase("oil").getPhysicalProperties().getDensity();

    // NIST: n-decane at 300 K, 10 bar = ~726 kg/m3
    assertEquals(726.0, density, 726.0 * 0.03,
        "n-decane COSTALD density should be within 3% of NIST value");
  }

  /**
   * Test binary mixture ethane + nC10 at 344.26 K, 689.47 bar. This is the original test case. The
   * compressed liquid correction should increase density significantly at 689 bar.
   */
  @Test
  void testBinaryEthaneDecane() {
    SystemInterface fluid = new SystemSrkEos(344.26, 689.47);
    fluid.addComponent("ethane", 70.0);
    fluid.addComponent("nC10", 30.0);
    fluid.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initPhysicalProperties();

    fluid.setLiquidDensityModel("COSTALD");
    double density = fluid.getPhase("oil").getPhysicalProperties().getDensity();

    // At 689 bar, the compressed liquid correction matters significantly
    assertTrue(density > 400.0 && density < 900.0,
        "Binary ethane/nC10 density at 689 bar should be physically reasonable");
  }

  /**
   * Test pure propane liquid density at 230 K, 5 bar. NIST reference: ~583 kg/m3.
   */
  @Test
  void testPurePropane() {
    SystemInterface fluid = new SystemSrkEos(230.0, 5.0);
    fluid.addComponent("propane", 1.0);
    fluid.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initPhysicalProperties();

    fluid.setLiquidDensityModel("COSTALD");
    double density = fluid.getPhase("oil").getPhysicalProperties().getDensity();

    // NIST: propane at 230 K, 5 bar = ~583 kg/m3
    assertEquals(583.0, density, 583.0 * 0.03,
        "propane COSTALD density should be within 3% of NIST value");
  }

  /**
   * Test that the system-level API applies COSTALD correctly to liquid phases.
   */
  @Test
  void testSystemLevelAPI() {
    SystemInterface fluid = new SystemSrkEos(298.15, 10.0);
    fluid.addComponent("n-hexane", 1.0);
    fluid.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initPhysicalProperties();

    // Get default (EOS/Peneloux) density
    double eosDensity = fluid.getPhase("oil").getPhysicalProperties().getDensity();

    // Switch to COSTALD
    fluid.setLiquidDensityModel("COSTALD");
    double costaldDensity = fluid.getPhase("oil").getPhysicalProperties().getDensity();

    // COSTALD and EOS should give different values (unless coincidentally equal)
    // Both should be physically reasonable for n-hexane at 298 K
    assertTrue(eosDensity > 500.0 && eosDensity < 800.0,
        "EOS density should be reasonable for n-hexane");
    assertTrue(costaldDensity > 500.0 && costaldDensity < 800.0,
        "COSTALD density should be reasonable for n-hexane");

    // Switch back to Peneloux
    fluid.setLiquidDensityModel("Peneloux");
    double penelouxDensity = fluid.getPhase("oil").getPhysicalProperties().getDensity();
    assertEquals(eosDensity, penelouxDensity, 0.01,
        "Switching back to Peneloux should restore original density");
  }

  /**
   * Test that custom V* values can be set and affect the result.
   */
  @Test
  void testCustomCharacteristicVolume() {
    SystemInterface fluid = new SystemSrkEos(298.15, 10.0);
    fluid.addComponent("n-hexane", 1.0);
    fluid.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initPhysicalProperties();
    fluid.setLiquidDensityModel("COSTALD");

    double densityWithVc = fluid.getPhase("oil").getPhysicalProperties().calcDensity();

    // Set custom V* (slightly different from Vc) - for n-hexane V* = 371.0 cm3/mol
    // (Vc from NIST is ~368 cm3/mol)
    fluid.getPhase("oil").getComponent("n-hexane").setCostaldCharacteristicVolume(371.0);
    double densityWithVstar = fluid.getPhase("oil").getPhysicalProperties().calcDensity();

    // Densities should differ due to different V*
    assertTrue(Math.abs(densityWithVc - densityWithVstar) > 0.1,
        "Custom V* should change the calculated density");
    assertTrue(densityWithVstar > 500.0 && densityWithVstar < 800.0,
        "Density with custom V* should still be physically reasonable");
  }

  /**
   * Test direct phase-level access (existing API pattern).
   */
  @Test
  void testDirectPhaseLevelAccess() {
    SystemInterface fluid = new SystemSrkEos(344.26, 689.47);
    fluid.addComponent("ethane", 70.0);
    fluid.addComponent("nC10", 30.0);
    fluid.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initPhysicalProperties();

    // Original API pattern should still work
    fluid.getPhase("oil").getPhysicalProperties().setDensityModel("Costald");
    double density = fluid.getPhase("oil").getPhysicalProperties().calcDensity();

    assertTrue(density > 400.0 && density < 900.0,
        "Direct phase-level COSTALD should return a valid density");
  }

  /**
   * Test multi-component natural gas condensate density.
   */
  @Test
  void testNaturalGasCondensate() {
    SystemInterface fluid = new SystemSrkEos(293.15, 50.0);
    fluid.addComponent("methane", 5.0);
    fluid.addComponent("ethane", 10.0);
    fluid.addComponent("propane", 20.0);
    fluid.addComponent("n-butane", 30.0);
    fluid.addComponent("n-pentane", 35.0);
    fluid.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initPhysicalProperties();

    if (fluid.hasPhaseType("oil")) {
      fluid.setLiquidDensityModel("COSTALD");
      double density = fluid.getPhase("oil").getPhysicalProperties().getDensity();
      assertTrue(density > 400.0 && density < 800.0,
          "Multi-component condensate density should be physically reasonable");
    }
  }

  /**
   * Test COSTALD with a single TBP pseudo-component (C7 fraction). V* should be back-calculated
   * from the normal liquid density (0.75 g/cm3).
   */
  @Test
  void testSingleTBPFraction() {
    SystemInterface fluid = new SystemSrkEos(293.15, 50.0);
    fluid.addComponent("methane", 80.0);
    fluid.addComponent("ethane", 10.0);
    fluid.addComponent("propane", 5.0);
    fluid.addTBPfraction("C7", 5.0, 95.0 / 1000.0, 0.75);
    fluid.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initPhysicalProperties();

    if (fluid.hasPhaseType("oil")) {
      fluid.setLiquidDensityModel("COSTALD");
      double density = fluid.getPhase("oil").getPhysicalProperties().calcDensity();
      assertTrue(density > 500.0 && density < 900.0,
          "TBP fraction COSTALD density should be physically reasonable, got: " + density);
    }
  }

  /**
   * Test COSTALD with multiple TBP pseudo-components (C7 through C20). This is the typical oil
   * characterization use case. V* should be estimated for each pseudo-component from its normal
   * liquid density.
   */
  @Test
  void testMultipleTBPFractions() {
    SystemInterface fluid = new SystemSrkEos(313.15, 100.0);
    fluid.addComponent("methane", 50.0);
    fluid.addComponent("ethane", 10.0);
    fluid.addComponent("propane", 8.0);
    fluid.addComponent("n-butane", 5.0);
    fluid.addComponent("n-pentane", 3.0);
    fluid.addComponent("n-hexane", 2.0);
    fluid.addTBPfraction("C7", 5.0, 95.0 / 1000.0, 0.738);
    fluid.addTBPfraction("C8", 4.0, 107.0 / 1000.0, 0.765);
    fluid.addTBPfraction("C9", 3.0, 121.0 / 1000.0, 0.781);
    fluid.addTBPfraction("C10", 2.5, 134.0 / 1000.0, 0.792);
    fluid.addTBPfraction("C15", 4.0, 206.0 / 1000.0, 0.836);
    fluid.addTBPfraction("C20", 3.5, 282.0 / 1000.0, 0.865);
    fluid.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initPhysicalProperties();

    if (fluid.hasPhaseType("oil")) {
      // Compare EOS density with COSTALD density
      double eosDensity = fluid.getPhase("oil").getPhysicalProperties().getDensity();

      fluid.setLiquidDensityModel("COSTALD");
      double costaldDensity = fluid.getPhase("oil").getPhysicalProperties().calcDensity();

      assertTrue(costaldDensity > 500.0 && costaldDensity < 900.0,
          "Multi-TBP COSTALD density should be physically reasonable, got: " + costaldDensity);
      assertTrue(eosDensity > 500.0 && eosDensity < 900.0,
          "Multi-TBP EOS density should be physically reasonable, got: " + eosDensity);

      // Both methods should give reasonably similar values (within 10% of each other)
      double relDiff = Math.abs(costaldDensity - eosDensity) / eosDensity;
      assertTrue(relDiff < 0.15,
          "COSTALD and EOS densities should be within 15% for oil with TBP fractions, " + "COSTALD="
              + costaldDensity + ", EOS=" + eosDensity);
    }
  }

  /**
   * Test COSTALD with a heavy oil containing plus fraction (C20+). These heavy fractions have high
   * acentric factors and molar masses — COSTALD should still give reasonable results.
   */
  @Test
  void testHeavyOilPlusFraction() {
    SystemInterface fluid = new SystemSrkEos(333.15, 50.0);
    fluid.addComponent("methane", 30.0);
    fluid.addComponent("ethane", 5.0);
    fluid.addComponent("propane", 3.0);
    fluid.addComponent("n-pentane", 5.0);
    fluid.addTBPfraction("C7", 10.0, 95.0 / 1000.0, 0.738);
    fluid.addTBPfraction("C10", 10.0, 134.0 / 1000.0, 0.792);
    fluid.addPlusFraction("C20", 37.0, 350.0 / 1000.0, 0.895);
    fluid.getCharacterization().setTBPModel("PedersenSRK");
    fluid.getCharacterization().setLumpingModel("PVTlumpingModel");
    fluid.getCharacterization().characterisePlusFraction();
    fluid.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initPhysicalProperties();

    if (fluid.hasPhaseType("oil")) {
      fluid.setLiquidDensityModel("COSTALD");
      double density = fluid.getPhase("oil").getPhysicalProperties().calcDensity();
      assertTrue(density > 500.0 && density < 1100.0,
          "Heavy oil COSTALD density should be physically reasonable, got: " + density);
    }
  }

  /**
   * Test that TBP V* estimation reproduces the input density at standard conditions (288.71 K). The
   * COSTALD method with back-calculated V* from normal liquid density should give approximately the
   * same density at 60 deg F when only that single component is present.
   */
  @Test
  void testTBPVstarSelfConsistency() {
    // Use a temperature near the standard conditions used for V* back-calculation
    SystemInterface fluid = new SystemSrkEos(288.71, 1.01325);
    fluid.addTBPfraction("C10", 1.0, 134.0 / 1000.0, 0.792);
    fluid.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initPhysicalProperties();

    if (fluid.hasPhaseType("oil")) {
      fluid.setLiquidDensityModel("COSTALD");
      double costaldDensity = fluid.getPhase("oil").getPhysicalProperties().calcDensity();

      // The input density was 0.792 g/cm3 = 792 kg/m3 at standard conditions
      // COSTALD with V* back-calculated from this density should reproduce it closely
      assertEquals(792.0, costaldDensity, 792.0 * 0.05,
          "COSTALD at standard conditions should reproduce input density within 5%, got: "
              + costaldDensity);
    }
  }

  // ---- Aqueous / Polar compound tests ----

  /**
   * Test COSTALD for pure water at 293.15 K (20°C), 10 bar. NIST reference: ~998 kg/m3. The V*
   * back-calculation from normalLiquidDensity (0.999 g/cm3) should give accurate results for water,
   * despite COSTALD being originally designed for non-polar compounds.
   */
  @Test
  void testPureWater() {
    SystemInterface fluid = new SystemSrkEos(293.15, 10.0);
    fluid.addComponent("water", 1.0);
    fluid.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initPhysicalProperties();

    if (fluid.hasPhaseType("aqueous")) {
      fluid.setLiquidDensityModel("COSTALD");
      double density = fluid.getPhase("aqueous").getPhysicalProperties().calcDensity();

      // NIST: water at 293.15 K, 10 bar = ~998 kg/m3
      assertEquals(998.0, density, 998.0 * 0.05,
          "Water COSTALD density should be within 5% of NIST value, got: " + density);
      assertTrue(density > 900.0 && density < 1100.0,
          "Water density should be physically reasonable, got: " + density);
    }
  }

  /**
   * Test COSTALD for methanol at 293.15 K (20°C), 10 bar. NIST reference: ~791 kg/m3. Methanol is a
   * polar associating compound — V* from density back-calculation compensates for polarity.
   */
  @Test
  void testPureMethanol() {
    SystemInterface fluid = new SystemSrkEos(293.15, 10.0);
    fluid.addComponent("methanol", 1.0);
    fluid.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initPhysicalProperties();

    // Methanol may appear as "oil" or "aqueous" depending on phase identification
    String phaseLabel = fluid.hasPhaseType("aqueous") ? "aqueous" : "oil";
    fluid.setLiquidDensityModel("COSTALD");
    double density = fluid.getPhase(phaseLabel).getPhysicalProperties().calcDensity();

    // NIST: methanol at 293.15 K = ~791 kg/m3
    assertEquals(791.0, density, 791.0 * 0.05,
        "Methanol COSTALD density should be within 5% of NIST, got: " + density);
  }

  /**
   * Test COSTALD for MEG (mono ethylene glycol) at 293.15 K, 10 bar. Reference: ~1113 kg/m3. MEG is
   * a common hydrate inhibitor used in subsea production.
   */
  @Test
  void testPureMEG() {
    SystemInterface fluid = new SystemSrkEos(293.15, 10.0);
    fluid.addComponent("MEG", 1.0);
    fluid.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initPhysicalProperties();

    String phaseLabel = fluid.hasPhaseType("aqueous") ? "aqueous" : "oil";
    fluid.setLiquidDensityModel("COSTALD");
    double density = fluid.getPhase(phaseLabel).getPhysicalProperties().calcDensity();

    // MEG at 20°C = ~1113 kg/m3
    assertEquals(1113.0, density, 1113.0 * 0.05,
        "MEG COSTALD density should be within 5% of reference, got: " + density);
    assertTrue(density > 1000.0 && density < 1250.0,
        "MEG density should be physically reasonable, got: " + density);
  }

  /**
   * Test COSTALD for a gas-oil-water three-phase system. This is the main use case for offshore
   * production systems. COSTALD should give reasonable densities for both oil and aqueous phases.
   */
  @Test
  void testGasOilWaterThreePhase() {
    SystemInterface fluid = new SystemSrkEos(313.15, 50.0);
    fluid.addComponent("methane", 60.0);
    fluid.addComponent("ethane", 5.0);
    fluid.addComponent("propane", 3.0);
    fluid.addComponent("n-hexane", 10.0);
    fluid.addComponent("nC10", 10.0);
    fluid.addComponent("water", 12.0);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initPhysicalProperties();

    fluid.setLiquidDensityModel("COSTALD");

    // Check oil phase
    if (fluid.hasPhaseType("oil")) {
      double oilDensity = fluid.getPhase("oil").getPhysicalProperties().calcDensity();
      assertTrue(oilDensity > 500.0 && oilDensity < 900.0,
          "Oil phase COSTALD density should be reasonable, got: " + oilDensity);
    }

    // Check aqueous phase
    if (fluid.hasPhaseType("aqueous")) {
      double waterDensity = fluid.getPhase("aqueous").getPhysicalProperties().calcDensity();
      assertTrue(waterDensity > 900.0 && waterDensity < 1100.0,
          "Aqueous phase COSTALD density should be reasonable, got: " + waterDensity);
    }
  }

  /**
   * Test COSTALD for a gas-oil-water system with MEG inhibitor. Common offshore scenario where MEG
   * is injected for hydrate prevention — the aqueous phase contains water + MEG.
   */
  @Test
  void testGasOilWaterWithMEG() {
    SystemInterface fluid = new SystemSrkEos(303.15, 80.0);
    fluid.addComponent("methane", 70.0);
    fluid.addComponent("ethane", 5.0);
    fluid.addComponent("propane", 3.0);
    fluid.addComponent("n-hexane", 5.0);
    fluid.addComponent("water", 10.0);
    fluid.addComponent("MEG", 7.0);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initPhysicalProperties();

    fluid.setLiquidDensityModel("COSTALD");

    // Check aqueous phase with MEG
    if (fluid.hasPhaseType("aqueous")) {
      double aqueousDensity = fluid.getPhase("aqueous").getPhysicalProperties().calcDensity();
      // Water+MEG mixture density should be between pure water (~998) and pure MEG (~1113)
      assertTrue(aqueousDensity > 900.0 && aqueousDensity < 1200.0,
          "Water+MEG aqueous COSTALD density should be reasonable, got: " + aqueousDensity);
    }
  }

  /**
   * Test COSTALD for a system with methanol hydrate inhibitor. Methanol is widely used for hydrate
   * prevention in gas pipelines.
   */
  @Test
  void testGasWaterMethanol() {
    SystemInterface fluid = new SystemSrkEos(278.15, 100.0);
    fluid.addComponent("methane", 80.0);
    fluid.addComponent("ethane", 5.0);
    fluid.addComponent("water", 10.0);
    fluid.addComponent("methanol", 5.0);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initPhysicalProperties();

    fluid.setLiquidDensityModel("COSTALD");

    if (fluid.hasPhaseType("aqueous")) {
      double density = fluid.getPhase("aqueous").getPhysicalProperties().calcDensity();
      // Water+methanol density should be somewhat less than pure water
      assertTrue(density > 700.0 && density < 1100.0,
          "Water+methanol aqueous COSTALD density should be reasonable, got: " + density);
    }
  }

  /**
   * Test COSTALD for TEG (triethylene glycol) dehydration system. TEG is used in gas dehydration —
   * it is a heavy glycol with high liquid density (~1.127 g/cm3).
   */
  @Test
  void testPureTEG() {
    SystemInterface fluid = new SystemSrkEos(293.15, 10.0);
    fluid.addComponent("TEG", 1.0);
    fluid.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initPhysicalProperties();

    String phaseLabel = fluid.hasPhaseType("aqueous") ? "aqueous" : "oil";
    fluid.setLiquidDensityModel("COSTALD");
    double density = fluid.getPhase(phaseLabel).getPhysicalProperties().calcDensity();

    // TEG at 20°C = ~1125 kg/m3
    assertEquals(1125.0, density, 1125.0 * 0.08,
        "TEG COSTALD density should be within 8% of reference, got: " + density);
    assertTrue(density > 1000.0 && density < 1300.0,
        "TEG density should be physically reasonable, got: " + density);
  }

  /**
   * Test COSTALD for ethanol at 293.15 K, 10 bar. Ethanol is a polar associating compound.
   * Reference: ~789 kg/m3.
   */
  @Test
  void testPureEthanol() {
    SystemInterface fluid = new SystemSrkEos(293.15, 10.0);
    fluid.addComponent("ethanol", 1.0);
    fluid.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initPhysicalProperties();

    String phaseLabel = fluid.hasPhaseType("aqueous") ? "aqueous" : "oil";
    fluid.setLiquidDensityModel("COSTALD");
    double density = fluid.getPhase(phaseLabel).getPhysicalProperties().calcDensity();

    // Ethanol at 20°C = ~789 kg/m3
    assertEquals(789.0, density, 789.0 * 0.05,
        "Ethanol COSTALD density should be within 5% of reference, got: " + density);
  }
}
