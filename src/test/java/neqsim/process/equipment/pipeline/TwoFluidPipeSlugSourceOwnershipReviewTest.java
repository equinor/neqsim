package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.LagrangianSlugTracker;
import neqsim.process.equipment.pipeline.twophasepipe.LagrangianSlugTracker.SlugBubbleUnit;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidConservationEquations;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;

/**
 * Timestep selection must use current tracked-marker membership when deciding explicit source ownership.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class TwoFluidPipeSlugSourceOwnershipReviewTest {

  /**
   * A newly created marker owns implicit subcell forces before the next source-limit evaluation.
   *
   * @throws Exception if the private timestep selector or fixture fields cannot be accessed
   */
  @Test
  void convectiveSourceLimitSeesANewMarkerImmediately() throws Exception {
    ObservedEquations equations = new ObservedEquations();
    TwoFluidPipe pipe = pipe(equations);
    replaceTrackerMarkers(pipe, Collections.singletonList(marker()));
    equations.setConservativeSlugs(Collections.<SlugBubbleUnit>emptyList());

    invokeSelector(pipe, "calcConvectiveTimeStep");

    assertEquals(1, equations.markerCountAtSourceEvaluation,
        "the source limiter must see the new marker before deciding which friction remains explicit");
  }

  /**
   * Removing the last marker restores explicit mean-cell forces in the next source-limit evaluation.
   *
   * @throws Exception if the private timestep selector or fixture fields cannot be accessed
   */
  @Test
  void convectiveSourceLimitDropsTheLastRemovedMarkerImmediately() throws Exception {
    ObservedEquations equations = new ObservedEquations();
    TwoFluidPipe pipe = pipe(equations);
    equations.setConservativeSlugs(Collections.singletonList(marker()));
    replaceTrackerMarkers(pipe, Collections.<SlugBubbleUnit>emptyList());

    invokeSelector(pipe, "calcConvectiveTimeStep");

    assertEquals(0, equations.markerCountAtSourceEvaluation,
        "a removed marker cannot keep exempting explicit mean-cell friction from its source limit");
    assertEquals(0, equations.currentMarkerCount);
  }

  /**
   * The acoustic selector also clears stale reconstruction geometry when no tracked slugs remain.
   *
   * @throws Exception if the private timestep selector or fixture fields cannot be accessed
   */
  @Test
  void acousticSelectionDropsStaleMarkersWhenTheTrackerBecomesEmpty() throws Exception {
    ObservedEquations equations = new ObservedEquations();
    TwoFluidPipe pipe = pipe(equations);
    equations.setConservativeSlugs(Collections.singletonList(marker()));
    replaceTrackerMarkers(pipe, Collections.<SlugBubbleUnit>emptyList());

    invokeSelector(pipe, "calcStableTimeStep");

    assertEquals(0, equations.currentMarkerCount,
        "an empty tracker must clear the equation geometry even when slug-film CFL selection returns early");
  }

  /**
   * Construct a scalar cell and observable source evaluator without running a thermodynamic simulation.
   *
   * @param equations source evaluator that records the geometry seen by its real source calculation
   * @return pipe ready for private timestep selection
   * @throws Exception if the fixture fields cannot be accessed
   */
  private static TwoFluidPipe pipe(ObservedEquations equations) throws Exception {
    TwoFluidPipe pipe = new TwoFluidPipe("marker-source-ownership");
    pipe.setLength(10.0);
    pipe.setDiameter(0.1);
    pipe.setNumberOfSections(1);
    pipe.setSlugTrackingMode(TwoFluidPipe.SlugTrackingMode.CONSERVATIVE_LAGRANGIAN);
    TwoFluidSection section = new TwoFluidSection(5.0, 10.0, 0.1, 0.0);
    section.setGasDensity(50.0);
    section.setOilDensity(800.0);
    section.setWaterDensity(1000.0);
    section.setLiquidDensity(800.0);
    section.setGasHoldup(0.8);
    section.setLiquidHoldup(0.2);
    section.setOilHoldup(0.2);
    section.setWaterHoldup(0.0);
    section.setWaterCut(0.0);
    section.setGasVelocity(3.0);
    section.setLiquidVelocity(1.0);
    section.setOilVelocity(1.0);
    section.setWaterVelocity(0.0);
    section.setGasViscosity(1e-5);
    section.setOilViscosity(1e-3);
    section.setWaterViscosity(1e-3);
    section.setLiquidViscosity(1e-3);
    section.setSurfaceTension(0.02);
    section.setPressure(1e5);
    section.setTemperature(300.0);
    section.setMixtureHeatCapacity(2000.0);
    section.setGasEnthalpy(5e5);
    section.setLiquidEnthalpy(2e5);
    section.updateDerivedQuantities();
    section.updateConservativeVariables();
    Field cells = TwoFluidPipe.class.getDeclaredField("sections");
    cells.setAccessible(true);
    cells.set(pipe, new TwoFluidSection[] {section});
    Field source = TwoFluidPipe.class.getDeclaredField("equations");
    source.setAccessible(true);
    source.set(pipe, equations);
    equations.setIncludeMassTransfer(false);
    equations.setIncludeEnergyEquation(false);
    equations.setEnableWaterOilSlip(false);
    equations.setConservativeSlugForceIntegrationEnabled(true);
    return pipe;
  }

  /**
   * Replace tracker membership while retaining independent equation membership until the selector synchronizes it.
   *
   * @param pipe pipe with initialized tracker
   * @param markers current tracker markers
   * @throws Exception if the tracker membership cannot be accessed
   */
  private static void replaceTrackerMarkers(TwoFluidPipe pipe, List<SlugBubbleUnit> markers) throws Exception {
    Field tracked = LagrangianSlugTracker.class.getDeclaredField("slugs");
    tracked.setAccessible(true);
    tracked.set(pipe.getLagrangianSlugTracker(), new ArrayList<SlugBubbleUnit>(markers));
  }

  /**
   * Invoke the unchanged timestep selector used by the transient loop.
   *
   * @param pipe pipe containing current cells and tracker
   * @param selector private selector method name
   * @throws Exception if selector invocation fails
   */
  private static void invokeSelector(TwoFluidPipe pipe, String selector) throws Exception {
    Method method = TwoFluidPipe.class.getDeclaredMethod(selector);
    method.setAccessible(true);
    method.invoke(pipe);
  }

  /**
   * Construct an admissible marker partially occupying the test cell.
   *
   * @return conservative slug interval
   */
  private static SlugBubbleUnit marker() {
    SlugBubbleUnit marker = new SlugBubbleUnit();
    marker.tailPosition = 0.0;
    marker.frontPosition = 1.0;
    marker.slugHoldup = 0.9;
    marker.usesConservativeFilmCoupling = true;
    marker.hasConservativeVelocity = true;
    marker.slugLiquidVelocity = 3.0;
    return marker;
  }

  /**
   * Observe geometry at the real explicit source-limit call without replacing its numerical calculation.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static final class ObservedEquations extends TwoFluidConservationEquations {
    private static final long serialVersionUID = 1L;
    private int currentMarkerCount;
    private int markerCountAtSourceEvaluation = -1;

    /** {@inheritDoc} */
    @Override
    public void setConservativeSlugs(List<SlugBubbleUnit> markers) {
      super.setConservativeSlugs(markers);
      currentMarkerCount = markers == null ? 0 : markers.size();
    }

    /** {@inheritDoc} */
    @Override
    public double calcExplicitMomentumSourceTimeStep(TwoFluidSection[] sections) {
      markerCountAtSourceEvaluation = currentMarkerCount;
      return super.calcExplicitMomentumSourceTimeStep(sections);
    }
  }
}
