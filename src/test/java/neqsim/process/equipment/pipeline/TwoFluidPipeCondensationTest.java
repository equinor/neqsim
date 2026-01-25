package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Test for TwoFluidPipe condensation behavior.
 * 
 * <p>
 * This test verifies that as temperature drops along a pipeline (due to heat transfer to cold
 * seawater), the liquid holdup should INCREASE due to condensation of heavier hydrocarbons and
 * water from the gas phase.
 * </p>
 * 
 * <p>
 * The test compares TwoFluidPipe against PipeBeggsAndBrills to ensure both models show the expected
 * condensation-driven holdup increase along the pipeline length.
 * </p>
 * 
 * @author ESOL
 * @version 1.0
 */
public class TwoFluidPipeCondensationTest {

  /**
   * Create wet natural gas feed fluid.
   * 
   * @return Configured fluid system
   */
  private SystemInterface createFeedFluid() {
    SystemInterface fluid = new SystemPrEos(298.15, 10.0);

    // Wet natural gas composition with water
    fluid.addComponent("nitrogen", 0.0165);
    fluid.addComponent("methane", 0.9271);
    fluid.addComponent("ethane", 0.0305);
    fluid.addComponent("propane", 0.0053);
    fluid.addComponent("i-butane", 0.0035);
    fluid.addComponent("n-butane", 0.0008);
    fluid.addComponent("i-pentane", 0.0007);
    fluid.addComponent("n-pentane", 0.0002);
    fluid.addComponent("n-hexane", 0.0001);
    fluid.addComponent("benzene", 0.0011);
    fluid.addComponent("n-heptane", 0.0002);
    fluid.addComponent("water", 0.0038);

    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    return fluid;
  }

