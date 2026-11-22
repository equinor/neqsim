package neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import neqsim.physicalproperties.system.PhysicalProperties;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regressions for mutable methane-reference ownership on cloned PFCT viscosity models.
 *
 * @author NeqSim
 * @version 1.0
 */
class PFCTViscosityCloneIsolationTest {
  /**
   * Builds initialized physical properties for model construction.
   *
   * @return initialized methane/decane gas properties
   */
  private PhysicalProperties properties() {
    SystemInterface fluid = new SystemPrEos(300.0, 35.0);
    fluid.addComponent("methane", 0.98);
    fluid.addComponent("n-decane", 0.02);
    fluid.setMixingRule("classic");
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
    return fluid.getPhase("gas").getPhysicalProperties();
  }

  /** Every selectable PFCT model must own its mutable reference fluid. */
  @Test
  void allPfctClonesOwnTheirReferenceFluid() {
    PhysicalProperties properties = properties();
    PFCTViscosityMethod original = new PFCTViscosityMethod(properties);
    PFCTViscosityMethodMod86 modified = new PFCTViscosityMethodMod86(properties);
    PFCTViscosityMethodHeavyOil heavy = new PFCTViscosityMethodHeavyOil(properties);
    assertNotSame(original.referenceSystem, original.clone().referenceSystem);
    assertNotSame(modified.referenceSystem, modified.clone().referenceSystem);
    assertNotSame(heavy.referenceSystem, heavy.clone().referenceSystem);
  }

  /**
   * Forces a sibling calculation between reference initialization and density consumption.
   */
  @Test
  void interleavedSiblingCalculationCannotChangeReferenceDensity() {
    PFCTViscosityMethodHeavyOil original = new PFCTViscosityMethodHeavyOil(properties());
    InterleavingReference reference = new InterleavingReference();
    original.referenceSystem = reference;
    PFCTViscosityMethodHeavyOil sibling = original.clone();
    double expected = original.getRefComponentViscosity(260.0, 35.0);
    reference.interference = () -> sibling.getRefComponentViscosity(420.0, 120.0);
    assertEquals(expected, original.getRefComponentViscosity(260.0, 35.0), 1e-16);
    assertEquals(260.0, original.referenceSystem.getTemperature(), 0.0);
    assertEquals(420.0, sibling.referenceSystem.getTemperature(), 0.0);
  }

  /**
   * Concurrent transport-property evaluation on sibling fluid clones matches independent sequential results.
   *
   * @throws Exception if a worker fails
   */
  @Test
  void parallelClonedFluidViscositiesMatchSequentialResults() throws Exception {
    SystemInterface template = new SystemPrEos(288.15, 36.0);
    template.addComponent("methane", 0.92);
    template.addComponent("ethane", 0.05);
    template.addComponent("n-decane", 0.02);
    template.addComponent("water", 0.01);
    template.setMixingRule("classic");
    template.setMultiPhaseCheck(true);
    new ThermodynamicOperations(template).TPflash();
    template.initProperties();
    List<SystemInterface> fluids = new ArrayList<>();
    List<Double> references = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      SystemInterface fluid = template.clone();
      fluid.setTemperature(278.15 + 30.0 * i);
      fluid.setPressure(20.0 + 25.0 * i);
      new ThermodynamicOperations(fluid).TPflash();
      fluid.initProperties();
      references.add(fluid.getPhase("gas").getViscosity());
      fluids.add(fluid);
    }
    ExecutorService executor = Executors.newFixedThreadPool(fluids.size());
    try {
      List<Callable<Void>> calculations = new ArrayList<>();
      for (int i = 0; i < fluids.size(); i++) {
        final SystemInterface fluid = fluids.get(i);
        final double expected = references.get(i);
        calculations.add(() -> {
          for (int repeat = 0; repeat < 100; repeat++) {
            fluid.initProperties();
            assertEquals(expected, fluid.getPhase("gas").getViscosity(), Math.abs(expected) * 1e-12);
          }
          return null;
        });
      }
      for (Future<Void> result : executor.invokeAll(calculations)) {
        result.get();
      }
    } finally {
      executor.shutdownNow();
    }
  }

  /** Mutable reference whose density read permits deterministic interference. */
  private static class InterleavingReference extends SystemSrkEos {
    /** Serialization identifier. */
    private static final long serialVersionUID = 1L;
    /** One-shot sibling calculation, installed after cloning. */
    private transient Runnable interference;

    /** Initializes the same methane reference used by the production model. */
    InterleavingReference() {
      super(273.0, 1.01325);
      addComponent("methane", 10.0);
      init(0);
    }

    /** {@inheritDoc} */
    @Override
    public PhaseInterface getLowestGibbsEnergyPhase() {
      Runnable calculation = interference;
      interference = null;
      if (calculation != null) {
        calculation.run();
      }
      return super.getLowestGibbsEnergyPhase();
    }
  }
}
