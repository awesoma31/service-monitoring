package org.awesoma.notification.web.dto;

import org.awesoma.notification.domain.Channel;
import org.awesoma.notification.domain.ChannelType;

public record ChannelResponse(
        Long id, Long projectId, ChannelType type, String target, boolean enabled) {

    public static ChannelResponse of(Channel channel) {
        return new ChannelResponse(
                channel.getId(),
                channel.getProjectId(),
                channel.getType(),
                channel.getTarget(),
                channel.isEnabled());
    }
}
