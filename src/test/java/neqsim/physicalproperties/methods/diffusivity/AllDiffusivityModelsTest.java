package neqsim.physicalproperties.methods.diffusivity;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Comprehensive tests for all gas and liquid diffusivity models. Tests cover the new
 * Fuller-Schettler-Giddings (gas), Wilke-Chang (liquid), and Tyn-Calus (liquid) models, as well as
 * the existing Chapman-Enskog, Wilke-Lee, and Siddiqi-Lucas models after bug fixes.
 *
 * @author Even Solbraa
 */
public class AllDiffusivityModelsTest {

  private static SystemInterface gasSystem;
  private static SystemInterface liquidSystem;
  private static SystemInterface aqueousSystem;

  @BeforeAll
  static void setUp() {
    // Gas system for gas-phase diffusion tests
    gasSystem = new SystemSrkEos(298.15, 1.01325);
    gasSystem.addComponent("methane", 0.80);
    gasSystem.addComponent("ethane", 0.10);
    gasSystem.addComponent("CO2", 0.05);
    gasSystem.addComponent("nitrogen", 0.05);
    gasSystem.createDatabase(true);
    gasSystem.setMixingRule(2);
    ThermodynamicOperations opsGas = new ThermodynamicOperations(gasSystem);
    opsGas.TPflash();
    gasSystem.initPhysicalProperties();

    // Liquid hydrocarbon system
    liquidSystem = new SystemSrkEos(300.0, 10.0);
    liquidSystem.addComponent("methane", 0.1);
    liquidSystem.addComponent("n-hexane", 0.6);
    liquidSystem.addComponent("n-heptane", 0.3);
    liquidSystem.createDatabase(true);
    liquidSystem.setMixingRule(2);
    ThermodynamicOperations opsLiq = new ThermodynamicOperations(liquidSystem);
    opsLiq.TPflash();
    liquidSystem.initPhysicalProperties();

    // Aqueous system
    aqueousSystem = new SystemSrkEos(298.15, 1.01325);
    aqueousSystem.addComponent("CO2", 0.02);
    aqueousSystem.addComponent("methane", 0.03);
    aqueousSystem.addComponent("water", 0.95);
    aqueousSystem.createDatabase(true);
    aqueousSystem.setMixingRule(2);
    ThermodynamicOperations opsAq = new ThermodynamicOperations(aqueousSystem);
    opsAq.TPflash();
    aqueousSystem.initPhysicalProperties();
  }

  // ---------- Gas-phase diffusion model tests ----------

  @Test
  void testChapmanEnskogGasDiffusivity() {
    assertTrue(gasSystem.hasPhaseType("gas"), "Gas phase should exist");
    // Default gas diffusion model is Chapman-Enskog
    double D = gasSystem.getPhase("gas").getPhysicalProperties().diffusivityCalc
        .calcBinaryDiffusionCoefficient(0, 1, 0);

    // Gas diffusivities at 1 atm, 25C: typically 1e-6 to 1e-4 m2/s
    assertTrue(D > 1e-7, "Gas D should be > 1e-7 m2/s, got: " + D);
    assertTrue(D < 1e-3, "Gas D should be < 1e-3 m2/s, got: " + D);
  }

  @Test
  void testFullerSchettlerGiddingsModel() {
    assertTrue(gasSystem.hasPhaseType("gas"), "Gas phase should exist");

    // Switch to Fuller model
    gasSystem.getPhase("gas").getPhysicalProperties()
        .setDiffusionCoefficientModel("Fuller-Schettler-Giddings");

    int nComps = gasSystem.getPhase("gas").getNumberOfComponents();
    for (int i = 0; i < nComps; i++) {
      for (int j = 0; j < nComps; j++) {
        if (i != j) {
          double D = gasSystem.getPhase("gas").getPhysicalProperties().diffusivityCalc
              .calcBinaryDiffusionCoefficient(i, j, 0);
          assertTrue(D > 1e-7, "Fuller D[" + i + "][" + j + "] should be > 1e-7 m2/s, got: " + D);
          assertTrue(D < 1e-3, "Fuller D[" + i + "][" + j + "] should be < 1e-3 m2/s, got: " + D);
        }
      }
    }

    // Restore default
    gasSystem.getPhase("gas").getPhysicalProperties().setDiffusionCoefficientModel("CSP");
  }

