package neqsim.process.equipment.reactor.sulfurrecovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Material-balance and response tests for sulfur-recovery equipment. */
public class SulfurRecoveryEquipmentTest extends neqsim.NeqSimTest {
  /** Feed-forward Claus demand is one oxygen per two H2S for a clean acid gas. */
  @Test
  public void testAirDemandControllerStoichiometry() {
    SystemInterface feed = createAcidGas(10.0);
    AirDemandController controller = new AirDemandController();
    assertEquals(5.0, controller.calculateOxygenDemand(feed), 1.0e-10);
  }

  /** Furnace reduced mechanism preserves sulfur atoms. */
  @Test
  public void testReactionFurnaceSulfurBalance() {
    SystemInterface feed = createAcidGas(10.0);
    feed.addComponent("oxygen", 5.0);
    feed.addComponent("nitrogen", 18.8);
    Stream stream = new Stream("furnace feed", feed);
    stream.run();

    ClausReactionFurnace furnace = new ClausReactionFurnace("furnace", stream);
    furnace.run();

    assertTrue(furnace.getFurnaceTemperature() > feed.getTemperature());
    assertEquals(0.0, furnace.getSulfurBalanceError(), 1.0e-10);
    assertNotNull(furnace.getOutletStream());
  }

  /** A colder sulfur condenser removes at least as much elemental sulfur as a hotter unit. */
  @Test
  public void testSulfurCondenserTemperatureResponse() {
    SystemInterface feed = createAcidGas(1.0);
    feed.addComponent("S8", 1.0);
    feed.setTemperature(650.0);
    Stream stream = new Stream("condenser feed", feed);
    stream.run();

    SulfurCondenser cold = new SulfurCondenser("cold condenser", stream);
    cold.setOutletTemperature(130.0, "C");
    cold.run();
    SulfurCondenser hot = new SulfurCondenser("hot condenser", stream);
    hot.setOutletTemperature(220.0, "C");
    hot.run();

    assertTrue(cold.getCondensedSulfur("kg/hr") >= hot.getCondensedSulfur("kg/hr"));
    assertTrue(cold.getCondensedSulfur("kg/hr") > 0.0);
  }

  /** Tail-gas treatment converts oxidized sulfur and removes the resulting H2S. */
  @Test
  public void testTailGasTreatmentCreatesRecycle() {
    SystemInterface feed = createAcidGas(1.0);
    feed.addComponent("SO2", 1.0);
    feed.addComponent("COS", 0.2);
    feed.addComponent("water", 5.0);
    feed.setTemperature(500.0);
    Stream stream = new Stream("tail gas", feed);
    stream.run();

    TailGasTreatmentUnit unit = new TailGasTreatmentUnit("TGTU", stream);
    unit.run();

    assertTrue(unit.getHydrogenationConversion() > 0.0);
    assertTrue(unit.getAbsorbedH2SMoles() > 0.0);
    assertNotNull(unit.getAcidGasRecycleStream());
  }

  /** Titania must provide at least the COS hydrolysis activity of alumina. */
  @Test
  public void testCatalystHydrolysisTrend() {
    SystemInterface feed = createAcidGas(2.0);
    feed.addComponent("SO2", 1.0);
    feed.addComponent("COS", 0.5);
    feed.addComponent("water", 5.0);
    feed.setTemperature(513.15);
    Stream stream = new Stream("converter feed", feed);
    stream.run();

    ClausCatalyticConverter alumina = new ClausCatalyticConverter("alumina", stream);
    alumina.setCatalystType(ClausCatalyticConverter.CatalystType.ALUMINA);
    alumina.run();
    ClausCatalyticConverter titania = new ClausCatalyticConverter("titania", stream);
    titania.setCatalystType(ClausCatalyticConverter.CatalystType.TITANIA);
    titania.run();

    assertTrue(titania.getCosConversion() >= alumina.getCosConversion());
  }

  /** Adsorption-mode sub-dew-point operation retains sulfur inventory on the bed. */
  @Test
  public void testSubDewPointInventory() {
    SystemInterface feed = createAcidGas(2.0);
    feed.addComponent("SO2", 1.0);
    feed.addComponent("water", 5.0);
    Stream stream = new Stream("sub-dew-point feed", feed);
    stream.run();

    SubDewPointSulfurReactor reactor = new SubDewPointSulfurReactor("sub-dew-point", stream);
    reactor.setSulfurCapacity(100.0, "kg");
    reactor.run();

    assertTrue(reactor.getStoredSulfur("kg") >= 0.0);
    assertNotNull(reactor.getSulfurProductStream());
  }

  /** Create a simple water-bearing acid gas at two bar. */
  private SystemInterface createAcidGas(double h2sMoles) {
    SystemInterface system = new SystemSrkEos(313.15, 2.0);
    system.addComponent("H2S", h2sMoles);
    system.addComponent("CO2", 2.0);
    system.addComponent("water", 1.0);
    system.setMixingRule("classic");
    return system;
  }
}
