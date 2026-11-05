package org.awesoma.notification.adapters.out.persistence;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.awesoma.notification.application.model.PageResult;
import org.awesoma.notification.domain.Notification;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationPersistence {
    private final NotificationEntityRows rows;
    private final ChannelEntityRows channels;

    public PageResult<Notification> pageByIncident(Long id, int page, int size) {
        var result = rows.findByIncidentId(id, PageRequest.of(page, size));
        return new PageResult<>(result.map(DomainMapper::notification).getContent(), result.getTotalElements(), result.hasNext());
    }

    public List<Notification> findReadyForClaim(int limit) {
        return rows.findReadyForClaim(limit).stream().map(DomainMapper::notification).toList();
    }

    public Optional<Notification> findForUpdateById(Long id) {
        return rows.findForUpdateById(id).map(DomainMapper::notification);
    }

    public Optional<Notification> findById(Long id) {
        return rows.findById(id).map(DomainMapper::notification);
    }

    public List<Notification> findAll() {
        return rows.findAll().stream().map(DomainMapper::notification).toList();
    }

    @Transactional
    public Notification save(Notification value) {
        var channel = channels.getReferenceById(value.getChannel().getId());
        var row = rows.save(DomainMapper.notification(value, channel));
        value.setId(row.getId());
        return value;
    }

    @Transactional
    public Notification saveAndFlush(Notification value) {
        var channel = channels.getReferenceById(value.getChannel().getId());
        var row = rows.saveAndFlush(DomainMapper.notification(value, channel));
        value.setId(row.getId());
        return value;
    }

    @Transactional
    public List<Notification> saveAll(List<Notification> values) {
        return values.stream().map(this::save).toList();
    }

    @Transactional
    public void deleteAllInBatch() {
        rows.deleteAllInBatch();
    }
}
