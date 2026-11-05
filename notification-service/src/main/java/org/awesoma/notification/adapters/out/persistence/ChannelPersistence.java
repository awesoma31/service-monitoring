package org.awesoma.notification.adapters.out.persistence;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.awesoma.notification.application.model.PageResult;
import org.awesoma.notification.domain.Channel;
import org.awesoma.notification.domain.ChannelType;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChannelPersistence {
    private final ChannelEntityRows rows;

    public PageResult<Channel> pageByProject(Long id, int page, int size) {
        var result = rows.findByProjectId(id, PageRequest.of(page, size));
        return new PageResult<>(result.map(DomainMapper::channel).getContent(), result.getTotalElements(), result.hasNext());
    }

    public List<Channel> findByProjectIdAndEnabledTrue(Long id) {
        return rows.findByProjectIdAndEnabledTrue(id).stream().map(DomainMapper::channel).toList();
    }

    public Optional<Channel> findById(Long id) {
        return rows.findById(id).map(DomainMapper::channel);
    }

    public boolean existsByProjectIdAndTypeAndTarget(Long id, ChannelType type, String target) {
        return rows.existsByProjectIdAndTypeAndTarget(id, type, target);
    }

    @Transactional
    public Channel save(Channel value) {
        var row = rows.save(DomainMapper.channel(value));
        value.setId(row.getId());
        return value;
    }

    @Transactional
    public Channel saveAndFlush(Channel value) {
        var row = rows.saveAndFlush(DomainMapper.channel(value));
        value.setId(row.getId());
        return value;
    }

    @Transactional
    public void delete(Channel value) {
        rows.deleteById(value.getId());
    }

    @Transactional
    public int deleteByProjectId(Long id) {
        return rows.deleteByProjectId(id);
    }

    @Transactional
    public void deleteAllInBatch() {
        rows.deleteAllInBatch();
    }
}
