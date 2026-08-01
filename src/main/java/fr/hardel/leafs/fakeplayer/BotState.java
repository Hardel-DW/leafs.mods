package fr.hardel.leafs.fakeplayer;

final class BotState {
    final BotScenario scenario;
    final double spawnX;
    final double spawnZ;
    boolean initialized;
    double heading;
    int cooldown;
    int dimensionIndex;

    BotState(BotScenario scenario, double spawnX, double spawnZ) {
        this.scenario = scenario;
        this.spawnX = spawnX;
        this.spawnZ = spawnZ;
    }
}
