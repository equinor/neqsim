package neqsim.process.equipment.reactor;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.EquipmentFactory;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for hydrogen-production reactor models.
 */
public class HydrogenProductionReactorTest extends neqsim.NeqSimTest {

  @Test
  public void testCatalyticTubeReformerProducesSyngasMetrics() {
    Stream feed = createMethaneSteamFeed("SMR feed", 10.0, 3.0, 773.15, 25.0);

    CatalyticTubeReformer reformer = new CatalyticTubeReformer("SMR tubes", feed);
    reformer.setReformingTemperature(850.0, "C");
    reformer.run();

    assertNotNull(reformer.getOutletStream());
    assertTrue(reformer.getSteamToCarbonRatio() > 2.9);
    assertTrue(reformer.getMethaneConversion() >= 0.0);
    assertTrue(reformer.getHeatDuty("kW") >= 0.0);
    assertTrue(reformer.toJson().contains("methaneConversion"));
  }

  @Test
  public void testReformerFurnaceCouplesBurnerAndTubes() {
    Stream feed = createMethaneSteamFeed("SMR feed", 10.0, 3.0, 773.15, 25.0);
    Stream fuel = createMethaneFuel("fuel", 4.0, 298.15, 25.0);
    Stream air = createAir("air", 8.0, 298.15, 25.0);

    ReformerFurnace furnace = new ReformerFurnace("SMR furnace", feed);
    furnace.setFuelInletStream(fuel);
    furnace.setAirInletStream(air);
    furnace.run();

    assertNotNull(furnace.getSyngasOutStream());
    assertNotNull(furnace.getFlueGasOutStream());
    assertTrue(furnace.getAvailableRadiantHeatKW() >= 0.0);
    assertTrue(furnace.getTubeHeatDemandKW() >= 0.0);
    assertTrue(furnace.toJson().contains("heatBalanceRatio"));
  }

  @Test
  public void testAutothermalReformerAppliesRatioControls() {
    Stream feed = createMethaneSteamOxygenFeed("ATR feed", 10.0, 1.0, 0.2, 823.15, 30.0);

    AutothermalReformer atr = new AutothermalReformer("ATR", feed);
    atr.setOxygenToCarbonTarget(0.60);
    atr.setSteamToCarbonTarget(1.5);
    atr.run();

    assertNotNull(atr.getOutletStream());
    assertTrue(atr.getOxygenToCarbonRatio() > 0.55 && atr.getOxygenToCarbonRatio() < 0.65,
        "O2/C=" + atr.getOxygenToCarbonRatio());
    assertTrue(atr.getSteamToCarbonRatio() > 1.45 && atr.getSteamToCarbonRatio() < 1.55);
    assertTrue(atr.getSootRiskIndex() >= 0.0 && atr.getSootRiskIndex() <= 1.0);
    assertTrue(atr.toJson().contains("burnerSafetyWarning"));
  }

  @Test
  public void testPartialOxidationReactorQuenchAndRefractoryMetrics() {
    Stream feed = createMethaneSteamOxygenFeed("POX feed", 10.0, 0.1, 0.5, 573.15, 30.0);

    PartialOxidationReactor pox = new PartialOxidationReactor("POX", feed);
    pox.setOxygenToCarbonTarget(0.55);
    pox.setSteamToCarbonTarget(0.2);
    pox.run();

    assertNotNull(pox.getOutletStream());
    assertNotNull(pox.getQuenchSection());
    assertTrue(pox.getMethaneConversion() >= 0.0);
    assertTrue(pox.getSootRiskIndex() >= 0.0 && pox.getSootRiskIndex() <= 1.0);
    assertTrue(pox.getRefractoryWarning().length() > 0);
  }

  @Test
  public void testEquipmentFactoryCreatesHydrogenReactors() {
    assertTrue(EquipmentFactory.createEquipment("smr",
        "catalytictubereformer") instanceof CatalyticTubeReformer);
    assertTrue(
        EquipmentFactory.createEquipment("furnace", "reformerfurnace") instanceof ReformerFurnace);
    assertTrue(EquipmentFactory.createEquipment("atr", "atr") instanceof AutothermalReformer);
    assertTrue(EquipmentFactory.createEquipment("pox", "pox") instanceof PartialOxidationReactor);
    assertTrue(
        EquipmentFactory.createEquipment("quench", "quenchsection") instanceof QuenchSection);
  }

