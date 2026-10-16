package fixture.shared;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ModState {
    public static final Map<String, Integer> COUNTS = new HashMap<>();
    public static final List<String> FILLED;
    public static final Map<String, Integer> FACTORY = Map.of("a", 1);
    public static final Map<String, Set<String>> NESTED = new HashMap<>();
    public static Map<String, Integer> buffer = new HashMap<>();

    static {
        List<String> filled = new ArrayList<>();
        filled.add("a");
        FILLED = filled;
    }

    private ModState() {
    }

    public static Map<String, Integer> drain() {
        Map<String, Integer> drained = buffer;
        buffer = new HashMap<>();
        return drained;
    }
}
