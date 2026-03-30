package space.controlnet.mineagent.core.proposal;

import java.io.Serializable;
import java.util.List;

public record ProposalDetails(
        String action,
        String itemId,
        long count,
        List<String> missingItems,
        String note
) implements Serializable {
    public ProposalDetails {
        action = action == null ? "" : action;
        itemId = itemId == null ? "" : itemId;
        missingItems = missingItems == null ? List.of() : List.copyOf(missingItems);
        note = note == null ? "" : note;
    }

    public static ProposalDetails empty() {
        return new ProposalDetails("", "", 0L, List.of(), "");
    }
}
