package neqsim.documentation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

/** Tests the S8 checkpoint-transition-chain checkpoint-transition documentation contract. */
class H2sOxygenS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransitionDocumentationTest {
  private static final Path DOCUMENT = Paths.get("docs", "chemicalreactions",
      "h2s_oxygen_s8_stream_application_batch_reconciliation_checkpoint_manifest_transition_chain_transition_ledger_transition_chain_checkpoint_transition_chain_checkpoint_transition.md");

  /** Verify identity, ordering, digest, numerical, and evidence boundaries remain explicit. */
  @Test
  void testDocumentationContract() throws IOException {
    String documentation = new String(Files.readAllBytes(DOCUMENT), StandardCharsets.UTF_8);

    assertTrue(documentation.startsWith("---\n"));
    assertTrue(documentation.contains("independently verified checkpoints"));
    assertTrue(documentation.contains("candidate sequence cannot regress"));
    assertTrue(documentation.contains("same-sequence candidate"));
    assertTrue(documentation.contains("preserve every prior transition receipt"));
    assertTrue(documentation.contains("checked subtraction"));
    assertTrue(documentation.contains("close exactly"));
    assertTrue(documentation.contains("versioned canonical SHA-256 digest"));
    assertTrue(documentation.contains("compares raw digests in constant time"));
    assertTrue(documentation.contains("defensive copy"));
    assertTrue(documentation.contains("performs no flash calculation"));
    assertTrue(documentation.contains("no stream, fluid, process, pipeline, or injection mutation"));
  }
}
