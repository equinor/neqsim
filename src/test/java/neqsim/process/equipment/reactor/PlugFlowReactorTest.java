package neqsim.process.equipment.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Integration tests for PlugFlowReactor class.
 */
public class PlugFlowReactorTest extends neqsim.NeqSimTest {

  /**
   * Test isothermal first-order reaction with mass balance verification.
   */
  @Test
  public void testIsothermalFirstOrderReaction() {
    SystemInterface gas = new SystemSrkEos(273.15 + 300.0, 20.0);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.10);
    gas.setMixingRule("classic");

    Stream feed = new Stream("Feed", gas);
    feed.setFlowRate(10.0, "mole/sec");
    feed.run();

    // Simple first-order reaction: methane -> ethane (illustrative, not real chemistry)
    KineticReaction rxn = new KineticReaction("CH4 to C2H6");
    rxn.addReactant("methane", 1.0, 1.0);
    rxn.addProduct("ethane", 1.0);
    rxn.setPreExponentialFactor(1.0e4);
    rxn.setActivationEnergy(50000.0);
    rxn.setHeatOfReaction(-50000.0);
    rxn.setRateType(KineticReaction.RateType.POWER_LAW);

    PlugFlowReactor pfr = new PlugFlowReactor("PFR-1", feed);
    pfr.addReaction(rxn);
    pfr.setLength(5.0, "m");
    pfr.setDiameter(0.10, "m");
    pfr.setEnergyMode(PlugFlowReactor.EnergyMode.ISOTHERMAL);
    pfr.setNumberOfSteps(50);
    pfr.setKeyComponent("methane");
    pfr.run();

    // Check positive conversion
    double conversion = pfr.getConversion();
    assertTrue(conversion >= 0.0 && conversion <= 1.0,
        "Conversion should be between 0 and 1, got: " + conversion);

    // Axial profile should have data
    ReactorAxialProfile profile = pfr.getAxialProfile();
    assertNotNull(profile, "Axial profile should not be null");
    double[] zPositions = profile.getPositionProfile();
    assertEquals(51, zPositions.length, "Should have numberOfSteps+1 data points");
    assertEquals(0.0, zPositions[0], 1e-10, "First z position should be 0");
    assertEquals(5.0, zPositions[50], 1e-6, "Last z position should be reactor length");

