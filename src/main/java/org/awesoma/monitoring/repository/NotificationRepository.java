package org.awesoma.monitoring.repository;

import org.awesoma.monitoring.domain.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByIncidentId(Long incidentId, Pageable pageable);
}
