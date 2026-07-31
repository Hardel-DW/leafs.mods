package fr.hardel.leafs.entity;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PassengerTreeTest {
    private final Map<String, List<String>> passengers = new HashMap<>();
    private final Map<String, String> vehicles = new HashMap<>();

    private final TeleportOps<String> ops = new TeleportOps<>() {
        @Override
        public List<String> passengersOf(String entity) {
            return passengers.getOrDefault(entity, List.of());
        }

        @Override
        public void dismount(String passenger) {
            String vehicle = vehicles.remove(passenger);
            if (vehicle != null) {
                passengers.get(vehicle).remove(passenger);
            }
        }

        @Override
        public void mount(String passenger, String vehicle) {
            vehicles.put(passenger, vehicle);
            passengers.computeIfAbsent(vehicle, _ -> new ArrayList<>()).add(passenger);
        }
    };

    private void mountChain() {
        ops.mount("player", "camel");
        ops.mount("passenger", "camel");
        ops.mount("parrot", "player");
    }

    @Test
    void detachFlattensAndClearsEveryRidingLink() {
        mountChain();

        PassengerTree<String> tree = PassengerTree.detach("camel", ops);

        assertTrue(vehicles.isEmpty(), "every riding link must be cleared");
        assertEquals(List.of("camel", "player", "parrot", "passenger"), tree.allEntities(), "depth-first, parents before riders");
    }

    @Test
    void restoreRemountsTheExactHierarchy() {
        mountChain();
        PassengerTree<String> tree = PassengerTree.detach("camel", ops);

        tree.restore(ops);

        assertEquals("camel", vehicles.get("player"));
        assertEquals("camel", vehicles.get("passenger"));
        assertEquals("player", vehicles.get("parrot"));
        assertEquals(List.of("player", "passenger"), passengers.get("camel"));
    }

    @Test
    void entityWithoutPassengersIsATrivialTree() {
        PassengerTree<String> tree = PassengerTree.detach("boat", ops);

        assertEquals(List.of("boat"), tree.allEntities());
        tree.restore(ops);
        assertTrue(vehicles.isEmpty());
    }
}
