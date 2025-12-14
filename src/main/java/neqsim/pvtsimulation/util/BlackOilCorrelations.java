package neqsim.pvtsimulation.util;

/**
 * Black oil correlations for estimating PVT properties.
 *
 * <p>
 * This class provides industry-standard empirical correlations for:
 * <ul>
 * <li>Bubble point pressure (Pb)</li>
 * <li>Solution gas-oil ratio (Rs)</li>
 * <li>Oil formation volume factor (Bo)</li>
 * <li>Dead oil viscosity</li>
 * <li>Live oil viscosity (saturated and undersaturated)</li>
 * <li>Gas formation volume factor (Bg)</li>
 * <li>Gas viscosity</li>
 * <li>Oil compressibility</li>
 * </ul>
 *
 * <p>
 * Supported correlations include:
 * <ul>
 * <li>Standing (1947)</li>
 * <li>Vasquez-Beggs (1980)</li>
 * <li>Glaso (1980)</li>
 * <li>Petrosky-Farshad (1993)</li>
 * <li>Beggs-Robinson (1975) - viscosity</li>
 * <li>Lee-Gonzalez-Eakin (1966) - gas viscosity</li>
 * </ul>
 *
 * <p>
 * <b>Unit Support:</b> All core methods use field units (psia, °F, scf/STB, cP). For SI/NeqSim
 * units, use the overloaded methods that accept a {@link BlackOilUnits} parameter, or use the
 * convenience "SI" suffix methods (e.g., {@code bubblePointStandingSI}).
 *
 * @author ESOL
 * @see BlackOilUnits
 */
public final class BlackOilCorrelations {

  /** Default unit system for new calculations. */
  private static BlackOilUnits defaultUnits = BlackOilUnits.FIELD;

  private BlackOilCorrelations() {
    // Utility class
  }

  /**
   * Get the default unit system.
   *
   * @return Default unit system
   */
  public static BlackOilUnits getDefaultUnits() {
    return defaultUnits;
  }

  /**
   * Set the default unit system for all calculations.
   *
   * @param units Unit system to use as default
   */
  public static void setDefaultUnits(BlackOilUnits units) {
    defaultUnits = units;
  }

  // ==================== BUBBLE POINT PRESSURE ====================

  /**
   * Standing correlation for bubble point pressure.
   *
   * @param Rs Solution GOR at bubble point (scf/STB)
   * @param gammaG Gas specific gravity (air = 1)
   * @param gammaO Oil specific gravity (water = 1) or API gravity if useAPI=true
   * @param T Temperature (°F)
   * @param useAPI If true, gammaO is treated as API gravity
   * @return Bubble point pressure (psia)
   */
  public static double bubblePointStanding(double Rs, double gammaG, double gammaO, double T,
      boolean useAPI) {
    double api = useAPI ? gammaO : apiFromSpecificGravity(gammaO);
    double a = 0.00091 * T - 0.0125 * api;
    double pb = 18.2 * (Math.pow(Rs / gammaG, 0.83) * Math.pow(10, a) - 1.4);
    return pb;
  }

  /**
   * Vasquez-Beggs correlation for bubble point pressure.
   *
   * @param Rs Solution GOR (scf/STB)
   * @param gammaG Gas specific gravity (air = 1)
   * @param api API gravity
   * @param T Temperature (°F)
   * @return Bubble point pressure (psia)
   */
  public static double bubblePointVasquezBeggs(double Rs, double gammaG, double api, double T) {
    // Correct gas gravity to separator conditions (100 psig)
    double gammaGc = gammaG * (1 + 5.912e-5 * api * 114.7 * Math.log10(114.7 / 114.7));

    double c1, c2, c3;
    if (api <= 30) {
      c1 = 0.0362;
      c2 = 1.0937;
      c3 = 25.724;
    } else {
      c1 = 0.0178;
      c2 = 1.187;
      c3 = 23.931;
    }

    double pb = Math.pow(Rs / (c1 * gammaGc * Math.exp(c3 * api / (T + 460))), 1.0 / c2);
    return pb;
  }

  /**
   * Glaso correlation for bubble point pressure.
   *
   * @param Rs Solution GOR (scf/STB)
   * @param gammaG Gas specific gravity (air = 1)
   * @param api API gravity
   * @param T Temperature (°F)
   * @return Bubble point pressure (psia)
   */
  public static double bubblePointGlaso(double Rs, double gammaG, double api, double T) {
    double gammaO = specificGravityFromAPI(api);
    double a = Math.pow(Rs / gammaG, 0.816) * Math.pow(T, 0.172) / Math.pow(gammaO, 0.989);
    double logPb = 1.7669 + 1.7447 * Math.log10(a) - 0.30218 * Math.pow(Math.log10(a), 2);
    return Math.pow(10, logPb);
  }

  // ==================== BUBBLE POINT - UNIT-AWARE METHODS ====================

