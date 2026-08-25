package neqsim.thermo.component;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves a user-supplied component name to the name used in the NeqSim component database.
 *
 * <p>
 * The same molecule is written differently by different tools. The component database uses an in-house shorthand
 * ({@code 224-TM-C5}), laboratory reports use systematic names ({@code 2,2,4-trimethylpentane}) or inverted CAS index
 * names ({@code Cyclopentane, 1-ethyl-2-methyl}), and reservoir engineering uses yet another shorthand ({@code nC7}).
 * This class maps all of them onto the database name.
 * </p>
 *
 * <p>
 * Resolution proceeds in four steps, and the first hit wins:
 * </p>
 * <ol>
 * <li>case-insensitive match against a database name;</li>
 * <li>match against the synonym table;</li>
 * <li>the same two lookups after converting an inverted CAS index name to prefix form;</li>
 * <li>otherwise the input is returned unchanged.</li>
 * </ol>
 *
 * <p>
 * Stereochemistry is never discarded. Where the database holds both partners of a cis/trans pair, the ambiguous parent
 * name is deliberately absent from the synonym table so that an under-specified name is passed through rather than
 * silently resolved to one of the two.
 * </p>
 *
 * <p>
 * The tables target the standard database {@code data/COMP.csv}. When the extended database is active, a name known
 * only to it is passed through unchanged.
 * </p>
 *
 * @author NeqSim
 * @version $Id: $Id
 */
public final class ComponentNameResolver {
  /** Inverted CAS index name, e.g. {@code cyclopentane,1-ethyl-2-methyl}. */
  private static final Pattern CAS_INVERTED = Pattern
      .compile("^([a-z0-9,\\-\\[\\]]*?(?:ane|ene|yne|ol|thalene|zene|adiene)),(.+)$");

  /** Normalised database name to the exact name stored in the database. */
  private static final Map<String, String> CANONICAL_NAMES;

  /** Normalised alternative name to the database name it denotes. */
  private static final Map<String, String> SYNONYMS;

  static {
    CANONICAL_NAMES = Collections.unmodifiableMap(buildCanonicalNames());
    SYNONYMS = Collections.unmodifiableMap(buildSynonyms());
  }

  /** Utility class; not instantiable. */
  private ComponentNameResolver() {
  }

  /**
   * Resolve a component name to the name used in the component database.
   *
   * @param name component name, alias, synonym or inverted CAS index name; may be null
   * @return the database name, or the input unchanged when nothing matches
   */
  public static String resolve(String name) {
    if (name == null) {
      return null;
    }
    String key = normalize(name);
    if (key.isEmpty()) {
      return name;
    }
    String hit = lookup(key);
    if (hit != null) {
      return hit;
    }
    // 'n-' marks a straight chain and carries no information the database name needs.
    // It is only stripped after a direct lookup fails, so database names that legitimately
    // start with it ('n-butane', 'n-heptane') are matched first and untouched.
    if (key.length() > 2 && key.charAt(0) == 'n' && key.charAt(1) == '-') {
      hit = lookup(key.substring(2));
      if (hit != null) {
        return hit;
      }
    }
    String deinverted = deinvertCasIndexName(key);
    if (!deinverted.equals(key)) {
      hit = lookup(deinverted);
      if (hit != null) {
        return hit;
      }
    }
    return name;
  }

  /**
   * Look a normalised key up in the canonical names first, then the synonyms.
   *
   * @param key normalised lookup key
   * @return the database name, or null when the key is unknown
   */
  private static String lookup(String key) {
    String hit = CANONICAL_NAMES.get(key);
    if (hit != null) {
      return hit;
    }
    return SYNONYMS.get(key);
  }

  /**
   * Report whether a name resolves to a component of the standard database.
   *
   * @param name component name, alias or synonym
   * @return true when the name is known to the resolver
   */
  public static boolean isKnownName(String name) {
    if (name == null) {
      return false;
    }
    String key = normalize(name);
    return lookup(key) != null
        || (key.length() > 2 && key.charAt(0) == 'n' && key.charAt(1) == '-' && lookup(key.substring(2)) != null)
        || lookup(deinvertCasIndexName(key)) != null;
  }

