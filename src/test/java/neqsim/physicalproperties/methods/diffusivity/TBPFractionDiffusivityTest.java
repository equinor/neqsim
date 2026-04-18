package neqsim.physicalproperties.methods.diffusivity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests that all diffusivity models produce valid (non-NaN, non-zero, physically reasonable)
 * results for systems containing TBP fractions and pseudo components.
 *
 * <p>
 * TBP fractions get hardcoded water Lennard-Jones parameters (sigma=1.8 A, eps/k=809.1 K) from the
 * temp database insert, which historically produced incorrect gas-phase diffusion coefficients. The
 * Diffusivity base class now auto-detects TBP fractions and estimates LJ parameters from critical
 * properties using the Tee-Gotoh-Stewart correlation.
 * </p>
 *
 * @author Even Solbraa
 */
public class TBPFractionDiffusivityTest {

  private static SystemInterface oilSystem;

  @BeforeAll
  static void setUp() {
    // Create a system with light components + TBP fractions (typical oil characterization)
    oilSystem = new SystemSrkEos(273.15 + 80.0, 50.0);
    oilSystem.addComponent("methane", 0.40);
    oilSystem.addComponent("ethane", 0.10);
    oilSystem.addComponent("propane", 0.05);
    oilSystem.addComponent("n-butane", 0.03);
    oilSystem.addComponent("n-pentane", 0.02);
    // Add TBP fractions (pseudo components)
    oilSystem.addTBPfraction("C7", 0.10, 0.0913, 0.746);
    oilSystem.addTBPfraction("C8", 0.08, 0.1070, 0.770);
    oilSystem.addTBPfraction("C9", 0.07, 0.1212, 0.790);
    oilSystem.addTBPfraction("C10", 0.06, 0.134, 0.810);
    oilSystem.addTBPfraction("C15", 0.05, 0.206, 0.860);
    oilSystem.addTBPfraction("C20", 0.04, 0.282, 0.895);
    oilSystem.setMixingRule("classic");
    oilSystem.setMultiPhaseCheck(true);

    ThermodynamicOperations ops = new ThermodynamicOperations(oilSystem);
    ops.TPflash();
    oilSystem.initProperties();
  }

  @Test
  void testDefaultGasDiffusivityWithTBPFractions() {
    assertTrue(oilSystem.hasPhaseType("gas"), "Gas phase should exist");

    // Default Chapman-Enskog model should produce valid results for TBP fractions
    int nComps = oilSystem.getPhase("gas").getNumberOfComponents();
    for (int i = 0; i < nComps; i++) {
      for (int j = 0; j < nComps; j++) {
        if (i != j) {
          double D = oilSystem.getPhase("gas").getPhysicalProperties().diffusivityCalc
              .calcBinaryDiffusionCoefficient(i, j, 0);
          assertFalse(Double.isNaN(D),
              "Gas D[" + i + "][" + j + "] should not be NaN (components: "
                  + oilSystem.getPhase("gas").getComponent(i).getComponentName() + " / "
                  + oilSystem.getPhase("gas").getComponent(j).getComponentName() + ")");
          assertFalse(Double.isInfinite(D), "Gas D[" + i + "][" + j + "] should not be Infinite");
          assertTrue(D > 0, "Gas D[" + i + "][" + j + "] should be positive, got: " + D);
          // Gas diffusivities at 50 bar, 80C: typically 1e-7 to 1e-4 m2/s
          assertTrue(D > 1e-8, "Gas D[" + i + "][" + j + "] should be > 1e-8 m2/s, got: " + D);
          assertTrue(D < 1e-3, "Gas D[" + i + "][" + j + "] should be < 1e-3 m2/s, got: " + D);
        }
      }
    }
  }

