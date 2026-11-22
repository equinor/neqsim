package neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary.filmmodelboundary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import neqsim.fluidmechanics.flownode.FlowNodeInterface;
import neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode.StratifiedFlowNode;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Numerical qualification of the nonreactive gas/oil film equations, not pool-rate accuracy.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class CoupledGasOilFilmTest {
  /** Checks that ordinary calls retain the existing path unless the new option is selected. */
  @Test
  void coupledRouteIsExplicitAndDefaultsOff() {
    FilmProbe film = fixture(true, 277.15, 50.0, 1.0, 0.01, 0.0, 1.0);
    film.setCoupledMassHeatTransferEnabled(false);
    assertTrue(!film.isCoupledMassHeatTransferEnabled());
    film.setCoupledMassHeatTransferEnabled(true);
    assertTrue(film.isCoupledMassHeatTransferEnabled());
    film.setHeatTransferCalc(false);
    assertThrows(IllegalStateException.class, film::solve);
  }

  /** Checks the unchanged zero-driving-force gates for binary and six-component fluids. */
  @Test
  void equilibriumDoesNotCreateFluxOrTemperatureDrift() {
    for (boolean rich : new boolean[] {false, true}) {
      for (double velocity : new double[] {0.0, 1.0, 5.0}) {
        FilmProbe film = fixture(rich, 277.15, 50.0, velocity, 0.01, 0.0, 1.0);
        film.solve();
        film.assertResiduals();
        assertEquals(277.15, film.getInterphaseSystem().getTemperature(), 1.0e-6);
        for (double flux : film.calcFluxes()) {
          assertEquals(0.0, flux, 1.0e-8);
        }
        assertEquals(0.0, film.getInterphaseHeatFlux(0), 1.0e-3);
        assertEquals(0.0, film.getInterphaseHeatFlux(1), 1.0e-3);
      }
    }
  }

  /** Checks both signed perturbations, nearby pressures/temperatures and difference-step refinement. */
  @Test
  void nearbyNonequilibriumStatesSatisfyEveryFilmAndHeatEquation() {
    for (double temperature : new double[] {276.15, 277.15, 278.15}) {
      for (double pressure : new double[] {45.0, 50.0, 55.0}) {
        for (double perturbation : new double[] {-0.01, 0.01}) {
          FilmProbe reference = fixture(true, temperature, pressure, 1.0, 0.01, perturbation, 1.0);
          reference.solve();
          reference.assertResiduals();
          double[] expected = reference.calcFluxes();
          FilmProbe refined = fixture(true, temperature, pressure, 1.0, 0.01, perturbation, 0.5);
          refined.solve();
          refined.assertResiduals();
          for (int component = 0; component < expected.length; component++) {
            assertEquals(expected[component], refined.getInterphaseMolarFlux(component),
                Math.max(1.0e-9, Math.abs(expected[component]) * 1.0e-5));
          }
          assertEquals(reference.getInterphaseSystem().getTemperature(), refined.getInterphaseSystem().getTemperature(),
              1.0e-7);
          // Repeated solve starts with a nonzero flux; it must converge without an equilibrium reset.
          reference.solve();
          reference.assertResiduals();
          assertEquals(expected[5], reference.getInterphaseMolarFlux(5), 1.0e-9);
        }
      }
    }
  }

  /** Checks nearby finite liquid speeds and explicit rejection of unsupported countercurrent transport. */
  @Test
  void nearbyLiquidVelocitiesRetainFullResidualAcceptance() {
    for (double velocity : new double[] {0.005, 0.02}) {
      FilmProbe film = fixture(true, 277.15, 50.0, 1.0, velocity, 0.01, 1.0);
      film.solve();
      film.assertResiduals();
    }
    FilmProbe reverse = fixture(true, 277.15, 50.0, 1.0, -0.01, 0.01, 1.0);
    assertThrows(IllegalStateException.class, reverse::solve);
  }

  /**
   * Checks deterministic restart of the option and nonzero converged flux.
   *
   * @throws IOException if serialization fails
   * @throws ClassNotFoundException if the restart class cannot be loaded
   */
  @Test
  void serializedCoupledBoundaryRetainsOptionAndResult() throws IOException, ClassNotFoundException {
    FilmProbe film = fixture(true, 277.15, 50.0, 1.0, 0.01, 0.01, 1.0);
    film.solve();
    double expected = film.getInterphaseMolarFlux(5);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(film);
    }
    FilmProbe restored;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      restored = (FilmProbe) input.readObject();
    }
    assertTrue(restored.isCoupledMassHeatTransferEnabled());
    assertEquals(expected, restored.getInterphaseMolarFlux(5), 0.0);
    restored.solve();
    restored.assertResiduals();
    assertEquals(expected, restored.getInterphaseMolarFlux(5), 1.0e-9);
  }

  /** Checks thermal driving force and equal/opposite externally accounted interface heat. */
  @Test
  void unequalBulkTemperaturesConvergeWithComponentEnthalpyTransfer() {
    FilmProbe film = fixture(true, 277.15, 50.0, 1.0, 0.01, 0.01, 1.0);
    film.getBulkSystem().getPhase(0).setTemperature(278.15);
    film.getBulkSystem().getPhase(1).setTemperature(276.15);
    film.getBulkSystem().initProperties();
    film.solve();
    film.assertResiduals();
    assertTrue(Math.abs(film.getInterphaseHeatFlux(0)) > 1.0);
  }

  /** Rejects the zero-Re liquid correlation instead of substituting a unit conductance. */
  @Test
  void zeroLiquidTransportRejectsAndRestoresPublishedInterfaceState() {
    FilmProbe film = fixture(true, 277.15, 50.0, 1.0, 0.01, 0.01, 1.0);
    film.solve();
    SystemInterface before = film.getInterphaseSystem().clone();
    double temperature = before.getTemperature();
    double gasHeat = film.getInterphaseHeatFlux(0);
    double oilHeat = film.getInterphaseHeatFlux(1);
    double[] flux = film.calcFluxes();
    film.stopLiquidMotion();
    IllegalStateException error = assertThrows(IllegalStateException.class, film::solve);
    assertTrue(error.getMessage().contains("positive finite binary transfer"));
    assertEquals(temperature, film.getInterphaseSystem().getTemperature(), 0.0);
    for (int component = 0; component < flux.length; component++) {
      assertEquals(flux[component], film.getInterphaseMolarFlux(component), 0.0);
    }
    assertEquals(gasHeat, film.getInterphaseHeatFlux(0), 0.0);
    assertEquals(oilHeat, film.getInterphaseHeatFlux(1), 0.0);
    for (int phase = 0; phase < 2; phase++) {
      for (int component = 0; component < flux.length; component++) {
        assertEquals(before.getPhase(phase).getComponent(component).getx(),
            film.getInterphaseSystem().getPhase(phase).getComponent(component).getx(), 0.0);
      }
    }
    assertTrue(Double.isFinite(film.getBulkSystem().getPressure()));
  }

  /**
   * Creates the public SRK gas/oil fixture with an optional gas-composition perturbation.
   *
   * @param rich true for the public six-component composition, false for binary methane/heptane
   * @param temperature bulk temperature in K
   * @param pressure absolute pressure in bara
   * @param gasVelocity gas speed in m/s
   * @param oilVelocity oil speed in m/s
   * @param perturbation relative gas heptane perturbation, balanced with methane
   * @param differenceScale multiplier on the centered-difference intervals
   * @return initialized film using a prescribed local 5% oil fraction, not a pipeline pool
   */
  private static FilmProbe fixture(boolean rich, double temperature, double pressure, double gasVelocity,
      double oilVelocity, double perturbation, double differenceScale) {
    SystemInterface fluid = new SystemSrkEos(temperature, pressure);
    if (rich) {
      String[] names = {"methane", "ethane", "propane", "n-butane", "n-pentane", "n-heptane"};
      double[] fractions = {0.916, 0.050, 0.020, 0.007, 0.003, 0.004};
      for (int component = 0; component < names.length; component++) {
        fluid.addComponent(names[component], fractions[component]);
      }
    } else {
      fluid.addComponent("methane", 0.95);
      fluid.addComponent("n-heptane", 0.05);
    }
    fluid.setMixingRule("classic");
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
    assertEquals(2, fluid.getNumberOfPhases());
    double transferred = fluid.getPhase(0).getComponent("n-heptane").getNumberOfMolesInPhase() * perturbation;
    fluid.addComponent("n-heptane", transferred, 0);
    fluid.addComponent("methane", -transferred, 0);
    fluid.init_x_y();
    fluid.initBeta();
    fluid.initProperties();
    PipeData pipe = new PipeData(1.0148, 1.0e-5);
    pipe.setNodeLength(1.0);
    StratifiedFlowNode node = new StratifiedFlowNode(fluid, pipe);
    node.setPhaseFraction(0, 0.95);
    node.setPhaseFraction(1, 0.05);
    node.setVelocity(0, gasVelocity);
    node.setVelocity(1, oilVelocity);
    node.init();
    FilmProbe film = new FilmProbe(node, differenceScale);
    film.setCoupledMassHeatTransferEnabled(true);
    film.setHeatTransferCalc(true);
    film.setMassTransferCalc(true);
    film.useFiniteFluxCorrection(true);
    film.useThermodynamicCorrections(true);
    return film;
  }

  /**
   * Exposes independent equation audits and finite-difference refinement without replacing equations.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  private static final class FilmProbe extends KrishnaStandartFilmModel {
    private static final long serialVersionUID = 1L;
    private final double differenceScale;

    /**
     * Creates the probe using the existing public flow node.
     *
     * @param node configured flow node
     * @param differenceScale finite-difference interval multiplier
     */
    private FilmProbe(FlowNodeInterface node, double differenceScale) {
      super(node);
      this.differenceScale = differenceScale;
    }

    /** {@inheritDoc} */
    @Override
    protected double coupledFilmDifferenceStep(double[] state, int column) {
      return differenceScale * super.coupledFilmDifferenceStep(state, column);
    }

    /** Stops the liquid motion to exercise rejection after a valid nonzero result. */
    private void stopLiquidMotion() {
      flowNode.setVelocity(1, 0.0);
      flowNode.init();
    }

    /** Independently checks fugacity equality, normalizations, both films and interface heat balance. */
    private void assertResiduals() {
      init();
      setfvecMassTrans2();
      int count = bulkSystem.getNumberOfComponents();
      for (int row = 0; row < neq; row++) {
        assertTrue(Double.isFinite(fvec.get(row, 0)), "finite residual " + row);
        assertEquals(0.0, fvec.get(row, 0), row < count ? 1.0e-10 : row < count + 2 ? 1.0e-12 : 1.0e-10);
      }
      double enthalpy = 0.0;
      for (int component = 0; component < count; component++) {
        double gasTemperature = bulkSystem.getPhase(0).getTemperature();
        double oilTemperature = bulkSystem.getPhase(1).getTemperature();
        double gasEnthalpy = bulkSystem.getPhase(0).getComponent(component).getEnthalpy(gasTemperature)
            / bulkSystem.getPhase(0).getComponent(component).getNumberOfMolesInPhase();
        double oilEnthalpy = bulkSystem.getPhase(1).getComponent(component).getEnthalpy(oilTemperature)
            / bulkSystem.getPhase(1).getComponent(component).getNumberOfMolesInPhase();
        enthalpy += getInterphaseMolarFlux(component) * (gasEnthalpy - oilEnthalpy);
      }
      assertEquals(enthalpy, getInterphaseHeatFlux(0) + getInterphaseHeatFlux(1), 1.0e-6);
    }
  }
}
