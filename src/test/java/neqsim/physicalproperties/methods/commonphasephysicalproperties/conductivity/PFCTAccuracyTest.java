package neqsim.physicalproperties.methods.commonphasephysicalproperties.conductivity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Comprehensive accuracy test for PFCT thermal conductivity method. Compares against NIST WebBook
 * reference data (Huber et al. correlations).
 *
 * <p>
 * Expected accuracy for PFCT corresponding states method:
 * </p>
 * <ul>
 * <li>Methane (reference substance): 2-8% for gas, 6-8% for liquid</li>
 * <li>Light HC gases (ethane, propane): 10-17% overprediction (known limitation)</li>
 * <li>Liquid alkanes: 10-12% underprediction (EOS density effects)</li>
 * <li>Non-hydrocarbons (N2, CO2): 4-12%</li>
 * </ul>
 *
 * @author esol
 */
public class PFCTAccuracyTest {

  private double getConductivity(SystemInterface system, int phaseIdx) {
    return system.getPhase(phaseIdx).getPhysicalProperties().getConductivity();
  }

  private int findLiquidPhase(SystemInterface system) {
    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      String phType = system.getPhase(i).getType().toString();
      if (phType.contains("LIQUID") || phType.contains("OIL")) {
        return i;
      }
    }
    return -1;
  }

  /**
   * PFCT methane gas at 300 K, various pressures. Since methane is the reference substance, PFCT
   * should be most accurate here.
   */
  @Test
  void testPFCTMethaneGasPressureSeries() {
    double[][] cases = {{300.0, 1.0, 0.03437}, {300.0, 10.0, 0.03530}, {300.0, 50.0, 0.03823},
        {300.0, 100.0, 0.04436}, {300.0, 200.0, 0.05915}};

    System.out.println("\n=== PFCT Methane Gas at 300 K ===");
    System.out.printf("%-8s %-12s %-12s %-10s%n", "P(bar)", "PFCT", "NIST", "Error%");

    for (double[] c : cases) {
      SystemInterface system = new SystemSrkEos(c[0], c[1]);
      system.addComponent("methane", 1.0);
      system.setMixingRule("classic");
      ThermodynamicOperations ops = new ThermodynamicOperations(system);
      ops.TPflash();
      system.initProperties();

      double lambda = getConductivity(system, 0);
      double nist = c[2];
      double errPct = (lambda - nist) / nist * 100.0;
      System.out.printf("%-8.0f %-12.5f %-12.5f %-10.1f%n", c[1], lambda, nist, errPct);

      assertEquals(nist, lambda, nist * 0.10,
          "CH4 at 300K/" + c[1] + "bar: PFCT=" + lambda + " vs NIST=" + nist);
    }
  }

  /**
   * PFCT methane gas temperature series at 1 bar. Note: error increases above 400 K.
   */
  @Test
  void testPFCTMethaneGasTemperatureSeries() {
    double[][] cases = {{200.0, 1.0, 0.02168}, {250.0, 1.0, 0.02809}, {300.0, 1.0, 0.03437},
        {350.0, 1.0, 0.04042}, {400.0, 1.0, 0.04624}};

    System.out.println("\n=== PFCT Methane Gas Temperature Series at 1 bar ===");
    System.out.printf("%-8s %-12s %-12s %-10s%n", "T(K)", "PFCT", "NIST", "Error%");

    for (double[] c : cases) {
      SystemInterface system = new SystemSrkEos(c[0], c[1]);
      system.addComponent("methane", 1.0);
      system.setMixingRule("classic");
      ThermodynamicOperations ops = new ThermodynamicOperations(system);
      ops.TPflash();
      system.initProperties();

      double lambda = getConductivity(system, 0);
      double nist = c[2];
      double errPct = (lambda - nist) / nist * 100.0;
      System.out.printf("%-8.0f %-12.5f %-12.5f %-10.1f%n", c[0], lambda, nist, errPct);

      assertEquals(nist, lambda, nist * 0.10,
          "CH4 at " + c[0] + "K/1bar: PFCT=" + lambda + " vs NIST=" + nist);
    }
  }

  /**
   * PFCT methane liquid at cryogenic conditions.
   */
  @Test
  void testPFCTMethaneLiquid() {
    System.out.println("\n=== PFCT Methane Liquid ===");
    System.out.printf("%-8s %-8s %-12s %-12s %-10s%n", "T(K)", "P(bar)", "PFCT", "NIST", "Error%");

    double[][] cases = {{120.0, 10.0, 0.1857}, {120.0, 50.0, 0.1890}, {110.0, 10.0, 0.1996}};

    for (double[] c : cases) {
      SystemInterface system = new SystemSrkEos(c[0], c[1]);
      system.addComponent("methane", 1.0);
      system.setMixingRule("classic");
      ThermodynamicOperations ops = new ThermodynamicOperations(system);
      ops.TPflash();
      system.initProperties();

      int liqIdx = findLiquidPhase(system);
      assertTrue(liqIdx >= 0, "Should find liquid phase for CH4 at " + c[0] + "K/" + c[1] + "bar");

      double lambda = getConductivity(system, liqIdx);
      double nist = c[2];
      double errPct = (lambda - nist) / nist * 100.0;
      System.out.printf("%-8.0f %-8.0f %-12.5f %-12.5f %-10.1f%n", c[0], c[1], lambda, nist,
          errPct);

      assertEquals(nist, lambda, nist * 0.15,
          "CH4 liquid at " + c[0] + "K/" + c[1] + "bar: PFCT=" + lambda + " vs NIST=" + nist);
    }
  }

  /**
   * PFCT pure HC gases at 300 K, 1 bar. Known systematic overprediction that increases with
   * molecular weight: ethane ~16%, propane ~22%, n-butane ~30%. This is a fundamental limitation of
   * corresponding states with methane reference for dilute gas conductivity.
   */
  @Test
  void testPFCTPureGases() {
    System.out.println("\n=== PFCT Pure Gases at 300 K, 1 bar ===");
    System.out.printf("%-12s %-12s %-12s %-10s%n", "Component", "PFCT", "NIST", "Error%");

    String[] names = {"ethane", "propane", "n-butane"};
    double[] nistValues = {0.02122, 0.01787, 0.01589};
    // Tolerance increases with MW: ethane 20%, propane 25%, butane 35%
    double[] tolerances = {0.20, 0.25, 0.35};

    for (int k = 0; k < names.length; k++) {
      SystemInterface system = new SystemSrkEos(300.0, 1.0);
      system.addComponent(names[k], 1.0);
      system.setMixingRule("classic");
      ThermodynamicOperations ops = new ThermodynamicOperations(system);
      ops.TPflash();
      system.initProperties();

      double lambda = getConductivity(system, 0);
      double nist = nistValues[k];
      double errPct = (lambda - nist) / nist * 100.0;
      System.out.printf("%-12s %-12.5f %-12.5f %-10.1f%n", names[k], lambda, nist, errPct);

      assertEquals(nist, lambda, nist * tolerances[k],
          names[k] + " at 300K/1bar: PFCT=" + lambda + " vs NIST=" + nist);
    }
  }

  /**
   * PFCT non-hydrocarbon gases at 300 K, 1 bar.
   */
  @Test
  void testPFCTNonHydrocarbonGases() {
    System.out.println("\n=== PFCT Non-hydrocarbon Gases at 300 K, 1 bar ===");
    System.out.printf("%-12s %-12s %-12s %-10s%n", "Component", "PFCT", "NIST", "Error%");

    String[] names = {"nitrogen", "CO2"};
    double[] nistValues = {0.02583, 0.01662};

    for (int k = 0; k < names.length; k++) {
      SystemInterface system = new SystemSrkEos(300.0, 1.0);
      system.addComponent(names[k], 1.0);
      system.setMixingRule("classic");
      ThermodynamicOperations ops = new ThermodynamicOperations(system);
      ops.TPflash();
      system.initProperties();

      double lambda = getConductivity(system, 0);
      double nist = nistValues[k];
      double errPct = (lambda - nist) / nist * 100.0;
      System.out.printf("%-12s %-12.5f %-12.5f %-10.1f%n", names[k], lambda, nist, errPct);

      assertEquals(nist, lambda, nist * 0.15,
          names[k] + " at 300K/1bar: PFCT=" + lambda + " vs NIST=" + nist);
    }
  }

  /**
   * PFCT liquid alkanes at 300 K, 10 bar. Known systematic underprediction (~10-12%).
   */
  @Test
  void testPFCTLiquidAlkanes() {
    System.out.println("\n=== PFCT Liquid Alkanes at 300 K, 10 bar ===");
    System.out.printf("%-12s %-12s %-12s %-10s%n", "Component", "PFCT", "NIST", "Error%");

    String[] names = {"n-pentane", "n-hexane", "n-heptane", "n-octane"};
    double[] nistValues = {0.1127, 0.1191, 0.1232, 0.1274};

    for (int k = 0; k < names.length; k++) {
      SystemInterface system = new SystemSrkEos(300.0, 10.0);
      system.addComponent(names[k], 1.0);
      system.setMixingRule("classic");
      ThermodynamicOperations ops = new ThermodynamicOperations(system);
      ops.TPflash();
      system.initProperties();

      int liqIdx = findLiquidPhase(system);
      assertTrue(liqIdx >= 0, "Should find liquid phase for " + names[k]);

      double lambda = getConductivity(system, liqIdx);
      double nist = nistValues[k];
      double errPct = (lambda - nist) / nist * 100.0;
      System.out.printf("%-12s %-12.5f %-12.5f %-10.1f%n", names[k], lambda, nist, errPct);

      assertEquals(nist, lambda, nist * 0.15,
          names[k] + " liquid at 300K/10bar: PFCT=" + lambda + " vs NIST=" + nist);
    }
  }

  /**
   * PFCT for natural gas mixture. Checks monotonic increase with pressure.
   */
  @Test
  void testPFCTNaturalGasMixturePressureSeries() {
    System.out.println("\n=== PFCT Natural Gas Mixture at 300 K ===");
    System.out.printf("%-8s %-12s%n", "P(bar)", "PFCT");

    double[] pressures = {1.0, 10.0, 50.0, 100.0, 200.0};
    double prevLambda = 0;

    for (double p : pressures) {
      SystemInterface system = new SystemSrkEos(300.0, p);
      system.addComponent("methane", 0.85);
      system.addComponent("ethane", 0.07);
      system.addComponent("propane", 0.03);
      system.addComponent("n-butane", 0.01);
      system.addComponent("nitrogen", 0.03);
      system.addComponent("CO2", 0.01);
      system.setMixingRule("classic");
      ThermodynamicOperations ops = new ThermodynamicOperations(system);
      ops.TPflash();
      system.initProperties();

      double lambda = getConductivity(system, 0);
      System.out.printf("%-8.0f %-12.5f%n", p, lambda);

      assertTrue(lambda > 0.01 && lambda < 0.15,
          "Conductivity out of range at P=" + p + ": " + lambda);

      if (p > 1.0) {
        assertTrue(lambda >= prevLambda * 0.98,
            "Conductivity should not decrease with pressure: P=" + p);
      }
      prevLambda = lambda;
    }
  }

  /**
   * PFCT high-temperature methane - documents known degradation above 400 K.
   */
  @Test
  void testPFCTMethaneHighTemperature() {
    System.out.println("\n=== PFCT Methane High Temperature (known limitation) ===");
    System.out.printf("%-8s %-12s %-12s %-10s%n", "T(K)", "PFCT", "NIST", "Error%");

    double[][] cases = {{500.0, 1.0, 0.05722}, {600.0, 1.0, 0.06810}};

    for (double[] c : cases) {
      SystemInterface system = new SystemSrkEos(c[0], c[1]);
      system.addComponent("methane", 1.0);
      system.setMixingRule("classic");
      ThermodynamicOperations ops = new ThermodynamicOperations(system);
      ops.TPflash();
      system.initProperties();

      double lambda = getConductivity(system, 0);
      double nist = c[2];
      double errPct = (lambda - nist) / nist * 100.0;
      System.out.printf("%-8.0f %-12.5f %-12.5f %-10.1f%n", c[0], lambda, nist, errPct);

      // High-T: larger tolerance - known limitation of methane reference correlation
      assertEquals(nist, lambda, nist * 0.30,
          "CH4 high-T at " + c[0] + "K: PFCT=" + lambda + " vs NIST=" + nist);
    }
  }
}
