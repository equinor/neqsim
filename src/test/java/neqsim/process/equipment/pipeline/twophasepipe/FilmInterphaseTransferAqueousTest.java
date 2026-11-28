package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.FilmInterphaseTransfer.CellMode;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** One-cell contracts of the film-model transfer with a small aqueous (water and TEG) phase, with and without oil. */
class FilmInterphaseTransferAqueousTest {
  private static final double DIAMETER = 1.0;
  private static final double AREA = Math.PI * DIAMETER * DIAMETER / 4.0;
  private static final double TEMPERATURE = 278.15;

  /** Phases in equilibrium exchange nothing, with a film solved for every liquid that is present. */
  @Test
  void equilibriumCellsHaveNoSource() {
    for (boolean withOil : new boolean[] {false, true}) {
      Cell cell = cell(withOil, 100.0, 100.0, 0.0, 2.0);
      FilmInterphaseTransfer.Result result = new FilmInterphaseTransfer().calculate(cell.transport, cell.sections,
          cell.fluid, cell.equilibrium(), 0.0, 10.0);
      assertEquals(CellMode.FILM, result.getAqueousModes()[0], result.getLastFailure());
      assertEquals(withOil ? CellMode.FILM : CellMode.NONE, result.getModes()[0], result.getLastFailure());
      assertEquals(0L, result.getFilmFailureCount());
      double[][][] sources = result.getSourcesKgPerMetreSecond();
      for (int phase = 0; phase < 3; phase++) {
        for (double source : sources[0][phase]) {
          assertEquals(0.0, source, 1.0e-9 * cell.aqueousMassPerMetre, "phase " + phase + ", oil " + withOil);
        }
      }
    }
  }

  /**
   * A fall in pressure lets the gas hold more water. The aqueous phase is stable on its own apart from the gas
   * dissolved in it, so the water has to evaporate through the interface: slowly, on the small surface of a small
   * phase.
   */
  @Test
  void waterEvaporatesThroughTheInterfaceOnAPressureFall() {
    Cell cell = cell(false, 100.0, 90.0, 0.0, 2.0);
    int water = index(cell, "water");
    double[][][] target = cell.transport.createEquilibriumRelaxationSourceRates(cell.equilibrium(), cell.sections, 1.0);
    assertTrue(target[0][2][water] < 0.0, "the aqueous phase must lose water");
    FilmInterphaseTransfer closure = new FilmInterphaseTransfer();
    FilmInterphaseTransfer.Result result = closure.calculate(cell.transport, cell.sections, cell.fluid,
        cell.equilibrium(), 0.0, 1.0);
    assertEquals(CellMode.FILM, result.getAqueousModes()[0], result.getLastFailure());
    double[][][] sources = result.getSourcesKgPerMetreSecond();
    assertTrue(sources[0][2][water] < 0.0, "water source " + sources[0][2][water]);
    assertEquals(-sources[0][2][water], sources[0][0][water], 0.0);
    // Dissolved gas leaves in the bulk of the liquid; the gas sheds no hydrocarbon liquid.
    assertTrue(result.getAqueousFlashingRatesKgPerMetreSecond()[0] > 0.0);
    assertEquals(0.0, result.getFogRatesKgPerMetreSecond()[0], 0.0);
    // Evaporation acts on the stratified surface of the liquid, far less than the pipe wall.
    assertTrue(result.getAqueousInterfaceWidthsMetres()[0] > 0.0
        && result.getAqueousInterfaceWidthsMetres()[0] < 0.1 * Math.PI * DIAMETER);
    double time = result.getAqueousCharacteristicTimesSeconds()[0];
    assertTrue(time > 60.0 && time < 1.0e6, "aqueous characteristic time " + time + " s");
    // No overshoot of local equilibrium on a long step.
    double moved = closure.calculate(cell.transport, cell.sections, cell.fluid, cell.equilibrium(), 0.0, 1.0e9)
        .getSourcesKgPerMetreSecond()[0][2][water] * 1.0e9;
    assertTrue(moved < 0.0 && moved >= target[0][2][water] * (1.0 + 1.0e-9), "moved " + moved);
  }

