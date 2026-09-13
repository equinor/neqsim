package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.TwoFluidPipe;
import neqsim.process.equipment.pipeline.TwoFluidPipe.BoundaryCondition;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Geometry and source integrals use explicit cell faces; these checks do not claim a transient hydrostatic fixed point.
 */
class TwoFluidCellFaceTerrainTest {
  @Test
  void uniformCellsUseFaceRisesIncludingBothEndCells() throws Exception {
    double[] elevations = { 12.0, 14.0, 13.0, 13.0, 11.0 };
    TwoFluidPipe pipe = pipe(new double[] { 2.0, 2.0, 2.0, 2.0 }, false);
    pipe.setCellFaceElevationProfile(elevations);
    initialize(pipe);
    TwoFluidSection[] cells = pipe.getSectionSnapshots();
    for (int cell = 0; cell < cells.length; cell++) {
      assertEquals(1.0 + 2.0 * cell, cells[cell].getPosition(), 0.0);
      assertEquals(0.5 * (elevations[cell] + elevations[cell + 1]), cells[cell].getElevation(), 0.0);
      assertEquals(elevations[cell + 1] - elevations[cell],
          cells[cell].getLength() * Math.sin(cells[cell].getInclination()), 1.0e-14);
    }
    assertGravityLedger(cells, elevations, 0.0);
    elevations[0] = -100.0;
    double[] exposed = pipe.getCellFaceElevationProfile();
    exposed[1] = -100.0;
    assertArrayEquals(new double[] { 12.0, 14.0, 13.0, 13.0, 11.0 }, pipe.getCellFaceElevationProfile(), 0.0);
    assertNull(pipe.getElevationProfile());
  }

  @Test
  void nonuniformRefinementPreservesSignedGravityAndItsEnergyWork() throws Exception {
    double[] baseLengths = { 1.0, 2.0, 4.0, 1.0 };
    double[] baseElevations = { 3.0, 4.0, 3.0, 5.0, 4.0 };
    for (int subdivision : new int[] { 1, 2, 4 }) {
      double[] lengths = new double[baseLengths.length * subdivision];
      double[] elevations = new double[lengths.length + 1];
      for (int base = 0; base < baseLengths.length; base++) {
        for (int part = 0; part < subdivision; part++) {
          int cell = base * subdivision + part;
          lengths[cell] = baseLengths[base] / subdivision;
          elevations[cell] = baseElevations[base]
              + (baseElevations[base + 1] - baseElevations[base]) * part / subdivision;
        }
      }
      elevations[elevations.length - 1] = baseElevations[baseElevations.length - 1];
      TwoFluidPipe pipe = pipe(lengths);
      pipe.setCellFaceElevationProfile(elevations);
      initialize(pipe);
      TwoFluidSection[] cells = pipe.getSectionSnapshots();
      double position = 0.0;
      for (int cell = 0; cell < cells.length; cell++) {
        assertEquals(position + 0.5 * lengths[cell], cells[cell].getPosition(), 0.0);
        position += lengths[cell];
      }
      assertGravityLedger(cells, elevations, 0.75);
    }
  }

  @Test
  void midpointPressureMarchAndBothBoundaryOffsetsMatchConstantDensityHydrostatics() throws Exception {
    double[] lengths = { 1.0, 2.0, 4.0, 1.0 };
    double[] elevations = { 3.0, 4.0, 3.0, 5.0, 4.0 };
    TwoFluidPipe pipe = pipe(lengths);
    pipe.setCellFaceElevationProfile(elevations);
    initialize(pipe);
    TwoFluidSection[] cells = cells(pipe);
    for (TwoFluidSection cell : cells) {
      setConstantPhases(cell, new double[] { 0.0, 0.0, 1.0 }, 0.0);
    }
    double inletPressure = 5.0e5;
    cells[0].setPressure(inletPressure - 1000.0 * 9.81 * (cells[0].getElevation() - elevations[0]));
    Method march = method("marchPressure", TwoFluidSection.class, TwoFluidSection.class);
    for (int cell = 1; cell < cells.length; cell++) {
      cells[cell].setPressure((Double) march.invoke(pipe, cells[cell - 1], cells[cell]));
      assertEquals(inletPressure - 1000.0 * 9.81 * (cells[cell].getElevation() - elevations[0]),
          cells[cell].getPressure(), 1.0e-9);
    }
    assertEquals(inletPressure - 1000.0 * 9.81 * (elevations[elevations.length - 1] - elevations[0]),
        (Double) method("steadyOutletFacePressure").invoke(pipe), 1.0e-9);

    double outletPressure = 4.0e5;
    pipe.setOutletPressure(outletPressure);
    method("applySteadyStatePressureBoundary").invoke(pipe);
    for (TwoFluidSection cell : cells) {
      assertEquals(outletPressure + 1000.0 * 9.81 * (elevations[elevations.length - 1] - cell.getElevation()),
          cell.getPressure(), 1.0e-9);
    }
    assertEquals(outletPressure, (Double) method("steadyOutletFacePressure").invoke(pipe), 1.0e-9);

    pipe.setOutletBoundaryCondition(BoundaryCondition.STREAM_CONNECTED);
    pipe.setInletBoundaryCondition(BoundaryCondition.CONSTANT_PRESSURE);
    pipe.setInletPressure(inletPressure);
    method("applySteadyStatePressureBoundary").invoke(pipe);
    for (TwoFluidSection cell : cells) {
      assertEquals(inletPressure - 1000.0 * 9.81 * (cell.getElevation() - elevations[0]), cell.getPressure(), 1.0e-9);
    }
  }

