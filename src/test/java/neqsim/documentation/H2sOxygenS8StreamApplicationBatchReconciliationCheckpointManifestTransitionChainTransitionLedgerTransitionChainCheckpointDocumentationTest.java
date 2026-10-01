package neqsim.documentation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

/** Tests the S8 transition-ledger transition-chain checkpoint documentation contract. */
class H2sOxygenS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointDocumentationTest {
  private static final Path DOCUMENT = Paths.get("docs", "chemicalreactions",
      "h2s_oxygen_s8_stream_application_batch_reconciliation_checkpoint_manifest_transition_chain_transition_ledger_transition_chain_checkpoint.md");

  /** Verify checkpoint identity, numerical, digest, and evidence boundaries remain explicit. */
  @Test
  void testDocumentationContract() throws IOException {
    String documentation = new String(Files.readAllBytes(DOCUMENT), StandardCharsets.UTF_8);

    assertTrue(documentation.startsWith("---\n"));
    assertTrue(documentation.contains("non-negative caller-owned checkpoint sequence"));
    assertTrue(documentation.contains("defensive copy of the upstream raw chain digest"));
    assertTrue(documentation.contains("close exactly to the transition count"));
    assertTrue(documentation.contains("overflow-detecting integer arithmetic"));
    assertTrue(documentation.contains("versioned canonical SHA-256 digest"));
    assertTrue(documentation.contains("MessageDigest.isEqual"));
    assertTrue(documentation.contains("allocates no\nsequence number"));
    assertTrue(documentation.contains("performs no flash calculation"));
    assertTrue(documentation.contains("no stream, fluid, process, pipeline, or injection mutation"));
  }
}
