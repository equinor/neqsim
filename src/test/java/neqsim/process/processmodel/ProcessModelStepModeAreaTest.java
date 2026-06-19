package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.Recycle;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link ProcessSystem#setSolveFullyInModelStep(boolean)}: when a {@link ProcessModel} is run in step mode,
 * individual areas can be flagged to converge fully (recycles included) on each model step while other areas advance
 * only a single pass.
 *
 * @author ESOL
 * @version 1.0
 */
class ProcessModelStepModeAreaTest {

  /**
   * Builds a slowly converging recycle loop inside its own {@link ProcessSystem}: feed -&gt; mixer -&gt; heater -&gt;
   * splitter, where 50% of the splitter outlet is product and 50% is recycled back to the mixer. The high recycle
   * fraction makes convergence take many passes, so a single forced step-mode pass is clearly not yet converged.
   *
   * @param areaName   name for the process area
   * @param solveFully whether the area should fully converge on each model step
   * @return the assembled process area
   */
  private ProcessSystem buildRecycleArea(String areaName, boolean solveFully) {
    SystemInterface fluid = new SystemSrkEos(298.0, 50.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("ethane", 0.15);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");

    Stream feed = new Stream(areaName + " feed", fluid);
    feed.setFlowRate(100.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(50.0, "bara");

    Mixer mixer = new Mixer(areaName + " mixer");
    mixer.addStream(feed);

    Heater heater = new Heater(areaName + " heater", mixer.getOutletStream());
    heater.setOutTemperature(30.0, "C");

    Splitter splitter = new Splitter(areaName + " splitter", heater.getOutletStream(), 2);
    splitter.setSplitFactors(new double[] { 0.5, 0.5 });

    Stream product = new Stream(areaName + " product", splitter.getSplitStream(0));

    Recycle recycle = new Recycle(areaName + " recycle");
    recycle.addStream(splitter.getSplitStream(1));
    recycle.setOutletStream(new Stream(areaName + " recycleOut", fluid.clone()));
    mixer.addStream(recycle.getOutletStream());

    ProcessSystem process = new ProcessSystem(areaName);
    process.add(feed);
    process.add(mixer);
    process.add(heater);
    process.add(splitter);
    process.add(product);
    process.add(recycle);
    process.setSolveFullyInModelStep(solveFully);
    return process;
  }

  /**
   * When the model runs in step mode, the area flagged with solveFullyInModelStep converges its recycle, while the
   * unflagged area does not.
   */
  @Test
  void testFlaggedAreaConvergesWhileOthersStep() {
    ProcessModel model = new ProcessModel();
    model.add("converged area", buildRecycleArea("Converged", true));
    model.add("stepped area", buildRecycleArea("Stepped", false));

    model.setRunStep(true);
    model.run();

    ProcessSystem convergedArea = model.get("converged area");
    ProcessSystem steppedArea = model.get("stepped area");

    Recycle convergedRecycle = (Recycle) convergedArea.getUnit("Converged recycle");
    Recycle steppedRecycle = (Recycle) steppedArea.getUnit("Stepped recycle");

    assertTrue(convergedArea.isSolveFullyInModelStep());
    assertFalse(steppedArea.isSolveFullyInModelStep());

    assertTrue(convergedRecycle.solved(), "Flagged area should fully converge its recycle on a single model step");
    assertFalse(steppedRecycle.solved(), "Unflagged area should only single-step and not converge its recycle");
  }

  /**
   * The solveFullyInModelStep flag survives a ProcessSystem copy.
   */
  @Test
  void testFlagSurvivesCopy() {
    ProcessSystem area = buildRecycleArea("Area", true);
    ProcessSystem copy = area.copy();
    assertTrue(copy.isSolveFullyInModelStep());
  }
}
