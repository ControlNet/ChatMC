package space.controlnet.mineagent.core.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.util.Optional;

public final class OptionalAndLocaleRegressionTest {
    @Test
    void task21_optionalTypeAdapter_roundTripsOptionalValues_withoutReflectiveAccessErrors() {
        Gson gson = new GsonBuilder()
                .registerTypeAdapterFactory(new OptionalTypeAdapterFactory())
                .create();
        Type optionalStringType = new TypeToken<Optional<String>>() {
        }.getType();

        String presentJson = gson.toJson(Optional.of("hello"), optionalStringType);
        String emptyJson = gson.toJson(Optional.empty(), optionalStringType);

        Optional<String> parsedPresent = gson.fromJson("\"world\"", optionalStringType);
        Optional<String> parsedNull = gson.fromJson("null", optionalStringType);

        assertEquals("task21/optional/present-serialized", "\"hello\"", presentJson);
        assertEquals("task21/optional/empty-serialized", "null", emptyJson);
        assertEquals("task21/optional/present-parsed", Optional.of("world"), parsedPresent);
        assertEquals("task21/optional/null-parsed", Optional.empty(), parsedNull);
    }

    @Test
    void task21_optionalTypeAdapterFactory_nonOptionalTypes_returnNullAdapter() {
        OptionalTypeAdapterFactory factory = new OptionalTypeAdapterFactory();
        Gson gson = new GsonBuilder().create();

        Object adapter = factory.create(gson, TypeToken.get(String.class));
        assertEquals("task21/optional/non-optional-adapter", null, adapter);
    }

    @Test
    void task21_localeResolver_prefersOverride_thenClient_thenDefault() {
        assertEquals("task21/locale/override", "ja_jp", LocaleResolver.resolveEffectiveLocale("en_us", " ja_jp "));
        assertEquals("task21/locale/client-fallback", "fr_fr", LocaleResolver.resolveEffectiveLocale(" fr_fr ", "   "));
        assertEquals("task21/locale/default", "en_us", LocaleResolver.resolveEffectiveLocale("  ", null));
    }

    private static void assertEquals(String assertionName, Object expected, Object actual) {
        if ((expected == null && actual == null) || (expected != null && expected.equals(actual))) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected: " + expected + ", actual: " + actual);
    }
}
