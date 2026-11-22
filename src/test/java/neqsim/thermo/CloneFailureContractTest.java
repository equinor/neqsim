package neqsim.thermo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import neqsim.physicalproperties.PhysicalPropertyHandler;
import neqsim.physicalproperties.system.gasphysicalproperties.GasPhysicalProperties;
import neqsim.thermo.component.ComponentEos;
import neqsim.thermo.component.ComponentEosInterface;
import neqsim.thermo.component.ComponentElectrolyteCPA;
import neqsim.thermo.component.ComponentElectrolyteCPAOld;
import neqsim.thermo.component.ComponentGERG2008Eos;
import neqsim.thermo.component.ComponentPCSAFTa;
import neqsim.thermo.component.ComponentSrk;
import neqsim.thermo.component.ComponentSrkCPA;
import neqsim.thermo.component.ComponentUMRCPA;
import neqsim.thermo.component.attractiveeosterm.AttractiveTermInterface;
import neqsim.thermo.component.attractiveeosterm.AttractiveTermPr;
import neqsim.thermo.phase.PhaseElectrolyteCPAMM;
import neqsim.thermo.phase.PhaseCSPsrkEos;
import neqsim.thermo.phase.PhaseEos;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhasePCSAFT;
import neqsim.thermo.phase.PhasePrEos;
import neqsim.thermo.phase.PhasePrEosvolcor;
import neqsim.thermo.phase.PhaseSrkCPA;
import neqsim.thermo.phase.PhaseSrkEos;
import neqsim.thermo.phase.PhaseSrkEosvolcor;
import neqsim.thermo.system.SystemGEWilson;
import neqsim.thermo.system.SystemDesmukhMather;
import neqsim.thermo.system.SystemElectrolyteCPAAdvanced;
import neqsim.thermo.system.SystemElectrolyteCPAMM;
import neqsim.thermo.system.SystemFurstElectrolyteEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPCSAFT;
import neqsim.thermo.system.SystemPrCPA;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkCPA;
import neqsim.thermo.system.SystemSrkCPAstatoilFullyImplicit;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemSrkSchwartzentruberEos;
import neqsim.thermo.system.SystemSoreideWhitson;
import neqsim.thermo.system.SystemUNIFAC;
import neqsim.thermo.system.SystemWaterIF97;

