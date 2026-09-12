package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidConservationEquations.TransactionalEvaluation;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.AUSMPlusFluxCalculator;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.AUSMPlusFluxCalculator.PhaseFlux;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.AUSMPlusFluxCalculator.PhaseState;

/** Conservation and independent spatial pressure-mode checks for the optional internal-face interpolation. */
class TwoFluidPressureInterpolationTest {
  @Test
  void zeroCorrectionRetainsEveryOriginalFluxBit() {
    AUSMPlusFluxCalculator calculator = new AUSMPlusFluxCalculator();
    for (double velocity : new double[] { -20.0, -1.0, -0.0, 0.0, 1.0, 20.0 }) {
      PhaseState left = new PhaseState(2.0, velocity, 1.2e5, 10.0, 1.0e5, 0.3);
      PhaseState right = new PhaseState(5.0, 0.5 * velocity, 1.0e5, 12.0, 2.0e5, 0.7);
      PhaseFlux legacy = calculator.calcPhaseFlux(left, right, 0.2);
      assertFluxBits(legacy, calculator.calcPhaseFlux(left, right, 0.2, 0.0));
      assertFluxBits(legacy, calculator.calcPhaseFlux(left, right, 0.2, -0.0));
    }

    TwoFluidConservationEquations equations = equations();
    assertEquals(0.0, equations.getPressureInterpolationTimeScale(), 0.0);
    TwoFluidSection[] cells = checkerboard(new double[] { 0.4, 0.3, 0.3 });
    double[][] baseline = equations.evaluateTransactional(copy(cells), 1.0).getRates();
    equations.setPressureInterpolationTimeScale(0.01);
    equations.evaluateTransactional(copy(cells), 1.0);
    equations.setPressureInterpolationTimeScale(0.0);
    assertMatrixEquals(baseline, equations.evaluateTransactional(copy(cells), 1.0).getRates(), 0.0);
  }

  @Test
  void correctedVelocitySelectsTheSameSignedDonorForMassMomentumAndEnergy() {
    AUSMPlusFluxCalculator calculator = new AUSMPlusFluxCalculator();
    PhaseState left = new PhaseState(2.0, 1.0, 1.2e5, 10.0, 100.0, 0.25);
    PhaseState right = new PhaseState(5.0, 1.0, 1.0e5, 10.0, 700.0, 0.75);
    double area = 0.2;
    PhaseFlux original = calculator.calcPhaseFlux(left, right, area);
    double initialFaceVelocity = original.massFlux / (left.holdup * left.density * area);
    for (double correction : new double[] { -3.0, 3.0 }) {
      double faceVelocity = initialFaceVelocity + correction;
      PhaseState donor = faceVelocity >= 0.0 ? left : right;
      PhaseFlux corrected = calculator.calcPhaseFlux(left, right, area, correction);
      double expectedMass = donor.holdup * donor.density * faceVelocity * area;
      assertEquals(expectedMass, corrected.massFlux, 1.0e-14);
      assertEquals(expectedMass * donor.velocity + original.interfaceHoldup * original.interfacePressure * area,
          corrected.momentumFlux, 1.0e-12);
      assertEquals(expectedMass * donor.enthalpy, corrected.energyFlux, 1.0e-10);
      assertEquals(donor.holdup * faceVelocity, corrected.holdupFlux, 1.0e-14);
      assertEquals(original.interfacePressure, corrected.interfacePressure, 0.0);
      assertEquals(original.interfaceHoldup, corrected.interfaceHoldup, 0.0);

      PhaseState reversedLeft = new PhaseState(right.density, -right.velocity, right.pressure, right.soundSpeed,
          right.enthalpy, right.holdup);
      PhaseState reversedRight = new PhaseState(left.density, -left.velocity, left.pressure, left.soundSpeed,
          left.enthalpy, left.holdup);
      PhaseFlux reversed = calculator.calcPhaseFlux(reversedLeft, reversedRight, area, -correction);
      assertEquals(-corrected.massFlux, reversed.massFlux, 1.0e-14);
      assertEquals(corrected.momentumFlux, reversed.momentumFlux, 1.0e-10);
      assertEquals(-corrected.energyFlux, reversed.energyFlux, 1.0e-10);
    }
  }

