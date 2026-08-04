package fr.hardel.leafs.chunk;

/** Implemented onto {@code SectionStorage} by mixin; the POI subclass shares the same lock instance. */
public interface PoiLockAccess {

    PoiVillageLock leafs$villageLock();
}
