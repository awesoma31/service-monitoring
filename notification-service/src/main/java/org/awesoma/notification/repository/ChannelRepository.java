package org.awesoma.notification.repository;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.awesoma.notification.adapters.out.persistence.ChannelPersistence;
import org.awesoma.notification.application.model.PageResult;
import org.awesoma.notification.application.port.ChannelStore;
import org.awesoma.notification.domain.Channel;
import org.awesoma.notification.domain.ChannelType;
import org.springframework.stereotype.Component;

/** Database adapter exposed to application use cases through the ChannelStore port. */
@Component
@RequiredArgsConstructor
public class ChannelRepository implements ChannelStore {
    private final ChannelPersistence persistence;

    public PageResult<Channel> pageByProject(Long projectId, int page, int size) {
        return persistence.pageByProject(projectId, page, size);
    }

    public List<Channel> findByProjectIdAndEnabledTrue(Long id) {
        return persistence.findByProjectIdAndEnabledTrue(id);
    }

    public Optional<Channel> findById(Long id) {
        return persistence.findById(id);
    }

    public boolean existsByProjectIdAndTypeAndTarget(Long projectId, ChannelType type, String target) {
        return persistence.existsByProjectIdAndTypeAndTarget(projectId, type, target);
    }

    public Channel save(Channel value) {
        return persistence.save(value);
    }

    public Channel saveAndFlush(Channel value) {
        return persistence.saveAndFlush(value);
    }

    public void delete(Channel value) {
        persistence.delete(value);
    }

    public int deleteByProjectId(Long projectId) {
        return persistence.deleteByProjectId(projectId);
    }

    public void deleteAllInBatch() {
        persistence.deleteAllInBatch();
    }
}