/**
 * Complete-copy and failure-propagation contracts across model clone layers.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class CloneFailureContractTest {
  /** A required phase-copy failure cannot become a null system copy at a covariant wrapper. */
  @Test
  void systemClonePropagatesRequiredPhaseFailure() {
    SystemInterface[] systems = {new SystemSrkEos(298.15, 50.0), new SystemPrEos(298.15, 50.0),
        new SystemSrkCPA(298.15, 50.0), new SystemPrCPA(298.15, 50.0), new SystemUNIFAC(298.15, 50.0),
        new SystemGEWilson(298.15, 50.0), new SystemSoreideWhitson(298.15, 50.0), new SystemDesmukhMather(298.15, 50.0),
        new SystemWaterIF97(298.15, 50.0), new SystemElectrolyteCPAMM(298.15, 50.0),
        new SystemSrkCPAstatoilFullyImplicit(298.15, 50.0), new SystemFurstElectrolyteEos(298.15, 50.0),
        new SystemElectrolyteCPAAdvanced(298.15, 50.0), new SystemSrkSchwartzentruberEos(298.15, 50.0)};
    for (SystemInterface system : systems) {
      IllegalStateException cause = new IllegalStateException("Injected required phase-copy failure");
      FailingPhase phase = new FailingPhase(cause);
      system.setPhase(phase, 0);
      IllegalStateException actual = assertThrows(IllegalStateException.class, system::clone);
      assertRootCause(cause, actual);
      assertSame(phase, system.getPhase(0));
      assertEquals(298.15, system.getTemperature(), 0.0);
      assertEquals(50.0, system.getPressure(), 0.0);
    }
  }

  /** A required component-copy failure must propagate through EOS, SAFT and CPA phase wrappers. */
  @Test
  void phaseClonePropagatesRequiredComponentFailure() {
    PhaseEos[] phases = {new PhaseSrkEos(), new PhasePrEos(), new PhasePCSAFT(), new PhaseElectrolyteCPAMM(),
        new PhaseSrkEosvolcor(), new PhaseCSPsrkEos(), new PhasePrEosvolcor()};
    for (PhaseEos phase : phases) {
      phase.addComponent("methane", 1.0, 1.0, 0);
      IllegalStateException cause = new IllegalStateException("Injected required component-copy failure");
      FailingComponent component = new FailingComponent(cause);
      phase.getcomponentArray()[0] = component;
      IllegalStateException actual = assertThrows(IllegalStateException.class, phase::clone);
      assertRootCause(cause, actual);
      assertSame(component, phase.getComponent(0));
      assertEquals(1, phase.getNumberOfComponents());
    }
  }

  /** A failed attractive-term copy cannot be hidden by a covariant component wrapper. */
  @Test
  void componentClonePropagatesRequiredAttractiveTermFailure() {
    ComponentEos[] components = {new ComponentSrk("methane", 1.0, 1.0, 0),
        new ComponentGERG2008Eos("methane", 1.0, 1.0, 0),
        new ComponentSrkCPA("methane", 1.0, 1.0, 0, new PhaseSrkCPA()),
        new ComponentElectrolyteCPA("methane", 1.0, 1.0, 0), new ComponentElectrolyteCPAOld("methane", 1.0, 1.0, 0),
        new ComponentPCSAFTa("methane", 1.0, 1.0, 0), new ComponentUMRCPA("methane", 1.0, 1.0, 0)};
    for (ComponentEos component : components) {
      IllegalStateException cause = new IllegalStateException("Injected required attractive-term-copy failure");
      FailingAttractiveTerm term = new FailingAttractiveTerm(component, cause);
      component.setAttractiveParameter(term);
      IllegalStateException actual = assertThrows(IllegalStateException.class, component::clone);
      assertRootCause(cause, actual);
      assertSame(term, component.getAttractiveParameter());
    }
  }

  /**
   * A handler must fail instead of returning a partial copy whose property models still belong to its source.
   *
   * @throws ReflectiveOperationException if the failure fixture cannot install its property model
   */
  @Test
  void handlerCloneDoesNotReturnPartiallyCopiedModels() throws ReflectiveOperationException {
    PhysicalPropertyHandler handler = new PhysicalPropertyHandler();
    IllegalStateException cause = new IllegalStateException("Injected required property-model-copy failure");
    FailingGasProperties properties = new FailingGasProperties(new PhaseSrkEos(), cause);
    Field field = PhysicalPropertyHandler.class.getDeclaredField("gasPhysicalProperties");
    field.setAccessible(true);
    field.set(handler, properties);
    IllegalStateException actual = assertThrows(IllegalStateException.class, handler::clone);
    assertRootCause(cause, actual);
    assertSame(properties, field.get(handler));
  }

  /** Ordinary SRK, PR, CPA and SAFT copies preserve subtype and independent fluid/coefficient state. */
  @Test
  void ordinaryModelCopiesRemainIndependent() {
    SystemInterface[] systems = {new SystemSrkEos(298.15, 50.0), new SystemPrEos(298.15, 50.0),
        new SystemSrkCPA(298.15, 50.0), new SystemPCSAFT(298.15, 50.0)};
    for (SystemInterface system : systems) {
      system.addComponent("methane", 0.8);
      system.addComponent("ethane", 0.2);
      system.setMixingRule(2);
      system.init(0);
      SystemInterface copy = system.clone();
      assertNotNull(copy);
      assertEquals(system.getClass(), copy.getClass());
      assertNotSame(system, copy);
      for (int i = 0; i < system.getMaxNumberOfPhases(); i++) {
        assertNotSame(system.getPhases()[i], copy.getPhases()[i]);
        assertNotSame(system.getPhases()[i].getComponent(0), copy.getPhases()[i].getComponent(0));
      }
      copy.setTemperature(310.15);
      copy.setPressure(60.0);
      assertEquals(298.15, system.getTemperature(), 0.0);
      assertEquals(50.0, system.getPressure(), 0.0);
      ComponentEos originalComponent = (ComponentEos) system.getPhase(0).getComponent(0);
      ComponentEos copiedComponent = (ComponentEos) copy.getPhase(0).getComponent(0);
      AttractiveTermInterface originalTerm = originalComponent.getAttractiveParameter();
      AttractiveTermInterface copiedTerm = copiedComponent.getAttractiveParameter();
      assertNotSame(originalTerm, copiedTerm);
      double originalParameter = originalTerm.getParameters(0);
      copiedTerm.setParameters(0, originalParameter + 1.0);
      assertEquals(originalParameter, originalTerm.getParameters(0), 0.0);
    }
  }

  /** Property-family copies keep independently owned calculator objects and preserve phase-rebinding isolation. */
  @Test
  void physicalPropertyCopiesRetainIndependentCalculatorInstances() {
    SystemSrkEos system = new SystemSrkEos(298.15, 50.0);
    system.addComponent("methane", 1.0);
    system.setMixingRule(2);
    system.init(0);
    PhaseInterface phase = system.getPhase(0);
    GasPhysicalProperties properties = new GasPhysicalProperties(phase, 0, 0);
    GasPhysicalProperties copy = properties.clone();
    assertNotSame(properties, copy);
    assertNotSame(properties.getViscosityModel(), copy.getViscosityModel());
    assertNotSame(properties.getConductivityModel(), copy.getConductivityModel());
    PhaseInterface copiedPhase = phase.clone();
    copy.setPhase(copiedPhase);
    assertSame(phase, properties.getPhase());
    assertSame(copiedPhase, copy.getPhase());
  }

  /**
   * Checks that layer-specific context preserves the original failing copy as its root cause.
   *
   * @param expected injected copy failure
   * @param actual failure propagated by the public clone operation
   */
  private void assertRootCause(Throwable expected, Throwable actual) {
    Throwable root = actual;
    while (root.getCause() != null) {
      root = root.getCause();
    }
    assertSame(expected, root);
  }

  /**
   * Required phase-copy failure fixture.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  private static final class FailingPhase extends PhaseSrkEos {
    private static final long serialVersionUID = 1L;
    private final IllegalStateException failure;

    /**
     * Installs a failure thrown when the phase is copied.
     *
     * @param failure original failure to preserve
     */
    private FailingPhase(IllegalStateException failure) {
      this.failure = failure;
    }

    /** {@inheritDoc} */
    @Override
    public FailingPhase clone() {
      throw failure;
    }
  }

  /**
   * Required component-copy failure fixture.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  private static final class FailingComponent extends ComponentSrk {
    private static final long serialVersionUID = 1L;
    private final IllegalStateException failure;

    /**
     * Creates a component whose copy fails deterministically.
     *
     * @param failure original failure to preserve
     */
    private FailingComponent(IllegalStateException failure) {
      super("methane", 1.0, 1.0, 0);
      this.failure = failure;
    }

    /** {@inheritDoc} */
    @Override
    public FailingComponent clone() {
      throw failure;
    }
  }

  /**
   * Required attractive-term-copy failure fixture.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  private static final class FailingAttractiveTerm extends AttractiveTermPr {
    private static final long serialVersionUID = 1L;
    private final IllegalStateException failure;

    /**
     * Creates an attractive term whose copy fails deterministically.
     *
     * @param component owning component
     * @param failure original failure to preserve
     */
    private FailingAttractiveTerm(ComponentEosInterface component, IllegalStateException failure) {
      super(component);
      this.failure = failure;
    }

    /** {@inheritDoc} */
    @Override
    public FailingAttractiveTerm clone() {
      throw failure;
    }
  }

  /**
   * Required property-family-copy failure fixture.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  private static final class FailingGasProperties extends GasPhysicalProperties {
    private static final long serialVersionUID = 1L;
    private final IllegalStateException failure;

    /**
     * Creates a property family whose copy fails deterministically.
     *
     * @param phase phase whose property calculators are configured
     * @param failure original failure to preserve
     */
    private FailingGasProperties(PhaseInterface phase, IllegalStateException failure) {
      super(phase, 0, 0);
      this.failure = failure;
    }

    /** {@inheritDoc} */
    @Override
    public FailingGasProperties clone() {
      throw failure;
    }
  }
}
