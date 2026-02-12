package com.iisaka.cypher2sql.schema;

import java.util.concurrent.atomic.AtomicInteger;

final class AliasState {
    private final AtomicInteger counter;

    AliasState(final int initialValue) {
        this.counter = new AtomicInteger(initialValue);
    }

    String nextJoinAlias() {
        return "j" + counter.getAndIncrement();
    }
}
