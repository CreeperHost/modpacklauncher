package net.creeperhost.minetogether.lib.util;

public class Consumers {
    @FunctionalInterface
    public interface TriConsumer<A1, A2, A3> {
        void accept(A1 a1, A2 a2, A3 a3);
    }

    @FunctionalInterface
    public interface DuoConsumer<A1, A2> {
        void accept(A1 a1, A2 a2);
    }
}
