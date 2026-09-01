package neqsim.process.equipment.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regression tests for {@link Stream#CCT(String)} and {@link Stream#CCB(String)}.
 *
 * <p>
 * Both methods used to trace the phase envelope with the no-argument {@code calcPTphaseEnvelope()} overload. That
 * overload fails to trace lean export gases carrying heavy pseudo-components at (near) zero moles. On failure
 * {@code PTphaseEnvelope} falls back to reporting the source fluid's own temperature and pressure, so the caller
 * received the stream conditions echoed back as if they were a cricondenpoint - a finite, plausible, badly wrong number
 * with no exception raised.
 * </p>
 *
 * <p>
 * The fluid below is a 17-component lean natural gas export stream at 147.0 bara and 86.301 degC. The three heaviest
 * pseudo-components carry (near) zero moles, which is what breaks the trace; dropping them lets even the old code
 * succeed, so they must be kept for this to remain a regression test.
 * </p>
 *
 * @author NeqSim
 * @version $Id: $Id
 */
public class StreamCricondenTest extends neqsim.NeqSimTest {
  /** Stream pressure in bara. */
  private static final double STREAM_PRESSURE_BARA = 147.0;
  /** Stream temperature in degrees Celsius. */
  private static final double STREAM_TEMPERATURE_C = 86.30090581135869;

  /** Stream variant that counts actual phase-envelope traces. */
  private static final class CountingStream extends Stream {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;
    /** Number of operations objects created for an envelope trace. */
    private int cricondenTraceCount = 0;

    /**
     * Create a counting stream.
     *
     * @param name stream name
     * @param fluid stream fluid
     */
    private CountingStream(String name, SystemInterface fluid) {
      super(name, fluid);
    }

    /** {@inheritDoc} */
    @Override
    ThermodynamicOperations createCricondenOperations(SystemInterface system) {
      cricondenTraceCount++;
      return super.createCricondenOperations(system);
    }

    /**
     * Get the number of actual envelope traces requested.
     *
     * @return trace count
     */
    private int getCricondenTraceCount() {
      return cricondenTraceCount;
    }
  }

  /**
   * Build the lean natural gas export stream that exposes the defect.
   *
   * <p>
   * Mole fractions are dimensionless. Pseudo-component molar masses are in kg/mol and normal liquid densities in g/cm3,
   * as required by {@code SystemInterface.addTBPfraction(String, double, double, double)}.
   * </p>
   *
   * @return a {@link Stream} that has been run inside a {@link ProcessSystem}
   */
  private Stream createExportGasStream() {
    SystemInterface gas = new SystemSrkEos(273.15 + STREAM_TEMPERATURE_C, STREAM_PRESSURE_BARA);
    gas.addComponent("nitrogen", 0.0055941222);
    gas.addComponent("CO2", 0.0139708366);
    gas.addComponent("methane", 0.8576211644);
    gas.addComponent("ethane", 0.0756375044);
    gas.addComponent("propane", 0.0325232685);
    gas.addTBPfraction("IC4", 0.0038799327, 0.058124, 0.69779737);
    gas.addComponent("n-butane", 0.0074317682);
    gas.addTBPfraction("IC5", 0.0012407802, 0.072151, 0.70487539);
    gas.addComponent("n-pentane", 0.0013491593);
    gas.addComponent("n-hexane", 0.0005140988);
    gas.addTBPfraction("F1", 0.0002056274, 0.09439190, 0.71609815);
    gas.addTBPfraction("F2", 0.0000305388, 0.10623860, 0.72207600);
    gas.addTBPfraction("F3", 0.0000011984, 0.12120060, 0.72962582);
    // The three heaviest cuts and the water component carry (near) zero moles. That is what
    // breaks the envelope trace, so it is the essential part of the regression.
    gas.addTBPfraction("F4", 0.000000000015, 0.16540890, 0.75193333);
    gas.addTBPfraction("F5", 0.0, 0.29306510, 0.81634865);
    gas.addTBPfraction("F6", 0.0, 0.58861730, 0.96548429);
    gas.addComponent("water", 0.0);
    gas.setMixingRule("classic");

    Stream exportGasStream = new Stream("export gas", gas);
    exportGasStream.setPressure(STREAM_PRESSURE_BARA, "bara");
    exportGasStream.setTemperature(STREAM_TEMPERATURE_C, "C");
    exportGasStream.setFlowRate(10.0, "MSm3/day");

    ProcessSystem processOps = new ProcessSystem();
    processOps.add(exportGasStream);
    processOps.run();
    return exportGasStream;
  }

  /**
   * The cricondentherm must not echo the stream conditions. This is the direct regression guard for the silent-failure
   * defect: before the fix this returned exactly 86.30090581135869 degC and 147.0 bara.
   */
  @Test
  @DisplayName("CCT does not return the stream temperature or pressure")
  public void testCricondenThermIsNotStreamState() {
    Stream exportGasStream = createExportGasStream();
    double cctC = exportGasStream.CCT("C");
    double cctBara = exportGasStream.CCT("bara");

    assertFalse(Double.isNaN(cctC), "CCT(\"C\") must resolve for this lean gas");
    assertFalse(Double.isNaN(cctBara), "CCT(\"bara\") must resolve for this lean gas");
    assertTrue(Math.abs(cctC - STREAM_TEMPERATURE_C) > 1.0, "CCT(\"C\") returned " + cctC
        + " degC, which echoes the stream temperature of " + STREAM_TEMPERATURE_C + " degC");
    assertTrue(Math.abs(cctBara - STREAM_PRESSURE_BARA) > 1.0, "CCT(\"bara\") returned " + cctBara
        + " bara, which echoes the stream pressure of " + STREAM_PRESSURE_BARA + " bara");
  }

  /**
   * The cricondenbar must not echo the stream conditions either.
   */
  @Test
  @DisplayName("CCB does not return the stream temperature or pressure")
  public void testCricondenBarIsNotStreamState() {
    Stream exportGasStream = createExportGasStream();
    double ccbC = exportGasStream.CCB("C");
    double ccbBara = exportGasStream.CCB("bara");

    assertFalse(Double.isNaN(ccbC), "CCB(\"C\") must resolve for this lean gas");
    assertFalse(Double.isNaN(ccbBara), "CCB(\"bara\") must resolve for this lean gas");
    assertTrue(Math.abs(ccbC - STREAM_TEMPERATURE_C) > 1.0, "CCB(\"C\") returned " + ccbC
        + " degC, which echoes the stream temperature of " + STREAM_TEMPERATURE_C + " degC");
    assertTrue(Math.abs(ccbBara - STREAM_PRESSURE_BARA) > 1.0, "CCB(\"bara\") returned " + ccbBara
        + " bara, which echoes the stream pressure of " + STREAM_PRESSURE_BARA + " bara");
  }

  /**
   * {@code CCT} and {@code CCB} must agree with the phase envelope they are derived from. Pinning to the envelope
   * rather than to a hard-coded literal keeps this a test of the accessor, not of the correlation set.
   */
  @Test
  @DisplayName("CCT and CCB agree with the traced phase envelope")
  public void testCricondenPointsMatchEnvelope() {
    Stream exportGasStream = createExportGasStream();

    SystemInterface localSyst = exportGasStream.getFluid().clone();
    ThermodynamicOperations ops = new ThermodynamicOperations(localSyst);
    ops.calcPTphaseEnvelope(true, 1.0);
    double[] cricondenTherm = ops.get("cricondentherm");
    double[] cricondenBar = ops.get("cricondenbar");

    assertEquals(cricondenTherm[0] - 273.15, exportGasStream.CCT("C"), 1.0e-6,
        "CCT(\"C\") must equal the envelope cricondentherm temperature");
    assertEquals(cricondenTherm[1], exportGasStream.CCT("bara"), 1.0e-6,
        "CCT(\"bara\") must equal the envelope cricondentherm pressure");
    assertEquals(cricondenBar[0] - 273.15, exportGasStream.CCB("C"), 1.0e-6,
        "CCB(\"C\") must equal the envelope cricondenbar temperature");
    assertEquals(cricondenBar[1], exportGasStream.CCB("bara"), 1.0e-6,
        "CCB(\"bara\") must equal the envelope cricondenbar pressure");
  }

  /**
   * By definition the cricondenbar is the maximum pressure on the envelope and the cricondentherm the maximum
   * temperature, so the two points must order consistently.
   */
  @Test
  @DisplayName("cricondenbar and cricondentherm are mutually consistent")
  public void testCricondenPointConsistency() {
    Stream exportGasStream = createExportGasStream();
    double cctC = exportGasStream.CCT("C");
    double cctBara = exportGasStream.CCT("bara");
    double ccbC = exportGasStream.CCB("C");
    double ccbBara = exportGasStream.CCB("bara");

    assertTrue(ccbBara >= cctBara,
        "cricondenbar pressure " + ccbBara + " bara must be at least the cricondentherm pressure " + cctBara + " bara");
    assertTrue(cctC >= ccbC,
        "cricondentherm temperature " + cctC + " degC must be at least the cricondenbar temperature " + ccbC + " degC");
  }

  /**
   * Unit contract: the {@code "C"} branch returns degrees Celsius and the default (unrecognised unit) branch returns
   * Kelvin.
   */
  @Test
  @DisplayName("CCT and CCB honour the K versus degC unit contract")
  public void testUnitContract() {
    Stream exportGasStream = createExportGasStream();
    assertEquals(273.15, exportGasStream.CCT("K") - exportGasStream.CCT("C"), 1.0e-6,
        "CCT(\"K\") minus CCT(\"C\") must be exactly 273.15");
    assertEquals(273.15, exportGasStream.CCB("K") - exportGasStream.CCB("C"), 1.0e-6,
        "CCB(\"K\") minus CCB(\"C\") must be exactly 273.15");
  }

  /**
   * One PT-envelope trace yields both cricondenpoints and every supported return unit. Repeated accessor calls for an
   * unchanged stream must therefore reuse that trace, while direct fluid-state and EOS-parameter mutations must
   * invalidate the cache.
   */
  @Test
  @DisplayName("CCT and CCB share one envelope trace and invalidate it after input changes")
  public void testCricondenEnvelopeCacheReuseAndInvalidation() {
    Stream exportGasStream = createExportGasStream();
    CountingStream countingStream = new CountingStream("counting export gas", exportGasStream.getFluid().clone());

    assertFalse(Double.isNaN(countingStream.CCT("C")));
    assertFalse(Double.isNaN(countingStream.CCT("bara")));
    assertFalse(Double.isNaN(countingStream.CCB("C")));
    assertFalse(Double.isNaN(countingStream.CCB("bara")));
    assertEquals(1, countingStream.getCricondenTraceCount(),
        "all criconden accessors for one unchanged state must share one phase-envelope trace");

    countingStream.setTemperature(STREAM_TEMPERATURE_C + 1.0, "C");
    countingStream.CCT("C");
    assertEquals(2, countingStream.getCricondenTraceCount(),
        "changing an input that seeds the envelope trace must invalidate the cache");

    countingStream.getFluid().setBinaryInteractionParameter(0, 1, 0.0123);
    countingStream.CCB("C");
    assertEquals(3, countingStream.getCricondenTraceCount(),
        "changing an EOS binary-interaction parameter must invalidate the cache");
  }
}
