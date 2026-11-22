package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Independent energy and relaxation checks for passive coupled fluid/radial heat exchange.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class MultilayerImplicitThermalReviewTest {
  /**
   * The reported inner-wall interface follows the instantaneous film drop rather than a steady ambient network.
   */
  @Test
  void innerSurfaceDiagnosticUsesTheAcceptedTransientFilmFlux() {
    MultilayerThermalCalculator calculator = new MultilayerThermalCalculator(0.1);
    calculator.addCustomLayer("resistive-wall", 0.05, 0.05, 1000.0, 1000.0).initializeTemperature(290.0);
    double[] temperatures = {290.0};

    double surface = TwoFluidPipe.advanceMultilayerCellThermalState(calculator, temperatures, 300.0, 280.0, 300.0,
        0.01);

    double filmConductance = 300.0 * 2.0 * Math.PI * 0.1;
    assertEquals(calculator.getLastFluidHeatTransferPerLength(), filmConductance * (300.0 - surface), 1.0e-9);
    assertTrue(surface > temperatures[0] && surface < 300.0,
        "A resistive wall has distinct inner-surface and layer-node temperatures during heating");
  }

  /**
   * The long-time radial heat flux must recover the independently configured cylindrical resistance network.
   */
  @Test
  void longTimeRadialFluxRecoversTheConfiguredSteadyCylindricalResistance() {
    for (boolean multipleLayers : new boolean[] {false, true}) {
      MultilayerThermalCalculator calculator = new MultilayerThermalCalculator(0.1);
      calculator.addCustomLayer("low-conductivity-wall", 0.05, 0.05, 1000.0, 1000.0).initializeTemperature(300.0);
      if (multipleLayers) {
        calculator.addCustomLayer("outer-layer", 0.02, 0.25, 1000.0, 1000.0).initializeTemperature(300.0);
      }
      calculator.setInnerHTC(300.0);
      calculator.setOuterHTC(50.0);
      calculator.setFluidTemperature(350.0);
      calculator.setAmbientTemperature(280.0);
      double expected = calculator.calculateHeatLossPerLength();

      calculator.updateTransient(1.0e12);

      assertEquals(expected, calculator.getLastFluidHeatTransferPerLength(), 1.0e-5,
          "Boundary and internal links must retain the complete cylindrical resistance network");
      assertEquals(expected, calculator.getLastAmbientHeatTransferPerLength(), 1.0e-5,
          "The transient ambient boundary must include the remaining half-layer resistance");
    }
  }

  /**
   * A zero-conductivity layer cannot conduct between its centre and either adjacent boundary.
   */
  @Test
  void zeroConductivityMakesTheTransientLayerInsulating() {
    MultilayerThermalCalculator calculator = new MultilayerThermalCalculator(0.1);
    RadialThermalLayer layer = calculator.addCustomLayer("insulating", 0.02, 0.0, 1000.0, 1000.0);
    layer.initializeTemperature(300.0);
    calculator.setFluidTemperature(350.0);
    calculator.setAmbientTemperature(280.0);

    calculator.updateTransient(100.0);

    assertEquals(300.0, layer.getTemperature(), 1.0e-12);
    assertEquals(0.0, calculator.getLastFluidHeatTransferPerLength(), 0.0);
    assertEquals(0.0, calculator.getLastAmbientHeatTransferPerLength(), 0.0);
  }

  /**
   * A fluid with very little thermal inertia must remain bounded together with a thin wall and coating.
   */
  @Test
  void thinFluidAndRadialLayersStayBoundedAndConserveEnergy() {
    MultilayerThermalCalculator calculator = new MultilayerThermalCalculator(0.1);
    calculator.createBareSubseaPipe(0.2, 1.0e-4);
    calculator.setInnerHTC(5000.0);
    calculator.setOuterHTC(500.0);
    calculator.setFluidTemperature(300.0);
    calculator.setAmbientTemperature(280.0);
    for (RadialThermalLayer layer : calculator.getLayers()) {
      layer.initializeTemperature(280.0);
    }
    RadialThermalLayer firstLayer = calculator.getLayers().get(0);
    double capacity = 1.0;
    double initialEnergy = storedEnergy(calculator, capacity);
    double dt = 60.0;

    double fluid = calculator.advanceCoupledTransient(capacity, 0.0, dt);

    assertTrue(fluid >= 280.0 && fluid <= 300.0);
    for (RadialThermalLayer layer : calculator.getLayers()) {
      assertTrue(layer.getTemperature() >= 280.0 && layer.getTemperature() <= 300.0);
    }
    assertSame(firstLayer, calculator.getLayers().get(0), "A thermal advance must retain connected layer identity");
    double ambientEnergy = dt * calculator.getLastAmbientHeatTransferPerLength();
    assertEquals(0.0, storedEnergy(calculator, capacity) - initialEnergy + ambientEnergy, 1.0e-6);
    assertEquals(capacity * (300.0 - fluid) / dt, calculator.getLastFluidHeatTransferPerLength(), 1.0e-8,
        "The published inner flux must be the same flux used by the fluid energy update");
  }

  /**
   * Refining the step must approach the analytical two-capacity adiabatic exponential relaxation.
   */
  @Test
  void oneLayerAdiabaticRelaxationConvergesToTheAnalyticalSolution() {
    MultilayerThermalCalculator reference = oneLayerCalculator();
    double wallCapacity = reference.getLayers().get(0).getThermalMassPerLength();
    double fluidCapacity = 100.0;
    double conductance = 1.0
        / (1.0 / (1000.0 * 2.0 * Math.PI * 0.1) + 0.5 * reference.getLayers().get(0).getThermalResistance());
    double equilibrium = (fluidCapacity * 300.0 + wallCapacity * 280.0) / (fluidCapacity + wallCapacity);
    double horizon = 0.1;
    double expected = equilibrium
        + (300.0 - equilibrium) * Math.exp(-conductance * (1.0 / fluidCapacity + 1.0 / wallCapacity) * horizon);
    double coarse = Math.abs(relaxedFluidTemperature(4, horizon) - expected);
    double medium = Math.abs(relaxedFluidTemperature(8, horizon) - expected);
    double fine = Math.abs(relaxedFluidTemperature(16, horizon) - expected);

    assertTrue(medium < 0.65 * coarse, "Halving the step must reduce the analytical error");
    assertTrue(fine < 0.65 * medium, "A second halving must retain first-order convergence");
  }

  /**
   * The no-layer and disabled-wall-inertia limits must retain both ambient and explicit-source energy accounting.
   */
  @Test
  void zeroRadialInventoryLimitsBalanceTheFluidAndAmbientSources() {
    for (boolean empty : new boolean[] {true, false}) {
      MultilayerThermalCalculator calculator = new MultilayerThermalCalculator(0.1);
      if (!empty) {
        calculator.createBareSubseaPipe(0.2, 0.02);
        calculator.setEnableThermalMass(false);
      }
      calculator.setFluidTemperature(300.0);
      calculator.setAmbientTemperature(280.0);
      double dt = 10.0;
      double capacity = 100.0;
      double source = 20.0;

      double fluid = calculator.advanceCoupledTransient(capacity, source, dt);

      assertTrue(fluid > 280.0 && fluid < 300.0);
      assertEquals(0.0,
          capacity * (fluid - 300.0) + dt * calculator.getLastAmbientHeatTransferPerLength() - dt * source, 1.0e-8);
      assertEquals(calculator.getLastFluidHeatTransferPerLength(), calculator.getLastAmbientHeatTransferPerLength(),
          0.0);
    }
  }

  /**
   * Invalid requests must leave layer temperatures, fluid temperature and heat diagnostics unchanged.
   */
  @Test
  void invalidLocalRequestsDoNotPublishAnyThermalState() {
    MultilayerThermalCalculator calculator = oneLayerCalculator();
    calculator.advanceCoupledTransient(100.0, 0.0, 0.01);
    double fluid = calculator.getFluidTemperature();
    double wall = calculator.getLayers().get(0).getTemperature();
    double innerFlux = calculator.getLastFluidHeatTransferPerLength();
    double outerFlux = calculator.getLastAmbientHeatTransferPerLength();

    assertThrows(IllegalArgumentException.class, () -> calculator.advanceCoupledTransient(Double.NaN, 0.0, 1.0));
    assertThrows(IllegalArgumentException.class, () -> calculator.advanceCoupledTransient(100.0, 0.0, 0.0));
    assertThrows(IllegalArgumentException.class,
        () -> calculator.advanceCoupledTransient(100.0, Double.POSITIVE_INFINITY, 1.0));
    assertThrows(IllegalStateException.class, () -> calculator.advanceCoupledTransient(100.0, -1.0e9, 1.0));

    assertEquals(fluid, calculator.getFluidTemperature(), 0.0);
    assertEquals(wall, calculator.getLayers().get(0).getTemperature(), 0.0);
    assertEquals(innerFlux, calculator.getLastFluidHeatTransferPerLength(), 0.0);
    assertEquals(outerFlux, calculator.getLastAmbientHeatTransferPerLength(), 0.0);
  }

  /**
   * Construct one radial capacity coupled to a finite fluid capacity with an adiabatic outer boundary.
   *
   * @return calculator initialized with a 20 K fluid-wall difference
   */
  private static MultilayerThermalCalculator oneLayerCalculator() {
    MultilayerThermalCalculator calculator = new MultilayerThermalCalculator(0.1);
    calculator.addCustomLayer("wall", 0.001, 45.0, 7800.0, 500.0).initializeTemperature(280.0);
    calculator.setInnerHTC(1000.0);
    calculator.setOuterHTC(0.0);
    calculator.setFluidTemperature(300.0);
    calculator.setAmbientTemperature(280.0);
    return calculator;
  }

  /**
   * Advance the same analytical relaxation fixture at one chosen temporal resolution.
   *
   * @param steps number of equal steps
   * @param horizon total duration in s
   * @return fluid endpoint temperature in K
   */
  private static double relaxedFluidTemperature(int steps, double horizon) {
    MultilayerThermalCalculator calculator = oneLayerCalculator();
    double initialEnergy = storedEnergy(calculator, 100.0);
    for (int step = 0; step < steps; step++) {
      calculator.advanceCoupledTransient(100.0, 0.0, horizon / steps);
    }
    assertEquals(initialEnergy, storedEnergy(calculator, 100.0), 1.0e-6,
        "Adiabatic exchange must conserve energy at every temporal resolution");
    return calculator.getFluidTemperature();
  }

  /**
   * Evaluate the independently summed sensible energy relative to zero kelvin, per metre.
   *
   * @param calculator current local thermal state
   * @param fluidCapacity fluid capacity in J/(m K)
   * @return sensible energy in J/m
   */
  private static double storedEnergy(MultilayerThermalCalculator calculator, double fluidCapacity) {
    double energy = fluidCapacity * calculator.getFluidTemperature();
    for (RadialThermalLayer layer : calculator.getLayers()) {
      energy += layer.getThermalMassPerLength() * layer.getTemperature();
    }
    return energy;
  }
}
