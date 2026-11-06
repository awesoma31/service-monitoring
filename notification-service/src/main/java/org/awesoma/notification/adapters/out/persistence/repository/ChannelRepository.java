package org.awesoma.notification.adapters.out.persistence.repository;

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

    @Override
    public PageResult<Channel> pageByProject(Long projectId, int page, int size) {
        return persistence.pageByProject(projectId, page, size);
    }

    @Override
    public List<Channel> findByProjectIdAndEnabledTrue(Long id) {
        return persistence.findByProjectIdAndEnabledTrue(id);
    }

    @Override
    public Optional<Channel> findById(Long id) {
        return persistence.findById(id);
    }

    @Override
    public boolean existsByProjectIdAndTypeAndTarget(Long projectId, ChannelType type, String target) {
        return persistence.existsByProjectIdAndTypeAndTarget(projectId, type, target);
    }

    @Override
    public Channel save(Channel value) {
        return persistence.save(value);
    }

    public Channel saveAndFlush(Channel value) {
        return persistence.saveAndFlush(value);
    }

    @Override
    public void delete(Channel value) {
        persistence.delete(value);
    }

    @Override
    public int deleteByProjectId(Long projectId) {
        return persistence.deleteByProjectId(projectId);
    }

    public void deleteAllInBatch() {
        persistence.deleteAllInBatch();
    }
}
