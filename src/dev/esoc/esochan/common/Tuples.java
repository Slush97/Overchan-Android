package dev.esoc.esochan.common;

public final class Tuples {
    private Tuples() {}

    public record Pair<L, R>(L left, R right) {
        public static <L, R> Pair<L, R> of(L left, R right) {
            return new Pair<>(left, right);
        }
        public L getLeft() { return left; }
        public R getRight() { return right; }
        public L getKey() { return left; }
        public R getValue() { return right; }
    }

    public record Triple<L, M, R>(L left, M middle, R right) {
        public static <L, M, R> Triple<L, M, R> of(L left, M middle, R right) {
            return new Triple<>(left, middle, right);
        }
        public L getLeft() { return left; }
        public M getMiddle() { return middle; }
        public R getRight() { return right; }
    }
}
