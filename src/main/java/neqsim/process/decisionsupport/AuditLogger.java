package neqsim.process.decisionsupport;

import java.time.Instant;
import java.util.List;

/**
 * Interface for persisting and querying decision support audit records.
 *
 * <p>
 * Implementations provide the storage backend (file, database, in-memory) for audit trail records.
 * Every {@link EngineeringRecommendation} produced by the {@link DecisionSupportEngine} is logged
 * via this interface for traceability and reproducibility.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 * @see FileAuditLogger
 * @see InMemoryAuditLogger
 */
public interface AuditLogger {

  /**
   * Persists an audit record.
   *
   * @param record the audit record to log
   */
  void log(AuditRecord record);

  /**
   * Retrieves an audit record by its ID.
   *
   * @param auditId the audit identifier
   * @return the audit record, or null if not found
   */
  AuditRecord getRecord(String auditId);

  /**
   * Queries audit records within a time range.
   *
   * @param from start of time range (inclusive)
   * @param to end of time range (inclusive)
   * @return list of matching records, ordered by timestamp
   */
  List<AuditRecord> getRecords(Instant from, Instant to);

  /**
   * Returns the total number of audit records.
   *
   * @return the record count
   */
  int getRecordCount();
}
