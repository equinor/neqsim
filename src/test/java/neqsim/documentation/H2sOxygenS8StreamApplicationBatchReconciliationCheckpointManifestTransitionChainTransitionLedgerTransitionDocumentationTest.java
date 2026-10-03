package neqsim.documentation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

/** Tests the S8 transition-ledger transition documentation contract. */
class H2sOxygenS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionDocumentationTest {
  private static final Path DOCUMENT = Paths.get("docs", "chemicalreactions",
      "h2s_oxygen_s8_stream_application_batch_reconciliation_checkpoint_manifest_transition_chain_transition_ledger_transition.md");

  /** Verify identity, prefix, numerical, and evidence boundaries remain explicit. */
  @Test
  void testDocumentationContract() throws IOException {
    String documentation = new String(Files.readAllBytes(DOCUMENT), StandardCharsets.UTF_8);

    assertTrue(documentation.startsWith("---\n"));
    assertTrue(documentation.contains("same caller-owned\nledger, chain, and manifest identities"));
    assertTrue(documentation.contains("exact\nordered prefix"));
    assertTrue(documentation.contains("continues both the prior final candidate-chain digest"));
    assertTrue(documentation.contains("Truncation, replacement, reordering"));
    assertTrue(documentation.contains("close exactly to the total transition delta"));
    assertTrue(documentation.contains("versioned canonical SHA-256 receipt"));
    assertTrue(documentation.contains("performs no flash calculation"));
    assertTrue(documentation.contains("no stream, fluid, process, pipeline, or injection mutation"));
  }
}
