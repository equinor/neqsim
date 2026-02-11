package neqsim.process.equipment.compressor;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;

/**
 * Tests for compressor with CPA equation of state and water wash scenarios.
 *
 * <p>
 * These tests verify that compressor calculations complete without hanging when the inlet fluid uses
 * CPA EOS (autoSelectModel with water/MEG) and has two phases (gas + aqueous). Previously, the
 * PSflash Newton iteration would oscillate at 1e-6 error due to expensive stability analysis noise,
 * never reaching the 1e-8 convergence criterion.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class CompressorCPAHangTest extends neqsim.NeqSimTest {

  /**
   * Test isentropic compressor with CPA water wash and plus fractions.
   *
   * <p>
   * Simulates gas compression from 27.9 to 122 bara with a mixed gas+water feed produced by
   * scrubbing and water wash injection. The fluid uses CPA EOS via autoSelectModel due to
   * water/MEG components.
   * </p>
   */
  @Test
  @Timeout(120)
  public void testCompressorWithWaterWashCPAPlusFractions() {
    double compPin = 27.9;
    double compTin = 27.0;
    double compPout = 122.0;
    double compFlow = 149520.0;

    SystemInterface gasFluid = new SystemPrEos(273.15 + compTin, compPin);
    gasFluid.addComponent("water", 0.0);
    gasFluid.addComponent("MEG", 0.0);
    gasFluid.addComponent("nitrogen", 2.53);
    gasFluid.addComponent("CO2", 0.51);
    gasFluid.addComponent("methane", 68.54);
    gasFluid.addComponent("ethane", 12.81);
    gasFluid.addComponent("propane", 10.27);
    gasFluid.addComponent("i-butane", 1.02);
    gasFluid.addComponent("n-butane", 2.75);
    gasFluid.addComponent("i-pentane", 0.44);
    gasFluid.addComponent("n-pentane", 0.53);
    gasFluid.addComponent("n-hexane", 0.61);
    gasFluid.addTBPfraction("C7", 0.0, 0.0891, 0.7537);
    gasFluid.addTBPfraction("C8", 0.0, 0.1020, 0.7629);
    gasFluid.addTBPfraction("C9", 0.0, 0.1180, 0.7829);
    gasFluid.addTBPfraction("C10", 0.0, 0.134, 0.804);
    gasFluid.setMixingRule(2);
    gasFluid.setMultiPhaseCheck(true);
    gasFluid = gasFluid.autoSelectModel();

    SystemInterface washFluid = new SystemPrEos(273.15 + compTin, compPin);
    washFluid.addComponent("water", 0.0);
    washFluid.addComponent("MEG", 0.0);
    washFluid.addComponent("nitrogen", 2.53);
    washFluid.addComponent("CO2", 0.51);
    washFluid.addComponent("methane", 68.54);
    washFluid.addComponent("ethane", 12.81);
    washFluid.addComponent("propane", 10.27);
    washFluid.addComponent("i-butane", 1.02);
    washFluid.addComponent("n-butane", 2.75);
    washFluid.addComponent("i-pentane", 0.44);
    washFluid.addComponent("n-pentane", 0.53);
    washFluid.addComponent("n-hexane", 0.61);
    washFluid.addTBPfraction("C7", 0.0, 0.0891, 0.7537);
    washFluid.addTBPfraction("C8", 0.0, 0.1020, 0.7629);
    washFluid.addTBPfraction("C9", 0.0, 0.1180, 0.7829);
    washFluid.addTBPfraction("C10", 0.0, 0.134, 0.804);
    washFluid.setMixingRule(2);
    washFluid = washFluid.autoSelectModel();
    double[] waterComp = new double[washFluid.getNumberOfComponents()];
    waterComp[0] = 1.0;
    washFluid.setMolarComposition(waterComp);
    washFluid.setMultiPhaseCheck(true);

    Stream gasStream = new Stream("gasStream", gasFluid);
    gasStream.setFlowRate(compFlow, "kg/hr");
    gasStream.setTemperature(compTin, "C");
    gasStream.setPressure(compPin, "bara");
    gasStream.run();

    Stream waterStream = new Stream("waterStream", washFluid);
    waterStream.setFlowRate(5000.0, "kg/hr");
    waterStream.setTemperature(compTin, "C");
    waterStream.setPressure(compPin, "bara");
    waterStream.run();

    Separator scrubber = new Separator("scrubber", gasStream);
    scrubber.run();
    Stream gasFromScrubber = new Stream("gasFromScrubber", scrubber.getGasOutStream());
    gasFromScrubber.run();

    Mixer mixer = new Mixer("mixer");
    mixer.addStream(gasFromScrubber);
    mixer.addStream(waterStream);
    mixer.run();

    Stream feedToCompressor = new Stream("feedToCompressor", mixer.getOutletStream());
    feedToCompressor.run();

    Assertions.assertEquals(2, feedToCompressor.getThermoSystem().getNumberOfPhases(),
        "Feed should have 2 phases (gas + aqueous)");

    Compressor compressor1 = new Compressor("27AKA60", feedToCompressor);
    compressor1.setOutletPressure(compPout, "bara");
    compressor1.run();

    Assertions.assertTrue(compressor1.getOutletStream().getTemperature("C") > compTin,
        "Outlet temperature should exceed inlet temperature");
    Assertions.assertEquals(compPout, compressor1.getOutletStream().getPressure("bara"), 0.1,
        "Outlet pressure should match setpoint");
  }

  /**
   * Test polytropic detailed compressor with CPA gas (no water wash).
   *
   * <p>
   * Verifies that polytropic detailed calculation with specified outlet temperature completes
   * without hanging when using CPA EOS.
   * </p>
   */
  @Test
  @Timeout(120)
  public void testCompressorWithPolytropicDetailedCPA() {
    double compPin = 27.9;
    double compTin = 27.0;
    double compPout = 122.0;
    double compTout = 167.0;
    double compFlow = 149520.0;

    SystemInterface gasFluid = new SystemPrEos(273.15 + compTin, compPin);
    gasFluid.addComponent("water", 0.0);
    gasFluid.addComponent("MEG", 0.0);
    gasFluid.addComponent("nitrogen", 2.53);
    gasFluid.addComponent("CO2", 0.51);
    gasFluid.addComponent("methane", 68.54);
    gasFluid.addComponent("ethane", 12.81);
    gasFluid.addComponent("propane", 10.27);
    gasFluid.addComponent("i-butane", 1.02);
    gasFluid.addComponent("n-butane", 2.75);
    gasFluid.addComponent("i-pentane", 0.44);
    gasFluid.addComponent("n-pentane", 0.53);
    gasFluid.addComponent("n-hexane", 0.61);
    gasFluid.addTBPfraction("C7", 0.0, 0.0891, 0.7537);
    gasFluid.addTBPfraction("C8", 0.0, 0.1020, 0.7629);
    gasFluid.addTBPfraction("C9", 0.0, 0.1180, 0.7829);
    gasFluid.addTBPfraction("C10", 0.0, 0.134, 0.804);
    gasFluid.setMixingRule(2);
    gasFluid.setMultiPhaseCheck(true);
    gasFluid = gasFluid.autoSelectModel();

    Stream gasStream = new Stream("gasStream", gasFluid);
    gasStream.setFlowRate(compFlow, "kg/hr");
    gasStream.setTemperature(compTin, "C");
    gasStream.setPressure(compPin, "bara");
    gasStream.run();

    Compressor compressor1 = new Compressor("27AKA60", gasStream);
    compressor1.setOutletPressure(compPout, "bara");
    compressor1.setOutTemperature(273.15 + compTout);
    compressor1.setUsePolytropicCalc(true);
    compressor1.setPolytropicMethod("detailed");
    compressor1.setNumberOfCompressorCalcSteps(10);
    compressor1.run();

    Assertions.assertEquals(compTout, compressor1.getOutletStream().getTemperature("C"), 1.0,
        "Outlet temperature should match specified value");
    Assertions.assertTrue(compressor1.getPolytropicEfficiency() > 0.5,
        "Polytropic efficiency should be reasonable");
  }
}
