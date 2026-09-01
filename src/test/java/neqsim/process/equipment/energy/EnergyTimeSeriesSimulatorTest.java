package neqsim.process.equipment.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.EnergyBus;
import neqsim.process.equipment.stream.EnergyPort;
import neqsim.process.equipment.stream.EnergyPortDirection;
import neqsim.process.equipment.stream.EnergyPortMode;
import neqsim.process.equipment.stream.EnergyType;
import neqsim.process.processmodel.ProcessSystem;

class EnergyTimeSeriesSimulatorTest {

  @Test
  void testProfileInterpolationAndValidation() {
    EnergyTimeSeriesProfile step = EnergyTimeSeriesProfile.step("step", new double[] { 0.0, 10.0, 20.0 },
        new double[] { 1.0, 2.0, 4.0 });
    EnergyTimeSeriesProfile linear = EnergyTimeSeriesProfile.linear("linear", new double[] { 0.0, 10.0 },
        new double[] { 0.0, 100.0 });

    assertEquals(1.0, step.getValue(9.0), 1.0e-12);
    assertEquals(1.0, step.getValue(Math.nextDown(10.0)), 1.0e-12);
    assertEquals(2.0, step.getValue(10.0), 1.0e-12);
    assertEquals(2.0, step.getValue(Math.nextUp(10.0)), 1.0e-12);
    assertEquals(4.0, step.getValue(100.0), 1.0e-12);
    assertEquals(50.0, linear.getValue(5.0), 1.0e-12);
    assertEquals(0.0, linear.getValue(0.0), 1.0e-12);
    assertEquals(100.0, linear.getValue(20.0), 1.0e-12);

    assertThrows(IllegalArgumentException.class,
        () -> EnergyTimeSeriesProfile.step("bad", new double[] { 0.0, 0.0 }, new double[] { 1.0, 2.0 }));
    assertThrows(IllegalArgumentException.class, () -> new EnergyTimeSeriesProfile.Point(Double.NaN, 1.0));
    assertThrows(IllegalArgumentException.class, () -> step.getValue(-1.0));
  }

  @Test
  void testIntegratedEnergyCostEmissionsAndPartialFinalInterval() {
    EnergyBus grid = new EnergyBus("grid", EnergyType.ELECTRICAL);
    EnergyPort generator = port("generator", EnergyPortDirection.OUTPUT, EnergyPortMode.CALCULATED, grid);
    EnergyPort load = port("load", EnergyPortDirection.INPUT, EnergyPortMode.SPECIFICATION, grid);
    generator.setEnergyPricePerMWh(100.0);
    generator.setEmissionFactorKgPerMWh(200.0);

    EnergyTimeSeriesSimulator simulator = new EnergyTimeSeriesSimulator(new ProcessSystem());
    simulator.addEnergyBus(grid);
    simulator.setIntervalSeconds(3600.0);
    simulator.setDurationSeconds(9000.0);
    simulator.addProfile(
        EnergyTimeSeriesProfile.step("generation", new double[] { 0.0, 7200.0 }, new double[] { 2.0e6, 1.0e6 }),
        value -> generator.setDuty(value));
    simulator.addProfile(
        EnergyTimeSeriesProfile.step("load", new double[] { 0.0, 3600.0 }, new double[] { 1.0e6, 1.5e6 }),
        value -> load.setRequestedPower(value));

    EnergyTimeSeriesResult result = simulator.run();

    assertEquals(3, result.getIntervals().size());
    assertEquals(3600.0, result.getIntervals().get(0).getDurationSeconds(), 1.0e-12);
    assertEquals(1800.0, result.getIntervals().get(2).getDurationSeconds(), 1.0e-12);
    assertEquals(3.0, result.getServedEnergyMWh(), 1.0e-12);
    assertEquals(0.25, result.getUnmetEnergyMWh(), 1.0e-12);
    assertEquals(1.5, result.getCurtailedEnergyMWh(), 1.0e-12);
    assertEquals(300.0, result.getOperatingCost(), 1.0e-12);
    assertEquals(600.0, result.getCo2EmissionsKg(), 1.0e-12);
    assertThrows(UnsupportedOperationException.class, () -> result.getIntervals().clear());
    assertThrows(UnsupportedOperationException.class, () -> result.getIntervals().get(0).getNetworkReports().clear());
  }

  @Test
  void testMultipleBusesAndJsonResult() {
    EnergyBus electrical = new EnergyBus("electrical", EnergyType.ELECTRICAL);
    EnergyBus heat = new EnergyBus("heat", EnergyType.HEAT);
    electrical.setContribution("source", 1.0e6);
    electrical.setContribution("load", -0.5e6);
    heat.setContribution("source", 2.0e6);
    heat.setContribution("load", -1.0e6);

    EnergyTimeSeriesSimulator simulator = new EnergyTimeSeriesSimulator(new ProcessSystem());
    simulator.addEnergyBus(electrical);
    simulator.addEnergyBus(heat);
    simulator.setDurationSeconds(3600.0);

    EnergyTimeSeriesResult result = simulator.run();

    assertEquals(2, result.getIntervals().get(0).getNetworkReports().size());
    assertEquals(1.5, result.getServedEnergyMWh(), 1.0e-12);
    org.junit.jupiter.api.Assertions.assertTrue(result.toJson().contains("servedEnergyMWh"));
  }

  @Test
  void testSimulatorPreconditions() {
    EnergyTimeSeriesSimulator simulator = new EnergyTimeSeriesSimulator(new ProcessSystem());
    assertThrows(IllegalStateException.class, simulator::run);
    assertThrows(IllegalArgumentException.class, () -> simulator.setIntervalSeconds(0.0));
    assertThrows(IllegalArgumentException.class, () -> simulator.setDurationSeconds(Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> simulator.setExecutionMode(null));
    assertThrows(IllegalArgumentException.class, () -> new EnergyTimeSeriesProfile("empty",
        EnergyTimeSeriesProfile.Interpolation.STEP, Arrays.<EnergyTimeSeriesProfile.Point>asList()));
  }

  private static EnergyPort port(String owner, EnergyPortDirection direction, EnergyPortMode mode, EnergyBus bus) {
    EnergyPort port = new EnergyPort("power", bus.getEnergyType(), direction, mode);
    port.setOwnerName(owner);
    port.connect(bus);
    return port;
  }
}
