package space.controlnet.mineagent.common.recipes;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.RecipeManager;
import org.junit.jupiter.api.Test;
import space.controlnet.mineagent.core.recipes.RecipeIndexSnapshot;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RecipeIndexServiceInternalsRegressionTest {
    @Test
    void task20_recipeService_buildSnapshot_withEmptyRecipeIds_returnsStableEmptyIndexes() {
        RecipeIndexSnapshot snapshot = (RecipeIndexSnapshot) invokeStatic(
                "buildSnapshot",
                new Class<?>[]{MinecraftServer.class, RecipeManager.class, List.class},
                null,
                null,
                List.of()
        );

        assertEquals("task20/recipe-service/build-snapshot/by-id-empty", 0, snapshot.byId().size());
        assertEquals("task20/recipe-service/build-snapshot/by-output-empty", 0, snapshot.byOutputItemId().size());
        assertEquals("task20/recipe-service/build-snapshot/by-ingredient-empty", 0, snapshot.byIngredientItemId().size());
        assertEquals("task20/recipe-service/build-snapshot/by-tag-empty", 0, snapshot.byTagId().size());
        assertEquals("task20/recipe-service/build-snapshot/by-keyword-empty", 0, snapshot.byKeyword().size());
    }

    @Test
    void task20_recipeService_privateIndexHelpers_areDeterministicAndFreezeLists() {
        Map<String, List<String>> index = new HashMap<>();

        invokeStatic("addIndex", new Class<?>[]{Map.class, String.class, String.class}, index, "minecraft:oak", "minecraft:chest");
        invokeStatic("addIndex", new Class<?>[]{Map.class, String.class, String.class}, index, "minecraft:oak", "minecraft:barrel");
        invokeStatic("addIndex", new Class<?>[]{Map.class, String.class, String.class}, index, "minecraft:spruce", "minecraft:boat");

        assertEquals("task20/recipe-service/add-index/oak-size", 2, index.get("minecraft:oak").size());
        assertEquals("task20/recipe-service/add-index/spruce-size", 1, index.get("minecraft:spruce").size());

        invokeStatic("freezeIndex", new Class<?>[]{Map.class}, index);

        Throwable mutationFailure = assertThrows(
                "task20/recipe-service/freeze-index/list-unmodifiable",
                Throwable.class,
                () -> index.get("minecraft:oak").add("minecraft:hopper")
        );
        assertTrue("task20/recipe-service/freeze-index/is-unsupported-op", mutationFailure instanceof UnsupportedOperationException);
    }

    @Test
    void task20_recipeService_rebuildAsync_nullServer_failsFast() {
        RecipeIndexService service = new RecipeIndexService();

        Throwable failure = assertThrows(
                "task20/recipe-service/rebuild-null-server",
                Throwable.class,
                () -> service.rebuildAsync(null)
        );
        assertTrue("task20/recipe-service/rebuild-null-server-throws-npe", rootCause(failure) instanceof NullPointerException);
    }

    private static Object invokeStatic(String name, Class<?>[] paramTypes, Object... args) {
        try {
            Method method = RecipeIndexService.class.getDeclaredMethod(name, paramTypes);
            method.setAccessible(true);
            return method.invoke(null, args);
        } catch (Exception exception) {
            throw new AssertionError("task20/recipe-service/invoke-static/" + name, exception);
        }
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static <T extends Throwable> T assertThrows(String assertionName, Class<T> expectedType, ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Throwable throwable) {
            if (expectedType.isInstance(throwable)) {
                return expectedType.cast(throwable);
            }
            throw new AssertionError(
                    assertionName + " -> expected " + expectedType.getName() + " but got " + throwable.getClass().getName(),
                    throwable
            );
        }
        throw new AssertionError(assertionName + " -> expected exception " + expectedType.getName());
    }

    private static void assertTrue(String assertionName, boolean condition) {
        if (condition) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected true");
    }

    private static void assertEquals(String assertionName, Object expected, Object actual) {
        if ((expected == null && actual == null) || (expected != null && expected.equals(actual))) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected: " + expected + ", actual: " + actual);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Throwable;
    }
}
