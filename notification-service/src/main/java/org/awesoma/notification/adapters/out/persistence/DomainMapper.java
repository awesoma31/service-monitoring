package org.awesoma.notification.adapters.out.persistence;

import org.awesoma.notification.domain.Channel;
import org.awesoma.notification.domain.Notification;

/** Maps detached domain state to the persistence model at the database boundary. */
final class DomainMapper {
    private DomainMapper() {
    }

    static Channel channel(ChannelEntity row) {
        Channel result = new Channel();
        result.setId(row.getId());
        result.setProjectId(row.getProjectId());
        result.setType(row.getType());
        result.setTarget(row.getTarget());
        result.setEnabled(row.isEnabled());
        return result;
    }

    static ChannelEntity channel(Channel value) {
        ChannelEntity row = new ChannelEntity();
        row.setId(value.getId());
        row.setProjectId(value.getProjectId());
        row.setType(value.getType());
        row.setTarget(value.getTarget());
        row.setEnabled(value.isEnabled());
        return row;
    }

    static Notification notification(NotificationEntity row) {
        Notification result = new Notification();
        result.setId(row.getId());
        result.setIncidentId(row.getIncidentId());
        result.setReportId(row.getReportId());
        result.setChannel(channel(row.getChannel()));
        result.setSentAt(row.getSentAt());
        result.setIncidentKind(row.getIncidentKind());
        result.setSubject(row.getSubject());
        result.setMessage(row.getMessage());
        result.setStatus(row.getStatus());
        result.setAttempts(row.getAttempts());
        result.setNextAttemptAt(row.getNextAttemptAt());
        result.setLastError(row.getLastError());
        result.setDeliveryClaimToken(row.getDeliveryClaimToken());
        result.setDeliveryClaimedUntil(row.getDeliveryClaimedUntil());
        return result;
    }

    static NotificationEntity notification(Notification value, ChannelEntity channel) {
        NotificationEntity row = new NotificationEntity();
        row.setId(value.getId());
        row.setIncidentId(value.getIncidentId());
        row.setReportId(value.getReportId());
        row.setChannel(channel);
        row.setSentAt(value.getSentAt());
        row.setIncidentKind(value.getIncidentKind());
        row.setSubject(value.getSubject());
        row.setMessage(value.getMessage());
        row.setStatus(value.getStatus());
        row.setAttempts(value.getAttempts());
        row.setNextAttemptAt(value.getNextAttemptAt());
        row.setLastError(value.getLastError());
        row.setDeliveryClaimToken(value.getDeliveryClaimToken());
        row.setDeliveryClaimedUntil(value.getDeliveryClaimedUntil());
        return row;
    }
}