  @Test
  void testChapmanEnskogWithLJOverrideAndTBPFractions() {
    assertTrue(oilSystem.hasPhaseType("gas"), "Gas phase should exist");

    oilSystem.getPhase("gas").getPhysicalProperties()
        .setDiffusionCoefficientModel("Chapman-Enskog");

    int nComps = oilSystem.getPhase("gas").getNumberOfComponents();
    for (int i = 0; i < nComps; i++) {
      for (int j = 0; j < nComps; j++) {
        if (i != j) {
          double D = oilSystem.getPhase("gas").getPhysicalProperties().diffusivityCalc
              .calcBinaryDiffusionCoefficient(i, j, 0);
          assertFalse(Double.isNaN(D), "CE(override) D[" + i + "][" + j + "] should not be NaN");
          assertTrue(D > 1e-8, "CE(override) D[" + i + "][" + j + "] > 1e-8, got: " + D);
          assertTrue(D < 1e-3, "CE(override) D[" + i + "][" + j + "] < 1e-3, got: " + D);
        }
      }
    }

    // Restore default
    oilSystem.getPhase("gas").getPhysicalProperties().setDiffusionCoefficientModel("CSP");
  }

  @Test
  void testFullerSchettlerGiddingsWithTBPFractions() {
    assertTrue(oilSystem.hasPhaseType("gas"), "Gas phase should exist");

    oilSystem.getPhase("gas").getPhysicalProperties()
        .setDiffusionCoefficientModel("Fuller-Schettler-Giddings");

    int nComps = oilSystem.getPhase("gas").getNumberOfComponents();
    for (int i = 0; i < nComps; i++) {
      for (int j = 0; j < nComps; j++) {
        if (i != j) {
          double D = oilSystem.getPhase("gas").getPhysicalProperties().diffusivityCalc
              .calcBinaryDiffusionCoefficient(i, j, 0);
          assertFalse(Double.isNaN(D), "FSG D[" + i + "][" + j + "] should not be NaN");
          assertTrue(D > 1e-8, "FSG D[" + i + "][" + j + "] > 1e-8, got: " + D);
          assertTrue(D < 1e-3, "FSG D[" + i + "][" + j + "] < 1e-3, got: " + D);
        }
      }
    }

    // Restore default
    oilSystem.getPhase("gas").getPhysicalProperties().setDiffusionCoefficientModel("CSP");
  }

  @Test
  void testWilkeLeeWithTBPFractions() {
    assertTrue(oilSystem.hasPhaseType("gas"), "Gas phase should exist");

    oilSystem.getPhase("gas").getPhysicalProperties().setDiffusionCoefficientModel("Wilke Lee");

    int nComps = oilSystem.getPhase("gas").getNumberOfComponents();
    for (int i = 0; i < nComps; i++) {
      for (int j = 0; j < nComps; j++) {
        if (i != j) {
          double D = oilSystem.getPhase("gas").getPhysicalProperties().diffusivityCalc
              .calcBinaryDiffusionCoefficient(i, j, 0);
          assertFalse(Double.isNaN(D), "WL D[" + i + "][" + j + "] should not be NaN");
          assertTrue(D > 1e-8, "WL D[" + i + "][" + j + "] > 1e-8, got: " + D);
          assertTrue(D < 1e-3, "WL D[" + i + "][" + j + "] < 1e-3, got: " + D);
        }
      }
    }

    // Restore default
    oilSystem.getPhase("gas").getPhysicalProperties().setDiffusionCoefficientModel("CSP");
  }

  @Test
  void testLiquidDiffusivityWithTBPFractions() {
    assertTrue(oilSystem.hasPhaseType("oil"), "Oil phase should exist");

    // Default liquid model (Siddiqi-Lucas) with TBP fractions
    int nComps = oilSystem.getPhase("oil").getNumberOfComponents();
    for (int i = 0; i < nComps; i++) {
      for (int j = 0; j < nComps; j++) {
        if (i != j) {
          double D = oilSystem.getPhase("oil").getPhysicalProperties().diffusivityCalc
              .calcBinaryDiffusionCoefficient(i, j, 0);
          assertFalse(Double.isNaN(D),
              "Liq D[" + i + "][" + j + "] should not be NaN (components: "
                  + oilSystem.getPhase("oil").getComponent(i).getComponentName() + " / "
                  + oilSystem.getPhase("oil").getComponent(j).getComponentName() + ")");
          assertFalse(Double.isInfinite(D), "Liq D[" + i + "][" + j + "] should not be Infinite");
          assertTrue(D > 0, "Liq D[" + i + "][" + j + "] should be positive, got: " + D);
          // Liquid diffusivities: typically 1e-11 to 1e-7 m2/s
          assertTrue(D > 1e-13, "Liq D[" + i + "][" + j + "] > 1e-13, got: " + D);
          assertTrue(D < 1e-5, "Liq D[" + i + "][" + j + "] < 1e-5, got: " + D);
        }
      }
    }
  }

