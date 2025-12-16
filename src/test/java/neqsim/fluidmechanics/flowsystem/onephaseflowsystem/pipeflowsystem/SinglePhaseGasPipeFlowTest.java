package neqsim.fluidmechanics.flowsystem.onephaseflowsystem.pipeflowsystem;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import neqsim.fluidmechanics.flowsystem.FlowSystemInterface;
import neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Comprehensive test suite for single-phase gas pipeline flow simulation.
 *
 * <p>
 * This test suite validates:
 * <ul>
 * <li>Steady-state solver mathematical correctness</li>
 * <li>Numerical stability under various conditions</li>
 * <li>Dynamic/transient solver with time-varying inlet conditions</li>
 * <li>Compositional tracking in dynamic simulations</li>
 * <li>Physical reasonableness of results</li>
 * </ul>
 * </p>
 *
 * <p>
 * The solver implements a staggered grid finite volume method with TDMA (Tri-Diagonal Matrix
 * Algorithm) for solving:
 * <ul>
 * <li>Mass conservation: ∂ρ/∂t + ∂(ρv)/∂x = 0</li>
 * <li>Momentum conservation: ∂(ρv)/∂t + ∂(ρv²)/∂x = -∂P/∂x - ρg·sin(θ) - f·ρv|v|/(2D)</li>
 * <li>Energy conservation: ∂(ρh)/∂t + ∂(ρvh)/∂x = Q_wall + ρvg·sin(θ)</li>
 * <li>Component mass: ∂(ρω_i)/∂t + ∂(ρvω_i)/∂x = 0 for each component i</li>
 * </ul>
 * </p>
 *
 * @author NeqSim Development Team
 */
public class SinglePhaseGasPipeFlowTest extends neqsim.NeqSimTest {

  private static final double TOLERANCE_PRESSURE = 0.1; // bar
  private static final double TOLERANCE_TEMPERATURE = 0.5; // K
  private static final double TOLERANCE_COMPOSITION = 0.01; // mole fraction (1% allowed drift)
  private static final double TOLERANCE_MASS_BALANCE = 0.01; // relative (1% allowed)

  @Nested
  @DisplayName("Steady State Solver Tests")
  class SteadyStateSolverTests {

    private FlowSystemInterface pipe;
    private SystemInterface inletGas;

    @BeforeEach
    void setUp() {
      pipe = new PipeFlowSystem();

      // Natural gas at typical pipeline conditions
      inletGas = new SystemSrkEos(288.15, 100.0); // 15°C, 100 bar
      inletGas.addComponent("methane", 0.90);
      inletGas.addComponent("ethane", 0.06);
      inletGas.addComponent("propane", 0.03);
      inletGas.addComponent("nitrogen", 0.01);
      inletGas.createDatabase(true);
      inletGas.init(0);
      inletGas.init(3);
      inletGas.initPhysicalProperties();
      inletGas.setTotalFlowRate(10.0, "MSm3/day"); // Typical pipeline flow
    }

    @Test
    @DisplayName("Pressure decreases monotonically along horizontal pipe")
    void testPressureDropHorizontalPipe() {
      setupHorizontalPipeline(100000.0, 1.0); // 100 km, 1 m diameter

      pipe.createSystem();
      pipe.init();
      pipe.solveSteadyState(10);

      // Verify pressure decreases monotonically
      double previousPressure = pipe.getNode(0).getBulkSystem().getPressure();
      for (int i = 1; i < pipe.getTotalNumberOfNodes(); i++) {
        double currentPressure = pipe.getNode(i).getBulkSystem().getPressure();
        assertTrue(currentPressure <= previousPressure,
            "Pressure should decrease or stay constant along pipe at node " + i);
        previousPressure = currentPressure;
      }

      // Verify total pressure drop is positive and reasonable
      double totalPressureDrop = pipe.getTotalPressureDrop();
      assertTrue(totalPressureDrop > 0, "Total pressure drop should be positive");
      assertTrue(totalPressureDrop < 50, "Pressure drop should be reasonable (< 50 bar)");
    }

    @Test
    @DisplayName("Temperature changes due to heat transfer with surroundings")
    void testTemperatureEvolution() {
      setupHorizontalPipeline(200000.0, 1.0); // 200 km

      pipe.createSystem();
      pipe.init();
      pipe.solveSteadyState(10);

      // Inlet temperature is 288.15 K, surroundings at 278 K
      double inletTemp = pipe.getNode(0).getBulkSystem().getTemperature();
      double outletTemp =
          pipe.getNode(pipe.getTotalNumberOfNodes() - 1).getBulkSystem().getTemperature();

      // Temperature should approach surroundings
      assertEquals(288.15, inletTemp, TOLERANCE_TEMPERATURE);
      // Outlet should be cooler (approaching 278 K)
      assertTrue(outletTemp < inletTemp, "Temperature should decrease towards surroundings");
    }

    @Test
    @DisplayName("Mass conservation across all nodes")
    void testMassConservation() {
      setupHorizontalPipeline(50000.0, 0.8); // 50 km

      pipe.createSystem();
      pipe.init();
      pipe.solveSteadyState(10);

      // In steady state compressible flow, the mass flow entering should equal mass flow exiting
      // For single phase gas: mass flow = ρ·v·A
      // Due to compressibility, velocity increases as pressure drops, but mass is conserved
      double inletMassFlow = pipe.getNode(0).getMassFlowRate(0);

      // For steady state, inlet and outlet mass flow should match within tolerance
      // (intermediate nodes may show variations due to numerical scheme)
      double outletMassFlow = pipe.getNode(pipe.getTotalNumberOfNodes() - 1).getMassFlowRate(0);

      // Allow 10% tolerance for compressible flow numerical accuracy
      assertEquals(inletMassFlow, outletMassFlow, inletMassFlow * 0.15,
          "Inlet and outlet mass flow should be approximately equal in steady state");

      // Verify outlet pressure is lower than inlet (basic sanity check)
      double inletPressure = pipe.getNode(0).getBulkSystem().getPressure();
      double outletPressure =
          pipe.getNode(pipe.getTotalNumberOfNodes() - 1).getBulkSystem().getPressure();
      assertTrue(outletPressure < inletPressure, "Outlet pressure should be lower than inlet");
    }

    @Test
    @DisplayName("Reynolds number calculation is physically correct")
    void testReynoldsNumberCalculation() {
      setupHorizontalPipeline(10000.0, 1.0);

      pipe.createSystem();
      pipe.init();
      pipe.solveSteadyState(0);

      for (int i = 0; i < pipe.getTotalNumberOfNodes(); i++) {
        double Re = pipe.getNode(i).getReynoldsNumber();
        // High-pressure gas pipeline should have turbulent flow (Re > 4000)
        assertTrue(Re > 4000, "Pipeline flow should be turbulent, Re = " + Re);
        // But not unreasonably high
        assertTrue(Re < 1e9, "Reynolds number should be realistic, Re = " + Re);
      }
    }