  @Test
  void testWilkeLeeGasDiffusivity() {
    assertTrue(gasSystem.hasPhaseType("gas"), "Gas phase should exist");

    gasSystem.getPhase("gas").getPhysicalProperties().setDiffusionCoefficientModel("Wilke Lee");

    double D = gasSystem.getPhase("gas").getPhysicalProperties().diffusivityCalc
        .calcBinaryDiffusionCoefficient(0, 1, 0);

    assertTrue(D > 1e-7, "Wilke-Lee D should be > 1e-7 m2/s, got: " + D);
    assertTrue(D < 1e-3, "Wilke-Lee D should be < 1e-3 m2/s, got: " + D);

    // Restore default
    gasSystem.getPhase("gas").getPhysicalProperties().setDiffusionCoefficientModel("CSP");
  }

  @Test
  void testGasModelsAgreement() {
    assertTrue(gasSystem.hasPhaseType("gas"), "Gas phase should exist");

    // Chapman-Enskog (default)
    double dCE = gasSystem.getPhase("gas").getPhysicalProperties().diffusivityCalc
        .calcBinaryDiffusionCoefficient(0, 1, 0);

    // Fuller-Schettler-Giddings
    gasSystem.getPhase("gas").getPhysicalProperties()
        .setDiffusionCoefficientModel("Fuller-Schettler-Giddings");
    double dFSG = gasSystem.getPhase("gas").getPhysicalProperties().diffusivityCalc
        .calcBinaryDiffusionCoefficient(0, 1, 0);

    // Wilke-Lee
    gasSystem.getPhase("gas").getPhysicalProperties().setDiffusionCoefficientModel("Wilke Lee");
    double dWL = gasSystem.getPhase("gas").getPhysicalProperties().diffusivityCalc
        .calcBinaryDiffusionCoefficient(0, 1, 0);

    // Models should agree within an order of magnitude for methane-ethane
    // Fuller and Chapman-Enskog use different molecular parameters so larger differences expected
    double ratio1 = dFSG / dCE;
    double ratio2 = dWL / dCE;
    assertTrue(ratio1 > 0.1 && ratio1 < 10.0, "Fuller/CE ratio should be 0.1-10.0, got: " + ratio1);
    assertTrue(ratio2 > 0.1 && ratio2 < 10.0,
        "WilkeLee/CE ratio should be 0.1-10.0, got: " + ratio2);

    // Restore default
    gasSystem.getPhase("gas").getPhysicalProperties().setDiffusionCoefficientModel("CSP");
  }

  // ---------- Liquid-phase diffusion model tests ----------

  @Test
  void testSiddiqiLucasLiquidDiffusivity() {
    assertTrue(liquidSystem.hasPhaseType("oil"), "Oil phase should exist");

    double D = liquidSystem.getPhase("oil").getPhysicalProperties().diffusivityCalc
        .calcBinaryDiffusionCoefficient(0, 1, 0);

    // Liquid diffusivities: typically 1e-10 to 1e-8 m2/s
    assertTrue(D > 1e-12, "Liquid D should be > 1e-12 m2/s, got: " + D);
    assertTrue(D < 1e-6, "Liquid D should be < 1e-6 m2/s, got: " + D);
  }

  @Test
  void testSiddiqiLucasAqueousDetection() {
    assertTrue(aqueousSystem.hasPhaseType("aqueous"), "Aqueous phase should exist");

    double D = aqueousSystem.getPhase("aqueous").getPhysicalProperties().diffusivityCalc
        .calcBinaryDiffusionCoefficient(0, 1, 0);

    assertTrue(D > 1e-11, "Aqueous D should be > 1e-11 m2/s, got: " + D);
    assertTrue(D < 1e-7, "Aqueous D should be < 1e-7 m2/s, got: " + D);
  }

