package space.controlnet.mineagent.fabric.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.StringJoiner;
import java.util.UUID;

public final class MineAgentFabricGameTestSupport {
    private static final Object UNSUPPORTED_ARGUMENT = new Object();

    private MineAgentFabricGameTestSupport() {
    }

    public static ServerPlayer createServerPlayer(GameTestHelper helper, UUID playerId, String playerName) {
        MinecraftServer server = requireNonNull("runtime/create-player/server", helper.getLevel().getServer());
        ServerLevel level = helper.getLevel();
        GameProfile profile = new GameProfile(playerId, playerName);
        StringJoiner failureReasons = new StringJoiner(" | ");
        int compatibleConstructorCount = 0;

        for (Constructor<?> constructor : ServerPlayer.class.getDeclaredConstructors()) {
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            if (parameterTypes.length < 3) {
                continue;
            }
            if (!MinecraftServer.class.isAssignableFrom(parameterTypes[0])
                    || !ServerLevel.class.isAssignableFrom(parameterTypes[1])
                    || !GameProfile.class.isAssignableFrom(parameterTypes[2])) {
                continue;
            }

            Object[] args = new Object[parameterTypes.length];
            args[0] = server;
            args[1] = level;
            args[2] = profile;

            boolean supported = true;
            for (int index = 3; index < parameterTypes.length; index++) {
                Object argument = defaultConstructorArgument(parameterTypes[index], failureReasons);
                if (argument == UNSUPPORTED_ARGUMENT) {
                    supported = false;
                    break;
                }
                args[index] = argument;
            }

            if (!supported) {
                continue;
            }

            compatibleConstructorCount++;

            try {
                constructor.setAccessible(true);
                ServerPlayer player = (ServerPlayer) constructor.newInstance(args);
                player.setPos(0.5D, 2.0D, 0.5D);
                return player;
            } catch (ReflectiveOperationException | RuntimeException exception) {
                failureReasons.add(
                        "constructor "
                                + constructor
                                + " -> "
                                + exception.getClass().getSimpleName()
                                + ": "
                                + String.valueOf(exception.getMessage())
                );
            }
        }

        throw new AssertionError(
                "runtime/create-player -> unable to instantiate ServerPlayer for Fabric runtime tests"
                        + " (compatibleConstructors="
                        + compatibleConstructorCount
                        + ", failures="
                        + failureReasons
                        + ")"
        );
    }

    private static <T> T requireNonNull(String assertionName, T value) {
        if (value != null) {
            return value;
        }
        throw new AssertionError(assertionName + " -> value must not be null");
    }

    private static Object defaultConstructorArgument(Class<?> parameterType, StringJoiner failureReasons) {
        if (!parameterType.isPrimitive()) {
            Object defaultFactoryValue = invokeDefaultFactory(parameterType, failureReasons);
            if (defaultFactoryValue != UNSUPPORTED_ARGUMENT) {
                return defaultFactoryValue;
            }

            try {
                Constructor<?> constructor = parameterType.getDeclaredConstructor();
                constructor.setAccessible(true);
                return constructor.newInstance();
            } catch (NoSuchMethodException missingConstructor) {
                failureReasons.add(
                        "default-ctor "
                                + parameterType.getName()
                                + " -> NoSuchMethodException: "
                                + String.valueOf(missingConstructor.getMessage())
                );
                return null;
            } catch (ReflectiveOperationException | RuntimeException exception) {
                failureReasons.add(
                        "default-ctor "
                                + parameterType.getName()
                                + " -> "
                                + exception.getClass().getSimpleName()
                                + ": "
                                + String.valueOf(exception.getMessage())
                );
            }

            return null;
        }

        if (parameterType == boolean.class) {
            return false;
        }
        if (parameterType == byte.class) {
            return (byte) 0;
        }
        if (parameterType == short.class) {
            return (short) 0;
        }
        if (parameterType == int.class) {
            return 0;
        }
        if (parameterType == long.class) {
            return 0L;
        }
        if (parameterType == float.class) {
            return 0.0F;
        }
        if (parameterType == double.class) {
            return 0.0D;
        }
        if (parameterType == char.class) {
            return '\0';
        }
        return UNSUPPORTED_ARGUMENT;
    }

    private static Object invokeDefaultFactory(Class<?> parameterType, StringJoiner failureReasons) {
        for (Method method : parameterType.getDeclaredMethods()) {
            if (!Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0) {
                continue;
            }
            if (!parameterType.isAssignableFrom(method.getReturnType())) {
                continue;
            }

            String methodName = method.getName().toLowerCase(java.util.Locale.ROOT);
            if (!methodName.contains("default")) {
                continue;
            }

            try {
                method.setAccessible(true);
                return method.invoke(null);
            } catch (ReflectiveOperationException | RuntimeException exception) {
                failureReasons.add(
                        "default-factory "
                                + parameterType.getName()
                                + "#"
                                + method.getName()
                                + " -> "
                                + exception.getClass().getSimpleName()
                                + ": "
                                + String.valueOf(exception.getMessage())
                );
            }
        }
        return UNSUPPORTED_ARGUMENT;
    }
}
