package org.awesoma.configupdates;

/** Ends this instance so that its supervisor starts it again with the new settings. */
@FunctionalInterface
public interface Restarter {
    void restart();
}
