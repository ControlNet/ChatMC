package space.controlnet.chatmc.common.client.screen.components;

import net.minecraft.client.gui.components.Button;
import space.controlnet.chatmc.core.session.SessionSummary;

public final class SessionRow {
    private SessionSummary summary;
    private Button deleteButton;
    private Button visibilityButton;

    public SessionSummary getSummary() {
        return summary;
    }

    public void setSummary(SessionSummary summary) {
        this.summary = summary;
    }

    public Button getDeleteButton() {
        return deleteButton;
    }

    public void setDeleteButton(Button deleteButton) {
        this.deleteButton = deleteButton;
    }

    public Button getVisibilityButton() {
        return visibilityButton;
    }

    public void setVisibilityButton(Button visibilityButton) {
        this.visibilityButton = visibilityButton;
    }
}