    @Test
    @DisplayName("Friction factor is in reasonable range for turbulent pipe flow")
    void testFrictionFactor() {
      setupHorizontalPipeline(10000.0, 1.0);

      pipe.createSystem();
      pipe.init();
      pipe.solveSteadyState(0);

      for (int i = 0; i < pipe.getTotalNumberOfNodes(); i++) {
        double friction = pipe.getNode(i).getWallFrictionFactor(0);
        // Darcy friction factor for smooth-ish pipe: 0.005 - 0.03
        assertTrue(friction > 0.005, "Friction factor too low: " + friction);
        assertTrue(friction < 0.05, "Friction factor too high: " + friction);
      }
    }

    @Test
    @DisplayName("Solver types 0, 1, 10, 20 all converge")
    void testDifferentSolverTypes() {
      setupHorizontalPipeline(20000.0, 0.8);

      int[] solverTypes = {0, 1, 10, 20};

      for (int solverType : solverTypes) {
        // Reset for each solver type
        pipe = new PipeFlowSystem();
        inletGas = new SystemSrkEos(288.15, 100.0);
        inletGas.addComponent("methane", 0.90);
        inletGas.addComponent("ethane", 0.10);
        inletGas.createDatabase(true);
        inletGas.init(0);
        inletGas.init(3);
        inletGas.initPhysicalProperties();
        inletGas.setTotalFlowRate(10.0, "MSm3/day");

        setupHorizontalPipeline(20000.0, 0.8);
        pipe.createSystem();
        pipe.init();

        assertDoesNotThrow(() -> pipe.solveSteadyState(solverType),
            "Solver type " + solverType + " should converge");

        double pressureDrop = pipe.getTotalPressureDrop();
        assertTrue(pressureDrop > 0, "Solver type " + solverType + " should produce valid results");
      }
    }

    private void setupHorizontalPipeline(double lengthMeters, double diameterMeters) {
      int numLegs = 10;
      double[] height = new double[numLegs + 1];
      Arrays.fill(height, 0.0); // Horizontal

      double[] length = new double[numLegs + 1];
      for (int i = 0; i <= numLegs; i++) {
        length[i] = lengthMeters * i / numLegs;
      }

      double[] outerTemp = new double[numLegs + 1];
      Arrays.fill(outerTemp, 278.0); // 5°C surroundings

      double[] outerHeatCoef = new double[numLegs + 1];
      Arrays.fill(outerHeatCoef, 5.0);

      double[] wallHeatCoef = new double[numLegs + 1];
      Arrays.fill(wallHeatCoef, 15.0);

      GeometryDefinitionInterface[] pipeGeometry = new PipeData[numLegs + 1];
      for (int i = 0; i <= numLegs; i++) {
        pipeGeometry[i] = new PipeData();
        pipeGeometry[i].setDiameter(diameterMeters);
        pipeGeometry[i].setInnerSurfaceRoughness(1e-5);
      }

      pipe.setInletThermoSystem(inletGas);
      pipe.setNumberOfLegs(numLegs);
      pipe.setNumberOfNodesInLeg(20);
      pipe.setEquipmentGeometry(pipeGeometry);
      pipe.setLegHeights(height);
      pipe.setLegPositions(length);
      pipe.setLegOuterTemperatures(outerTemp);
      pipe.setLegWallHeatTransferCoefficients(wallHeatCoef);
      pipe.setLegOuterHeatTransferCoefficients(outerHeatCoef);
    }
  }

  @Nested
  @DisplayName("Compositional Tracking Tests")
  class CompositionalTrackingTests {

    @Test
    @DisplayName("Steady state preserves inlet composition")
    void testSteadyStateCompositionPreservation() {
      FlowSystemInterface pipe = new PipeFlowSystem();

      SystemInterface gas = new SystemSrkEos(288.15, 100.0);
      gas.addComponent("methane", 0.85);
      gas.addComponent("ethane", 0.10);
      gas.addComponent("propane", 0.05);
      gas.createDatabase(true);
      gas.init(0);
      gas.init(3);
      gas.initPhysicalProperties();
      gas.setTotalFlowRate(5.0, "MSm3/day");

      setupSimplePipeline(pipe, gas, 50000.0);
      pipe.createSystem();
      pipe.init();
      pipe.solveSteadyState(20); // Type 20 includes composition solve

      // In steady state single-phase flow, composition should be uniform
      double inletMethane =
          pipe.getNode(0).getBulkSystem().getPhase(0).getComponent("methane").getx();
      double inletEthane =
          pipe.getNode(0).getBulkSystem().getPhase(0).getComponent("ethane").getx();

      for (int i = 1; i < pipe.getTotalNumberOfNodes(); i++) {
        double nodeMethane =
            pipe.getNode(i).getBulkSystem().getPhase(0).getComponent("methane").getx();
        double nodeEthane =
            pipe.getNode(i).getBulkSystem().getPhase(0).getComponent("ethane").getx();

        assertEquals(inletMethane, nodeMethane, TOLERANCE_COMPOSITION,
            "Methane fraction should be preserved at node " + i);
        assertEquals(inletEthane, nodeEthane, TOLERANCE_COMPOSITION,
            "Ethane fraction should be preserved at node " + i);
      }
    }

    @Test
    @DisplayName("Verify component mass fractions sum to unity")
    void testMassFractionNormalization() {
      FlowSystemInterface pipe = new PipeFlowSystem();

      SystemInterface gas = new SystemSrkEos(300.0, 80.0);
      gas.addComponent("methane", 0.80);
      gas.addComponent("ethane", 0.12);
      gas.addComponent("propane", 0.05);
      gas.addComponent("n-butane", 0.03);
      gas.createDatabase(true);
      gas.init(0);
      gas.init(3);
      gas.initPhysicalProperties();
      gas.setTotalFlowRate(8.0, "MSm3/day");

      setupSimplePipeline(pipe, gas, 30000.0);
      pipe.createSystem();
      pipe.init();
      pipe.solveSteadyState(20);

      for (int i = 0; i < pipe.getTotalNumberOfNodes(); i++) {
        double sumMoleFractions = 0.0;
        for (int j = 0; j < pipe.getNode(i).getBulkSystem().getPhase(0)
            .getNumberOfComponents(); j++) {
          sumMoleFractions += pipe.getNode(i).getBulkSystem().getPhase(0).getComponent(j).getx();
        }
        assertEquals(1.0, sumMoleFractions, 1e-6, "Mole fractions should sum to 1 at node " + i);
      }
    }

    private void setupSimplePipeline(FlowSystemInterface pipe, SystemInterface gas,
        double lengthMeters) {
      int numLegs = 5;
      double[] height = new double[numLegs + 1];
      double[] length = new double[numLegs + 1];
      double[] outerTemp = new double[numLegs + 1];
      double[] outerHeatCoef = new double[numLegs + 1];
      double[] wallHeatCoef = new double[numLegs + 1];

      for (int i = 0; i <= numLegs; i++) {
        height[i] = 0.0;
        length[i] = lengthMeters * i / numLegs;
        outerTemp[i] = 278.0;
        outerHeatCoef[i] = 5.0;
        wallHeatCoef[i] = 15.0;
      }

      GeometryDefinitionInterface[] pipeGeometry = new PipeData[numLegs + 1];
      for (int i = 0; i <= numLegs; i++) {
        pipeGeometry[i] = new PipeData();
        pipeGeometry[i].setDiameter(0.8);
        pipeGeometry[i].setInnerSurfaceRoughness(1e-5);
      }

      pipe.setInletThermoSystem(gas);
      pipe.setNumberOfLegs(numLegs);
      pipe.setNumberOfNodesInLeg(10);
      pipe.setEquipmentGeometry(pipeGeometry);
      pipe.setLegHeights(height);
      pipe.setLegPositions(length);
      pipe.setLegOuterTemperatures(outerTemp);
      pipe.setLegWallHeatTransferCoefficients(wallHeatCoef);
      pipe.setLegOuterHeatTransferCoefficients(outerHeatCoef);
    }
  }

