package neqsim.process.equipment.pipeline;

import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.closure.InterfacialFriction;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regression tests for the {@link TwoFluidPipe} energy balance and stratified interfacial friction.
 *
 * <p>
 * The steady energy balance of a flowing fluid is {@code dh = q - g dz}. Three defects broke it: the potential-energy
 * term was missing, so the hydrostatic part of the pressure drop on an uphill line was credited as Joule-Thomson
 * heating; a frozen-phase heat capacity omitted the latent heat of condensation, so a cooled two-phase line lost
 * temperature too fast; and the transient update split the overall U into two equal film coefficients in series, so the
 * steady state was not a fixed point of the transient. The stratified interfacial friction used the Wallis annular film
 * correlation {@code 1 + 75 alpha_L}, which multiplies the gas friction twenty-fold at a thirty per cent liquid
 * fraction.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
class TwoFluidPipeEnergyBalanceTest {
  /** Gravitational acceleration, in m/s2. */
  private static final double GRAVITY = 9.81;

  /**
   * Stabilised oil that stays single-phase liquid.
   *
   * @param temperatureC temperature in C
   * @param pressureBara pressure in bara
   * @return the fluid
   */
  private SystemInterface deadOil(double temperatureC, double pressureBara) {
    SystemInterface fluid = new SystemSrkEos(273.15 + temperatureC, pressureBara);
    fluid.addComponent("n-heptane", 30.0);
    fluid.addComponent("n-octane", 20.0);
    fluid.addComponent("nC10", 30.0);
    fluid.addComponent("nC12", 20.0);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /**
   * Rich gas condensate that is two-phase over the line.
   *
   * @param temperatureC temperature in C
   * @param pressureBara pressure in bara
   * @return the fluid
   */
  private SystemInterface gasCondensate(double temperatureC, double pressureBara) {
    SystemInterface fluid = new SystemSrkEos(273.15 + temperatureC, pressureBara);
    fluid.addComponent("methane", 82.0);
    fluid.addComponent("ethane", 6.5);
    fluid.addComponent("propane", 3.5);
    fluid.addComponent("n-butane", 2.0);
    fluid.addComponent("n-pentane", 1.5);
    fluid.addComponent("n-heptane", 2.0);
    fluid.addComponent("nC10", 2.5);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /**
   * Builds and runs a line with inlet flow and outlet pressure specified.
   *
   * @param fluid inlet fluid
   * @param massFlowKgS mass flow in kg/s
   * @param length length in m
   * @param rise elevation gain over the line in m
   * @param diameter inside diameter in m
   * @param uValue overall heat transfer coefficient in W/m2K, zero for adiabatic
   * @param sections number of sections
   * @return the pipe after the steady solve
   */
  private TwoFluidPipe runLine(SystemInterface fluid, double massFlowKgS, double length, double rise, double diameter,
      double uValue, int sections) {
    Stream stream = new Stream("feed", fluid);
    stream.setFlowRate(massFlowKgS, "kg/sec");
    stream.run();
    TwoFluidPipe pipe = new TwoFluidPipe("pipe", stream);
    pipe.setLength(length);
    pipe.setDiameter(diameter);
    pipe.setRoughness(4.5e-5);
    pipe.setNumberOfSections(sections);
    double[] faces = new double[sections + 1];
    for (int i = 0; i <= sections; i++) {
      faces[i] = rise * i / sections;
    }
    pipe.setCellFaceElevationProfile(faces);
    if (uValue > 0.0) {
      pipe.setHeatTransferCoefficient(uValue);
      pipe.setSurfaceTemperature(4.0, "C");
    }
    pipe.run();
    return pipe;
  }

  /**
   * Temperature reached by an isenthalpic-plus-work change of the inlet fluid.
   *
   * @param template fluid template with the inlet composition
   * @param pInBara inlet pressure in bara
   * @param tInK inlet temperature in K
   * @param pOutBara outlet pressure in bara
   * @param specificEnthalpyChange enthalpy added per kg, in J/kg
   * @return equilibrium outlet temperature in K
   */
  private double enthalpyTarget(SystemInterface template, double pInBara, double tInK, double pOutBara,
      double specificEnthalpyChange) {
    SystemInterface local = template.clone();
    local.setPressure(pInBara, "bara");
    local.setTemperature(tInK, "K");
    ThermodynamicOperations ops = new ThermodynamicOperations(local);
    ops.TPflash();
    local.init(2);
    double mass = local.getTotalNumberOfMoles() * local.getMolarMass();
    double target = local.getEnthalpy() + specificEnthalpyChange * mass;
    local.setPressure(pOutBara, "bara");
    ops.PHflash(target);
    return local.getTemperature("K");
  }

  @Test
  @DisplayName("adiabatic uphill liquid line satisfies dh = -g dz")
  void testUphillLiquidLineLosesPotentialEnergyNotGainsJouleThomsonHeat() {
    double rise = 300.0;
    SystemInterface fluid = deadOil(40.0, 60.0);
    TwoFluidPipe pipe = runLine(fluid, 40.0, 3000.0, rise, 0.20, 0.0, 30);
    Assertions.assertTrue(pipe.isSteadyStateConverged(), "steady solve must converge");
    double[] p = pipe.getPressureProfile();
    double[] t = pipe.getTemperatureProfile();
    double[] x = pipe.getPositionProfile();
    int last = p.length - 1;
    double dz = rise * (x[last] - x[0]) / 3000.0;
    double reference = enthalpyTarget(fluid, p[0] / 1.0e5, t[0], p[last] / 1.0e5, -GRAVITY * dz);
    Assertions.assertEquals(reference, t[last], 0.05,
        "outlet temperature must follow the equilibrium dh = -g dz between the first and last cell");
  }

  @Test
  @DisplayName("adiabatic two-phase line follows the equilibrium isenthalpic expansion")
  void testAdiabaticTwoPhaseExpansionIsIsenthalpic() {
    SystemInterface fluid = gasCondensate(40.0, 120.0);
    TwoFluidPipe pipe = runLine(fluid, 45.0, 20000.0, 0.0, 0.30, 0.0, 40);
    Assertions.assertTrue(pipe.isSteadyStateConverged(), "steady solve must converge");
    double[] p = pipe.getPressureProfile();
    double[] t = pipe.getTemperatureProfile();
    int last = p.length - 1;
    Assertions.assertTrue(p[0] - p[last] > 5.0e5, "the case must carry a real expansion");
    double reference = enthalpyTarget(fluid, p[0] / 1.0e5, t[0], p[last] / 1.0e5, 0.0);
    Assertions.assertEquals(reference, t[last], 0.3, "adiabatic outlet temperature must match the PH flash");
  }

  @Test
  @DisplayName("cooled two-phase line conserves energy with the latent heat included")
  void testCooledTwoPhaseLineConservesEnthalpy() {
    SystemInterface fluid = gasCondensate(60.0, 100.0);
    double massFlow = 30.0;
    double diameter = 0.30;
    double uValue = 10.0;
    TwoFluidPipe pipe = runLine(fluid, massFlow, 10000.0, 0.0, diameter, uValue, 50);
    double[] p = pipe.getPressureProfile();
    double[] t = pipe.getTemperatureProfile();
    double[] x = pipe.getPositionProfile();
    int last = p.length - 1;
    double duty = 0.0;
    for (int i = 1; i <= last; i++) {
      double tMean = 0.5 * (t[i] + t[i - 1]);
      duty += uValue * Math.PI * diameter * (x[i] - x[i - 1]) * (tMean - 277.15);
    }
    double reference = enthalpyTarget(fluid, p[0] / 1.0e5, t[0], p[last] / 1.0e5, -duty / massFlow);
    Assertions.assertTrue(t[0] - t[last] > 5.0, "the line must cool materially");
    Assertions.assertEquals(reference, t[last], 0.5,
        "outlet temperature must be consistent with the wall duty on an equilibrium enthalpy basis");
  }

  @Test
  @DisplayName("transient thermal update keeps the steady state as a fixed point")
  void testSteadyTemperatureIsAFixedPointOfTheTransient() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 40.0, 90.0);
    fluid.addComponent("methane", 0.92);
    fluid.addComponent("ethane", 0.06);
    fluid.addComponent("propane", 0.02);
    fluid.setMixingRule("classic");
    TwoFluidPipe pipe = runLine(fluid, 40.0, 10000.0, 0.0, 0.30, 5.0, 40);
    double steadyOutlet = pipe.getTemperatureProfile()[39];
    UUID id = UUID.randomUUID();
    for (int step = 0; step < 60; step++) {
      pipe.runTransient(60.0, id);
    }
    double transientOutlet = pipe.getTemperatureProfile()[39];
    Assertions.assertEquals(steadyOutlet, transientOutlet, 0.5,
        "constant boundaries must not move the outlet temperature after the steady handoff");
  }

  @Test
  @DisplayName("Andritsos-Hanratty wave enhancement uses the gas-density transition")
  void testAndritsosHanrattyEnhancement() {
    Assertions.assertEquals(1.0, InterfacialFriction.andritsosHanrattyEnhancement(4.0, 1.3, 0.25), 1e-12);
    Assertions.assertEquals(2.5, InterfacialFriction.andritsosHanrattyEnhancement(6.0, 1.3, 0.25), 1e-9);
    double transition = 5.0 * Math.sqrt(1.3 / 50.0);
    Assertions.assertEquals(1.0 + 15.0 * 0.5 * 0.2,
        InterfacialFriction.andritsosHanrattyEnhancement(1.2 * transition, 50.0, 0.25), 1e-9);
    Assertions.assertEquals(InterfacialFriction.ANDRITSOS_HANRATTY_MAXIMUM_ENHANCEMENT,
        InterfacialFriction.andritsosHanrattyEnhancement(1000.0, 50.0, 0.5), 1e-12);
  }
}