  @Test
  void correctedAdvectionPreservesAbsentAndArbitrarilySmallDonorInventories() {
    AUSMPlusFluxCalculator calculator = new AUSMPlusFluxCalculator();
    PhaseState absent = new PhaseState(2.0, 0.0, 1.0e5, 10.0, 100.0, 0.0);
    PhaseState present = new PhaseState(5.0, 0.0, 1.0e5, 10.0, 700.0, 0.8);
    assertEquals(0.0, calculator.calcPhaseFlux(absent, present, 0.2, 1.0).massFlux, 0.0);
    assertEquals(0.0, calculator.calcPhaseFlux(present, absent, 0.2, -1.0).massFlux, 0.0);
    assertEquals(-0.8, calculator.calcPhaseFlux(absent, present, 0.2, -1.0).massFlux, 1.0e-15);
    for (double holdup : new double[] { 1.0e-10, 1.0e-16, 1.0e-24 }) {
      PhaseState trace = new PhaseState(2.0, 0.0, 1.0e5, 10.0, 100.0, holdup);
      PhaseFlux flux = calculator.calcPhaseFlux(trace, present, 0.2, 1.0);
      assertTrue(flux.massFlux > 0.0);
      assertEquals(0.4, flux.massFlux / holdup, 1.0e-15);
    }
    PhaseFlux empty = calculator.calcPhaseFlux(absent, absent, 0.2, 100.0);
    assertEquals(0.0, empty.massFlux, 0.0);
    assertEquals(0.0, empty.momentumFlux, 0.0);
    assertEquals(0.0, empty.energyFlux, 0.0);
  }

  /**
   * Four equal cells with alternating pressure p0+a*[1,-1,1,-1] have defect-driven mass rates
   * alpha*A*timeScale*a/dx^2*[-1,3,-3,1]. This finite-domain reference includes the one-sided end gradients and zero
   * external correction flux. Each phase's alternating inventory amplitude decreases independently.
   */
  @Test
  void closedPressureModeHasTheExpectedDampingRatesAndConservesEachPhase() {
    for (double[] fractions : new double[][] { { 1.0, 0.0, 0.0 }, { 0.4, 0.3, 0.3 } }) {
      TwoFluidSection[] cells = checkerboard(fractions);
      TwoFluidConservationEquations equations = equations();
      TransactionalEvaluation blind = equations.evaluateTransactional(copy(cells), 1.0);
      for (double[] rate : blind.getRates()) {
        for (int phase = 0; phase < 3; phase++) {
          assertEquals(0.0, rate[phase], 0.0, "The original flux cannot see a stationary pressure mode");
        }
      }
      for (int cell = 1; cell < cells.length - 1; cell++) {
        for (int phase = 0; phase < 3; phase++) {
          assertEquals(0.0, blind.getRates()[cell][3 + phase], 1.0e-12);
        }
      }
      double timeScale = 0.01;
      equations.setPressureInterpolationTimeScale(timeScale);
      TransactionalEvaluation corrected = equations.evaluateTransactional(copy(cells), 1.0);
      double[][] rates = corrected.getRates();
      double[] reference = { -1.0, 3.0, -3.0, 1.0 };
      for (int phase = 0; phase < 3; phase++) {
        double inventoryRate = 0.0;
        double alternatingRate = 0.0;
        for (int cell = 0; cell < cells.length; cell++) {
          double expected = reference[cell] * timeScale * 100.0 * fractions[phase] * cells[cell].getArea();
          assertEquals(expected, rates[cell][phase], 1.0e-12);
          inventoryRate += rates[cell][phase] * cells[cell].getLength();
          alternatingRate += (cell % 2 == 0 ? 1.0 : -1.0) * rates[cell][phase];
        }
        assertEquals(0.0, inventoryRate, 1.0e-12);
        if (fractions[phase] > 0.0) {
          assertTrue(alternatingRate < 0.0, "High-pressure cells must lose phase mass to low-pressure cells");
        }
      }
      assertLedger(cells, corrected);
      assertArrayEquals(new double[3], corrected.getPhaseMassFaceFluxes()[0], 0.0);
      assertArrayEquals(new double[3], corrected.getPhaseMassFaceFluxes()[cells.length], 0.0);
    }
  }

