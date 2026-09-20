package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentGePitzer;
import neqsim.thermo.phase.PhreeqcPitzerParameterCatalog.Family;
import neqsim.thermo.system.SystemPitzer;

/** Documents the distinction between preserved source rows and supported neutral chemistry. */
class PitzerNeutralCatalogSupportTest extends neqsim.NeqSimTest {
  @Test
  void sourceNeutralRowsAreNotImplicitSpeciesMappings() {
    PhreeqcPitzerParameterCatalog catalog = PhreeqcPitzerParameterCatalog.getInstance();
    assertEquals(37, catalog.size(Family.LAMBDA) + catalog.size(Family.ZETA));
    assertEquals(0, catalog.size(Family.MU));
    assertEquals(0, catalog.size(Family.ETA));
    for (String sourceName : new String[] {"B(OH)3", "H4SiO4", "H2Sg", "(H2Sg)2", "Hdg"}) {
      assertNotNull(catalog.find(Family.LAMBDA, sourceName, "Na+"), sourceName);
      assertNull(catalog.find(Family.LAMBDA, sourceName, sourceName), sourceName + " self interaction");
    }
    assertNotNull(catalog.find(Family.ZETA, "H2Sg", "Na+", "Cl-"));
    assertNull(catalog.find(Family.LAMBDA, "H2S", "Na+"));
    assertNull(catalog.find(Family.LAMBDA, "hydrogen", "Na+"));
  }

  @Test
  void unsupportedNeutralSourcesRetainLegacyFallbackAndExplicitApplicationRejectsThem() {
    for (String neutral : new String[] {"H2S", "hydrogen", "CO2"}) {
      SystemPitzer system = new SystemPitzer(298.15, 1.0);
      system.addComponent("water", 55.508);
      system.addComponent("Na+", 1.0);
      system.addComponent("Cl-", 1.0);
      system.addComponent(neutral, 0.1);
      system.init(0);
      PhasePitzer phase = (PhasePitzer) system.getPhase(1);
      assertFalse(PitzerParameterDatasets.tryApplyCompletePhreeqcPitzerCatalog(phase), neutral);
      assertFalse(phase.hasNeutralPitzerInteractions());
      IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
          () -> PitzerParameterDatasets.applyCompletePhreeqcPitzerCatalog(phase));
      assertTrue(error.getMessage().contains("no explicit"));
      assertTrue(error.getMessage().contains(neutral));
      assertFalse(phase.hasNeutralPitzerInteractions());
      ComponentGePitzer component = (ComponentGePitzer) phase.getComponent(neutral);
      assertEquals(1.0, component.getGamma(phase, phase.getNumberOfComponents(), phase.getTemperature(),
          phase.getPressure(), phase.getType()), 0.0);
      assertFalse(phase.hasNeutralPitzerInteractions());
    }
  }
}
