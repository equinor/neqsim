package neqsim.physicalproperties.interfaceproperties.solidadsorption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Integration tests for adsorption and capillary condensation models.
 *
 * <p>
 * Tests realistic scenarios combining adsorption isotherms with capillary condensation for
 * mesoporous materials.
 * </p>
 *
 * @author ESOL
 */
public class AdsorptionIntegrationTest {

  /**
   * Test CO2 capture on activated carbon scenario.
   */
  @Test
  public void testCO2CaptureOnActivatedCarbon() {
    // Flue gas-like conditions: 50°C, 1.5 bar, 15% CO2, balance N2
    SystemInterface flueGas = new SystemSrkEos(323.15, 1.5);
    flueGas.addComponent("CO2", 0.15);
    flueGas.addComponent("nitrogen", 0.85);
    flueGas.setMixingRule("classic");
    flueGas.init(0);

    ThermodynamicOperations ops = new ThermodynamicOperations(flueGas);
    try {
      ops.TPflash();
    } catch (Exception e) {
      // Handle exception
    }

    // Test with Langmuir model
    LangmuirAdsorption langmuir = new LangmuirAdsorption(flueGas);
    langmuir.setSolidMaterial("AC");

    // Set typical AC parameters for CO2 and N2
    langmuir.setQmax(0, 8.0); // CO2 - higher capacity
    langmuir.setKLangmuir(0, 2.5);
    langmuir.setQmax(1, 4.0); // N2 - lower capacity
    langmuir.setKLangmuir(1, 0.3);

    langmuir.calcExtendedLangmuir(0);

    double co2Loading = langmuir.getSurfaceExcess("CO2");
    double n2Loading = langmuir.getSurfaceExcess("nitrogen");
    double selectivity = langmuir.getSelectivity(0, 1, 0);

    System.out.println("CO2 Capture on Activated Carbon:");
    System.out.println("  CO2 loading: " + co2Loading + " mol/kg");
    System.out.println("  N2 loading: " + n2Loading + " mol/kg");
    System.out.println("  CO2/N2 selectivity: " + selectivity);

    assertTrue(co2Loading > n2Loading, "CO2 should adsorb more than N2");
    assertTrue(selectivity > 5.0, "CO2/N2 selectivity should be > 5 for AC");
  }

  /**
   * Test natural gas dehydration with silica gel.
   */
  @Test
  public void testNaturalGasDehydration() {
    // Natural gas at 30°C, 50 bar with water vapor
    SystemInterface natGas = new SystemSrkEos(303.15, 50.0);
    natGas.addComponent("methane", 0.95);
    natGas.addComponent("water", 0.0001); // Traces of water
    natGas.setMixingRule("classic");
    natGas.init(0);

    ThermodynamicOperations ops = new ThermodynamicOperations(natGas);
    try {
      ops.TPflash();
    } catch (Exception e) {
      // Handle exception
    }

    // BET model for multilayer water adsorption on silica gel
    BETAdsorption bet = new BETAdsorption(natGas);
    bet.setSolidMaterial("Silica Gel");
    bet.setBetSurfaceArea(650.0);

    // Set BET parameters
    bet.setMonolayerCapacity(0, 0.1); // methane - low adsorption
    bet.setBETConstant(0, 50.0);
    bet.setMonolayerCapacity(1, 8.0); // water - high adsorption
    bet.setBETConstant(1, 200.0);

    bet.calcAdsorption(0);

    double waterLoading = bet.getSurfaceExcess(1);
    double nLayers = bet.getNumberOfLayers(1);

    System.out.println("Natural Gas Dehydration on Silica Gel:");
    System.out.println("  Water loading: " + waterLoading + " mol/kg");
    System.out.println("  Number of water layers: " + nLayers);

    assertTrue(waterLoading > 0, "Water should adsorb on silica gel");
  }