  /**
   * Standing correlation for bubble point pressure with unit support.
   *
   * @param Rs Solution GOR at bubble point (in inputUnits)
   * @param gammaG Gas specific gravity (air = 1)
   * @param api API gravity
   * @param T Temperature (in inputUnits)
   * @param inputUnits Unit system for inputs and outputs
   * @return Bubble point pressure (in inputUnits)
   */
  public static double bubblePointStanding(double Rs, double gammaG, double api, double T,
      BlackOilUnits inputUnits) {
    double rsField = BlackOilUnits.toScfPerStb(Rs, inputUnits);
    double tField = BlackOilUnits.toFahrenheit(T, inputUnits);
    double pbField = bubblePointStanding(rsField, gammaG, api, tField, true);
    return BlackOilUnits.fromPsia(pbField, inputUnits);
  }

  /**
   * Standing correlation for bubble point using SI units (bara, °C, Sm³/Sm³).
   *
   * @param Rs Solution GOR (Sm³/Sm³)
   * @param gammaG Gas specific gravity (air = 1)
   * @param api API gravity
   * @param T Temperature (°C)
   * @return Bubble point pressure (bara)
   */
  public static double bubblePointStandingSI(double Rs, double gammaG, double api, double T) {
    return bubblePointStanding(Rs, gammaG, api, T, BlackOilUnits.SI);
  }

  /**
   * Vasquez-Beggs correlation for bubble point pressure with unit support.
   *
   * @param Rs Solution GOR (in inputUnits)
   * @param gammaG Gas specific gravity (air = 1)
   * @param api API gravity
   * @param T Temperature (in inputUnits)
   * @param inputUnits Unit system for inputs and outputs
   * @return Bubble point pressure (in inputUnits)
   */
  public static double bubblePointVasquezBeggs(double Rs, double gammaG, double api, double T,
      BlackOilUnits inputUnits) {
    double rsField = BlackOilUnits.toScfPerStb(Rs, inputUnits);
    double tField = BlackOilUnits.toFahrenheit(T, inputUnits);
    double pbField = bubblePointVasquezBeggs(rsField, gammaG, api, tField);
    return BlackOilUnits.fromPsia(pbField, inputUnits);
  }

  /**
   * Vasquez-Beggs correlation for bubble point using SI units (bara, °C, Sm³/Sm³).
   *
   * @param Rs Solution GOR (Sm³/Sm³)
   * @param gammaG Gas specific gravity (air = 1)
   * @param api API gravity
   * @param T Temperature (°C)
   * @return Bubble point pressure (bara)
   */
  public static double bubblePointVasquesBeggsS(double Rs, double gammaG, double api, double T) {
    return bubblePointVasquezBeggs(Rs, gammaG, api, T, BlackOilUnits.SI);
  }

  /**
   * Glaso correlation for bubble point pressure with unit support.
   *
   * @param Rs Solution GOR (in inputUnits)
   * @param gammaG Gas specific gravity (air = 1)
   * @param api API gravity
   * @param T Temperature (in inputUnits)
   * @param inputUnits Unit system for inputs and outputs
   * @return Bubble point pressure (in inputUnits)
   */
  public static double bubblePointGlaso(double Rs, double gammaG, double api, double T,
      BlackOilUnits inputUnits) {
    double rsField = BlackOilUnits.toScfPerStb(Rs, inputUnits);
    double tField = BlackOilUnits.toFahrenheit(T, inputUnits);
    double pbField = bubblePointGlaso(rsField, gammaG, api, tField);
    return BlackOilUnits.fromPsia(pbField, inputUnits);
  }

  /**
   * Glaso correlation for bubble point using SI units (bara, °C, Sm³/Sm³).
   *
   * @param Rs Solution GOR (Sm³/Sm³)
   * @param gammaG Gas specific gravity (air = 1)
   * @param api API gravity
   * @param T Temperature (°C)
   * @return Bubble point pressure (bara)
   */
  public static double bubblePointGlasoSI(double Rs, double gammaG, double api, double T) {
    return bubblePointGlaso(Rs, gammaG, api, T, BlackOilUnits.SI);
  }

  // ==================== SOLUTION GAS-OIL RATIO ====================

  /**
   * Standing correlation for solution GOR.
   *
   * @param p Pressure (psia)
   * @param gammaG Gas specific gravity (air = 1)
   * @param api API gravity
   * @param T Temperature (°F)
   * @return Rs in scf/STB
   */
  public static double solutionGORStanding(double p, double gammaG, double api, double T) {
    double a = 0.00091 * T - 0.0125 * api;
    double Rs = gammaG * Math.pow((p / 18.2 + 1.4) * Math.pow(10, -a), 1.2048);
    return Rs;
  }

  /**
   * Vasquez-Beggs correlation for solution GOR.
   *
   * @param p Pressure (psia)
   * @param gammaG Gas specific gravity (air = 1)
   * @param api API gravity
   * @param T Temperature (°F)
   * @return Rs in scf/STB
   */
  public static double solutionGORVasquezBeggs(double p, double gammaG, double api, double T) {
    double gammaGc = gammaG * (1 + 5.912e-5 * api * 114.7 * Math.log10(114.7 / 114.7));

    double c1, c2, c3;
    if (api <= 30) {
      c1 = 0.0362;
      c2 = 1.0937;
      c3 = 25.724;
    } else {
      c1 = 0.0178;
      c2 = 1.187;
      c3 = 23.931;
    }

    double Rs = c1 * gammaGc * Math.pow(p, c2) * Math.exp(c3 * api / (T + 460));
    return Rs;
  }