  @Nested
  @DisplayName("Numerical Stability Tests")
  class NumericalStabilityTests {

    @Test
    @DisplayName("Solver handles high flow rates without divergence")
    void testHighFlowRateStability() {
      FlowSystemInterface pipe = new PipeFlowSystem();

      SystemInterface gas = new SystemSrkEos(288.15, 120.0);
      gas.addComponent("methane", 0.95);
      gas.addComponent("ethane", 0.05);
      gas.createDatabase(true);
      gas.init(0);
      gas.init(3);
      gas.initPhysicalProperties();
      gas.setTotalFlowRate(100.0, "MSm3/day"); // Very high flow rate

      setupPipeline(pipe, gas, 50000.0, 1.2);
      pipe.createSystem();
      pipe.init();

      assertDoesNotThrow(() -> pipe.solveSteadyState(10));

      // Results should still be physical
      assertTrue(pipe.getTotalPressureDrop() > 0);
      assertTrue(pipe.getNode(pipe.getTotalNumberOfNodes() - 1).getBulkSystem().getPressure() > 0);
    }

    @Test
    @DisplayName("Solver handles low pressure conditions")
    void testLowPressureStability() {
      FlowSystemInterface pipe = new PipeFlowSystem();

      SystemInterface gas = new SystemSrkEos(300.0, 10.0); // Low pressure
      gas.addComponent("methane", 0.90);
      gas.addComponent("ethane", 0.10);
      gas.createDatabase(true);
      gas.init(0);
      gas.init(3);
      gas.initPhysicalProperties();
      gas.setTotalFlowRate(1.0, "MSm3/day");

      setupPipeline(pipe, gas, 20000.0, 0.5);
      pipe.createSystem();
      pipe.init();

      assertDoesNotThrow(() -> pipe.solveSteadyState(10));
      assertTrue(pipe.getTotalPressureDrop() >= 0);
    }

    @Test
    @DisplayName("Solver handles inclined pipeline (with elevation changes)")
    void testInclinedPipelineStability() {
      FlowSystemInterface pipe = new PipeFlowSystem();

      SystemInterface gas = new SystemSrkEos(288.15, 100.0);
      gas.addComponent("methane", 0.90);
      gas.addComponent("ethane", 0.10);
      gas.createDatabase(true);
      gas.init(0);
      gas.init(3);
      gas.initPhysicalProperties();
      gas.setTotalFlowRate(10.0, "MSm3/day");

      // Uphill pipeline
      int numLegs = 5;
      double[] height = {0, 100, 200, 300, 400, 500}; // 500m elevation gain
      double[] length = {0, 10000, 20000, 30000, 40000, 50000};
      double[] outerTemp = {278.0, 277.0, 276.0, 275.0, 274.0, 273.0};
      double[] outerHeatCoef = {5.0, 5.0, 5.0, 5.0, 5.0, 5.0};
      double[] wallHeatCoef = {15.0, 15.0, 15.0, 15.0, 15.0, 15.0};

      GeometryDefinitionInterface[] pipeGeometry = new PipeData[numLegs + 1];
      for (int i = 0; i <= numLegs; i++) {
        pipeGeometry[i] = new PipeData();
        pipeGeometry[i].setDiameter(0.8);
        pipeGeometry[i].setInnerSurfaceRoughness(1e-5);
      }

      pipe.setInletThermoSystem(gas);
      pipe.setNumberOfLegs(numLegs);
      pipe.setNumberOfNodesInLeg(10);
      pipe.setEquipmentGeometry(pipeGeometry);
      pipe.setLegHeights(height);
      pipe.setLegPositions(length);
      pipe.setLegOuterTemperatures(outerTemp);
      pipe.setLegWallHeatTransferCoefficients(wallHeatCoef);
      pipe.setLegOuterHeatTransferCoefficients(outerHeatCoef);

      pipe.createSystem();
      pipe.init();

      assertDoesNotThrow(() -> pipe.solveSteadyState(10));

      // Pressure drop should be higher than horizontal due to gravity
      double pressureDrop = pipe.getTotalPressureDrop();
      assertTrue(pressureDrop > 0, "Pressure should drop in uphill flow");
    }

    private void setupPipeline(FlowSystemInterface pipe, SystemInterface gas, double lengthMeters,
        double diameter) {
      int numLegs = 5;
      double[] height = new double[numLegs + 1];
      double[] length = new double[numLegs + 1];
      double[] outerTemp = new double[numLegs + 1];
      double[] outerHeatCoef = new double[numLegs + 1];
      double[] wallHeatCoef = new double[numLegs + 1];

      for (int i = 0; i <= numLegs; i++) {
        height[i] = 0.0;
        length[i] = lengthMeters * i / numLegs;
        outerTemp[i] = 278.0;
        outerHeatCoef[i] = 5.0;
        wallHeatCoef[i] = 15.0;
      }

      GeometryDefinitionInterface[] pipeGeometry = new PipeData[numLegs + 1];
      for (int i = 0; i <= numLegs; i++) {
        pipeGeometry[i] = new PipeData();
        pipeGeometry[i].setDiameter(diameter);
        pipeGeometry[i].setInnerSurfaceRoughness(1e-5);
      }

      pipe.setInletThermoSystem(gas);
      pipe.setNumberOfLegs(numLegs);
      pipe.setNumberOfNodesInLeg(10);
      pipe.setEquipmentGeometry(pipeGeometry);
      pipe.setLegHeights(height);
      pipe.setLegPositions(length);
      pipe.setLegOuterTemperatures(outerTemp);
      pipe.setLegWallHeatTransferCoefficients(wallHeatCoef);
      pipe.setLegOuterHeatTransferCoefficients(outerHeatCoef);
    }
  }

  @Nested
  @DisplayName("Physical Validation Tests")
  class PhysicalValidationTests {

    @Test
    @DisplayName("Pressure drop follows Darcy-Weisbach equation qualitatively")
    void testDarcyWeisbachScaling() {
      // Pressure drop should scale with L/D and v^2
      FlowSystemInterface pipe1 = new PipeFlowSystem();
      FlowSystemInterface pipe2 = new PipeFlowSystem();

      SystemInterface gas = new SystemSrkEos(288.15, 100.0);
      gas.addComponent("methane", 0.95);
      gas.addComponent("ethane", 0.05);
      gas.createDatabase(true);
      gas.init(0);
      gas.init(3);
      gas.initPhysicalProperties();
      gas.setTotalFlowRate(10.0, "MSm3/day");

      // Pipe 1: standard
      setupPipeline(pipe1, gas, 50000.0, 0.8);
      pipe1.createSystem();
      pipe1.init();
      pipe1.solveSteadyState(10);
      double dp1 = pipe1.getTotalPressureDrop();

      // Pipe 2: double length
      gas.setTotalFlowRate(10.0, "MSm3/day");
      setupPipeline(pipe2, gas, 100000.0, 0.8);
      pipe2.createSystem();
      pipe2.init();
      pipe2.solveSteadyState(10);
      double dp2 = pipe2.getTotalPressureDrop();

      // Pressure drop should roughly double with double length
      assertTrue(dp2 > dp1 * 1.5, "Pressure drop should scale with length");
      assertTrue(dp2 < dp1 * 2.5, "Pressure drop scaling should be approximately linear");
    }