  /**
   * Test that condensation causes liquid holdup to increase along the pipeline.
   * 
   * <p>
   * Pipeline conditions: 70km, 900mm diameter, 30 MSm³/day, heat transfer to 5°C seawater.
   * </p>
   */
  @Test
  public void testCondensationIncreasesHoldup() {
    // Pipeline parameters
    double pipeLength = 70000.0; // m (70 km)
    double pipeDiameter = 0.900; // m (900 mm)
    double pipeRoughness = 10e-6; // m
    int numberOfSections = 100;

    // Operating conditions
    double flowMSm3d = 30.0; // MSm³/day
    double gasDensityStd = 0.75; // kg/Sm³
    double flowKgHr = flowMSm3d * gasDensityStd * 1e6 / 24.0;

    double inletTempC = 45.0; // °C
    double seawaterTempC = 5.0; // °C
    double heatTransferCoeff = 25.0; // W/(m²·K)
    double outletPressureBara = 80.0; // bara
    double inletPressureBara = 100.0; // Initial guess

    // ========== TwoFluidPipe ==========
    System.out.println("===== TwoFluidPipe Condensation Test =====");

    SystemInterface fluidTF = createFeedFluid();
    ProcessSystem psTF = new ProcessSystem();

    Stream inletTF = new Stream("Pipeline Inlet TF", fluidTF);
    inletTF.setFlowRate(flowKgHr, "kg/hr");
    inletTF.setTemperature(inletTempC, "C");
    inletTF.setPressure(inletPressureBara, "bara");
    inletTF.run();
    psTF.add(inletTF);

    TwoFluidPipe pipeTF = new TwoFluidPipe("Export Pipeline TF", inletTF);
    pipeTF.setLength(pipeLength);
    pipeTF.setDiameter(pipeDiameter);
    pipeTF.setRoughness(pipeRoughness);
    pipeTF.setNumberOfSections(numberOfSections);

    // Set flat elevation profile (horizontal pipe)
    double[] elevations = new double[numberOfSections];
    for (int i = 0; i < numberOfSections; i++) {
      elevations[i] = 0.0;
    }
    pipeTF.setElevationProfile(elevations);

    // Enable heat transfer to seawater
    pipeTF.setSurfaceTemperature(seawaterTempC, "C");
    pipeTF.setHeatTransferCoefficient(heatTransferCoeff);

    pipeTF.run();
    psTF.add(pipeTF);

    // Iterate to find correct inlet pressure for target outlet
    for (int iter = 0; iter < 30; iter++) {
      double actualOutletP = pipeTF.getOutletStream().getPressure("bara");
      double error = outletPressureBara - actualOutletP;

      if (Math.abs(error) < 0.1) {
        System.out.println("TwoFluidPipe converged after " + (iter + 1) + " iterations");
        break;
      }

      double newInletP = inletTF.getPressure("bara") + error * 0.8;
      inletTF.setPressure(newInletP, "bara");
      inletTF.run();
      pipeTF.run();
    }

    // Get profiles
    double[] pressureTF = pipeTF.getPressureProfile();
    double[] temperatureTF = pipeTF.getTemperatureProfile();
    double[] liquidHoldupTF = pipeTF.getLiquidHoldupProfile();
    double[] waterHoldupTF = pipeTF.getWaterHoldupProfile();
    double[] oilHoldupTF = pipeTF.getOilHoldupProfile();

    // Print TwoFluidPipe results
    System.out.println("\nTwoFluidPipe Results:");
    System.out
        .println("  Inlet P: " + String.format("%.1f", inletTF.getPressure("bara")) + " bara");
    System.out.println("  Outlet P: "
        + String.format("%.1f", pipeTF.getOutletStream().getPressure("bara")) + " bara");
    System.out
        .println("  Pressure drop: "
            + String.format("%.1f",
                inletTF.getPressure("bara") - pipeTF.getOutletStream().getPressure("bara"))
            + " bar");
    System.out.println("  Inlet T: " + String.format("%.1f", temperatureTF[0] - 273.15) + " °C");
    System.out.println("  Outlet T: "
        + String.format("%.1f", temperatureTF[temperatureTF.length - 1] - 273.15) + " °C");

    // Print holdup at inlet, middle, outlet
    int mid = numberOfSections / 2;
    int last = numberOfSections - 1;
    System.out.println("\nTwoFluidPipe Holdup Profile:");
    System.out.println("  Position\tWater %\t\tOil %\t\tTotal Liq %");
    System.out.println("  Inlet:\t" + String.format("%.4f", waterHoldupTF[0] * 100) + "\t\t"
        + String.format("%.4f", oilHoldupTF[0] * 100) + "\t\t"
        + String.format("%.4f", liquidHoldupTF[0] * 100));
    System.out.println("  Middle:\t" + String.format("%.4f", waterHoldupTF[mid] * 100) + "\t\t"
        + String.format("%.4f", oilHoldupTF[mid] * 100) + "\t\t"
        + String.format("%.4f", liquidHoldupTF[mid] * 100));
    System.out.println("  Outlet:\t" + String.format("%.4f", waterHoldupTF[last] * 100) + "\t\t"
        + String.format("%.4f", oilHoldupTF[last] * 100) + "\t\t"
        + String.format("%.4f", liquidHoldupTF[last] * 100));

    // Print flow regimes
    PipeSection.FlowRegime[] flowRegimes = pipeTF.getFlowRegimeProfile();
    System.out.println("\nFlow Regimes:");
    System.out.println("  Inlet: " + flowRegimes[0]);
    System.out.println("  Middle: " + flowRegimes[mid]);
    System.out.println("  Outlet: " + flowRegimes[last]);

    // Print detailed debug info
    System.out.println("\nDetailed Debug Info:");
    double[] gasVelocityTF = pipeTF.getGasVelocityProfile();
    double[] liquidVelocityTF = pipeTF.getLiquidVelocityProfile();
    System.out.println("  Position\tT(C)\t\tP(bara)\t\tVgas(m/s)\tVliq(m/s)\tHoldup(%)");
    for (int idx : new int[] {0, 25, 50, 75, last}) {
      System.out.println("  " + idx + ":\t\t" + String.format("%.1f", temperatureTF[idx] - 273.15)
          + "\t\t" + String.format("%.1f", pressureTF[idx] / 1e5) + "\t\t"
          + String.format("%.2f", gasVelocityTF[idx]) + "\t\t"
          + String.format("%.4f", liquidVelocityTF[idx]) + "\t\t"
          + String.format("%.4f", liquidHoldupTF[idx] * 100));
    }

    // Calculate and print no-slip holdup (lambdaL) at each position
    System.out.println("\nNo-slip holdup calculation:");
    double pipeArea = Math.PI * pipeDiameter * pipeDiameter / 4.0;
    for (int idx : new int[] {0, 50, last}) {
      // Calculate superficial velocities from actual velocities and holdup
      double alphaL = liquidHoldupTF[idx];
      double alphaG = 1.0 - alphaL;
      double vsL = liquidVelocityTF[idx] * alphaL; // vsL = vL * alphaL
      double vsG = gasVelocityTF[idx] * alphaG; // vsG = vG * alphaG
      double vMix = vsG + vsL;
      double lambdaL = vsL / vMix;
      System.out.println("  Idx " + idx + ": vsL=" + String.format("%.4f", vsL) + " m/s, vsG="
          + String.format("%.2f", vsG) + " m/s, lambdaL=" + String.format("%.6f", lambdaL) + " ("
          + String.format("%.4f", lambdaL * 100) + "%)");
    }

    // ========== PipeBeggsAndBrills for comparison ==========
    System.out.println("\n===== PipeBeggsAndBrills Comparison =====");

    SystemInterface fluidBB = createFeedFluid();
    ProcessSystem psBB = new ProcessSystem();

    Stream inletBB = new Stream("Pipeline Inlet BB", fluidBB);
    inletBB.setFlowRate(flowKgHr, "kg/hr");
    inletBB.setTemperature(inletTempC, "C");
    inletBB.setPressure(100.0, "bara");
    inletBB.run();
    psBB.add(inletBB);

    PipeBeggsAndBrills pipeBB = new PipeBeggsAndBrills("Export Pipeline BB", inletBB);
    pipeBB.setLength(pipeLength);
    pipeBB.setDiameter(pipeDiameter);
    pipeBB.setElevation(0.0);
    pipeBB.setPipeWallRoughness(pipeRoughness);
    pipeBB.setNumberOfIncrements(numberOfSections);
    pipeBB.setConstantSurfaceTemperature(seawaterTempC, "C");
    pipeBB.setHeatTransferCoefficient(heatTransferCoeff);
    pipeBB.run();
    psBB.add(pipeBB);

    // Iterate to target outlet pressure
    for (int iter = 0; iter < 30; iter++) {
      double actualOutletP = pipeBB.getOutletStream().getPressure("bara");
      double error = outletPressureBara - actualOutletP;

      if (Math.abs(error) < 0.1) {
        System.out.println("PipeBeggsAndBrills converged after " + (iter + 1) + " iterations");
        break;
      }

      double newInletP = inletBB.getPressure("bara") + error * 0.7;
      inletBB.setPressure(newInletP, "bara");
      inletBB.run();
      pipeBB.run();
    }

    // Get profiles
    double[] pressureBB = pipeBB.getPressureProfile();
    double[] temperatureBB = pipeBB.getTemperatureProfile();
    double[] liquidHoldupBB = pipeBB.getLiquidHoldupProfile();

    // Print BB results
    System.out.println("\nPipeBeggsAndBrills Results:");
    System.out
        .println("  Inlet P: " + String.format("%.1f", inletBB.getPressure("bara")) + " bara");
    System.out.println("  Outlet P: "
        + String.format("%.1f", pipeBB.getOutletStream().getPressure("bara")) + " bara");
    System.out
        .println("  Pressure drop: "
            + String.format("%.1f",
                inletBB.getPressure("bara") - pipeBB.getOutletStream().getPressure("bara"))
            + " bar");
    System.out.println("  Inlet T: " + String.format("%.1f", temperatureBB[0] - 273.15) + " °C");
    System.out.println("  Outlet T: "
        + String.format("%.1f", temperatureBB[temperatureBB.length - 1] - 273.15) + " °C");

    System.out.println("\nPipeBeggsAndBrills Holdup Profile:");
    System.out.println("  Position\tTotal Liq %");
    System.out.println("  Inlet:\t" + String.format("%.4f", liquidHoldupBB[0] * 100));
    System.out.println("  Middle:\t" + String.format("%.4f", liquidHoldupBB[mid] * 100));
    System.out.println("  Outlet:\t" + String.format("%.4f", liquidHoldupBB[last] * 100));

    // ========== Assertions ==========
    // Temperature should drop significantly (from 45°C towards 5°C)
    double tempDropTF = (temperatureTF[0] - temperatureTF[last]);
    double tempDropBB = (temperatureBB[0] - temperatureBB[last]);
    System.out.println("\n===== Verification =====");
    System.out.println("Temperature drop TF: " + String.format("%.1f", tempDropTF) + " K");
    System.out.println("Temperature drop BB: " + String.format("%.1f", tempDropBB) + " K");

    assertTrue(tempDropTF > 30, "TwoFluidPipe: Temperature should drop > 30K due to heat transfer");
    assertTrue(tempDropBB > 30,
        "PipeBeggsAndBrills: Temperature should drop > 30K due to heat transfer");

    // For BB, holdup should increase from inlet to outlet due to condensation
    double holdupIncreaseBB = liquidHoldupBB[last] - liquidHoldupBB[0];
    System.out.println("BB holdup increase (outlet - inlet): "
        + String.format("%.6f", holdupIncreaseBB * 100) + " %");
    assertTrue(holdupIncreaseBB > 0,
        "PipeBeggsAndBrills: Holdup should increase from inlet to outlet due to condensation");

    // For TwoFluidPipe, holdup should also increase from inlet to outlet
    double holdupIncreaseTF = liquidHoldupTF[last] - liquidHoldupTF[0];
    System.out.println("TF holdup increase (outlet - inlet): "
        + String.format("%.6f", holdupIncreaseTF * 100) + " %");

    // This is the critical test - if this fails, the condensation isn't affecting holdup
    assertTrue(holdupIncreaseTF > 0,
        "TwoFluidPipe: Holdup should increase from inlet to outlet due to condensation. "
            + "Current increase: " + String.format("%.6f", holdupIncreaseTF * 100) + "%");

    System.out.println("\n✓ All assertions passed!");
  }

