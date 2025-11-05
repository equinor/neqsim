import neqsim.process.equipment.diffpressure.Orifice;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Quick test to verify Orifice limits flow based on discharge coefficient
 */
public class test_orifice_flow_limit {
  public static void main(String[] args) {
    System.out.println("=== Testing Orifice Flow Limiting ===\n");

    // Create high pressure gas stream
    SystemInterface gas = new SystemSrkEos(298.15, 50.0);
    gas.addComponent("methane", 85.0);
    gas.addComponent("ethane", 10.0);
    gas.addComponent("propane", 5.0);
    gas.setMixingRule(2);

    Stream inlet = new Stream("Inlet", gas);
    inlet.setFlowRate(10000.0, "kg/hr");
    inlet.setPressure(50.0, "bara");
    inlet.setTemperature(25.0, "C");
    inlet.run();

    System.out.println("Inlet Conditions:");
    System.out.printf("  Pressure: %.2f bara%n", inlet.getPressure("bara"));
    System.out.printf("  Flow rate: %.1f kg/hr%n", inlet.getFlowRate("kg/hr"));
    System.out.printf("  Temperature: %.2f C%n", inlet.getTemperature("C"));
    System.out.printf("  Density: %.2f kg/m3%n", inlet.getFluid().getDensity("kg/m3"));

    // Create orifice with restrictive sizing
    // D = 0.4 m (400 mm pipe), d = 0.15 m (150 mm orifice)
    // Cd = 0.61 (typical for sharp-edged orifice)
    // Upstream = 50 bara, Downstream = 1.5 bara
    Orifice orifice = new Orifice("Test Orifice", 0.4, 0.15, 50.0, 1.5, 0.61);
    orifice.setInletStream(inlet);

    // Set outlet pressure (simulating flare header at 1.5 bara)
    orifice.getOutletStream().setPressure(1.5, "bara");

    System.out.println("\nOrifice Configuration:");
    System.out.println("  Pipe diameter (D): 0.4 m");
    System.out.println("  Orifice diameter (d): 0.15 m");
    System.out.printf("  Beta ratio (d/D): %.3f%n", 0.15 / 0.4);
    System.out.println("  Discharge coefficient (Cd): 0.61");
    System.out.println("  Design upstream pressure: 50.0 bara");
    System.out.println("  Design downstream pressure: 1.5 bara");
    System.out.printf("  Design pressure drop: %.1f bar%n", 50.0 - 1.5);

    // Run orifice calculation
    orifice.run();

    Stream outlet = new Stream("Outlet", orifice.getOutletStream());
    outlet.run();

    System.out.println("\nOutlet Conditions (After Orifice):");
    System.out.printf("  Pressure: %.2f bara%n", outlet.getPressure("bara"));
    System.out.printf("  Flow rate: %.1f kg/hr%n", outlet.getFlowRate("kg/hr"));
    System.out.printf("  Temperature: %.2f C%n", outlet.getTemperature("C"));

    // Calculate theoretical maximum flow using ISO 5167
    double rho = inlet.getFluid().getDensity("kg/m3");
    double mu = inlet.getFluid().getViscosity("Pa*s");
    double k = inlet.getFluid().getGamma();
    double P1 = 50.0 * 1e5; // Pa
    double P2 = 1.5 * 1e5; // Pa

    double maxFlow = Orifice.calculateMassFlowRate(0.4, 0.15, P1, P2, rho, mu, k, "flange");

    System.out.println("\nFlow Limiting Analysis:");
    System.out.printf("  Inlet flow rate: %.1f kg/hr (%.3f kg/s)%n", inlet.getFlowRate("kg/hr"),
        inlet.getFlowRate("kg/sec"));
    System.out.printf("  Maximum orifice capacity (ISO 5167): %.3f kg/s (%.1f kg/hr)%n", maxFlow,
        maxFlow * 3600.0);
    System.out.printf("  Outlet flow rate: %.1f kg/hr (%.3f kg/s)%n", outlet.getFlowRate("kg/hr"),
        outlet.getFlowRate("kg/sec"));

    double flowReduction =
        (1.0 - outlet.getFlowRate("kg/sec") / inlet.getFlowRate("kg/sec")) * 100.0;
    if (flowReduction > 1.0) {
      System.out.printf("  ✓ Flow LIMITED by orifice: %.1f%% reduction%n", flowReduction);
    } else {
      System.out.printf("  ✗ Flow NOT limited (reduction: %.1f%%)%n", flowReduction);
    }

    System.out.printf("  Pressure drop: %.2f bar%n",
        inlet.getPressure("bara") - outlet.getPressure("bara"));

    System.out.println("\n=== Test Complete ===");
  }
}
