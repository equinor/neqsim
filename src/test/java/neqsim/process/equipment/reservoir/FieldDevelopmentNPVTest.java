package neqsim.process.equipment.reservoir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.util.Adjuster;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Test recreating NPV field development calculation from NeqSim-Colab notebook
 * (notebooks/fielddevelopment/npv.ipynb).
 *
 * <p>
 * Models a subsea gas tieback with SimpleReservoir, WellFlow, three pipeline segments (vertical
 * well, new flowline, existing export pipeline), and an adjuster controlling gas flow to maintain
 * outlet pressure at the receiving facility.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class FieldDevelopmentNPVTest {

  /**
   * Tests the basic process setup with adjuster converging on target outlet pressure.
   */
  @Test
  void testSubseaTiebackWithAdjuster() {
    // Technical data from notebook
    double reservoirPressure = 150.0; // bara
    double reservoirTemperature = 75.0; // C
    double gasReservoirVolume = 1e9; // m3
    double pipeWallRoughness = 15e-6; // meter
    double wellDepth = 1070; // meter
    double inletPressure = 30.0; // bara
    double maxGasProduction = 10.0; // MSm3/day
    int referenceNumberOfWells = 6;
    int numberOfWells = 6;

    // Pipeline parameters
    double newPipelineLength = 10; // km
    double newPipelineDiameter = 14.0 * 0.0254; // 14" flowline
    double existingPipelineLength = 80; // km
    double existingPipelineDiameter = 24.0 * 0.0254; // 24" pipeline

    // Create reservoir fluid (SRK EOS)
    neqsim.thermo.system.SystemInterface reservoirFluid =
        new neqsim.thermo.system.SystemSrkEos(273.15 + reservoirTemperature, reservoirPressure);
    reservoirFluid.addComponent("nitrogen", 0.5);
    reservoirFluid.addComponent("CO2", 0.5);
    reservoirFluid.addComponent("methane", 90.0);
    reservoirFluid.addComponent("ethane", 5.0);
    reservoirFluid.addComponent("propane", 2.0);
    reservoirFluid.addComponent("i-butane", 1.0);
    reservoirFluid.addComponent("n-butane", 1.0);
    reservoirFluid.addComponent("n-hexane", 0.5);
    reservoirFluid.addComponent("water", 1.0);
    reservoirFluid.setMixingRule("classic");
    reservoirFluid.setMultiPhaseCheck(true);

    // Create reservoir
    SimpleReservoir reservoirOps = new SimpleReservoir("Well 1 reservoir");
    reservoirOps.setReservoirFluid(reservoirFluid.clone(), gasReservoirVolume, 1.0, 10.0e7);
    reservoirOps.setLowPressureLimit(10.0, "bara");

    // Gas producer stream
    StreamInterface producedGasStream = reservoirOps.addGasProducer("well number");
    producedGasStream.setFlowRate(maxGasProduction, "MSm3/day");

    // Well flow model
    double productionIndex = 10.0E-3 * numberOfWells / referenceNumberOfWells;
    WellFlow wellflow = new WellFlow("well flow unit");
    wellflow.setInletStream(producedGasStream);
    wellflow.setWellProductionIndex(productionIndex);

    // Vertical well pipe (wellbore to Xmas tree)
    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("pipe", wellflow.getOutletStream());
    pipe.setPipeWallRoughness(pipeWallRoughness);
    pipe.setLength(wellDepth);
    pipe.setElevation(wellDepth);
    pipe.setDiameter(0.625);

    // New pipeline (subsea, Xmas tree to existing infrastructure)
    PipeBeggsAndBrills pipeline = new PipeBeggsAndBrills("pipe2", pipe.getOutletStream());
    pipeline.setPipeWallRoughness(pipeWallRoughness);
    pipeline.setLength(newPipelineLength * 1e3);
    pipeline.setElevation(0);
    pipeline.setDiameter(newPipelineDiameter);

    // Existing export pipeline (subsea to topside)
    PipeBeggsAndBrills pipeline2 = new PipeBeggsAndBrills("pipe3", pipeline.getOutletStream());
    pipeline2.setPipeWallRoughness(pipeWallRoughness);
    pipeline2.setLength(existingPipelineLength * 1e3);
    pipeline2.setElevation(300);
    pipeline2.setDiameter(existingPipelineDiameter);

    // Choke valve at topside
    ThrottlingValve chokeValve = new ThrottlingValve("choke");
    chokeValve.setInletStream(pipeline2.getOutletStream());
    chokeValve.setOutletPressure(inletPressure - 1, "bara");

    // Adjuster: match outlet pressure to inlet_pressure by adjusting gas flow
    Adjuster adjuster = new Adjuster("adjuster");
    adjuster.setTargetVariable(pipeline2.getOutletStream(), "pressure", inletPressure, "bara");
    adjuster.setAdjustedVariable(producedGasStream, "flow", "MSm3/day");
    adjuster.setMaxAdjustedValue(maxGasProduction);
    adjuster.setMinAdjustedValue(1.0);

    // Build process
    ProcessSystem process = new ProcessSystem();
    process.add(reservoirOps);
    process.add(wellflow);
    process.add(pipe);
    process.add(pipeline);
    process.add(pipeline2);
    process.add(chokeValve);
    process.add(adjuster);
    process.run();

    // Verify adjuster converges (at bounds if target is unreachable)
    double outletPressure = pipeline2.getOutletStream().getPressure("bara");
    assertTrue(adjuster.solved(), "Adjuster should declare convergence");

    // Verify flow is within bounds
    double gasFlow = producedGasStream.getFlowRate("MSm3/day");
    assertTrue(gasFlow >= 1.0 && gasFlow <= maxGasProduction,
        "Gas flow should be within adjuster bounds: " + gasFlow);

    // At 150 bara reservoir with max 10 MSm3/day, the outlet pressure will be
    // much higher than 30 bara since maximum flow doesn't produce enough pressure
    // drop. The adjuster should converge at max flow accepting the higher pressure.
    assertEquals(maxGasProduction, gasFlow, 0.5,
        "Flow should be at max when target pressure is unreachable");
    assertTrue(outletPressure > inletPressure,
        "Outlet pressure should be above target when at max flow: " + outletPressure);

    // Verify reservoir pressure is reasonable
    double resPressure = reservoirOps.getReservoirFluid().getPressure("bara");
    assertTrue(resPressure > 100 && resPressure <= 150,
        "Reservoir pressure should be reasonable: " + resPressure);
  }

  /**
   * Tests transient production simulation over multiple years with adjuster. This reproduces the
   * full NPV production profile calculation from the notebook.
   */
  @Test
  void testTransientProductionWithAdjuster() {
    // Technical data
    double reservoirPressure = 150.0;
    double reservoirTemperature = 75.0;
    double gasReservoirVolume = 1e9;
    double pipeWallRoughness = 15e-6;
    double wellDepth = 1070;
    double inletPressure = 30.0;
    double maxGasProduction = 10.0;
    double productionEfficiency = 0.94;
    int referenceNumberOfWells = 6;
    int numberOfWells = 6;

    double newPipelineLength = 10;
    double newPipelineDiameter = 14.0 * 0.0254;
    double existingPipelineLength = 80;
    double existingPipelineDiameter = 24.0 * 0.0254;

    // Create reservoir fluid
    neqsim.thermo.system.SystemInterface reservoirFluid =
        new neqsim.thermo.system.SystemSrkEos(273.15 + reservoirTemperature, reservoirPressure);
    reservoirFluid.addComponent("nitrogen", 0.5);
    reservoirFluid.addComponent("CO2", 0.5);
    reservoirFluid.addComponent("methane", 90.0);
    reservoirFluid.addComponent("ethane", 5.0);
    reservoirFluid.addComponent("propane", 2.0);
    reservoirFluid.addComponent("i-butane", 1.0);
    reservoirFluid.addComponent("n-butane", 1.0);
    reservoirFluid.addComponent("n-hexane", 0.5);
    reservoirFluid.addComponent("water", 1.0);
    reservoirFluid.setMixingRule("classic");
    reservoirFluid.setMultiPhaseCheck(true);

    // Create reservoir
    SimpleReservoir reservoirOps = new SimpleReservoir("Well 1 reservoir");
    reservoirOps.setReservoirFluid(reservoirFluid.clone(), gasReservoirVolume, 1.0, 10.0e7);
    reservoirOps.setLowPressureLimit(10.0, "bara");

    StreamInterface producedGasStream = reservoirOps.addGasProducer("well number");
    producedGasStream.setFlowRate(maxGasProduction, "MSm3/day");

    double productionIndex = 10.0E-3 * numberOfWells / referenceNumberOfWells;
    WellFlow wellflow = new WellFlow("well flow unit");
    wellflow.setInletStream(producedGasStream);
    wellflow.setWellProductionIndex(productionIndex);

    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("pipe", wellflow.getOutletStream());
    pipe.setPipeWallRoughness(pipeWallRoughness);
    pipe.setLength(wellDepth);
    pipe.setElevation(wellDepth);
    pipe.setDiameter(0.625);

    PipeBeggsAndBrills pipeline = new PipeBeggsAndBrills("pipe2", pipe.getOutletStream());
    pipeline.setPipeWallRoughness(pipeWallRoughness);
    pipeline.setLength(newPipelineLength * 1e3);
    pipeline.setElevation(0);
    pipeline.setDiameter(newPipelineDiameter);

    PipeBeggsAndBrills pipeline2 = new PipeBeggsAndBrills("pipe3", pipeline.getOutletStream());
    pipeline2.setPipeWallRoughness(pipeWallRoughness);
    pipeline2.setLength(existingPipelineLength * 1e3);
    pipeline2.setElevation(300);
    pipeline2.setDiameter(existingPipelineDiameter);

    ThrottlingValve chokeValve = new ThrottlingValve("choke");
    chokeValve.setInletStream(pipeline2.getOutletStream());
    chokeValve.setOutletPressure(inletPressure - 1, "bara");

    Adjuster adjuster = new Adjuster("adjuster");
    adjuster.setTargetVariable(pipeline2.getOutletStream(), "pressure", inletPressure, "bara");
    adjuster.setAdjustedVariable(producedGasStream, "flow", "MSm3/day");
    adjuster.setMaxAdjustedValue(maxGasProduction);
    adjuster.setMinAdjustedValue(1.0);

    ProcessSystem process = new ProcessSystem();
    process.add(reservoirOps);
    process.add(wellflow);
    process.add(pipe);
    process.add(pipeline);
    process.add(pipeline2);
    process.add(chokeValve);
    process.add(adjuster);
    process.run();

    // Initial GIP
    double gip = reservoirOps.getGasInPlace("GSm3");
    assertTrue(gip > 100, "GIP should be > 100 GSm3: " + gip);

    // Run transient production simulation
    double deltaT = 60 * 60.0 * 365 * 24 * productionEfficiency;

    for (int t = 0; t < 15; t++) {
      productionIndex = 10.000100751427403E-3 * numberOfWells / referenceNumberOfWells;
      wellflow.setWellProductionIndex(productionIndex);

      if (pipeline2.getOutletStream().getPressure("bara") < inletPressure) {
        break;
      }
      if (t >= 1) {
        for (int k = 0; k < 3; k++) {
          reservoirOps.runTransient(deltaT / 10.0);
          process.run();
        }
      }

      double resPressure = reservoirOps.getReservoirFluid().getPressure("bara");
      double gasProductionRate = reservoirOps.getGasProdution("Sm3/day") / 1e6;
      double topsidePressure = pipeline2.getOutletStream().getPressure("bara");

      // Validate pressure stays reasonable (should be > low pressure limit)
      assertTrue(resPressure > 10,
          "Year " + t + ": Reservoir pressure should stay above low limit: " + resPressure);

      // Validate flow rate is within bounds
      assertTrue(gasProductionRate >= 0,
          "Year " + t + ": Gas production should be non-negative: " + gasProductionRate);

      // Validate topside pressure is positive and within physical range
      assertTrue(topsidePressure > 0 && topsidePressure < 200,
          "Year " + t + ": Topside pressure should be in physical range: " + topsidePressure);
    }

    // Verify some gas was produced
    double totalProduced = reservoirOps.getProductionTotal("MSm3 oe");
    assertTrue(totalProduced > 0, "Should have produced some gas: " + totalProduced);
  }
}
