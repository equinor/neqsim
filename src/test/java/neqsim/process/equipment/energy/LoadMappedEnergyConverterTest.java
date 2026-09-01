package neqsim.process.equipment.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.EnergyBus;
import neqsim.process.equipment.stream.EnergyPort;
import neqsim.process.equipment.stream.EnergyPortDirection;
import neqsim.process.equipment.stream.EnergyPortMode;
import neqsim.process.equipment.stream.EnergyType;

class LoadMappedEnergyConverterTest {

  private static final LoadEfficiencyCurve CURVE = new LoadEfficiencyCurve(new double[] { 0.25, 0.5, 1.0 },
      new double[] { 0.80, 0.90, 0.96 });

  @Test
  void testCurveInterpolationAndValidation() {
    assertEquals(0.80, CURVE.getEfficiency(0.1), 1.0e-12);
    assertEquals(0.85, CURVE.getEfficiency(0.375), 1.0e-12);
    assertEquals(0.96, CURVE.getEfficiency(1.2), 1.0e-12);
    assertThrows(IllegalArgumentException.class,
        () -> new LoadEfficiencyCurve(new double[] { 0.5, 0.5 }, new double[] { 0.9, 0.95 }));
  }

  @Test
  void testGeneratorInverseSizingAndForwardConversion() {
    Generator generator = new Generator("generator");
    generator.setRatedOutputPower(1.0e6);
    generator.setLoadEfficiencyCurve(CURVE);

    assertEquals(500.0e3 / 0.90, generator.getRequiredInputPowerForOutput(500.0e3), 1.0e-9);

    EnergyBus shaft = new EnergyBus("shaft", EnergyType.SHAFT_WORK);
    EnergyBus grid = new EnergyBus("grid", EnergyType.ELECTRICAL);
    EnergyPort source = source("source", shaft);
    generator.connectEnergyStream(EnergyConverter.INPUT_PORT, shaft, EnergyPortMode.SPECIFICATION);
    generator.connectEnergyStream(EnergyConverter.OUTPUT_PORT, grid, EnergyPortMode.CALCULATED);
    generator.setRequestedInputPower(500.0e3 / 0.90);
    source.setDuty(500.0e3 / 0.90);
    shaft.solveBalance();
    generator.run(UUID.randomUUID());

    assertEquals(500.0e3, generator.getOutputPower(), 1.0e-5);
    assertEquals(generator.getInputPower(), generator.getOutputPower() + generator.getHeatLoss(), 1.0e-9);
  }

  @Test
  void testTransformerAndPrimeMoverUseSameCurveContract() {
    Transformer transformer = new Transformer("transformer");
    transformer.setRatedOutputPower(10.0e6);
    transformer.setLoadEfficiencyCurve(CURVE);
    assertEquals(2.5e6 / 0.80, transformer.getRequiredInputPowerForOutput(2.5e6), 1.0e-9);

    PrimeMover primeMover = new PrimeMover("prime mover");
    primeMover.setRatedOutputPower(5.0e6);
    primeMover.setLoadEfficiencyCurve(CURVE);
    assertEquals(5.0e6 / 0.96, primeMover.getRequiredInputPowerForOutput(5.0e6), 1.0e-9);
    assertThrows(IllegalArgumentException.class, () -> primeMover.getRequiredInputPowerForOutput(5.1e6));
  }

  @Test
  void testCurveRequiresRatingAndCanBeCleared() {
    Generator generator = new Generator("fallback", 0.97);
    generator.setLoadEfficiencyCurve(CURVE);
    assertThrows(IllegalStateException.class, () -> generator.getRequiredInputPowerForOutput(100.0e3));
    generator.clearLoadEfficiencyCurve();
    assertFalse(generator.hasLoadEfficiencyCurve());
    assertEquals(100.0e3 / 0.97, generator.getRequiredInputPowerForOutput(100.0e3), 1.0e-9);
  }

  private static EnergyPort source(String owner, EnergyBus bus) {
    EnergyPort port = new EnergyPort("power", bus.getEnergyType(), EnergyPortDirection.OUTPUT,
        EnergyPortMode.CALCULATED);
    port.setOwnerName(owner);
    port.connect(bus);
    return port;
  }
}
