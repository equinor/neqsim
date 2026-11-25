package neqsim.process.equipment.pipeline;

import java.lang.reflect.Method;
import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import neqsim.process.equipment.pipeline.TwoFluidMassBalanceReport.Phase;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TimeIntegrator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Annular wall friction charged at the flowing mixture density, against OLGA 2025.1.0.
 *
 * <p>
 * Reference line: 15.5 km, 285.7 mm, 4.5e-5 m roughness, falling 5.5 m, followed by a 150 m riser rising 121 m; 90 C
 * feed, 13 bara at the riser top, U = 2 W/m2K, 10 C ambient; SRK rich gas of 78/7/4/2/1 mol methane to n-pentane with
 * 2/3/2 n-heptane/n-decane/n-hexadecane and 4 water, 37 kg/s, about 38 per cent liquid by mass. OLGA needs 49.71 bara
 * at the inlet on 164 sections. With the hold-up weighted mixture density the same line needs 59.3 bara here, with the
 * flowing density 47.2 bara, both on 78 sections; the test uses 39 sections to stay short.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
class TwoFluidPipeAnnularFlowingFrictionTest {
  private static final double DIAMETER = 0.2857;
  private static final double FLOWLINE_LENGTH = 15500.0;
  private static final double LENGTH = 15650.0;
  private static final double OUTLET_PRESSURE = 13.0;
  private static final double OLGA_INLET_PRESSURE = 49.71;
  private static final int SECTIONS = 39;

  /**
   * Solves the reference line for the inlet pressure.
   *
   * @param flowingDensityFriction true to charge annular friction at the flowing density
   * @return the solved pipe
   */
  private static TwoFluidPipe solveRichGasLine(boolean flowingDensityFriction) {
    SystemInterface fluid = new SystemSrkEos(273.15 + 90.0, 50.0);
    fluid.addComponent("methane", 78.0);
    fluid.addComponent("ethane", 7.0);
    fluid.addComponent("propane", 4.0);
    fluid.addComponent("n-butane", 2.0);
    fluid.addComponent("n-pentane", 1.0);
    fluid.addComponent("n-heptane", 2.0);
    fluid.addComponent("nC10", 3.0);
    fluid.addComponent("nC16", 2.0);
    fluid.addComponent("water", 4.0);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(37.0, "kg/sec");
    feed.setTemperature(90.0, "C");
    feed.setPressure(50.0, "bara");
    feed.run();

    double[] elevations = new double[SECTIONS + 1];
    for (int node = 0; node <= SECTIONS; node++) {
      double position = LENGTH * node / SECTIONS;
      elevations[node] = position <= FLOWLINE_LENGTH ? -5.5 * position / FLOWLINE_LENGTH
          : -5.5 + 121.0 * (position - FLOWLINE_LENGTH) / (LENGTH - FLOWLINE_LENGTH);
    }

    TwoFluidPipe pipe = new TwoFluidPipe("rich gas line", feed);
    pipe.setLength(LENGTH);
    pipe.setDiameter(DIAMETER);
    pipe.setRoughness(4.5e-5);
    pipe.setNumberOfSections(SECTIONS);
    pipe.setElevationProfile(elevations);
    pipe.setIncludeEnergyEquation(true);
    pipe.setHeatTransferCoefficient(2.0);
    pipe.setSurfaceTemperature(10.0, "C");
    pipe.setOutletPressure(OUTLET_PRESSURE, "bara");
    pipe.setAnnularFlowingDensityFriction(flowingDensityFriction);
    pipe.run();
    Assertions.assertTrue(pipe.isSteadyStateConverged(), "the reference line must converge");
    Assertions.assertFalse(pipe.isSteadyStatePressureFloorLimited(), "the reference line must be deliverable");
    return pipe;
  }