  /**
   * Cooling leaves the gas above its water dew point for the glycol solution it carries: a little aqueous fog forms in
   * the bulk, and water condenses on the whole wall.
   */
  @Test
  void coolingFormsAqueousFogAndCondensesOnTheWall() {
    Cell cell = cell(false, 100.0, 100.0, -5.0, 2.0);
    int water = index(cell, "water");
    FilmInterphaseTransfer.Result result = new FilmInterphaseTransfer().calculate(cell.transport, cell.sections,
        cell.fluid, cell.equilibrium(), 0.0, 1.0);
    assertTrue(result.getAqueousFogRatesKgPerMetreSecond()[0] > 0.0);
    assertTrue(result.getSourcesKgPerMetreSecond()[0][2][water] > 0.0);
    assertEquals(Math.PI * DIAMETER * Math.sqrt(cell.sections[0].getGasHoldup()),
        result.getAqueousInterfaceWidthsMetres()[0], 1.0e-12);
  }

  /**
   * A rise in pressure leaves the gas with more water than the glycol solution allows, but still below the dew point of
   * water on its own: nothing condenses in the bulk, and the water is absorbed through the interface.
   */
  @Test
  void glycolAbsorbsWaterThroughTheInterfaceWithoutFog() {
    Cell cell = cell(false, 100.0, 110.0, 0.0, 2.0);
    int water = index(cell, "water");
    FilmInterphaseTransfer.Result result = new FilmInterphaseTransfer().calculate(cell.transport, cell.sections,
        cell.fluid, cell.equilibrium(), 0.0, 1.0);
    assertEquals(0.0, result.getAqueousFogRatesKgPerMetreSecond()[0], 0.0);
    assertTrue(result.getSourcesKgPerMetreSecond()[0][2][water] > 0.0);
    FilmInterphaseTransfer noInterface = new FilmInterphaseTransfer();
    noInterface.setInterfaceAreaFactor(1.0e-12);
    assertEquals(0.0, noInterface.calculate(cell.transport, cell.sections, cell.fluid, cell.equilibrium(), 0.0, 1.0)
        .getSourcesKgPerMetreSecond()[0][2][water], 1.0e-9 * result.getSourcesKgPerMetreSecond()[0][2][water]);
  }

  /**
   * Gas, condensate and aqueous phase together: every component is conserved over the three phases, the two liquids
   * share the interface in proportion to their volume, the hydrocarbons condense as fog on a pressure fall, and the
   * small aqueous phase follows far more slowly than the oil.
   */
  @Test
  void threePhaseCellConservesComponentsAndSharesTheInterface() {
    for (double[] state : new double[][] {{90.0, 0.0}, {100.0, -5.0}, {110.0, 0.0}}) {
      Cell cell = cell(true, 100.0, state[0], state[1], 2.0);
      FilmInterphaseTransfer.Result result = new FilmInterphaseTransfer().calculate(cell.transport, cell.sections,
          cell.fluid, cell.equilibrium(), 0.0, 1.0);
      String label = state[0] + " bara, " + state[1] + " K";
      assertEquals(CellMode.FILM, result.getModes()[0], label + ": " + result.getLastFailure());
      assertEquals(CellMode.FILM, result.getAqueousModes()[0], label + ": " + result.getLastFailure());
      double[][][] sources = result.getSourcesKgPerMetreSecond();
      double moved = 0.0;
      for (int component = 0; component < sources[0][0].length; component++) {
        assertEquals(0.0, sources[0][0][component] + sources[0][1][component] + sources[0][2][component],
            1.0e-15 * cell.oilMassPerMetre, label + " component " + component);
        moved += Math.abs(sources[0][1][component]) + Math.abs(sources[0][2][component]);
      }
      assertTrue(moved > 0.0, label);
      double oilWidth = result.getInterfaceWidthsMetres()[0];
      double aqueousWidth = result.getAqueousInterfaceWidthsMetres()[0];
      double aqueousShare = cell.sections[0].getWaterHoldup()
          / (cell.sections[0].getWaterHoldup() + cell.sections[0].getOilHoldup());
      int water = index(cell, "water");
      double[][][] target = cell.transport.createEquilibriumRelaxationSourceRates(cell.equilibrium(), cell.sections,
          1.0);
      if (target[0][2][water] < 0.0) {
        // Water evaporates from the surface of the aqueous phase only: its share of the liquid volume.
        assertTrue(aqueousWidth > 0.0 && aqueousWidth <= 1.01 * aqueousShare * Math.PI * DIAMETER,
            label + " aqueous width " + aqueousWidth);
        assertTrue(aqueousWidth < 0.05 * oilWidth, label + " aqueous " + aqueousWidth + " m, oil " + oilWidth + " m");
      } else {
        // Water condenses on any surface: the whole wall.
        assertEquals(Math.PI * DIAMETER * Math.sqrt(cell.sections[0].getGasHoldup()), aqueousWidth, 1.0e-12, label);
      }
      assertTrue(Double.isFinite(result.getLiquidLiquidRatesKgPerMetreSecond()[0]), label);
      if (state[0] < 100.0) {
        assertTrue(result.getFogRatesKgPerMetreSecond()[0] > 0.0, label + " hydrocarbon fog");
        assertTrue(result.getAqueousCharacteristicTimesSeconds()[0] > 10.0 * result.getCharacteristicTimesSeconds()[0],
            label + " aqueous " + result.getAqueousCharacteristicTimesSeconds()[0] + " s, oil "
                + result.getCharacteristicTimesSeconds()[0] + " s");
      }
    }
  }

