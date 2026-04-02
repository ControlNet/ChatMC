package space.controlnet.mineagent.common.recipes;

import net.minecraft.server.packs.resources.PreparableReloadListener;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RecipeIndexReloadListenerRegressionTest {
    @Test
    void task20_recipeReloadListener_nullServerPath_waitsBarrierAndCompletes() {
        RecipeIndexService service = new RecipeIndexService();
        RecipeIndexReloadListener listener = new RecipeIndexReloadListener(service, () -> null);
        AtomicBoolean waited = new AtomicBoolean(false);

        PreparableReloadListener.PreparationBarrier barrier = newBarrier(waited);
        Executor directExecutor = Runnable::run;

        CompletableFuture<Void> future = listener.reload(
                barrier,
                null,
                null,
                null,
                directExecutor,
                directExecutor
        );

        future.join();
        assertTrue("task20/reload-listener/barrier-wait-called", waited.get());
    }

    private static PreparableReloadListener.PreparationBarrier newBarrier(AtomicBoolean waited) {
        InvocationHandler handler = (proxy, method, args) -> {
            if ("wait".equals(method.getName())) {
                waited.set(true);
                return CompletableFuture.completedFuture(null);
            }
            return defaultValue(method.getReturnType());
        };
        return (PreparableReloadListener.PreparationBarrier) Proxy.newProxyInstance(
                PreparableReloadListener.PreparationBarrier.class.getClassLoader(),
                new Class<?>[]{PreparableReloadListener.PreparationBarrier.class},
                handler
        );
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == char.class) {
            return '\0';
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0f;
        }
        if (returnType == double.class) {
            return 0d;
        }
        return null;
    }

    private static void assertTrue(String assertionName, boolean condition) {
        if (condition) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected true");
    }
}
