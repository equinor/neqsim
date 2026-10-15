package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.ejml.data.DMatrixRMaj;
import org.ejml.interfaces.linsol.LinearSolverDense;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;

/**
 * Constrained two-phase Newton qualification using complete ionic inventories and both logical phase orders.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class ConstrainedNewtonFlashTest {
  /**
   * Molecular corrections preserve normalization, component inventory and charge without treating ions as volatile.
   *
   * @param aqueousFirst whether the aqueous role precedes the gas role in the logical phase map
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void molecularCorrectionsKeepIonsInTheirCapturedHost(boolean aqueousFirst) {
    SystemInterface fluid = seededBrine(aqueousFirst);
    SysNewtonRhapsonTPflash solver = new SysNewtonRhapsonTPflash(fluid, 2, fluid.getNumberOfComponents());
    assertEquals(2, solver.neq, "Only nitrogen and water have independent phase-transfer variables");
    double initialResidual = maximumMolecularResidual(fluid);
    for (int iteration = 0; iteration < 6; iteration++) {
      solver.solve();
      assertPhysicalInventory(fluid, aqueousFirst ? 0 : 1);
    }
    assertTrue(maximumMolecularResidual(fluid) < initialResidual * 1e-4,
        "Newton must reduce the molecular equilibrium residual within its local basin");
  }

  /**
   * The objective retains ionic chemical potentials while excluded-phase amounts contribute their zero limit.
   *
   * @throws Exception if reflective access fails
   */
  @Test
  void objectiveIncludesFixedAqueousIonInventory() throws Exception {
    SystemInterface fluid = seededBrine(false);
    SysNewtonRhapsonTPflash solver = new SysNewtonRhapsonTPflash(fluid, 2, fluid.getNumberOfComponents());
    solver.init();
    Field coordinates = SysNewtonRhapsonTPflash.class.getDeclaredField("uVector");
    coordinates.setAccessible(true);
    double[] amounts = ((double[]) coordinates.get(solver)).clone();
    double expected = 0.0;
    double ionicContribution = 0.0;
    for (int component = 0; component < amounts.length; component++) {
      for (int phase = 0; phase < 2; phase++) {
        double composition = fluid.getPhase(phase).getComponent(component).getx();
        if (composition > 0.0) {
          double contribution = fluid.getBeta(phase) * composition
              * Math.log(composition * fluid.getPhase(phase).getComponent(component).getFugacityCoefficient());
          expected += contribution;
          if (component >= 2) {
            ionicContribution += contribution;
          }
        }
      }
    }
    Method objective = SysNewtonRhapsonTPflash.class.getDeclaredMethod("computeQ", double[].class);
    objective.setAccessible(true);
    double actual = (Double) objective.invoke(solver, (Object) amounts);
    assertTrue(Double.isFinite(actual));
    assertTrue(Math.abs(ionicContribution) > 1e-3, "This fixture must exercise the retained ionic contribution");
    assertEquals(expected, actual, 1e-12);
  }

  /**
   * Both logical phase orders preserve a trace whose complement rounds to the complete feed amount.
   *
   * @param aqueousFirst whether the aqueous phase is first
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void smallerAmountCoordinatePreservesSubUlpTrace(boolean aqueousFirst) {
    SystemInterface fluid = seededBrine(aqueousFirst);
    int aqueous = aqueousFirst ? 0 : 1;
    int gas = 1 - aqueous;
    double trace = 1e-24;
    double feed = fluid.getComponent(0).getz();
    assertEquals(feed, feed - trace, 0.0, "This trace cannot be recovered by subtraction from the feed");
    fluid.getPhase(aqueous).getComponent(0).setx(trace / fluid.getBeta(aqueous));
    fluid.getPhase(gas).getComponent(0).setx(feed / fluid.getBeta(gas));
    SysNewtonRhapsonTPflash solver = new SysNewtonRhapsonTPflash(fluid, 2, fluid.getNumberOfComponents());
    solver.init();
    assertEquals(trace, fluid.getBeta(aqueous) * fluid.getPhase(aqueous).getComponent(0).getx(), 1e-38);
    assertPhysicalInventory(fluid, aqueous);
  }

  /**
   * Mixed coordinate orientations apply the same sign to the gradient and both Hessian indices.
   *
   * @throws Exception if reflective access fails
   */
  @Test
  void mixedCoordinateHessianDifferentiatesSignedChemicalPotentials() throws Exception {
    SystemInterface fluid = seededBrine(false);
    SysNewtonRhapsonTPflash solver = new SysNewtonRhapsonTPflash(fluid, 2, fluid.getNumberOfComponents());
    solver.init();
    solver.setJac();
    Field jacobian = SysNewtonRhapsonTPflash.class.getDeclaredField("jacMatrix");
    Field coordinates = SysNewtonRhapsonTPflash.class.getDeclaredField("uVector");
    Field gradient = SysNewtonRhapsonTPflash.class.getDeclaredField("fvecVector");
    Field orientation = SysNewtonRhapsonTPflash.class.getDeclaredField("secondPhaseCoordinate");
    for (Field field : new Field[] {jacobian, coordinates, gradient, orientation}) {
      field.setAccessible(true);
    }
    boolean[] second = (boolean[]) orientation.get(solver);
    assertTrue(second[0] != second[1], "The fixture must exercise opposite coordinate signs");
    DMatrixRMaj analytical = ((DMatrixRMaj) jacobian.get(solver)).copy();
    double[] amounts = (double[]) coordinates.get(solver);
    for (int column = 0; column < solver.neq; column++) {
      double initial = amounts[column];
      double step = 1e-4 * initial;
      amounts[column] = initial + step;
      solver.init();
      solver.setfvec();
      DMatrixRMaj plus = ((DMatrixRMaj) gradient.get(solver)).copy();
      amounts[column] = initial - step;
      solver.init();
      solver.setfvec();
      DMatrixRMaj minus = ((DMatrixRMaj) gradient.get(solver)).copy();
      amounts[column] = initial;
      for (int row = 0; row < solver.neq; row++) {
        double numerical = (plus.get(row, 0) - minus.get(row, 0)) / (2.0 * step);
        assertEquals(numerical, analytical.get(row, column), 2e-5 * Math.max(1.0, Math.abs(numerical)),
            "Signed Hessian entry " + row + ", " + column);
      }
    }
    solver.init();
    assertPhysicalInventory(fluid, 1);
  }

  /**
   * Legacy serialized state can reenter through either public initializer before taking a Newton step.
   *
   * @param serializedActivePhases retained phase count, or zero when absent from the older stream
   * @throws Exception if reflective access fails
   */
  @ParameterizedTest
  @ValueSource(ints = {0, 2})
  void missingSerializedOrientationIsRebuiltByPublicInitializers(int serializedActivePhases) throws Exception {
    for (boolean initializeAmounts : new boolean[] {false, true}) {
      SystemInterface fluid = seededBrine(false);
      SysNewtonRhapsonTPflash solver = new SysNewtonRhapsonTPflash(fluid, 2, fluid.getNumberOfComponents());
      solver.init();
      double beta = fluid.getBeta(0);
      double composition = fluid.getPhase(1).getComponent(0).getx();
      Field orientation = SysNewtonRhapsonTPflash.class.getDeclaredField("secondPhaseCoordinate");
      orientation.setAccessible(true);
      orientation.set(solver, null);
      Field activePhases = SysNewtonRhapsonTPflash.class.getDeclaredField("activePhases");
      activePhases.setAccessible(true);
      activePhases.setInt(solver, serializedActivePhases);
      if (initializeAmounts) {
        solver.setu();
      }
      solver.init();
      assertEquals(beta, fluid.getBeta(0), 1e-14);
      assertEquals(composition, fluid.getPhase(1).getComponent(0).getx(), 1e-14);
      assertPhysicalInventory(fluid, 1);
    }
  }

  /** Rebinding a cached solver refreshes both the feed and the captured aqueous role. */
  @Test
  void reboundSolverRespectsReorderedAqueousRole() {
    SystemInterface first = seededBrine(false);
    SysNewtonRhapsonTPflash reused = new SysNewtonRhapsonTPflash(first, 2, first.getNumberOfComponents());
    reused.solve();
    SystemInterface reordered = seededBrine(true);
    SystemInterface reference = seededBrine(true);
    reused.setSystem(reordered);
    SysNewtonRhapsonTPflash fresh = new SysNewtonRhapsonTPflash(reference, 2, reference.getNumberOfComponents());
    reused.solve();
    fresh.solve();
    for (int phase = 0; phase < 2; phase++) {
      assertEquals(reference.getBeta(phase), reordered.getBeta(phase), 1e-13);
      for (int component = 0; component < reordered.getNumberOfComponents(); component++) {
        assertEquals(reference.getPhase(phase).getComponent(component).getx(),
            reordered.getPhase(phase).getComponent(component).getx(), 1e-13);
      }
    }
    assertPhysicalInventory(reordered, 0);
  }

  /**
   * An exhausted constrained line search restores the last accepted full inventory.
   *
   * @throws Exception if reflective access fails
   */
  @Test
  void failedConstrainedTrialRetainsFullPhysicalInventory() throws Exception {
    SystemInterface fluid = seededBrine(false);
    SysNewtonRhapsonTPflash solver = new SysNewtonRhapsonTPflash(fluid, 2, fluid.getNumberOfComponents());
    solver.init();
    double beta = fluid.getBeta(0);
    double[] composition = new double[fluid.getNumberOfComponents()];
    for (int component = 0; component < composition.length; component++) {
      composition[component] = fluid.getPhase(0).getComponent(component).getx();
    }
    Object linear = Proxy.newProxyInstance(LinearSolverDense.class.getClassLoader(),
        new Class<?>[] {LinearSolverDense.class}, (proxy, method, args) -> {
          if ("setA".equals(method.getName())) {
            return true;
          }
          if ("solve".equals(method.getName())) {
            DMatrixRMaj residual = (DMatrixRMaj) args[0];
            DMatrixRMaj correction = (DMatrixRMaj) args[1];
            for (int row = 0; row < residual.numRows; row++) {
              correction.set(row, 0, 1e6 * residual.get(row, 0));
            }
            return null;
          }
          throw new UnsupportedOperationException(method.getName());
        });
    Field field = SysNewtonRhapsonTPflash.class.getDeclaredField("linearSolver");
    field.setAccessible(true);
    field.set(solver, linear);
    assertThrows(IllegalStateException.class, solver::solve);
    assertEquals(beta, fluid.getBeta(0), 1e-14);
    for (int component = 0; component < composition.length; component++) {
      assertEquals(composition[component], fluid.getPhase(0).getComponent(component).getx(), 1e-14);
    }
    assertPhysicalInventory(fluid, 1);
  }

  /** Zero-feed species remain absent and do not introduce a logarithmic residual or singular transfer variable. */
  @Test
  void absentNeutralSpeciesDoesNotEnterTheReducedSystem() {
    SystemInterface fluid = seededBrine(false, true);
    SysNewtonRhapsonTPflash solver = new SysNewtonRhapsonTPflash(fluid, 2, fluid.getNumberOfComponents());
    solver.init();
    assertEquals(2, solver.neq);
    solver.solve();
    assertEquals(0.0, fluid.getPhase(0).getComponent("methane").getx(), 0.0);
    assertEquals(0.0, fluid.getPhase(1).getComponent("methane").getx(), 0.0);
    assertPhysicalInventory(fluid, 1);
  }

  /**
   * A strictly positive, representable molecular amount is admissible even far below an arbitrary absolute cutoff.
   *
   * @throws Exception if reflective access fails
   */
  @Test
  void positiveTraceAmountsRemainInTheFeasibleDomain() throws Exception {
    SystemInterface fluid = seededBrine(false);
    SysNewtonRhapsonTPflash solver = new SysNewtonRhapsonTPflash(fluid, 2, fluid.getNumberOfComponents());
    Method feasibility = SysNewtonRhapsonTPflash.class.getDeclaredMethod("isFeasible", double[].class);
    feasibility.setAccessible(true);
    double feed = fluid.getPhase(0).getComponent(0).getz();
    for (double amount : new double[] {1e-197, Math.nextDown(feed)}) {
      double[] trial = {amount, 0.0001, 0.0, 0.0};
      assertTrue(amount > 0.0 && feed - amount > 0.0, "Both molecular phase amounts must be representable");
      assertTrue((Boolean) feasibility.invoke(solver, (Object) trial),
          "A positive molecular phase amount must not be excluded by an absolute composition floor");
    }
  }

  /**
   * Zero, negative and unrepresentable complementary amounts remain outside the two-phase Newton domain.
   *
   * @throws Exception if reflective access fails
   */
  @Test
  void nonPositiveAndRoundedAwayAmountsRemainInfeasible() throws Exception {
    SystemInterface fluid = seededBrine(false);
    SysNewtonRhapsonTPflash solver = new SysNewtonRhapsonTPflash(fluid, 2, fluid.getNumberOfComponents());
    Method feasibility = SysNewtonRhapsonTPflash.class.getDeclaredMethod("isFeasible", double[].class);
    feasibility.setAccessible(true);
    double feed = fluid.getPhase(0).getComponent(0).getz();
    double reversedTrace = feed - 1e-197;
    assertEquals(feed, reversedTrace, 0.0, "The complementary trace amount rounds away in this coordinate");
    for (double amount : new double[] {0.0, -1e-197, reversedTrace, Math.nextUp(feed)}) {
      double[] trial = {amount, 0.0001, 0.0, 0.0};
      assertFalse((Boolean) feasibility.invoke(solver, (Object) trial),
          "Newton requires positive representable amounts in both allowed phases");
    }
  }

  /** An ionic state with no aqueous host cannot silently become an unconstrained Newton problem. */
  @Test
  void absentAqueousHostIsRejectedBeforeMutation() {
    SystemInterface fluid = seededBrine(false);
    // A unique configured liquid root is an admissible bootstrap host even before it is labelled aqueous.
    // Two gas roles provide neither an explicit host nor the unique-liquid fallback.
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      fluid.setPhaseType(phase, PhaseType.GAS);
      fluid.getPhase(phase).setType(PhaseType.GAS);
    }
    double beta = fluid.getBeta(0);
    assertThrows(IllegalStateException.class,
        () -> new SysNewtonRhapsonTPflash(fluid, 2, fluid.getNumberOfComponents()));
    assertEquals(beta, fluid.getBeta(0), 0.0);
  }

  /**
   * Creates a conserved two-phase initial guess without invoking any composition-specific flash entry point.
   *
   * @param aqueousFirst whether to reverse the logical phase order
   * @return initialized gas/brine split
   */
  private SystemInterface seededBrine(boolean aqueousFirst) {
    return seededBrine(aqueousFirst, false);
  }

  /**
   * Creates a brine fixture with an optional component absent from the feed inventory.
   *
   * @param aqueousFirst whether to reverse the logical phase order
   * @param absentMethane whether to include a zero-feed molecular species
   * @return initialized gas/brine split
   */
  private SystemInterface seededBrine(boolean aqueousFirst, boolean absentMethane) {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(298.15, 50.0);
    fluid.addComponent("nitrogen", 0.1);
    fluid.addComponent("water", 1.0);
    fluid.addComponent("Na+", 0.018);
    fluid.addComponent("Cl-", 0.018);
    if (absentMethane) {
      fluid.addComponent("methane", 0.0);
    }
    fluid.setMixingRule(10);
    fluid.init(0);
    fluid.setNumberOfPhases(2);
    fluid.setPhaseType(0, PhaseType.GAS);
    fluid.setPhaseType(1, PhaseType.AQUEOUS);
    double totalMoles = 1.136;
    double[] gasAmounts = new double[fluid.getNumberOfComponents()];
    gasAmounts[0] = 0.099 / totalMoles;
    gasAmounts[1] = 0.0001 / totalMoles;
    double beta = gasAmounts[0] + gasAmounts[1];
    fluid.setBeta(0, beta);
    fluid.setBeta(1, 1.0 - beta);
    for (int component = 0; component < gasAmounts.length; component++) {
      double feed = fluid.getPhase(0).getComponent(component).getz();
      fluid.getPhase(0).getComponent(component).setx(gasAmounts[component] / beta);
      fluid.getPhase(1).getComponent(component).setx((feed - gasAmounts[component]) / (1.0 - beta));
    }
    if (aqueousFirst) {
      int firstPhysicalPhase = fluid.getPhaseIndex(0);
      fluid.setPhaseIndex(0, fluid.getPhaseIndex(1));
      fluid.setPhaseIndex(1, firstPhysicalPhase);
      fluid.setBeta(0, 1.0 - beta);
      fluid.setBeta(1, beta);
      fluid.setPhaseType(0, PhaseType.AQUEOUS);
      fluid.setPhaseType(1, PhaseType.GAS);
    }
    fluid.init(3);
    return fluid;
  }

  /**
   * Checks normalization, charge, feed closure and the physical phase admissibility constraints.
   *
   * @param fluid candidate split
   * @param aqueousPhase logical aqueous phase index
   */
  private void assertPhysicalInventory(SystemInterface fluid, int aqueousPhase) {
    assertEquals(1.0, fluid.getBeta(0) + fluid.getBeta(1), 1e-14);
    for (int phase = 0; phase < 2; phase++) {
      double sum = 0.0;
      double charge = 0.0;
      for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
        double composition = fluid.getPhase(phase).getComponent(component).getx();
        assertTrue(Double.isFinite(composition) && composition >= 0.0 && composition <= 1.0);
        sum += composition;
        charge += composition * fluid.getPhase(phase).getComponent(component).getIonicCharge();
        if (FlashPhaseConstraints.isIon(fluid.getPhase(phase).getComponent(component)) && phase != aqueousPhase) {
          assertEquals(0.0, composition, 0.0, "Ions must not enter the nonaqueous phase");
        }
      }
      assertEquals(1.0, sum, 1e-12);
      assertEquals(0.0, charge, 1e-13);
    }
    for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
      double restored = fluid.getBeta(0) * fluid.getPhase(0).getComponent(component).getx()
          + fluid.getBeta(1) * fluid.getPhase(1).getComponent(component).getx();
      assertEquals(fluid.getPhase(0).getComponent(component).getz(), restored, 1e-13);
    }
  }

  /**
   * Evaluates the largest molecular log-fugacity mismatch.
   *
   * @param fluid candidate split
   * @return maximum absolute residual for partitioning neutral species
   */
  private double maximumMolecularResidual(SystemInterface fluid) {
    double maximum = 0.0;
    for (int component = 0; component < 2; component++) {
      double first = fluid.getPhase(0).getComponent(component).getx()
          * fluid.getPhase(0).getComponent(component).getFugacityCoefficient();
      double second = fluid.getPhase(1).getComponent(component).getx()
          * fluid.getPhase(1).getComponent(component).getFugacityCoefficient();
      maximum = Math.max(maximum, Math.abs(Math.log(first / second)));
    }
    return maximum;
  }
}
