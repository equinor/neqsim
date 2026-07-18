package neqsim.process.engineering.pid;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Deterministic ISA-style tag allocator scoped to one P&amp;ID design model. */
public final class PidTagAllocator implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final PidDesignBasis basis;
  private final Map<String, Integer> counters = new LinkedHashMap<String, Integer>();
  private final Map<String, String> allocatedByStableKey = new LinkedHashMap<String, String>();
  private final Set<String> reservedTags = new LinkedHashSet<String>();

  public PidTagAllocator(PidDesignBasis basis) {
    if (basis == null) {
      throw new IllegalArgumentException("basis must not be null");
    }
    this.basis = basis;
  }

  public String allocate(String functionCode, String stableKey) {
    String function = normalize(functionCode, "functionCode");
    String key = normalize(stableKey, "stableKey");
    String existing = allocatedByStableKey.get(key);
    if (existing != null) {
      return existing;
    }
    int sequence = counters.containsKey(function) ? counters.get(function).intValue()
        : basis.getFirstSequenceNumber();
    String candidate;
    do {
      candidate = basis.getAreaCode() + "-" + function + "-" + pad(sequence, basis.getSequenceWidth());
      sequence++;
    } while (reservedTags.contains(candidate));
    counters.put(function, Integer.valueOf(sequence));
    reservedTags.add(candidate);
    allocatedByStableKey.put(key, candidate);
    return candidate;
  }

  public PidTagAllocator reserve(String tag) {
    String normalized = normalize(tag, "tag");
    if (!reservedTags.add(normalized)) {
      throw new IllegalArgumentException("P&ID tag is already reserved: " + normalized);
    }
    return this;
  }

  private static String normalize(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_-]", "-");
  }

  private static String pad(int value, int width) {
    return String.format(Locale.ROOT, "%0" + width + "d", Integer.valueOf(value));
  }
}