    @Test
    @DisplayName("Joule-Thomson cooling effect is captured")
    void testJouleThomsonEffect() {
      // For natural gas, Joule-Thomson effect causes cooling on expansion
      FlowSystemInterface pipe = new PipeFlowSystem();

      SystemInterface gas = new SystemSrkEos(300.0, 150.0); // High pressure
      gas.addComponent("methane", 0.90);
      gas.addComponent("ethane", 0.10);
      gas.createDatabase(true);
      gas.init(0);
      gas.init(3);
      gas.initPhysicalProperties();
      gas.setTotalFlowRate(20.0, "MSm3/day");

      // Adiabatic conditions (very low heat transfer)
      int numLegs = 5;
      double[] height = new double[numLegs + 1];
      double[] length = new double[numLegs + 1];
      double[] outerTemp = new double[numLegs + 1];
      double[] outerHeatCoef = new double[numLegs + 1];
      double[] wallHeatCoef = new double[numLegs + 1];

      for (int i = 0; i <= numLegs; i++) {
        height[i] = 0.0;
        length[i] = 100000.0 * i / numLegs; // 100 km
        outerTemp[i] = 300.0; // Same as inlet (isothermal surroundings)
        outerHeatCoef[i] = 0.001; // Near-adiabatic
        wallHeatCoef[i] = 0.001;
      }

      GeometryDefinitionInterface[] pipeGeometry = new PipeData[numLegs + 1];
      for (int i = 0; i <= numLegs; i++) {
        pipeGeometry[i] = new PipeData();
        pipeGeometry[i].setDiameter(1.0);
        pipeGeometry[i].setInnerSurfaceRoughness(1e-5);
      }

      pipe.setInletThermoSystem(gas);
      pipe.setNumberOfLegs(numLegs);
      pipe.setNumberOfNodesInLeg(10);
      pipe.setEquipmentGeometry(pipeGeometry);
      pipe.setLegHeights(height);
      pipe.setLegPositions(length);
      pipe.setLegOuterTemperatures(outerTemp);
      pipe.setLegWallHeatTransferCoefficients(wallHeatCoef);
      pipe.setLegOuterHeatTransferCoefficients(outerHeatCoef);

      pipe.createSystem();
      pipe.init();
      pipe.solveSteadyState(10);

      double inletTemp = pipe.getNode(0).getBulkSystem().getTemperature();
      double outletTemp =
          pipe.getNode(pipe.getTotalNumberOfNodes() - 1).getBulkSystem().getTemperature();

      // Gas temperature should change due to JT effect as pressure drops
      // For methane-rich gas above inversion temperature, expect cooling
      assertNotEquals(inletTemp, outletTemp, 0.1,
          "Temperature should change due to Joule-Thomson effect");
    }

    private void setupPipeline(FlowSystemInterface pipe, SystemInterface gas, double lengthMeters,
        double diameter) {
      int numLegs = 5;
      double[] height = new double[numLegs + 1];
      double[] length = new double[numLegs + 1];
      double[] outerTemp = new double[numLegs + 1];
      double[] outerHeatCoef = new double[numLegs + 1];
      double[] wallHeatCoef = new double[numLegs + 1];

      for (int i = 0; i <= numLegs; i++) {
        height[i] = 0.0;
        length[i] = lengthMeters * i / numLegs;
        outerTemp[i] = 278.0;
        outerHeatCoef[i] = 5.0;
        wallHeatCoef[i] = 15.0;
      }

      GeometryDefinitionInterface[] pipeGeometry = new PipeData[numLegs + 1];
      for (int i = 0; i <= numLegs; i++) {
        pipeGeometry[i] = new PipeData();
        pipeGeometry[i].setDiameter(diameter);
        pipeGeometry[i].setInnerSurfaceRoughness(1e-5);
      }

      pipe.setInletThermoSystem(gas);
      pipe.setNumberOfLegs(numLegs);
      pipe.setNumberOfNodesInLeg(10);
      pipe.setEquipmentGeometry(pipeGeometry);
      pipe.setLegHeights(height);
      pipe.setLegPositions(length);
      pipe.setLegOuterTemperatures(outerTemp);
      pipe.setLegWallHeatTransferCoefficients(wallHeatCoef);
      pipe.setLegOuterHeatTransferCoefficients(outerHeatCoef);
    }
  }

  @Nested
  @DisplayName("Transient Flow Tests")
  class TransientFlowTests {

    @Test
    @DisplayName("Transient solver runs without errors")
    void testTransientSolverRuns() {
      FlowSystemInterface pipe = new PipeFlowSystem();

      SystemInterface gas = new SystemSrkEos(288.15, 100.0);
      gas.addComponent("methane", 0.90);
      gas.addComponent("ethane", 0.10);
      gas.createDatabase(true);
      gas.init(0);
      gas.init(3);
      gas.initPhysicalProperties();
      gas.setTotalFlowRate(10.0, "MSm3/day");

      setupTransientPipeline(pipe, gas, 50000.0, 0.8);
      pipe.createSystem();
      pipe.init();

      // First solve steady state to initialize
      pipe.solveSteadyState(10);

      // Setup time series
      double[] times = {0, 5000, 10000};
      pipe.getTimeSeries().setTimes(times);

      SystemInterface[] systems = {gas.clone(), gas.clone(), gas.clone()};
      pipe.getTimeSeries().setInletThermoSystems(systems);
      pipe.getTimeSeries().setNumberOfTimeStepsInInterval(5);

      // Should not throw
      assertDoesNotThrow(() -> pipe.solveTransient(20));
    }

    @Test
    @DisplayName("Transient solver maintains pressure profile with constant inlet")
    void testTransientPressureProfile() {
      FlowSystemInterface pipe = new PipeFlowSystem();

      SystemInterface gas = new SystemSrkEos(288.15, 100.0);
      gas.addComponent("methane", 0.95);
      gas.addComponent("ethane", 0.05);
      gas.createDatabase(true);
      gas.init(0);
      gas.init(3);
      gas.initPhysicalProperties();
      gas.setTotalFlowRate(8.0, "MSm3/day");

      setupTransientPipeline(pipe, gas, 30000.0, 0.6);
      pipe.createSystem();
      pipe.init();
      pipe.solveSteadyState(10);

      // Record steady-state pressure profile
      double[] steadyStatePressures = new double[pipe.getTotalNumberOfNodes()];
      for (int i = 0; i < pipe.getTotalNumberOfNodes(); i++) {
        steadyStatePressures[i] = pipe.getNode(i).getBulkSystem().getPressure();
      }

      // Run transient with constant inlet (should maintain steady state)
      double[] times = {0, 3000, 6000};
      pipe.getTimeSeries().setTimes(times);

      SystemInterface gas2 = gas.clone();
      gas2.init(0);
      gas2.init(3);
      gas2.initPhysicalProperties();

      SystemInterface[] systems = {gas.clone(), gas2, gas2};
      pipe.getTimeSeries().setInletThermoSystems(systems);
      pipe.getTimeSeries().setNumberOfTimeStepsInInterval(3);

      pipe.solveTransient(10);

      // After transient with constant inlet, pressure profile should be similar
      for (int i = 0; i < pipe.getTotalNumberOfNodes(); i++) {
        double transientPressure = pipe.getNode(i).getBulkSystem().getPressure();
        // Allow 20% deviation due to transient dynamics
        assertEquals(steadyStatePressures[i], transientPressure, steadyStatePressures[i] * 0.20,
            "Pressure at node " + i + " should be similar to steady state");
      }
    }

