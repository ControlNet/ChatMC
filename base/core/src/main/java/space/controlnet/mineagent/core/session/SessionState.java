package space.controlnet.mineagent.core.session;

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