  @Test
  void testWilkeChangLiquidDiffusivity() {
    assertTrue(liquidSystem.hasPhaseType("oil"), "Oil phase should exist");

    liquidSystem.getPhase("oil").getPhysicalProperties()
        .setDiffusionCoefficientModel("Wilke-Chang");

    int nComps = liquidSystem.getPhase("oil").getNumberOfComponents();
    for (int i = 0; i < nComps; i++) {
      for (int j = 0; j < nComps; j++) {
        if (i != j) {
          double D = liquidSystem.getPhase("oil").getPhysicalProperties().diffusivityCalc
              .calcBinaryDiffusionCoefficient(i, j, 0);
          assertTrue(D > 1e-12,
              "Wilke-Chang D[" + i + "][" + j + "] should be > 1e-12 m2/s, got: " + D);
          assertTrue(D < 1e-6,
              "Wilke-Chang D[" + i + "][" + j + "] should be < 1e-6 m2/s, got: " + D);
        }
      }
    }

    // Restore default
    liquidSystem.getPhase("oil").getPhysicalProperties()
        .setDiffusionCoefficientModel("Siddiqi Lucas");
  }

  @Test
  void testTynCalusLiquidDiffusivity() {
    assertTrue(liquidSystem.hasPhaseType("oil"), "Oil phase should exist");

    liquidSystem.getPhase("oil").getPhysicalProperties().setDiffusionCoefficientModel("Tyn-Calus");

    int nComps = liquidSystem.getPhase("oil").getNumberOfComponents();
    for (int i = 0; i < nComps; i++) {
      for (int j = 0; j < nComps; j++) {
        if (i != j) {
          double D = liquidSystem.getPhase("oil").getPhysicalProperties().diffusivityCalc
              .calcBinaryDiffusionCoefficient(i, j, 0);
          assertTrue(D > 1e-12,
              "Tyn-Calus D[" + i + "][" + j + "] should be > 1e-12 m2/s, got: " + D);
          assertTrue(D < 1e-6,
              "Tyn-Calus D[" + i + "][" + j + "] should be < 1e-6 m2/s, got: " + D);
        }
      }
    }

    // Restore default
    liquidSystem.getPhase("oil").getPhysicalProperties()
        .setDiffusionCoefficientModel("Siddiqi Lucas");
  }

  @Test
  void testLiquidModelsAgreement() {
    assertTrue(liquidSystem.hasPhaseType("oil"), "Oil phase should exist");

    // Siddiqi-Lucas (default)
    double dSL = liquidSystem.getPhase("oil").getPhysicalProperties().diffusivityCalc
        .calcBinaryDiffusionCoefficient(0, 1, 0);

    // Wilke-Chang
    liquidSystem.getPhase("oil").getPhysicalProperties()
        .setDiffusionCoefficientModel("Wilke-Chang");
    double dWC = liquidSystem.getPhase("oil").getPhysicalProperties().diffusivityCalc
        .calcBinaryDiffusionCoefficient(0, 1, 0);

    // Tyn-Calus
    liquidSystem.getPhase("oil").getPhysicalProperties().setDiffusionCoefficientModel("Tyn-Calus");
    double dTC = liquidSystem.getPhase("oil").getPhysicalProperties().diffusivityCalc
        .calcBinaryDiffusionCoefficient(0, 1, 0);

    // Models should agree within a factor of 5
    if (dSL > 0 && dWC > 0) {
      double ratio1 = dWC / dSL;
      assertTrue(ratio1 > 0.2 && ratio1 < 5.0,
          "WilkeChang/SiddiqiLucas ratio should be 0.2-5.0, got: " + ratio1);
    }
    if (dSL > 0 && dTC > 0) {
      double ratio2 = dTC / dSL;
      assertTrue(ratio2 > 0.2 && ratio2 < 5.0,
          "TynCalus/SiddiqiLucas ratio should be 0.2-5.0, got: " + ratio2);
    }

    // Restore default
    liquidSystem.getPhase("oil").getPhysicalProperties()
        .setDiffusionCoefficientModel("Siddiqi Lucas");
  }