  // ==================== SOLUTION GOR - UNIT-AWARE METHODS ====================

  /**
   * Standing correlation for solution GOR with unit support.
   *
   * @param p Pressure (in inputUnits)
   * @param gammaG Gas specific gravity (air = 1)
   * @param api API gravity
   * @param T Temperature (in inputUnits)
   * @param inputUnits Unit system for inputs and outputs
   * @return Rs (in inputUnits)
   */
  public static double solutionGORStanding(double p, double gammaG, double api, double T,
      BlackOilUnits inputUnits) {
    double pField = BlackOilUnits.toPsia(p, inputUnits);
    double tField = BlackOilUnits.toFahrenheit(T, inputUnits);
    double rsField = solutionGORStanding(pField, gammaG, api, tField);
    return BlackOilUnits.fromScfPerStb(rsField, inputUnits);
  }

  /**
   * Standing correlation for solution GOR using SI units.
   *
   * @param p Pressure (bara)
   * @param gammaG Gas specific gravity (air = 1)
   * @param api API gravity
   * @param T Temperature (°C)
   * @return Rs in Sm³/Sm³
   */
  public static double solutionGORStandingSI(double p, double gammaG, double api, double T) {
    return solutionGORStanding(p, gammaG, api, T, BlackOilUnits.SI);
  }

  /**
   * Vasquez-Beggs correlation for solution GOR with unit support.
   *
   * @param p Pressure (in inputUnits)
   * @param gammaG Gas specific gravity (air = 1)
   * @param api API gravity
   * @param T Temperature (in inputUnits)
   * @param inputUnits Unit system for inputs and outputs
   * @return Rs (in inputUnits)
   */
  public static double solutionGORVasquezBeggs(double p, double gammaG, double api, double T,
      BlackOilUnits inputUnits) {
    double pField = BlackOilUnits.toPsia(p, inputUnits);
    double tField = BlackOilUnits.toFahrenheit(T, inputUnits);
    double rsField = solutionGORVasquezBeggs(pField, gammaG, api, tField);
    return BlackOilUnits.fromScfPerStb(rsField, inputUnits);
  }

  /**
   * Vasquez-Beggs correlation for solution GOR using SI units.
   *
   * @param p Pressure (bara)
   * @param gammaG Gas specific gravity (air = 1)
   * @param api API gravity
   * @param T Temperature (°C)
   * @return Rs in Sm³/Sm³
   */
  public static double solutionGORVasquesBeggsS(double p, double gammaG, double api, double T) {
    return solutionGORVasquezBeggs(p, gammaG, api, T, BlackOilUnits.SI);
  }

  // ==================== OIL FORMATION VOLUME FACTOR ====================

  /**
   * Standing correlation for Bo (saturated oil).
   *
   * @param Rs Solution GOR (scf/STB)
   * @param gammaG Gas specific gravity (air = 1)
   * @param gammaO Oil specific gravity (water = 1)
   * @param T Temperature (°F)
   * @return Bo in bbl/STB
   */
  public static double oilFVFStanding(double Rs, double gammaG, double gammaO, double T) {
    double f = Rs * Math.sqrt(gammaG / gammaO) + 1.25 * T;
    double Bo = 0.9759 + 0.00012 * Math.pow(f, 1.2);
    return Bo;
  }

  /**
   * Vasquez-Beggs correlation for Bo (saturated oil).
   *
   * @param Rs Solution GOR (scf/STB)
   * @param gammaG Gas specific gravity (air = 1)
   * @param api API gravity
   * @param T Temperature (°F)
   * @return Bo in bbl/STB
   */
  public static double oilFVFVasquezBeggs(double Rs, double gammaG, double api, double T) {
    double gammaGc = gammaG * (1 + 5.912e-5 * api * 114.7 * Math.log10(114.7 / 114.7));

    double c1, c2, c3;
    if (api <= 30) {
      c1 = 4.677e-4;
      c2 = 1.751e-5;
      c3 = -1.811e-8;
    } else {
      c1 = 4.670e-4;
      c2 = 1.100e-5;
      c3 = 1.337e-9;
    }

    double Bo = 1.0 + c1 * Rs + (T - 60) * (api / gammaGc) * (c2 + c3 * Rs);
    return Bo;
  }

  /**
   * Calculate undersaturated oil Bo using compressibility.
   *
   * @param Bob Bo at bubble point (bbl/STB)
   * @param co Oil compressibility (1/psi)
   * @param p Current pressure (psia)
   * @param pb Bubble point pressure (psia)
   * @return Bo in bbl/STB
   */
  public static double oilFVFUndersaturated(double Bob, double co, double p, double pb) {
    return Bob * Math.exp(-co * (p - pb));
  }

  // ==================== OIL FVF - UNIT-AWARE METHODS ====================

