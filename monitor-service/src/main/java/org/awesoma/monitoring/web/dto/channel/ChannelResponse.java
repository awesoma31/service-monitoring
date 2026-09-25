package org.awesoma.monitoring.web.dto.channel;

import org.awesoma.monitoring.domain.enums.ChannelType;

public record ChannelResponse(
        Long id, Long projectId, ChannelType type, String target, boolean enabled) {
}