  /**
   * Builds a level annular section with the given hold-up and phase velocities.
   *
   * @param liquidHoldup liquid hold-up
   * @param gasVelocity gas velocity in m/s
   * @param liquidVelocity liquid velocity in m/s
   * @param regime flow regime of the section
   * @return the section
   */
  private static TwoFluidSection section(double liquidHoldup, double gasVelocity, double liquidVelocity,
      FlowRegime regime) {
    TwoFluidSection section = new TwoFluidSection(0.0, 1.0, DIAMETER, 0.0);
    section.setGasDensity(30.0);
    section.setLiquidDensity(700.0);
    section.setGasViscosity(1.3e-5);
    section.setLiquidViscosity(4.0e-4);
    section.setSurfaceTension(0.012);
    section.setLiquidHoldup(liquidHoldup);
    section.setGasHoldup(1.0 - liquidHoldup);
    section.setGasVelocity(gasVelocity);
    section.setLiquidVelocity(liquidVelocity);
    section.setFlowRegime(regime);
    return section;
  }

  /**
   * Evaluates the steady pressure gradient of one section.
   *
   * @param flowingDensityFriction true to charge annular friction at the flowing density
   * @param section section to evaluate
   * @return pressure gradient in Pa/m
   * @throws Exception if the closure cannot be reached
   */
  private static double gradient(boolean flowingDensityFriction, TwoFluidSection section) throws Exception {
    TwoFluidPipe pipe = new TwoFluidPipe("closure");
    pipe.setDiameter(DIAMETER);
    pipe.setRoughness(4.5e-5);
    pipe.setAnnularFlowingDensityFriction(flowingDensityFriction);
    Method closure = TwoFluidPipe.class.getDeclaredMethod("estimatePressureGradient", TwoFluidSection.class);
    closure.setAccessible(true);
    return (Double) closure.invoke(pipe, section);
  }

  /**
   * The annular friction of a slow film equals the mixture friction of the same flow rates without slip.
   *
   * @throws Exception if the closure cannot be reached
   */
  @Test
  void annularFrictionWithSlipEqualsTheNoSlipMixtureAtTheSameFlowRates() throws Exception {
    // 10 m/s gas and 0.3 m/s liquid superficial velocity, once with a slow film and once without slip.
    double gasSuperficial = 10.0;
    double liquidSuperficial = 0.3;
    double filmHoldup = 0.08;
    double noSlipHoldup = liquidSuperficial / (gasSuperficial + liquidSuperficial);
    double mixtureVelocity = gasSuperficial + liquidSuperficial;
    TwoFluidSection slip = section(filmHoldup, gasSuperficial / (1.0 - filmHoldup), liquidSuperficial / filmHoldup,
        FlowRegime.ANNULAR);
    TwoFluidSection noSlip = section(noSlipHoldup, mixtureVelocity, mixtureVelocity, FlowRegime.ANNULAR);

    double homogeneous = gradient(false, noSlip);
    Assertions.assertEquals(homogeneous, gradient(true, noSlip), 1.0e-9 * homogeneous,
        "without slip the flowing density is the hold-up weighted density");
    Assertions.assertEquals(homogeneous, gradient(true, slip), 1.0e-9 * homogeneous,
        "a slow film must not add friction beyond the mass it transports");
    Assertions.assertTrue(gradient(false, slip) > 1.5 * homogeneous,
        "the hold-up weighted density charges the film at the mixture velocity");
  }

  /**
   * Stratified sections keep their per-phase wall shear.
   *
   * @throws Exception if the closure cannot be reached
   */
  @Test
  void stratifiedFrictionIsNotChanged() throws Exception {
    TwoFluidSection stratified = section(0.08, 10.0 / 0.92, 0.3 / 0.08, FlowRegime.STRATIFIED_WAVY);
    Assertions.assertEquals(gradient(false, stratified), gradient(true, stratified), 0.0);
  }

