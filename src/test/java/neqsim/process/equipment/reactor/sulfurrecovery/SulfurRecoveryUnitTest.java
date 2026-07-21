package neqsim.process.equipment.reactor.sulfurrecovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** End-to-end tests for the integrated sulfur-recovery process. */
public class SulfurRecoveryUnitTest extends neqsim.NeqSimTest {
  /** A conventional two-stage SRU builds and closes the sulfur balance. */
  @Test
  public void testStraightThroughProcess() {
    Stream acidGas = createFeed();
    SulfurRecoveryUnit unit = new SulfurRecoveryProcessBuilder("SRU", acidGas)
        .catalyticStages(2)
        .incinerator(true)
        .build();
    unit.run();

    SulfurRecoveryPerformance performance = unit.getPerformance();
    assertNotNull(performance);
    assertEquals(2, unit.getCatalyticConverters().size());
    assertEquals(3, unit.getSulfurCondensers().size());
    assertTrue(performance.getRecoveredSulfurKgPerHour() > 0.0);
    assertTrue(performance.getSulfurRecoveryPercent() > 0.0);
    assertTrue(performance.getSulfurRecoveryPercent() <= 100.01);
    assertTrue(Math.abs(performance.getSulfurBalanceRelativeError()) < 1.0e-5);
    assertEquals(2.0, performance.getTailGasH2SToSO2Ratio(), 0.05);
    assertNotNull(unit.getCombinedSulfurProductStream());
    assertNotNull(unit.getThermalIncinerator());
  }

  /** Oxygen-enriched configuration reduces inert nitrogen in the furnace train. */
  @Test
  public void testOxygenEnrichedConfiguration() {
    Stream acidGas = createFeed();
    SulfurRecoveryUnit airUnit = new SulfurRecoveryProcessBuilder("air SRU", acidGas)
        .catalyticStages(1)
        .incinerator(false)
        .build();
    airUnit.run();
    double airNitrogen = airUnit.getReactionFurnace().getInletStream().getThermoSystem()
        .getComponent("nitrogen").getNumberOfmoles();

    SulfurRecoveryUnit enrichedUnit = new SulfurRecoveryProcessBuilder("enriched SRU", acidGas)
        .configuration(SulfurRecoveryUnit.Configuration.OXYGEN_ENRICHED)
        .catalyticStages(1)
        .incinerator(false)
        .build();
    enrichedUnit.run();
    double enrichedNitrogen = enrichedUnit.getReactionFurnace().getInletStream().getThermoSystem()
        .getComponent("nitrogen").getNumberOfmoles();

    assertTrue(enrichedNitrogen < airNitrogen);
  }

  /** Builder creates the special split-flow topology and requested stage count. */
  @Test
  public void testSplitFlowBuilderConfiguration() {
    SulfurRecoveryUnit unit = new SulfurRecoveryProcessBuilder("split SRU", createFeed())
        .configuration(SulfurRecoveryUnit.Configuration.SPLIT_FLOW)
        .splitFlowFurnaceFraction(0.35)
        .catalyticStages(1)
        .incinerator(false)
        .build();
    unit.run();

    assertEquals(SulfurRecoveryUnit.Configuration.SPLIT_FLOW, unit.getConfiguration());
    assertEquals(1, unit.getCatalyticConverters().size());
    assertNotNull(unit.getClausTailGasStream());
  }

  /** Integrated TGTU exposes its converged acid-gas recycle and cleaned outlet. */
  @Test
  public void testTailGasRecycleTopology() {
    SulfurRecoveryUnit unit = new SulfurRecoveryProcessBuilder("SRU with TGTU", createFeed())
        .catalyticStages(1)
        .tailGasTreatment(true)
        .incinerator(false)
        .build();
    unit.setMaximumAirControlIterations(1);
    unit.setRecycleConvergence(1.0, 2, 0.5);
    unit.run();

    assertNotNull(unit.getTailGasTreatmentUnit());
    assertNotNull(unit.getAcidGasRecycleStream());
    assertTrue(unit.getPerformance().isRecycleConverged());
  }

  /** Null configuration and catalyst selections fail explicitly. */
  @Test
  public void testNullConfigurationRejected() {
    SulfurRecoveryUnit unit = new SulfurRecoveryProcessBuilder("SRU", createFeed()).build();
    assertThrows(IllegalArgumentException.class, () -> unit.setConfiguration(null));
    assertThrows(IllegalArgumentException.class, () -> unit.setCatalystType(0, null));
    assertThrows(IllegalArgumentException.class,
        () -> new SulfurRecoveryProcessBuilder("SRU", createFeed()).configuration(null));
  }

  /** Create representative sour-water-free acid gas. */
  private Stream createFeed() {
    SystemInterface system = new SystemSrkEos(313.15, 2.0);
    system.addComponent("H2S", 10.0);
    system.addComponent("CO2", 2.0);
    system.addComponent("methane", 0.05);
    system.addComponent("water", 1.0);
    system.setMixingRule("classic");
    Stream stream = new Stream("acid gas", system);
    stream.run();
    return stream;
  }
}