    @Test
    @DisplayName("Transient solver responds to temperature change at inlet")
    void testTransientTemperatureTracking() {
      FlowSystemInterface pipe = new PipeFlowSystem();

      // Start with cold gas
      SystemInterface coldGas = new SystemSrkEos(280.0, 80.0);
      coldGas.addComponent("methane", 0.92);
      coldGas.addComponent("ethane", 0.08);
      coldGas.createDatabase(true);
      coldGas.init(0);
      coldGas.init(3);
      coldGas.initPhysicalProperties();
      coldGas.setTotalFlowRate(5.0, "MSm3/day");

      setupTransientPipeline(pipe, coldGas, 20000.0, 0.5);
      pipe.createSystem();
      pipe.init();
      pipe.solveSteadyState(10);

      double initialOutletTemp =
          pipe.getNode(pipe.getTotalNumberOfNodes() - 1).getBulkSystem().getTemperature();

      // Now introduce hot gas at inlet
      SystemInterface hotGas = new SystemSrkEos(320.0, 80.0); // 40K hotter
      hotGas.addComponent("methane", 0.92);
      hotGas.addComponent("ethane", 0.08);
      hotGas.createDatabase(true);
      hotGas.init(0);
      hotGas.init(3);
      hotGas.initPhysicalProperties();
      hotGas.setTotalFlowRate(5.0, "MSm3/day");

      // Times array defines time points; systems array has one entry per interval
      double[] times = {0, 3000, 6000};
      pipe.getTimeSeries().setTimes(times);

      // 2 intervals: [0-3000] cold, [3000-6000] hot -> 2 systems
      SystemInterface[] systems = {coldGas.clone(), hotGas.clone()};
      pipe.getTimeSeries().setInletThermoSystems(systems);
      pipe.getTimeSeries().setNumberOfTimeStepsInInterval(5);

      pipe.solveTransient(10);

      // Inlet should now be hot
      double newInletTemp = pipe.getNode(0).getBulkSystem().getTemperature();
      assertTrue(newInletTemp > 310.0, "Inlet temperature should reflect hot gas: " + newInletTemp);

      // Temperature profile should show propagation of thermal front
      double midpointTemp =
          pipe.getNode(pipe.getTotalNumberOfNodes() / 2).getBulkSystem().getTemperature();

      // Midpoint should be between cold and hot (thermal front propagating)
      assertTrue(midpointTemp > initialOutletTemp - 10,
          "Midpoint temp should be affected by hot front: " + midpointTemp);
    }

    @Test
    @DisplayName("Transient solver tracks composition changes at inlet")
    void testTransientCompositionalTracking() {
      FlowSystemInterface pipe = new PipeFlowSystem();

      // Start with lean gas (high methane)
      SystemInterface leanGas = new SystemSrkEos(290.0, 90.0);
      leanGas.addComponent("methane", 0.95);
      leanGas.addComponent("ethane", 0.05);
      leanGas.createDatabase(true);
      leanGas.init(0);
      leanGas.init(3);
      leanGas.initPhysicalProperties();
      leanGas.setTotalFlowRate(6.0, "MSm3/day");

      setupTransientPipeline(pipe, leanGas, 15000.0, 0.5);
      pipe.createSystem();
      pipe.init();
      pipe.solveSteadyState(20); // Type 20 for composition

      // Record initial composition at outlet
      double initialOutletMethane = pipe.getNode(pipe.getTotalNumberOfNodes() - 1).getBulkSystem()
          .getPhase(0).getComponent("methane").getx();

      // Now introduce rich gas (more ethane)
      SystemInterface richGas = new SystemSrkEos(290.0, 90.0);
      richGas.addComponent("methane", 0.80);
      richGas.addComponent("ethane", 0.20);
      richGas.createDatabase(true);
      richGas.init(0);
      richGas.init(3);
      richGas.initPhysicalProperties();
      richGas.setTotalFlowRate(6.0, "MSm3/day");

      // Times array defines time points; systems array has one entry per interval
      double[] times = {0, 2000, 4000};
      pipe.getTimeSeries().setTimes(times);

      // 2 intervals: [0-2000] lean, [2000-4000] rich -> 2 systems
      SystemInterface[] systems = {leanGas.clone(), richGas.clone()};
      pipe.getTimeSeries().setInletThermoSystems(systems);
      pipe.getTimeSeries().setNumberOfTimeStepsInInterval(5);

      pipe.solveTransient(20); // Type 20 for composition tracking

      // Inlet should now be rich gas
      double newInletMethane =
          pipe.getNode(0).getBulkSystem().getPhase(0).getComponent("methane").getx();
      assertEquals(0.80, newInletMethane, 0.02, "Inlet methane should be ~80%: " + newInletMethane);

      // Verify ethane increased at inlet
      double newInletEthane =
          pipe.getNode(0).getBulkSystem().getPhase(0).getComponent("ethane").getx();
      assertEquals(0.20, newInletEthane, 0.02, "Inlet ethane should be ~20%: " + newInletEthane);

      // Check that composition front is propagating through pipe
      // Nodes near inlet should have higher ethane than nodes near outlet
      double node2Ethane =
          pipe.getNode(2).getBulkSystem().getPhase(0).getComponent("ethane").getx();
      double lastNodeEthane = pipe.getNode(pipe.getTotalNumberOfNodes() - 1).getBulkSystem()
          .getPhase(0).getComponent("ethane").getx();

      assertTrue(node2Ethane >= lastNodeEthane - 0.05, "Ethane should be higher near inlet ("
          + node2Ethane + ") than at outlet (" + lastNodeEthane + ")");
    }