  /**
   * Standing correlation for Bo with unit support.
   *
   * @param Rs Solution GOR (in inputUnits)
   * @param gammaG Gas specific gravity (air = 1)
   * @param gammaO Oil specific gravity (water = 1)
   * @param T Temperature (in inputUnits)
   * @param inputUnits Unit system for inputs
   * @return Bo in bbl/STB (dimensionless ratio, same in all units)
   */
  public static double oilFVFStanding(double Rs, double gammaG, double gammaO, double T,
      BlackOilUnits inputUnits) {
    double rsField = BlackOilUnits.toScfPerStb(Rs, inputUnits);
    double tField = BlackOilUnits.toFahrenheit(T, inputUnits);
    return oilFVFStanding(rsField, gammaG, gammaO, tField);
  }

  /**
   * Standing correlation for Bo using SI units.
   *
   * @param Rs Solution GOR (Sm³/Sm³)
   * @param gammaG Gas specific gravity (air = 1)
   * @param gammaO Oil specific gravity (water = 1)
   * @param T Temperature (°C)
   * @return Bo (m³/Sm³)
   */
  public static double oilFVFStandingSI(double Rs, double gammaG, double gammaO, double T) {
    return oilFVFStanding(Rs, gammaG, gammaO, T, BlackOilUnits.SI);
  }

  /**
   * Vasquez-Beggs correlation for Bo with unit support.
   *
   * @param Rs Solution GOR (in inputUnits)
   * @param gammaG Gas specific gravity (air = 1)
   * @param api API gravity
   * @param T Temperature (in inputUnits)
   * @param inputUnits Unit system for inputs
   * @return Bo in bbl/STB (dimensionless ratio, same in all units)
   */
  public static double oilFVFVasquezBeggs(double Rs, double gammaG, double api, double T,
      BlackOilUnits inputUnits) {
    double rsField = BlackOilUnits.toScfPerStb(Rs, inputUnits);
    double tField = BlackOilUnits.toFahrenheit(T, inputUnits);
    return oilFVFVasquezBeggs(rsField, gammaG, api, tField);
  }

  /**
   * Vasquez-Beggs correlation for Bo using SI units.
   *
   * @param Rs Solution GOR (Sm³/Sm³)
   * @param gammaG Gas specific gravity (air = 1)
   * @param api API gravity
   * @param T Temperature (°C)
   * @return Bo (m³/Sm³)
   */
  public static double oilFVFVasquesBeggsS(double Rs, double gammaG, double api, double T) {
    return oilFVFVasquezBeggs(Rs, gammaG, api, T, BlackOilUnits.SI);
  }

  /**
   * Calculate undersaturated oil Bo with unit support.
   *
   * @param Bob Bo at bubble point
   * @param co Oil compressibility (in inputUnits: 1/psi for FIELD, 1/bara for SI/NEQSIM)
   * @param p Current pressure (in inputUnits)
   * @param pb Bubble point pressure (in inputUnits)
   * @param inputUnits Unit system for inputs
   * @return Bo (dimensionless)
   */
  public static double oilFVFUndersaturated(double Bob, double co, double p, double pb,
      BlackOilUnits inputUnits) {
    double pField = BlackOilUnits.toPsia(p, inputUnits);
    double pbField = BlackOilUnits.toPsia(pb, inputUnits);
    double coField = BlackOilUnits.toPerPsi(co, inputUnits);
    return oilFVFUndersaturated(Bob, coField, pField, pbField);
  }

  // ==================== OIL COMPRESSIBILITY ====================

  /**
   * Vasquez-Beggs correlation for undersaturated oil compressibility.
   *
   * @param Rs Solution GOR at Pb (scf/STB)
   * @param gammaG Gas specific gravity
   * @param api API gravity
   * @param T Temperature (°F)
   * @param p Pressure (psia)
   * @return Oil compressibility (1/psi)
   */
  public static double oilCompressibilityVasquezBeggs(double Rs, double gammaG, double api,
      double T, double p) {
    double co = (-1433 + 5 * Rs + 17.2 * T - 1180 * gammaG + 12.61 * api) / (1e5 * p);
    return co;
  }

  /**
   * Vasquez-Beggs correlation for oil compressibility with unit support.
   *
   * @param Rs Solution GOR at Pb (in inputUnits)
   * @param gammaG Gas specific gravity
   * @param api API gravity
   * @param T Temperature (in inputUnits)
   * @param p Pressure (in inputUnits)
   * @param inputUnits Unit system for inputs and outputs
   * @return Oil compressibility (in inputUnits: 1/psi for FIELD, 1/bara for SI/NEQSIM)
   */
  public static double oilCompressibilityVasquezBeggs(double Rs, double gammaG, double api,
      double T, double p, BlackOilUnits inputUnits) {
    double rsField = BlackOilUnits.toScfPerStb(Rs, inputUnits);
    double tField = BlackOilUnits.toFahrenheit(T, inputUnits);
    double pField = BlackOilUnits.toPsia(p, inputUnits);
    double coField = oilCompressibilityVasquezBeggs(rsField, gammaG, api, tField, pField);
    return BlackOilUnits.fromPerPsi(coField, inputUnits);
  }

