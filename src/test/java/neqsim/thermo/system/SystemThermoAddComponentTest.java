package neqsim.thermo.system;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class SystemThermoAddComponentTest extends neqsim.NeqSimTest {
  SystemInterface sys;

  @BeforeEach
  void setup() {
    sys = new SystemSrkEos(298.0, 300.0);
    /*
     * sys.addComponent("nitrogen", 0.64); sys.addTBPfraction("C7", 1.06, 92.2 / 1000.0, 0.7324);
     * sys.addPlusFraction("C20", 10.62, 381.0 / 1000.0, 0.88); sys.getCharacterization().characterisePlusFraction();
     * sys.createDatabase(true); sys.setMixingRule(2);
     */
  }

  @Test
  void testAddComponent() {
    // Assert that System contains no components
    Assertions.assertEquals(0, sys.getNumberOfComponents());

    // Add a component with no moles,
    // Assert number of system components is 1 and that number of moles is 0
    sys.addComponent("nitrogen");
    Assertions.assertEquals(1, sys.getNumberOfComponents());
    Assertions.assertEquals(0, sys.getTotalNumberOfMoles());

    // Add a component with moles,
    // Assert number of components is 1 and that number of moles is equal to input
    double moles = 0.64;
    sys.addComponent("nitrogen", moles);
    Assertions.assertEquals(1, sys.getNumberOfComponents());
    Assertions.assertEquals(moles, sys.getTotalNumberOfMoles());

    // Add same component with no moles,
    // Assert no change in number of components nor number of moles
    sys.addComponent("nitrogen");
    Assertions.assertEquals(1, sys.getNumberOfComponents());
    Assertions.assertEquals(moles, sys.getTotalNumberOfMoles());

    // Add same component with no moles,
    // Assert no change in number of components nor number of moles
    sys.addComponent("nitrogen", 0);
    Assertions.assertEquals(1, sys.getNumberOfComponents());
    Assertions.assertEquals(moles, sys.getTotalNumberOfMoles());

    // Add same component with moles, assert no change in number of components
    // assert number of moles is doubled
    sys.addComponent("nitrogen", moles);
    Assertions.assertEquals(1, sys.getNumberOfComponents());
    Assertions.assertEquals(2 * moles, sys.getTotalNumberOfMoles());
  }

  @Test
  void testAddPlusFraction() {
    // Assert that System contains no components
    Assertions.assertEquals(0, sys.getNumberOfComponents());

    // Add a component with no moles,
    // assert number of components is 1 and that number of moles is 0
    sys.addPlusFraction("C20", 0, 381.0 / 1000.0, 0.88);
    Assertions.assertEquals(1, sys.getNumberOfComponents());
    Assertions.assertEquals(0, sys.getTotalNumberOfMoles());

    // Add a component with moles,
    // assert number of components is 1 and that number of moles is equal to input
    double moles = 10.62;
    sys.addPlusFraction("C20", moles, 381.0 / 1000.0, 0.88);
    Assertions.assertEquals(1, sys.getNumberOfComponents());
    Assertions.assertEquals(moles, sys.getTotalNumberOfMoles());

    // Add same component with no moles,
    // assert no change in number of components nor number of moles
    sys.addPlusFraction("C20", 0, 381.0 / 1000.0, 0.88);
    Assertions.assertEquals(1, sys.getNumberOfComponents());
    Assertions.assertEquals(moles, sys.getTotalNumberOfMoles());

    // Add same component with moles,
    // assert no change in number of components, assert number of moles is doubled
    sys.addPlusFraction("C20", moles, 381.0 / 1000.0, 0.88);
    Assertions.assertEquals(1, sys.getNumberOfComponents());
    Assertions.assertEquals(2 * moles, sys.getTotalNumberOfMoles());
  }

  @Test
  void testAddTBPFraction() {
    // Assert that System contains no components
    Assertions.assertEquals(0, sys.getNumberOfComponents());

    // Add a component with no moles,
    // assert number of components is 1 and that number of moles is 0
    sys.addTBPfraction("C7", 0, 92.2 / 1000.0, 0.7324);
    Assertions.assertEquals(1, sys.getNumberOfComponents());
    Assertions.assertEquals(0, sys.getTotalNumberOfMoles());

    // Add a component with moles,
    // assert number of components is 1 and that number of moles is equal to input
    double moles = 1.06;
    sys.addTBPfraction("C7", moles, 92.2 / 1000.0, 0.7324);
    Assertions.assertEquals(1, sys.getNumberOfComponents());
    Assertions.assertEquals(moles, sys.getTotalNumberOfMoles());

    // Add same component with no moles,
    // assert no change in number of components nor number of moles
    sys.addTBPfraction("C7", 0, 92.2 / 1000.0, 0.7324);
    Assertions.assertEquals(1, sys.getNumberOfComponents());
    Assertions.assertEquals(moles, sys.getTotalNumberOfMoles());

    // Add same component with moles,
    // assert no change in number of components, assert number of moles is doubled
    sys.addTBPfraction("C7", moles, 92.2 / 1000.0, 0.7324);
    Assertions.assertEquals(1, sys.getNumberOfComponents());
    Assertions.assertEquals(2 * moles, sys.getTotalNumberOfMoles());
  }

  /**
   * A database component added with user critical properties keeps its database ideal-gas Cp, also when given by a
   * reservoir alias (C1, iC4), while Tc, Pc and the acentric factor are overridden.
   */
  @Test
  void testAddComponentWithCriticalPropertiesKeepsDatabaseCp() {
    SystemInterface reference = new SystemSrkEos(298.0, 300.0);
    reference.addComponent("methane", 1.0);
    reference.addComponent("i-butane", 1.0);

    sys.addComponent("C1", 1.0, 190.0, 46.0, 0.01);
    sys.addComponent("iC4", 1.0, 408.0, 36.0, 0.18);

    for (int i = 0; i < 2; i++) {
      Assertions.assertEquals(reference.getPhase(0).getComponent(i).getCpA(), sys.getPhase(0).getComponent(i).getCpA(),
          1e-12);
      Assertions.assertEquals(reference.getPhase(0).getComponent(i).getCpB(), sys.getPhase(0).getComponent(i).getCpB(),
          1e-12);
      Assertions.assertEquals(reference.getPhase(0).getComponent(i).getCpC(), sys.getPhase(0).getComponent(i).getCpC(),
          1e-12);
    }
    Assertions.assertEquals(190.0, sys.getPhase(0).getComponent(0).getTC(), 1e-9);
    Assertions.assertEquals(0.18, sys.getPhase(0).getComponent(1).getAcentricFactor(), 1e-9);
    Assertions.assertEquals(2, sys.getNumberOfComponents());
  }

  /**
   * Reservoir shorthand names (C1, iC4, N2, H2O ...) given with the database critical properties must give exactly the
   * same component data, binary interaction parameters and flash properties as the database names. This is what an E300
   * style component list relies on.
   */
  @Test
  void testShortNamesGiveSamePropertiesAsDatabaseNames() {
    String[] shortNames = {"N2", "CO2", "C1", "C2", "C3", "iC4", "nC4", "iC5", "nC5", "C6", "H2O"};
    String[] dbNames = {"nitrogen", "CO2", "methane", "ethane", "propane", "i-butane", "n-butane", "i-pentane",
        "n-pentane", "n-hexane", "water"};
    double[] moles = {0.01, 0.02, 0.70, 0.08, 0.05, 0.01, 0.02, 0.01, 0.01, 0.01, 0.05};

    SystemInterface db = new SystemPrEos(313.15, 50.0);
    SystemInterface alias = new SystemPrEos(313.15, 50.0);
    for (int i = 0; i < dbNames.length; i++) {
      db.addComponent(dbNames[i], moles[i]);
    }
    for (int i = 0; i < dbNames.length; i++) {
      ComponentInterface ref = db.getPhase(0).getComponent(i);
      alias.addComponent(shortNames[i], moles[i], ref.getTC(), ref.getPC(), ref.getAcentricFactor());
    }
    db.setMixingRule(2);
    alias.setMixingRule(2);

    Assertions.assertEquals(db.getNumberOfComponents(), alias.getNumberOfComponents());
    for (int i = 0; i < dbNames.length; i++) {
      ComponentInterface a = db.getPhase(0).getComponent(i);
      ComponentInterface b = alias.getPhase(0).getComponent(i);
      String label = shortNames[i];
      Assertions.assertEquals(a.getComponentName(), b.getComponentName(), label);
      Assertions.assertEquals(a.getMolarMass(), b.getMolarMass(), 1e-12, label + " MW");
      Assertions.assertEquals(a.getNormalBoilingPoint(), b.getNormalBoilingPoint(), 1e-12, label + " Tb");
      Assertions.assertEquals(a.getCriticalVolume(), b.getCriticalVolume(), 1e-12, label + " Vc");
      Assertions.assertEquals(a.getParachorParameter(), b.getParachorParameter(), 1e-12, label + " parachor");
      Assertions.assertEquals(a.getVolumeCorrectionConst(), b.getVolumeCorrectionConst(), 1e-12,
          label + " volume shift");
      Assertions.assertEquals(a.getRacketZ(), b.getRacketZ(), 1e-12, label + " RacketZ");
      Assertions.assertEquals(a.getCpA(), b.getCpA(), 1e-12, label + " CpA");
      Assertions.assertEquals(a.getCpB(), b.getCpB(), 1e-12, label + " CpB");
      Assertions.assertEquals(a.getCpC(), b.getCpC(), 1e-12, label + " CpC");
      Assertions.assertEquals(a.getCpD(), b.getCpD(), 1e-12, label + " CpD");
      Assertions.assertEquals(a.getCpE(), b.getCpE(), 1e-12, label + " CpE");
      for (int j = 0; j < dbNames.length; j++) {
        Assertions.assertEquals(
            ((PhaseEosInterface) db.getPhase(0)).getEosMixingRule().getBinaryInteractionParameter(i, j),
            ((PhaseEosInterface) alias.getPhase(0)).getEosMixingRule().getBinaryInteractionParameter(i, j), 1e-12,
            "kij " + shortNames[i] + "-" + shortNames[j]);
      }
    }

    new ThermodynamicOperations(db).TPflash();
    new ThermodynamicOperations(alias).TPflash();
    db.initProperties();
    alias.initProperties();
    Assertions.assertEquals(db.getNumberOfPhases(), alias.getNumberOfPhases());
    Assertions.assertEquals(db.getCp(), alias.getCp(), 1e-9 * Math.abs(db.getCp()));
    Assertions.assertEquals(db.getEnthalpy(), alias.getEnthalpy(), 1e-9 * Math.abs(db.getEnthalpy()));
    Assertions.assertEquals(db.getEntropy(), alias.getEntropy(), 1e-9 * Math.abs(db.getEntropy()));
    Assertions.assertEquals(db.getDensity("kg/m3"), alias.getDensity("kg/m3"), 1e-9 * db.getDensity("kg/m3"));
    Assertions.assertEquals(db.getViscosity("kg/msec"), alias.getViscosity("kg/msec"),
        1e-9 * db.getViscosity("kg/msec"));
    Assertions.assertEquals(db.getThermalConductivity("W/mK"), alias.getThermalConductivity("W/mK"),
        1e-9 * db.getThermalConductivity("W/mK"));
  }

  @Test
  void testGetTotalNumberOfMoles() {
    sys.addComponent("nitrogen");
    Assertions.assertEquals(0, sys.getTotalNumberOfMoles());

    double moles = 1;
    sys.setTotalNumberOfMoles(moles);
    Assertions.assertEquals(moles, sys.getTotalNumberOfMoles());
  }
}