  @Test
  void testWilkeChangLiquidWithTBPFractions() {
    assertTrue(oilSystem.hasPhaseType("oil"), "Oil phase should exist");

    oilSystem.getPhase("oil").getPhysicalProperties().setDiffusionCoefficientModel("Wilke-Chang");

    int nComps = oilSystem.getPhase("oil").getNumberOfComponents();
    for (int i = 0; i < nComps; i++) {
      for (int j = 0; j < nComps; j++) {
        if (i != j) {
          double D = oilSystem.getPhase("oil").getPhysicalProperties().diffusivityCalc
              .calcBinaryDiffusionCoefficient(i, j, 0);
          assertFalse(Double.isNaN(D), "WC D[" + i + "][" + j + "] should not be NaN");
          assertTrue(D > 1e-13, "WC D[" + i + "][" + j + "] > 1e-13, got: " + D);
          assertTrue(D < 1e-5, "WC D[" + i + "][" + j + "] < 1e-5, got: " + D);
        }
      }
    }

    // Restore default
    oilSystem.getPhase("oil").getPhysicalProperties().setDiffusionCoefficientModel("Siddiqi Lucas");
  }

  @Test
  void testTynCalusLiquidWithTBPFractions() {
    assertTrue(oilSystem.hasPhaseType("oil"), "Oil phase should exist");

    oilSystem.getPhase("oil").getPhysicalProperties().setDiffusionCoefficientModel("Tyn-Calus");

    int nComps = oilSystem.getPhase("oil").getNumberOfComponents();
    for (int i = 0; i < nComps; i++) {
      for (int j = 0; j < nComps; j++) {
        if (i != j) {
          double D = oilSystem.getPhase("oil").getPhysicalProperties().diffusivityCalc
              .calcBinaryDiffusionCoefficient(i, j, 0);
          assertFalse(Double.isNaN(D), "TC D[" + i + "][" + j + "] should not be NaN");
          assertTrue(D > 1e-13, "TC D[" + i + "][" + j + "] > 1e-13, got: " + D);
          assertTrue(D < 1e-5, "TC D[" + i + "][" + j + "] < 1e-5, got: " + D);
        }
      }
    }

    // Restore default
    oilSystem.getPhase("oil").getPhysicalProperties().setDiffusionCoefficientModel("Siddiqi Lucas");
  }

  @Test
  void testHaydukMinhasLiquidWithTBPFractions() {
    assertTrue(oilSystem.hasPhaseType("oil"), "Oil phase should exist");

    oilSystem.getPhase("oil").getPhysicalProperties().setDiffusionCoefficientModel("Hayduk-Minhas");

    int nComps = oilSystem.getPhase("oil").getNumberOfComponents();
    for (int i = 0; i < nComps; i++) {
      for (int j = 0; j < nComps; j++) {
        if (i != j) {
          double D = oilSystem.getPhase("oil").getPhysicalProperties().diffusivityCalc
              .calcBinaryDiffusionCoefficient(i, j, 0);
          assertFalse(Double.isNaN(D), "HM D[" + i + "][" + j + "] should not be NaN");
          assertTrue(D > 1e-13, "HM D[" + i + "][" + j + "] > 1e-13, got: " + D);
          assertTrue(D < 1e-5, "HM D[" + i + "][" + j + "] < 1e-5, got: " + D);
        }
      }
    }

    // Restore default
    oilSystem.getPhase("oil").getPhysicalProperties().setDiffusionCoefficientModel("Siddiqi Lucas");
  }

