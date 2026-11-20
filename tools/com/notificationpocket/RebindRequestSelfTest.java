package com.notificationpocket;

public final class RebindRequestSelfTest {
    public static void main(String[] args) {
        automaticRecoveryNeverResetsSystemBinding();
        manualRecoveryResetsSystemBindingOnlyOnce();
        cancellingRecoveryClearsPendingReset();
        automaticRecoveryClearsPendingManualReset();
        System.out.println("RebindRequestSelfTest: 4 groups passed");
    }

    private static void automaticRecoveryNeverResetsSystemBinding() {
        RebindRequest request = new RebindRequest();
        request.beginAutomatic();
        check(!request.consumeResetSystemBinding(), "automatic first attempt");
        check(!request.consumeResetSystemBinding(), "automatic retry");
    }

    private static void manualRecoveryResetsSystemBindingOnlyOnce() {
        RebindRequest request = new RebindRequest();
        request.beginManual();
        check(request.consumeResetSystemBinding(), "manual first attempt");
        check(!request.consumeResetSystemBinding(), "manual retry");
    }

    private static void cancellingRecoveryClearsPendingReset() {
        RebindRequest request = new RebindRequest();
        request.beginManual();
        request.cancel();
        check(!request.consumeResetSystemBinding(), "cancelled manual recovery");
    }

    private static void automaticRecoveryClearsPendingManualReset() {
        RebindRequest request = new RebindRequest();
        request.beginManual();
        request.beginAutomatic();
        check(!request.consumeResetSystemBinding(), "automatic recovery clears pending reset");
    }

    private static void check(boolean condition, String name) {
        if (!condition) {
            throw new AssertionError("Failed: " + name);
        }
    }
}
