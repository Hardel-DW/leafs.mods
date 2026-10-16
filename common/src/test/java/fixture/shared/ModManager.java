package fixture.shared;

import java.util.ArrayList;
import java.util.List;

public final class ModManager {
    public static final ModManager INSTANCE = new ModManager();
    public final List<String> entries = new ArrayList<>();
}