  @Test
  void testEffectiveDiffusionWithTBPFractions() {
    assertTrue(oilSystem.hasPhaseType("gas"), "Gas phase should exist");

    // Test effective (multicomponent) diffusion coefficients
    oilSystem.getPhase("gas").getPhysicalProperties()
        .setDiffusionCoefficientModel("Fuller-Schettler-Giddings");

    oilSystem.getPhase("gas").getPhysicalProperties().diffusivityCalc.calcDiffusionCoefficients(0,
        1);

    int nComps = oilSystem.getPhase("gas").getNumberOfComponents();
    for (int i = 0; i < nComps; i++) {
      double Deff = oilSystem.getPhase("gas").getPhysicalProperties().diffusivityCalc
          .getEffectiveDiffusionCoefficient(i);
      assertFalse(Double.isNaN(Deff), "Effective D[" + i + "] should not be NaN for: "
          + oilSystem.getPhase("gas").getComponent(i).getComponentName());
      assertFalse(Double.isInfinite(Deff), "Effective D[" + i + "] should not be Infinite");
      assertTrue(Deff > 0, "Effective D[" + i + "] should be positive, got: " + Deff);
    }

    // Restore default
    oilSystem.getPhase("gas").getPhysicalProperties().setDiffusionCoefficientModel("CSP");
  }

  @Test
  void testGasModelConsistencyForTBPFractions() {
    assertTrue(oilSystem.hasPhaseType("gas"), "Gas phase should exist");

    // Get methane-C10 binary diffusion from different models
    // Find C10 index
    int methaneIdx = -1;
    int c10Idx = -1;
    for (int i = 0; i < oilSystem.getPhase("gas").getNumberOfComponents(); i++) {
      String name = oilSystem.getPhase("gas").getComponent(i).getComponentName();
      if (name.equals("methane")) {
        methaneIdx = i;
      }
      if (name.contains("C10")) {
        c10Idx = i;
      }
    }
    assertTrue(methaneIdx >= 0, "Methane should be found");
    assertTrue(c10Idx >= 0, "C10 TBP fraction should be found");

    // Default Chapman-Enskog (with auto-fixed LJ params)
    double dCE = oilSystem.getPhase("gas").getPhysicalProperties().diffusivityCalc
        .calcBinaryDiffusionCoefficient(methaneIdx, c10Idx, 0);

    // Fuller-Schettler-Giddings
    oilSystem.getPhase("gas").getPhysicalProperties()
        .setDiffusionCoefficientModel("Fuller-Schettler-Giddings");
    double dFSG = oilSystem.getPhase("gas").getPhysicalProperties().diffusivityCalc
        .calcBinaryDiffusionCoefficient(methaneIdx, c10Idx, 0);

    // Wilke-Lee
    oilSystem.getPhase("gas").getPhysicalProperties().setDiffusionCoefficientModel("Wilke Lee");
    double dWL = oilSystem.getPhase("gas").getPhysicalProperties().diffusivityCalc
        .calcBinaryDiffusionCoefficient(methaneIdx, c10Idx, 0);

    // All models should give reasonable results (within 5x of each other)
    double ratio1 = dFSG / dCE;
    double ratio2 = dWL / dCE;
    assertTrue(ratio1 > 0.2 && ratio1 < 5.0,
        "Fuller/CE ratio for methane-C10 TBP should be 0.2-5.0, got: " + ratio1);
    assertTrue(ratio2 > 0.2 && ratio2 < 5.0,
        "WilkeLee/CE ratio for methane-C10 TBP should be 0.2-5.0, got: " + ratio2);

    // Restore default
    oilSystem.getPhase("gas").getPhysicalProperties().setDiffusionCoefficientModel("CSP");
  }

  @Test
  void testHighPressureLiquidDiffusivityWithTBPFractions() {
    assertTrue(oilSystem.hasPhaseType("oil"), "Oil phase should exist");

    oilSystem.getPhase("oil").getPhysicalProperties().setDiffusionCoefficientModel("High Pressure");

    int nComps = oilSystem.getPhase("oil").getNumberOfComponents();
    for (int i = 0; i < nComps; i++) {
      for (int j = 0; j < nComps; j++) {
        if (i != j) {
          double D = oilSystem.getPhase("oil").getPhysicalProperties().diffusivityCalc
              .calcBinaryDiffusionCoefficient(i, j, 0);
          assertFalse(Double.isNaN(D), "HP D[" + i + "][" + j + "] should not be NaN");
          assertTrue(D > 1e-14, "HP D[" + i + "][" + j + "] > 1e-14, got: " + D);
          assertTrue(D < 1e-5, "HP D[" + i + "][" + j + "] < 1e-5, got: " + D);
        }
      }
    }

    // Restore default
    oilSystem.getPhase("oil").getPhysicalProperties().setDiffusionCoefficientModel("Siddiqi Lucas");
  }
}
