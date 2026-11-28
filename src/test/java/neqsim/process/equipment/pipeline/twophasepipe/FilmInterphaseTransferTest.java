package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.FilmInterphaseTransfer.CellMode;
import neqsim.process.equipment.pipeline.twophasepipe.FilmInterphaseTransfer.TransferLaw;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Local contracts of the film-model interphase transfer closure on one gas/oil cell. */
class FilmInterphaseTransferTest {
  private static final double DIAMETER = 1.0;
  private static final double AREA = Math.PI * DIAMETER * DIAMETER / 4.0;

  /** A cell at phase equilibrium exchanges nothing. */
  @Test
  void equilibriumCellHasNoSource() {
    Cell cell = cell(80.0, 80.0, 2.0, 0.3);
    FilmInterphaseTransfer.Result result = new FilmInterphaseTransfer().calculate(cell.transport, cell.sections,
        cell.fluid, cell.equilibrium(), 0.0, 10.0);

    assertEquals(CellMode.FILM, result.getModes()[0]);
    double[][][] sources = result.getSourcesKgPerMetreSecond();
    for (int component = 0; component < sources[0][0].length; component++) {
      assertEquals(0.0, sources[0][1][component], 1.0e-9 * cell.oilMassPerMetre / 10.0);
      assertEquals(-sources[0][1][component], sources[0][0][component], 0.0);
    }
    assertTrue(result.getInterfaceWidthsMetres()[0] > 0.0);
    assertEquals(cell.sections[0].getTemperature(), result.getInterfaceTemperaturesK()[0], 1.0e-3);
  }

  /**
   * After a pressure change the two-resistance law moves every component toward its equilibrium inventory, never past
   * it, at a finite rate, and the exact exponential update stays bounded for any step.
   */
  @Test
  void twoResistanceLawRelaxesTowardEquilibriumWithoutOvershoot() {
    Cell cell = cell(80.0, 86.0, 2.0, 0.3);
    double[][][] target = cell.transport.createHydrocarbonEquilibriumInventoryChanges(cell.equilibrium(),
        cell.sections);
    double previousFraction = 0.0;
    for (double step : new double[] {1.0, 60.0, 3600.0, 1.0e7}) {
      FilmInterphaseTransfer.Result result = new FilmInterphaseTransfer().calculate(cell.transport, cell.sections,
          cell.fluid, cell.equilibrium(), 0.0, step);
      assertEquals(CellMode.FILM, result.getModes()[0]);
      double[][][] sources = result.getSourcesKgPerMetreSecond();
      double moved = 0.0;
      double wanted = 0.0;
      for (int component = 0; component < sources[0][1].length; component++) {
        double change = sources[0][1][component] * step;
        assertTrue(change * target[0][1][component] >= 0.0, "direction of component " + component);
        assertTrue(Math.abs(change) <= Math.abs(target[0][1][component]) * (1.0 + 1.0e-12), "overshoot " + component);
        assertEquals(-sources[0][1][component], sources[0][0][component], 0.0);
        moved += Math.abs(change);
        wanted += Math.abs(target[0][1][component]);
      }
      assertTrue(wanted > 0.0);
      double fraction = moved / wanted;
      assertTrue(fraction > previousFraction && fraction <= 1.0 + 1.0e-12, "fraction " + fraction + " at " + step);
      previousFraction = fraction;
      double slowest = result.getSlowestTimeConstantsSeconds()[0];
      assertTrue(slowest > 1.0 && slowest < 1.0e6, "slowest time constant " + slowest + " s");
    }
    assertTrue(previousFraction > 0.999, "a very long step must reach equilibrium, got " + previousFraction);
  }

  /** Faster gas shortens the time constant: the coefficients come from the flow state, not from an input. */
  @Test
  void timeConstantFollowsTheGasSpeed() {
    double slow = slowestTimeConstant(cell(80.0, 86.0, 0.5, 0.1));
    double fast = slowestTimeConstant(cell(80.0, 86.0, 5.0, 0.1));
    assertTrue(fast < 0.5 * slow, "slow gas " + slow + " s, fast gas " + fast + " s");
  }

