package com.notificationpocket;

/** State for one listener-recovery round. */
final class RebindRequest {
    private boolean resetSystemBinding;

    void beginAutomatic() {
        resetSystemBinding = false;
    }

    void beginManual() {
        resetSystemBinding = true;
    }

    boolean consumeResetSystemBinding() {
        boolean shouldReset = resetSystemBinding;
        resetSystemBinding = false;
        return shouldReset;
    }

    void cancel() {
        resetSystemBinding = false;
    }
}
