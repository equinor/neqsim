package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RefineryHydrotreatingSulfurNitrogenThroughputBalanceTest {
  @Test
  void qualifiesPublicBigHillHourlyRates() {
    RefineryHydrotreatingSulfurNitrogenBalance material = publicBigHillBalance();

    RefineryHydrotreatingSulfurNitrogenThroughputBalance rates = RefineryHydrotreatingSulfurNitrogenThroughputBalance
        .calculate(material, 1000.0);

    assertSame(material, rates.getMaterialBalance());
    assertEquals(1.0, rates.getBasisScalePerHour(), 0.0);
    assertEquals(995.4894479786512, rates.getProductMassFlowKgPerHour(), 1.0e-12);
    assertEquals(4.07181945828032, rates.getSulfurRemovedMassFlowKgPerHour(), 1.0e-12);
    assertEquals(1.0851741055202135, rates.getNitrogenRemovedMassFlowKgPerHour(), 1.0e-12);
    assertEquals(0.5119768402275201, rates.getSulfurHydrogenConsumedMassFlowKgPerHour(), 1.0e-12);
    assertEquals(0.6247249957409144, rates.getNitrogenHydrogenConsumedMassFlowKgPerHour(), 1.0e-12);
    assertEquals(1.1367018359684344, rates.getTotalHydrogenConsumedMassFlowKgPerHour(), 1.0e-12);
    assertEquals(4.327807878394079, rates.getHydrogenSulfideProducedMassFlowKgPerHour(), 1.0e-12);
    assertEquals(1.3194459789230566, rates.getAmmoniaProducedMassFlowKgPerHour(), 1.0e-12);
    assertEquals(0.6464415424518316, rates.getHydrogenRetainedInLiquidMassFlowKgPerHour(), 1.0e-12);
    assertEquals(0.0, rates.getTotalMassBalanceResidualKgPerHour(), 1.0e-12);
    assertEquals(0.0, rates.getSulfurBalanceResidualKgPerHour(), 1.0e-12);
    assertEquals(0.0, rates.getNitrogenBalanceResidualKgPerHour(), 1.0e-12);
  }

  @Test
  void scalesEveryRateLinearly() {
    RefineryHydrotreatingSulfurNitrogenBalance material = publicBigHillBalance();
    RefineryHydrotreatingSulfurNitrogenThroughputBalance base = RefineryHydrotreatingSulfurNitrogenThroughputBalance
        .calculate(material, 1000.0);
    RefineryHydrotreatingSulfurNitrogenThroughputBalance doubled = RefineryHydrotreatingSulfurNitrogenThroughputBalance
        .calculate(material, 2000.0);

    assertEquals(2.0, doubled.getBasisScalePerHour(), 0.0);
    assertEquals(2.0 * base.getProductMassFlowKgPerHour(), doubled.getProductMassFlowKgPerHour(), 1.0e-12);
    assertEquals(2.0 * base.getSulfurRemovedMassFlowKgPerHour(), doubled.getSulfurRemovedMassFlowKgPerHour(), 1.0e-12);
    assertEquals(2.0 * base.getNitrogenRemovedMassFlowKgPerHour(), doubled.getNitrogenRemovedMassFlowKgPerHour(),
        1.0e-12);
    assertEquals(2.0 * base.getTotalHydrogenConsumedMassFlowKgPerHour(),
        doubled.getTotalHydrogenConsumedMassFlowKgPerHour(), 1.0e-12);
    assertEquals(2.0 * base.getHydrogenSulfideProducedMassFlowKgPerHour(),
        doubled.getHydrogenSulfideProducedMassFlowKgPerHour(), 1.0e-12);
    assertEquals(2.0 * base.getAmmoniaProducedMassFlowKgPerHour(), doubled.getAmmoniaProducedMassFlowKgPerHour(),
        1.0e-12);
  }

  @Test
  void preservesNoRemovalCase() {
    RefineryHydrotreatingSulfurNitrogenBalance material = RefineryHydrotreatingSulfurNitrogenBalance.calculate(1000.0,
        0.004, 0.001, 0.004, 0.001, 1.0, 1.5);

    RefineryHydrotreatingSulfurNitrogenThroughputBalance rates = RefineryHydrotreatingSulfurNitrogenThroughputBalance
        .calculate(material, 725.0);

    assertEquals(725.0, rates.getProductMassFlowKgPerHour(), 1.0e-12);
    assertEquals(0.0, rates.getSulfurRemovedMassFlowKgPerHour(), 0.0);
    assertEquals(0.0, rates.getNitrogenRemovedMassFlowKgPerHour(), 0.0);
    assertEquals(0.0, rates.getTotalHydrogenConsumedMassFlowKgPerHour(), 0.0);
    assertEquals(0.0, rates.getHydrogenSulfideProducedMassFlowKgPerHour(), 0.0);
    assertEquals(0.0, rates.getAmmoniaProducedMassFlowKgPerHour(), 0.0);
  }

  @Test
  void rejectsInvalidInputs() {
    RefineryHydrotreatingSulfurNitrogenBalance material = publicBigHillBalance();

    assertThrows(NullPointerException.class,
        () -> RefineryHydrotreatingSulfurNitrogenThroughputBalance.calculate(null, 1000.0));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingSulfurNitrogenThroughputBalance.calculate(material, 0.0));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryHydrotreatingSulfurNitrogenThroughputBalance.calculate(material, Double.NaN));
  }

  private static RefineryHydrotreatingSulfurNitrogenBalance publicBigHillBalance() {
    return RefineryHydrotreatingSulfurNitrogenBalance.calculate(1000.0, 0.0040867518, 0.001095129, 15.0e-6, 10.0e-6,
        2.0, 4.0);
  }
}