  /** A larger interface gives proportionally faster transfer under the two-resistance law. */
  @Test
  void interfaceAreaFactorScalesTheRate() {
    Cell cell = cell(80.0, 86.0, 2.0, 0.3);
    FilmInterphaseTransfer base = new FilmInterphaseTransfer();
    FilmInterphaseTransfer doubled = new FilmInterphaseTransfer();
    doubled.setInterfaceAreaFactor(2.0);
    double baseConstant = base.calculate(cell.transport, cell.sections, cell.fluid, cell.equilibrium(), 0.0, 1.0)
        .getSlowestTimeConstantsSeconds()[0];
    double doubledConstant = doubled.calculate(cell.transport, cell.sections, cell.fluid, cell.equilibrium(), 0.0, 1.0)
        .getSlowestTimeConstantsSeconds()[0];
    assertEquals(0.5 * baseConstant, doubledConstant, 1.0e-9 * baseConstant);
  }

  /**
   * Above the phase envelope the cell composition is single phase: the oil may only lose components under either law,
   * whatever label the flash gives the dense phase.
   */
  @Test
  void minorityPhaseCannotGrowWhereEquilibriumIsSinglePhase() {
    Cell cell = cell(80.0, 250.0, 2.0, 0.3);
    assertEquals(1, cell.equilibrium()[0].getNumberOfPhases());
    for (TransferLaw law : TransferLaw.values()) {
      FilmInterphaseTransfer closure = new FilmInterphaseTransfer();
      closure.setTransferLaw(law);
      double[][][] sources = closure.calculate(cell.transport, cell.sections, cell.fluid, cell.equilibrium(), 0.0, 5.0)
          .getSourcesKgPerMetreSecond();
      double total = 0.0;
      for (int component = 0; component < sources[0][1].length; component++) {
        assertTrue(sources[0][1][component] <= 0.0, law + " component " + component);
        total += sources[0][1][component];
      }
      assertTrue(total < 0.0, law + " must remove oil");
    }
  }

  /** A liquid below the minimum film holdup relaxes toward equilibrium with the fallback time. */
  @Test
  void traceLiquidUsesTheFallbackRelaxation() {
    Cell cell = cell(80.0, 86.0, 2.0, 0.3);
    FilmInterphaseTransfer closure = new FilmInterphaseTransfer();
    closure.setMinimumHoldup(0.4);
    closure.setFallbackRelaxationTime(50.0);
    double[][][] target = cell.transport.createHydrocarbonEquilibriumInventoryChanges(cell.equilibrium(),
        cell.sections);
    FilmInterphaseTransfer.Result result = closure.calculate(cell.transport, cell.sections, cell.fluid,
        cell.equilibrium(), 0.0, 1.0e-3);
    assertEquals(CellMode.RELAXATION, result.getModes()[0]);
    assertEquals(0L, result.getFilmSolveCount());
    double[][][] sources = result.getSourcesKgPerMetreSecond();
    for (int component = 0; component < sources[0][1].length; component++) {
      assertEquals(target[0][1][component] / 50.0, sources[0][1][component],
          1.0e-12 * Math.abs(target[0][1][component]) + 1.0e-300);
    }
  }

  /** Held coefficients are reused inside the update interval and solved again after it. */
  @Test
  void updateIntervalControlsTheNumberOfFilmSolves() {
    Cell cell = cell(80.0, 86.0, 2.0, 0.3);
    FilmInterphaseTransfer closure = new FilmInterphaseTransfer();
    closure.setUpdateInterval(100.0);
    assertEquals(1L, closure.calculate(cell.transport, cell.sections, cell.fluid, cell.equilibrium(), 0.0, 10.0)
        .getFilmSolveCount());
    assertEquals(1L, closure.calculate(cell.transport, cell.sections, cell.fluid, cell.equilibrium(), 50.0, 10.0)
        .getFilmSolveCount());
    assertEquals(2L, closure.calculate(cell.transport, cell.sections, cell.fluid, cell.equilibrium(), 100.0, 10.0)
        .getFilmSolveCount());
    // A time before the cached solve means the step was rolled back.
    assertEquals(3L, closure.calculate(cell.transport, cell.sections, cell.fluid, cell.equilibrium(), 20.0, 10.0)
        .getFilmSolveCount());
  }