  /** The solved inlet pressure of the rich gas line is within 8 per cent of OLGA, the earlier form is not. */
  @Test
  void richGasLineInletPressureFollowsOlga() {
    Assertions.assertFalse(new TwoFluidPipe("default").isAnnularFlowingDensityFriction());
    double flowing = solveRichGasLine(true).getPressureProfile()[0] / 1.0e5;
    double holdupWeighted = solveRichGasLine(false).getPressureProfile()[0] / 1.0e5;
    Assertions.assertEquals(OLGA_INLET_PRESSURE, flowing, 0.08 * OLGA_INLET_PRESSURE);
    Assertions.assertTrue(holdupWeighted > 1.12 * OLGA_INLET_PRESSURE,
        "the hold-up weighted density over-predicts this line, got " + holdupWeighted + " bara");
  }

  /** Compare the first physical second at fixed boundaries on the 39-cell lean line and rich riser. */
  @Test
  @Tag("slow")
  void coupledImexPressureProfileConvergesAcrossReportingIntervals() {
    for (TwoFluidPipe steady : new TwoFluidPipe[] {TwoFluidPipeFeedSplitPressureTest.solve(114.0, false, SECTIONS),
        solveRichGasLine(true)}) {
      double[] initial = steady.getPressureProfile();
      double[] coarse = firstSecondPressures(SerializationUtils.clone(steady), 1);
      double[] fine = firstSecondPressures(steady, 10);
      for (int cell = 0; cell < initial.length; cell++) {
        Assertions.assertEquals(fine[cell], coarse[cell], 1000.0, steady.getName() + " interval spread (Pa)");
        Assertions.assertEquals(initial[cell], coarse[cell], 10000.0, steady.getName() + " startup drift (Pa)");
      }
    }
  }

  /**
   * Replay a fixed-boundary hand-off for one second with coupled pressure, energy, and drag-limited IMEX steps.
   *
   * @param pipe initialized steady pipe
   * @param intervals number of reporting intervals in the first second
   * @return final cell pressures in Pa
   */
  private static double[] firstSecondPressures(TwoFluidPipe pipe, int intervals) {
    pipe.setTimeIntegrationMethod(TimeIntegrator.Method.IMEX_PRESSURE_CORRECTION);
    pipe.setEnableInterfacialPressure(true);
    pipe.setEnableCoupledPressureMomentum(true);
    for (int i = 0; i < intervals; i++) {
      pipe.runTransient(1.0 / intervals, null);
      for (Phase phase : new Phase[] {Phase.GAS, Phase.OIL, Phase.WATER}) {
        Assertions.assertEquals(0.0, pipe.getLastMassBalanceReport().getResidualKg(phase), 1.0e-8);
      }
    }
    Assertions.assertEquals(1.0, pipe.getSimulationTime(), 1.0e-12);
    Assertions.assertFalse(pipe.isTransientCoupledPressureMomentumFailureDetected());
    Assertions.assertFalse(pipe.isTransientCoupledPressureMomentumCorrectionLimited());
    return pipe.getPressureProfile();
  }

  /** Fixed-boundary, 60 s hand-off on the coupled momentum and energy path without steady momentum calibration. */
  @Test
  @Tag("slow")
  void coupledEnergyReplayConservesPhaseMassWithFlowingFriction() {
    TwoFluidPipe pipe = solveRichGasLine(true);
    pipe.setEnableCoupledPressureMomentum(true);
    pipe.setEnableInterfacialPressure(true);
    for (int step = 0; step < 240; step++) {
      pipe.runTransient(0.25, null);
      TwoFluidMassBalanceReport balance = pipe.getLastMassBalanceReport();
      Assertions.assertEquals(37.0 * 0.25, balance.getInletMassKg(Phase.TOTAL), 1.0e-9);
      for (Phase phase : new Phase[] {Phase.GAS, Phase.OIL, Phase.WATER}) {
        Assertions.assertEquals(0.0, balance.getResidualKg(phase), 1.0e-8);
      }
    }
    Assertions.assertFalse(pipe.isTransientCoupledPressureMomentumFailureDetected());
    Assertions.assertFalse(pipe.isTransientCoupledPressureMomentumCorrectionLimited());
  }
}
