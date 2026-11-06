package org.awesoma.notification.application.port;

import java.util.List;
import java.util.Optional;
import org.awesoma.notification.application.model.PageResult;
import org.awesoma.notification.domain.Channel;
import org.awesoma.notification.domain.ChannelType;
public interface ChannelStore {
    PageResult<Channel> pageByProject(Long projectId, int page, int size);
    List<Channel> findByProjectIdAndEnabledTrue(Long projectId);
    Optional<Channel> findById(Long id);
    boolean existsByProjectIdAndTypeAndTarget(Long projectId, ChannelType type, String target);
    Channel save(Channel channel);
    void delete(Channel channel);
    int deleteByProjectId(Long projectId);
}
