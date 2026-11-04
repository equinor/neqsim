package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/**
 * Signed inlet transport must select one physical donor per phase.
 *
 * @author NeqSim
 * @version 1.0
 */
class TwoFluidInletBoundaryTest {
  /**
   * Verifies external donors for inflow, cell donors for outflow, and interior pressure traction.
   *
   * @throws Exception when reflective access to the private flux evaluator fails
   */
  @Test
  void countercurrentPhasesUseUpwindDonorsAndInteriorPressureTraction() throws Exception {
    TwoFluidSection interior = section(0.5, 0.3, 0.2, 5.0, 800.0, 1000.0, 2.0e5, 3.0e5, 4.0e5);
    TwoFluidSection boundary = section(0.2, 0.3, 0.5, 7.0, 720.0, 980.0, 8.0e5, 9.0e5, 1.0e6);
    boundary.setGasVelocity(0.8);
    boundary.setOilVelocity(-0.1);
    boundary.setWaterVelocity(0.03);

    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    equations.setIncludeEnergyEquation(true);
    equations.setInletPhaseFlowBoundaryState(boundary);
    double facePressure = 2.4e5;
    double[] flux = inletFlux(equations, interior, facePressure);
    double area = interior.getArea();

    double gasMass = 0.2 * 7.0 * 0.8 * area;
    double oilMass = 0.3 * 800.0 * -0.1 * area;
    double waterMass = 0.5 * 980.0 * 0.03 * area;
    assertEquals(gasMass, flux[0], 1.0e-12);
    assertEquals(oilMass, flux[1], 1.0e-12);
    assertEquals(waterMass, flux[2], 1.0e-12);

    assertEquals(0.2 * 7.0 * 0.8 * 0.8 * area + 0.5 * facePressure * area, flux[3], 1.0e-9);
    assertEquals(0.3 * 800.0 * 0.1 * 0.1 * area + 0.3 * facePressure * area, flux[4], 1.0e-9);
    assertEquals(0.5 * 980.0 * 0.03 * 0.03 * area + 0.2 * facePressure * area, flux[5], 1.0e-9);

    double[][] phaseEnergy = equations.getLastBoundaryPhaseEnergyFluxes();
    double expectedGasEnergy = gasMass * (8.0e5 + 0.5 * 0.8 * 0.8);
    double expectedOilEnergy = oilMass * (3.0e5 + 0.5 * 0.1 * 0.1);
    double expectedWaterEnergy = waterMass * (1.0e6 + 0.5 * 0.03 * 0.03);
    assertEquals(expectedGasEnergy, phaseEnergy[0][0], 1.0e-9);
    assertEquals(expectedOilEnergy, phaseEnergy[0][1], 1.0e-9);
    assertEquals(expectedWaterEnergy, phaseEnergy[0][2], 1.0e-9);
    assertEquals(expectedGasEnergy + expectedOilEnergy + expectedWaterEnergy, flux[6], 1.0e-9);

    double[][] phaseMomentum = equations.getLastBoundaryPhaseMomentumFluxes();
    double[][] pressureForces = equations.getLastBoundaryPhasePressureForces();
    assertEquals(flux[3], phaseMomentum[0][0], 1.0e-12);
    assertEquals(flux[4], phaseMomentum[0][1], 1.0e-12);
    assertEquals(flux[5], phaseMomentum[0][2], 1.0e-12);
    assertEquals(0.5 * facePressure * area, pressureForces[0][0], 1.0e-12);
    assertEquals(0.3 * facePressure * area, pressureForces[0][1], 1.0e-12);
    assertEquals(0.2 * facePressure * area, pressureForces[0][2], 1.0e-12);
  }

