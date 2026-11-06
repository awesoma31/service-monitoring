package org.awesoma.notification.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Channel {

    private Long id;

    /** A project of monitor-service; another database, so an id rather than an association. */

    private Long projectId;

    private ChannelType type;

    private String target;

    private boolean enabled = true;
}
