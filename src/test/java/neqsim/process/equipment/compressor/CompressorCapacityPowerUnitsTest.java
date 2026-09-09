package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/** Verifies the common watt basis used by legacy compressor capacity ratios. */
class CompressorCapacityPowerUnitsTest extends NeqSimTest {
  @Test
  void mechanicalDesignRatingAndShaftDutyUseTheSamePowerUnit() {
    SystemSrkEos fluid = new SystemSrkEos(303.15, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(50000.0, "kg/hr");
    Compressor compressor = new Compressor("compressor", feed);
    compressor.setOutletPressure(100.0, "bara");
    compressor.setIsentropicEfficiency(0.78);
    compressor.getMechanicalDesign().setMaxDesignPower(4000.0);
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(compressor);
    process.run();

    assertEquals(4000000.0, compressor.getCapacityMax(), 1.0e-6);
    assertEquals(compressor.getPower("kW") / 4000.0, compressor.getCapacityDuty() / compressor.getCapacityMax(),
        1.0e-12);
    assertTrue(compressor.getCapacityDuty() > 0.0);
    assertTrue(compressor.getCapacityDuty() < compressor.getCapacityMax());

    compressor.getMechanicalDesign().setMaxDesignPower(2000.0);
    assertEquals(2000000.0, compressor.getCapacityMax(), 1.0e-6,
        "Changing an installed kW rating must update the capacity ceiling in watts");
  }
}
