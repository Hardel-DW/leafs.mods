package fr.hardel.leafs.entity;

import java.util.ArrayList;
import java.util.List;

/**
 * A vehicle's full passenger hierarchy, captured before a teleport and re-mounted after placement.
 * Detach flattens depth-first and clears every riding link, so each node travels as a free entity.
 *
 * @param root  the tree's vehicle
 * @param mounts every (passenger, vehicle) pair in mount order, parents before their riders
 */
public record PassengerTree<E>(E root, List<Mount<E>> mounts) {

    public record Mount<E>(E passenger, E vehicle) {
    }

    public static <E> PassengerTree<E> detach(E root, TeleportOps<E> ops) {
        List<Mount<E>> mounts = new ArrayList<>();
        collect(root, ops, mounts);
        for (Mount<E> mount : mounts) {
            ops.dismount(mount.passenger());
        }

        return new PassengerTree<>(root, List.copyOf(mounts));
    }

    private static <E> void collect(E vehicle, TeleportOps<E> ops, List<Mount<E>> mounts) {
        for (E passenger : List.copyOf(ops.passengersOf(vehicle))) {
            mounts.add(new Mount<>(passenger, vehicle));
            collect(passenger, ops, mounts);
        }
    }

    public void restore(TeleportOps<E> ops) {
        for (Mount<E> mount : mounts) {
            ops.mount(mount.passenger(), mount.vehicle());
        }
    }

    public List<E> allEntities() {
        List<E> all = new ArrayList<>();
        all.add(root);
        for (Mount<E> mount : mounts) {
            all.add(mount.passenger());
        }

        return all;
    }
}
