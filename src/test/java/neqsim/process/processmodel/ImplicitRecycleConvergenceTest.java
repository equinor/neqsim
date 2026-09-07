package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.util.Adjuster;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Verifies that implicit loops cannot silently return unconverged thermal or component states. */
class ImplicitRecycleConvergenceTest extends neqsim.NeqSimTest {
  private static class AlternatingUnit extends TwoPortEquipment {
    private static final long serialVersionUID = 1000L;
    private final boolean changeComposition;
    private int runs;

    AlternatingUnit(String name, StreamInterface inlet, boolean changeComposition) {
      super(name, inlet);
      this.changeComposition = changeComposition;
    }

    @Override
    public void run(UUID id) {
      runs++;
      SystemInterface fluid = getInletStream().getFluid().clone();
      if (changeComposition) {
        double methane = runs % 2 == 0 ? 0.7 : 0.9;
        fluid.setMolarComposition(new double[] { methane, 1.0 - methane });
        fluid.setTotalFlowRate(100.0, "mol/sec");
      } else {
        fluid.setTemperature(runs % 2 == 0 ? 300.0 : 310.0);
      }
      fluid.init(2);
      getOutletStream().setThermoSystem(fluid);
      setCalculationIdentifier(id);
    }
  }

  @ParameterizedTest
  @CsvSource({ "false,false", "true,false", "false,true", "true,true" })
  void unconvergedLoopFailsInsteadOfReportingSuccess(boolean changeComposition, boolean addAdjuster) {
    SystemInterface fluid = new SystemSrkEos(300.0, 20.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("ethane", 0.2);
    fluid.setMixingRule("classic");
    Stream seed = new Stream("seed", fluid);
    seed.setFlowRate(100.0, "mol/sec");
    seed.run();
    AlternatingUnit oscillator = new AlternatingUnit("oscillator", seed, changeComposition);
    Stream feedback = new Stream("feedback", oscillator.getOutletStream());
    oscillator.setInletStream(feedback);
    ProcessSystem process = new ProcessSystem();
    process.add(oscillator);
    process.add(feedback);
    if (addAdjuster) {
      // An unrelated signal controller must not hide a real material-stream cycle.
      Adjuster adjuster = new Adjuster("pressure controller");
      adjuster.setAdjustedVariable(seed, "pressure", "bara");
      adjuster.setTargetVariable(seed, "pressure", 20.0, "bara");
      process.add(seed);
      process.add(adjuster);
    }
    assertTrue(process.hasRecycleLoops());
    IllegalStateException error = assertThrows(IllegalStateException.class, process::run);
    assertTrue(error.getMessage().contains("Implicit recycle loop did not converge"));
    assertEquals(100, oscillator.runs);
    assertFalse(process.getRunStatus().isSuccess());
  }
}
