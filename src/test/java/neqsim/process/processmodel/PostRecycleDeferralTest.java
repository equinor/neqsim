package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Verifies that units downstream of all recycle loops are solved once after recycle convergence and that this does not
 * change the converged flowsheet.
 */
public class PostRecycleDeferralTest {
  private static final class Fixture {
    ProcessSystem process = new ProcessSystem("recompression");
    Stream feed;
    Compressor exportCompressor;
    Cooler exportCooler;
    Separator inletSeparator;
  }

  private static Fixture build(boolean defer) {
    SystemInterface fluid = new SystemSrkEos(298.15, 30.0);
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.08);
    fluid.addComponent("n-butane", 0.05);
    fluid.addComponent("n-hexane", 0.04);
    fluid.addComponent("n-octane", 0.03);
    fluid.setMixingRule("classic");

    Fixture fixture = new Fixture();
    ProcessSystem process = fixture.process;
    process.setDeferPostRecycleUnits(defer);

    fixture.feed = new Stream("feed", fluid);
    fixture.feed.setFlowRate(20000.0, "kg/hr");
    process.add(fixture.feed);

    Stream recycleGuess = fixture.feed.clone();
    recycleGuess.setName("recycle liquid");
    recycleGuess.setFlowRate(10.0, "kg/hr");
    process.add(recycleGuess);

    Mixer inletMixer = new Mixer("inlet mixer");
    inletMixer.addStream(fixture.feed);
    inletMixer.addStream(recycleGuess);
    process.add(inletMixer);

    fixture.inletSeparator = new Separator("inlet separator", inletMixer.getOutletStream());
    process.add(fixture.inletSeparator);

    Compressor compressor = new Compressor("booster", fixture.inletSeparator.getGasOutStream());
    compressor.setOutletPressure(70.0, "bara");
    process.add(compressor);

    Cooler cooler = new Cooler("after cooler", compressor.getOutletStream());
    cooler.setOutTemperature(298.15, "K");
    process.add(cooler);

    Separator scrubber = new Separator("scrubber", cooler.getOutletStream());
    process.add(scrubber);

    ThrottlingValve liquidValve = new ThrottlingValve("liquid valve", scrubber.getLiquidOutStream());
    liquidValve.setOutletPressure(30.0, "bara");
    process.add(liquidValve);

    Recycle recycle = new Recycle("liquid recycle");
    recycle.addStream(liquidValve.getOutletStream());
    recycle.setOutletStream(recycleGuess);
    recycle.setTolerance(1.0e-4);
    process.add(recycle);

    // Export train: downstream of the recycle loop.
    fixture.exportCompressor = new Compressor("export compressor", scrubber.getGasOutStream());
    fixture.exportCompressor.setOutletPressure(150.0, "bara");
    process.add(fixture.exportCompressor);

    fixture.exportCooler = new Cooler("export cooler", fixture.exportCompressor.getOutletStream());
    fixture.exportCooler.setOutTemperature(303.15, "K");
    process.add(fixture.exportCooler);
    return fixture;
  }

  @Test
  public void deferredExportTrainMatchesFullIteration() {
    Fixture deferred = build(true);
    Fixture iterated = build(false);
    deferred.process.run();
    iterated.process.run();
    assertTrue(deferred.process.isDeferPostRecycleUnits());
    assertTrue(deferred.process.solved());

    assertEquals(iterated.exportCompressor.getPower(), deferred.exportCompressor.getPower(),
        1.0e-6 * Math.abs(iterated.exportCompressor.getPower()));
    assertEquals(iterated.exportCooler.getOutletStream().getFlowRate("kg/hr"),
        deferred.exportCooler.getOutletStream().getFlowRate("kg/hr"),
        1.0e-6 * iterated.exportCooler.getOutletStream().getFlowRate("kg/hr"));
    assertEquals(iterated.exportCompressor.getOutletStream().getTemperature(),
        deferred.exportCompressor.getOutletStream().getTemperature(), 1.0e-4);
    assertEquals(iterated.inletSeparator.getLiquidOutStream().getFlowRate("kg/hr"),
        deferred.inletSeparator.getLiquidOutStream().getFlowRate("kg/hr"),
        1.0e-6 * iterated.inletSeparator.getLiquidOutStream().getFlowRate("kg/hr"));
  }

  @Test
  public void exportTrainIsSolvedOncePerRun() {
    Fixture deferred = build(true);
    deferred.process.setProfilingEnabled(true);
    deferred.process.run();
    Map<String, double[]> profile = deferred.process.getExecutionProfile();
    assertEquals(1.0, profile.get("export compressor")[1], 1.0e-12);
    assertEquals(1.0, profile.get("export cooler")[1], 1.0e-12);
    assertTrue(profile.get("booster")[1] > 1.0, "units inside the recycle loop are still iterated");

    // A changed feed must propagate to the deferred units on the next run.
    double powerBefore = deferred.exportCompressor.getPower();
    deferred.feed.setFlowRate(24000.0, "kg/hr");
    deferred.process.run();
    assertEquals(1.2, deferred.exportCompressor.getPower() / powerBefore, 0.02);
  }
}
