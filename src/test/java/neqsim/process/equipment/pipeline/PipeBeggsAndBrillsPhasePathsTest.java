package neqsim.process.equipment.pipeline;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regression tests for the phase-count code paths of {@link PipeBeggsAndBrills}.
 *
 * <p>
 * The Beggs and Brill correlation is a gas-liquid correlation and the implementation assumes phase 0 is the gas
 * whenever the stream has more than one phase. Two defects followed from that:
 * </p>
 *
 * <table>
 * <caption>Defects covered by these tests</caption>
 * <tr>
 * <th>Defect</th>
 * <th>Symptom</th>
 * </tr>
 * <tr>
 * <td>A gas-free stream that split into oil and water was modelled as gas-liquid</td>
 * <td>The oil phase was used as the gas, giving a fictitious flow regime, a hold-up of 0.21 instead of 1, and a
 * pressure drop 44 per cent above the homogeneous liquid value</td>
 * </tr>
 * <tr>
 * <td>Three-phase liquid density combined on mass fractions</td>
 * <td>A mixture density is total mass over total volume, so the phase densities combine on volume fractions; the mass
 * form biased the liquid density high, by 1 per cent at low water cut and more as the water cut rises</td>
 * </tr>
 * </table>
 *
 * @author NeqSim
 * @version 1.0
 */
class PipeBeggsAndBrillsPhasePathsTest {
  /** Inside diameter used by every case, in m. */
  private static final double DIAMETER = 0.20;

  /** Pipe length used by every case, in m. */
  private static final double LENGTH = 1000.0;

  /** Wall roughness used by every case, in m. */
  private static final double ROUGHNESS = 2.0e-5;

  /** Mass flow used by every case, in kg/hr. */
  private static final double MASS_FLOW = 200000.0;

  /**
   * Builds a flashed stream from a CPA fluid with the requested composition.
   *
   * @param pressureBara pressure in bara, must be positive
   * @param temperatureC temperature in degrees Celsius
   * @param moleFractionMethane methane mole fraction, between 0 and 1
   * @param moleFractionDecane n-decane mole fraction, between 0 and 1
   * @param moleFractionWater water mole fraction, between 0 and 1
   * @return a stream whose fluid has been flashed and had its properties initialised
   */
  private Stream buildStream(double pressureBara, double temperatureC, double moleFractionMethane,
      double moleFractionDecane, double moleFractionWater) {
    SystemInterface fluid = new SystemSrkCPAstatoil(273.15 + temperatureC, pressureBara);
    if (moleFractionMethane > 0.0) {
      fluid.addComponent("methane", moleFractionMethane);
    }
    fluid.addComponent("nC10", moleFractionDecane);
    fluid.addComponent("water", moleFractionWater);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    fluid.setPressure(pressureBara, "bara");
    fluid.setTemperature(temperatureC, "C");
    fluid.setTotalFlowRate(MASS_FLOW, "kg/hr");
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();

    Stream stream = new Stream("feed", fluid);
    stream.setFlowRate(MASS_FLOW, "kg/hr");
    stream.setPressure(pressureBara, "bara");
    stream.setTemperature(temperatureC, "C");
    stream.run();
    return stream;
  }

  /**
   * Runs a horizontal single-increment pipe on the given stream.
   *
   * @param stream the inlet stream, must not be null
   * @return the pipe after it has been run
   */
  private PipeBeggsAndBrills runPipe(Stream stream) {
    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("segment", stream);
    pipe.setLength(LENGTH);
    pipe.setDiameter(DIAMETER);
    pipe.setPipeWallRoughness(ROUGHNESS);
    pipe.setElevation(0.0);
    pipe.setNumberOfIncrements(1);
    pipe.setRunIsothermal(true);
    pipe.run();
    return pipe;
  }

  @Test
  @DisplayName("A gas-free oil and water stream is carried as a homogeneous liquid")
  void testLiquidLiquidStreamIsNotTreatedAsGasLiquid() {
    Stream stream = buildStream(150.0, 40.0, 0.0, 0.6, 0.4);
    SystemInterface fluid = stream.getFluid();
    Assertions.assertEquals(2, fluid.getNumberOfPhases(), "expected an oil and an aqueous phase");
    Assertions.assertFalse(fluid.hasPhaseType("gas"), "the test fluid must not contain a gas phase");

    PipeBeggsAndBrills pipe = runPipe(stream);

    // Before the fix the oil phase was used as the gas: the regime came out as
    // TRANSITION with a hold-up of 0.207 and a pressure drop 44 per cent high.
    Assertions.assertEquals(PipeBeggsAndBrills.FlowRegime.SINGLE_PHASE, pipe.getSegmentFlowRegime(0));
    Assertions.assertEquals(1.0, pipe.getSegmentLiquidHoldup(0), 1.0e-9,
        "a pipe running full of liquid has a hold-up of one");

    // Volume-weighted mixture density of the two liquid phases.
    double volumeOil = fluid.getPhase(0).getFlowRate("m3/sec");
    double volumeWater = fluid.getPhase(1).getFlowRate("m3/sec");
    double expectedDensity = (fluid.getPhase(0).getDensity("kg/m3") * volumeOil
        + fluid.getPhase(1).getDensity("kg/m3") * volumeWater) / (volumeOil + volumeWater);
    Assertions.assertEquals(expectedDensity, pipe.getMixtureDensityProfile().get(0), 1.0,
        "the homogeneous liquid density must be volume weighted");
  }

  @Test
  @DisplayName("Three-phase liquid density is the volume-weighted mixture density")
  void testThreePhaseLiquidDensityIsVolumeWeighted() {
    Stream stream = buildStream(60.0, 40.0, 0.50, 0.30, 0.20);
    SystemInterface fluid = stream.getFluid();
    Assertions.assertEquals(3, fluid.getNumberOfPhases(), "expected gas, oil and aqueous phases");

    PipeBeggsAndBrills pipe = runPipe(stream);

    double massOil = fluid.getPhase(1).getFlowRate("kg/hr");
    double massWater = fluid.getPhase(2).getFlowRate("kg/hr");
    double volumeOil = fluid.getPhase(1).getFlowRate("m3/sec");
    double volumeWater = fluid.getPhase(2).getFlowRate("m3/sec");
    double expected = (massOil + massWater) / 3600.0 / (volumeOil + volumeWater);

    // The mass-weighted form that was used before gives 697.3 kg/m3 here against a
    // true mixture density of 690.0 kg/m3.
    double massWeighted = fluid.getPhase(1).getDensity("kg/m3") * massOil / (massOil + massWater)
        + fluid.getPhase(2).getDensity("kg/m3") * massWater / (massOil + massWater);
    Assertions.assertTrue(Math.abs(massWeighted - expected) > 1.0,
        "the test state must actually distinguish the two mixing conventions");

    Assertions.assertEquals(expected, pipe.getLiquidDensityProfile().get(0), 0.5,
        "liquid mixture density must be total mass over total volume");
  }
}
