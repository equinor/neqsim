package neqsim.thermo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.fluidmechanics.flownode.twophasenode.TwoPhaseFlowNode;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData;
import neqsim.physicalproperties.methods.commonphasephysicalproperties.conductivity.HydrogenConductivityMethod;
import neqsim.physicalproperties.methods.gasphysicalproperties.diffusivity.Diffusivity;
import neqsim.physicalproperties.methods.liquidphysicalproperties.density.Density;
import neqsim.physicalproperties.system.PhysicalProperties;
import neqsim.physicalproperties.system.gasphysicalproperties.GasPhysicalProperties;
import neqsim.physicalproperties.system.liquidphysicalproperties.LiquidPhysicalProperties;
import neqsim.statistics.parameterfitting.BaseFunction;
import neqsim.statistics.parameterfitting.SampleSet;
import neqsim.statistics.parameterfitting.SampleValue;
import neqsim.statistics.parameterfitting.StatisticsBaseClass;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Required-copy failures and successful-copy independence in the remaining unreviewed clone families.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class CloneFamilyClosureTest {
  /** A required fitting-function failure propagates through the sample, sample set and statistics copy. */
  @Test
  void statisticsClonePropagatesRequiredFunctionFailure() {
    IllegalStateException cause = new IllegalStateException("Injected fitting-function-copy failure");
    FailingFunction function = new FailingFunction(cause);
    SampleValue sample = new SampleValue(10.0, 0.1, new double[] {2.0});
    sample.setFunction(function);
    SampleSet samples = new SampleSet(new SampleValue[] {sample});
    SimpleStatistics statistics = new SimpleStatistics();
    statistics.setSampleSet(samples);
    IllegalStateException actual = assertThrows(IllegalStateException.class, statistics::clone);
    assertRootCause(cause, actual);
    assertSame(samples, statistics.getSampleSet());
    assertSame(sample, statistics.getSample(0));
    assertSame(function, sample.getFunction());
  }

  /** Statistical copies retain independent sample collections, dependent values and fitting parameters. */
  @Test
  void statisticsCopiesKeepSamplesAndParametersIndependent() {
    SimpleFunction function = new SimpleFunction();
    SampleValue sample = new SampleValue(10.0, 0.1, new double[] {2.0});
    sample.setFunction(function);
    SimpleStatistics statistics = new SimpleStatistics();
    statistics.setSampleSet(new SampleSet(new SampleValue[] {sample}));
    StatisticsBaseClass copy = statistics.clone();
    assertNotSame(statistics, copy);
    assertNotSame(statistics.getSampleSet(), copy.getSampleSet());
    assertNotSame(sample, copy.getSample(0));
    assertNotSame(function, copy.getSample(0).getFunction());
    copy.getSample(0).getDependentValues()[0] = 3.0;
    copy.getSample(0).getFunction().setFittingParams(0, 5.0);
    copy.getSampleSet().add(new SampleValue());
    assertEquals(2.0, sample.getDependentValue(0), 0.0);
    assertEquals(1.0, function.getFittingParams(0), 0.0);
    assertEquals(1, statistics.getSampleSet().getLength());
    assertEquals(2, copy.getSampleSet().getLength());
  }

  /** A required fluid-copy failure retains its cause through the two-phase node's superclass-copy wrapper. */
  @Test
  void flowNodeClonePropagatesRequiredFluidFailure() {
    RequiredFlowNode node = new RequiredFlowNode(gas());
    IllegalStateException cause = new IllegalStateException("Injected node-fluid-copy failure");
    FailingFluid failingFluid = new FailingFluid(cause);
    node.bulkSystem = failingFluid;
    IllegalStateException actual = assertThrows(IllegalStateException.class, node::clone);
    assertRootCause(cause, actual);
    assertSame(failingFluid, node.getBulkSystem());
  }

  /** A required conductivity-copy failure cannot escape as a partial copy of its owning property family. */
  @Test
  void propertyFamilyClonePropagatesRequiredConductivityFailure() {
    IllegalStateException cause = new IllegalStateException("Injected conductivity-copy failure");
    FailingConductivityProperties properties = new FailingConductivityProperties(gas().getPhase(0), cause);
    IllegalStateException actual = assertThrows(IllegalStateException.class, properties::clone);
    assertRootCause(cause, actual);
  }

  /**
   * Gas diffusion matrix state and concrete liquid-density/hydrogen-conductivity calculator identities survive copying.
   */
  @Test
  void propertyCalculatorCopiesKeepIndependentMatrixState() {
    SystemSrkEos fluid = gas();
    GasPhysicalProperties gasProperties = new GasPhysicalProperties(fluid.getPhase(0), 0, 0);
    Diffusivity diffusivity = new Diffusivity(gasProperties);
    diffusivity.calcDiffusionCoefficients(0, 0);
    double original = diffusivity.getFickBinaryDiffusionCoefficient(0, 1);
    assertTrue(Double.isFinite(original) && original > 0.0,
        "Configured gas diffusion reference must be finite and positive");
    Diffusivity copiedDiffusivity = diffusivity.clone();
    assertNotSame(diffusivity, copiedDiffusivity);
    copiedDiffusivity.calcDiffusionCoefficients(0, 0)[0][1] = original + 1.0e-6;
    assertEquals(original, diffusivity.getFickBinaryDiffusionCoefficient(0, 1), 0.0);
    Density density = new Density(new LiquidPhysicalProperties(fluid.getPhase(1), 0, 0));
    assertNotSame(density, density.clone());
    SystemSrkEos hydrogen = new SystemSrkEos(298.15, 50.0);
    hydrogen.addComponent("hydrogen", 1.0);
    hydrogen.setMixingRule(2);
    hydrogen.init(0);
    HydrogenConductivityMethod conductivity = new HydrogenConductivityMethod(
        new GasPhysicalProperties(hydrogen.getPhase(0), 0, 0));
    assertNotSame(conductivity, conductivity.clone());
  }

  /**
   * Creates a configured two-component fluid without solving an unrelated process.
   *
   * @return initialized SRK fluid
   */
  private SystemSrkEos gas() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("ethane", 0.2);
    fluid.setMixingRule(2);
    fluid.init(0);
    return fluid;
  }

  /**
   * Verifies that copy-layer context preserves the original failure.
   *
   * @param expected injected copy failure
   * @param actual propagated failure
   */
  private void assertRootCause(Throwable expected, Throwable actual) {
    Throwable root = actual;
    while (root.getCause() != null) {
      root = root.getCause();
    }
    assertSame(expected, root);
  }

  /**
   * Minimal valid fitting function for copy ownership checks.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  private static class SimpleFunction extends BaseFunction {
    /** Creates one independently tunable coefficient. */
    private SimpleFunction() {
      setInitialGuess(new double[] {1.0});
    }

    /** {@inheritDoc} */
    @Override
    public double calcValue(double[] dependentValues) {
      return params[0] * dependentValues[0];
    }

    /** {@inheritDoc} */
    @Override
    public void setFittingParams(int index, double value) {
      params[index] = value;
    }
  }

  /**
   * Required fitting-function-copy failure fixture.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  private static final class FailingFunction extends SimpleFunction {
    private final IllegalStateException failure;

    /**
     * Installs the required copy failure.
     *
     * @param failure original failure to preserve
     */
    private FailingFunction(IllegalStateException failure) {
      this.failure = failure;
    }

    /** {@inheritDoc} */
    @Override
    public FailingFunction clone() {
      throw failure;
    }
  }

  /**
   * Concrete statistics container retaining the inherited clone contract.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  private static final class SimpleStatistics extends StatisticsBaseClass {
    /** {@inheritDoc} */
    @Override
    public void init() {
    }

    /** {@inheritDoc} */
    @Override
    public void solve() {
    }
  }

  /**
   * Concrete two-phase node retaining the inherited clone contract.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  private static final class RequiredFlowNode extends TwoPhaseFlowNode {
    /**
     * Configures the normal node state before replacing its failing dependency.
     *
     * @param fluid valid source fluid
     */
    private RequiredFlowNode(SystemInterface fluid) {
      super(fluid, new PipeData(0.1));
    }
  }

  /**
   * Required node-fluid-copy failure fixture.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  private static final class FailingFluid extends SystemSrkEos {
    private static final long serialVersionUID = 1L;
    private final IllegalStateException failure;

    /**
     * Installs the required copy failure.
     *
     * @param failure original failure to preserve
     */
    private FailingFluid(IllegalStateException failure) {
      super(298.15, 50.0);
      this.failure = failure;
    }

    /** {@inheritDoc} */
    @Override
    public FailingFluid clone() {
      throw failure;
    }
  }

  /**
   * Owning property family with one deliberately failing required calculator.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  private static final class FailingConductivityProperties extends GasPhysicalProperties {
    private static final long serialVersionUID = 1L;

    /**
     * Installs a failing calculator using the property's normal protected extension point.
     *
     * @param phase source phase
     * @param failure original calculator failure
     */
    private FailingConductivityProperties(PhaseInterface phase, IllegalStateException failure) {
      super(phase, 0, 0);
      conductivityCalc = new FailingConductivity(this, failure);
    }
  }

  /**
   * Required conductivity-copy failure fixture.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  private static final class FailingConductivity extends HydrogenConductivityMethod {
    private static final long serialVersionUID = 1L;
    private final IllegalStateException failure;

    /**
     * Installs the required calculator-copy failure.
     *
     * @param properties owning property family
     * @param failure original failure to preserve
     */
    private FailingConductivity(PhysicalProperties properties, IllegalStateException failure) {
      super(properties);
      this.failure = failure;
    }

    /** {@inheritDoc} */
    @Override
    public FailingConductivity clone() {
      throw failure;
    }
  }
}
