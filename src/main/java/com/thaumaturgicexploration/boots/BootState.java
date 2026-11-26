package com.thaumaturgicexploration.boots;

/** Per-player, unsaved state of the boots: time in the air, the slam in progress, time spent sprinting. */
public final class BootState {
    int airTicks;
    int smashTicks;
    boolean smashing;
    int runTicks;

    void reset() {
        airTicks = 0;
        smashTicks = 0;
        smashing = false;
    }
}