  @Test
  void legacySamplesRetainTheirHistoricalAnglesAndWholeCellPressureMarch() throws Exception {
    TwoFluidPipe pipe = pipe(new double[] { 2.0, 2.0, 2.0, 2.0 });
    pipe.setCellFaceElevationProfile(new double[] { 0.0, 1.0, 1.0, 0.0, 0.0 });
    pipe.setElevationProfile(new double[] { 0.0, 1.0, 1.0, 0.0 });
    assertNull(pipe.getCellFaceElevationProfile());
    initialize(pipe);
    TwoFluidSection[] cells = cells(pipe);
    double[] historicalSines = { 0.5, 0.0, -0.5, -0.5 };
    double[] historicalElevations = { 0.0, 1.0, 1.0, 0.0 };
    Method march = method("marchPressure", TwoFluidSection.class, TwoFluidSection.class);
    Method historicalMarch = method("marchPressure", TwoFluidSection.class);
    for (int cell = 0; cell < cells.length; cell++) {
      assertEquals(historicalElevations[cell], cells[cell].getElevation(), 0.0);
      assertEquals(historicalSines[cell], Math.sin(cells[cell].getInclination()), 1.0e-15);
      setConstantPhases(cells[cell], new double[] { 0.0, 0.0, 1.0 }, 0.0);
      if (cell > 0) {
        assertEquals((Double) historicalMarch.invoke(pipe, cells[cell - 1]),
            (Double) march.invoke(pipe, cells[cell - 1], cells[cell]), 0.0);
      }
    }
    pipe.setOutletPressure(4.0e5);
    method("applySteadyStatePressureBoundary").invoke(pipe);
    assertEquals(4.0e5, cells[cells.length - 1].getPressure(), 0.0);
  }

  @Test
  void outletPublicationUsesItsBoundaryPressureAndOnlyExtrapolatesSteadyMidpoints() throws Exception {
    TwoFluidPipe pipe = pipe(new double[] { 2.0, 2.0 });
    pipe.setCellFaceElevationProfile(new double[] { 0.0, 1.0, 2.0 });
    initialize(pipe);
    TwoFluidSection last = cells(pipe)[1];
    setConstantPhases(last, new double[] { 0.0, 0.0, 1.0 }, 0.0);
    last.setPressure(4.5e5);
    pipe.setOutletPressure(4.0e5);
    method("updateOutletStream").invoke(pipe);
    assertEquals(4.0e5, pipe.getOutletStream().getFluid().getPressure("Pa"), 1.0e-9);

    pipe.setOutletBoundaryCondition(BoundaryCondition.STREAM_CONNECTED);
    method("updateOutletStream").invoke(pipe);
    assertEquals(4.5e5, pipe.getOutletStream().getFluid().getPressure("Pa"), 1.0e-9);
    method("updateOutletStream", boolean.class).invoke(pipe, true);
    assertEquals(4.5e5 - 1000.0 * 9.81 * 0.5, pipe.getOutletStream().getFluid().getPressure("Pa"), 1.0e-9);

    pipe.setElevationProfile(new double[] { 0.0, 1.0 });
    pipe.setOutletBoundaryCondition(BoundaryCondition.CONSTANT_PRESSURE);
    method("updateOutletStream", boolean.class).invoke(pipe, true);
    assertEquals(4.5e5, pipe.getOutletStream().getFluid().getPressure("Pa"), 1.0e-9);
  }

  @Test
  void invalidTerrainAndImplicitRemeshingCannotReplaceAValidProfileOrAcceptedCells() throws Exception {
    TwoFluidPipe pipe = pipe(new double[] { 2.0, 2.0 });
    double[] valid = { 0.0, 1.0, 2.0 };
    pipe.setCellFaceElevationProfile(valid);
    initialize(pipe);
    TwoFluidSection[] before = pipe.getSectionSnapshots();
    double[][] invalid = { null, { 0.0, 1.0 }, { 0.0, Double.NaN, 2.0 }, { 0.0, Double.POSITIVE_INFINITY, 2.0 },
        { 0.0, 2.01, 2.0 } };
    for (double[] candidate : invalid) {
      assertThrows(IllegalArgumentException.class, () -> pipe.setCellFaceElevationProfile(candidate));
      assertArrayEquals(valid, pipe.getCellFaceElevationProfile(), 0.0);
    }
    assertThrows(IllegalStateException.class, () -> pipe.generateRefinedMesh(4, 2.0));
    assertArrayEquals(new double[] { 2.0, 2.0 }, pipe.getSectionLengths(), 0.0);
    pipe.setLength(5.0);
    assertThrows(IllegalArgumentException.class, () -> pipe.run(UUID.randomUUID()));
    TwoFluidSection[] after = pipe.getSectionSnapshots();
    for (int cell = 0; cell < before.length; cell++) {
      assertArrayEquals(before[cell].getStateVector(), after[cell].getStateVector(), 0.0);
      assertEquals(before[cell].getPressure(), after[cell].getPressure(), 0.0);
      assertEquals(before[cell].getInclination(), after[cell].getInclination(), 0.0);
    }
    for (double[] lengths : new double[][] { { 0.0, 4.0 }, { -1.0, 5.0 }, { Double.NaN, 4.0 }, { 1.0, 1.0 } }) {
      pipe.setLength(4.0);
      pipe.setSectionLengths(lengths);
      assertThrows(IllegalArgumentException.class, () -> pipe.setCellFaceElevationProfile(valid));
    }
  }

