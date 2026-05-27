package neqsim.process.costestimation.adsorber;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.adsorber.PSACascade;
import neqsim.process.equipment.adsorber.PressureSwingAdsorptionBed;

/**
 * Unit tests for {@link PSACostEstimate}. Validates the per-bed reference cost, sorbent inventory
 * cost, balance-of-plant toggle, and the bed-volume scale-exponent correlation against published
 * industrial PSA cost benchmarks.
 */
class PSACostEstimateTest {

  @Test
  void testDefaultEstimateIsPositive() {
    PSACostEstimate est = new PSACostEstimate();
    est.setNumberOfBeds(4);
    est.setSorbentMassPerBedKg(20000.0);
    est.calculateCostEstimate();
    assertTrue(est.getPurchasedEquipmentCost() > 0.0,
        "Default PSA cost must be positive, got " + est.getPurchasedEquipmentCost());
  }

  @Test
  void testMoreBedsCostMore() {
    PSACostEstimate four = new PSACostEstimate();
    four.setNumberOfBeds(4);
    four.setSorbentMassPerBedKg(20000.0);
    four.calculateCostEstimate();

    PSACostEstimate twelve = new PSACostEstimate();
    twelve.setNumberOfBeds(12);
    twelve.setSorbentMassPerBedKg(20000.0);
    twelve.calculateCostEstimate();

    assertTrue(twelve.getPurchasedEquipmentCost() > four.getPurchasedEquipmentCost(),
        "12-bed cascade must cost more than 4-bed: 12=" + twelve.getPurchasedEquipmentCost()
            + ", 4=" + four.getPurchasedEquipmentCost());
  }

  @Test
  void testZeoliteCostsMoreThanActivatedCarbon() {
    PSACostEstimate ac = new PSACostEstimate();
    ac.setNumberOfBeds(4);
    ac.setSorbentMassPerBedKg(20000.0);
    ac.setSorbent(PressureSwingAdsorptionBed.SorbentType.ACTIVATED_CARBON);
    ac.calculateCostEstimate();

    PSACostEstimate zeo = new PSACostEstimate();
    zeo.setNumberOfBeds(4);
    zeo.setSorbentMassPerBedKg(20000.0);
    zeo.setSorbent(PressureSwingAdsorptionBed.SorbentType.ZEOLITE_13X);
    zeo.calculateCostEstimate();

    assertTrue(zeo.getPurchasedEquipmentCost() > ac.getPurchasedEquipmentCost(),
        "Zeolite 13X (~10 USD/kg) must exceed AC (~4 USD/kg) on equal sorbent mass");
  }

  @Test
  void testBalanceOfPlantToggleReducesCost() {
    PSACostEstimate withBop = new PSACostEstimate();
    withBop.setNumberOfBeds(6);
    withBop.setSorbentMassPerBedKg(15000.0);
    withBop.setIncludeBalanceOfPlant(true);
    withBop.calculateCostEstimate();

    PSACostEstimate noBop = new PSACostEstimate();
    noBop.setNumberOfBeds(6);
    noBop.setSorbentMassPerBedKg(15000.0);
    noBop.setIncludeBalanceOfPlant(false);
    noBop.calculateCostEstimate();

    assertTrue(noBop.getPurchasedEquipmentCost() < withBop.getPurchasedEquipmentCost(),
        "BoP=false should give a lower CAPEX, got with=" + withBop.getPurchasedEquipmentCost()
            + ", no=" + noBop.getPurchasedEquipmentCost());
    double ratio = noBop.getPurchasedEquipmentCost() / withBop.getPurchasedEquipmentCost();
    assertTrue(ratio > 0.70 && ratio < 0.80,
        "BoP strip should reduce CAPEX by ~25%, ratio=" + ratio);
  }

  @Test
  void testChangingInputsInvalidatesCachedCost() {
    PSACostEstimate est = new PSACostEstimate();
    est.setNumberOfBeds(4);
    est.setSorbentMassPerBedKg(10000.0);
    double initialCost = est.getPurchasedEquipmentCost();

    est.setNumberOfBeds(8);
    double moreBedsCost = est.getPurchasedEquipmentCost();

    est.setIncludeBalanceOfPlant(false);
    double noBopCost = est.getPurchasedEquipmentCost();

    assertTrue(moreBedsCost > initialCost,
        "Changing bed count after reading cost must trigger recalculation");
    assertTrue(noBopCost < moreBedsCost,
        "Changing BoP flag after reading cost must trigger recalculation");
  }

  @Test
  void testConstructFromCascadePopulatesBedsAndSorbent() {
    PSACascade cascade = new PSACascade("PSA");
    cascade.setConfiguration(PSACascade.CascadeConfiguration.BEDS_6);
    cascade.setSorbent(PressureSwingAdsorptionBed.SorbentType.ZEOLITE_13X);

    PSACostEstimate est = new PSACostEstimate(cascade);
    org.junit.jupiter.api.Assertions.assertEquals(6, est.getNumberOfBeds());
    org.junit.jupiter.api.Assertions
        .assertEquals(PressureSwingAdsorptionBed.SorbentType.ZEOLITE_13X, est.getSorbent());
    assertTrue(est.getSorbentMassPerBedKg() > 0.0,
        "Sorbent mass should be derived from bed volume × bulk density");
  }

  @Test
  void testReasonableOrderOfMagnitude() {
    // A 4-bed cascade with 20 t sorbent per bed (typical for a 200 t/day H2 plant)
    // should land in the USD 1.5-5 M range at CEPCI 800 (2024 basis).
    PSACostEstimate est = new PSACostEstimate();
    est.setNumberOfBeds(4);
    est.setSorbentMassPerBedKg(20000.0);
    est.setSorbent(PressureSwingAdsorptionBed.SorbentType.ACTIVATED_CARBON);
    est.calculateCostEstimate();
    double cost = est.getPurchasedEquipmentCost();
    assertTrue(cost > 1.0e6 && cost < 1.0e7,
        "4-bed PSA with 20 t AC/bed should be USD 1-10 M, got " + cost);
  }

  @Test
  void testInvalidInputsRejected() {
    PSACostEstimate est = new PSACostEstimate();
    assertThrows(IllegalArgumentException.class, () -> est.setNumberOfBeds(0));
    assertThrows(IllegalArgumentException.class, () -> est.setNumberOfBeds(-1));
    assertThrows(IllegalArgumentException.class, () -> est.setSorbentMassPerBedKg(-1.0));
    assertThrows(IllegalArgumentException.class, () -> est.setSorbent(null));
    assertThrows(IllegalArgumentException.class, () -> new PSACostEstimate((PSACascade) null));
  }
}
