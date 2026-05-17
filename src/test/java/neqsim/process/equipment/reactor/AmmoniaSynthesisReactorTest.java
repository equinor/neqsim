package neqsim.process.equipment.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for AmmoniaSynthesisReactor class.
 */
public class AmmoniaSynthesisReactorTest extends neqsim.NeqSimTest {

  @Test
  public void testBasicConversion() {
    // Create 3:1 H2:N2 synthesis gas
    SystemInterface synthGas = new SystemSrkEos(273.15 + 450.0, 200.0);
    synthGas.addComponent("hydrogen", 0.75);
    synthGas.addComponent("nitrogen", 0.25);
    synthGas.setMixingRule("classic");

    Stream feed = new Stream("Synth Gas", synthGas);
    feed.setFlowRate(100.0, "mole/sec");
    feed.run();

    AmmoniaSynthesisReactor reactor = new AmmoniaSynthesisReactor("HB Reactor", feed);
    reactor.setPerPassConversion(0.15);
    reactor.setIsothermal(true);
    reactor.run();

    // N2 in feed: 25 mol/s, conversion 15% -> 3.75 mol N2 reacted
    // NH3 produced: 2 * 3.75 = 7.5 mol/s
    double nh3Production = reactor.getAmmoniaProductionRate("mol/s");
    assertEquals(7.5, nh3Production, 0.5);
    assertEquals(0.15, reactor.getConversion(), 0.02);
    assertTrue(reactor.getHeatDuty() < 0.0, "Reaction should be exothermic");
  }

  @Test
  public void testH2Limited() {
    // Non-stoichiometric feed: excess N2
    SystemInterface synthGas = new SystemSrkEos(273.15 + 400.0, 150.0);
    synthGas.addComponent("hydrogen", 0.20);
    synthGas.addComponent("nitrogen", 0.80);
    synthGas.setMixingRule("classic");

    Stream feed = new Stream("Feed", synthGas);
    feed.setFlowRate(100.0, "mole/sec");
    feed.run();

    AmmoniaSynthesisReactor reactor = new AmmoniaSynthesisReactor("Reactor", feed);
    reactor.setPerPassConversion(0.90); // High conversion requested
    reactor.setIsothermal(true);
    reactor.run();

    // Should be limited by H2 availability
    double nh3Rate = reactor.getAmmoniaProductionRate("mol/s");
    assertTrue(nh3Rate > 0, "Should produce some NH3");
  }

  @Test
  public void testMassBalance() {
    SystemInterface synthGas = new SystemSrkEos(273.15 + 450.0, 200.0);
    synthGas.addComponent("hydrogen", 0.75);
    synthGas.addComponent("nitrogen", 0.25);
    synthGas.setMixingRule("classic");

    Stream feed = new Stream("Feed", synthGas);
    feed.setFlowRate(100.0, "mole/sec");
    feed.run();

    AmmoniaSynthesisReactor reactor = new AmmoniaSynthesisReactor("Reactor", feed);
    reactor.setPerPassConversion(0.15);
    reactor.setIsothermal(true);
    reactor.run();

    // Check element balance: N atoms should be conserved
    double n2In = 25.0; // 25 mol/s N2
    SystemInterface outSys = reactor.getOutletStream().getThermoSystem();
    double n2Out = outSys.getComponent("nitrogen").getNumberOfmoles();
    double nh3Out = outSys.getComponent("ammonia").getNumberOfmoles();
    // N balance: n2In * 2 = n2Out * 2 + nh3Out
    double nIn = n2In * 2.0;
    double nOut = n2Out * 2.0 + nh3Out;
    assertEquals(nIn, nOut, 0.5);
  }

  @Test
  public void testProductionRateUnits() {
    SystemInterface synthGas = new SystemSrkEos(273.15 + 450.0, 200.0);
    synthGas.addComponent("hydrogen", 0.75);
    synthGas.addComponent("nitrogen", 0.25);
    synthGas.setMixingRule("classic");

    Stream feed = new Stream("Feed", synthGas);
    feed.setFlowRate(1000.0, "mole/sec");
    feed.run();

    AmmoniaSynthesisReactor reactor = new AmmoniaSynthesisReactor("Reactor", feed);
    reactor.setPerPassConversion(0.15);
    reactor.setIsothermal(true);
    reactor.run();

    double molPerSec = reactor.getAmmoniaProductionRate("mol/s");
    double kgPerHr = reactor.getAmmoniaProductionRate("kg/hr");
    // kg/hr = mol/s * MW * 3600
    assertEquals(molPerSec * 0.01703 * 3600.0, kgPerHr, 1.0);

    double tonnePerDay = reactor.getAmmoniaProductionRate("tonne/day");
    assertEquals(kgPerHr * 24.0 / 1000.0, tonnePerDay, 0.1);
  }

  @Test
  public void testEquipmentFactory() {
    AmmoniaSynthesisReactor reactor = (AmmoniaSynthesisReactor) neqsim.process.equipment
        .EquipmentFactory.createEquipment("reactor", "ammoniasynthesisreactor");
    assertTrue(reactor instanceof AmmoniaSynthesisReactor);
  }
}