  /** Affine preservation applies to the added term; this does not claim a generally well-balanced gravity operator. */
  @Test
  void constantAndAffinePressuresAddNoFluxOnNonuniformCellsWithChangingAreaAndDensity() {
    double[] lengths = { 1.0, 2.0, 4.0, 2.0, 1.0, 2.0 };
    for (double gradient : new double[] { 0.0, -1024.0 }) {
      TwoFluidSection[] cells = new TwoFluidSection[lengths.length];
      double position = 0.0;
      for (int cell = 0; cell < cells.length; cell++) {
        double center = position + 0.5 * lengths[cell];
        cells[cell] = section(center, lengths[cell], 0.1 + 0.02 * cell, 2.0e6 + gradient * center,
            new double[] { 0.4, 0.3, 0.3 });
        cells[cell].setGasDensity(10.0 + cell);
        cells[cell].setOilDensity(800.0 + 10.0 * cell);
        cells[cell].setWaterDensity(1000.0 + 10.0 * cell);
        cells[cell].updateConservativeVariables();
        position += lengths[cell];
      }
      TwoFluidConservationEquations equations = equations();
      TransactionalEvaluation baseline = equations.evaluateTransactional(copy(cells), 1.0);
      equations.setPressureInterpolationTimeScale(0.03);
      TransactionalEvaluation corrected = equations.evaluateTransactional(copy(cells), 1.0);
      assertMatrixEquals(baseline.getRates(), corrected.getRates(), 0.0);
      assertMatrixEquals(baseline.getPhaseMassFaceFluxes(), corrected.getPhaseMassFaceFluxes(), 0.0);
    }
  }

  @Test
  void externalTransportAndPhaseSourcesRemainInTheExactCellLedger() {
    TwoFluidSection[] cells = checkerboard(new double[] { 0.4, 0.3, 0.3 });
    for (int cell = 0; cell < cells.length; cell++) {
      cells[cell].setGasVelocity(0.4);
      cells[cell].setLiquidVelocity(0.15);
      cells[cell].setOilVelocity(0.2);
      cells[cell].setWaterVelocity(-0.05);
      cells[cell].setMassTransferRate(cell % 2 == 0 ? 1.0e-4 : -2.0e-4);
      cells[cell].updateConservativeVariables();
    }
    TwoFluidConservationEquations equations = equations();
    equations.setClosedBoundaries(false, false);
    equations.setAllowOutletPhaseBackflow(true);
    equations.setIncludeMassTransfer(true);
    TransactionalEvaluation baseline = equations.evaluateTransactional(copy(cells), 1.0);
    equations.setPressureInterpolationTimeScale(0.01);
    TransactionalEvaluation corrected = equations.evaluateTransactional(copy(cells), 1.0);
    assertArrayEquals(baseline.getPhaseMassFaceFluxes()[0], corrected.getPhaseMassFaceFluxes()[0], 0.0);
    assertArrayEquals(baseline.getPhaseMassFaceFluxes()[cells.length], corrected.getPhaseMassFaceFluxes()[cells.length],
        0.0);
    assertMatrixEquals(baseline.getPhaseMassSourcesPerLength(), corrected.getPhaseMassSourcesPerLength(), 0.0);
    assertLedger(cells, corrected);
  }

  @Test
  void invalidTrialsAndUnsupportedDonorsCannotReplaceRetainedDiagnostics() {
    TwoFluidConservationEquations equations = equations();
    TwoFluidSection[] cells = checkerboard(new double[] { 0.4, 0.3, 0.3 });
    equations.calcRHS(copy(cells), 1.0);
    double[][] retained = equations.getLastPhaseMassFaceFluxes();
    equations.setPressureInterpolationTimeScale(0.01);
    for (double invalid : new double[] { -0.01, Double.NaN, Double.POSITIVE_INFINITY }) {
      assertThrows(IllegalArgumentException.class, () -> equations.setPressureInterpolationTimeScale(invalid));
      assertEquals(0.01, equations.getPressureInterpolationTimeScale(), 0.0);
    }
    TwoFluidSection[] invalid = copy(cells);
    invalid[2].setPressure(Double.NaN);
    assertThrows(IllegalStateException.class, () -> equations.evaluateTransactional(invalid, 1.0));
    assertMatrixEquals(retained, equations.getLastPhaseMassFaceFluxes(), 0.0);
    equations.setEnableWaterOilSlip(false);
    assertThrows(IllegalStateException.class, () -> equations.evaluateTransactional(copy(cells), 1.0));
    assertThrows(IllegalStateException.class, () -> equations.calcPhaseMassFaceFluxes(copy(cells), 1.0));
    equations.setEnableWaterOilSlip(true);
    equations.setConservativeSlugs(Collections.singletonList(new LagrangianSlugTracker.SlugBubbleUnit()));
    assertThrows(IllegalStateException.class, () -> equations.evaluateTransactional(copy(cells), 1.0));
    equations.setConservativeSlugs(null);
    TransactionalEvaluation first = equations.evaluateTransactional(copy(cells), 1.0);
    TransactionalEvaluation repeated = equations.evaluateTransactional(copy(cells), 1.0);
    assertMatrixEquals(first.getRates(), repeated.getRates(), 0.0);
    assertMatrixEquals(retained, equations.getLastPhaseMassFaceFluxes(), 0.0);
    assertEquals(0.01, equations.getPressureInterpolationTimeScale(), 0.0);
  }