  /**
   * Verifies independent external gas/liquid fallback donors and interior outlet pressure traction.
   *
   * @throws Exception when reflective access to the private flux evaluator fails
   */
  @Test
  void reversedOutletPhasesUseExternalDonorsButInteriorPressureTraction() throws Exception {
    TwoFluidSection interior = section(0.6, 0.3, 0.1, 4.0, 810.0, 1010.0, 2.0e5, 3.0e5, 4.0e5);
    interior.setGasVelocity(-0.4);
    interior.setOilVelocity(-0.08);
    interior.setWaterVelocity(0.02);
    TwoFluidSection donor = section(0.25, 0.55, 0.2, 8.0, 740.0, 985.0, 7.0e5, 8.0e5, 9.0e5);

    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    equations.setIncludeEnergyEquation(true);
    equations.setAllowOutletPhaseBackflow(true);
    equations.setOutletBoundaryState(donor);
    double facePressure = 1.8e5;
    double[] flux = outletFlux(equations, interior, facePressure);
    double area = interior.getArea();

    double gasMass = 0.25 * 8.0 * -0.4 * area;
    double oilMass = 0.55 * 740.0 * -0.08 * area;
    double waterMass = 0.1 * 1010.0 * 0.02 * area;
    assertEquals(gasMass, flux[0], 1.0e-12);
    assertEquals(oilMass, flux[1], 1.0e-12);
    assertEquals(waterMass, flux[2], 1.0e-12);

    double[][] phaseMomentum = equations.getLastBoundaryPhaseMomentumFluxes();
    double[][] pressureForces = equations.getLastBoundaryPhasePressureForces();
    assertEquals(0.25 * 8.0 * 0.4 * 0.4 * area + 0.6 * facePressure * area, phaseMomentum[1][0], 1.0e-9);
    assertEquals(0.55 * 740.0 * 0.08 * 0.08 * area + 0.3 * facePressure * area, phaseMomentum[1][1], 1.0e-9);
    assertEquals(0.1 * 1010.0 * 0.02 * 0.02 * area + 0.1 * facePressure * area, phaseMomentum[1][2], 1.0e-9);
    assertEquals(0.6 * facePressure * area, pressureForces[1][0], 1.0e-9);
    assertEquals(0.3 * facePressure * area, pressureForces[1][1], 1.0e-9);
    assertEquals(0.1 * facePressure * area, pressureForces[1][2], 1.0e-9);

    double[][] phaseEnergy = equations.getLastBoundaryPhaseEnergyFluxes();
    assertEquals(gasMass * (7.0e5 + 0.5 * 0.4 * 0.4), phaseEnergy[1][0], 1.0e-8);
    assertEquals(oilMass * (8.0e5 + 0.5 * 0.08 * 0.08), phaseEnergy[1][1], 1.0e-8);
    assertEquals(waterMass * (4.0e5 + 0.5 * 0.02 * 0.02), phaseEnergy[1][2], 1.0e-8);
  }

  /**
   * Evaluate the private inlet flux as a focused unit-test seam.
   *
   * @param equations conservation equations
   * @param interior first physical cell
   * @param pressure independent inlet-face pressure in Pa
   * @return conservative inlet flux vector
   * @throws Exception when reflective lookup or invocation fails
   */
  private static double[] inletFlux(TwoFluidConservationEquations equations, TwoFluidSection interior, double pressure)
      throws Exception {
    Method method = TwoFluidConservationEquations.class.getDeclaredMethod("calcInletFlux", TwoFluidSection.class,
        double.class);
    method.setAccessible(true);
    return (double[]) method.invoke(equations, interior, pressure);
  }

  /**
   * Evaluate the private outlet flux as a focused unit-test seam.
   *
   * @param equations conservation equations
   * @param interior last physical cell
   * @param pressure independent outlet-face pressure in Pa
   * @return conservative outlet flux vector
   * @throws Exception when reflective lookup or invocation fails
   */
  private static double[] outletFlux(TwoFluidConservationEquations equations, TwoFluidSection interior, double pressure)
      throws Exception {
    Method method = TwoFluidConservationEquations.class.getDeclaredMethod("calcOutletFlux", TwoFluidSection.class,
        double.class);
    method.setAccessible(true);
    return (double[]) method.invoke(equations, interior, pressure);
  }

  /**
   * Create an initialized three-phase section for boundary donor tests.
   *
   * @param gas gas holdup
   * @param oil oil holdup
   * @param water water holdup
   * @param gasDensity gas density in kg/m3
   * @param oilDensity oil density in kg/m3
   * @param waterDensity water density in kg/m3
   * @param gasEnthalpy gas enthalpy in J/kg
   * @param oilEnthalpy oil enthalpy in J/kg
   * @param waterEnthalpy water enthalpy in J/kg
   * @return initialized section
   */
  private static TwoFluidSection section(double gas, double oil, double water, double gasDensity, double oilDensity,
      double waterDensity, double gasEnthalpy, double oilEnthalpy, double waterEnthalpy) {
    TwoFluidSection section = new TwoFluidSection(0.5, 1.0, 1.0, 0.0);
    section.setPressure(2.0e5);
    section.setGasDensity(gasDensity);
    section.setOilDensity(oilDensity);
    section.setWaterDensity(waterDensity);
    section.setGasHoldup(gas);
    section.setOilHoldup(oil);
    section.setWaterHoldup(water);
    section.setLiquidHoldup(oil + water);
    section.setWaterCut(water / (oil + water));
    section.setGasEnthalpy(gasEnthalpy);
    section.setPhaseEnthalpies(oilEnthalpy, waterEnthalpy);
    section.setGasVelocity(0.0);
    section.setOilVelocity(0.0);
    section.setWaterVelocity(0.0);
    section.updateConservativeVariables();
    return section;
  }
}