  /**
   * Test nitrogen porosimetry at 77K.
   */
  @Test
  public void testNitrogenPorosimetry() {
    // N2 adsorption at boiling point for pore analysis
    SystemInterface n2System = new SystemSrkEos(77.0, 0.5);
    n2System.addComponent("nitrogen", 1.0);
    n2System.setMixingRule("classic");
    n2System.init(0);

    // BET adsorption component
    BETAdsorption bet = new BETAdsorption(n2System);
    bet.setMonolayerCapacity(0, 10.0);
    bet.setBETConstant(0, 100.0);
    bet.setSaturationPressure(0, 1.0); // N2 saturation at 77K ~ 1 bar
    bet.calcAdsorption(0);

    // Capillary condensation component
    CapillaryCondensationModel capillary = new CapillaryCondensationModel(n2System);
    capillary.setMeanPoreRadius(5.0);
    capillary.setPoreRadiusStdDev(2.0);
    capillary.setMinPoreRadius(1.0);
    capillary.setMaxPoreRadius(25.0);
    capillary.setTotalPoreVolume(0.5);
    capillary.calcCapillaryCondensation(0);

    double betAdsorption = bet.getSurfaceExcess(0);
    double condensate = capillary.getCondensateAmount(0);
    double kelvinRadius = capillary.getKelvinRadius(0);
    double totalUptake = betAdsorption + condensate;

    System.out.println("N2 Porosimetry at 77K (P/P0 ~ 0.5):");
    System.out.println("  BET adsorption: " + betAdsorption + " mol/kg");
    System.out.println("  Capillary condensate: " + condensate + " mol/kg");
    System.out.println("  Total uptake: " + totalUptake + " mol/kg");
    System.out.println("  Kelvin radius: " + kelvinRadius + " nm");

    assertTrue(betAdsorption > 0, "BET adsorption should be positive");
    assertTrue(kelvinRadius > 0, "Kelvin radius should be positive");
  }

  /**
   * Test isotherm type conversion utility.
   */
  @Test
  public void testIsothermTypeConversion() {
    assertEquals(IsothermType.LANGMUIR, IsothermType.fromString("LANGMUIR"));
    assertEquals(IsothermType.BET, IsothermType.fromString("Brunauer-Emmett-Teller"));
    assertEquals(IsothermType.DRA, IsothermType.fromString("invalid")); // Default

    assertTrue(IsothermType.DRA.supportsMultiComponent());
    assertTrue(IsothermType.EXTENDED_LANGMUIR.supportsMultiComponent());
    assertTrue(!IsothermType.LANGMUIR.supportsMultiComponent());
    assertTrue(!IsothermType.BET.supportsMultiComponent());
  }

  /**
   * Test pressure swing adsorption cycle simulation.
   */
  @Test
  public void testPressureSwingCycle() {
    // High pressure adsorption step
    SystemInterface highP = new SystemSrkEos(298.15, 10.0);
    highP.addComponent("CO2", 0.5);
    highP.addComponent("methane", 0.5);
    highP.setMixingRule("classic");
    highP.init(0);

    ThermodynamicOperations ops1 = new ThermodynamicOperations(highP);
    try {
      ops1.TPflash();
    } catch (Exception e) {
      // Handle exception
    }

    LangmuirAdsorption highPAds = new LangmuirAdsorption(highP);
    highPAds.setQmax(0, 8.0);
    highPAds.setKLangmuir(0, 1.0);
    highPAds.setQmax(1, 4.0);
    highPAds.setKLangmuir(1, 0.2);
    highPAds.calcExtendedLangmuir(0);

    double highPCO2 = highPAds.getSurfaceExcess("CO2");
    double highPCH4 = highPAds.getSurfaceExcess("methane");

    // Low pressure desorption step
    SystemInterface lowP = new SystemSrkEos(298.15, 1.0);
    lowP.addComponent("CO2", 0.5);
    lowP.addComponent("methane", 0.5);
    lowP.setMixingRule("classic");
    lowP.init(0);

    ThermodynamicOperations ops2 = new ThermodynamicOperations(lowP);
    try {
      ops2.TPflash();
    } catch (Exception e) {
      // Handle exception
    }

    LangmuirAdsorption lowPAds = new LangmuirAdsorption(lowP);
    lowPAds.setQmax(0, 8.0);
    lowPAds.setKLangmuir(0, 1.0);
    lowPAds.setQmax(1, 4.0);
    lowPAds.setKLangmuir(1, 0.2);
    lowPAds.calcExtendedLangmuir(0);

    double lowPCO2 = lowPAds.getSurfaceExcess("CO2");
    double lowPCH4 = lowPAds.getSurfaceExcess("methane");

    double co2WorkingCapacity = highPCO2 - lowPCO2;
    double ch4WorkingCapacity = highPCH4 - lowPCH4;

    System.out.println("PSA Cycle Simulation:");
    System.out.println("  CO2 at 10 bar: " + highPCO2 + " mol/kg");
    System.out.println("  CO2 at 1 bar: " + lowPCO2 + " mol/kg");
    System.out.println("  CO2 working capacity: " + co2WorkingCapacity + " mol/kg");
    System.out.println("  CH4 working capacity: " + ch4WorkingCapacity + " mol/kg");

    assertTrue(co2WorkingCapacity > 0, "CO2 working capacity should be positive");
    // Both should have positive working capacity demonstrating PSA principle
    assertTrue(ch4WorkingCapacity >= 0, "CH4 working capacity should be non-negative");
  }

