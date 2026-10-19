package fixtures.mla;

public final class Counter extends CounterBase {
    private int count;
    private final int fixed = 7;
    public Counter(int count) { this.count = count; }
    private int add(int amount) { return count += amount; }
    private String add(String suffix) { return count + suffix; }
    public static int sum(int[] values) {
        if (Thread.currentThread().getContextClassLoader() != Counter.class.getClassLoader()) throw new AssertionError("Wrong legacy context loader");
        return java.util.Arrays.stream(values).sum();
    }
    public Counter self() { return this; }
    public static Counter identity(Counter value) { return value; }
    public Counter[] array() { return new Counter[]{this}; }
    public int fail() { throw new IllegalArgumentException("fixture failure"); }
}
