package fr.hardel.leafs.region;

/** Lifecycle of a {@link Region}. Transitions only ever happen under the regionizer's write lock. */
public enum RegionState {
    /** Alive but owed to a merge target that is currently ticking; never schedulable. */
    TRANSIENT,
    /** Alive and schedulable, as long as no merge involving it is pending. */
    READY,
    /** Currently ticking: it may still gain sections from the feed, and it never loses any until {@link Region#markNotTicking()}. */
    TICKING,
    /** Merged away or split; the object must no longer be used. */
    DEAD
}