  /**
   * Equilibrium mode: every component of every phase approaches its local equilibrium partition with one time constant,
   * without a film solve, for gas with an aqueous phase and for three phases, in any flow regime. It is the relaxation
   * of the pipe itself, written with an update that is stable on any step.
   */
  @Test
  void equilibriumModeRelaxesEveryPhaseWithOneTimeConstant() {
    for (boolean withOil : new boolean[] {false, true}) {
      for (FlowRegime regime : new FlowRegime[] {FlowRegime.STRATIFIED_WAVY, FlowRegime.SLUG, FlowRegime.ANNULAR,
          FlowRegime.MIST, FlowRegime.BUBBLE}) {
        Cell cell = cell(withOil, 100.0, 90.0, -3.0, 2.0);
        cell.sections[0].setFlowRegime(regime);
        double[][][] pipeRates = cell.transport.createEquilibriumRelaxationSourceRates(cell.equilibrium(),
            cell.sections, 45.0);
        FilmInterphaseTransfer closure = new FilmInterphaseTransfer();
        closure.setEquilibriumMode(true);
        assertTrue(closure.isEquilibriumMode());
        closure.setFallbackRelaxationTime(45.0);
        double step = 1.0e-3;
        FilmInterphaseTransfer.Result result = closure.calculate(cell.transport, cell.sections, cell.fluid,
            cell.equilibrium(), 0.0, step);
        assertEquals(0L, result.getFilmSolveCount());
        assertEquals(CellMode.RELAXATION, result.getAqueousModes()[0]);
        assertEquals(withOil ? CellMode.RELAXATION : CellMode.NONE, result.getModes()[0]);
        assertEquals(0.0, result.getFogRatesKgPerMetreSecond()[0], 0.0);
        assertEquals(0.0, result.getAqueousFogRatesKgPerMetreSecond()[0], 0.0);
        double[][][] sources = result.getSourcesKgPerMetreSecond();
        double moved = 0.0;
        for (int phase = 0; phase < 3; phase++) {
          for (int component = 0; component < sources[0][phase].length; component++) {
            // The rate of the pipe's own relaxation, less the curvature of the exponential over the step.
            assertEquals(pipeRates[0][phase][component], sources[0][phase][component],
                1.0e-4 * Math.abs(pipeRates[0][phase][component]) + 1.0e-18,
                regime + ", oil " + withOil + ", phase " + phase + ", component " + component);
            moved += Math.abs(sources[0][phase][component]);
          }
        }
        assertTrue(moved > 0.0);
      }
    }
  }

