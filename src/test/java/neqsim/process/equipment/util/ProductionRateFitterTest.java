package neqsim.process.equipment.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Tests for {@link ProductionRateFitter}. */
public class ProductionRateFitterTest {
  /**
   * The fitter should reproduce a target gas-phase rate, GOR, and produced-water rate.
   */
  @Test
  void testMatchesGasGorAndWater() {
    SystemInterface fluid = new SystemSrkEos(338.15, 90.0);
    fluid.addComponent("nitrogen", 0.5);
    fluid.addComponent("CO2", 1.8);
    fluid.addComponent("methane", 80.0);
    fluid.addComponent("ethane", 7.0);
    fluid.addComponent("propane", 4.0);
    fluid.addComponent("i-butane", 0.8);
    fluid.addComponent("n-butane", 1.6);
    fluid.addComponent("n-pentane", 1.0);
    fluid.addComponent("nC10", 3.3);
    fluid.addComponent("water", 1.0);
    fluid.setMixingRule(2);
    fluid.setMultiPhaseCheck(true);
    fluid.setTemperature(75.0, "C");
    fluid.setPressure(90.0, "bara");
    fluid.setTotalFlowRate(1.0e6, "kg/hr");

    Stream feed = new Stream("feed", fluid);

    double targetGasMSm3d = 3.32;
    double targetGor = 3085.0;
    double targetWaterSm3d = 234.0;

    ProductionRateFitter fitter = new ProductionRateFitter("fitter", feed);
    fitter.setReferenceConditions("standard");
    fitter.setGOR(targetGor);
    fitter.setGasRate(targetGasMSm3d, "MSm3/day");
    fitter.setWaterRate(targetWaterSm3d, "Sm3/day");

    Stream out = new Stream("out", fitter.getOutletStream());

    ProcessSystem ops = new ProcessSystem();
    ops.add(feed);
    ops.add(fitter);
    ops.add(out);
    ops.run();

    // Produced (liquid) water is read at operating conditions, where the aqueous
    // phase holds essentially all the added water.
    SystemInterface opFluid = fitter.getOutletStream().getFluid();
    double waterM3d = opFluid.hasPhaseType("aqueous") ? opFluid.getPhase("aqueous").getFlowRate("m3/hr") * 24.0 : 0.0;

    // Flash the fitted stream at standard conditions and read the phase rates.
    out.setTemperature(15.0, "C");
    out.setPressure(1.01325, "bara");
    out.run();
    SystemInterface res = out.getFluid();

    Assertions.assertTrue(res.hasPhaseType("gas"), "expected a gas phase");
    Assertions.assertTrue(res.hasPhaseType("oil"), "expected an oil phase");

    double gasMSm3d = res.getPhase("gas").getFlowRate("MSm3/day");
    // GOR = standard gas volume / stock-tank (liquid) oil volume.
    double gor = res.getPhase("gas").getFlowRate("Sm3/hr") / res.getPhase("oil").getFlowRate("m3/hr");

    // Gas-phase standard rate within 3% of target.
    Assertions.assertEquals(targetGasMSm3d, gasMSm3d, targetGasMSm3d * 0.03,
        "gas-phase standard rate should match target");
    // GOR within 5% of target.
    Assertions.assertEquals(targetGor, gor, targetGor * 0.05, "GOR should match target");

    // Produced-water rate (liquid volume at operating conditions) within 5% of target.
    Assertions.assertEquals(targetWaterSm3d, waterM3d, targetWaterSm3d * 0.05,
        "produced-water rate should match target");
  }
}