  @Test
  void testWilkeChangAqueousAssociation() {
    assertTrue(aqueousSystem.hasPhaseType("aqueous"), "Aqueous phase should exist");

    aqueousSystem.getPhase("aqueous").getPhysicalProperties()
        .setDiffusionCoefficientModel("Wilke-Chang");

    // CO2 (solute=0) in water (solvent=2)
    double D = aqueousSystem.getPhase("aqueous").getPhysicalProperties().diffusivityCalc
        .calcBinaryDiffusionCoefficient(0, 2, 0);

    assertTrue(D > 1e-11, "CO2/water D should be > 1e-11 m2/s, got: " + D);
    assertTrue(D < 1e-7, "CO2/water D should be < 1e-7 m2/s, got: " + D);

    // Restore default
    aqueousSystem.getPhase("aqueous").getPhysicalProperties()
        .setDiffusionCoefficientModel("Siddiqi Lucas");
  }

  // ---------- Temperature dependence tests ----------

  @Test
  void testGasDiffusivityIncreaseWithTemperature() {
    double[] temps = {273.15, 323.15, 373.15, 473.15};
    double[] dValues = new double[temps.length];

    for (int t = 0; t < temps.length; t++) {
      SystemInterface sys = new SystemSrkEos(temps[t], 1.01325);
      sys.addComponent("methane", 0.5);
      sys.addComponent("ethane", 0.5);
      sys.createDatabase(true);
      sys.setMixingRule(2);
      ThermodynamicOperations ops = new ThermodynamicOperations(sys);
      ops.TPflash();
      sys.initPhysicalProperties();

      if (sys.hasPhaseType("gas")) {
        sys.getPhase("gas").getPhysicalProperties()
            .setDiffusionCoefficientModel("Fuller-Schettler-Giddings");
        dValues[t] = sys.getPhase("gas").getPhysicalProperties().diffusivityCalc
            .calcBinaryDiffusionCoefficient(0, 1, 0);
      }
    }

    // Gas diffusivity should increase with temperature (D ~ T^1.75)
    for (int i = 1; i < dValues.length; i++) {
      if (dValues[i] > 0 && dValues[i - 1] > 0) {
        assertTrue(dValues[i] > dValues[i - 1], "Gas D should increase with T: D[" + temps[i] + "]="
            + dValues[i] + " <= D[" + temps[i - 1] + "]=" + dValues[i - 1]);
      }
    }
  }

  @Test
  void testGasDiffusivityDecreaseWithPressure() {
    double[] pressures = {1.0, 5.0, 10.0, 50.0};
    double[] dValues = new double[pressures.length];

    for (int p = 0; p < pressures.length; p++) {
      SystemInterface sys = new SystemSrkEos(298.15, pressures[p]);
      sys.addComponent("methane", 0.5);
      sys.addComponent("ethane", 0.5);
      sys.createDatabase(true);
      sys.setMixingRule(2);
      ThermodynamicOperations ops = new ThermodynamicOperations(sys);
      ops.TPflash();
      sys.initPhysicalProperties();

      if (sys.hasPhaseType("gas")) {
        sys.getPhase("gas").getPhysicalProperties()
            .setDiffusionCoefficientModel("Fuller-Schettler-Giddings");
        dValues[p] = sys.getPhase("gas").getPhysicalProperties().diffusivityCalc
            .calcBinaryDiffusionCoefficient(0, 1, 0);
      }
    }

    // Gas diffusivity should decrease with pressure (D ~ 1/P)
    for (int i = 1; i < dValues.length; i++) {
      if (dValues[i] > 0 && dValues[i - 1] > 0) {
        assertTrue(dValues[i] < dValues[i - 1], "Gas D should decrease with P: D[" + pressures[i]
            + "bar]=" + dValues[i] + " >= D[" + pressures[i - 1] + "bar]=" + dValues[i - 1]);
      }
    }
  }

