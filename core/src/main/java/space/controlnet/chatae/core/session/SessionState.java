package space.controlnet.chatae.core.session;

public enum SessionState {
    IDLE,
    INDEXING,
    THINKING,
    WAIT_APPROVAL,
    EXECUTING,
    DONE,
    FAILED,
    CANCELED
}
