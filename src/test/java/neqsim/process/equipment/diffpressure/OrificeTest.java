package neqsim.process.equipment.diffpressure;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class OrificeTest {
  @Test
  void testCalc_dp() {
    // Step 1: Create a fluid with the SRK equation of state
    SystemInterface fluid1 = new SystemSrkEos(303.15, 10.0); // 30 C and 10 bara
    fluid1.addComponent("methane", 1.0, "kg/sec");
    fluid1.setMixingRule(2);

    // Step 2: Create a stream with the fluid
    Stream stream1 = new Stream("stream 1", fluid1);
    stream1.setFlowRate(3000.0, "kg/hr");

    // Step 3: Define and set up the orifice unit operation
    Orifice orif1 = new Orifice("orifice 1");
    orif1.setInletStream(stream1);
    orif1.setOrificeParameters(0.07366, 0.05, 0.61); // Diameter, outer diameter, and discharge
                                                     // coefficient

    // Step 4: Define the output stream after the orifice
    Stream stream2 = new Stream("stream 2", orif1.getOutletStream());

    // Step 5: Set up and run the process system
    ProcessSystem oilProcess = new ProcessSystem();
    oilProcess.add(stream1);
    oilProcess.add(orif1);
    oilProcess.add(stream2);

    oilProcess.run();

    // Output the pressure after the orifice
    System.out.println("Pressure out of orifice: " + stream2.getPressure("bara") + " bara");
  }

  @Test
  void testOrificeCorrelation() {
    double C = Orifice.calculateDischargeCoefficient(0.07391, 0.0222, 1.165, 1.85E-5, 0.12,
        "flange");
    Assertions.assertEquals(0.5990326277, C, 1e-6);

    double eps = Orifice.calculateExpansibility(0.0739, 0.0222, 1.0E5, 9.9E4, 1.4);
    Assertions.assertEquals(0.9974739057, eps, 1e-9);

    double m = Orifice.calculateMassFlowRate(0.07366, 0.05, 200000.0, 183000.0, 999.1, 0.0011,
        1.33, "D");
    Assertions.assertEquals(7.702338, m, 1e-6);
  }
}
