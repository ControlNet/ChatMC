package space.controlnet.chatmc.ae.core.terminal;

import java.util.List;
import java.util.Optional;

/**
 * Shared DTOs for AE2 terminal interactions (block + part).
 */
public final class AiTerminalData {
    private AiTerminalData() {
    }

    public record AeEntry(String itemId, long amount, boolean craftable) {
    }

    public record AeListResult(List<AeEntry> results, Optional<String> nextPageToken, Optional<String> error) {
        public AeListResult {
            results = List.copyOf(results);
            nextPageToken = nextPageToken == null ? Optional.empty() : nextPageToken;
            error = error == null ? Optional.empty() : error;
        }
    }

    public record AePlanItem(String itemId, long amount) {
    }

    public record AeCraftSimulation(
            String jobId,
            String status,
            List<AePlanItem> missingItems,
            Optional<String> error) {
        public AeCraftSimulation {
            missingItems = List.copyOf(missingItems);
            error = error == null ? Optional.empty() : error;
        }
    }

    public record AeCraftRequest(String jobId, String status, Optional<String> error) {
        public AeCraftRequest {
            error = error == null ? Optional.empty() : error;
        }
    }

    public record AeJobStatus(
            String jobId,
            String status,
            List<AePlanItem> missingItems,
            Optional<String> error) {
        public AeJobStatus {
            missingItems = List.copyOf(missingItems);
            error = error == null ? Optional.empty() : error;
        }
    }
}