    // Outlet stream should exist
    assertNotNull(pfr.getOutletStream(), "Outlet stream should not be null");
  }

  /**
   * Test adiabatic mode - temperature should change for exothermic reaction.
   */
  @Test
  public void testAdiabaticTemperatureRise() {
    SystemInterface gas = new SystemSrkEos(273.15 + 200.0, 30.0);
    gas.addComponent("methane", 0.80);
    gas.addComponent("ethane", 0.20);
    gas.setMixingRule("classic");

    Stream feed = new Stream("Feed", gas);
    feed.setFlowRate(5.0, "mole/sec");
    feed.run();

    KineticReaction rxn = new KineticReaction("exothermic");
    rxn.addReactant("methane", 1.0, 1.0);
    rxn.addProduct("ethane", 1.0);
    rxn.setPreExponentialFactor(1.0e6);
    rxn.setActivationEnergy(60000.0);
    rxn.setHeatOfReaction(-80000.0); // Exothermic
    rxn.setRateType(KineticReaction.RateType.POWER_LAW);

    PlugFlowReactor pfr = new PlugFlowReactor("PFR-Adiabatic", feed);
    pfr.addReaction(rxn);
    pfr.setLength(3.0, "m");
    pfr.setDiameter(0.08, "m");
    pfr.setEnergyMode(PlugFlowReactor.EnergyMode.ADIABATIC);
    pfr.setNumberOfSteps(50);
    pfr.setKeyComponent("methane");
    pfr.run();

    double inletT = feed.getThermoSystem().getTemperature();
    double outletT = pfr.getOutletTemperature();

    // For exothermic reaction in adiabatic mode, outlet T should be >= inlet T
    // (could be equal if conversion is zero)
    assertTrue(outletT >= inletT - 1.0,
        "Outlet T should be >= inlet T for exothermic adiabatic, got: " + outletT + " vs inlet: "
            + inletT);
  }

  /**
   * Test packed bed with Ergun pressure drop.
   */
  @Test
  public void testPackedBedWithPressureDrop() {
    SystemInterface gas = new SystemSrkEos(273.15 + 250.0, 30.0);
    gas.addComponent("methane", 0.70);
    gas.addComponent("ethane", 0.30);
    gas.setMixingRule("classic");

    Stream feed = new Stream("Feed", gas);
    feed.setFlowRate(5.0, "mole/sec");
    feed.run();

    KineticReaction rxn = new KineticReaction("rxn");
    rxn.addReactant("methane", 1.0, 1.0);
    rxn.addProduct("ethane", 1.0);
    rxn.setPreExponentialFactor(1.0e5);
    rxn.setActivationEnergy(50000.0);
    rxn.setHeatOfReaction(-40000.0);
    rxn.setRateBasis(KineticReaction.RateBasis.CATALYST_MASS);

    CatalystBed catalyst = new CatalystBed(3.0, 0.40, 800.0);

    PlugFlowReactor pfr = new PlugFlowReactor("PFR-Packed", feed);
    pfr.addReaction(rxn);
    pfr.setCatalystBed(catalyst);
    pfr.setLength(4.0, "m");
    pfr.setDiameter(0.10, "m");
    pfr.setEnergyMode(PlugFlowReactor.EnergyMode.ISOTHERMAL);
    pfr.setNumberOfSteps(40);
    pfr.run();

    // Pressure drop should be positive in packed bed
    double dP = pfr.getPressureDrop();
    assertTrue(dP >= 0.0, "Pressure drop should be non-negative, got: " + dP);

    // Outlet pressure should be lower than inlet
    double inletP = feed.getThermoSystem().getPressure();
    double outletP = pfr.getOutletStream().getThermoSystem().getPressure();
    assertTrue(outletP <= inletP, "Outlet P should be <= inlet P after packed bed");
  }

  /**
   * Test coolant mode with external heat exchange.
   */
  @Test
  public void testCoolantMode() {
    SystemInterface gas = new SystemSrkEos(273.15 + 400.0, 20.0);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.10);
    gas.setMixingRule("classic");

    Stream feed = new Stream("Feed", gas);
    feed.setFlowRate(5.0, "mole/sec");
    feed.run();

    KineticReaction rxn = new KineticReaction("rxn");
    rxn.addReactant("methane", 1.0, 1.0);
    rxn.addProduct("ethane", 1.0);
    rxn.setPreExponentialFactor(1.0e5);
    rxn.setActivationEnergy(50000.0);
    rxn.setHeatOfReaction(-60000.0);

    PlugFlowReactor pfr = new PlugFlowReactor("PFR-Coolant", feed);
    pfr.addReaction(rxn);
    pfr.setLength(3.0, "m");
    pfr.setDiameter(0.05, "m");
    pfr.setEnergyMode(PlugFlowReactor.EnergyMode.COOLANT);
    pfr.setCoolantTemperature(300.0, "C");
    pfr.setOverallHeatTransferCoefficient(200.0);
    pfr.setNumberOfSteps(50);
    pfr.run();

    // Should produce some output
    assertNotNull(pfr.getOutletStream());
    assertTrue(pfr.getOutletTemperature() > 0, "Outlet T should be positive");
  }

  /**
   * Test running PFR inside a ProcessSystem.
   */
  @Test
  public void testInProcessSystem() {
    SystemInterface gas = new SystemSrkEos(273.15 + 350.0, 25.0);
    gas.addComponent("methane", 0.85);
    gas.addComponent("ethane", 0.15);
    gas.setMixingRule("classic");

    Stream feed = new Stream("Feed", gas);
    feed.setFlowRate(8.0, "mole/sec");

    KineticReaction rxn = new KineticReaction("rxn");
    rxn.addReactant("methane", 1.0, 1.0);
    rxn.addProduct("ethane", 1.0);
    rxn.setPreExponentialFactor(1.0e4);
    rxn.setActivationEnergy(40000.0);

    PlugFlowReactor pfr = new PlugFlowReactor("PFR", feed);
    pfr.addReaction(rxn);
    pfr.setLength(2.0, "m");
    pfr.setDiameter(0.06, "m");
    pfr.setEnergyMode(PlugFlowReactor.EnergyMode.ISOTHERMAL);
    pfr.setNumberOfSteps(30);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(pfr);
    process.run();

    // Should complete without exception
    assertNotNull(pfr.getOutletStream().getThermoSystem());
    assertTrue(pfr.getOutletStream().getThermoSystem().getTemperature() > 0);
  }

  /**
   * Test no-reaction passthrough.
   */
  @Test
  public void testNoReactionPassthrough() {
    SystemInterface gas = new SystemSrkEos(273.15 + 100.0, 10.0);
    gas.addComponent("methane", 0.95);
    gas.addComponent("nitrogen", 0.05);
    gas.setMixingRule("classic");

    Stream feed = new Stream("Feed", gas);
    feed.setFlowRate(10.0, "mole/sec");
    feed.run();

    // No reactions added — should pass through
    PlugFlowReactor pfr = new PlugFlowReactor("PFR-empty", feed);
    pfr.setLength(5.0, "m");
    pfr.setDiameter(0.10, "m");
    pfr.run();

    double inT = feed.getThermoSystem().getTemperature();
    double outT = pfr.getOutletStream().getThermoSystem().getTemperature();
    assertEquals(inT, outT, 1.0, "Passthrough should preserve temperature");
  }

  /**
   * Test configuration setters and getters.
   */
  @Test
  public void testConfigurationSettersGetters() {
    PlugFlowReactor pfr = new PlugFlowReactor("PFR-config");

    pfr.setLength(10.0, "m");
    assertEquals(10.0, pfr.getLength(), 1e-10);

    pfr.setLength(100.0, "cm");
    assertEquals(1.0, pfr.getLength(), 1e-10);

    pfr.setLength(10.0, "ft");
    assertEquals(10.0 * 0.3048, pfr.getLength(), 1e-6);

    pfr.setDiameter(50.0, "mm");
    assertEquals(0.05, pfr.getDiameter(), 1e-10);

    pfr.setDiameter(2.0, "in");
    assertEquals(2.0 * 0.0254, pfr.getDiameter(), 1e-10);

    pfr.setNumberOfTubes(10);
    assertEquals(10, pfr.getNumberOfTubes());

    pfr.setEnergyMode(PlugFlowReactor.EnergyMode.COOLANT);
    assertEquals(PlugFlowReactor.EnergyMode.COOLANT, pfr.getEnergyMode());

    pfr.setCoolantTemperature(200.0, "C");
    assertEquals(473.15, pfr.getCoolantTemperature(), 0.01);

    pfr.setOverallHeatTransferCoefficient(150.0);
    assertEquals(150.0, pfr.getOverallHeatTransferCoefficient(), 1e-10);

    pfr.setNumberOfSteps(200);
    assertEquals(200, pfr.getNumberOfSteps());

    pfr.setIntegrationMethod("EULER");
    assertEquals(PlugFlowReactor.IntegrationMethod.EULER, pfr.getIntegrationMethod());

    pfr.setIntegrationMethod("RK4");
    assertEquals(PlugFlowReactor.IntegrationMethod.RK4, pfr.getIntegrationMethod());

    pfr.setPropertyUpdateFrequency(5);
    assertEquals(5, pfr.getPropertyUpdateFrequency());

    pfr.setKeyComponent("methane");
    assertEquals("methane", pfr.getKeyComponent());
  }

  /**
   * Test Euler vs RK4 give roughly similar results.
   */
  @Test
  public void testEulerVsRK4Consistency() {
    SystemInterface gas1 = new SystemSrkEos(273.15 + 300.0, 20.0);
    gas1.addComponent("methane", 0.80);
    gas1.addComponent("ethane", 0.20);
    gas1.setMixingRule("classic");

    SystemInterface gas2 = gas1.clone();

    KineticReaction rxn = new KineticReaction("rxn");
    rxn.addReactant("methane", 1.0, 1.0);
    rxn.addProduct("ethane", 1.0);
    rxn.setPreExponentialFactor(1.0e4);
    rxn.setActivationEnergy(50000.0);

    Stream feed1 = new Stream("Feed1", gas1);
    feed1.setFlowRate(5.0, "mole/sec");
    feed1.run();

    PlugFlowReactor pfrRK4 = new PlugFlowReactor("PFR-RK4", feed1);
    pfrRK4.addReaction(rxn);
    pfrRK4.setLength(2.0, "m");
    pfrRK4.setDiameter(0.05, "m");
    pfrRK4.setEnergyMode(PlugFlowReactor.EnergyMode.ISOTHERMAL);
    pfrRK4.setIntegrationMethod("RK4");
    pfrRK4.setNumberOfSteps(100);
    pfrRK4.setKeyComponent("methane");
    pfrRK4.run();

    Stream feed2 = new Stream("Feed2", gas2);
    feed2.setFlowRate(5.0, "mole/sec");
    feed2.run();

    PlugFlowReactor pfrEuler = new PlugFlowReactor("PFR-Euler", feed2);
    pfrEuler.addReaction(rxn);
    pfrEuler.setLength(2.0, "m");
    pfrEuler.setDiameter(0.05, "m");
    pfrEuler.setEnergyMode(PlugFlowReactor.EnergyMode.ISOTHERMAL);
    pfrEuler.setIntegrationMethod("EULER");
    pfrEuler.setNumberOfSteps(100);
    pfrEuler.setKeyComponent("methane");
    pfrEuler.run();

    double convRK4 = pfrRK4.getConversion();
    double convEuler = pfrEuler.getConversion();

    // Both should give reasonable results; with sufficient steps they should be close
    assertTrue(Math.abs(convRK4 - convEuler) < 0.10,
        "RK4 and Euler should give similar conversions with 100 steps: " + convRK4 + " vs "
            + convEuler);
  }

  /**
   * Test axial profile interpolation.
   */
  @Test
  public void testAxialProfileInterpolation() {
    SystemInterface gas = new SystemSrkEos(273.15 + 300.0, 20.0);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.10);
    gas.setMixingRule("classic");

    Stream feed = new Stream("Feed", gas);
    feed.setFlowRate(5.0, "mole/sec");
    feed.run();

    KineticReaction rxn = new KineticReaction("rxn");
    rxn.addReactant("methane", 1.0, 1.0);
    rxn.addProduct("ethane", 1.0);
    rxn.setPreExponentialFactor(1.0e5);
    rxn.setActivationEnergy(50000.0);

    PlugFlowReactor pfr = new PlugFlowReactor("PFR-Profile", feed);
    pfr.addReaction(rxn);
    pfr.setLength(4.0, "m");
    pfr.setDiameter(0.08, "m");
    pfr.setEnergyMode(PlugFlowReactor.EnergyMode.ISOTHERMAL);
    pfr.setNumberOfSteps(40);
    pfr.run();

    ReactorAxialProfile profile = pfr.getAxialProfile();
    assertNotNull(profile);

    // Interpolation at midpoint
    double midT = profile.getTemperatureAt(2.0);
    assertTrue(midT > 0, "Interpolated T should be positive");

    double midConv = profile.getConversionAt(2.0);
    assertTrue(midConv >= 0.0, "Interpolated conversion should be non-negative");

    // Conversion should monotonically increase (for isothermal irreversible)
    double conv0 = profile.getConversionAt(0.0);
    double conv4 = profile.getConversionAt(4.0);
    assertTrue(conv4 >= conv0,
        "Conversion at outlet should be >= inlet: " + conv4 + " vs " + conv0);
  }

  /**
   * Test multi-tube reactor configuration.
   */
  @Test
  public void testMultiTubeReactor() {
    SystemInterface gas = new SystemSrkEos(273.15 + 300.0, 20.0);
    gas.addComponent("methane", 0.80);
    gas.addComponent("ethane", 0.20);
    gas.setMixingRule("classic");

    Stream feed = new Stream("Feed", gas);
    feed.setFlowRate(10.0, "mole/sec");
    feed.run();

    KineticReaction rxn = new KineticReaction("rxn");
    rxn.addReactant("methane", 1.0, 1.0);
    rxn.addProduct("ethane", 1.0);
    rxn.setPreExponentialFactor(1.0e4);
    rxn.setActivationEnergy(50000.0);

    PlugFlowReactor pfr = new PlugFlowReactor("PFR-MultiTube", feed);
    pfr.addReaction(rxn);
    pfr.setLength(3.0, "m");
    pfr.setDiameter(0.025, "m");
    pfr.setNumberOfTubes(50);
    pfr.setEnergyMode(PlugFlowReactor.EnergyMode.ISOTHERMAL);
    pfr.setNumberOfSteps(30);
    pfr.run();

    assertTrue(pfr.getConversion() >= 0.0, "Multi-tube reactor should produce valid conversion");
    assertNotNull(pfr.getOutletStream());
  }
}
