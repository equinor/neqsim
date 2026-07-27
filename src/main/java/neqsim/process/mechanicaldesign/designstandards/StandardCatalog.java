package neqsim.process.mechanicaldesign.designstandards;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Publisher-sourced lifecycle and edition catalogue for NeqSim design standards. */
public final class StandardCatalog {
  private static final String VERIFIED_ON = "2026-07-21";
  private static final String NORSOK_P = "https://standard.no/en/sectors/petroleum/norsok-standards/p-process";
  private static final String NORSOK_L = "https://standard.no/en/sectors/energi-og-klima/petroleum/norsok-standard-categories/l-piping--layout/";
  private static final String NORSOK_M = "https://standard.no/en/sectors/energi-og-klima/petroleum/norsok-standard-categories/m-material/";
  private static final String NORSOK_S = "https://standard.no/en/sectors/energi-og-klima/petroleum/norsok-standard-categories/s-safety-she/";
  private static final String NORSOK_I = "https://standard.no/en/sectors/petroleum/norsok-standards/i-instrumentation";
  private static final String ASME_BPVC = "https://www.asme.org/codes-standards/bpvc-standards/bpvc-2025";
  private static final String ASME_B313 = "https://www.asme.org/codes-standards/find-codes-standards/b313-2018-process-piping";
  private static final String ASME_B314 = "https://www.asme.org/codes-standards/find-codes-standards/b31-4-pipeline-transportation-systems-liquids-slurries";
  private static final String ASME_B318 = "https://www.asme.org/codes-standards/find-codes-standards/b31-8-gas-transmission-distribution-piping-systems";
  private static final String API_CATALOG = "https://www.api.org/products-and-services/standards/digital-catalog";
  private static final String API_REFINING_2025 = "https://www.api.org/-/media/files/publications/2025-catalog/06_refining_2025.pdf";
  private static final String DNV_F101 = "https://www.dnv.com/energy/standards-guidelines/dnv-st-f101-submarine-pipeline-systems/";
  private static final String IEC_61511_SOURCE = "https://webstore.iec.ch/en/publication/5527";
  private static final Map<StandardType, StandardCatalogEntry> ENTRIES;

  static {
    Map<StandardType, StandardCatalogEntry> entries = new EnumMap<StandardType, StandardCatalogEntry>(
        StandardType.class);
    for (StandardType standardType : StandardType.values()) {
      entries.put(standardType,
          new StandardCatalogEntry(standardType, StandardLifecycleStatus.UNVERIFIED, null, "", ""));
    }

    current(entries, NORSOK_L, StandardType.NORSOK_L_001);
    entries.put(StandardType.NORSOK_P_001, new StandardCatalogEntry(StandardType.NORSOK_P_001,
        StandardLifecycleStatus.SUPERSEDED, StandardType.NORSOK_P_002, NORSOK_P, VERIFIED_ON));
    current(entries, NORSOK_P, StandardType.NORSOK_P_002);
    current(entries, NORSOK_M, StandardType.NORSOK_M_001, StandardType.NORSOK_M_630);
    current(entries, NORSOK_S, StandardType.NORSOK_S_001);
    current(entries, NORSOK_I, StandardType.NORSOK_I_002);

    current(entries, ASME_BPVC, StandardType.ASME_VIII_DIV1, StandardType.ASME_VIII_DIV2);
    current(entries, ASME_B313, StandardType.ASME_B31_3);
    current(entries, ASME_B314, StandardType.ASME_B31_4);
    current(entries, ASME_B318, StandardType.ASME_B31_8);

    current(entries, API_CATALOG, StandardType.API_617, StandardType.API_610, StandardType.API_650,
        StandardType.API_620, StandardType.API_660, StandardType.API_661, StandardType.API_521,
        StandardType.API_520_PART_1, StandardType.API_520_PART_2, StandardType.API_527, StandardType.API_2000,
        StandardType.API_614, StandardType.API_618, StandardType.API_625, StandardType.API_676, StandardType.API_685,
        StandardType.API_5L, StandardType.API_12J);
    current(entries, API_REFINING_2025, StandardType.API_526);

    current(entries, DNV_F101, StandardType.DNV_ST_F101);
    current(entries, "https://www.iso.org/standard/75144.html", StandardType.ISO_23251);
    current(entries, "https://www.iso.org/standard/50826.html", StandardType.ISO_4126_1);
    current(entries, "https://www.iso.org/standard/55440.html", StandardType.ISO_10418);
    current(entries, IEC_61511_SOURCE, StandardType.IEC_61511);
    ENTRIES = Collections.unmodifiableMap(entries);
  }

  private StandardCatalog() {
    // Utility class.
  }

  /**
   * Get the lifecycle and source record for one standard.
   *
   * @param standardType standard to inspect
   * @return immutable catalogue entry
   */
  public static StandardCatalogEntry get(StandardType standardType) {
    if (standardType == null) {
      throw new IllegalArgumentException("standardType cannot be null");
    }
    return ENTRIES.get(standardType);
  }

  /** @return immutable entries in {@link StandardType} declaration order */
  public static List<StandardCatalogEntry> getAll() {
    List<StandardCatalogEntry> result = new ArrayList<StandardCatalogEntry>();
    for (StandardType standardType : StandardType.values()) {
      result.add(get(standardType));
    }
    return Collections.unmodifiableList(result);
  }

  private static void current(Map<StandardType, StandardCatalogEntry> entries, String source,
      StandardType... standardTypes) {
    for (StandardType standardType : standardTypes) {
      entries.put(standardType,
          new StandardCatalogEntry(standardType, StandardLifecycleStatus.CURRENT, null, source, VERIFIED_ON));
    }
  }
}
