package neqsim.process.corrosion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link PipeSegmentIntegrity} — per-segment coupled corrosion and scale screening along a profile.
 *
 * @author ESOL
 * @version 1.0
 */
public class PipeSegmentIntegrityTest {

  /**
   * Verifies that a cooling profile is walked, per-segment results are produced, and the worst corrosion and scale
   * segments are identified.
   */
  @Test
  void walksProfileAndRanksSegments() {
    double[] tC = { 90.0, 70.0, 50.0, 30.0 };
    double[] pBara = { 120.0, 100.0, 80.0, 60.0 };
    double[] vMs = { 4.0, 3.5, 3.0, 2.5 };

    PipeSegmentIntegrity integrity = new PipeSegmentIntegrity();
    integrity.setProfile(tC, pBara, vMs).setPipeAndGas(0.254, 0.03).setBrineChemistry(1500.0, 500.0, 0.0, 0.0, 20000.0,
        35000.0);
    integrity.evaluate();

    assertEquals(4, integrity.getSegments().size(), "should produce one result per segment");
    for (PipeSegmentIntegrity.SegmentResult s : integrity.getSegments()) {
      assertTrue(s.getCorrosionRateMmYr() >= 0.0, "corrosion rate should be non-negative");
      assertTrue(s.getPH() > 2.0 && s.getPH() < 9.0, "segment pH should be in a plausible band, was " + s.getPH());
    }

    assertTrue(integrity.getWorstCorrosionIndex() >= 0, "a worst corrosion segment should be identified");
    assertTrue(integrity.getWorstScaleIndex() >= 0, "a worst scale segment should be identified");
    assertTrue(integrity.getMaxCorrosionRateMmYr() > 0.0, "max corrosion rate should be positive");
    assertNotNull(integrity.toJson());
  }

  /**
   * Verifies that a supplied brine pH overrides the per-segment CO2-water correlation.
   */
  @Test
  void suppliedPHOverridesCorrelation() {
    double[] tC = { 60.0, 40.0 };
    double[] pBara = { 80.0, 60.0 };
    double[] vMs = { 3.0, 2.0 };

    PipeSegmentIntegrity integrity = new PipeSegmentIntegrity();
    integrity.setProfile(tC, pBara, vMs).setPipeAndGas(0.2, 0.02).setBrinePH(5.0);
    integrity.evaluate();

    for (PipeSegmentIntegrity.SegmentResult s : integrity.getSegments()) {
      assertEquals(5.0, s.getPH(), 1.0e-9, "supplied brine pH should be used for every segment");
    }
  }

  /**
   * Verifies that an empty profile is handled gracefully (no worst segment identified).
   */
  @Test
  void emptyProfileIsHandled() {
    PipeSegmentIntegrity integrity = new PipeSegmentIntegrity();
    integrity.evaluate();
    assertEquals(0, integrity.getSegments().size());
    assertEquals(-1, integrity.getWorstCorrosionIndex());
    assertEquals(-1, integrity.getWorstScaleIndex());
  }

  /**
   * Verifies that a run {@link PipeBeggsAndBrills} profile can be consumed directly, producing one integrity result per
   * pipe segment.
   */
  @Test
  void consumesLivePipeProfile() {
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
    pipe.setNumberOfIncrements(10);
    pipe.run();

    PipeSegmentIntegrity integrity = new PipeSegmentIntegrity();
    integrity.fromPipe(pipe).setPipeAndGas(0.2, 0.05).setBrineChemistry(1500.0, 400.0, 0.0, 0.0, 12000.0, 35000.0);
    integrity.evaluate();

    assertTrue(integrity.getSegments().size() > 0, "should produce one result per pipe segment");
    assertTrue(integrity.getWorstCorrosionIndex() >= 0, "a worst corrosion segment should be identified");
    assertNotNull(integrity.toJson());
    for (PipeSegmentIntegrity.SegmentResult seg : integrity.getSegments()) {
      assertTrue(seg.getCorrosionRateMmYr() >= 0.0);
    }
  }
}
