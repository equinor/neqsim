package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Integration test: a three-stage oil/gas separation train whose low-pressure flash gas is boosted back to the
 * first-stage pressure by a TWO-body recompression string on ONE shaft (single common speed), then mixed with the inlet
 * (first-stage) separator gas into the export header.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class CompressorShaftSeparationTest {

  /** First-stage (inlet) separator pressure, bara. */
  private static final double P1 = 50.0;
  /** Second-stage separator pressure, bara. */
  private static final double P2 = 15.0;
  /** Third-stage separator pressure, bara. */
  private static final double P3 = 3.0;

  /**
   * Build a gas/oil reservoir fluid.
   *
   * @return an un-run {@link SystemSrkEos} at first-stage conditions
   */
  private SystemSrkEos oilGasFluid() {
    SystemSrkEos fluid = new SystemSrkEos(273.15 + 60.0, P1);
    fluid.addComponent("methane", 0.55);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-butane", 0.03);
    fluid.addComponent("n-pentane", 0.02);
    fluid.addComponent("n-hexane", 0.02);
    fluid.addComponent("nC10", 0.25);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /**
   * Three-stage separation with a shared-shaft, two-body recompression string; the recompressed gas rejoins the
   * first-stage gas in the export header. The single common speed is solved so the HP body reaches the first-stage
   * pressure while the intermediate pressure floats; the export mixer then sees matched pressures.
   */
  @Test
  void testThreeStageSeparationWithSharedShaftRecompression() {
    ProcessSystem process = new ProcessSystem();

    Stream feed = new Stream("well feed", oilGasFluid());
    feed.setFlowRate(50000.0, "kg/hr");
    feed.setTemperature(60.0, "C");
    feed.setPressure(P1, "bara");
    process.add(feed);

    Separator sep1 = new Separator("1st stage separator", feed);
    process.add(sep1);
    ThrottlingValve v12 = new ThrottlingValve("oil valve 1->2", sep1.getLiquidOutStream());
    v12.setOutletPressure(P2);
    process.add(v12);
    Separator sep2 = new Separator("2nd stage separator", v12.getOutletStream());
    process.add(sep2);
    ThrottlingValve v23 = new ThrottlingValve("oil valve 2->3", sep2.getLiquidOutStream());
    v23.setOutletPressure(P3);
    process.add(v23);
    Separator sep3 = new Separator("3rd stage separator", v23.getOutletStream());
    process.add(sep3);

    // Two-body recompression string: rc1 (3 -> 15) then rc2 (15 -> 50). The 2nd-stage gas (15 bara) joins the rc2
    // suction. Both bodies sit on ONE gas-turbine shaft (single common speed).
    Compressor rc1 = new Compressor("recompressor rc1", sep3.getGasOutStream());
    rc1.setOutletPressure(P2);
    rc1.setUsePolytropicCalc(true);
    rc1.setPolytropicEfficiency(0.75);
    process.add(rc1);
    Mixer interstage = new Mixer("recompressor interstage (+2nd stage gas)");
    interstage.addStream(rc1.getOutletStream());
    interstage.addStream(sep2.getGasOutStream());
    process.add(interstage);
    Compressor rc2 = new Compressor("recompressor rc2", interstage.getOutletStream());
    rc2.setOutletPressure(P1);
    rc2.setUsePolytropicCalc(true);
    rc2.setPolytropicEfficiency(0.75);
    process.add(rc2);

    // Export header: recompressed gas (50 bara) mixed with the inlet-separator gas (50 bara).
    Mixer exportHeader = new Mixer("export gas header");
    exportHeader.addStream(rc2.getOutletStream());
    exportHeader.addStream(sep1.getGasOutStream());
    process.add(exportHeader);
    Stream exportGas = new Stream("export gas", exportHeader.getOutletStream());
    process.add(exportGas);

    process.run();

    // Attach a common-speed performance chart to each recompressor body, anchored at ONE common speed so both bodies
    // reach their design discharge at that speed.
    double commonSpeed = rc2.getSpeed();
    for (Compressor body : new Compressor[] { rc1, rc2 }) {
      body.setSpeed(commonSpeed);
      body.generateCompressorChart("normal curves", 5);
      body.getCompressorChart().setUseCompressorChart(true);
      body.setUsePolytropicCalc(true);
    }
    process.run();

    // Solve the ONE common shaft speed so the HP body reaches the first-stage pressure; intermediates float.
    final ProcessSystem proc = process;
    CompressorShaft shaft = new CompressorShaft("recompression shaft (single GT)");
    shaft.addCompressor(rc1);
    shaft.addCompressor(rc2);
    shaft.setSpeedBounds(commonSpeed * 0.6, commonSpeed * 1.6);
    shaft.setPressureTolerance(0.5);
    shaft.setMaxIterations(30);
    shaft.solveSpeed(rc2, P1, "bara", new Runnable() {
      @Override
      public void run() {
        proc.run();
      }
    });

    // ONE common speed applied to every body.
    double solvedSpeed = shaft.getSpeed();
    assertEquals(solvedSpeed, rc1.getSpeed(), 1e-6);
    assertEquals(solvedSpeed, rc2.getSpeed(), 1e-6);

    // The shaft reached the target and reports feasible.
    assertTrue(shaft.isFeasible(), "shaft should reach the first-stage pressure");
    assertEquals(CompressorShaft.SolveStatus.FEASIBLE, shaft.getLastSolveResult().getStatus());
    assertEquals(P1, rc2.getOutletStream().getPressure("bara"), 0.6);

    // Export header at first-stage pressure (matched inlets) and mass balance closes.
    assertEquals(P1, exportGas.getPressure("bara"), 0.6);
    double crude = sep3.getLiquidOutStream().getFlowRate("kg/hr");
    double exported = exportGas.getFlowRate("kg/hr");
    assertEquals(50000.0, crude + exported, 50000.0 * 0.01);
    assertTrue(exported > 0.0 && crude > 0.0);
  }
}