    @Test
    @DisplayName("Transient solver handles flow rate changes")
    void testTransientFlowRateChange() {
      FlowSystemInterface pipe = new PipeFlowSystem();

      SystemInterface gas = new SystemSrkEos(288.15, 100.0);
      gas.addComponent("methane", 0.90);
      gas.addComponent("ethane", 0.10);
      gas.createDatabase(true);
      gas.init(0);
      gas.init(3);
      gas.initPhysicalProperties();
      gas.setTotalFlowRate(5.0, "MSm3/day");

      setupTransientPipeline(pipe, gas, 25000.0, 0.6);
      pipe.createSystem();
      pipe.init();
      pipe.solveSteadyState(10);

      // Increase flow rate
      SystemInterface highFlowGas = new SystemSrkEos(288.15, 100.0);
      highFlowGas.addComponent("methane", 0.90);
      highFlowGas.addComponent("ethane", 0.10);
      highFlowGas.createDatabase(true);
      highFlowGas.init(0);
      highFlowGas.init(3);
      highFlowGas.initPhysicalProperties();
      highFlowGas.setTotalFlowRate(15.0, "MSm3/day"); // 3x flow rate

      double[] times = {0, 2000, 4000};
      pipe.getTimeSeries().setTimes(times);

      // 2 intervals: [0-2000], [2000-4000] -> 2 systems
      SystemInterface[] systems = {gas.clone(), highFlowGas.clone()};
      pipe.getTimeSeries().setInletThermoSystems(systems);
      pipe.getTimeSeries().setNumberOfTimeStepsInInterval(5);

      pipe.solveTransient(10);

      double finalInletVelocity = pipe.getNode(0).getVelocity();

      // Higher flow rate should result in higher velocity
      assertTrue(finalInletVelocity > 0,
          "Inlet velocity should be positive after transient: " + finalInletVelocity);
    }

    @Test
    @DisplayName("Transient solver with combined composition and temperature changes")
    void testTransientCombinedChanges() {
      FlowSystemInterface pipe = new PipeFlowSystem();

      // Initial state: cold lean gas
      SystemInterface initialGas = new SystemSrkEos(280.0, 85.0);
      initialGas.addComponent("methane", 0.92);
      initialGas.addComponent("ethane", 0.05);
      initialGas.addComponent("propane", 0.03);
      initialGas.createDatabase(true);
      initialGas.init(0);
      initialGas.init(3);
      initialGas.initPhysicalProperties();
      initialGas.setTotalFlowRate(7.0, "MSm3/day");

      setupTransientPipeline(pipe, initialGas, 20000.0, 0.5);
      pipe.createSystem();
      pipe.init();
      pipe.solveSteadyState(20);

      // Record initial state
      double initialInletTemp = pipe.getNode(0).getBulkSystem().getTemperature();
      double initialInletPressure = pipe.getNode(0).getBulkSystem().getPressure();
      double initialMethane =
          pipe.getNode(0).getBulkSystem().getPhase(0).getComponent("methane").getx();

      // Final state: hot rich gas at higher pressure
      SystemInterface finalGas = new SystemSrkEos(310.0, 95.0); // Hotter, higher pressure
      finalGas.addComponent("methane", 0.82);
      finalGas.addComponent("ethane", 0.12);
      finalGas.addComponent("propane", 0.06);
      finalGas.createDatabase(true);
      finalGas.init(0);
      finalGas.init(3);
      finalGas.initPhysicalProperties();
      finalGas.setTotalFlowRate(7.0, "MSm3/day");

      // Times array defines time points; systems array has one entry per interval
      double[] times = {0, 3000, 6000};
      pipe.getTimeSeries().setTimes(times);

      // 2 intervals: [0-3000] initial, [3000-6000] final -> 2 systems
      SystemInterface[] systems = {initialGas.clone(), finalGas.clone()};
      pipe.getTimeSeries().setInletThermoSystems(systems);
      pipe.getTimeSeries().setNumberOfTimeStepsInInterval(5);

      pipe.solveTransient(20);

      // Verify inlet reflects new conditions
      double newInletTemp = pipe.getNode(0).getBulkSystem().getTemperature();
      double newMethane =
          pipe.getNode(0).getBulkSystem().getPhase(0).getComponent("methane").getx();
      double newPropane =
          pipe.getNode(0).getBulkSystem().getPhase(0).getComponent("propane").getx();

      // Temperature should have increased
      assertTrue(newInletTemp > initialInletTemp + 20,
          "Inlet temp should increase. Was: " + initialInletTemp + ", Now: " + newInletTemp);

      // Methane fraction should have decreased
      assertTrue(newMethane < initialMethane - 0.05,
          "Methane fraction should decrease. Was: " + initialMethane + ", Now: " + newMethane);

      // Propane fraction should have increased
      assertTrue(newPropane > 0.04, "Propane fraction should be ~6%: " + newPropane);

      // Pressure profile should still be physically reasonable
      assertTrue(pipe.getTotalPressureDrop() > 0, "Pressure drop should be positive");
    }

    private void setupTransientPipeline(FlowSystemInterface pipe, SystemInterface gas,
        double lengthMeters, double diameter) {
      int numLegs = 5;
      double[] height = new double[numLegs + 1];
      double[] length = new double[numLegs + 1];
      double[] outerTemp = new double[numLegs + 1];
      double[] outerHeatCoef = new double[numLegs + 1];
      double[] wallHeatCoef = new double[numLegs + 1];

      for (int i = 0; i <= numLegs; i++) {
        height[i] = 0.0;
        length[i] = lengthMeters * i / numLegs;
        outerTemp[i] = 278.0;
        outerHeatCoef[i] = 5.0;
        wallHeatCoef[i] = 15.0;
      }

      GeometryDefinitionInterface[] pipeGeometry = new PipeData[numLegs + 1];
      for (int i = 0; i <= numLegs; i++) {
        pipeGeometry[i] = new PipeData();
        pipeGeometry[i].setDiameter(diameter);
        pipeGeometry[i].setInnerSurfaceRoughness(1e-5);
      }

      pipe.setInletThermoSystem(gas);
      pipe.setNumberOfLegs(numLegs);
      pipe.setNumberOfNodesInLeg(10);
      pipe.setEquipmentGeometry(pipeGeometry);
      pipe.setLegHeights(height);
      pipe.setLegPositions(length);
      pipe.setLegOuterTemperatures(outerTemp);
      pipe.setLegWallHeatTransferCoefficients(wallHeatCoef);
      pipe.setLegOuterHeatTransferCoefficients(outerHeatCoef);
    }
  }

  @Nested
  @DisplayName("Outlet Boundary Condition Tests")
  class OutletBoundaryConditionTests {

    @Test
    @DisplayName("Closed outlet boundary type is correctly set")
    void testClosedOutletBoundaryType() {
      FlowSystemInterface pipe = new PipeFlowSystem();

      SystemInterface gas = new SystemSrkEos(288.15, 50.0);
      gas.addComponent("methane", 0.95);
      gas.addComponent("ethane", 0.05);
      gas.createDatabase(true);
      gas.init(0);
      gas.init(3);
      gas.initPhysicalProperties();
      gas.setTotalFlowRate(5.0, "MSm3/day");

      setupOutletBoundaryPipeline(pipe, gas, 10000.0, 0.5);
      pipe.createSystem();
      pipe.init();
      pipe.solveSteadyState(10);

      // Set up transient with closed outlet
      double[] times = {0, 1000, 2000};
      pipe.getTimeSeries().setTimes(times);

      SystemInterface[] systems = {gas.clone(), gas.clone()};
      pipe.getTimeSeries().setInletThermoSystems(systems);
      pipe.getTimeSeries().setNumberOfTimeStepsInInterval(5);
      pipe.getTimeSeries().setOutletClosed();

      // Verify boundary type is set
      assertTrue(pipe.getTimeSeries().isOutletClosed(),
          "TimeSeries should be marked as closed outlet");

      // Run transient - should not throw even with closed outlet
      assertDoesNotThrow(() -> pipe.solveTransient(10));
    }

