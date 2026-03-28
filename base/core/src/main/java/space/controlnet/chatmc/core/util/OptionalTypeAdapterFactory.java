package space.controlnet.chatmc.core.util;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;

/**
 * Gson adapter factory for java.util.Optional to avoid reflective access errors on JDK 17+.
 */
public final class OptionalTypeAdapterFactory implements TypeAdapterFactory {
    @Override
    @SuppressWarnings("unchecked")
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        if (!Optional.class.isAssignableFrom(type.getRawType())) {
            return null;
        }

        Type optionalType = type.getType();
        Type valueType = Object.class;
        if (optionalType instanceof ParameterizedType parameterized) {
            Type[] args = parameterized.getActualTypeArguments();
            if (args.length == 1) {
                valueType = args[0];
            }
        }

        TypeAdapter<?> valueAdapter = gson.getAdapter(TypeToken.get(valueType));
        return (TypeAdapter<T>) new OptionalAdapter<>(valueAdapter);
    }

    private static final class OptionalAdapter<E> extends TypeAdapter<Optional<E>> {
        private final TypeAdapter<E> valueAdapter;

        private OptionalAdapter(TypeAdapter<E> valueAdapter) {
            this.valueAdapter = valueAdapter;
        }

        @Override
        public void write(JsonWriter out, Optional<E> value) throws IOException {
            if (value == null || value.isEmpty()) {
                out.nullValue();
                return;
            }
            valueAdapter.write(out, value.get());
        }

        @Override
        public Optional<E> read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return Optional.empty();
            }
            return Optional.ofNullable(valueAdapter.read(in));
        }
    }
}