  /**
   * Vasquez-Beggs correlation for oil compressibility using SI units.
   *
   * @param Rs Solution GOR at Pb (Sm³/Sm³)
   * @param gammaG Gas specific gravity
   * @param api API gravity
   * @param T Temperature (°C)
   * @param p Pressure (bara)
   * @return Oil compressibility (1/bara)
   */
  public static double oilCompressibilityVasquesBeggsS(double Rs, double gammaG, double api,
      double T, double p) {
    return oilCompressibilityVasquezBeggs(Rs, gammaG, api, T, p, BlackOilUnits.SI);
  }

  // ==================== DEAD OIL VISCOSITY ====================

  /**
   * Beggs-Robinson correlation for dead oil viscosity.
   *
   * @param api API gravity
   * @param T Temperature (°F)
   * @return Dead oil viscosity (cP)
   */
  public static double deadOilViscosityBeggsRobinson(double api, double T) {
    double z = 3.0324 - 0.02023 * api;
    double y = Math.pow(10, z);
    double x = y * Math.pow(T, -1.163);
    double muOD = Math.pow(10, x) - 1;
    return muOD;
  }

  /**
   * Glaso correlation for dead oil viscosity.
   *
   * @param api API gravity
   * @param T Temperature (°F)
   * @return Dead oil viscosity (cP)
   */
  public static double deadOilViscosityGlaso(double api, double T) {
    double a = 10.313 * Math.log10(T) - 36.447;
    double muOD = 3.141e10 * Math.pow(T, -3.444) * Math.pow(Math.log10(api), a);
    return muOD;
  }

  /**
   * Kartoatmodjo-Schmidt correlation for dead oil viscosity.
   *
   * @param api API gravity
   * @param T Temperature (°F)
   * @return Dead oil viscosity (cP)
   */
  public static double deadOilViscosityKartoatmodjo(double api, double T) {
    double a = 5.7526 * Math.log10(T) - 26.9718;
    double muOD = 16e8 * Math.pow(T, -2.8177) * Math.pow(Math.log10(api), a);
    return muOD;
  }

  // ==================== DEAD OIL VISCOSITY - UNIT-AWARE METHODS ====================

  /**
   * Beggs-Robinson correlation for dead oil viscosity with unit support.
   *
   * @param api API gravity
   * @param T Temperature (in inputUnits)
   * @param inputUnits Unit system for inputs and outputs
   * @return Dead oil viscosity (in inputUnits: cP for FIELD, Pa·s for SI/NEQSIM)
   */
  public static double deadOilViscosityBeggsRobinson(double api, double T,
      BlackOilUnits inputUnits) {
    double tField = BlackOilUnits.toFahrenheit(T, inputUnits);
    double muField = deadOilViscosityBeggsRobinson(api, tField);
    return BlackOilUnits.fromCentipoise(muField, inputUnits);
  }

  /**
   * Beggs-Robinson correlation for dead oil viscosity using SI units.
   *
   * @param api API gravity
   * @param T Temperature (°C)
   * @return Dead oil viscosity (Pa·s)
   */
  public static double deadOilViscosityBeggsRobinsonSI(double api, double T) {
    return deadOilViscosityBeggsRobinson(api, T, BlackOilUnits.SI);
  }

  /**
   * Glaso correlation for dead oil viscosity with unit support.
   *
   * @param api API gravity
   * @param T Temperature (in inputUnits)
   * @param inputUnits Unit system for inputs and outputs
   * @return Dead oil viscosity (in inputUnits: cP for FIELD, Pa·s for SI/NEQSIM)
   */
  public static double deadOilViscosityGlaso(double api, double T, BlackOilUnits inputUnits) {
    double tField = BlackOilUnits.toFahrenheit(T, inputUnits);
    double muField = deadOilViscosityGlaso(api, tField);
    return BlackOilUnits.fromCentipoise(muField, inputUnits);
  }

  /**
   * Glaso correlation for dead oil viscosity using SI units.
   *
   * @param api API gravity
   * @param T Temperature (°C)
   * @return Dead oil viscosity (Pa·s)
   */
  public static double deadOilViscosityGlasoSI(double api, double T) {
    return deadOilViscosityGlaso(api, T, BlackOilUnits.SI);
  }

  /**
   * Kartoatmodjo-Schmidt correlation for dead oil viscosity with unit support.
   *
   * @param api API gravity
   * @param T Temperature (in inputUnits)
   * @param inputUnits Unit system for inputs and outputs
   * @return Dead oil viscosity (in inputUnits: cP for FIELD, Pa·s for SI/NEQSIM)
   */
  public static double deadOilViscosityKartoatmodjo(double api, double T,
      BlackOilUnits inputUnits) {
    double tField = BlackOilUnits.toFahrenheit(T, inputUnits);
    double muField = deadOilViscosityKartoatmodjo(api, tField);
    return BlackOilUnits.fromCentipoise(muField, inputUnits);
  }

  /**
   * Kartoatmodjo-Schmidt correlation for dead oil viscosity using SI units.
   *
   * @param api API gravity
   * @param T Temperature (°C)
   * @return Dead oil viscosity (Pa·s)
   */
  public static double deadOilViscosityKartoatmodjoSI(double api, double T) {
    return deadOilViscosityKartoatmodjo(api, T, BlackOilUnits.SI);
  }

