package fr.hardel.leafs.world;

import net.minecraft.world.level.gameevent.GameEventListener;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

public final class GameEventListeners extends AbstractList<GameEventListener> {
    private final List<Registration> listeners = new ArrayList<>();
    private final List<Registration> pending = new ArrayList<>();
    private int visits;

    @Override
    public synchronized boolean add(GameEventListener listener) {
        (visits == 0 ? listeners : pending).add(new Registration(listener));
        return true;
    }

    @Override
    public synchronized boolean remove(Object listener) {
        return removeFrom(listeners, listener) | removeFrom(pending, listener);
    }

    private static boolean removeFrom(List<Registration> registrations, Object listener) {
        for (Iterator<Registration> iterator = registrations.iterator(); iterator.hasNext();) {
            Registration registration = iterator.next();
            if (Objects.equals(registration.listener, listener)) {
                registration.active = false;
                iterator.remove();
                return true;
            }
        }

        return false;
    }

    @Override
    public synchronized GameEventListener get(int index) {
        return listeners.get(index).listener;
    }

    @Override
    public synchronized int size() {
        return listeners.size();
    }

    public synchronized void beginVisit() {
        visits++;
    }

    public synchronized void endVisit() {
        if (--visits == 0) {
            listeners.addAll(pending);
            pending.clear();
        }
    }

    @Override
    public synchronized Iterator<GameEventListener> iterator() {
        List<Registration> snapshot = List.copyOf(listeners);
        return new Iterator<>() {
            private int index;
            private Registration next;

            @Override
            public boolean hasNext() {
                while (next == null && index < snapshot.size()) {
                    Registration candidate = snapshot.get(index++);
                    if (candidate.active) {
                        next = candidate;
                    }
                }

                return next != null;
            }

            @Override
            public GameEventListener next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }

                GameEventListener listener = next.listener;
                next = null;
                return listener;
            }
        };
    }

    private static final class Registration {
        private final GameEventListener listener;
        private volatile boolean active = true;

        private Registration(GameEventListener listener) {
            this.listener = listener;
        }
    }
}
