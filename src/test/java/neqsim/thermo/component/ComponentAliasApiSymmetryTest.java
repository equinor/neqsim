package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * A name accepted by {@code addComponent} must also be accepted by every other name-taking method.
 *
 * <p>
 * Component construction resolved synonyms but {@code Phase.getComponent} did not, so a component could be created
 * under a systematic name and then be unreachable by that same name. The pairing was actively misleading:
 * {@code hasComponent} resolved the name and returned true while {@code getComponent} returned null, so a caller that
 * correctly guarded with {@code hasComponent} still received a null, and anything that dereferenced it threw a
 * NullPointerException.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class ComponentAliasApiSymmetryTest {
  /** A systematic name held in the synonym table. */
  private static final String SYNONYM = "2,2,4-trimethylpentane";

  /** The database name that synonym denotes. */
  private static final String DATABASE_NAME = "224-TM-C5";

  /**
   * Build a fluid holding one component added under the given name.
   *
   * @param name component name or synonym to add
   * @return the fluid, with a mixing rule set so binary interaction parameters exist
   */
  private static SystemInterface fluidWith(String name) {
    SystemInterface fluid = new SystemSrkEos(298.15, 10.0);
    fluid.addComponent("methane", 1.0);
    fluid.addComponent(name, 1.0);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /** A systematic name must reach the component it created. */
  @Test
  public void aSynonymReachesTheComponentItCreated() {
    SystemInterface fluid = fluidWith(SYNONYM);
    assertEquals(DATABASE_NAME, fluid.getPhase(0).getComponent(1).getComponentName(),
        "addComponent should store the database name");
    assertNotNull(fluid.getComponent(SYNONYM), "getComponent must accept the name addComponent accepted");
    assertEquals(DATABASE_NAME, fluid.getComponent(SYNONYM).getComponentName());
    assertNotNull(fluid.getPhase(0).getComponent(SYNONYM));
    assertEquals(DATABASE_NAME, fluid.getPhase(0).getComponent(SYNONYM).getComponentName());
  }

  /** hasComponent and getComponent must agree, otherwise a guarded call still fails. */
  @Test
  public void hasComponentAndGetComponentAgree() {
    SystemInterface fluid = fluidWith(SYNONYM);
    String[] names = new String[] { SYNONYM, DATABASE_NAME };
    for (int i = 0; i < names.length; i++) {
      if (fluid.hasComponent(names[i])) {
        assertNotNull(fluid.getComponent(names[i]),
            "hasComponent reported '" + names[i] + "' present but getComponent returned null");
      }
    }
  }

  /** An unknown name must still return null rather than resolving to something near it. */
  @Test
  public void anUnknownNameIsNotGuessed() {
    SystemInterface fluid = fluidWith(SYNONYM);
    assertNull(fluid.getPhase(0).getComponent("not-a-real-component"));
    assertNull(fluid.getPhase(0).getComponent("methan"), "a near miss must not resolve to methane");
  }

  /** The remaining name-taking methods must accept the same synonym. */
  @Test
  public void otherNameTakingMethodsAcceptTheSynonym() {
    SystemInterface fluid = fluidWith(SYNONYM);
    fluid.setComponentCriticalParameters(SYNONYM, 999.0, 27.0, 0.35);
    assertEquals(999.0, fluid.getComponent(DATABASE_NAME).getTC(), 1e-9,
        "setComponentCriticalParameters must act on the component the synonym denotes");

    SystemInterface second = fluidWith(SYNONYM);
    second.setBinaryInteractionParameter("methane", SYNONYM, 0.01);

    SystemInterface third = fluidWith(SYNONYM);
    int before = third.getNumberOfComponents();
    third.removeComponent(SYNONYM);
    assertEquals(before - 1, third.getNumberOfComponents(), "removeComponent must accept the synonym");
  }
}