  // ==================== LIVE OIL VISCOSITY (SATURATED) ====================

  /**
   * Beggs-Robinson correlation for saturated oil viscosity.
   *
   * @param muOD Dead oil viscosity (cP)
   * @param Rs Solution GOR (scf/STB)
   * @return Saturated oil viscosity (cP)
   */
  public static double saturatedOilViscosityBeggsRobinson(double muOD, double Rs) {
    double a = 10.715 * Math.pow(Rs + 100, -0.515);
    double b = 5.44 * Math.pow(Rs + 150, -0.338);
    double muO = a * Math.pow(muOD, b);
    return muO;
  }

  /**
   * Kartoatmodjo-Schmidt correlation for saturated oil viscosity.
   *
   * @param muOD Dead oil viscosity (cP)
   * @param Rs Solution GOR (scf/STB)
   * @return Saturated oil viscosity (cP)
   */
  public static double saturatedOilViscosityKartoatmodjo(double muOD, double Rs) {
    double a = -0.06821 + 0.9824 / (Rs + 100) + 40.34e-5 * Rs;
    double b = 0.2001 + 0.8428 / (Rs + 100) - 0.000845 * Rs;
    double muO = Math.pow(10, a) * Math.pow(muOD, b);
    return muO;
  }

  // ==================== SATURATED OIL VISCOSITY - UNIT-AWARE METHODS ====================

  /**
   * Beggs-Robinson correlation for saturated oil viscosity with unit support.
   *
   * @param muOD Dead oil viscosity (in inputUnits: cP for FIELD, Pa·s for SI/NEQSIM)
   * @param Rs Solution GOR (in inputUnits)
   * @param inputUnits Unit system for inputs and outputs
   * @return Saturated oil viscosity (in inputUnits)
   */
  public static double saturatedOilViscosityBeggsRobinson(double muOD, double Rs,
      BlackOilUnits inputUnits) {
    double muField = BlackOilUnits.toCentipoise(muOD, inputUnits);
    double rsField = BlackOilUnits.toScfPerStb(Rs, inputUnits);
    double result = saturatedOilViscosityBeggsRobinson(muField, rsField);
    return BlackOilUnits.fromCentipoise(result, inputUnits);
  }

  /**
   * Beggs-Robinson correlation for saturated oil viscosity using SI units.
   *
   * @param muOD Dead oil viscosity (Pa·s)
   * @param Rs Solution GOR (Sm³/Sm³)
   * @return Saturated oil viscosity (Pa·s)
   */
  public static double saturatedOilViscosityBeggsRobinsonSI(double muOD, double Rs) {
    return saturatedOilViscosityBeggsRobinson(muOD, Rs, BlackOilUnits.SI);
  }

  /**
   * Kartoatmodjo-Schmidt correlation for saturated oil viscosity with unit support.
   *
   * @param muOD Dead oil viscosity (in inputUnits: cP for FIELD, Pa·s for SI/NEQSIM)
   * @param Rs Solution GOR (in inputUnits)
   * @param inputUnits Unit system for inputs and outputs
   * @return Saturated oil viscosity (in inputUnits)
   */
  public static double saturatedOilViscosityKartoatmodjo(double muOD, double Rs,
      BlackOilUnits inputUnits) {
    double muField = BlackOilUnits.toCentipoise(muOD, inputUnits);
    double rsField = BlackOilUnits.toScfPerStb(Rs, inputUnits);
    double result = saturatedOilViscosityKartoatmodjo(muField, rsField);
    return BlackOilUnits.fromCentipoise(result, inputUnits);
  }

  /**
   * Kartoatmodjo-Schmidt correlation for saturated oil viscosity using SI units.
   *
   * @param muOD Dead oil viscosity (Pa·s)
   * @param Rs Solution GOR (Sm³/Sm³)
   * @return Saturated oil viscosity (Pa·s)
   */
  public static double saturatedOilViscosityKartoatmodjoSI(double muOD, double Rs) {
    return saturatedOilViscosityKartoatmodjo(muOD, Rs, BlackOilUnits.SI);
  }

  // ==================== UNDERSATURATED OIL VISCOSITY ====================

  /**
   * Vasquez-Beggs correlation for undersaturated oil viscosity.
   *
   * @param muOb Saturated oil viscosity at Pb (cP)
   * @param p Pressure (psia)
   * @param pb Bubble point pressure (psia)
   * @return Undersaturated oil viscosity (cP)
   */
  public static double undersaturatedOilViscosityVasquezBeggs(double muOb, double p, double pb) {
    double m = 2.6 * Math.pow(p, 1.187) * Math.exp(-11.513 - 8.98e-5 * p);
    double muO = muOb * Math.pow(p / pb, m);
    return muO;
  }