  /**
   * The same three-phase cell in the two modes: equilibrium mode moves every component at the relaxation time, the
   * non-equilibrium mode moves the hydrocarbons about as fast (fog) and the water much more slowly (interface).
   */
  @Test
  void bothModesRunTheSameCellAndDifferWhereTheInterfaceLimits() {
    Cell cell = cell(true, 100.0, 90.0, 0.0, 2.0);
    int water = index(cell, "water");
    FilmInterphaseTransfer equilibrium = new FilmInterphaseTransfer();
    equilibrium.setEquilibriumMode(true);
    double[][][] atEquilibrium = equilibrium
        .calculate(cell.transport, cell.sections, cell.fluid, cell.equilibrium(), 0.0, 1.0)
        .getSourcesKgPerMetreSecond();
    double[][][] finiteRate = new FilmInterphaseTransfer()
        .calculate(cell.transport, cell.sections, cell.fluid, cell.equilibrium(), 0.0, 1.0)
        .getSourcesKgPerMetreSecond();
    double oilAtEquilibrium = 0.0;
    double oilFiniteRate = 0.0;
    for (int component = 0; component < atEquilibrium[0][1].length; component++) {
      oilAtEquilibrium += atEquilibrium[0][1][component];
      oilFiniteRate += finiteRate[0][1][component];
      assertEquals(0.0, finiteRate[0][0][component] + finiteRate[0][1][component] + finiteRate[0][2][component],
          1.0e-15 * cell.oilMassPerMetre);
      assertEquals(0.0,
          atEquilibrium[0][0][component] + atEquilibrium[0][1][component] + atEquilibrium[0][2][component],
          1.0e-15 * cell.oilMassPerMetre);
    }
    assertTrue(oilAtEquilibrium > 0.0 && oilFiniteRate > 0.0, "the pressure fall must condense hydrocarbons");
    assertTrue(oilFiniteRate > 0.3 * oilAtEquilibrium && oilFiniteRate < 3.0 * oilAtEquilibrium,
        "oil source " + oilFiniteRate + " against " + oilAtEquilibrium + " kg/(m s)");
    assertTrue(atEquilibrium[0][2][water] < 0.0 && finiteRate[0][2][water] < 0.0);
    assertTrue(Math.abs(finiteRate[0][2][water]) < 0.05 * Math.abs(atEquilibrium[0][2][water]),
        "water source " + finiteRate[0][2][water] + " against " + atEquilibrium[0][2][water] + " kg/(m s)");
  }

  /** The liquid-liquid time constant must be positive and finite. */
  @Test
  void liquidLiquidRelaxationTimeIsValidated() {
    FilmInterphaseTransfer closure = new FilmInterphaseTransfer();
    assertEquals(30.0, closure.getLiquidLiquidRelaxationTime(), 0.0);
    assertThrows(IllegalArgumentException.class, () -> closure.setLiquidLiquidRelaxationTime(0.0));
    closure.setLiquidLiquidRelaxationTime(120.0);
    assertEquals(120.0, closure.getLiquidLiquidRelaxationTime(), 0.0);
  }

  private static int index(Cell cell, String component) {
    return java.util.Arrays.asList(cell.transport.getComponentNames()).indexOf(component);
  }