  /**
   * Test comparison of different isotherm models.
   */
  @Test
  public void testIsothermModelComparison() {
    SystemInterface gas = new SystemSrkEos(298.15, 5.0);
    gas.addComponent("CO2", 1.0);
    gas.setMixingRule("classic");
    gas.init(0);

    ThermodynamicOperations ops = new ThermodynamicOperations(gas);
    try {
      ops.TPflash();
    } catch (Exception e) {
      // Handle exception
    }

    // Langmuir model
    LangmuirAdsorption langmuir = new LangmuirAdsorption(gas);
    langmuir.setQmax(0, 8.0);
    langmuir.setKLangmuir(0, 0.5);
    langmuir.calcAdsorption(0);
    double langmuirLoading = langmuir.getSurfaceExcess(0);

    // BET model
    BETAdsorption bet = new BETAdsorption(gas);
    bet.setMonolayerCapacity(0, 3.0);
    bet.setBETConstant(0, 50.0);
    bet.setSaturationPressure(0, 64.0); // CO2 saturation pressure at 298K
    bet.calcAdsorption(0);
    double betLoading = bet.getSurfaceExcess(0);

    System.out.println("Isotherm Model Comparison at 5 bar CO2:");
    System.out.println("  Langmuir: " + langmuirLoading + " mol/kg");
    System.out.println("  BET: " + betLoading + " mol/kg");

    assertTrue(langmuirLoading > 0, "Langmuir loading should be positive");
    assertTrue(betLoading > 0, "BET loading should be positive");
  }

  /**
   * Test mesoporous silica (MCM-41) with capillary condensation.
   */
  @Test
  public void testMesoporousSilicaAdsorption() {
    // Toluene vapor at room temperature
    SystemInterface toluene = new SystemSrkEos(298.15, 0.02); // Low pressure
    toluene.addComponent("toluene", 1.0);
    toluene.setMixingRule("classic");
    toluene.init(0);

    ThermodynamicOperations ops = new ThermodynamicOperations(toluene);
    try {
      ops.TPflash();
    } catch (Exception e) {
      // Handle exception
    }

    // BET for surface adsorption
    BETAdsorption bet = new BETAdsorption(toluene);
    bet.setMonolayerCapacity(0, 2.0);
    bet.setBETConstant(0, 100.0);
    bet.setSaturationPressure(0, 0.038); // Toluene Psat at 298K
    bet.calcAdsorption(0);

    // Capillary condensation in MCM-41 mesopores
    CapillaryCondensationModel capillary = new CapillaryCondensationModel(toluene);
    capillary.setMeanPoreRadius(2.5); // MCM-41 typical pore size
    capillary.setPoreRadiusStdDev(0.5);
    capillary.setMinPoreRadius(1.5);
    capillary.setMaxPoreRadius(4.0);
    capillary.setTotalPoreVolume(0.8);
    capillary.setPoreType(CapillaryCondensationModel.PoreType.CYLINDRICAL);
    capillary.calcCapillaryCondensation(0);

    double surfaceAdsorption = bet.getSurfaceExcess(0);
    double condensate = capillary.getCondensateAmount(0);
    double kelvinR = capillary.getKelvinRadius(0);

    System.out.println("Toluene on MCM-41:");
    System.out.println("  Surface adsorption: " + surfaceAdsorption + " mol/kg");
    System.out.println("  Capillary condensate: " + condensate + " mol/kg");
    System.out.println("  Kelvin radius: " + kelvinR + " nm");
    System.out.println("  P/P0: " + (0.02 / 0.038));

    assertTrue(surfaceAdsorption >= 0, "Surface adsorption should be non-negative");
    assertTrue(kelvinR > 0, "Kelvin radius should be positive");
  }
}
