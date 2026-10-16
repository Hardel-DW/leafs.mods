package fixture.gametest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ModTickState {
    public static final Map<UUID, Integer> SEEN = new HashMap<>();

    private ModTickState() {
    }
}
