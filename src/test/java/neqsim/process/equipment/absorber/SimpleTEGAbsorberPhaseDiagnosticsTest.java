package neqsim.process.equipment.absorber;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.StreamSaturatorUtil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;

/**
 * Tests the glycol-phase identity diagnostics of {@link SimpleTEGAbsorber}: at a high gas to glycol ratio with a rich
 * gas the two-phase absorber flash lumps condensed hydrocarbon with the glycol, which must be reported.
 */
class SimpleTEGAbsorberPhaseDiagnosticsTest extends NeqSimTest {
  private static SimpleTEGAbsorber runCase(double gasMSm3PerDay) {
    SystemInterface gas = new SystemSrkCPAstatoil(273.15 + 24.0, 59.3);
    gas.addComponent("nitrogen", 0.008);
    gas.addComponent("CO2", 0.016);
    gas.addComponent("methane", 0.844);
    gas.addComponent("ethane", 0.078);
    gas.addComponent("propane", 0.030);
    gas.addComponent("i-butane", 0.004);
    gas.addComponent("n-butane", 0.008);
    gas.addComponent("i-pentane", 0.002);
    gas.addComponent("n-pentane", 0.003);
    gas.addComponent("n-hexane", 0.0025);
    gas.addComponent("n-octane", 0.0034);
    gas.addComponent("m-Xylene", 0.0019);
    gas.addComponent("water", 0.0);
    gas.addComponent("TEG", 0.0);
    gas.setMixingRule(10);
    gas.setMultiPhaseCheck(false);

    Stream dry = new Stream("dry gas", gas);
    dry.setFlowRate(gasMSm3PerDay, "MSm3/day");
    dry.setTemperature(24.0, "C");
    dry.setPressure(59.3, "bara");
    dry.run();
    StreamSaturatorUtil saturator = new StreamSaturatorUtil("saturator", dry);
    saturator.run();
    Stream wet = new Stream("wet gas", saturator.getOutletStream());
    wet.run();

    SystemInterface lean = gas.clone();
    double[] z = new double[lean.getNumberOfComponents()];
    double nTeg = 0.999 / 150.17;
    double nWater = 0.001 / 18.015;
    z[z.length - 1] = nTeg / (nTeg + nWater);
    z[z.length - 2] = nWater / (nTeg + nWater);
    lean.setMolarComposition(z);
    Stream leanTeg = new Stream("lean TEG", lean);
    leanTeg.setFlowRate(11.7 * 1114.0, "kg/hr");
    leanTeg.setTemperature(29.0, "C");
    leanTeg.setPressure(59.3, "bara");
    leanTeg.run();

    SimpleTEGAbsorber absorber = new SimpleTEGAbsorber("contactor");
    absorber.addGasInStream(wet);
    absorber.addSolventInStream(leanTeg);
    absorber.setNumberOfStages(4);
    absorber.setStageEfficiency(0.65);
    absorber.setInternalDiameter(3.1);
    absorber.run();
    return absorber;
  }

  @Test
  void normalLoadKeepsGlycolRichLiquidAndPositiveAbsorptionFactor() {
    SimpleTEGAbsorber absorber = runCase(14.4); // 600 kSm3/h
    assertTrue(absorber.isEquilibriumLiquidGlycolRich(), "liquid should be glycol-rich at normal load");
    assertTrue(absorber.getEquilibriumLiquidTEGWeightFraction() > 0.5);
    assertTrue(absorber.getAbsorptionFactor() > 1.0, "absorption factor should exceed 1 at normal load");
  }

  @Test
  void highGasToGlycolRatioIsFlaggedAsHydrocarbonDominatedLiquid() {
    SimpleTEGAbsorber absorber = runCase(21.6); // 900 kSm3/h with the same glycol rate
    assertFalse(absorber.isEquilibriumLiquidGlycolRich(),
        "hydrocarbon-dominated liquid must be reported; TEG wt frac = "
            + absorber.getEquilibriumLiquidTEGWeightFraction());
  }
}
