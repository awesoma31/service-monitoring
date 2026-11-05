package org.awesoma.monitoring.domain.entity;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.awesoma.monitoring.domain.enums.HttpMethod;
import org.awesoma.monitoring.domain.enums.MonitorState;

@Getter
@Setter
@NoArgsConstructor
public class Monitor extends BaseEntity {

    private Project project;

    private String name;

    private String url;

    private HttpMethod httpMethod = HttpMethod.GET;

    private int intervalSec;

    private int timeoutMs;

    private int expectedStatus = 200;

    private boolean active = true;

    private MonitorState currentState = MonitorState.UNKNOWN;

    /** Null until the first probe, which makes a fresh monitor due straight away. */
    private OffsetDateTime lastCheckedAt;

    /** Identifies the check-service job that currently owns this monitor's next probe. */
    private UUID checkClaimToken;

    /** A crashed worker releases its claim implicitly when this instant is reached. */
    private OffsetDateTime checkClaimedUntil;

    public void claimForCheck(UUID token, OffsetDateTime until) {
        this.checkClaimToken = token;
        this.checkClaimedUntil = until;
    }

    public boolean ownsActiveCheckClaim(UUID token, OffsetDateTime now) {
        return token != null
                && token.equals(checkClaimToken)
                && checkClaimedUntil != null
                && checkClaimedUntil.isAfter(now);
    }

    public void clearCheckClaim() {
        this.checkClaimToken = null;
        this.checkClaimedUntil = null;
    }

    /** Labels attached to this monitor. */
    private Set<Tag> tags = new LinkedHashSet<>();
}
