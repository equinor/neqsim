package neqsim.documentation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

/** Tests the S8 manifest transition-chain transition-ledger documentation contract. */
class H2sOxygenS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerDocumentationTest {
  private static final Path DOCUMENT = Paths.get("docs", "chemicalreactions",
      "h2s_oxygen_s8_stream_application_batch_reconciliation_checkpoint_manifest_transition_chain_transition_ledger.md");

  /**
   * Verify the documentation keeps adjacency, numerical, and evidence boundaries.
   *
   * @throws IOException if the documentation cannot be read
   */
  @Test
  void testDocumentationContract() throws IOException {
    String documentation = new String(Files.readAllBytes(DOCUMENT), StandardCharsets.UTF_8);

    assertTrue(documentation.startsWith("---\n"));
    assertTrue(documentation.contains("same caller-owned chain and manifest identities"));
    assertTrue(documentation.contains("duplicate receipt digests"));
    assertTrue(documentation.contains("reordering, chain gaps"));
    assertTrue(documentation.contains("close exactly to total added"));
    assertTrue(documentation.contains("versioned canonical SHA-256 digest"));
    assertTrue(documentation.contains("performs no flash calculation"));
    assertTrue(documentation.contains("no"));
    assertTrue(documentation.contains("pipeline, or injection mutation"));
  }
}