  /**
   * Build one cell whose phases are in equilibrium at one pressure and are then held at another pressure and
   * temperature.
   *
   * @param withOil true for gas, condensate and aqueous phase; false for gas and aqueous phase
   * @param equilibriumPressure pressure in bara at which the phases are in equilibrium
   * @param cellPressure pressure in bara of the cell when the closure is evaluated
   * @param temperatureShift change of the cell temperature in K after equilibrium
   * @param gasVelocity gas speed in m/s
   * @return cell, transported inventory and template
   */
  private static Cell cell(boolean withOil, double equilibriumPressure, double cellPressure, double temperatureShift,
      double gasVelocity) {
    SystemInterface fluid = new SystemSrkCPAstatoil(TEMPERATURE, equilibriumPressure);
    fluid.addComponent("nitrogen", 0.005);
    fluid.addComponent("CO2", 0.015);
    fluid.addComponent("methane", withOil ? 0.880 : 0.900);
    fluid.addComponent("ethane", 0.050);
    fluid.addComponent("propane", 0.020);
    fluid.addComponent("n-butane", 0.007);
    if (withOil) {
      fluid.addComponent("n-pentane", 0.004);
      fluid.addComponent("n-heptane", 0.006);
      fluid.addComponent("n-nonane", 0.003);
    } else {
      fluid.addComponent("n-pentane", 0.003);
    }
    fluid.addComponent("water", 300.0e-6);
    fluid.addComponent("TEG", 60.0e-6);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
    assertEquals(withOil ? 3 : 2, fluid.getNumberOfPhases());
    double[] density = new double[3];
    double[] volumeFraction = new double[3];
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      PhaseType type = fluid.getPhase(phase).getType();
      int kind = type == PhaseType.GAS ? 0 : type == PhaseType.AQUEOUS ? 2 : 1;
      density[kind] = fluid.getPhase(phase).getDensity("kg/m3");
      volumeFraction[kind] = fluid.getPhase(phase).getVolume() / fluid.getVolume();
    }
    assertTrue(density[0] > 0.0 && density[2] > 0.0 && (!withOil || density[1] > 0.0));

    TwoFluidSection section = new TwoFluidSection(0.0, 1.0, DIAMETER, 0.0);
    section.setRoughness(4.5e-5);
    section.setPressure(equilibriumPressure * 1.0e5);
    section.setTemperature(TEMPERATURE);
    section.setGasDensity(density[0]);
    section.setGasViscosity(fluid.getPhase(0).getPhysicalProperties().getViscosity());
    section.setOilDensity(withOil ? density[1] : 600.0);
    section.setWaterDensity(density[2]);
    section.setLiquidDensity(withOil ? density[1] : density[2]);
    section.setGasHoldup(volumeFraction[0]);
    section.setLiquidHoldup(volumeFraction[1] + volumeFraction[2]);
    section.setOilHoldup(volumeFraction[1]);
    section.setWaterHoldup(volumeFraction[2]);
    section.setGasMassPerLength(density[0] * volumeFraction[0] * AREA);
    section.setOilMassPerLength(density[1] * volumeFraction[1] * AREA);
    section.setWaterMassPerLength(density[2] * volumeFraction[2] * AREA);
    section.setGasVelocity(gasVelocity);
    section.setLiquidVelocity(0.2);
    section.setOilVelocity(0.2);
    section.setWaterVelocity(0.2);
    section.setFlowRegime(FlowRegime.STRATIFIED_WAVY);
    TwoFluidSection[] sections = {section};
    TwoFluidComponentTransport transport = new TwoFluidComponentTransport(fluid, sections);
    section.setPressure(cellPressure * 1.0e5);
    section.setTemperature(TEMPERATURE + temperatureShift);
    return new Cell(fluid, sections, transport, density[1] * volumeFraction[1] * AREA,
        density[2] * volumeFraction[2] * AREA);
  }

  /** One-cell fixture. */
  private static final class Cell {
    private final SystemInterface fluid;
    private final TwoFluidSection[] sections;
    private final TwoFluidComponentTransport transport;
    private final double oilMassPerMetre;
    private final double aqueousMassPerMetre;

    private Cell(SystemInterface fluid, TwoFluidSection[] sections, TwoFluidComponentTransport transport,
        double oilMassPerMetre, double aqueousMassPerMetre) {
      this.fluid = fluid;
      this.sections = sections;
      this.transport = transport;
      this.oilMassPerMetre = oilMassPerMetre;
      this.aqueousMassPerMetre = aqueousMassPerMetre;
    }

    private SystemInterface[] equilibrium() {
      return new SystemInterface[] {
          transport.createThermodynamicState(0, fluid, sections[0].getPressure(), sections[0].getTemperature())};
    }
  }
}
