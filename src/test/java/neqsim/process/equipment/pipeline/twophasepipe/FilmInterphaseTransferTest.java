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
      FilmInterphaseTransfer closure = new FilmInterphaseTransfer();
      closure.setTransferLaw(TransferLaw.TWO_RESISTANCE_RELAXATION);
      FilmInterphaseTransfer.Result result = closure.calculate(cell.transport, cell.sections, cell.fluid,
          cell.equilibrium(), 0.0, step);
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

  /**
   * The default law starts every component at its Maxwell-Stefan film flux and never carries a component that moves
   * toward equilibrium past it, whatever the step. A component whose film flux points away from equilibrium is held.
   * The bulk split is off here so that the film law is seen alone.
   */
  @Test
  void defaultLawStartsAtTheFilmFluxAndSaturatesAtEquilibrium() {
    for (double pressure : new double[] {80.3, 79.7}) {
      Cell cell = cell(80.0, pressure, 2.0, 0.3);
      double[][][] target = cell.transport.createHydrocarbonEquilibriumInventoryChanges(cell.equilibrium(),
          cell.sections);
      FilmInterphaseTransfer explicit = new FilmInterphaseTransfer();
      explicit.setTransferLaw(TransferLaw.FILM_FLUX);
      explicit.setWallCondensation(false);
      explicit.setBulkPhaseSplit(false);
      FilmInterphaseTransfer relaxing = new FilmInterphaseTransfer();
      relaxing.setWallCondensation(false);
      relaxing.setBulkPhaseSplit(false);
      assertEquals(TransferLaw.FILM_FLUX_RELAXATION, relaxing.getTransferLaw());
      double shortStep = 1.0e-4;
      double[] filmFlux = explicit
          .calculate(cell.transport, cell.sections, cell.fluid, cell.equilibrium(), 0.0, shortStep)
          .getSourcesKgPerMetreSecond()[0][1];
      FilmInterphaseTransfer.Result first = relaxing.calculate(cell.transport, cell.sections, cell.fluid,
          cell.equilibrium(), 0.0, shortStep);
      double[] initial = first.getSourcesKgPerMetreSecond()[0][1];
      double[] longStep = relaxing.calculate(cell.transport, cell.sections, cell.fluid, cell.equilibrium(), 0.0, 1.0e9)
          .getSourcesKgPerMetreSecond()[0][1];
      int towardEquilibrium = 0;
      boolean held = false;
      for (int component = 0; component < filmFlux.length; component++) {
        if (filmFlux[component] * target[0][1][component] > 0.0) {
          towardEquilibrium++;
          assertEquals(filmFlux[component], initial[component], 1.0e-3 * Math.abs(filmFlux[component]),
              "initial rate of component " + component);
          assertEquals(target[0][1][component], longStep[component] * 1.0e9, 1.0e-9 * Math.abs(target[0][1][component]),
              "end state of component " + component);
        } else {
          // A film flux away from equilibrium is held, on any step.
          assertEquals(0.0, initial[component], 0.0, "held component " + component);
          assertEquals(0.0, longStep[component], 0.0, "held component " + component + " on a long step");
          held |= filmFlux[component] != 0.0 && target[0][1][component] != 0.0;
        }
      }
      assertEquals(held, first.getCounterEquilibrium()[0]);
      assertTrue(towardEquilibrium >= 4, "components moving toward equilibrium: " + towardEquilibrium);
    }
  }

  /** Entrained droplets add interface: with the droplet field the cell approaches equilibrium faster. */
  @Test
  void dropletFieldAddsInterfaceWhereTheGasEntrainsLiquid() {
    Cell cell = cell(80.0, 86.0, 8.0, 0.5);
    FilmInterphaseTransfer filmOnly = new FilmInterphaseTransfer();
    FilmInterphaseTransfer withDroplets = new FilmInterphaseTransfer();
    withDroplets.setDropletField(true);
    FilmInterphaseTransfer.Result film = filmOnly.calculate(cell.transport, cell.sections, cell.fluid,
        cell.equilibrium(), 0.0, 1.0);
    FilmInterphaseTransfer.Result droplets = withDroplets.calculate(cell.transport, cell.sections, cell.fluid,
        cell.equilibrium(), 0.0, 1.0);
    assertEquals(0.0, film.getDropletAreasSquareMetresPerMetre()[0], 0.0);
    assertEquals(0.0, film.getEntrainedFractions()[0], 0.0);
    assertTrue(droplets.getEntrainedFractions()[0] > 0.0 && droplets.getEntrainedFractions()[0] <= 1.0);
    assertTrue(droplets.getDropletAreasSquareMetresPerMetre()[0] > film.getInterfaceWidthsMetres()[0],
        "droplet area " + droplets.getDropletAreasSquareMetresPerMetre()[0] + " m2/m");
    assertTrue(droplets.getCharacteristicTimesSeconds()[0] < 0.1 * film.getCharacteristicTimesSeconds()[0],
        "with droplets " + droplets.getCharacteristicTimesSeconds()[0] + " s, film only "
            + film.getCharacteristicTimesSeconds()[0] + " s");
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
    for (double pressure : new double[] {120.0, 250.0}) {
      Cell cell = cell(80.0, pressure, 2.0, 0.3);
      assertEquals(1, cell.equilibrium()[0].getNumberOfPhases());
      for (TransferLaw law : TransferLaw.values()) {
        FilmInterphaseTransfer closure = new FilmInterphaseTransfer();
        closure.setTransferLaw(law);
        double[][][] sources = closure
            .calculate(cell.transport, cell.sections, cell.fluid, cell.equilibrium(), 0.0, 5.0)
            .getSourcesKgPerMetreSecond();
        double total = 0.0;
        for (int component = 0; component < sources[0][1].length; component++) {
          assertTrue(sources[0][1][component] <= 0.0, law + " component " + component + " at " + pressure);
          total += sources[0][1][component];
        }
        assertTrue(total < 0.0, law + " must remove oil at " + pressure);
      }
    }
  }

  /**
   * A liquid can evaporate into gas that would hold all of it: the cell is single phase at equilibrium, yet mixtures of
   * the two phases split, so an interface exists and the film is solved, starting from a tie line on the mixing line.
   */
  @Test
  void filmIsSolvedWhereEquilibriumIsSinglePhaseButThePhasesAreImmiscible() {
    Cell cell = cell(80.0, 120.0, 2.0, 0.3);
    assertEquals(1, cell.equilibrium()[0].getNumberOfPhases());
    FilmInterphaseTransfer.Result result = new FilmInterphaseTransfer().calculate(cell.transport, cell.sections,
        cell.fluid, cell.equilibrium(), 0.0, 1.0);
    assertEquals(CellMode.FILM, result.getModes()[0], result.getLastFailure());
    assertEquals(0L, result.getFilmFailureCount());
    assertTrue(Double.isNaN(result.getMiscibleMixingTimesSeconds()[0]));
    assertTrue(result.getCharacteristicTimesSeconds()[0] > 0.0);
  }

  /**
   * Far above the critical pressure of the gas/oil mixing line no mixture of the two phases splits. There is no
   * interface: the oil mixes into the gas with the turbulent mixing time, or with a fixed time when one is set.
   */
  @Test
  void miscibleCellMixesWithTheTurbulentMixingTime() {
    Cell cell = cell(80.0, 250.0, 2.0, 0.3);
    double[][][] target = cell.transport.createHydrocarbonEquilibriumInventoryChanges(cell.equilibrium(),
        cell.sections);
    FilmInterphaseTransfer closure = new FilmInterphaseTransfer();
    FilmInterphaseTransfer.Result result = closure.calculate(cell.transport, cell.sections, cell.fluid,
        cell.equilibrium(), 0.0, 10.0);
    assertEquals(CellMode.MISCIBLE, result.getModes()[0]);
    double mixingTime = result.getMiscibleMixingTimesSeconds()[0];
    double reynolds = cell.sections[0].getGasDensity() * 2.0 * DIAMETER / cell.sections[0].getGasViscosity();
    double frictionVelocity = 2.0 * Math.sqrt(0.316 * Math.pow(reynolds, -0.25) / 8.0);
    assertEquals(0.5 * DIAMETER / (0.07 * frictionVelocity), mixingTime, 1.0e-9 * mixingTime);
    assertTrue(mixingTime > 30.0 && mixingTime < 1000.0, "mixing time " + mixingTime + " s");
    double[][][] sources = result.getSourcesKgPerMetreSecond();
    for (int component = 0; component < sources[0][1].length; component++) {
      assertEquals(-cell.transport.getInventoryKg()[0][1][component], target[0][1][component],
          1.0e-12 * cell.oilMassPerMetre);
      assertEquals(target[0][1][component] * -Math.expm1(-10.0 / mixingTime) / 10.0, sources[0][1][component],
          1.0e-12 * cell.oilMassPerMetre);
    }

    FilmInterphaseTransfer fixed = new FilmInterphaseTransfer();
    fixed.setMiscibleMixingTime(100.0);
    assertEquals(100.0, fixed.calculate(cell.transport, cell.sections, cell.fluid, cell.equilibrium(), 0.0, 10.0)
        .getMiscibleMixingTimesSeconds()[0], 0.0);

    // Gas at rest does not mix a miscible layer.
    Cell still = cell(80.0, 250.0, 0.0, 0.0);
    FilmInterphaseTransfer.Result atRest = new FilmInterphaseTransfer().calculate(still.transport, still.sections,
        still.fluid, still.equilibrium(), 0.0, 10.0);
    assertEquals(CellMode.MISCIBLE, atRest.getModes()[0]);
    for (double source : atRest.getSourcesKgPerMetreSecond()[0][1]) {
      assertEquals(0.0, source, 1.0e-200);
    }
  }

  /**
   * A fall in pressure leaves the gas below its dew point and the oil above its bubble point. Each phase splits on its
   * own, without an interface: the gas sheds fog and the oil flashes vapour, at the bulk split time. Together they
   * carry most of the distance to local equilibrium. A rise in pressure leaves both phases stable, and nothing is shed.
   */
  @Test
  void pressureFallIsCarriedByFogAndFlashingWithoutAnInterface() {
    Cell cell = cell(80.0, 70.0, 2.0, 0.3);
    double[][][] target = cell.transport.createHydrocarbonEquilibriumInventoryChanges(cell.equilibrium(),
        cell.sections);
    double netTarget = 0.0;
    for (double value : target[0][1]) {
      netTarget += value;
    }
    assertTrue(netTarget > 0.0, "net condensation expected, got " + netTarget);
    double step = 1.0e-6;
    FilmInterphaseTransfer closure = new FilmInterphaseTransfer();
    assertTrue(closure.isBulkPhaseSplit());
    assertEquals(30.0, closure.getBulkPhaseSplitTime(), 0.0);
    FilmInterphaseTransfer.Result split = closure.calculate(cell.transport, cell.sections, cell.fluid,
        cell.equilibrium(), 0.0, step);
    double fog = split.getFogRatesKgPerMetreSecond()[0];
    double flashing = split.getFlashingRatesKgPerMetreSecond()[0];
    assertTrue(fog > 0.0 && flashing > 0.0, "fog " + fog + ", flashing " + flashing);
    double carried = (fog - flashing) * closure.getBulkPhaseSplitTime();
    assertEquals(netTarget, carried, 0.15 * netTarget, "bulk split against the equilibrium change");

    FilmInterphaseTransfer slower = new FilmInterphaseTransfer();
    slower.setBulkPhaseSplitTime(60.0);
    assertEquals(0.5 * fog, slower.calculate(cell.transport, cell.sections, cell.fluid, cell.equilibrium(), 0.0, step)
        .getFogRatesKgPerMetreSecond()[0], 1.0e-6 * fog);

    // Without an interface to speak of, the source is the bulk split alone; without the split it vanishes.
    FilmInterphaseTransfer noInterface = new FilmInterphaseTransfer();
    noInterface.setInterfaceAreaFactor(1.0e-12);
    double[] bulkOnly = noInterface.calculate(cell.transport, cell.sections, cell.fluid, cell.equilibrium(), 0.0, step)
        .getSourcesKgPerMetreSecond()[0][1];
    double bulkOnlySum = 0.0;
    for (double value : bulkOnly) {
      bulkOnlySum += value;
    }
    assertEquals(fog - flashing, bulkOnlySum, 1.0e-6 * fog);
    noInterface.setBulkPhaseSplit(false);
    FilmInterphaseTransfer.Result interfaceOnly = noInterface.calculate(cell.transport, cell.sections, cell.fluid,
        cell.equilibrium(), 0.0, step);
    assertEquals(0.0, interfaceOnly.getFogRatesKgPerMetreSecond()[0], 0.0);
    assertEquals(0.0, interfaceOnly.getFlashingRatesKgPerMetreSecond()[0], 0.0);
    for (double value : interfaceOnly.getSourcesKgPerMetreSecond()[0][1]) {
      assertEquals(0.0, value, 1.0e-9 * fog);
    }

    // A long step moves what the phases shed, no more.
    double[] longStep = closure.calculate(cell.transport, cell.sections, cell.fluid, cell.equilibrium(), 0.0, 1.0e9)
        .getSourcesKgPerMetreSecond()[0][1];
    double moved = 0.0;
    for (int component = 0; component < longStep.length; component++) {
      moved += longStep[component] * 1.0e9;
    }
    assertTrue(moved > 0.0 && moved <= netTarget * 1.15, "moved " + moved + " kg/m of " + netTarget);

    Cell compressed = cell(80.0, 86.0, 2.0, 0.3);
    FilmInterphaseTransfer.Result stable = new FilmInterphaseTransfer().calculate(compressed.transport,
        compressed.sections, compressed.fluid, compressed.equilibrium(), 0.0, step);
    assertEquals(0.0, stable.getFogRatesKgPerMetreSecond()[0], 0.0);
    assertEquals(0.0, stable.getFlashingRatesKgPerMetreSecond()[0], 0.0);
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
      assertEquals(target[0][1][component] * -Math.expm1(-1.0e-3 / 50.0) / 1.0e-3, sources[0][1][component],
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

  /**
   * Annular and slug cells are solved with their own film nodes (annular geometry and Wallis friction, slug
   * enhancement). Each gives a film solution with a time constant that differs from the stratified treatment.
   */
  @Test
  void annularAndSlugCellsUseTheirOwnFilmNodes() {
    for (FlowRegime regime : new FlowRegime[] {FlowRegime.ANNULAR, FlowRegime.SLUG}) {
      Cell cell = cell(80.0, 86.0, 5.0, 0.5);
      cell.sections[0].setFlowRegime(regime);
      FilmInterphaseTransfer specific = new FilmInterphaseTransfer();
      FilmInterphaseTransfer stratified = new FilmInterphaseTransfer();
      stratified.setRegimeSpecificFilm(false);
      FilmInterphaseTransfer.Result own = specific.calculate(cell.transport, cell.sections, cell.fluid,
          cell.equilibrium(), 0.0, 1.0);
      FilmInterphaseTransfer.Result generic = stratified.calculate(cell.transport, cell.sections, cell.fluid,
          cell.equilibrium(), 0.0, 1.0);
      assertEquals(CellMode.FILM, own.getModes()[0], regime + ": " + own.getLastFailure());
      assertEquals(CellMode.FILM, generic.getModes()[0], regime + ": " + generic.getLastFailure());
      double ownConstant = own.getSlowestTimeConstantsSeconds()[0];
      double genericConstant = generic.getSlowestTimeConstantsSeconds()[0];
      assertTrue(ownConstant > 0.0 && Double.isFinite(ownConstant), regime + " " + ownConstant);
      assertTrue(ownConstant != genericConstant, regime + " must not use the stratified node");
    }
  }

  /**
   * Every flow regime is solved, each with its own interface: a layer (stratified, slug, churn), a wall film (annular),
   * droplets in the gas (mist) or bubbles in the liquid (bubble and dispersed-bubble flow).
   */
  @Test
  void everyFlowRegimeIsSolvedWithItsOwnInterface() {
    FilmInterphaseTransfer.Result stratified = regimeResult(FlowRegime.STRATIFIED_WAVY, Double.NaN, true);
    double chord = stratified.getInterfaceWidthsMetres()[0];
    for (FlowRegime regime : new FlowRegime[] {FlowRegime.STRATIFIED_SMOOTH, FlowRegime.STRATIFIED_WAVY,
        FlowRegime.SLUG, FlowRegime.CHURN, FlowRegime.ANNULAR, FlowRegime.MIST, FlowRegime.SINGLE_PHASE_GAS}) {
      FilmInterphaseTransfer.Result result = regimeResult(regime, Double.NaN, true);
      assertEquals(CellMode.FILM, result.getModes()[0], regime + ": " + result.getLastFailure());
      assertEquals(0L, result.getFilmFailureCount(), regime.toString());
      double time = result.getCharacteristicTimesSeconds()[0];
      assertTrue(time > 0.0 && Double.isFinite(time), regime + " characteristic time " + time);
      double width = result.getInterfaceWidthsMetres()[0];
      if (regime == FlowRegime.MIST) {
        // Droplets of some tens of micrometres: thousands of square metres per metre of pipe.
        assertTrue(width > 1000.0 * chord, "droplet surface " + width + " m2/m");
        assertTrue(time < 1.0e-3 * stratified.getCharacteristicTimesSeconds()[0], "mist time " + time + " s");
      } else if (regime == FlowRegime.ANNULAR) {
        assertEquals(Math.PI * DIAMETER * Math.sqrt(1.0 - 0.0), width, 0.02 * Math.PI * DIAMETER);
      } else {
        assertEquals(chord, width, 1.0e-12, regime.toString());
      }
      // Without the regime-specific treatment the stratified film node is used on the gas-core perimeter in
      // annular and mist cells and on the chord elsewhere.
      boolean core = regime == FlowRegime.ANNULAR || regime == FlowRegime.MIST;
      assertEquals(core ? Math.PI * DIAMETER : chord,
          regimeResult(regime, Double.NaN, false).getInterfaceWidthsMetres()[0],
          core ? 0.02 * Math.PI * DIAMETER : 1.0e-12, regime + " without the regime-specific treatment");
    }
    double liquidChord = regimeResult(FlowRegime.STRATIFIED_WAVY, 0.85, true).getInterfaceWidthsMetres()[0];
    for (FlowRegime regime : new FlowRegime[] {FlowRegime.BUBBLE, FlowRegime.DISPERSED_BUBBLE,
        FlowRegime.SINGLE_PHASE_LIQUID}) {
      FilmInterphaseTransfer.Result result = regimeResult(regime, 0.85, true);
      assertEquals(CellMode.FILM, result.getModes()[0], regime + ": " + result.getLastFailure());
      double time = result.getCharacteristicTimesSeconds()[0];
      assertTrue(time > 0.0 && Double.isFinite(time), regime + " characteristic time " + time);
      // Bubbles of 0.1 mm to four capillary lengths in 15 % gas.
      double width = result.getInterfaceWidthsMetres()[0];
      assertTrue(width > 10.0 * liquidChord && width < 6.0 * 0.15 * AREA / 1.0e-4, regime + " bubble surface " + width);
    }
  }

  /**
   * Evaluate one cell in a given flow regime.
   *
   * @param regime flow regime of the cell
   * @param liquidFraction liquid volume fraction to impose, or NaN for the equilibrium amount (below 1 %)
   * @param regimeSpecific true for the regime-specific film treatment
   * @return closure result
   */
  private static FilmInterphaseTransfer.Result regimeResult(FlowRegime regime, double liquidFraction,
      boolean regimeSpecific) {
    Cell cell = cell(80.0, 86.0, 5.0, 0.5);
    if (!Double.isNaN(liquidFraction)) {
      TwoFluidSection section = cell.sections[0];
      double gasDensity = section.getGasDensity();
      double oilDensity = section.getOilDensity();
      section.setPressure(80.0e5);
      section.setGasHoldup(1.0 - liquidFraction);
      section.setLiquidHoldup(liquidFraction);
      section.setOilHoldup(liquidFraction);
      section.setGasMassPerLength(gasDensity * (1.0 - liquidFraction) * AREA);
      section.setOilMassPerLength(oilDensity * liquidFraction * AREA);
      section.setGasVelocity(1.5);
      section.setLiquidVelocity(1.0);
      section.setOilVelocity(1.0);
      TwoFluidComponentTransport transport = new TwoFluidComponentTransport(cell.fluid, cell.sections);
      section.setPressure(86.0e5);
      cell = new Cell(cell.fluid, cell.sections, transport, oilDensity * liquidFraction * AREA);
    }
    cell.sections[0].setFlowRegime(regime);
    FilmInterphaseTransfer closure = new FilmInterphaseTransfer();
    closure.setRegimeSpecificFilm(regimeSpecific);
    return closure.calculate(cell.transport, cell.sections, cell.fluid, cell.equilibrium(), 0.0, 1.0);
  }

  /**
   * Equilibrium mode of a gas/oil cell: no film is solved, and every component moves at the relaxation rate of the pipe
   * whatever the flow regime; the non-equilibrium mode of the same cell is slower where the interface limits.
   */
  @Test
  void equilibriumModeGivesTheRelaxationRateInEveryRegime() {
    for (FlowRegime regime : FlowRegime.values()) {
      Cell cell = cell(80.0, 86.0, 2.0, 0.3);
      cell.sections[0].setFlowRegime(regime);
      double[][][] target = cell.transport.createHydrocarbonEquilibriumInventoryChanges(cell.equilibrium(),
          cell.sections);
      FilmInterphaseTransfer closure = new FilmInterphaseTransfer();
      closure.setEquilibriumMode(true);
      FilmInterphaseTransfer.Result result = closure.calculate(cell.transport, cell.sections, cell.fluid,
          cell.equilibrium(), 0.0, 1.0e-3);
      assertEquals(0L, result.getFilmSolveCount());
      assertEquals(CellMode.RELAXATION, result.getModes()[0], regime.toString());
      double[][][] sources = result.getSourcesKgPerMetreSecond();
      for (int component = 0; component < sources[0][1].length; component++) {
        assertEquals(target[0][1][component] / 30.0, sources[0][1][component],
            1.0e-4 * Math.abs(target[0][1][component]) / 30.0 + 1.0e-300, regime + " component " + component);
        assertEquals(-sources[0][1][component], sources[0][0][component], 0.0);
      }
    }
    Cell cell = cell(80.0, 86.0, 2.0, 0.3);
    FilmInterphaseTransfer equilibrium = new FilmInterphaseTransfer();
    equilibrium.setEquilibriumMode(true);
    double atEquilibrium = 0.0;
    double finiteRate = 0.0;
    double[] equilibriumSources = equilibrium
        .calculate(cell.transport, cell.sections, cell.fluid, cell.equilibrium(), 0.0, 1.0)
        .getSourcesKgPerMetreSecond()[0][1];
    double[] filmSources = new FilmInterphaseTransfer()
        .calculate(cell.transport, cell.sections, cell.fluid, cell.equilibrium(), 0.0, 1.0)
        .getSourcesKgPerMetreSecond()[0][1];
    int heavy = java.util.Arrays.asList(cell.transport.getComponentNames()).indexOf("n-heptane");
    for (int component = 0; component < equilibriumSources.length; component++) {
      atEquilibrium += Math.abs(equilibriumSources[component]);
      finiteRate += Math.abs(filmSources[component]);
    }
    assertTrue(atEquilibrium > 0.0 && finiteRate > 0.0);
    // After a pressure rise the heaviest component evaporates through the interface, far slower than 30 s.
    assertTrue(Math.abs(filmSources[heavy]) < 0.5 * Math.abs(equilibriumSources[heavy]),
        "heaviest component " + filmSources[heavy] + " against " + equilibriumSources[heavy] + " kg/(m s)");
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
    assertThrows(IllegalArgumentException.class, () -> closure.setBulkPhaseSplitTime(0.0));
    assertThrows(IllegalArgumentException.class, () -> closure.setMiscibleMixingTime(-1.0));
    closure.setMiscibleMixingTime(Double.NaN);
    assertTrue(Double.isNaN(closure.getMiscibleMixingTime()));
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
    section.setGasViscosity(fluid.getPhase(0).getPhysicalProperties().getViscosity());
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
