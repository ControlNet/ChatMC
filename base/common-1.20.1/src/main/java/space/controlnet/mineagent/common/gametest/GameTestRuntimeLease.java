package space.controlnet.mineagent.common.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayDeque;
import java.util.Deque;

public final class GameTestRuntimeLease {
    private static final Deque<LeaseRequest> WAITING_REQUESTS = new ArrayDeque<>();
    private static LeaseRequest activeRequest;

    private GameTestRuntimeLease() {
    }

    public static void runWhenAvailable(GameTestHelper helper, Runnable scenario) {
        LeaseRequest request = new LeaseRequest(requireServer(helper), scenario);
        boolean shouldRun;
        synchronized (GameTestRuntimeLease.class) {
            if (activeRequest == null) {
                activeRequest = request;
                shouldRun = true;
            } else {
                WAITING_REQUESTS.addLast(request);
                shouldRun = false;
            }
        }

        if (shouldRun) {
            runRequest(request);
        }
    }

    public static void release() {
        LeaseRequest nextRequest;
        synchronized (GameTestRuntimeLease.class) {
            if (activeRequest == null) {
                return;
            }

            activeRequest = null;
            nextRequest = WAITING_REQUESTS.pollFirst();
            if (nextRequest != null) {
                activeRequest = nextRequest;
            }
        }

        if (nextRequest != null) {
            nextRequest.server().execute(() -> runRequest(nextRequest));
        }
    }

    private static void runRequest(LeaseRequest request) {
        try {
            request.scenario().run();
        } catch (Throwable throwable) {
            release();
            throw throwable;
        }
    }

    private static MinecraftServer requireServer(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        if (server != null) {
            return server;
        }
        throw new AssertionError("game-test-runtime-lease/missing-server");
    }

    private record LeaseRequest(MinecraftServer server, Runnable scenario) {
    }
}