  /**
   * Debug test to trace thermodynamic updates.
   */
  @Test
  public void testFlashAtPipelineConditions() {
    System.out.println("===== Flash at Pipeline Conditions =====");

    SystemInterface fluid = createFeedFluid();

    // Test flash at inlet conditions (45°C, 100 bara)
    fluid.setTemperature(45.0, "C");
    fluid.setPressure(100.0, "bara");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initPhysicalProperties();

    System.out.println("\nAt INLET (45°C, 100 bara):");
    System.out.println("  Number of phases: " + fluid.getNumberOfPhases());

    double totalMassWeighted = 0.0;
    for (int p = 0; p < fluid.getNumberOfPhases(); p++) {
      double beta = fluid.getBeta(p);
      double molarMass = fluid.getPhase(p).getMolarMass();
      totalMassWeighted += beta * molarMass;
    }

    double gasMassContrib = 0.0;
    double liqMassContrib = 0.0;

    for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
      String phaseType = fluid.getPhase(i).getType().toString();
      double phaseFrac = fluid.getBeta(i);
      double density = fluid.getPhase(i).getDensity("kg/m3");
      double molarMass = fluid.getPhase(i).getMolarMass();
      double massContrib = phaseFrac * molarMass;

      System.out.println("  Phase " + (i + 1) + ": type='" + phaseType + "', beta=" + phaseFrac
          + ", molarMass=" + molarMass + " kg/mol, massContrib=" + massContrib);

      if (phaseType.equals("gas")) {
        gasMassContrib += massContrib;
      } else {
        liqMassContrib += massContrib;
      }
    }

