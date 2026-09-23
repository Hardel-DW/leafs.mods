package fr.hardel.leafs.chunk.owner;

public record ChunkClaim(Thread holder, RegionInbox mail) {

    public boolean mine() {
        return holder == Thread.currentThread();
    }
}
