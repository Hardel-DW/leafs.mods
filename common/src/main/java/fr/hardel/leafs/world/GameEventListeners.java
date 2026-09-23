package fr.hardel.leafs.world;

import net.minecraft.world.level.gameevent.GameEventListener;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public final class GameEventListeners extends AbstractList<GameEventListener> {
    private final List<Registration> listeners = new CopyOnWriteArrayList<>();
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
        for (Registration registration : registrations) {
            if (Objects.equals(registration.listener, listener)) {
                registration.active = false;
                return registrations.remove(registration);
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
    public Iterator<GameEventListener> iterator() {
        Iterator<Registration> snapshot = listeners.iterator();
        return new Iterator<>() {
            private Registration next;

            @Override
            public boolean hasNext() {
                while (next == null && snapshot.hasNext()) {
                    Registration candidate = snapshot.next();
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