  /**
   * Bergman-Sutton correlation for undersaturated oil viscosity.
   *
   * @param muOb Saturated oil viscosity at Pb (cP)
   * @param p Pressure (psia)
   * @param pb Bubble point pressure (psia)
   * @return Undersaturated oil viscosity (cP)
   */
  public static double undersaturatedOilViscosityBergmanSutton(double muOb, double p, double pb) {
    double a =
        6.5698e-7 * Math.log(muOb) * Math.log(muOb) - 1.48211e-5 * Math.log(muOb) + 2.27877e-4;
    double b = 2.24623e-2 * Math.log(muOb) + 0.873204;
    double muO = muOb * Math.exp(a * Math.pow(p - pb, b));
    return muO;
  }

  // ==================== UNDERSATURATED OIL VISCOSITY - UNIT-AWARE METHODS ====================

  /**
   * Vasquez-Beggs correlation for undersaturated oil viscosity with unit support.
   *
   * @param muOb Saturated oil viscosity at Pb (in inputUnits)
   * @param p Pressure (in inputUnits)
   * @param pb Bubble point pressure (in inputUnits)
   * @param inputUnits Unit system for inputs and outputs
   * @return Undersaturated oil viscosity (in inputUnits)
   */
  public static double undersaturatedOilViscosityVasquezBeggs(double muOb, double p, double pb,
      BlackOilUnits inputUnits) {
    double muField = BlackOilUnits.toCentipoise(muOb, inputUnits);
    double pField = BlackOilUnits.toPsia(p, inputUnits);
    double pbField = BlackOilUnits.toPsia(pb, inputUnits);
    double result = undersaturatedOilViscosityVasquezBeggs(muField, pField, pbField);
    return BlackOilUnits.fromCentipoise(result, inputUnits);
  }

  /**
   * Vasquez-Beggs correlation for undersaturated oil viscosity using SI units.
   *
   * @param muOb Saturated oil viscosity at Pb (Pa·s)
   * @param p Pressure (bara)
   * @param pb Bubble point pressure (bara)
   * @return Undersaturated oil viscosity (Pa·s)
   */
  public static double undersaturatedOilViscosityVasquesBeggsS(double muOb, double p, double pb) {
    return undersaturatedOilViscosityVasquezBeggs(muOb, p, pb, BlackOilUnits.SI);
  }

  /**
   * Bergman-Sutton correlation for undersaturated oil viscosity with unit support.
   *
   * @param muOb Saturated oil viscosity at Pb (in inputUnits)
   * @param p Pressure (in inputUnits)
   * @param pb Bubble point pressure (in inputUnits)
   * @param inputUnits Unit system for inputs and outputs
   * @return Undersaturated oil viscosity (in inputUnits)
   */
  public static double undersaturatedOilViscosityBergmanSutton(double muOb, double p, double pb,
      BlackOilUnits inputUnits) {
    double muField = BlackOilUnits.toCentipoise(muOb, inputUnits);
    double pField = BlackOilUnits.toPsia(p, inputUnits);
    double pbField = BlackOilUnits.toPsia(pb, inputUnits);
    double result = undersaturatedOilViscosityBergmanSutton(muField, pField, pbField);
    return BlackOilUnits.fromCentipoise(result, inputUnits);
  }

  /**
   * Bergman-Sutton correlation for undersaturated oil viscosity using SI units.
   *
   * @param muOb Saturated oil viscosity at Pb (Pa·s)
   * @param p Pressure (bara)
   * @param pb Bubble point pressure (bara)
   * @return Undersaturated oil viscosity (Pa·s)
   */
  public static double undersaturatedOilViscosityBergmanSuttonSI(double muOb, double p, double pb) {
    return undersaturatedOilViscosityBergmanSutton(muOb, p, pb, BlackOilUnits.SI);
  }

  // ==================== GAS FORMATION VOLUME FACTOR ====================

  /**
   * Calculate gas formation volume factor.
   *
   * @param p Pressure (psia)
   * @param T Temperature (°R)
   * @param z Z-factor
   * @return Bg in rcf/scf
   */
  public static double gasFVF(double p, double T, double z) {
    // Bg = 0.02827 * z * T / p (rcf/scf)
    return 0.02827 * z * T / p;
  }

  /**
   * Calculate gas formation volume factor in RB/Mscf.
   *
   * @param p Pressure (psia)
   * @param T Temperature (°R)
   * @param z Z-factor
   * @return Bg in RB/Mscf
   */
  public static double gasFVFrbPerMscf(double p, double T, double z) {
    return 5.04 * z * T / p;
  }

  // ==================== GAS FVF - UNIT-AWARE METHODS ====================

  /**
   * Calculate gas formation volume factor with unit support.
   *
   * @param p Pressure (in inputUnits)
   * @param T Temperature (in inputUnits)
   * @param z Z-factor (dimensionless)
   * @param inputUnits Unit system for inputs
   * @return Bg in rcf/scf (field units)
   */
  public static double gasFVF(double p, double T, double z, BlackOilUnits inputUnits) {
    double pField = BlackOilUnits.toPsia(p, inputUnits);
    double tRankine = BlackOilUnits.toRankine(T, inputUnits);
    return gasFVF(pField, tRankine, z);
  }

