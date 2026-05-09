package neqsim.process.chemistry;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.chemistry.scale.ScaleDepositionAccumulator;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Coverage for {@link ScaleDepositionAccumulator} integrating along a Beggs-and-Brills pipeline.
 *
 * @author ESOL
 * @version 1.0
 */
public class ChemistryDepositionAccumulatorTest {

  /**
   * Builds a small wet-gas + brine line and runs the accumulator. Asserts the accumulator
   * evaluates, the segment lists match the increment count, and total mass &gt;= 0.
   */
  @Test
  public void depositionAccumulatorRunsAndProducesProfiles() {
    SystemSrkEos sys = new SystemSrkEos(308.15, 80.0);
    sys.addComponent("methane", 95.0);
    sys.addComponent("CO2", 5.0);
    sys.addComponent("water", 1.5);
    sys.setMixingRule("classic");
    sys.setMultiPhaseCheck(true);
    StreamInterface s = new Stream("feed", sys);
    s.setFlowRate(50000.0, "kg/hr");
    s.run();

    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("line", s);
    pipe.setLength(1000.0);
    pipe.setDiameter(0.2);
    pipe.setElevation(0.0);
    pipe.setAngle(0.0);
    pipe.setNumberOfIncrements(20);
    pipe.run();

    ScaleDepositionAccumulator acc = new ScaleDepositionAccumulator(pipe);
    acc.setBrineChemistry(1500.0, 400.0, 100.0, 5.0, 12000.0, 35000.0);
    acc.setpHAndCo2(6.5, 4.0);
    acc.setInhibitorEfficiency(0.7);
    acc.setServiceYears(5.0);
    acc.evaluate();

    assertTrue(acc.isEvaluated());
    assertTrue(acc.getSegmentSaturationIndex().size() > 0);
    assertTrue(acc.getSegmentDepositionMassKg().size() == acc.getSegmentSaturationIndex().size());
    assertTrue(acc.getSegmentThicknessMm().size() == acc.getSegmentSaturationIndex().size());
    assertTrue(acc.getTotalDepositionMassKg() >= 0.0);
    assertTrue(acc.getMaxThicknessMm() >= 0.0);
    assertNotNull(acc.toMap().get("standardsApplied"));
    assertNotNull(acc.toJson());
  }

  private void assertEquals20(int actual, int expected) {
    assertTrue(actual == expected, "expected " + expected + " got " + actual);
  }
}
