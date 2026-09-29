package neqsim.process.equipment.flare;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/** Test of the Flare unit operation. */
public class FlareTest {
  ProcessSystem processOps;
  Flare flare;

  @BeforeEach
  public void setUp() {
    SystemSrkEos testSystem = new SystemSrkEos(298.15, 1.0);
    testSystem.addComponent("methane", 1.0);
    Stream gasStream = new Stream("gas stream", testSystem);
    gasStream.setFlowRate(1.0, "MSm3/day");

    processOps = new ProcessSystem();
    flare = new Flare("flare", gasStream);
    processOps.add(gasStream);
    processOps.add(flare);
    processOps.run();
  }

  /** Verify molar heating values, mixture composition and linear flow scaling without a volume conversion. */
  @Test
  public void molarHeatReleaseIsIndependentOfMeteringVolume() {
    for (double methaneFraction : new double[] {1.0, 0.8}) {
      SystemSrkEos fluid = new SystemSrkEos(298.15, 2.0);
      fluid.addComponent("methane", methaneFraction);
      fluid.addComponent("ethane", 1.0 - methaneFraction);
      fluid.setMixingRule("classic");
      Stream fuel = new Stream("fuel", fluid);
      // ISO 6976:1995 60 F combustion table, kJ/mol converted to J/mol.
      double molarLCV = 1000.0 * (methaneFraction * 802.69 + (1.0 - methaneFraction) * 1428.83);
      for (double flow : new double[] {1.0, 3.0}) {
        fuel.setFlowRate(flow, "mole/sec");
        fuel.run();
        Flare burner = new Flare("burner", fuel);
        burner.run();
        assertEquals(molarLCV * flow, burner.getHeatDuty(), 1.0e-5);
        assertEquals(fuel.LCV(), burner.getLCV(), 1.0e-6);
        burner.updateCumulative(2.0);
        assertEquals(molarLCV * flow * 2.0e-9, burner.getCumulativeHeatReleased("GJ"), 1.0e-12);
      }
    }
  }

  @Test
  public void testFlareCalculations() {
    assertTrue(flare.getHeatDuty() > 0.0);
    assertTrue(flare.getCO2Emission() > 0.0);
  }
}
