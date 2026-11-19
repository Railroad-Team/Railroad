package dev.railroadide.railroad.ide.sst.provider.api;

import dev.railroadide.railroad.ide.sst.request.api.CancellationToken;
import dev.railroadide.railroad.ide.sst.request.api.CancellationTokenSource;
import dev.railroadide.railroad.ide.sst.request.api.FeatureRequestContext;
import dev.railroadide.railroad.ide.sst.request.api.FeatureResult;
import dev.railroadide.railroad.ide.sst.request.api.WorkspaceRequestContext;
import dev.railroadide.railroad.ide.sst.request.api.RequestContext;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Owns a project's provider services. Closing cancels the lifetime and releases every distinct
 * closeable service once. Interface services are guarded before and after calls so retained
 * handles cannot publish results after unload. Request cancellation is combined with the session
 * lifetime. Providers must cooperate with request cancellation. Services must use public interface
 * contracts; concrete data values belong in session context services rather than provider capabilities.
 */
public final class LanguageSession implements AutoCloseable {
    private CapabilityRegistry capabilities;
    private final CancellationTokenSource lifetime = new CancellationTokenSource();
    private final Map<CapabilityKey<?>, Object> handles = new HashMap<>();
    private final List<Runnable> closeListeners = new ArrayList<>();

    /**
     * Takes ownership of provider capabilities and validates that their contracts are interfaces.
     *
     * @param capabilities immutable registry of services owned by this session
     * @throws IllegalArgumentException if a capability contract is not an interface
     */
    public LanguageSession(CapabilityRegistry capabilities) {
        this.capabilities = Objects.requireNonNull(capabilities, "capabilities");
        for (CapabilityKey<?> key : capabilities.keys()) {
            if (!key.type().isInterface())
                throw new IllegalArgumentException("Session capabilities must use interface contracts: " + key.id());
        }
    }

    /**
     * Exposes cancellation of the session lifetime.
     *
     * @return a token cancelled when this session closes
     */
    public CancellationToken cancellationToken() {
        return lifetime.token();
    }

    /**
     * Checks whether session ownership has ended.
     *
     * @return whether this session has been closed
     */
    public boolean isClosed() {
        return lifetime.token().isCancellationRequested();
    }

    /**
     * Runs once on closure, or immediately if ownership has already ended.
     *
     * @param listener callback invoked when ownership ends, or synchronously if already closed
     */
    public void onClose(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        synchronized (this) {
            if (!isClosed()) {
                closeListeners.add(listener);
                return;
            }
        }
        listener.run();
    }

    /**
     * Returns a guarded service handle that checks session and request activity around provider calls.
     *
     * @param <T> public contract type of the requested service
     * @param key capability identity and expected contract type
     * @return the guarded service, or empty if the capability is unsupported
     * @throws java.util.concurrent.CancellationException if this session is closed
     * @throws IllegalArgumentException if the capability identity has a different registered contract type
     */
    public synchronized <T> Optional<T> capability(CapabilityKey<T> key) {
        checkActive();
        return capabilities.find(key).map(value -> key.type().cast(handles.computeIfAbsent(key,
            _ -> guard(key))));
    }

    /**
     * Returns the contracts exposed by the active session.
     *
     * @return an immutable set of capability keys
     * @throws java.util.concurrent.CancellationException if this session is closed
     */
    public synchronized Set<CapabilityKey<?>> supportedCapabilities() {
        checkActive();
        return capabilities.keys();
    }

    private <T> Object guard(CapabilityKey<T> key) {
        return Proxy.newProxyInstance(key.type().getClassLoader(), new Class<?>[]{key.type()},
            (proxy, method, arguments) -> {
                if (method.getDeclaringClass() == Object.class)
                    return switch (method.getName()) {
                        case "toString" -> "Language capability " + key.id();
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == arguments[0];
                        default -> throw new UnsupportedOperationException(method.getName());
                    };
                checkActive();
                Object target;
                synchronized (this) {
                    checkActive();
                    target = capabilities.find(key).orElseThrow();
                }
                Object[] controlled = arguments == null ? null : arguments.clone();
                RequestContext original = null;
                RequestContext child = null;
                if (controlled != null) {
                    for (int i = 0; i < controlled.length; i++) {
                        if (controlled[i] instanceof FeatureRequestContext context) {
                            original = context;
                            controlled[i] = new FeatureRequestContext(context.workspaceSnapshot(),
                                context.documentSnapshot(), () -> isClosed()
                                    || context.cancellationToken().isCancellationRequested(),
                                context.deadline());
                            child = (RequestContext) controlled[i];
                        } else if (controlled[i] instanceof RequestContext context) {
                            original = context;
                            controlled[i] = new WorkspaceRequestContext(context.workspaceSnapshot(),
                                () -> isClosed() || context.cancellationToken().isCancellationRequested(),
                                context.deadline());
                            child = (RequestContext) controlled[i];
                        }
                    }
                }
                try {
                    if (child != null) {
                        child.checkActive();
                    }
                    Object result = method.invoke(target, controlled);
                    checkActive();
                    if (original != null) {
                        original.checkActive();
                    }
                    if (original != null && result instanceof FeatureResult<?> featureResult) {
                        if (!featureResult.belongsTo(child))
                            throw new IllegalStateException("Provider returned a result for another request");
                        return new FeatureResult<>(original, featureResult.value());
                    }
                    return result;
                } catch (InvocationTargetException exception) {
                    throw exception.getCause();
                }
            });
    }

    private void checkActive() {
        if (isClosed())
            throw new CancellationException("Language session is closed");
    }

    @Override
    public void close() {
        CapabilityRegistry released;
        List<Runnable> listeners;
        synchronized (this) {
            if (!lifetime.cancel())
                return;
            released = capabilities;
            capabilities = CapabilityRegistry.empty();
            handles.clear();
            listeners = List.copyOf(closeListeners);
            closeListeners.clear();
        }
        Set<Object> closed = Collections.newSetFromMap(new IdentityHashMap<>());
        RuntimeException failure = null;
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        for (Object value : released.values()) {
            if (value instanceof AutoCloseable closeable && closed.add(value)) {
                try {
                    closeable.close();
                } catch (Exception exception) {
                    if (failure == null) {
                        failure = new IllegalStateException("Could not dispose language session", exception);
                    } else {
                        failure.addSuppressed(exception);
                    }
                }
            }
        }
        if (failure != null)
            throw failure;
    }
}
