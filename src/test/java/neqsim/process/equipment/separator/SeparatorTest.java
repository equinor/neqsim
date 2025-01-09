package neqsim.process.equipment.separator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.measurementdevice.LevelTransmitter;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemSrkCPAstatoil;

/**
 * @author ESOL
 */
class SeparatorTest extends neqsim.NeqSimTest {
  static neqsim.thermo.system.SystemInterface testSystem = null;
  double pressure_inlet = 55.0;
  double temperature_inlet = 35.0;
  double gasFlowRate = 5.0;
  ProcessSystem processOps = null;
  Separator sep = null;

  /**
   * @throws java.lang.Exception
   */
  @BeforeEach
  public void setUpBeforeClass() {
    testSystem = new SystemSrkCPAstatoil(298.0, 10.0);
    testSystem.addComponent("methane", 100.0);
    testSystem.addComponent("ethane", 10.0);
    testSystem.addComponent("nC10", 10.0);
    testSystem.addComponent("water", 10.0);
    testSystem.setMixingRule(10);
    testSystem.setMultiPhaseCheck(true);

    StreamInterface inletStream = new Stream("inlet stream", testSystem);
    inletStream.setPressure(pressure_inlet, "bara");
    inletStream.setTemperature(temperature_inlet, "C");
    inletStream.setFlowRate(gasFlowRate, "MSm3/day");

    sep = new Separator("inlet separator");
    sep.setInletStream(inletStream);

    processOps = new ProcessSystem();
    processOps.add(inletStream);
    processOps.add(sep);
  }

  @Test
  public void testFlow() {
    LevelTransmitter lt = new LevelTransmitter("levelTransmitter", sep);
    Assertions.assertEquals(0.5, lt.getMeasuredValue(), 1e-12);
    ((StreamInterface) processOps.getUnit("inlet stream")).setFlowRate(0.01, "MSm3/day");
    processOps.run();
    Assertions.assertEquals(0.5, lt.getMeasuredValue(), 1e-12);
    Assertions.assertEquals(lt.getMeasuredValue() * 100, lt.getMeasuredPercentValue(), 1e-12);
  }

  @Test
  public void testOnePhase() {
    ((StreamInterface) processOps.getUnit("inlet stream")).setFlowRate(1.0, "MSm3/day");
    ((StreamInterface) processOps.getUnit("inlet stream")).getFluid()
        .setMolarComposition(new double[] {1.0, 0.0, 0.0, 0.0});

    processOps.run();
  }

  @Test
  public void testSimpleSeparator() {
    neqsim.thermo.system.SystemSrkEos fluid1 = new neqsim.thermo.system.SystemSrkEos(280.0, 10.0);
    fluid1.addComponent("water", 2.7);
    fluid1.addComponent("nitrogen", 0.7);
    fluid1.addComponent("CO2", 2.1);
    fluid1.addComponent("methane", 70.0);
    fluid1.addComponent("ethane", 10.0);
    fluid1.addComponent("propane", 5.0);
    fluid1.addComponent("i-butane", 3.0);
    fluid1.addComponent("n-butane", 2.0);
    fluid1.addComponent("i-pentane", 1.0);
    fluid1.addComponent("n-pentane", 1.0);
    fluid1.addTBPfraction("C6", 1.49985, 86.3 / 1000.0, 0.7432);
    fluid1.addTBPfraction("C7", 0.49985, 103.3 / 1000.0, 0.76432);
    fluid1.addTBPfraction("C8", 0.39985, 125.0 / 1000.0, 0.78432);
    fluid1.addTBPfraction("C9", 0.49985, 145.0 / 1000.0, 0.79432);
    fluid1.addTBPfraction("C10", 0.149985, 165.0 / 1000.0, 0.81);
    fluid1.setMixingRule(2);
    fluid1.setMultiPhaseCheck(true);
    fluid1.setTemperature(55.0, "C");
    fluid1.setPressure(55.0, "bara");

    Stream feedStream = new Stream("feed fluid", fluid1);
    Separator separator1 = new Separator("sep1", feedStream);

    processOps = new ProcessSystem();
    processOps.add(feedStream);
    processOps.add(separator1);
    processOps.run();

    Assertions.assertEquals(0.06976026260, feedStream.getFluid().getPhase(PhaseType.OIL).getBeta(),
        1e-5);

    Assertions.assertEquals(0.06976026260, separator1.getFluid().getPhase(PhaseType.OIL).getBeta(),
        1e-5);
  }
}