  /** Invalid settings are rejected. */
  @Test
  void settersValidateTheirArguments() {
    FilmInterphaseTransfer closure = new FilmInterphaseTransfer();
    assertThrows(IllegalArgumentException.class, () -> closure.setUpdateInterval(-1.0));
    assertThrows(IllegalArgumentException.class, () -> closure.setMinimumHoldup(0.0));
    assertThrows(IllegalArgumentException.class, () -> closure.setFallbackRelaxationTime(0.0));
    assertThrows(IllegalArgumentException.class, () -> closure.setInterfaceAreaFactor(Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> closure.setTransferLaw(null));
  }

  private static double slowestTimeConstant(Cell cell) {
    return new FilmInterphaseTransfer()
        .calculate(cell.transport, cell.sections, cell.fluid, cell.equilibrium(), 0.0, 1.0)
        .getSlowestTimeConstantsSeconds()[0];
  }

  /**
   * Build one cell whose phases are in equilibrium at the first pressure and then held at the second.
   *
   * @param equilibriumPressure pressure in bara at which gas and oil are in equilibrium
   * @param cellPressure pressure in bara of the cell when the closure is evaluated
   * @param gasVelocity gas speed in m/s
   * @param oilVelocity oil speed in m/s
   * @return cell, transported inventory and template
   */
  private static Cell cell(double equilibriumPressure, double cellPressure, double gasVelocity, double oilVelocity) {
    SystemInterface fluid = new SystemSrkEos(277.15, equilibriumPressure);
    String[] names = {"nitrogen", "CO2", "methane", "ethane", "propane", "n-butane", "n-pentane", "n-heptane"};
    double[] fractions = {0.004, 0.06, 0.846, 0.050, 0.020, 0.010, 0.005, 0.005};
    for (int component = 0; component < names.length; component++) {
      fluid.addComponent(names[component], fractions[component]);
    }
    fluid.setMixingRule("classic");
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
    assertEquals(2, fluid.getNumberOfPhases());
    double gasDensity = fluid.getPhase(0).getDensity("kg/m3");
    double oilDensity = fluid.getPhase(1).getDensity("kg/m3");
    double oilVolumeFraction = fluid.getPhase(1).getVolume() / fluid.getVolume();

    TwoFluidSection section = new TwoFluidSection(0.0, 1.0, DIAMETER, 0.0);
    section.setRoughness(4.5e-5);
    section.setPressure(equilibriumPressure * 1.0e5);
    section.setTemperature(277.15);
    section.setGasDensity(gasDensity);
    section.setOilDensity(oilDensity);
    section.setLiquidDensity(oilDensity);
    section.setGasHoldup(1.0 - oilVolumeFraction);
    section.setLiquidHoldup(oilVolumeFraction);
    section.setOilHoldup(oilVolumeFraction);
    section.setGasMassPerLength(gasDensity * (1.0 - oilVolumeFraction) * AREA);
    section.setOilMassPerLength(oilDensity * oilVolumeFraction * AREA);
    section.setGasVelocity(gasVelocity);
    section.setLiquidVelocity(oilVelocity);
    section.setOilVelocity(oilVelocity);
    section.setFlowRegime(FlowRegime.STRATIFIED_WAVY);
    TwoFluidSection[] sections = {section};
    TwoFluidComponentTransport transport = new TwoFluidComponentTransport(fluid, sections);
    section.setPressure(cellPressure * 1.0e5);
    return new Cell(fluid, sections, transport, oilDensity * oilVolumeFraction * AREA);
  }

  /** One-cell fixture. */
  private static final class Cell {
    private final SystemInterface fluid;
    private final TwoFluidSection[] sections;
    private final TwoFluidComponentTransport transport;
    private final double oilMassPerMetre;

    private Cell(SystemInterface fluid, TwoFluidSection[] sections, TwoFluidComponentTransport transport,
        double oilMassPerMetre) {
      this.fluid = fluid;
      this.sections = sections;
      this.transport = transport;
      this.oilMassPerMetre = oilMassPerMetre;
    }

    private SystemInterface[] equilibrium() {
      return new SystemInterface[] {
          transport.createThermodynamicState(0, fluid, sections[0].getPressure(), sections[0].getTemperature())};
    }
  }
}
