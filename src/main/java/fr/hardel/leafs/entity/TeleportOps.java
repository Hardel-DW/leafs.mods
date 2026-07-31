package fr.hardel.leafs.entity;

import java.util.List;

/** Entity operations the teleport machinery needs; M11 binds it to real entities, tests use fakes. */
public interface TeleportOps<E> {

    List<E> passengersOf(E entity);

    void dismount(E passenger);

    void mount(E passenger, E vehicle);
}
