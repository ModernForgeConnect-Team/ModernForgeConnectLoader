package github.com.gengyoubo.mfcl.internal.api;

import github.com.gengyoubo.mfcl.api.*;
import github.com.gengyoubo.mfcl.api.mla.*;
import org.junit.jupiter.api.*;
import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class LegacyAccessorTest {
    private URLClassLoader loader;
    private LegacyApiRuntime runtime;
    @BeforeEach void open() throws Exception {
        loader = new URLClassLoader(new java.net.URL[]{getClass().getProtectionDomain().getCodeSource().getLocation()}, ClassLoader.getPlatformClassLoader());
        runtime = create();
    }
    private LegacyApiRuntime create() {
        return new LegacyApiRuntime(loader, new LegacyApiRuntime.Scope() {
            @Override public synchronized <T> T call(Callable<T> task) throws Exception {
                var thread = Thread.currentThread(); var before = thread.getContextClassLoader();
                try { thread.setContextClassLoader(loader); return task.call(); }
                finally { thread.setContextClassLoader(before); }
            }
        }, new LegacyApiRuntime.Values() {
            public Set<String> keys(LegacyRuntime.Registry kind) { return Set.of(); }
            public Object find(LegacyRuntime.Registry kind, String key) { return null; }
            public Object stack(LegacyStack stack) { throw new UnsupportedOperationException(); }
            public LegacyStack snapshot(Object stack) { throw new UnsupportedOperationException(); }
        }, Set.of("fixture"));
    }
    @AfterEach void close() throws Exception { runtime.close(); loader.close(); }
    @LegacyType("fixtures.mla.Counter") public interface Factory {
        @LegacyConstructor(descriptor="(I)V") LegacyObject create(int value);
        @LegacyMethod(name="sum", descriptor="([I)I", isStatic=true) int sum(int[] values);
        @LegacyMethod(name="identity", descriptor="(Lfixtures/mla/Counter;)Lfixtures/mla/Counter;", isStatic=true) LegacyObject identity(LegacyObject value);
    }
    @LegacyType("fixtures.mla.Counter") public interface Counter {
        @LegacyField(name="count", descriptor="I") int count();
        @LegacyField(name="count", descriptor="I") void count(int value);
        @LegacyMethod(name="add", descriptor="(I)I") int add(int amount);
        @LegacyMethod(name="add", descriptor="(Ljava/lang/String;)Ljava/lang/String;") String add(String suffix);
        @LegacyMethod(name="inherited", descriptor="()I") int inherited();
        @LegacyMethod(name="self", descriptor="()Lfixtures/mla/Counter;") LegacyObject self();
        @LegacyMethod(name="array", descriptor="()[Lfixtures/mla/Counter;") LegacyObject[] array();
        @LegacyMethod(name="fail", descriptor="()I") int fail();
        default int doubleCount() { return count() * 2; }
    }
    @LegacyType("fixtures.mla.Counter") public interface WrongDescriptor {
        @LegacyMethod(name="add", descriptor="(I)Ljava/lang/String;") String wrong(int value);
    }
    @LegacyType("fixtures.mla.Counter") public interface WrongStatic {
        @LegacyField(name="count", descriptor="I", isStatic=true) int value();
    }
    @LegacyType("fixtures.mla.Counter") public interface FinalSetter {
        @LegacyField(name="fixed", descriptor="I") void value(int value);
    }
    @LegacyType("fixtures.mla.Counter") public interface Leak {
        @LegacyMethod(name="self", descriptor="()Lfixtures/mla/Counter;") Object value();
    }
    @LegacyType("java.lang.System") public interface HostTarget {
        @LegacyMethod(name="currentTimeMillis", descriptor="()J", isStatic=true) long time();
    }
    @Test void exactDescriptorsPrivateMembersInheritanceAndCopiesWork() {
        Factory factory = runtime.accessor(Factory.class);
        LegacyObject reference = factory.create(3);
        assertNotEquals(fixtures.mla.Counter.class, runtime.raw(reference).getClass());
        Counter counter = runtime.accessor(Counter.class, reference);
        assertEquals(5, counter.add(2)); assertEquals("5x", counter.add("x"));
        counter.count(11); assertEquals(22, counter.doubleCount()); assertEquals(42, counter.inherited());
        assertEquals(11, runtime.accessor(Counter.class, counter.array()[0]).count());
        int[] values = {2, 3}; assertEquals(5, factory.sum(values)); assertArrayEquals(new int[]{2,3}, values);
        assertTrue(counter.equals(counter)); assertFalse(counter.equals(factory)); assertTrue(counter.toString().contains("MLA"));
        var before = Thread.currentThread().getContextClassLoader();
        assertEquals("MLA_INVOKE", assertThrows(LegacyAccessException.class, counter::fail).code());
        assertSame(before, Thread.currentThread().getContextClassLoader());
        assertTrue(runtime.diagnostics().get(0).detail().contains("fixture failure"));
    }
    @Test void invalidContractsFailBeforeFirstInvocation() {
        var ref = runtime.accessor(Factory.class).create(0);
        for (Class<?> contract : List.of(WrongDescriptor.class, WrongStatic.class, FinalSetter.class, Leak.class, HostTarget.class))
            assertEquals("MLA_BIND", assertThrows(LegacyAccessException.class, () -> runtime.accessor(contract, ref)).code());
        assertThrows(LegacyAccessException.class, () -> runtime.accessor(Counter.class));
    }
    @Test void referencesCannotCrossRuntimeOrOutliveIt() {
        var factory = runtime.accessor(Factory.class); var ref = factory.create(2);
        try (var other = create()) {
            assertThrows(LegacyAccessException.class, () -> other.accessor(Counter.class, ref));
            var foreign = other.accessor(Factory.class).create(3);
            assertThrows(LegacyAccessException.class, () -> factory.identity(foreign));
        }
        assertThrows(LegacyAccessException.class, () -> factory.identity(() -> "fixtures.mla.Counter"));
        var counter = runtime.accessor(Counter.class, ref);
        runtime.close();
        assertThrows(LegacyAccessException.class, counter::count);
        assertThrows(LegacyAccessException.class, () -> factory.create(4));
    }
    @Test void borrowedGuardsPropagateThroughReturnsArraysAndArguments() throws Exception {
        var alive = new AtomicBoolean(true);
        var plain = runtime.accessor(Factory.class).create(1);
        var borrowed = runtime.borrowed(runtime.raw(plain), alive::get);
        var counter = runtime.accessor(Counter.class, borrowed);
        var child = runtime.accessor(Counter.class, counter.array()[0]);
        var returned = runtime.accessor(Factory.class).identity(borrowed);
        var executor = Executors.newSingleThreadExecutor();
        try { assertTrue(executor.submit(() -> assertThrows(LegacyAccessException.class, child::count)).get(5, TimeUnit.SECONDS).getMessage().contains("MLA_INVOKE")); }
        finally { executor.shutdownNow(); }
        alive.set(false);
        assertThrows(LegacyAccessException.class, counter::count);
        assertThrows(LegacyAccessException.class, () -> runtime.accessor(Counter.class, returned));
    }
    @Test void namedConvertersAreExplicitAndCannotReplaceEachOther() throws Exception {
        var ref = runtime.accessor(Factory.class).create(6);
        try (var registration = runtime.mappings().register("test:counter", String.class, value -> ref, old -> "six")) {
            assertEquals(ref, runtime.mappings().toLegacy("test:counter", "value").orElseThrow());
            assertEquals("six", runtime.mappings().toHost("test:counter", ref, String.class).orElseThrow());
            assertThrows(LegacyAccessException.class, () -> runtime.mappings().toLegacy("test:counter", 123));
            assertThrows(LegacyAccessException.class, () -> runtime.mappings().toHost("test:counter", ref, Integer.class));
            assertThrows(IllegalArgumentException.class, () -> runtime.mappings().register("test:counter", String.class, v -> ref, v -> ""));
        }
        assertTrue(runtime.mappings().supported().isEmpty());
        assertThrows(LegacyAccessException.class, () -> runtime.mappings().toLegacy("missing:converter", "value"));
    }
    @Test void readyCallbacksCanCancelAndFailureDoesNotBlockOtherAddons() throws Exception {
        var provider = new ApiRuntimeProvider(); var calls = new ArrayList<LegacyRuntime>();
        var canceled = provider.whenReady(calls::add); canceled.close();
        provider.whenReady(value -> { throw new IllegalArgumentException("broken addon"); });
        provider.whenReady(calls::add);
        assertTrue(MfclApi.runtime().isEmpty());
        ApiRuntimeProvider.publish(runtime);
        assertSame(runtime, MfclApi.runtime().orElseThrow()); assertEquals(List.of(runtime), calls);
        provider.whenReady(calls::add); assertEquals(2, calls.size());
        assertEquals("READY_CALLBACK", runtime.diagnostics().get(0).code());
        runtime.close(); assertTrue(MfclApi.runtime().isEmpty());
    }
    @Test void failureHistoryIsBounded() {
        for (int i = 0; i < 70; i++) runtime.note("TEST", "operation" + i, new IllegalArgumentException("failure"));
        assertEquals(64, runtime.diagnostics().size()); assertEquals("operation6", runtime.diagnostics().get(0).operation());
    }
}
