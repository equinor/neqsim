package neqsim.process.equipment.reservoir;

import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.pvtsimulation.reservoirproperties.materialbalance.GasMaterialBalance;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Integration tests for {@link ReservoirSurveillance} coupled with {@link SimpleReservoir}.
 */
public class ReservoirSurveillanceTest {

  private SimpleReservoir buildAndDepleteGasReservoir() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 90.0, 250.0);
    fluid.addComponent("methane", 90.0);
    fluid.addComponent("ethane", 6.0);
    fluid.addComponent("propane", 3.0);
    fluid.addComponent("water", 1.0);
    fluid.setMixingRule(2);
    fluid.setMultiPhaseCheck(true);

    SimpleReservoir reservoir = new SimpleReservoir("gas reservoir");
    reservoir.setReservoirFluid(fluid, 3.0e8, 0.0, 1.0e7);

    StreamInterface gasProd = reservoir.addGasProducer("gp1");
    gasProd.setFlowRate(4.0, "MSm3/day");

    reservoir.run();
    double dt = 24 * 3600.0 * 365.0;
    for (int i = 0; i < 9; i++) {
      reservoir.runTransient(dt);
    }
    return reservoir;
  }

  @Test
  public void testHistoryRecorded() {
    SimpleReservoir reservoir = buildAndDepleteGasReservoir();
    Assertions.assertEquals(10, reservoir.getPressureHistory().length, "1 initial + 9 transient points");
    Assertions.assertEquals(10, reservoir.getGasProductionHistory().length);
    // Pressure should decline monotonically under depletion.
    double[] p = reservoir.getPressureHistory();
    for (int i = 1; i < p.length; i++) {
      Assertions.assertTrue(p[i] < p[i - 1] + 1.0e-6, "Pressure should not increase during depletion");
    }
  }

  @Test
  public void testEstimatedOgipMatchesActual() {
    SimpleReservoir reservoir = buildAndDepleteGasReservoir();
    ReservoirSurveillance surv = reservoir.getSurveillance();
    GasMaterialBalance.Result r = surv.gasMaterialBalance();

    double estimated = r.getOgip() / 1.0e9;
    double actual = reservoir.getOGIP("GSm3");
    Assertions.assertTrue(r.getRSquared() > 0.98, "P/Z fit should be strong, R2=" + r.getRSquared());
    Assertions.assertEquals(actual, estimated, actual * 0.25,
        "Estimated OGIP (" + estimated + ") should be within 25% of actual (" + actual + ")");
  }

  @Test
  public void testGasDeclineFit() {
    SimpleReservoir reservoir = buildAndDepleteGasReservoir();
    ReservoirSurveillance surv = reservoir.getSurveillance();
    Map<String, Double> fit = surv.fitGasDecline();
    Assertions.assertTrue(fit.get("qi") > 0.0, "Fitted initial rate should be positive");
    Assertions.assertTrue(fit.get("b") >= 0.0 && fit.get("b") <= 1.0, "b should be in [0,1]");
  }

  @Test
  public void testAquiferInfluxIncreasesWithDepletion() {
    SimpleReservoir reservoir = buildAndDepleteGasReservoir();
    ReservoirSurveillance surv = reservoir.getSurveillance();
    surv.attachAquifer(1.0e-13, 0.20, 5.0e-4, 1.0e-4, 50.0, 2000.0, 360.0, Double.POSITIVE_INFINITY);
    double[] we = surv.computeWaterInflux();
    Assertions.assertEquals(reservoir.getTimeHistory().length, we.length);
    Assertions.assertEquals(0.0, we[0], 1.0e-9);
    for (int i = 1; i < we.length; i++) {
      Assertions.assertTrue(we[i] >= we[i - 1] - 1.0e-6, "Water influx should be non-decreasing");
    }
    Assertions.assertTrue(we[we.length - 1] > 0.0, "Total influx should be positive under depletion");
  }

  @Test
  public void testSummary() {
    SimpleReservoir reservoir = buildAndDepleteGasReservoir();
    Map<String, Double> summary = reservoir.getSurveillance().summary();
    Assertions.assertEquals(10.0, summary.get("numberOfHistoryPoints"), 1.0e-9);
    Assertions.assertTrue(summary.containsKey("estimatedOGIP_GSm3"));
    Assertions.assertTrue(summary.get("estimatedOGIP_GSm3") > 0.0);
  }
}
