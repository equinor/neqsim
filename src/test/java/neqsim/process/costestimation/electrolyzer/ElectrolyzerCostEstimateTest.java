package neqsim.process.costestimation.electrolyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.electrolyzer.Electrolyzer;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.mechanicaldesign.electrolyzer.ElectrolyzerMechanicalDesign;
import neqsim.thermo.Fluid;
import neqsim.thermo.system.SystemInterface;

/**
 * Validation tests for {@link ElectrolyzerCostEstimate}. Cross-checks the technology-dependent
 * specific CAPEX and scaling behaviour against IRENA 2022 and IEA 2023 benchmark ranges.
 */
class ElectrolyzerCostEstimateTest extends neqsim.NeqSimTest {

  private static ElectrolyzerMechanicalDesign buildMechanicalDesign(double h2KgPerHour) {
    SystemInterface water = new Fluid().create("water");
    Stream inlet = new Stream("water", water);
    inlet.setPressure(1.0, "bara");
    inlet.setTemperature(298.15, "K");
    // mol H2O -> mol H2, MW_H2 = 2.016e-3 kg/mol
    double molH2PerSec = (h2KgPerHour / 3600.0) / 2.016e-3;
    inlet.setFlowRate(molH2PerSec, "mole/sec");
    inlet.run();
    Electrolyzer el = new Electrolyzer("el", inlet);
    el.run();
    ElectrolyzerMechanicalDesign mech = new ElectrolyzerMechanicalDesign(el);
    mech.calcDesign();
    return mech;
  }

  @Test
  void testPemCostInExpectedRange() {
    // 100 kg/hr H2 at ~52 kWh/kg → ~5.2 MW stack.
    ElectrolyzerMechanicalDesign mech = buildMechanicalDesign(100.0);
    double powerKW = mech.getTotalPowerKW();
    assertTrue(powerKW > 1000.0, "Expected MW-scale stack, got " + powerKW + " kW");

    ElectrolyzerCostEstimate cost = new ElectrolyzerCostEstimate(mech);
    cost.setTechnology("PEM");
    double usd = cost.getPurchasedEquipmentCost();
    assertTrue(usd > 0.0, "PEM cost should be positive, got " + usd);
    // Specific cost = total / powerKW: should land within ±50% of 1250 USD/kW after scale & CEPCI.
    double usdPerKw = usd / powerKW;
    assertTrue(usdPerKw > 500.0 && usdPerKw < 3000.0, "PEM USD/kW out of band, got " + usdPerKw);
  }

  @Test
  void testTechnologyOrderingOnCost() {
    ElectrolyzerMechanicalDesign mech = buildMechanicalDesign(100.0);
    ElectrolyzerCostEstimate pem = new ElectrolyzerCostEstimate(mech);
    pem.setTechnology("PEM");
    ElectrolyzerCostEstimate alk = new ElectrolyzerCostEstimate(mech);
    alk.setTechnology("ALKALINE");
    ElectrolyzerCostEstimate soec = new ElectrolyzerCostEstimate(mech);
    soec.setTechnology("SOEC");

    double cPem = pem.getPurchasedEquipmentCost();
    double cAlk = alk.getPurchasedEquipmentCost();
    double cSoec = soec.getPurchasedEquipmentCost();

    // SOEC > PEM > Alkaline on USD/kW basis.
    assertTrue(cAlk < cPem, "ALKALINE should cost less than PEM");
    assertTrue(cSoec > cPem, "SOEC should cost more than PEM");
  }

  @Test
  void testScaleEconomies() {
    ElectrolyzerMechanicalDesign small = buildMechanicalDesign(50.0);
    ElectrolyzerMechanicalDesign big = buildMechanicalDesign(1000.0);

    ElectrolyzerCostEstimate cSmall = new ElectrolyzerCostEstimate(small);
    ElectrolyzerCostEstimate cBig = new ElectrolyzerCostEstimate(big);

    double usdPerKwSmall = cSmall.getPurchasedEquipmentCost() / small.getTotalPowerKW();
    double usdPerKwBig = cBig.getPurchasedEquipmentCost() / big.getTotalPowerKW();

    assertTrue(usdPerKwBig < usdPerKwSmall,
        "Larger plant should have lower USD/kW (scale economies), got " + usdPerKwBig + " vs "
            + usdPerKwSmall);
  }

  @Test
  void testBalanceOfPlantToggle() {
    ElectrolyzerMechanicalDesign mech = buildMechanicalDesign(100.0);
    ElectrolyzerCostEstimate withBop = new ElectrolyzerCostEstimate(mech);
    ElectrolyzerCostEstimate noBop = new ElectrolyzerCostEstimate(mech);
    noBop.setIncludeBalanceOfPlant(false);
    assertTrue(noBop.getPurchasedEquipmentCost() < withBop.getPurchasedEquipmentCost(),
        "Cost without BOP should be lower than with BOP");
    assertEquals(0.65, noBop.getPurchasedEquipmentCost() / withBop.getPurchasedEquipmentCost(),
        1e-6);
  }

  @Test
  void testTechnologyValidation() {
    ElectrolyzerMechanicalDesign mech = buildMechanicalDesign(100.0);
    ElectrolyzerCostEstimate cost = new ElectrolyzerCostEstimate(mech);
    assertThrows(IllegalArgumentException.class, () -> cost.setTechnology(null));
  }

  @Test
  void testSpecificCapexLookup() {
    ElectrolyzerMechanicalDesign mech = buildMechanicalDesign(100.0);
    ElectrolyzerCostEstimate cost = new ElectrolyzerCostEstimate(mech);
    cost.setTechnology("PEM");
    assertEquals(1250.0, cost.getSpecificCapexUsdPerKw(), 1e-9);
    cost.setTechnology("ALKALINE");
    assertEquals(800.0, cost.getSpecificCapexUsdPerKw(), 1e-9);
    cost.setTechnology("SOEC");
    assertEquals(2500.0, cost.getSpecificCapexUsdPerKw(), 1e-9);
    cost.setTechnology("AEM");
    assertEquals(1500.0, cost.getSpecificCapexUsdPerKw(), 1e-9);
  }
}