  // ---------- Model selection via setDiffusionCoefficientModel ----------

  @Test
  void testSetDiffusionCoefficientModelFuller() {
    assertTrue(gasSystem.hasPhaseType("gas"), "Gas phase should exist");
    gasSystem.getPhase("gas").getPhysicalProperties()
        .setDiffusionCoefficientModel("Fuller-Schettler-Giddings");

    assertNotNull(gasSystem.getPhase("gas").getPhysicalProperties().diffusivityCalc);

    // Restore
    gasSystem.getPhase("gas").getPhysicalProperties().setDiffusionCoefficientModel("CSP");
  }

  @Test
  void testSetDiffusionCoefficientModelWilkeChang() {
    assertTrue(liquidSystem.hasPhaseType("oil"), "Oil phase should exist");
    liquidSystem.getPhase("oil").getPhysicalProperties()
        .setDiffusionCoefficientModel("Wilke-Chang");

    assertNotNull(liquidSystem.getPhase("oil").getPhysicalProperties().diffusivityCalc);

    // Restore
    liquidSystem.getPhase("oil").getPhysicalProperties()
        .setDiffusionCoefficientModel("Siddiqi Lucas");
  }

  @Test
  void testSetDiffusionCoefficientModelTynCalus() {
    assertTrue(liquidSystem.hasPhaseType("oil"), "Oil phase should exist");
    liquidSystem.getPhase("oil").getPhysicalProperties().setDiffusionCoefficientModel("Tyn-Calus");

    assertNotNull(liquidSystem.getPhase("oil").getPhysicalProperties().diffusivityCalc);

    // Restore
    liquidSystem.getPhase("oil").getPhysicalProperties()
        .setDiffusionCoefficientModel("Siddiqi Lucas");
  }

  @Test
  void testSetDiffusionCoefficientModelHaydukMinhas() {
    assertTrue(liquidSystem.hasPhaseType("oil"), "Oil phase should exist");
    liquidSystem.getPhase("oil").getPhysicalProperties()
        .setDiffusionCoefficientModel("Hayduk-Minhas");

    assertNotNull(liquidSystem.getPhase("oil").getPhysicalProperties().diffusivityCalc);

    // Restore
    liquidSystem.getPhase("oil").getPhysicalProperties()
        .setDiffusionCoefficientModel("Siddiqi Lucas");
  }

  // ---------- Effective diffusion coefficient tests ----------

  @Test
  void testEffectiveDiffusionCoefficientsGas() {
    assertTrue(gasSystem.hasPhaseType("gas"), "Gas phase should exist");

    gasSystem.getPhase("gas").getPhysicalProperties()
        .setDiffusionCoefficientModel("Fuller-Schettler-Giddings");

    // First compute binary diffusion coefficients then effective ones
    gasSystem.getPhase("gas").getPhysicalProperties().diffusivityCalc.calcDiffusionCoefficients(0,
        0);
    gasSystem.getPhase("gas").getPhysicalProperties().diffusivityCalc
        .calcEffectiveDiffusionCoefficients();

    int nComps = gasSystem.getPhase("gas").getNumberOfComponents();
    for (int i = 0; i < nComps; i++) {
      double Deff = gasSystem.getPhase("gas").getPhysicalProperties().diffusivityCalc
          .getEffectiveDiffusionCoefficient(i);
      assertTrue(Deff > 0, "Effective D[" + i + "] should be > 0, got: " + Deff);
      assertTrue(Deff < 1e-3, "Effective D[" + i + "] should be < 1e-3, got: " + Deff);
    }

    // Restore
    gasSystem.getPhase("gas").getPhysicalProperties().setDiffusionCoefficientModel("CSP");
  }
}
