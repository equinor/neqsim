package neqsim.process.chemistry.hydrate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.process.chemistry.ProductionChemical;
import neqsim.process.chemistry.ProductionChemical.ChemicalType;
import neqsim.process.chemistry.hydrate.AntiAgglomerantQualification.Conditions;
import neqsim.process.chemistry.hydrate.AntiAgglomerantQualification.DoseBasis;
import neqsim.process.chemistry.hydrate.AntiAgglomerantQualification.Outcome;

/** Product balances and synthetic flow-loop evidence for hydrate management. */
class HydrateChemicalManagementTest {
  /** Neat and aqueous products close both the water and active balances. */
  @Test
  void closesComponentBalances() {
    ThermodynamicHydrateInhibitorPerformance screen = new ThermodynamicHydrateInhibitorPerformance();
    screen.setWaterFlowKgPerHour(1000);
    screen.setTargetSubcoolingC(2335.0 / (4.0 * 62.07));
    for (double lean : new double[] {0, 10}) {
      screen.setLeanInhibitorWtPctInWater(lean);
      for (double purity : new double[] {50, 100}) {
        screen.setInhibitorPurityWtPct(purity);
        screen.evaluate();
        double product = screen.getRequiredInjectionKgPerHour();
        double feed = 1000.0 / (1.0 - lean / 100);
        double active = feed - 1000.0 + product * purity / 100;
        double water = 1000.0 + product * (1.0 - purity / 100);
        assertEquals(0.20 * (feed + product), active, 1e-9);
        assertEquals(0.80 * (feed + product), water, 1e-9);
        if (lean == 0) {
          assertEquals(purity == 50 ? 2000.0 / 3 : 250, product, 1e-9);
        }
      }
    }
  }

  /** Already met targets need no makeup even with an unusable product. */
  @Test
  void reportsInfeasibilityAndZeroMakeup() {
    ThermodynamicHydrateInhibitorPerformance screen = new ThermodynamicHydrateInhibitorPerformance();
    screen.setTargetSubcoolingC(2335.0 / (4.0 * 62.07));
    for (double purity : new double[] {20, 10, 0}) {
      screen.setInhibitorPurityWtPct(purity);
      screen.evaluate();
      assertEquals(Double.POSITIVE_INFINITY, screen.getRequiredInjectionKgPerHour());
      assertEquals(Boolean.FALSE, screen.toMap().get("injectionFeasible"));
      assertTrue(screen.toJson().contains("\"requiredInjectionKgPerHour\": null"));
    }
    screen.setLeanInhibitorWtPctInWater(20);
    screen.evaluate();
    assertEquals(0.0, screen.getRequiredInjectionKgPerHour());
    assertEquals(Boolean.TRUE, screen.toMap().get("injectionFeasible"));
  }

  /**
   * @param cut liquid water cut in percent
   * @param hours time at rest in hours
   * @param basis dose numerator and denominator
   * @param subcooling temperature below hydrate equilibrium in K
   * @return synthetic laboratory point
   */
  private Conditions point(double cut, double hours, DoseBasis basis, double subcooling) {
    return new Conditions("synthetic oil/brine A", "loop-A/ramp-1", basis, 5, cut, 60, subcooling, 1.2, hours);
  }

  /** Product identity and the entire tested point bound the restart evidence. */
  @Test
  void retrievesOnlyMeasuredRestartOutcomes() {
    ProductionChemical product = new ProductionChemical("AA-1", ChemicalType.HYDRATE_INHIBITOR_LDHI);
    DoseBasis basis = DoseBasis.PRODUCT_G_PER_KG_WATER;
    Conditions normal = point(25, 4, basis, 8);
    Conditions overnight = point(25, 12, basis, 8);
    AntiAgglomerantQualification pass = new AntiAgglomerantQualification(product, "F1", normal,
        Outcome.TRANSPORTABLE_SLURRY, "synthetic:loop-A/run-1");
    AntiAgglomerantQualification fail = new AntiAgglomerantQualification(product, "F1", overnight,
        Outcome.BLOCKED_RESTART, "synthetic:loop-A/run-2");
    List<AntiAgglomerantQualification> records = Arrays.asList(pass, fail);
    assertEquals(Outcome.TRANSPORTABLE_SLURRY,
        AntiAgglomerantQualification.lookup(records, product, "F1", normal).get(0).getOutcome());
    assertEquals(Outcome.BLOCKED_RESTART,
        AntiAgglomerantQualification.lookup(records, product, "F1", overnight).get(0).getOutcome());
    assertEquals("synthetic:loop-A/run-2", fail.getSource());
    for (Conditions missing : Arrays.asList(point(30, 4, basis, 8), point(25, 24, basis, 8),
        point(25, 4, DoseBasis.ACTIVE_G_PER_KG_WATER, 8), point(25, 4, basis, 10))) {
      assertTrue(AntiAgglomerantQualification.lookup(records, product, "F1", missing).isEmpty());
    }
    assertTrue(AntiAgglomerantQualification.lookup(records, product, "F2", normal).isEmpty());
    product.setActiveWtPct(75.0);
    assertTrue(AntiAgglomerantQualification.lookup(records, product, "F1", normal).isEmpty());
  }
}