  /**
   * Calculate gas formation volume factor using SI units.
   *
   * @param p Pressure (bara)
   * @param T Temperature (°C)
   * @param z Z-factor (dimensionless)
   * @return Bg in Sm³/Sm³ (approximately equal to rcf/scf)
   */
  public static double gasFVFSI(double p, double T, double z) {
    return gasFVF(p, T, z, BlackOilUnits.SI);
  }

  // ==================== GAS VISCOSITY ====================

  /**
   * Lee-Gonzalez-Eakin correlation for gas viscosity.
   *
   * @param T Temperature (°R)
   * @param rhoG Gas density (lb/ft3)
   * @param Mg Gas molecular weight
   * @return Gas viscosity (cP)
   */
  public static double gasViscosityLeeGonzalezEakin(double T, double rhoG, double Mg) {
    double k = (9.4 + 0.02 * Mg) * Math.pow(T, 1.5) / (209 + 19 * Mg + T);
    double x = 3.5 + 986.0 / T + 0.01 * Mg;
    double y = 2.4 - 0.2 * x;
    double muG = 1e-4 * k * Math.exp(x * Math.pow(rhoG / 62.4, y));
    return muG;
  }

  // ==================== GAS VISCOSITY - UNIT-AWARE METHODS ====================

  /**
   * Lee-Gonzalez-Eakin correlation for gas viscosity with unit support.
   *
   * @param T Temperature (in inputUnits)
   * @param rhoG Gas density (in inputUnits: lb/ft³ for FIELD, kg/m³ for SI/NEQSIM)
   * @param Mg Gas molecular weight
   * @param inputUnits Unit system for inputs and outputs
   * @return Gas viscosity (in inputUnits: cP for FIELD, Pa·s for SI/NEQSIM)
   */
  public static double gasViscosityLeeGonzalezEakin(double T, double rhoG, double Mg,
      BlackOilUnits inputUnits) {
    double tRankine = BlackOilUnits.toRankine(T, inputUnits);
    double rhoField = BlackOilUnits.toLbPerFt3(rhoG, inputUnits);
    double muField = gasViscosityLeeGonzalezEakin(tRankine, rhoField, Mg);
    return BlackOilUnits.fromCentipoise(muField, inputUnits);
  }

  /**
   * Lee-Gonzalez-Eakin correlation for gas viscosity using SI units.
   *
   * @param T Temperature (°C)
   * @param rhoG Gas density (kg/m³)
   * @param Mg Gas molecular weight
   * @return Gas viscosity (Pa·s)
   */
  public static double gasViscosityLeeGonzalezEakinSI(double T, double rhoG, double Mg) {
    return gasViscosityLeeGonzalezEakin(T, rhoG, Mg, BlackOilUnits.SI);
  }

  // ==================== UTILITY METHODS ====================

  /**
   * Convert API gravity to specific gravity.
   *
   * @param api API gravity
   * @return Specific gravity (water = 1)
   */
  public static double specificGravityFromAPI(double api) {
    return 141.5 / (api + 131.5);
  }

  /**
   * Convert specific gravity to API gravity.
   *
   * @param sg Specific gravity (water = 1)
   * @return API gravity
   */
  public static double apiFromSpecificGravity(double sg) {
    return 141.5 / sg - 131.5;
  }

  /**
   * Convert temperature from Celsius to Fahrenheit.
   *
   * @param tempC Temperature in Celsius
   * @return Temperature in Fahrenheit
   */
  public static double celsiusToFahrenheit(double tempC) {
    return tempC * 9.0 / 5.0 + 32.0;
  }

  /**
   * Convert temperature from Fahrenheit to Celsius.
   *
   * @param tempF Temperature in Fahrenheit
   * @return Temperature in Celsius
   */
  public static double fahrenheitToCelsius(double tempF) {
    return (tempF - 32.0) * 5.0 / 9.0;
  }

  /**
   * Convert pressure from bara to psia.
   *
   * @param bara Pressure in bara
   * @return Pressure in psia
   */
  public static double baraToPsia(double bara) {
    return bara * 14.5038;
  }

  /**
   * Convert pressure from psia to bara.
   *
   * @param psia Pressure in psia
   * @return Pressure in bara
   */
  public static double psiaToBara(double psia) {
    return psia / 14.5038;
  }

  /**
   * Convert GOR from Sm3/Sm3 to scf/STB.
   *
   * @param gorSm3 GOR in Sm3/Sm3
   * @return GOR in scf/STB
   */
  public static double gorSm3ToScfPerStb(double gorSm3) {
    return gorSm3 * 5.6146;
  }

  /**
   * Convert GOR from scf/STB to Sm3/Sm3.
   *
   * @param gorScf GOR in scf/STB
   * @return GOR in Sm3/Sm3
   */
  public static double gorScfToSm3(double gorScf) {
    return gorScf / 5.6146;
  }

  /**
   * Convert Bo from bbl/STB to m3/Sm3.
   *
   * @param boBbl Bo in bbl/STB
   * @return Bo in m3/Sm3 (dimensionless ratio is same)
   */
  public static double boToBblPerStb(double boBbl) {
    // Bo is a ratio, so it's the same in both unit systems
    return boBbl;
  }
}