    System.out.println("  totalMassWeighted: " + totalMassWeighted);
    System.out.println("  gasMassContrib: " + gasMassContrib);
    System.out.println("  liqMassContrib: " + liqMassContrib);
    System.out.println("  Gas mass fraction: " + (gasMassContrib / totalMassWeighted));
    System.out.println("  Liquid mass fraction: " + (liqMassContrib / totalMassWeighted));

    // Test flash at outlet conditions (5°C, 80 bara)
    SystemInterface fluid2 = createFeedFluid();
    fluid2.setTemperature(5.0, "C");
    fluid2.setPressure(80.0, "bara");

    ThermodynamicOperations ops2 = new ThermodynamicOperations(fluid2);
    ops2.TPflash();
    fluid2.initPhysicalProperties();

    System.out.println("\nAt OUTLET (5°C, 80 bara):");
    System.out.println("  Number of phases: " + fluid2.getNumberOfPhases());

    totalMassWeighted = 0.0;
    for (int p = 0; p < fluid2.getNumberOfPhases(); p++) {
      double beta = fluid2.getBeta(p);
      double molarMass = fluid2.getPhase(p).getMolarMass();
      totalMassWeighted += beta * molarMass;
    }

    gasMassContrib = 0.0;
    liqMassContrib = 0.0;

    for (int i = 0; i < fluid2.getNumberOfPhases(); i++) {
      String phaseType = fluid2.getPhase(i).getType().toString();
      double phaseFrac = fluid2.getBeta(i);
      double molarMass = fluid2.getPhase(i).getMolarMass();
      double massContrib = phaseFrac * molarMass;

      System.out.println("  Phase " + (i + 1) + ": type='" + phaseType + "', beta=" + phaseFrac
          + ", molarMass=" + molarMass + " kg/mol, massContrib=" + massContrib);

      if (phaseType.equals("gas")) {
        gasMassContrib += massContrib;
      } else {
        liqMassContrib += massContrib;
      }
    }

    System.out.println("  totalMassWeighted: " + totalMassWeighted);
    System.out.println("  gasMassContrib: " + gasMassContrib);
    System.out.println("  liqMassContrib: " + liqMassContrib);
    System.out.println("  Gas mass fraction: " + (gasMassContrib / totalMassWeighted));
    System.out.println("  Liquid mass fraction: " + (liqMassContrib / totalMassWeighted));

    // The liquid fraction should increase from inlet to outlet due to condensation
    System.out.println("\n=> Liquid mass fraction should be HIGHER at outlet due to condensation");
  }
}