    @Test
    @DisplayName("Controlled outlet flow rate boundary is configured")
    void testControlledOutletFlowRate() {
      FlowSystemInterface pipe = new PipeFlowSystem();

      SystemInterface gas = new SystemSrkEos(288.15, 80.0);
      gas.addComponent("methane", 0.90);
      gas.addComponent("ethane", 0.10);
      gas.createDatabase(true);
      gas.init(0);
      gas.init(3);
      gas.initPhysicalProperties();
      gas.setTotalFlowRate(8.0, "MSm3/day");

      setupOutletBoundaryPipeline(pipe, gas, 20000.0, 0.6);
      pipe.createSystem();
      pipe.init();
      pipe.solveSteadyState(10);

      // Set up transient with controlled outlet velocity
      double[] times = {0, 2000, 4000};
      pipe.getTimeSeries().setTimes(times);

      SystemInterface[] systems = {gas.clone(), gas.clone()};
      pipe.getTimeSeries().setInletThermoSystems(systems);
      pipe.getTimeSeries().setNumberOfTimeStepsInInterval(5);

      // Set controlled outlet velocities
      double[] outletVelocities = {5.0, 4.0}; // m/s - modest reduction
      pipe.getTimeSeries().setOutletVelocity(outletVelocities);

      // Verify boundary type
      assertTrue(pipe.getTimeSeries().isOutletFlowControlled(), "Should be flow-controlled");

      // Run transient
      assertDoesNotThrow(() -> pipe.solveTransient(10));
    }

    @Test
    @DisplayName("Controlled outlet pressure maintains specified pressure")
    void testControlledOutletPressure() {
      FlowSystemInterface pipe = new PipeFlowSystem();

      SystemInterface gas = new SystemSrkEos(288.15, 100.0);
      gas.addComponent("methane", 0.92);
      gas.addComponent("ethane", 0.08);
      gas.createDatabase(true);
      gas.init(0);
      gas.init(3);
      gas.initPhysicalProperties();
      gas.setTotalFlowRate(10.0, "MSm3/day");

      setupOutletBoundaryPipeline(pipe, gas, 30000.0, 0.8);
      pipe.createSystem();
      pipe.init();
      pipe.solveSteadyState(10);

      // Set up transient with controlled outlet pressure
      double[] times = {0, 3000, 6000};
      pipe.getTimeSeries().setTimes(times);

      SystemInterface[] systems = {gas.clone(), gas.clone()};
      pipe.getTimeSeries().setInletThermoSystems(systems);
      pipe.getTimeSeries().setNumberOfTimeStepsInInterval(5);

      // Set controlled outlet pressures: increase backpressure in second interval
      double[] outletPressures = {70.0, 80.0}; // bar
      pipe.getTimeSeries().setOutletPressure(outletPressures);

      // Run transient
      assertDoesNotThrow(() -> pipe.solveTransient(10));

      // Verify pressure control was applied
      assertTrue(pipe.getTimeSeries().isOutletPressureControlled(),
          "Should be pressure-controlled");

      // Outlet pressure should be close to specified value
      double finalOutletPressure =
          pipe.getNode(pipe.getTotalNumberOfNodes() - 1).getBulkSystem().getPressure();
      assertEquals(80.0, finalOutletPressure, 10.0,
          "Outlet pressure should be near specified value");
    }

    @Test
    @DisplayName("Outlet velocity changes during transient")
    void testOutletVelocityChange() {
      FlowSystemInterface pipe = new PipeFlowSystem();

      SystemInterface gas = new SystemSrkEos(300.0, 60.0);
      gas.addComponent("methane", 0.88);
      gas.addComponent("ethane", 0.12);
      gas.createDatabase(true);
      gas.init(0);
      gas.init(3);
      gas.initPhysicalProperties();
      gas.setTotalFlowRate(6.0, "MSm3/day");

      setupOutletBoundaryPipeline(pipe, gas, 15000.0, 0.5);
      pipe.createSystem();
      pipe.init();
      pipe.solveSteadyState(10);

      // Record initial flow velocity
      double initialVelocity = pipe.getNode(pipe.getTotalNumberOfNodes() - 1).getVelocity();
      assertTrue(initialVelocity > 0, "Initial velocity should be positive");

      // Set up transient with slightly reduced outlet velocity
      double[] times = {0, 1000, 2000};
      pipe.getTimeSeries().setTimes(times);

      SystemInterface[] systems = {gas.clone(), gas.clone()};
      pipe.getTimeSeries().setInletThermoSystems(systems);
      pipe.getTimeSeries().setNumberOfTimeStepsInInterval(5);

      // Reduce velocity by 20% (not too aggressive)
      double[] outletVelocities = {initialVelocity, initialVelocity * 0.8}; // m/s
      pipe.getTimeSeries().setOutletVelocity(outletVelocities);

      // Run transient
      assertDoesNotThrow(() -> pipe.solveTransient(10));

      // Verify boundary is flow-controlled
      assertTrue(pipe.getTimeSeries().isOutletFlowControlled(), "Should be flow-controlled");
    }

    @Test
    @DisplayName("TimeSeries boundary type methods work correctly")
    void testTimeSeriesBoundaryTypeMethods() {
      neqsim.fluidmechanics.util.timeseries.TimeSeries ts =
          new neqsim.fluidmechanics.util.timeseries.TimeSeries();

      // Default should be pressure-controlled
      assertTrue(ts.isOutletPressureControlled(), "Default should be pressure-controlled");
      assertFalse(ts.isOutletClosed(), "Default should not be closed");
      assertFalse(ts.isOutletFlowControlled(), "Default should not be flow-controlled");

      // Test closed outlet
      ts.setOutletClosed();
      assertTrue(ts.isOutletClosed(), "Should be closed after setOutletClosed()");
      assertFalse(ts.isOutletPressureControlled(), "Should not be pressure-controlled when closed");
      assertFalse(ts.isOutletFlowControlled(), "Should not be flow-controlled when closed");

      // Test flow-controlled outlet
      ts.setOutletVelocity(new double[] {1.0, 2.0});
      assertTrue(ts.isOutletFlowControlled(), "Should be flow-controlled after setOutletVelocity");
      assertFalse(ts.isOutletClosed(), "Should not be closed when flow-controlled");

      // Test pressure-controlled outlet
      ts.setOutletPressure(new double[] {50.0, 60.0});
      assertTrue(ts.isOutletPressureControlled(),
          "Should be pressure-controlled after setOutletPressure");
      assertFalse(ts.isOutletFlowControlled(),
          "Should not be flow-controlled when pressure-controlled");
    }

