package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests the opt-in physical-property skipping used by the sequential column solver.
 *
 * <p>
 * The sequential solver skips expensive physical/transport property initialization on every tray flash during iteration
 * (only thermodynamic properties are needed for the mass/energy balances) and calls
 * {@link SimpleTray#finalizeTrayProperties()} once on the converged state. These tests verify that (1) the default
 * behavior still computes full properties and (2) after skipping during a run, {@code finalizeTrayProperties()}
 * restores full physical properties.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class SimpleTrayPropertySkipTest {

  /**
   * Build a representative two-phase feed stream.
   *
   * @return a run two-phase stream
   */
  private Stream twoPhaseFeed() {
    SystemSrkEos fluid = new SystemSrkEos(273.15 - 10.0, 20.0);
    fluid.addComponent("methane", 1.0);
    fluid.addComponent("propane", 1.0);
    fluid.addComponent("n-butane", 1.0);
    fluid.setMixingRule("classic");
    Stream stream = new Stream("tray feed", fluid);
    stream.setFlowRate(100.0, "kg/hr");
    stream.setTemperature(-10.0, "C");
    stream.setPressure(20.0, "bara");
    stream.run();
    return stream;
  }

  /**
   * Default behavior computes full physical properties after a tray run.
   */
  @Test
  public void defaultRunInitializesPhysicalProperties() {
    SimpleTray tray = new SimpleTray("default tray");
    tray.addStream(twoPhaseFeed());
    tray.run(UUID.randomUUID());

    double viscosity = tray.getThermoSystem().getPhase(0).getPhysicalProperties().getViscosity();
    assertTrue(viscosity > 0.0, "default tray run should initialize physical properties (viscosity > 0)");
  }

  /**
   * Skipping physical properties during the run still leaves a valid thermodynamic state, and
   * {@code finalizeTrayProperties()} restores full physical properties.
   */
  @Test
  public void skipThenFinalizeRestoresPhysicalProperties() {
    SimpleTray tray = new SimpleTray("skip tray");
    tray.addStream(twoPhaseFeed());
    tray.setSkipPhysicalPropertiesDuringSolve(true);
    tray.run(UUID.randomUUID());

    // Thermodynamic state is valid after a skipped run.
    assertTrue(Double.isFinite(tray.getThermoSystem().getTemperature()),
        "temperature should be finite after a skipped tray run");

    // Finalize computes the full physical properties on the converged state.
    tray.finalizeTrayProperties();
    double viscosity = tray.getThermoSystem().getPhase(0).getPhysicalProperties().getViscosity();
    assertTrue(viscosity > 0.0, "finalizeTrayProperties() should initialize physical properties (viscosity > 0)");
  }
}
