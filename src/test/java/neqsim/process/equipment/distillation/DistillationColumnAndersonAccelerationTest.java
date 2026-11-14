package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import neqsim.process.equipment.distillation.DistillationColumn.SolverType;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for Anderson mixing of the tray outlet states in sequential substitution.
 */
public class DistillationColumnAndersonAccelerationTest {
  /**
   * Build a ten-tray reboiled deethanizer whose substitution sweeps converge at about 0.99 per sweep.
   *
   * @param anderson whether Anderson mixing is enabled
   * @return configured column
   */
  private static DistillationColumn createDeethanizer(boolean anderson) {
    SystemInterface gas = new SystemSrkEos(216.0, 30.0);
    gas.addComponent("nitrogen", 1.67366E-3);
    gas.addComponent("CO2", 1.06819E-4);
    gas.addComponent("methane", 5.14168E-1);
    gas.addComponent("ethane", 1.92528E-1);
    gas.addComponent("propane", 1.70001E-1);
    gas.addComponent("i-butane", 3.14561E-2);
    gas.addComponent("n-butane", 5.58678E-2);
    gas.addComponent("i-pentane", 1.29573E-2);
    gas.addComponent("n-pentane", 1.23719E-2);
    gas.addComponent("n-hexane", 5.12878E-3);
    gas.addComponent("n-heptane", 1.0E-2);
    gas.setMixingRule("classic");
    Stream feed = new Stream("deethanizer feed", gas);
    feed.setFlowRate(100.0, "kg/hr");
    feed.run();

    DistillationColumn column = new DistillationColumn("deethanizer", 10, true, false);
    column.addFeedStream(feed, 10);
    column.getReboiler().setOutletTemperature(105.0 + 273.15);
    column.setTopPressure(30.0);
    column.setBottomPressure(32.0);
    column.setMaxNumberOfIterations(80);
    column.setSolverType(SolverType.DIRECT_SUBSTITUTION);
    column.setAndersonAccelerationEnabled(anderson);
    return column;
  }

  /** Mixing must cut the sweep count and land closer to the tightly converged product split. */
  @Test
  @Timeout(value = 120, unit = TimeUnit.SECONDS)
  public void mixingNeedsFewerSweepsAndIsCloserToTheFixedPoint() {
    DistillationColumn plain = createDeethanizer(false);
    plain.run();
    DistillationColumn mixed = createDeethanizer(true);
    mixed.run();

    assertTrue(plain.solved());
    assertTrue(mixed.solved());
    assertEquals(0, plain.getLastAndersonAccelerationCount());
    assertTrue(mixed.getLastAndersonAccelerationCount() > 0);
    assertTrue(2 * mixed.getLastIterationCount() < plain.getLastIterationCount(),
        "Mixing must at least halve the sweeps: " + mixed.getLastIterationCount() + " vs "
            + plain.getLastIterationCount());

    // Overhead rate of plain substitution run to a 1e-6 K sweep tolerance.
    double convergedGasRate = 50.7598;
    double plainError = Math.abs(plain.getGasOutStream().getFlowRate("kg/hr") - convergedGasRate);
    double mixedError = Math.abs(mixed.getGasOutStream().getFlowRate("kg/hr") - convergedGasRate);
    assertTrue(mixedError < plainError, "Mixed result must be closer to the fixed point");
    assertEquals(convergedGasRate, mixed.getGasOutStream().getFlowRate("kg/hr"), 0.02);
  }
}
