package fr.hardel.leafs.chunk;

/** Dynamic cap over vanilla's global view-ticket admission gate, pushed each serial tick from the player count. */
public interface ViewAdmissionAccess {

    void leafs$admissionCap(int cap);
}
