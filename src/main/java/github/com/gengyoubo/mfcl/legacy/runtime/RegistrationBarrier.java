package github.com.gengyoubo.mfcl.legacy.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Two-step declaration/binding barrier. Used only once a real legacy backend supplies declarations. */
public final class RegistrationBarrier<T> {
    private enum Stage { COLLECTING, SEALED, BINDING, COMPLETE, FAILED }
    private Stage stage = Stage.COLLECTING;
    private final Map<String, T> declarations = new LinkedHashMap<>();
    private final Map<String, String> bindingKeys = new LinkedHashMap<>();
    private Throwable failure;

    public synchronized void declare(String key, T declaration) {
        require(Stage.COLLECTING);
        if (declarations.putIfAbsent(Objects.requireNonNull(key), Objects.requireNonNull(declaration)) != null)
            throw new IllegalStateException("Duplicate legacy declaration: " + key);
    }

    public synchronized Map<String, T> seal() {
        require(Stage.COLLECTING);
        stage = Stage.SEALED;
        return Map.copyOf(declarations);
    }

    public synchronized void beginBinding() { require(Stage.SEALED); stage = Stage.BINDING; }

    public synchronized void bind(String key, String modernKey) {
        require(Stage.BINDING);
        if (!declarations.containsKey(key)) throw new IllegalArgumentException("Unknown legacy declaration: " + key);
        if (bindingKeys.putIfAbsent(key, Objects.requireNonNull(modernKey)) != null) throw new IllegalStateException("Already bound: " + key);
    }

    public synchronized void complete() {
        require(Stage.BINDING);
        if (bindingKeys.size() != declarations.size()) throw new IllegalStateException("Unbound legacy declarations remain");
        stage = Stage.COMPLETE;
    }

    public synchronized String resolve(String key) {
        if (stage == Stage.FAILED) throw new IllegalStateException("Legacy registration failed", failure);
        String binding = bindingKeys.get(key);
        if (binding == null) throw new IllegalStateException("Legacy declaration has not been bound: " + key);
        return binding;
    }

    public synchronized void fail(Throwable cause) { failure = Objects.requireNonNull(cause); stage = Stage.FAILED; }

    private void require(Stage expected) {
        if (stage != expected) throw new IllegalStateException("Legacy registration phase " + stage + "; expected " + expected, failure);
    }
}
