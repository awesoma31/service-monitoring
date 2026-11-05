package org.awesoma.notification.repository;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.awesoma.notification.adapters.out.persistence.NotificationPersistence;
import org.awesoma.notification.application.model.PageResult;
import org.awesoma.notification.application.port.NotificationStore;
import org.awesoma.notification.domain.Notification;
import org.springframework.stereotype.Component;

/** Database adapter exposed to application use cases through the NotificationStore port. */
@Component
@RequiredArgsConstructor
public class NotificationRepository implements NotificationStore {
    private final NotificationPersistence persistence;

    public PageResult<Notification> pageByIncident(Long id, int page, int size) {
        return persistence.pageByIncident(id, page, size);
    }

    public List<Notification> findReadyForClaim(int limit) {
        return persistence.findReadyForClaim(limit);
    }

    public Optional<Notification> findForUpdateById(Long id) {
        return persistence.findForUpdateById(id);
    }

    public Optional<Notification> findById(Long id) {
        return persistence.findById(id);
    }

    public List<Notification> findAll() {
        return persistence.findAll();
    }

    public Notification save(Notification value) {
        return persistence.save(value);
    }

    public Notification saveAndFlush(Notification value) {
        return persistence.saveAndFlush(value);
    }

    public List<Notification> saveAll(List<Notification> values) {
        return persistence.saveAll(values);
    }

    public void deleteAllInBatch() {
        persistence.deleteAllInBatch();
    }
}
