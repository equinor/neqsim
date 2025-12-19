package neqsim.process.util.example;

import org.apache.commons.lang3.StringUtils;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills.HeatTransferMode;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Example demonstrating transient heat transfer in PipeBeggsAndBrills.
 *
 * <p>
 * This example shows how the outlet temperature responds to varying inlet temperature over time.
 * The inlet temperature first increases (simulating a well heating up), then decreases (simulating
 * cooling). This tests the NTU-effectiveness heat transfer calculation in runTransient().
 * </p>
 */
public class TransientPipeHeatTransferExample {

  /**
   * Main method to run the transient heat transfer example.
   *
   * @param args command line arguments (not used)
   */
  public static void main(String[] args) {
    System.out.println("╔══════════════════════════════════════════════════════════════════════╗");
    System.out.println("║  TRANSIENT PIPE HEAT TRANSFER EXAMPLE                                ║");
    System.out.println("║  Inlet temperature varies: UP then DOWN                              ║");
    System.out.println("╚══════════════════════════════════════════════════════════════════════╝");
    System.out.println();

    // ========================================
    // CONFIGURATION
    // ========================================
    double initialInletTemp = 60.0; // °C - Starting inlet temperature
    double maxInletTemp = 90.0; // °C - Peak inlet temperature
    double minInletTemp = 40.0; // °C - Minimum inlet temperature
    double seaTemperature = 15.0; // °C - Constant sea temperature
    double inletPressure = 80.0; // bara
    double flowRate = 50000.0; // kg/hr
    double pipeLength = 5000.0; // m (5 km) - shorter for faster response
    double pipeDiameter = 0.1524; // m (6 inch)
    double heatTransferCoeff = 10.0; // W/(m²·K) - reasonable for subsea

    double dt = 10.0; // seconds - time step
    int totalSteps = 200; // Total simulation steps
    int rampUpSteps = 60; // Steps to ramp up temperature
    int holdHighSteps = 40; // Steps to hold at max temperature
    int rampDownSteps = 60; // Steps to ramp down temperature
    // Remaining steps hold at minimum

    // ========================================
    // CREATE FLUID
    // ========================================
    System.out.println("=== Setting Up Simulation ===");
    SystemInterface fluid = new SystemSrkEos(273.15 + initialInletTemp, inletPressure);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.04);
    fluid.addComponent("n-butane", 0.02);
    fluid.addComponent("n-pentane", 0.01);
    fluid.setMixingRule("classic");

    // ========================================
    // CREATE PROCESS
    // ========================================
    Stream inlet = new Stream("inlet", fluid);
    inlet.setFlowRate(flowRate, "kg/hr");
    inlet.setTemperature(initialInletTemp, "C");
    inlet.setPressure(inletPressure, "bara");

    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("subsea pipe", inlet);
    pipe.setLength(pipeLength);
    pipe.setDiameter(pipeDiameter);
    pipe.setElevation(0.0); // Horizontal
    pipe.setNumberOfIncrements(10);
    pipe.setConstantSurfaceTemperature(seaTemperature, "C");
    pipe.setHeatTransferCoefficient(heatTransferCoeff);
    pipe.setHeatTransferMode(HeatTransferMode.SPECIFIED_U);

    ProcessSystem process = new ProcessSystem();
    process.add(inlet);
    process.add(pipe);

    // Initial steady-state run
    process.run();
    System.out.printf("Pipe: %.0f m long, %.3f m diameter%n", pipeLength, pipeDiameter);
    System.out.printf("Sea temperature: %.1f °C%n", seaTemperature);
    System.out.printf("Heat transfer coefficient: %.1f W/(m²·K)%n", heatTransferCoeff);
    System.out.printf("Flow rate: %.0f kg/hr%n", flowRate);
    System.out.printf("Time step: %.1f seconds%n", dt);
    System.out.println();

    // ========================================
    // RUN TRANSIENT SIMULATION
    // ========================================
    System.out.println("=== Transient Simulation Results ===");
    System.out.println("Time(s)    Inlet T(°C)  Outlet T(°C)  ΔT(°C)    Phase");
    System.out.println(StringUtils.repeat("─", 70));

    // Disable steady-state calculation for transient mode
    pipe.setCalculateSteadyState(false);

    double currentTime = 0.0;
    double currentInletTemp = initialInletTemp;

    for (int step = 0; step <= totalSteps; step++) {
      // Determine current phase and inlet temperature
      String phase;
      if (step < rampUpSteps) {
        // Ramp UP
        double progress = (double) step / rampUpSteps;
        currentInletTemp = initialInletTemp + progress * (maxInletTemp - initialInletTemp);
        phase = "RAMP UP  ";
      } else if (step < rampUpSteps + holdHighSteps) {
        // Hold HIGH
        currentInletTemp = maxInletTemp;
        phase = "HOLD HIGH";
      } else if (step < rampUpSteps + holdHighSteps + rampDownSteps) {
        // Ramp DOWN
        double progress = (double) (step - rampUpSteps - holdHighSteps) / rampDownSteps;
        currentInletTemp = maxInletTemp - progress * (maxInletTemp - minInletTemp);
        phase = "RAMP DOWN";
      } else {
        // Hold LOW
        currentInletTemp = minInletTemp;
        phase = "HOLD LOW ";
      }

      // Update inlet temperature
      inlet.getThermoSystem().setTemperature(273.15 + currentInletTemp);
      inlet.run();

      // Run transient step
      process.runTransient(dt);

      double outletTemp = pipe.getOutStream().getTemperature("C");
      double deltaT = currentInletTemp - outletTemp;

      // Print every 10 steps or at key transitions
      if (step % 10 == 0 || step == rampUpSteps || step == rampUpSteps + holdHighSteps
          || step == rampUpSteps + holdHighSteps + rampDownSteps) {
        System.out.printf("%7.0f    %8.2f     %8.2f      %6.2f    %s%n", currentTime,
            currentInletTemp, outletTemp, deltaT, phase);
      }

      currentTime += dt;
    }

    System.out.println(StringUtils.repeat("─", 70));
    System.out.println();

    // ========================================
    // ANALYSIS
    // ========================================
    System.out.println("=== Analysis ===");

    // Calculate expected steady-state outlet temperature using NTU method
    double massFlowKgS = flowRate / 3600.0;
    double cp = fluid.getCp("J/kgK");
    double area = Math.PI * pipeDiameter * pipeLength;
    double NTU = heatTransferCoeff * area / (massFlowKgS * cp);
    double effectiveness = 1 - Math.exp(-NTU);

    System.out.printf("Heat transfer area: %.1f m²%n", area);
    System.out.printf("NTU (Number of Transfer Units): %.3f%n", NTU);
    System.out.printf("Effectiveness (1-e^-NTU): %.3f%n", effectiveness);
    System.out.println();

    // Expected outlet temperatures at different inlet temps
    double[] testInletTemps = {40.0, 60.0, 90.0};
    System.out.println("Expected steady-state outlet temperatures:");
    System.out.println("Inlet T    Expected Outlet T    Cooling (ΔT)");
    for (double Tin : testInletTemps) {
      double expectedTout = seaTemperature + (Tin - seaTemperature) * Math.exp(-NTU);
      double cooling = Tin - expectedTout;
      System.out.printf("%.1f°C     %.2f°C              %.2f°C%n", Tin, expectedTout, cooling);
    }
    System.out.println();

    System.out.println("╔══════════════════════════════════════════════════════════════════════╗");
    System.out.println("║  Example completed successfully!                                     ║");
    System.out.println("╚══════════════════════════════════════════════════════════════════════╝");
  }
}
