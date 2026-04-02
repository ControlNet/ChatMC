package space.controlnet.mineagent.common.recipes;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import space.controlnet.mineagent.core.recipes.RecipeSearchFilters;
import space.controlnet.mineagent.core.recipes.RecipeSearchResult;
import space.controlnet.mineagent.core.recipes.RecipeSummary;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

public final class RecipeIndexServiceMinecraftPathRegressionTest {
    @Test
    void task23_recipeIndexService_rebuildAsync_buildsSnapshotFromMinecraftRecipeManager() {
        ensureMinecraftBootstrap();
        RecipeIndexService service = new RecipeIndexService();

        MinecraftServer server = Mockito.mock(MinecraftServer.class);
        RecipeManager recipeManager = Mockito.mock(RecipeManager.class);
        Recipe<?> recipe = Mockito.mock(Recipe.class);
        Ingredient ingredient = Mockito.mock(Ingredient.class);

        ResourceLocation recipeId = requiredId("minecraft:chest_from_planks");
        RecipeType<?> anyType = BuiltInRegistries.RECIPE_TYPE.iterator().next();

        Mockito.when(server.getRecipeManager()).thenReturn(recipeManager);
        Mockito.when(recipeManager.getRecipeIds()).thenReturn(Stream.of(recipeId));
        Mockito.doReturn(Optional.of(recipe)).when(recipeManager).byKey(recipeId);
        Mockito.doReturn(anyType).when(recipe).getType();
        Mockito.doReturn(new ItemStack(Items.CHEST, 2)).when(recipe).getResultItem(Mockito.any());
        Mockito.doReturn(new ItemStack[]{new ItemStack(Items.OAK_PLANKS, 1)}).when(ingredient).getItems();
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(ingredient);
        Mockito.doReturn(ingredients).when(recipe).getIngredients();

        service.rebuildAsync(server).join();

        assertTrue("task23/service/rebuild-ready", service.isReady());
        RecipeSummary summary = service.get(recipeId.toString()).orElseThrow(
                () -> new AssertionError("task23/service/rebuild-summary-missing"));
        assertEquals("task23/service/summary-id", recipeId.toString(), summary.recipeId());
        assertEquals("task23/service/summary-output", "minecraft:chest", summary.outputItemId());
        assertEquals("task23/service/summary-output-count", 2, summary.outputCount());
        assertTrue("task23/service/summary-has-ingredient", summary.ingredientItemIds().contains("minecraft:oak_planks"));

        RecipeSearchResult byOutput = service.findByOutput("minecraft:chest", Optional.empty(), 5);
        assertEquals("task23/service/find-by-output-size", 1, byOutput.results().size());
        assertEquals("task23/service/find-by-output-id", recipeId.toString(), byOutput.results().get(0).recipeId());

        RecipeSearchResult byIngredient = service.findByIngredient("minecraft:oak_planks", Optional.empty(), 5);
        assertEquals("task23/service/find-by-ingredient-size", 1, byIngredient.results().size());

        RecipeSearchResult byKeyword = service.search("chest", RecipeSearchFilters.empty(), Optional.empty(), 5);
        assertEquals("task23/service/search-size", 1, byKeyword.results().size());
    }

    @Test
    void task23_recipeIndexReloadListener_nonNullServer_callsRebuildAndWaitsBarrier() {
        RecipeIndexService service = Mockito.mock(RecipeIndexService.class);
        MinecraftServer server = Mockito.mock(MinecraftServer.class);
        RecipeIndexReloadListener listener = new RecipeIndexReloadListener(service, () -> server);
        AtomicBoolean waited = new AtomicBoolean(false);

        Mockito.when(service.rebuildAsync(server)).thenReturn(CompletableFuture.completedFuture(null));

        PreparableReloadListener.PreparationBarrier barrier = newBarrier(waited);
        Executor direct = Runnable::run;

        listener.reload(barrier, null, null, null, direct, direct).join();

        Mockito.verify(service).rebuildAsync(server);
        assertTrue("task23/reload-listener/non-null-server-waits", waited.get());
    }

    private static ResourceLocation requiredId(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id != null) {
            return id;
        }
        throw new AssertionError("task23/required-id-invalid: " + value);
    }

    private static void ensureMinecraftBootstrap() {
        try {
            Class<?> sharedConstants = Class.forName("net.minecraft.SharedConstants");
            sharedConstants.getMethod("tryDetectVersion").invoke(null);

            Class<?> bootstrap = Class.forName("net.minecraft.server.Bootstrap");
            bootstrap.getMethod("bootStrap").invoke(null);
        } catch (Exception exception) {
            throw new AssertionError("task23/bootstrap", exception);
        }
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

    private static void assertTrue(String assertionName, boolean value) {
        if (value) {
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
}
