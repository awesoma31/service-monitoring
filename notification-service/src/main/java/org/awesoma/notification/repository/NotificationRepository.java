package org.awesoma.notification.repository;

import org.awesoma.notification.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** The channel is a to-one association, so fetching it keeps paging in the database. */
    @EntityGraph(attributePaths = "channel")
    Page<Notification> findByIncidentId(Long incidentId, Pageable pageable);
}
