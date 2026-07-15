package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for first-class inlet-guide-vane (IGV) control on a fixed-speed {@link Compressor}.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class CompressorInletGuideVaneTest {

  /**
   * Build a run gas stream.
   *
   * @param pressure inlet pressure in bara
   * @return a run {@link Stream}
   */
  private Stream feed(double pressure) {
    SystemSrkEos gas = new SystemSrkEos(273.15 + 40.0, pressure);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.07);
    gas.addComponent("propane", 0.03);
    gas.setMixingRule("classic");
    Stream stream = new Stream("feed", gas);
    stream.setFlowRate(8000.0, "kg/hr");
    stream.setTemperature(40.0, "C");
    stream.setPressure(pressure, "bara");
    stream.run();
    return stream;
  }

  /**
   * Build a charted, fixed-speed compressor.
   *
   * @return a charted {@link Compressor} run at its design point
   */
  private Compressor chartedCompressor() {
    Compressor comp = new Compressor("igv compressor", feed(20.0));
    comp.setOutletPressure(45.0);
    comp.setUsePolytropicCalc(true);
    comp.setPolytropicEfficiency(0.78);
    comp.run();
    comp.generateCompressorChart("normal curves", 5);
    comp.getCompressorChart().setUseCompressorChart(true);
    comp.setUsePolytropicCalc(true);
    comp.setSolveSpeed(false);
    return comp;
  }

  /**
   * The IGV model multipliers are the identity at fully open and reduce head, efficiency and surge flow as the vanes
   * close.
   */
  @Test
  void testIgvModelMonotonic() {
    InletGuideVaneModel igv = new InletGuideVaneModel();
    assertEquals(1.0, igv.headMultiplier(1.0), 1e-9);
    assertEquals(0.0, igv.efficiencyDelta(1.0), 1e-9);
    assertEquals(1.0, igv.surgeFlowMultiplier(1.0), 1e-9);

    assertTrue(igv.headMultiplier(0.7) < 1.0);
    assertTrue(igv.efficiencyDelta(0.7) < 0.0);
    assertTrue(igv.surgeFlowMultiplier(0.7) < 1.0); // surge flow drops -> surge line moves left
  }

  /**
   * A fully-open IGV leaves the fixed-speed discharge unchanged; closing the vanes lowers the discharge.
   */
  @Test
  void testFixedSpeedDischargeDropsWhenIgvCloses() {
    Compressor comp = chartedCompressor();
    double rated = comp.getSpeed();
    comp.setSpeed(rated);

    comp.setInletGuideVaneOpening(1.0);
    comp.run();
    double pOpen = comp.getOutletStream().getPressure("bara");

    comp.setInletGuideVaneOpening(0.8);
    comp.run();
    double pClosed = comp.getOutletStream().getPressure("bara");

    assertTrue(pClosed < pOpen - 0.5,
        "closing IGV at fixed speed should lower the discharge (" + pClosed + " vs " + pOpen + ")");
    // Speed is unchanged — IGV is a separate control from speed.
    assertEquals(rated, comp.getSpeed(), 1e-6);
  }

  /**
   * Closing the IGV lowers the surge flow, so at the same operating flow the distance to surge increases (more turndown
   * headroom).
   */
  @Test
  void testIgvLowersSurgeFlow() {
    Compressor comp = chartedCompressor();
    comp.setSpeed(comp.getSpeed());

    comp.setInletGuideVaneOpening(1.0);
    comp.run();
    double surgeOpen = comp.getSurgeFlowRate();
    double d2sOpen = comp.getDistanceToSurge();

    comp.setInletGuideVaneOpening(0.7);
    comp.run();
    double surgeClosed = comp.getSurgeFlowRate();
    double d2sClosed = comp.getDistanceToSurge();

    assertTrue(surgeClosed < surgeOpen, "closing IGV should lower the surge flow");
    assertTrue(d2sClosed > d2sOpen, "closing IGV should increase the distance to surge at fixed flow");
  }

  /**
   * Setting a guide-vane angle maps to an opening via the model, and fully-open angle leaves the machine unchanged.
   */
  @Test
  void testGuideVaneAngleMapping() {
    Compressor comp = chartedCompressor();
    comp.setGuideVaneAngle(0.0); // fully open (default open angle)
    assertEquals(1.0, comp.getInletGuideVaneOpening(), 1e-9);
    comp.setGuideVaneAngle(60.0); // fully closed (default closed angle)
    assertEquals(0.0, comp.getInletGuideVaneOpening(), 1e-9);
  }
}
