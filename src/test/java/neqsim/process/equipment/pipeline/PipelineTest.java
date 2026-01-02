package neqsim.process.equipment.pipeline;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;

public class PipelineTest {
  @Test
  public void testMain() {
    double flow = 60.0;
    double temperature = 20.0;
    double pressure = 200.0;

    double diameter = 1.0;
    double length = 700000.0;
    double elevation = 0;
    double wallroughness = 5e-6;

    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos((273.15 + temperature), pressure);
    testSystem.addComponent("methane", 0.9);
    testSystem.addComponent("ethane", 0.1);
    testSystem.setMixingRule("classic");

    Stream stream_1 = new Stream("Stream1", testSystem);
    stream_1.setFlowRate(flow, "MSm3/day");
    stream_1.setTemperature(temperature, "C");
    stream_1.setPressure(pressure, "bara");

    stream_1.run();
    OnePhasePipeLine pipeline = new OnePhasePipeLine("pipeline", stream_1);
    pipeline.setNumberOfLegs(1);
    pipeline.setPipeDiameters(new double[] {diameter, diameter});
    pipeline.setLegPositions(new double[] {0, length});
    pipeline.setHeightProfile(new double[] {0, elevation});
    pipeline.setPipeWallRoughness(new double[] {wallroughness, wallroughness});
    pipeline.setOuterTemperatures(new double[] {temperature + 273.15, temperature + 273.15});
    pipeline.setPipeOuterHeatTransferCoefficients(new double[] {15.0, 15.0});
    pipeline.setPipeWallHeatTransferCoefficients(new double[] {15.0, 15.0});

    AdiabaticPipe simplePipeline = new AdiabaticPipe("simplePipeline", stream_1);
    simplePipeline.setDiameter(diameter);
    simplePipeline.setLength(length);
    simplePipeline.setPipeWallRoughness(wallroughness);
    simplePipeline.setInletElevation(0);
    simplePipeline.setOutletElevation(elevation);

    PipeBeggsAndBrills beggsBrilsPipe = new PipeBeggsAndBrills("simplePipeline 2", stream_1);
    beggsBrilsPipe.setPipeWallRoughness(wallroughness);
    beggsBrilsPipe.setLength(length);
    beggsBrilsPipe.setElevation(elevation);
    beggsBrilsPipe.setDiameter(diameter);
    beggsBrilsPipe.setRunIsothermal(false);

    neqsim.process.processmodel.ProcessSystem operations =
        new neqsim.process.processmodel.ProcessSystem();
    operations.add(stream_1);
    operations.add(pipeline);
    operations.add(simplePipeline);
    operations.add(beggsBrilsPipe);
    operations.run();

    // pipeline.run();

    System.out.println(beggsBrilsPipe.getOutletStream().getTemperature());

    Assertions.assertEquals(123.876927, pipeline.getOutletPressure("bara"), 0.1);
    Assertions.assertEquals(120.711887695240, simplePipeline.getOutletPressure(), 0.1);
    Assertions.assertEquals(128.376, beggsBrilsPipe.getOutletPressure(), 0.2);
  }
}