  private static void assertGravityLedger(TwoFluidSection[] cells, double[] elevations, double velocity) {
    double[] fractions = { 0.4, 0.35, 0.25 };
    double[] densities = { 10.0, 800.0, 1000.0 };
    for (TwoFluidSection cell : cells) {
      setConstantPhases(cell, fractions, velocity);
    }
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    equations.setIncludeMassTransfer(false);
    equations.setIncludeEnergyEquation(true);
    equations.setHeatTransferCoefficient(0.0);
    double[][] sources = equations.calcSourceTerms(cells);
    double energyWork = 0.0;
    for (int phase = 0; phase < 3; phase++) {
      double integrated = 0.0;
      for (int cell = 0; cell < cells.length; cell++) {
        double force = -fractions[phase] * densities[phase] * 9.81 * cells[cell].getArea()
            * (elevations[cell + 1] - elevations[cell]);
        assertEquals(force, sources[cell][phase + 3] * cells[cell].getLength(), 1.0e-11);
        assertEquals(0.0, sources[cell][phase], 0.0);
        integrated += sources[cell][phase + 3] * cells[cell].getLength();
      }
      double totalForce = -fractions[phase] * densities[phase] * 9.81 * cells[0].getArea()
          * (elevations[elevations.length - 1] - elevations[0]);
      assertEquals(totalForce, integrated, 1.0e-10);
      energyWork += totalForce * velocity;
    }
    double integratedWork = 0.0;
    for (int cell = 0; cell < cells.length; cell++) {
      integratedWork += sources[cell][6] * cells[cell].getLength();
    }
    assertEquals(energyWork, integratedWork, 1.0e-10);
  }

  private static void setConstantPhases(TwoFluidSection cell, double[] fractions, double velocity) {
    cell.setGasDensity(10.0);
    cell.setOilDensity(800.0);
    cell.setWaterDensity(1000.0);
    cell.setPressure(5.0e5);
    double[] state = new double[7];
    double[] densities = { 10.0, 800.0, 1000.0 };
    for (int phase = 0; phase < 3; phase++) {
      state[phase] = fractions[phase] * densities[phase] * cell.getArea();
      state[phase + 3] = state[phase] * velocity;
    }
    state[6] = 1.0e7;
    cell.setConservativeEndpoint(state, 1.0e-12);
    cell.setFlowRegime(FlowRegime.STRATIFIED_SMOOTH);
    cell.setRegimeWeights(null);
    cell.setGasWallShear(0.0);
    cell.setLiquidWallShear(0.0);
    cell.setInterfacialShear(0.0);
  }

  private static TwoFluidPipe pipe(double[] lengths) {
    return pipe(lengths, true);
  }

  private static TwoFluidPipe pipe(double[] lengths, boolean nonuniform) {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 5.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream stream = new Stream("cell-face terrain feed", fluid);
    stream.setFlowRate(0.01, "kg/sec");
    stream.run();
    TwoFluidPipe pipe = new TwoFluidPipe("cell-face terrain", stream);
    double length = 0.0;
    for (double cellLength : lengths) {
      length += cellLength;
    }
    pipe.setLength(length);
    pipe.setDiameter(0.2);
    pipe.setNumberOfSections(lengths.length);
    if (nonuniform) {
      pipe.setSectionLengths(lengths);
    }
    pipe.setIncludeEnergyEquation(false);
    pipe.setIncludeMassTransfer(false);
    return pipe;
  }

  private static void initialize(TwoFluidPipe pipe) throws Exception {
    method("initializeSections").invoke(pipe);
  }

  private static TwoFluidSection[] cells(TwoFluidPipe pipe) throws Exception {
    Field field = TwoFluidPipe.class.getDeclaredField("sections");
    field.setAccessible(true);
    return (TwoFluidSection[]) field.get(pipe);
  }

  private static Method method(String name, Class<?>... parameterTypes) throws Exception {
    Method method = TwoFluidPipe.class.getDeclaredMethod(name, parameterTypes);
    method.setAccessible(true);
    return method;
  }
}