  /**
   * Normalise a name to its lookup key.
   *
   * <p>
   * Lower-cases, removes whitespace, unifies {@code _} to {@code -}, and treats a {@code .} between two digits as the
   * locant separator {@code ,} so that {@code 1.2.3-TM-Benzene} and {@code 1,2,3-TM-Benzene} agree.
   * </p>
   *
   * @param name component name; may be null
   * @return normalised key, empty string when the input is null or blank
   */
  public static String normalize(String name) {
    if (name == null) {
      return "";
    }
    String lower = name.trim().toLowerCase(Locale.ROOT);
    StringBuilder sb = new StringBuilder(lower.length());
    for (int i = 0; i < lower.length(); i++) {
      char c = lower.charAt(i);
      if (Character.isWhitespace(c)) {
        continue;
      }
      if (c == '_') {
        sb.append('-');
      } else if (c == '.' && i > 0 && i + 1 < lower.length() && Character.isDigit(lower.charAt(i - 1))
          && Character.isDigit(lower.charAt(i + 1))) {
        sb.append(',');
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  /**
   * Convert an inverted CAS index name to prefix form.
   *
   * <p>
   * {@code cyclopentane,1-ethyl-2-methyl} becomes {@code 1-ethyl-2-methylcyclopentane}. Input that is not in inverted
   * form is returned unchanged.
   * </p>
   *
   * @param normalizedName a name already passed through {@link #normalize(String)}
   * @return the prefix-form name, or the input unchanged
   */
  public static String deinvertCasIndexName(String normalizedName) {
    if (normalizedName == null || normalizedName.indexOf(',') < 0) {
      return normalizedName;
    }
    Matcher m = CAS_INVERTED.matcher(normalizedName);
    if (!m.matches()) {
      return normalizedName;
    }
    String parent = m.group(1);
    String substituents = trimSeparators(m.group(2));
    if (substituents.isEmpty()) {
      return parent;
    }
    // A substituent prefix attaches directly ('1,2,4-trimethyl' + 'cyclohexane'), but a parent
    // that starts with its own locant keeps the hyphen ('4,4-dimethyl' + '2-pentene').
    if (Character.isDigit(parent.charAt(0))) {
      return substituents + "-" + parent;
    }
    return substituents + parent;
  }

  /**
   * Strip leading and trailing hyphens and commas.
   *
   * @param text text to trim
   * @return trimmed text
   */
  private static String trimSeparators(String text) {
    int start = 0;
    int end = text.length();
    while (start < end && (text.charAt(start) == '-' || text.charAt(start) == ',')) {
      start++;
    }
    while (end > start && (text.charAt(end - 1) == '-' || text.charAt(end - 1) == ',')) {
      end--;
    }
    return text.substring(start, end);
  }

  /**
   * Get the synonym table.
   *
   * @return unmodifiable map of normalised synonym to database name
   */
  public static Map<String, String> getSynonyms() {
    return SYNONYMS;
  }

  /**
   * Get the case-insensitive index of database names.
   *
   * @return unmodifiable map of normalised database name to exact database name
   */
  public static Map<String, String> getCanonicalNames() {
    return CANONICAL_NAMES;
  }

  /**
   * Add one entry, failing loudly if the key is already taken.
   *
   * @param map map under construction
   * @param key normalised lookup key
   * @param value database name the key denotes
   */
  private static void put(Map<String, String> map, String key, String value) {
    String previous = map.put(key, value);
    if (previous != null && !previous.equals(value)) {
      throw new IllegalStateException("duplicate component name key '" + key + "': " + previous + " and " + value);
    }
  }

  /**
   * Build the case-insensitive index of standard database names.
   *
   * @return the populated map
   */
  private static Map<String, String> buildCanonicalNames() {
    Map<String, String> m = new LinkedHashMap<String, String>();
    put(m, "-oocpzcoo-", "-OOCPZCOO-");
    put(m, "1,1,3-tm-cy-c6", "1.1.3-TM-cy-C6");
    put(m, "1,2,3-tm-benzene", "1.2.3-TM-Benzene");
    put(m, "1,2,3-tmcyc6", "1.2.3-TMcyC6");
    put(m, "1,2,4-tmcyc6", "1.2.4-TMcyC6");
    put(m, "1,2-dm-cyc5", "1.2-DM-cyC5");
    put(m, "1,3-ddm-cyc5", "1.3-dDM-cyC5");
    put(m, "1-propanol", "1-propanol");
    put(m, "1.cis-2.trans-4-tmcyc5", "1.cis-2.trans-4-TMcyC5");
    put(m, "11-dm-cy-c5", "11-DM-cy-C5");
    put(m, "113-tm-cy-c5", "113-TM-cy-C5");
    put(m, "2,2-dm-c7", "2.2-DM-C7");
    put(m, "2,3-dm-c6", "2.3-DM-C6");
    put(m, "2,6-dm-c7", "2.6-DM-C7");
    put(m, "2-e-p-xylene", "2-E-p-xylene");
    put(m, "2-m-c5", "2-m-C5");
    put(m, "2-m-c6", "2-M-C6");
    put(m, "2-m-c7", "2-M-C7");
    put(m, "2-m-c8", "2-M-C8");
    put(m, "22-dim-c3", "22-dim-C3");
    put(m, "22-dim-c4", "22-dim-C4");
    put(m, "22-dm-c5", "22-DM-C5");
    put(m, "22-dm-c6", "22-DM-C6");
    put(m, "223-tm-c4", "223-TM-C4");
    put(m, "224-tm-c5", "224-TM-C5");
    put(m, "23-dim-c4", "23-dim-C4");
    put(m, "23-dm-c5", "23-DM-C5");
    put(m, "234-tm-c5", "234-TM-C5");
    put(m, "24-dm-c5", "24-DM-C5");
    put(m, "24-dm-c6", "24-DM-C6");
    put(m, "25-dm-c6", "25-DM-C6");
    put(m, "3-e-c6", "3-E-C6");
    put(m, "3-m-4,4-de-heptane", "3-M-4.4-DE-heptane");
    put(m, "3-m-c5", "3-m-C5");
    put(m, "3-m-c6", "3-M-C6");
    put(m, "3-m-c7", "3-M-C7");
    put(m, "3-m-c8", "3-M-C8");
    put(m, "33-dm-c5", "33-DM-C5");
    put(m, "33-dm-c6", "33-DM-C6");
    put(m, "4-m-c8", "4-M-C8");
    put(m, "ac-", "Ac-");
    put(m, "aceticacid", "acetic acid");
    put(m, "acetone", "acetone");
    put(m, "ammonia", "ammonia");
    put(m, "argon", "argon");
    put(m, "asphaltene", "asphaltene");
    put(m, "ba++", "Ba++");
    put(m, "benzene", "benzene");
    put(m, "br-", "Br-");
    put(m, "c-c4", "c-C4");
    put(m, "c-c5", "c-C5");
    put(m, "c-c7", "c-C7");
    put(m, "c-c8", "c-C8");
    put(m, "c-hexane", "c-hexane");
    put(m, "c-propane", "c-propane");
    put(m, "c2h4", "C2H4");
    put(m, "c2h4-", "C2H4-");
    put(m, "c2h4o", "C2H4O");
    put(m, "c2h4o-", "C2H4O-");
    put(m, "ca++", "Ca++");
    put(m, "cacl2", "CaCl2");
    put(m, "caco3", "CaCO3");
    put(m, "ch2o", "CH2O");
    put(m, "ch2o-", "CH2O-");
    put(m, "chlorine", "chlorine");
    put(m, "cis-12-dm-cy-c6", "cis-12-DM-cy-C6");
    put(m, "cis-13-dm-cy-c5", "cis-13-DM-cy-C5");
    put(m, "cis-13-dm-cy-c6", "cis-13-DM-cy-C6");
    put(m, "cis-14-dm-cy-c6", "cis-14-DM-cy-C6");
    put(m, "cis-butene", "cis-butene");
    put(m, "cl-", "Cl-");
    put(m, "clo4-", "ClO4-");
    put(m, "co", "CO");
    put(m, "co2", "CO2");
    put(m, "co3--", "CO3--");
    put(m, "cos", "COS");
    put(m, "cs+", "Cs+");
    put(m, "cs2", "CS2");
    put(m, "cs2-", "CS2-");
    put(m, "cy-c9", "cy-C9");
    put(m, "dea", "DEA");
    put(m, "dea+", "DEA+");
    put(m, "deacoo-", "DEACOO-");
    put(m, "default", "default");
    put(m, "deg", "DEG");
    put(m, "e-cy-c5", "E-cy-C5");
    put(m, "ethane", "ethane");
    put(m, "ethanol", "ethanol");
    put(m, "ethanolpvtsim", "ethanolPVTsim");
    put(m, "ethylbenzene", "ethylbenzene");
    put(m, "ethylcyclohexane", "ethylcyclohexane");
    put(m, "ethylene", "ethylene");
    put(m, "f-", "F-");
    put(m, "fe++", "Fe++");
    put(m, "formicacid", "formic acid");
    put(m, "glycerol", "glycerol");
    put(m, "h+", "H+");
    put(m, "h+pzcoo-", "H+PZCOO-");
    put(m, "h2o2", "H2O2");
    put(m, "h2s", "H2S");
    put(m, "h2so4", "H2SO4");
    put(m, "h3o+", "H3O+");
    put(m, "hcn", "HCN");
    put(m, "hcn-", "HCN-");
    put(m, "hco3-", "HCO3-");
    put(m, "helium", "helium");
    put(m, "hg++", "Hg++");
    put(m, "hno2", "HNO2");
    put(m, "hno2-", "HNO2-");
    put(m, "hno3", "HNO3");
    put(m, "hs-", "HS-");
    put(m, "hydrochloricacid", "hydrochloric acid");
    put(m, "hydrogen", "hydrogen");
    put(m, "i-", "I-");
    put(m, "i-butane", "i-butane");
    put(m, "i-p-cy-c5", "i-p-cy-C5");
    put(m, "i-pentane", "i-pentane");
    put(m, "i-propanol", "i-propanol");
    put(m, "ice", "ice");
    put(m, "iso-butene", "iso-butene");
    put(m, "k+", "K+");
    put(m, "li+", "Li+");
    put(m, "m-cy-c5", "M-cy-C5");
    put(m, "m-cy-c6", "M-cy-C6");
    put(m, "m-xylene", "m-Xylene");
    put(m, "mdea", "MDEA");
    put(m, "mdea+", "MDEA+");
    put(m, "mea", "MEA");
    put(m, "mea+", "MEA+");
    put(m, "meacoo-", "MEACOO-");
    put(m, "meg", "MEG");
    put(m, "megpvtsim18", "MEGPVTsim18");
    put(m, "megpvtsim19", "MEGPVTsim19");
    put(m, "mercury", "mercury");
    put(m, "methane", "methane");
    put(m, "methanol", "methanol");
    put(m, "methanolpvtsim", "methanolPVTsim");
    put(m, "mg++", "Mg++");
    put(m, "n-bcychexane", "n-Bcychexane");
    put(m, "n-butane", "n-butane");
    put(m, "n-heptane", "n-heptane");
    put(m, "n-hexane", "n-hexane");
    put(m, "n-nonane", "n-nonane");
    put(m, "n-octane", "n-octane");
    put(m, "n-pentane", "n-pentane");
    put(m, "n2h4", "N2H4");
    put(m, "n2h4-", "N2H4-");
    put(m, "n2o", "N2O");
    put(m, "n2o-", "N2O-");
    put(m, "n2o3", "N2O3");
    put(m, "n2o3-", "N2O3-");
    put(m, "n2o4", "N2O4");
    put(m, "n2o4-", "N2O4-");
    put(m, "n2o5", "N2O5");
    put(m, "n2o5-", "N2O5-");
    put(m, "na+", "Na+");
    put(m, "nacl", "NaCl");
    put(m, "nahso4", "NaHSO4");
    put(m, "nano2", "NaNO2");
    put(m, "naphthalene", "naphthalene");
    put(m, "nbutanepvtsim", "nbutanePVTsim");
    put(m, "nc10", "nC10");
    put(m, "nc10-benzene", "nC10-Benzene");
    put(m, "nc10-cy-c5", "nC10-cy-C5");
    put(m, "nc11", "nC11");
    put(m, "nc12", "nC12");
    put(m, "nc12-cy-c5", "nC12-cy-C5");
    put(m, "nc13", "nC13");
    put(m, "nc14", "nC14");
    put(m, "nc14-cy-c5", "nC14-cy-C5");
    put(m, "nc15", "nC15");
    put(m, "nc16", "nC16");
    put(m, "nc17", "nC17");
    put(m, "nc17-cy-c5", "nC17-cy-C5");
    put(m, "nc18", "nC18");
    put(m, "nc19", "nC19");
    put(m, "nc20", "nC20");
    put(m, "nc21", "nC21");
    put(m, "nc22", "nC22");
    put(m, "nc23", "nC23");
    put(m, "nc24", "nC24");
    put(m, "nc25", "nC25");
    put(m, "nc26", "nC26");
    put(m, "nc27", "nC27");
    put(m, "nc28", "nC28");
    put(m, "nc29", "nC29");
    put(m, "nc30", "nC30");
    put(m, "nc34", "nC34");
    put(m, "nc39", "nC39");
    put(m, "nc5-benzene", "nC5-Benzene");
    put(m, "nc6-benzene", "nC6-Benzene");
    put(m, "nc7-benzene", "nC7-Benzene");
    put(m, "nc8-benzene", "nC8-Benzene");
    put(m, "nc9-benzene", "nC9-Benzene");
    put(m, "neon", "neon");
    put(m, "nh2oh", "NH2OH");
    put(m, "nh2oh-", "NH2OH-");
    put(m, "nh2so3-", "NH2SO3-");
    put(m, "nh2so3h", "NH2SO3H");
    put(m, "nh4+", "NH4+");
    put(m, "nh4hso4", "NH4HSO4");
    put(m, "nh4hso4-", "NH4HSO4-");
    put(m, "nh4no3", "NH4NO3");
    put(m, "nh4no3-", "NH4NO3-");
    put(m, "nhã¢â€šâ€šoh", "NHÃ¢â€šâ€šOH");
    put(m, "nhã¢â€šâ€šoh-", "NHÃ¢â€šâ€šOH-");
    put(m, "nitricacid", "nitric acid");
    put(m, "nitrogen", "nitrogen");
    put(m, "no", "NO");
    put(m, "no-", "NO-");
    put(m, "no2", "NO2");
    put(m, "no2-", "NO2-");
    put(m, "no3-", "NO3-");
    put(m, "nã¢â€šâ€šhã¢â€šâ€ž", "NÃ¢â€šâ€šHÃ¢â€šâ€ž");
    put(m, "nã¢â€šâ€šhã¢â€šâ€ž-", "NÃ¢â€šâ€šHÃ¢â€šâ€ž-");
    put(m, "o-e-toluene", "o-E-toluene");
    put(m, "o-xylene", "o-Xylene");
    put(m, "oh-", "OH-");
    put(m, "ortho-hydrogen", "ortho-hydrogen");
    put(m, "oxygen", "oxygen");
    put(m, "p-xylene", "p-Xylene");
    put(m, "para-hydrogen", "para-hydrogen");
    put(m, "pb++", "Pb++");
    put(m, "pent-cc6", "Pent-CC6");
    put(m, "pg", "PG");
    put(m, "piperazine", "Piperazine");
    put(m, "piperazine+", "Piperazine+");
    put(m, "piperazine++", "Piperazine++");
    put(m, "propane", "propane");
    put(m, "propanepvtsim", "propanePVTsim");
    put(m, "propene", "propene");
    put(m, "propylbenzene", "propylbenzene");
    put(m, "pzcoo-", "PZCOO-");
    put(m, "r12", "R12");
    put(m, "r134a", "R134a");
    put(m, "rb+", "Rb+");
    put(m, "s", "S");
    put(m, "s-", "S-");
    put(m, "s--", "S--");
    put(m, "s8", "S8");
    put(m, "seawater", "seawater");
    put(m, "sf6", "SF6");
    put(m, "so2", "SO2");
    put(m, "so3", "SO3");
    put(m, "so3-", "SO3-");
    put(m, "so4--", "SO4--");
    put(m, "sr++", "Sr++");
    put(m, "sulfur(s8)", "sulfur(S8)");
    put(m, "sulfuricacid", "sulfuric acid");
    put(m, "teg", "TEG");
    put(m, "toluene", "toluene");
    put(m, "trans-12-dm-cy-c5", "trans-12-DM-cy-C5");
    put(m, "trans-12-dm-cy-c6", "trans-12-DM-cy-C6");
    put(m, "trans-13-dm-cy-c5", "trans-13-DM-cy-C5");
    put(m, "trans-14-dm-cy-c6", "trans-14-DM-cy-C6");
    put(m, "trans-butene", "trans-butene");
    put(m, "water", "water");
    return new HashMap<String, String>(m);
  }

  /**
   * Build the synonym table mapping alternative names to database names.
   *
   * @return the populated map
   */
  private static Map<String, String> buildSynonyms() {
    Map<String, String> m = new LinkedHashMap<String, String>();
    put(m, "1,1,3-trimethylcyclohexane", "1.1.3-TM-cy-C6");
    put(m, "1,1,3-trimethylcyclopentane", "113-TM-cy-C5");
    put(m, "1,1-dimethylcyclopentane", "11-DM-cy-C5");
    put(m, "1,2,3-trimethylbenzene", "1.2.3-TM-Benzene");
    put(m, "1,2,3-trimethylcyclohexane", "1.2.3-TMcyC6");
    put(m, "1,2,4-trimethylcyclohexane", "1.2.4-TMcyC6");
    put(m, "1,2-dimethylbenzene", "o-Xylene");
    put(m, "1,3-dimethylbenzene", "m-Xylene");
    put(m, "1,4-dimethylbenzene", "p-Xylene");
    put(m, "1-ethyl-2-methylbenzene", "o-E-toluene");
    put(m, "1-methyl-2-ethylbenzene", "o-E-toluene");
    put(m, "1-phenylpropane", "propylbenzene");
    put(m, "1-propene", "propene");
    put(m, "2,2,3-trimethylbutane", "223-TM-C4");
    put(m, "2,2,4-trimethylpentane", "224-TM-C5");
    put(m, "2,2-dimethylbutane", "22-dim-C4");
    put(m, "2,2-dimethylheptane", "2.2-DM-C7");
    put(m, "2,2-dimethylhexane", "22-DM-C6");
    put(m, "2,2-dimethylpentane", "22-DM-C5");
    put(m, "2,2-dimethylpropane", "22-dim-C3");
    put(m, "2,3,4-trimethylpentane", "234-TM-C5");
    put(m, "2,3-dimethylbutane", "23-dim-C4");
    put(m, "2,3-dimethylhexane", "2.3-DM-C6");
    put(m, "2,3-dimethylpentane", "23-DM-C5");
    put(m, "2,4-dimethylhexane", "24-DM-C6");
    put(m, "2,4-dimethylpentane", "24-DM-C5");
    put(m, "2,5-dimethylhexane", "25-DM-C6");
    put(m, "2,6-dimethylheptane", "2.6-DM-C7");
    put(m, "2-ethyl-1,4-dimethylbenzene", "2-E-p-xylene");
    put(m, "2-ethyl-p-xylene", "2-E-p-xylene");
    put(m, "2-ethyltoluene", "o-E-toluene");
    put(m, "2-methyl-1-propene", "iso-butene");
    put(m, "2-methylbutane", "i-pentane");
    put(m, "2-methylheptane", "2-M-C7");
    put(m, "2-methylhexane", "2-M-C6");
    put(m, "2-methyloctane", "2-M-C8");
    put(m, "2-methylpentane", "2-m-C5");
    put(m, "2-methylpropane", "i-butane");
    put(m, "2-methylpropene", "iso-butene");
    put(m, "2-propanol", "i-propanol");
    put(m, "3,3-dimethylhexane", "33-DM-C6");
    put(m, "3,3-dimethylpentane", "33-DM-C5");
    put(m, "3-ethylhexane", "3-E-C6");
    put(m, "3-methylheptane", "3-M-C7");
    put(m, "3-methylhexane", "3-M-C6");
    put(m, "3-methyloctane", "3-M-C8");
    put(m, "3-methylpentane", "3-m-C5");
    put(m, "4,4-diethyl-3-methylheptane", "3-M-4.4-DE-heptane");
    put(m, "4-methyloctane", "4-M-C8");
    put(m, "ar", "argon");
    put(m, "butane", "n-butane");
    put(m, "butylcyclohexane", "n-Bcychexane");
    put(m, "c1", "methane");
    put(m, "c2", "ethane");
    put(m, "c3", "propane");
    put(m, "c6", "n-hexane");
    put(m, "carbondioxide", "CO2");
    put(m, "carbonmonoxide", "CO");
    put(m, "carbonylsulfide", "COS");
    put(m, "carbonylsulphide", "COS");
    put(m, "cis-1,3-dimethylcyclohexane", "cis-13-DM-cy-C6");
    put(m, "cis-2-butene", "cis-butene");
    put(m, "cyclobutane", "c-C4");
    put(m, "cycloheptane", "c-C7");
    put(m, "cyclohexane", "c-hexane");
    put(m, "cyclononane", "cy-C9");
    put(m, "cyclooctane", "c-C8");
    put(m, "cyclopentane", "c-C5");
    put(m, "cyclopropane", "c-propane");
    put(m, "decane", "nC10");
    put(m, "decylbenzene", "nC10-Benzene");
    put(m, "decylcyclopentane", "nC10-cy-C5");
    put(m, "diethyleneglycol", "DEG");
    put(m, "dihydrogensulfide", "H2S");
    put(m, "dodecane", "nC12");
    put(m, "dodecylcyclopentane", "nC12-cy-C5");
    put(m, "ethene", "ethylene");
    put(m, "ethylalcohol", "ethanol");
    put(m, "ethylcyclopentane", "E-cy-C5");
    put(m, "ethyleneglycol", "MEG");
    put(m, "h2", "hydrogen");
    put(m, "h2o", "water");
    put(m, "he", "helium");
    put(m, "hemimellitene", "1.2.3-TM-Benzene");
    put(m, "heptadecane", "nC17");
    put(m, "heptane", "n-heptane");
    put(m, "heptylbenzene", "nC7-Benzene");
    put(m, "hexadecane", "nC16");
    put(m, "hexane", "n-hexane");
    put(m, "hexylbenzene", "nC6-Benzene");
    put(m, "hydrogensulfide", "H2S");
    put(m, "hydrogensulphide", "H2S");
    put(m, "ic4", "i-butane");
    put(m, "ic5", "i-pentane");
    put(m, "icosane", "nC20");
    put(m, "isobutane", "i-butane");
    put(m, "isobutene", "iso-butene");
    put(m, "isobutylene", "iso-butene");
    put(m, "isooctane", "224-TM-C5");
    put(m, "isopentane", "i-pentane");
    put(m, "isopropanol", "i-propanol");
    put(m, "isopropylalcohol", "i-propanol");
    put(m, "isopropylcyclopentane", "i-p-cy-C5");
    put(m, "meta-xylene", "m-Xylene");
    put(m, "methylalcohol", "methanol");
    put(m, "methylbenzene", "toluene");
    put(m, "methylcyclohexane", "M-cy-C6");
    put(m, "methylcyclopentane", "M-cy-C5");
    put(m, "monoethyleneglycol", "MEG");
    put(m, "n-butylcyclohexane", "n-Bcychexane");
    put(m, "n-propanol", "1-propanol");
    put(m, "n-propylbenzene", "propylbenzene");
    put(m, "n2", "nitrogen");
    put(m, "naphthalin", "naphthalene");
    put(m, "nc4", "n-butane");
    put(m, "nc5", "n-pentane");
    put(m, "nc7", "n-heptane");
    put(m, "nc8", "n-octane");
    put(m, "nc9", "n-nonane");
    put(m, "neopentane", "22-dim-C3");
    put(m, "nitricoxide", "NO");
    put(m, "nitrogendioxide", "NO2");
    put(m, "nonadecane", "nC19");
    put(m, "nonane", "n-nonane");
    put(m, "nonylbenzene", "nC9-Benzene");
    put(m, "o-ethyltoluene", "o-E-toluene");
    put(m, "o2", "oxygen");
    put(m, "octadecane", "nC18");
    put(m, "octane", "n-octane");
    put(m, "octylbenzene", "nC8-Benzene");
    put(m, "ortho-xylene", "o-Xylene");
    put(m, "para-xylene", "p-Xylene");
    put(m, "pentadecane", "nC15");
    put(m, "pentane", "n-pentane");
    put(m, "pentylbenzene", "nC5-Benzene");
    put(m, "pentylcyclohexane", "Pent-CC6");
    put(m, "propan-1-ol", "1-propanol");
    put(m, "propan-2-ol", "i-propanol");
    put(m, "propylalcohol", "1-propanol");
    put(m, "propylene", "propene");
    put(m, "propyleneglycol", "PG");
    put(m, "sulfurdioxide", "SO2");
    put(m, "tetradecane", "nC14");
    put(m, "tetradecylcyclopentane", "nC14-cy-C5");
    put(m, "trans-2-butene", "trans-butene");
    put(m, "tridecane", "nC13");
    put(m, "triethyleneglycol", "TEG");
    put(m, "undecane", "nC11");
    return new HashMap<String, String>(m);
  }
}
