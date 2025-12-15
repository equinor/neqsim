package neqsim.thermo.util.readwrite;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Whitson PVT parameter file reader.
 *
 * <p>
 * This class reads Whitson-style PVT parameter files (e.g., from Whitson+ or similar PVT software)
 * and creates a fully configured NeqSim fluid with:
 * </p>
 * <ul>
 * <li>Correct EOS type (PR, SRK)</li>
 * <li>LBC viscosity model with custom parameters</li>
 * <li>C7+ gamma distribution parameters</li>
 * <li>Omega A and Omega B parameters</li>
 * <li>Component properties (MW, Pc, Tc, ω, volume shift, Vc, Zc, parachor, SG, Tb)</li>
 * <li>Binary interaction parameters (full matrix)</li>
 * </ul>
 *
 * <h2>File Format:</h2>
 * <p>
 * The file should be tab-separated with three sections:
 * </p>
 * <ol>
 * <li><b>Parameters section</b>: EOS type, LBC coefficients, Gamma parameters, Omega values</li>
 * <li><b>Component table</b>: Component properties with header row</li>
 * <li><b>BIP matrix</b>: Binary interaction parameters with component names as row/column
 * headers</li>
 * </ol>
 *
 * <h2>Usage Example:</h2>
 *
 * <pre>
 * {@code
 * SystemInterface fluid = WhitsonPVTReader.read("path/to/volveparam.txt");
 * fluid.setTemperature(373.15);
 * fluid.setPressure(200.0);
 * fluid.init(0);
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class WhitsonPVTReader {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(WhitsonPVTReader.class);

  /** EOS parameters. */
  private String eosType = "PR";
  private double omegaA = 0.457236;
  private double omegaB = 0.0777961;

  /** LBC viscosity parameters. */
  private double lbcP0 = 0.1023;
  private double lbcP1 = 0.023364;
  private double lbcP2 = 0.058533;
  private double lbcP3 = -0.037734245;
  private double lbcP4 = 0.00839916;
  private double lbcF0 = 0.1;

  /** C7+ Gamma distribution parameters. */
  private double gammaShape = 1.0;
  private double gammaBound = 90.0;

  /** Component data. */
  private List<ComponentData> components = new ArrayList<>();

  /** BIP matrix. */
  private double[][] bipMatrix;

  /** Component name to index mapping. */
  private Map<String, Integer> componentIndex = new HashMap<>();

  /**
   * Read a Whitson PVT parameter file and create a NeqSim fluid.
   *
   * @param filePath path to the parameter file
   * @return configured SystemInterface fluid
   * @throws IOException if file cannot be read
   */
  public static SystemInterface read(String filePath) throws IOException {
    WhitsonPVTReader reader = new WhitsonPVTReader();
    reader.parseFile(filePath);
    return reader.createFluid();
  }

  /**
   * Read a Whitson PVT parameter file and create a NeqSim fluid with specified molar composition.
   *
   * @param filePath path to the parameter file
   * @param molarComposition array of molar fractions (same order as components in file)
   * @return configured SystemInterface fluid
   * @throws IOException if file cannot be read
   */
  public static SystemInterface read(String filePath, double[] molarComposition)
      throws IOException {
    WhitsonPVTReader reader = new WhitsonPVTReader();
    reader.parseFile(filePath);
    return reader.createFluid(molarComposition);
  }

  /**
   * Parse the Whitson PVT parameter file.
   *
   * @param filePath path to the file
   * @throws IOException if file cannot be read
   */
  private void parseFile(String filePath) throws IOException {
    try (BufferedReader br = new BufferedReader(new FileReader(new File(filePath)))) {
      String line;
      int section = 0; // 0=parameters, 1=components, 2=BIPs

      List<String> componentNames = new ArrayList<>();
      List<double[]> bipRows = new ArrayList<>();
      boolean inComponentTable = false;
      boolean inBipTable = false;

      while ((line = br.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty()) {
          continue;
        }

        // Detect section transitions
        if (line.startsWith("Component") && line.contains("MW")) {
          inComponentTable = true;
          inBipTable = false;
          // Skip header line
          br.readLine(); // Units line
          continue;
        }

        if (line.startsWith("BIPS")) {
          inBipTable = true;
          inComponentTable = false;
          // Parse BIP column headers
          String[] headers = line.split("\t");
          for (int i = 1; i < headers.length; i++) {
            componentNames.add(headers[i].trim());
          }
          continue;
        }

        if (inComponentTable && !inBipTable) {
          parseComponentLine(line);
        } else if (inBipTable) {
          parseBipLine(line, componentNames.size(), bipRows);
        } else {
          parseParameterLine(line);
        }
      }

      // Build BIP matrix
      if (!bipRows.isEmpty()) {
        bipMatrix = new double[bipRows.size()][bipRows.size()];
        for (int i = 0; i < bipRows.size(); i++) {
          bipMatrix[i] = bipRows.get(i);
        }
      }

      // Build component index map
      for (int i = 0; i < components.size(); i++) {
        componentIndex.put(components.get(i).name, i);
      }
    }
  }

  /**
   * Parse a parameter line (key-value pair).
   */
  private void parseParameterLine(String line) {
    String[] parts = line.split("\t");
    if (parts.length < 2) {
      return;
    }

    String key = parts[0].trim();
    String value = parts[1].trim();

    switch (key) {
      case "EOS Type":
        eosType = value;
        break;
      case "LBC P0":
        lbcP0 = parseDouble(value);
        break;
      case "LBC P1":
        lbcP1 = parseDouble(value);
        break;
      case "LBC P2":
        lbcP2 = parseDouble(value);
        break;
      case "LBC P3":
        lbcP3 = parseDouble(value);
        break;
      case "LBC P4":
        lbcP4 = parseDouble(value);
        break;
      case "LBC F0":
        lbcF0 = parseDouble(value);
        break;
      case "C7+ Gamma Shape":
        gammaShape = parseDouble(value);
        break;
      case "C7+ Gamma Bound":
        gammaBound = parseDouble(value);
        break;
      case "Omega A, ΩA":
        omegaA = parseDouble(value);
        break;
      case "Omega B, ΩB":
        omegaB = parseDouble(value);
        break;
      default:
        // Ignore unknown parameters
        break;
    }
  }

  /**
   * Parse a component data line.
   */
  private void parseComponentLine(String line) {
    String[] parts = line.split("\t");
    if (parts.length < 12) {
      return;
    }

    try {
      ComponentData comp = new ComponentData();
      comp.name = parts[0].trim();
      comp.mw = parseDouble(parts[1]);
      comp.pc = parseDouble(parts[2]); // bara
      comp.tc = parseDouble(parts[3]); // Celsius
      comp.accentricFactor = parseDouble(parts[4]);
      comp.volumeShift = parseDouble(parts[5]);
      comp.zcVisc = parseDouble(parts[6]);
      comp.vcVisc = parseDouble(parts[7]); // m3/kmol
      comp.vc = parseDouble(parts[8]); // m3/kmol
      comp.zc = parseDouble(parts[9]);
      comp.parachor = parseDouble(parts[10]);
      comp.sg = parseDouble(parts[11]);
      comp.tb = parseDouble(parts[12]); // Celsius

      // LMW is optional (only for C6+ fractions)
      if (parts.length > 13 && !parts[13].trim().isEmpty()) {
        comp.lmw = parseDouble(parts[13]);
      }

      components.add(comp);
    } catch (Exception e) {
      logger.warn("Failed to parse component line: " + line, e);
    }
  }

  /**
   * Parse a BIP matrix row.
   */
  private void parseBipLine(String line, int numComponents, List<double[]> bipRows) {
    String[] parts = line.split("\t");
    if (parts.length < 2) {
      return;
    }

    // First column is component name, rest are BIP values
    double[] row = new double[numComponents];
    for (int i = 1; i < parts.length && (i - 1) < numComponents; i++) {
      row[i - 1] = parseDouble(parts[i]);
    }
    bipRows.add(row);
  }

  /**
   * Parse a double value, handling empty or invalid strings.
   */
  private double parseDouble(String value) {
    if (value == null || value.trim().isEmpty() || value.equals("NA")) {
      return 0.0;
    }
    try {
      return Double.parseDouble(value.trim());
    } catch (NumberFormatException e) {
      return 0.0;
    }
  }

  /**
   * Create a NeqSim fluid from the parsed data.
   *
   * @return configured SystemInterface
   */
  private SystemInterface createFluid() {
    // Default composition: equal molar for all components
    double[] composition = new double[components.size()];
    for (int i = 0; i < composition.length; i++) {
      composition[i] = 1.0 / components.size();
    }
    return createFluid(composition);
  }

  /**
   * Create a NeqSim fluid with specified molar composition.
   *
   * @param molarComposition molar fractions
   * @return configured SystemInterface
   */
  private SystemInterface createFluid(double[] molarComposition) {
    // Create fluid with correct EOS type
    SystemInterface fluid = createEosSystem();

    // Add components
    for (int i = 0; i < components.size(); i++) {
      ComponentData comp = components.get(i);
      double moles = (i < molarComposition.length) ? molarComposition[i] : 0.0;

      addComponentToFluid(fluid, comp, moles);
    }

    // Set mixing rule (classic for Whitson-style EOS)
    fluid.setMixingRule(2); // Classic mixing rule

    // Enable volume correction
    fluid.useVolumeCorrection(true);

    // Initialize fluid
    fluid.init(0);

    // Set binary interaction parameters
    setBinaryInteractionParameters(fluid);

    // Apply LBC viscosity model with parameters from file
    applyLBCViscosityModel(fluid);

    return fluid;
  }

  /**
   * Apply LBC viscosity model with parameters from the Whitson PVT file.
   *
   * @param fluid the fluid to configure
   */
  private void applyLBCViscosityModel(SystemInterface fluid) {
    // LBC parameters array: [P0, P1, P2, P3, P4]
    double[] lbcParams = new double[] {lbcP0, lbcP1, lbcP2, lbcP3, lbcP4};

    // Set LBC viscosity model for all phases
    for (int phase = 0; phase < fluid.getMaxNumberOfPhases(); phase++) {
      try {
        fluid.getPhase(phase).getPhysicalProperties().setViscosityModel("LBC");
        fluid.getPhase(phase).getPhysicalProperties().setLbcParameters(lbcParams);
      } catch (Exception e) {
        logger.debug("Could not set LBC model for phase " + phase + ": " + e.getMessage());
      }
    }
  }

  /**
   * Create the appropriate EOS system based on parsed EOS type.
   */
  private SystemInterface createEosSystem() {
    double T = 288.15; // 15°C
    double P = ThermodynamicConstantsInterface.referencePressure;

    if (eosType.equalsIgnoreCase("SRK")) {
      return new neqsim.thermo.system.SystemSrkEos(T, P);
    } else if (eosType.equalsIgnoreCase("PR")) {
      return new neqsim.thermo.system.SystemPrEos(T, P);
    } else if (eosType.equalsIgnoreCase("PR78")) {
      return new neqsim.thermo.system.SystemPrEos1978(T, P);
    } else {
      // Default to PR
      return new neqsim.thermo.system.SystemPrEos(T, P);
    }
  }

  /**
   * Add a component to the fluid with all properties from the parameter file.
   */
  private void addComponentToFluid(SystemInterface fluid, ComponentData comp, double moles) {
    String name = comp.name;
    double tcKelvin = comp.tc + 273.15; // Convert from Celsius to Kelvin
    double tbKelvin = comp.tb + 273.15; // Convert from Celsius to Kelvin

    // Check if this is a standard component or a pseudo-component
    boolean isPseudoComponent = isC7PlusFraction(name);

    if (isPseudoComponent) {
      // Add as TBP fraction with properties
      String pseudoName = name + "_PC";
      fluid.addComponent(pseudoName, moles, tcKelvin, comp.pc, comp.accentricFactor);

      // Set all component properties
      for (int phase = 0; phase < fluid.getMaxNumberOfPhases(); phase++) {
        setComponentProperties(fluid, phase, pseudoName, comp, tcKelvin, tbKelvin);
      }
    } else {
      // Try to add as standard component first
      String standardName = mapToStandardName(name);
      try {
        fluid.addComponent(standardName, moles);

        // Override properties with those from file
        for (int phase = 0; phase < fluid.getMaxNumberOfPhases(); phase++) {
          setComponentProperties(fluid, phase, standardName, comp, tcKelvin, tbKelvin);
        }
      } catch (Exception e) {
        // If standard component fails, add as pseudo-component
        fluid.addComponent(name, moles, tcKelvin, comp.pc, comp.accentricFactor);
        for (int phase = 0; phase < fluid.getMaxNumberOfPhases(); phase++) {
          setComponentProperties(fluid, phase, name, comp, tcKelvin, tbKelvin);
        }
      }
    }
  }

  /**
   * Set all component properties from the data.
   */
  private void setComponentProperties(SystemInterface fluid, int phase, String name,
      ComponentData comp, double tcKelvin, double tbKelvin) {
    try {
      var component = fluid.getPhase(phase).getComponent(name);
      if (component == null) {
        return;
      }

      component.setMolarMass(comp.mw / 1000.0); // Convert g/mol to kg/mol
      component.setTC(tcKelvin);
      component.setPC(comp.pc);
      component.setAcentricFactor(comp.accentricFactor);
      component.setVolumeCorrectionConst(comp.volumeShift);
      component.setCriticalVolume(comp.vc * 1000.0); // Convert m3/kmol to cm3/mol
      component.setNormalBoilingPoint(tbKelvin);
      component.setParachorParameter(comp.parachor);

      if (comp.sg > 0) {
        component.setNormalLiquidDensity(comp.sg * 1000.0); // Convert to kg/m3
      }

      // Set viscosity-related critical properties if available
      if (comp.zcVisc > 0) {
        component.setRacketZ(comp.zcVisc);
      }
    } catch (Exception e) {
      logger.warn("Failed to set properties for component: " + name, e);
    }
  }

  /**
   * Set binary interaction parameters from the parsed BIP matrix.
   */
  private void setBinaryInteractionParameters(SystemInterface fluid) {
    if (bipMatrix == null) {
      return;
    }

    int n = Math.min(components.size(), bipMatrix.length);

    for (int i = 0; i < n; i++) {
      for (int j = i; j < n; j++) {
        if (i >= bipMatrix.length || j >= bipMatrix[i].length) {
          continue;
        }

        double kij = bipMatrix[i][j];
        if (Math.abs(kij) < 1e-10) {
          continue; // Skip zero BIPs
        }

        for (int phase = 0; phase < fluid.getMaxNumberOfPhases(); phase++) {
          try {
            ((PhaseEosInterface) fluid.getPhase(phase)).getEosMixingRule()
                .setBinaryInteractionParameter(i, j, kij);
            ((PhaseEosInterface) fluid.getPhase(phase)).getEosMixingRule()
                .setBinaryInteractionParameter(j, i, kij);
          } catch (Exception e) {
            logger.warn("Failed to set BIP for components " + i + ", " + j, e);
          }
        }
      }
    }
  }

  /**
   * Check if component name indicates a C7+ fraction.
   */
  private boolean isC7PlusFraction(String name) {
    if (name.matches("C\\d+.*")) {
      // Extract carbon number
      String numStr = name.replaceAll("[^0-9]", "");
      if (!numStr.isEmpty()) {
        int carbonNumber = Integer.parseInt(numStr.substring(0, Math.min(2, numStr.length())));
        return carbonNumber >= 7;
      }
    }
    return name.contains("+") || name.contains("C36");
  }

  /**
   * Map Whitson component names to NeqSim standard names.
   */
  private String mapToStandardName(String name) {
    switch (name.toUpperCase()) {
      case "C1":
        return "methane";
      case "C2":
        return "ethane";
      case "C3":
        return "propane";
      case "I-C4":
        return "i-butane";
      case "N-C4":
        return "n-butane";
      case "NEO-C5":
        return "22-dim-C3";
      case "I-C5":
        return "i-pentane";
      case "N-C5":
        return "n-pentane";
      case "C6":
        return "n-hexane";
      case "CO2":
        return "CO2";
      case "H2S":
        return "H2S";
      case "N2":
        return "nitrogen";
      case "H2O":
        return "water";
      case "HE":
        return "helium";
      case "AR":
        return "argon";
      default:
        return name;
    }
  }

  /**
   * Get the LBC viscosity parameters.
   *
   * @return array of [P0, P1, P2, P3, P4, F0]
   */
  public double[] getLBCParameters() {
    return new double[] {lbcP0, lbcP1, lbcP2, lbcP3, lbcP4, lbcF0};
  }

  /**
   * Get the C7+ gamma distribution parameters.
   *
   * @return array of [shape, bound]
   */
  public double[] getGammaParameters() {
    return new double[] {gammaShape, gammaBound};
  }

  /**
   * Get the Omega A parameter.
   *
   * @return Omega A value
   */
  public double getOmegaA() {
    return omegaA;
  }

  /**
   * Get the Omega B parameter.
   *
   * @return Omega B value
   */
  public double getOmegaB() {
    return omegaB;
  }

  /**
   * Get the number of components.
   *
   * @return number of components
   */
  public int getNumberOfComponents() {
    return components.size();
  }

  /**
   * Get component names.
   *
   * @return list of component names
   */
  public List<String> getComponentNames() {
    List<String> names = new ArrayList<>();
    for (ComponentData comp : components) {
      names.add(comp.name);
    }
    return names;
  }

  /**
   * Internal class to hold component data.
   */
  private static class ComponentData {
    String name;
    double mw; // g/mol
    double pc; // bara
    double tc; // Celsius
    double accentricFactor;
    double volumeShift;
    double zcVisc;
    double vcVisc; // m3/kmol
    double vc; // m3/kmol
    double zc;
    double parachor;
    double sg; // specific gravity
    double tb; // Celsius
    double lmw; // lumped molecular weight (optional)
  }
}
