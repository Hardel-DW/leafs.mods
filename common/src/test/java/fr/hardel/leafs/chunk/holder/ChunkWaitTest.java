package fr.hardel.leafs.chunk.holder;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChunkWaitTest {
    private final List<String> released = new ArrayList<>();

    @Test
    void aDemandWithoutAScopeIsReleasedAtOnce() {
        ChunkWait.keep(() -> released.add("now"));

        assertEquals(List.of("now"), released);
    }

    /** 2026-09-06: the portal spiral re-read the same nether chunks, each unloaded as soon as its wait ended; vanilla keeps them until its tick ends. */
    @Test
    void aDemandLivesUntilTheOutermostScopeEnds() {
        ChunkWait.enterScope();
        ChunkWait.keep(() -> released.add("read"));
        ChunkWait.enterScope();
        ChunkWait.keep(() -> released.add("write"));
        ChunkWait.exitScope();
        assertEquals(List.of(), released, "a chunk taken inside the tick closes before it, the reads hold");

        ChunkWait.exitScope();
        assertEquals(List.of("read", "write"), released);
    }
}
