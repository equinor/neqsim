package neqsim.process.decisionsupport;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.Gson;

/**
 * File-based implementation of {@link AuditLogger} that writes JSON-lines to a log file.
 *
 * <p>
 * Each audit record is written as a single JSON line, making the log easy to parse and append to.
 * Records are read back by scanning the file. Suitable for production deployments where a full
 * database is not needed.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class FileAuditLogger implements AuditLogger {
  private static final Logger logger = LogManager.getLogger(FileAuditLogger.class);
  private static final Gson GSON = GsonFactory.instance();

  private final File logFile;

  /**
   * Creates a file audit logger writing to the specified file.
   *
   * @param logFilePath path to the audit log file
   */
  public FileAuditLogger(String logFilePath) {
    this.logFile = new File(logFilePath);
    File parentDir = logFile.getParentFile();
    if (parentDir != null && !parentDir.exists()) {
      parentDir.mkdirs();
    }
  }

  /** {@inheritDoc} */
  @Override
  public synchronized void log(AuditRecord record) {
    if (record == null) {
      return;
    }
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
      writer.write(GSON.toJson(record));
      writer.newLine();
    } catch (IOException e) {
      logger.error("Failed to write audit record: {}", e.getMessage());
    }
  }

  /** {@inheritDoc} */
  @Override
  public AuditRecord getRecord(String auditId) {
    for (AuditRecord record : readAllRecords()) {
      if (record.getAuditId().equals(auditId)) {
        return record;
      }
    }
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public List<AuditRecord> getRecords(Instant from, Instant to) {
    List<AuditRecord> result = new ArrayList<>();
    for (AuditRecord record : readAllRecords()) {
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
    return readAllRecords().size();
  }

  /**
   * Reads all records from the log file.
   *
   * @return list of all audit records
   */
  private List<AuditRecord> readAllRecords() {
    List<AuditRecord> records = new ArrayList<>();
    if (!logFile.exists()) {
      return records;
    }
    try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
      String line;
      while ((line = reader.readLine()) != null) {
        String trimmed = line.trim();
        if (!trimmed.isEmpty()) {
          try {
            AuditRecord record = GSON.fromJson(trimmed, AuditRecord.class);
            if (record != null) {
              records.add(record);
            }
          } catch (Exception e) {
            logger.warn("Skipping malformed audit record line: {}", e.getMessage());
          }
        }
      }
    } catch (IOException e) {
      logger.error("Failed to read audit log: {}", e.getMessage());
    }
    return records;
  }
}
