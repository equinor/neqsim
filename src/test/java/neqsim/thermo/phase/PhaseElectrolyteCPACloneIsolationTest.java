package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import org.ejml.data.DMatrixRMaj;
import org.ejml.simple.SimpleMatrix;
import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentElectrolyteCPA;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.flashops.TPflash;

/**
 * Verifies ownership of initialized electrolyte-CPA phase association state.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class PhaseElectrolyteCPACloneIsolationTest {
  /**
   * Checks that cloned numerical workspaces and their nested storage are independent.
   *
   * @throws Exception if reflective inspection of the owned workspaces fails
   */
  @Test
  void initializedPhaseCloneOwnsAssociationBuffers() throws Exception {
    PhaseElectrolyteCPA source = initializedPhase();
    PhaseElectrolyteCPA copy = source.clone();
    assertNotNull(field(source, "delta"));
    assertNotNull(field(source, "hessianMatrix"));
    for (Field field : PhaseElectrolyteCPA.class.getDeclaredFields()) {
      if (!isOwnedBuffer(field)) {
        continue;
      }
      field.setAccessible(true);
      assertOwned(field.get(source), field.get(copy), field.getName());
    }

    double[][] sourceDelta = (double[][]) field(source, "delta");
    double[][] copyDelta = (double[][]) field(copy, "delta");
    double[][][] sourceKlkni = (double[][][]) field(source, "Klkni");
    double[][][] copyKlkni = (double[][][]) field(copy, "Klkni");
    double sourceDeltaValue = sourceDelta[0][0];
    double sourceKlkniValue = sourceKlkni[0][0][0];
    double sourceHessian = ((SimpleMatrix) field(source, "hessianMatrix")).get(0, 0);
    copyDelta[0][0] += 1.0;
    copyKlkni[0][0][0] += 1.0;
    ((SimpleMatrix) field(copy, "hessianMatrix")).set(0, 0, sourceHessian + 1.0);
    assertEquals(sourceDeltaValue, sourceDelta[0][0], 0.0);
    assertEquals(sourceKlkniValue, sourceKlkni[0][0][0], 0.0);
    assertEquals(sourceHessian, ((SimpleMatrix) field(source, "hessianMatrix")).get(0, 0), 0.0);
  }

  /**
   * Checks that a clone rebuilds a private LU factorization before using cached derivatives.
   *
   * @throws Exception if reflective inspection of the factorization fails
   */
  @Test
  void cloneRebuildsPrivateFactorizationForImmediateDerivativeRequest() throws Exception {
    PhaseElectrolyteCPA source = initializedPhase();
    assertNotNull(field(source, "hessianLU"));
    assertNull(field(source, "hessianInvers"));

    PhaseElectrolyteCPA copy = source.clone();
    assertNull(field(copy, "hessianLU"));
    copy.calcXsitedV();
    copy.initCPAMatrix(3);

    assertNotNull(field(copy, "hessianLU"));
    assertNotSame(field(source, "hessianLU"), field(copy, "hessianLU"));
    assertNotSame(field(source, "hessianLUinput"), field(copy, "hessianLUinput"));
    for (int component = 0; component < source.getNumberOfComponents(); component++) {
      ComponentElectrolyteCPA first = (ComponentElectrolyteCPA) source.getComponent(component);
      ComponentElectrolyteCPA second = (ComponentElectrolyteCPA) copy.getComponent(component);
      assertArrayEquals(first.getXsite(), second.getXsite(), 0.0);
      assertArrayEquals(first.getXsitedV(), second.getXsitedV(), 1.0e-12);
      assertArrayEquals(first.getXsitedT(), second.getXsitedT(), 1.0e-12);
      assertArrayEquals(first.getXsitedTdT(), second.getXsitedTdT(), 1.0e-12);
      for (int site = 0; site < first.getXsitedni().length; site++) {
        assertArrayEquals(first.getXsitedni()[site], second.getXsitedni()[site], 1.0e-12);
      }
    }
    assertEquals(source.getCp(), copy.getCp(), Math.abs(source.getCp()) * 1.0e-12);
  }

  /**
   * Builds a flashed and derivative-initialized electrolyte-CPA aqueous phase.
   *
   * @return initialized aqueous phase with active water association sites
   */
  private PhaseElectrolyteCPA initializedPhase() {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(298.15, 50.0);
    fluid.addComponent("CO2", 0.2);
    fluid.addComponent("water", 1.0);
    fluid.addComponent("Na+", 0.02);
    fluid.addComponent("Cl-", 0.02);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    new TPflash(fluid).run();
    fluid.init(3);
    for (int phaseNumber = 0; phaseNumber < fluid.getNumberOfPhases(); phaseNumber++) {
      PhaseInterface candidate = fluid.getPhase(phaseNumber);
      if (candidate instanceof PhaseElectrolyteCPA
          && ((PhaseElectrolyteCPA) candidate).getTotalNumberOfAccociationSites() > 0) {
        return (PhaseElectrolyteCPA) candidate;
      }
    }
    assertTrue(false, "No initialized electrolyte-CPA phase with association sites");
    return null;
  }

  /**
   * Identifies numerical buffers owned by the electrolyte-CPA phase.
   *
   * @param field candidate field
   * @return true for an owned array or matrix buffer
   */
  private boolean isOwnedBuffer(Field field) {
    return !field.getName().equals("hessianLUinput")
        && (field.getType().isArray() || field.getType() == SimpleMatrix.class || field.getType() == DMatrixRMaj.class);
  }

  /**
   * Reads a private field without changing its contents.
   *
   * @param phase phase to inspect
   * @param name field name
   * @return field value
   * @throws Exception if the field is unavailable
   */
  private Object field(PhaseElectrolyteCPA phase, String name) throws Exception {
    Field field = PhaseElectrolyteCPA.class.getDeclaredField(name);
    field.setAccessible(true);
    return field.get(phase);
  }

  /**
   * Checks nested array identity and EJML matrix backing storage.
   *
   * @param source source buffer
   * @param copy cloned buffer
   * @param name diagnostic path
   */
  private void assertOwned(Object source, Object copy, String name) {
    if (source == null) {
      assertNull(copy, name);
      return;
    }
    assertNotSame(source, copy, name);
    if (source instanceof SimpleMatrix) {
      assertNotSame(((SimpleMatrix) source).getDDRM().data, ((SimpleMatrix) copy).getDDRM().data, name);
    } else if (source instanceof DMatrixRMaj) {
      assertNotSame(((DMatrixRMaj) source).data, ((DMatrixRMaj) copy).data, name);
    } else if (source.getClass().getComponentType().isArray()) {
      assertEquals(Array.getLength(source), Array.getLength(copy), name);
      for (int i = 0; i < Array.getLength(source); i++) {
        assertOwned(Array.get(source, i), Array.get(copy, i), name + "." + i);
      }
    }
  }
}