  private static TwoFluidConservationEquations equations() {
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    equations.setIncludeEnergyEquation(false);
    equations.setConsistentPhasePressureEnabled(true);
    equations.setClosedBoundaries(true, true);
    return equations;
  }

  private static TwoFluidSection[] checkerboard(double[] fractions) {
    TwoFluidSection[] cells = new TwoFluidSection[4];
    for (int cell = 0; cell < cells.length; cell++) {
      cells[cell] = section(cell + 0.5, 1.0, 0.2, 1.0e6 + (cell % 2 == 0 ? 100.0 : -100.0), fractions);
    }
    return cells;
  }

  private static TwoFluidSection section(double position, double length, double diameter, double pressure,
      double[] fractions) {
    TwoFluidSection section = new TwoFluidSection(position, length, diameter, 0.0);
    section.setPressure(pressure);
    section.setGasHoldup(fractions[0]);
    section.setLiquidHoldup(fractions[1] + fractions[2]);
    section.setOilHoldup(fractions[1]);
    section.setWaterHoldup(fractions[2]);
    double liquid = fractions[1] + fractions[2];
    section.setWaterCut(liquid > 0.0 ? fractions[2] / liquid : 0.0);
    section.setGasDensity(10.0);
    section.setOilDensity(800.0);
    section.setWaterDensity(1000.0);
    section.setLiquidDensity(liquid > 0.0 ? (800.0 * fractions[1] + 1000.0 * fractions[2]) / liquid : 800.0);
    section.setGasViscosity(1.0e-5);
    section.setOilViscosity(1.0e-3);
    section.setWaterViscosity(1.0e-3);
    section.setLiquidViscosity(1.0e-3);
    section.setGasSoundSpeed(300.0);
    section.setLiquidSoundSpeed(1200.0);
    section.setGasEnthalpy(2.0e5);
    section.setLiquidEnthalpy(1.0e5);
    section.setSurfaceTension(0.02);
    section.setGasVelocity(0.0);
    section.setLiquidVelocity(0.0);
    section.setOilVelocity(0.0);
    section.setWaterVelocity(0.0);
    section.updateConservativeVariables();
    return section;
  }

  private static void assertLedger(TwoFluidSection[] cells, TransactionalEvaluation evaluation) {
    double[][] rates = evaluation.getRates();
    double[][] faces = evaluation.getPhaseMassFaceFluxes();
    double[][] sources = evaluation.getPhaseMassSourcesPerLength();
    for (int phase = 0; phase < 3; phase++) {
      double totalRate = 0.0;
      double totalSource = 0.0;
      for (int cell = 0; cell < cells.length; cell++) {
        double expected = faces[cell][phase] - faces[cell + 1][phase] + sources[cell][phase] * cells[cell].getLength();
        assertEquals(expected, rates[cell][phase] * cells[cell].getLength(), 1.0e-12);
        totalRate += rates[cell][phase] * cells[cell].getLength();
        totalSource += sources[cell][phase] * cells[cell].getLength();
      }
      assertEquals(faces[0][phase] - faces[cells.length][phase] + totalSource, totalRate, 1.0e-12);
    }
  }

  private static TwoFluidSection[] copy(TwoFluidSection[] sections) {
    TwoFluidSection[] copy = new TwoFluidSection[sections.length];
    for (int cell = 0; cell < sections.length; cell++) {
      copy[cell] = sections[cell].clone();
    }
    return copy;
  }

  private static void assertMatrixEquals(double[][] expected, double[][] actual, double tolerance) {
    assertEquals(expected.length, actual.length);
    for (int row = 0; row < expected.length; row++) {
      assertArrayEquals(expected[row], actual[row], tolerance);
    }
  }

  private static void assertFluxBits(PhaseFlux expected, PhaseFlux actual) {
    double[] before = { expected.massFlux, expected.momentumFlux, expected.energyFlux, expected.holdupFlux,
        expected.interfacePressure, expected.interfaceHoldup };
    double[] after = { actual.massFlux, actual.momentumFlux, actual.energyFlux, actual.holdupFlux,
        actual.interfacePressure, actual.interfaceHoldup };
    for (int field = 0; field < before.length; field++) {
      assertEquals(Double.doubleToLongBits(before[field]), Double.doubleToLongBits(after[field]));
    }
  }
}
