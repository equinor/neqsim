package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.thermo.characterization.CharacterizationOptions.ReferenceBoundaryMode;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;

/**
 * Tests the optional span-aware ({@link ReferenceBoundaryMode#CENTROID_SPAN}) reference cut-point placement of
 * {@link PseudoComponentCombiner#characterizeToReference(SystemInterface, SystemInterface, CharacterizationOptions)}
 * selected through {@link CharacterizationOptions#getReferenceBoundaryMode()}.
 *
 * <p>
 * The default {@link ReferenceBoundaryMode#MIDPOINT} places each cut edge at the arithmetic midpoint of the two
 * adjacent cut keys, which implicitly assumes equal cut widths. When the reference slate mixes a narrow cut with a much
 * wider one, the midpoint of the means is not the true span edge and material is mis-binned into the narrower
 * neighbour. The {@link ReferenceBoundaryMode#CENTROID_SPAN} mode instead requires each cut key to be the centroid of
 * its own span, which moves the contested edge and re-assigns the affected material. The span recurrence is linear in
 * molar mass, so it is only applied on the {@link CharacterizationOptions.DelumpBinningBasis#MOLAR_MASS} basis (reached
 * here by enabling delumping with resolution one, which leaves the source pseudo-components unspread).
 */
class CharacterizeToReferenceCentroidBoundaryTest {

  /**
   * Reference slate with three cuts of very unequal width on the molar-mass axis: a narrow F1/F2 pair (100, 120 g/mol)
   * next to a far heavier F3 (300 g/mol). Under MIDPOINT the F2/F3 edge lands at 210 g/mol; under CENTROID_SPAN the F2
   * span centred on 120 g/mol forces the edge down to 130 g/mol.
   */
  private static SystemInterface contestedReference() {
    SystemInterface fluid = new SystemPrEos(298.15, 60.0);
    fluid.addComponent("methane", 0.6);
    fluid.addTBPfraction("F1", 0.10, 0.100, 0.74);
    fluid.addTBPfraction("F2", 0.10, 0.120, 0.76);
    fluid.addTBPfraction("F3", 0.10, 0.300, 0.85);
    return fluid;
  }

  /** A single source pseudo-component at 150 g/mol, in the contested 130-210 g/mol band. */
  private static SystemInterface contestedSource() {
    SystemInterface fluid = new SystemPrEos(298.15, 60.0);
    fluid.addComponent("methane", 0.8);
    fluid.addTBPfraction("S1", 0.20, 0.150, 0.80);
    return fluid;
  }

  /** Options that reach the MOLAR_MASS basis (delump, resolution one) with the requested boundary mode. */
  private static CharacterizationOptions options(ReferenceBoundaryMode mode) {
    return CharacterizationOptions.builder().delumpBeforeRecharacterization(true).delumpResolution(1)
	.inheritReferenceProperties(false).referenceBoundaryMode(mode).build();
  }

  /** Name of the single populated pseudo-component cut, or {@code null} if none received any mass. */
  private static String binnedCutName(SystemInterface fluid) {
    String name = null;
    for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
      ComponentInterface c = fluid.getComponent(i);
      if ((c.isIsTBPfraction() || c.isIsPlusFraction()) && c.getNumberOfmoles() > 0.0) {
	name = c.getName();
      }
    }
    return name;
  }

  /** Total pseudo-fraction moles in a fluid. */
  private static double pseudoMoles(SystemInterface fluid) {
    double moles = 0.0;
    for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
      ComponentInterface c = fluid.getComponent(i);
      if (c.isIsTBPfraction() || c.isIsPlusFraction()) {
	moles += c.getNumberOfmoles();
      }
    }
    return moles;
  }

  @Test
  @DisplayName("referenceBoundaryMode defaults to MIDPOINT and reproduces the midpoint binning")
  void testDefaultIsMidpoint() {
    SystemInterface source = contestedSource();
    SystemInterface reference = contestedReference();

    CharacterizationOptions defaults = CharacterizationOptions.builder().delumpBeforeRecharacterization(true)
	.delumpResolution(1).inheritReferenceProperties(false).build();
    assertEquals(ReferenceBoundaryMode.MIDPOINT, defaults.getReferenceBoundaryMode(),
	"referenceBoundaryMode should default to MIDPOINT");

    SystemInterface defaultResult = PseudoComponentCombiner.characterizeToReference(source, reference, defaults);
    SystemInterface midpointResult = PseudoComponentCombiner.characterizeToReference(source, reference,
	options(ReferenceBoundaryMode.MIDPOINT));

    assertEquals(binnedCutName(midpointResult), binnedCutName(defaultResult),
	"the default mode must bin the source into the same cut as explicit MIDPOINT");
    assertEquals(pseudoMoles(midpointResult), pseudoMoles(defaultResult), 1e-9,
	"the default mode must conserve the same pseudo moles as explicit MIDPOINT");
  }

  @Test
  @DisplayName("CENTROID_SPAN moves the unequal-width edge and re-assigns the contested lump")
  void testCentroidSpanReassignsContestedLump() {
    SystemInterface source = contestedSource();
    SystemInterface reference = contestedReference();
    double sourceMoles = pseudoMoles(source);

    SystemInterface midpointResult = PseudoComponentCombiner.characterizeToReference(source, reference,
	options(ReferenceBoundaryMode.MIDPOINT));
    SystemInterface centroidResult = PseudoComponentCombiner.characterizeToReference(source, reference,
	options(ReferenceBoundaryMode.CENTROID_SPAN));

    // MIDPOINT: F2/F3 edge at 210 g/mol -> the 150 g/mol source bins to F2.
    assertEquals("F2_PC", binnedCutName(midpointResult), "MIDPOINT must bin the 150 g/mol lump into F2");
    assertNull(componentOrNull(midpointResult, "F3_PC"), "MIDPOINT must leave F3 empty");

    // CENTROID_SPAN: F2/F3 edge at 130 g/mol -> the 150 g/mol source bins to F3.
    assertEquals("F3_PC", binnedCutName(centroidResult), "CENTROID_SPAN must re-assign the 150 g/mol lump to F3");
    assertNull(componentOrNull(centroidResult, "F2_PC"), "CENTROID_SPAN must leave F2 empty");

    // Re-assignment must conserve the source pseudo moles in both modes.
    assertEquals(sourceMoles, pseudoMoles(midpointResult), sourceMoles * 1e-6, "MIDPOINT conserves pseudo moles");
    assertEquals(sourceMoles, pseudoMoles(centroidResult), sourceMoles * 1e-6, "CENTROID_SPAN conserves pseudo moles");

    ComponentInterface f3 = componentOrNull(centroidResult, "F3_PC");
    assertNotNull(f3, "CENTROID_SPAN must populate F3");
    assertEquals(0.20, f3.getNumberOfmoles(), 1e-6, "the full 0.20 mol of the source lump must land in F3");
  }

  /** Returns the named component if present and carrying mass, otherwise {@code null}. */
  private static ComponentInterface componentOrNull(SystemInterface fluid, String name) {
    for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
      ComponentInterface c = fluid.getComponent(i);
      if (c.getName().equals(name) && c.getNumberOfmoles() > 0.0) {
	return c;
      }
    }
    return null;
  }
}