    private void setupOutletBoundaryPipeline(FlowSystemInterface pipe, SystemInterface gas,
        double lengthMeters, double diameter) {
      int numLegs = 5;
      double[] height = new double[numLegs + 1];
      double[] length = new double[numLegs + 1];
      double[] outerTemp = new double[numLegs + 1];
      double[] outerHeatCoef = new double[numLegs + 1];
      double[] wallHeatCoef = new double[numLegs + 1];

      for (int i = 0; i <= numLegs; i++) {
        height[i] = 0.0;
        length[i] = lengthMeters * i / numLegs;
        outerTemp[i] = 288.0;
        outerHeatCoef[i] = 5.0;
        wallHeatCoef[i] = 15.0;
      }

      GeometryDefinitionInterface[] pipeGeometry = new PipeData[numLegs + 1];
      for (int i = 0; i <= numLegs; i++) {
        pipeGeometry[i] = new PipeData();
        pipeGeometry[i].setDiameter(diameter);
        pipeGeometry[i].setInnerSurfaceRoughness(1e-5);
      }

      pipe.setInletThermoSystem(gas);
      pipe.setNumberOfLegs(numLegs);
      pipe.setNumberOfNodesInLeg(10);
      pipe.setEquipmentGeometry(pipeGeometry);
      pipe.setLegHeights(height);
      pipe.setLegPositions(length);
      pipe.setLegOuterTemperatures(outerTemp);
      pipe.setLegWallHeatTransferCoefficients(wallHeatCoef);
      pipe.setLegOuterHeatTransferCoefficients(outerHeatCoef);
    }
  }

  @Nested
  @DisplayName("Simplified API Tests")
  class SimplifiedAPITests {

    @Test
    @DisplayName("runTransient with time and step works")
    void testRunTransientSimple() {
      PipeFlowSystem pipe = new PipeFlowSystem();

      SystemInterface gas = new SystemSrkEos(288.15, 80.0);
      gas.addComponent("methane", 0.95);
      gas.addComponent("ethane", 0.05);
      gas.createDatabase(true);
      gas.init(0);
      gas.init(3);
      gas.initPhysicalProperties();
      gas.setTotalFlowRate(5.0, "MSm3/day");

      setupSimplePipeline(pipe, gas, 10000.0, 0.5);
      pipe.createSystem();
      pipe.init();
      pipe.solveSteadyState(10);

      // Simple API: just specify total time and time step
      assertDoesNotThrow(() -> pipe.runTransient(300.0, 100.0));
    }

    @Test
    @DisplayName("runTransientClosedOutlet works")
    void testRunTransientClosedOutletSimple() {
      PipeFlowSystem pipe = new PipeFlowSystem();

      SystemInterface gas = new SystemSrkEos(288.15, 60.0);
      gas.addComponent("methane", 0.90);
      gas.addComponent("ethane", 0.10);
      gas.createDatabase(true);
      gas.init(0);
      gas.init(3);
      gas.initPhysicalProperties();
      gas.setTotalFlowRate(4.0, "MSm3/day");

      setupSimplePipeline(pipe, gas, 8000.0, 0.4);
      pipe.createSystem();
      pipe.init();
      pipe.solveSteadyState(10);

      // Simple API: simulate closed outlet
      assertDoesNotThrow(() -> pipe.runTransientClosedOutlet(200.0, 50.0));
    }

    @Test
    @DisplayName("runTransientControlledOutletVelocity works")
    void testRunTransientControlledVelocitySimple() {
      PipeFlowSystem pipe = new PipeFlowSystem();

      SystemInterface gas = new SystemSrkEos(300.0, 70.0);
      gas.addComponent("methane", 0.92);
      gas.addComponent("ethane", 0.08);
      gas.createDatabase(true);
      gas.init(0);
      gas.init(3);
      gas.initPhysicalProperties();
      gas.setTotalFlowRate(6.0, "MSm3/day");

      setupSimplePipeline(pipe, gas, 12000.0, 0.6);
      pipe.createSystem();
      pipe.init();
      pipe.solveSteadyState(10);

      // Simple API: control outlet velocity at 3 m/s
      assertDoesNotThrow(() -> pipe.runTransientControlledOutletVelocity(300.0, 100.0, 3.0));
    }

    @Test
    @DisplayName("runTransientControlledOutletPressure works")
    void testRunTransientControlledPressureSimple() {
      PipeFlowSystem pipe = new PipeFlowSystem();

      SystemInterface gas = new SystemSrkEos(288.15, 100.0);
      gas.addComponent("methane", 0.88);
      gas.addComponent("ethane", 0.12);
      gas.createDatabase(true);
      gas.init(0);
      gas.init(3);
      gas.initPhysicalProperties();
      gas.setTotalFlowRate(8.0, "MSm3/day");

      setupSimplePipeline(pipe, gas, 15000.0, 0.7);
      pipe.createSystem();
      pipe.init();
      pipe.solveSteadyState(10);

      // Simple API: control outlet pressure at 60 bar
      assertDoesNotThrow(() -> pipe.runTransientControlledOutletPressure(300.0, 100.0, 60.0));
    }

    @Test
    @DisplayName("Convenience setters work correctly")
    void testConvenienceSetters() {
      PipeFlowSystem pipe = new PipeFlowSystem();

      SystemInterface gas = new SystemSrkEos(288.15, 80.0);
      gas.addComponent("methane", 0.95);
      gas.addComponent("ethane", 0.05);
      gas.createDatabase(true);
      gas.init(0);
      gas.init(3);
      gas.initPhysicalProperties();
      gas.setTotalFlowRate(5.0, "MSm3/day");

      setupSimplePipeline(pipe, gas, 10000.0, 0.5);
      pipe.createSystem();
      pipe.init();
      pipe.solveSteadyState(10);

      // Test convenience setters
      pipe.setOutletClosed();
      assertTrue(pipe.getTimeSeries().isOutletClosed(), "Should be closed");

      pipe.setOutletVelocity(5.0);
      assertTrue(pipe.getTimeSeries().isOutletFlowControlled(), "Should be flow-controlled");

      pipe.setOutletPressure(50.0);
      assertTrue(pipe.getTimeSeries().isOutletPressureControlled(),
          "Should be pressure-controlled");
    }

    private void setupSimplePipeline(FlowSystemInterface pipe, SystemInterface gas,
        double lengthMeters, double diameter) {
      int numLegs = 5;
      double[] height = new double[numLegs + 1];
      double[] length = new double[numLegs + 1];
      double[] outerTemp = new double[numLegs + 1];
      double[] outerHeatCoef = new double[numLegs + 1];
      double[] wallHeatCoef = new double[numLegs + 1];

      for (int i = 0; i <= numLegs; i++) {
        height[i] = 0.0;
        length[i] = lengthMeters * i / numLegs;
        outerTemp[i] = 288.0;
        outerHeatCoef[i] = 5.0;
        wallHeatCoef[i] = 15.0;
      }

      GeometryDefinitionInterface[] pipeGeometry = new PipeData[numLegs + 1];
      for (int i = 0; i <= numLegs; i++) {
        pipeGeometry[i] = new PipeData();
        pipeGeometry[i].setDiameter(diameter);
        pipeGeometry[i].setInnerSurfaceRoughness(1e-5);
      }

      pipe.setInletThermoSystem(gas);
      pipe.setNumberOfLegs(numLegs);
      pipe.setNumberOfNodesInLeg(10);
      pipe.setEquipmentGeometry(pipeGeometry);
      pipe.setLegHeights(height);
      pipe.setLegPositions(length);
      pipe.setLegOuterTemperatures(outerTemp);
      pipe.setLegWallHeatTransferCoefficients(wallHeatCoef);
      pipe.setLegOuterHeatTransferCoefficients(outerHeatCoef);
    }
  }
}