  /**
   * Creates a methane and steam feed stream.
   *
   * @param name stream name
   * @param methaneMolesPerSec methane flow in mole/sec
   * @param steamToCarbon steam-to-carbon ratio
   * @param temperatureK temperature in Kelvin
   * @param pressureBara pressure in bara
   * @return configured stream
   */
  private Stream createMethaneSteamFeed(String name, double methaneMolesPerSec,
      double steamToCarbon, double temperatureK, double pressureBara) {
    return createMethaneSteamOxygenFeed(name, methaneMolesPerSec, steamToCarbon, 0.0, temperatureK,
        pressureBara);
  }

  /**
   * Creates a methane, steam, and oxygen feed stream.
   *
   * @param name stream name
   * @param methaneMolesPerSec methane flow in mole/sec
   * @param steamToCarbon steam-to-carbon ratio
   * @param oxygenToCarbon oxygen-to-carbon ratio
   * @param temperatureK temperature in Kelvin
   * @param pressureBara pressure in bara
   * @return configured stream
   */
  private Stream createMethaneSteamOxygenFeed(String name, double methaneMolesPerSec,
      double steamToCarbon, double oxygenToCarbon, double temperatureK, double pressureBara) {
    SystemInterface system = createBaseSystem(temperatureK, pressureBara);
    system.addComponent("methane", methaneMolesPerSec, "mole/sec");
    system.addComponent("water", methaneMolesPerSec * steamToCarbon, "mole/sec");
    system.addComponent("oxygen", Math.max(1.0e-20, methaneMolesPerSec * oxygenToCarbon),
        "mole/sec");
    addTraceProducts(system);
    Stream stream = new Stream(name, system);
    stream.run();
    return stream;
  }

  /**
   * Creates a methane fuel stream.
   *
   * @param name stream name
   * @param methaneMolesPerSec methane flow in mole/sec
   * @param temperatureK temperature in Kelvin
   * @param pressureBara pressure in bara
   * @return configured stream
   */
  private Stream createMethaneFuel(String name, double methaneMolesPerSec, double temperatureK,
      double pressureBara) {
    SystemInterface system = createBaseSystem(temperatureK, pressureBara);
    system.addComponent("methane", methaneMolesPerSec, "mole/sec");
    system.addComponent("oxygen", 1.0e-20, "mole/sec");
    system.addComponent("nitrogen", 1.0e-20, "mole/sec");
    addTraceProducts(system);
    Stream stream = new Stream(name, system);
    stream.run();
    return stream;
  }

  /**
   * Creates an air stream.
   *
   * @param name stream name
   * @param oxygenMolesPerSec oxygen flow in mole/sec
   * @param temperatureK temperature in Kelvin
   * @param pressureBara pressure in bara
   * @return configured stream
   */
  private Stream createAir(String name, double oxygenMolesPerSec, double temperatureK,
      double pressureBara) {
    SystemInterface system = createBaseSystem(temperatureK, pressureBara);
    system.addComponent("oxygen", oxygenMolesPerSec, "mole/sec");
    system.addComponent("nitrogen", oxygenMolesPerSec * 3.76, "mole/sec");
    system.addComponent("water", 1.0e-20, "mole/sec");
    system.addComponent("CO2", 1.0e-20, "mole/sec");
    initialize(system);
    Stream stream = new Stream(name, system);
    stream.run();
    return stream;
  }

  /**
   * Creates a base SRK system.
   *
   * @param temperatureK temperature in Kelvin
   * @param pressureBara pressure in bara
   * @return thermodynamic system
   */
  private SystemInterface createBaseSystem(double temperatureK, double pressureBara) {
    return new SystemSrkEos(temperatureK, pressureBara);
  }

  /**
   * Adds trace syngas products and initializes the system.
   *
   * @param system thermodynamic system to update
   */
  private void addTraceProducts(SystemInterface system) {
    system.addComponent("hydrogen", 1.0e-20, "mole/sec");
    system.addComponent("CO", 1.0e-20, "mole/sec");
    system.addComponent("CO2", 1.0e-20, "mole/sec");
    initialize(system);
  }

  /**
   * Initializes a thermodynamic system.
   *
   * @param system thermodynamic system to initialize
   */
  private void initialize(SystemInterface system) {
    system.createDatabase(true);
    system.setMixingRule("classic");
    system.init(0);
    system.init(3);
  }
}
