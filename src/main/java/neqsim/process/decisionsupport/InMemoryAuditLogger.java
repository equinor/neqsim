package neqsim.process.decisionsupport;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link AuditLogger} for testing and lightweight deployments.
 *
 * <p>
 * Stores records in a thread-safe map. Records are lost when the JVM terminates. Suitable for unit
 * tests, demos, and development environments.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class InMemoryAuditLogger implements AuditLogger {
  private final Map<String, AuditRecord> records = new ConcurrentHashMap<>();

  /**
   * Creates a new in-memory audit logger.
   */
  public InMemoryAuditLogger() {}

  /** {@inheritDoc} */
  @Override
  public void log(AuditRecord record) {
    if (record == null) {
      return;
    }
    records.put(record.getAuditId(), record);
  }

  /** {@inheritDoc} */
  @Override
  public AuditRecord getRecord(String auditId) {
    return records.get(auditId);
  }

  /** {@inheritDoc} */
  @Override
  public List<AuditRecord> getRecords(Instant from, Instant to) {
    List<AuditRecord> result = new ArrayList<>();
    for (AuditRecord record : records.values()) {
      Instant ts = record.getTimestamp();
      if (ts != null && !ts.isBefore(from) && !ts.isAfter(to)) {
        result.add(record);
      }
    }
    Collections.sort(result, new java.util.Comparator<AuditRecord>() {
      @Override
      public int compare(AuditRecord a, AuditRecord b) {
        return a.getTimestamp().compareTo(b.getTimestamp());
      }
    });
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public int getRecordCount() {
    return records.size();
  }

  /**
   * Clears all stored records.
   */
  public void clear() {
    records.clear();
  }
}
