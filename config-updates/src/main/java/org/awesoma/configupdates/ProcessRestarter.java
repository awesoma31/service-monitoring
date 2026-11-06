package org.awesoma.configupdates;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;

/**
 * Shuts the application down gracefully and exits with a code of its own. The instance restarts
 * itself this way, so nothing needs access to the Docker API: Compose brings it back because of
 * {@code restart: on-failure}, and Kubernetes would do the same.
 */
final class ProcessRestarter implements Restarter {
    static final int EXIT_CODE = 75;

    private final ApplicationContext context;

    ProcessRestarter(ApplicationContext context) {
        this.context = context;
    }

    @Override
    public void restart() {
        // Not on the caller's thread: closing the context waits for that thread's executor.
        new Thread(() -> System.exit(SpringApplication.exit(context, () -> EXIT_CODE)), "config-restart").start();
    }
}
