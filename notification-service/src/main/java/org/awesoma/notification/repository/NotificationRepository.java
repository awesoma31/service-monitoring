package org.awesoma.notification.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.awesoma.notification.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** The channel is a to-one association, so fetching it keeps paging in the database. */
    @EntityGraph(attributePaths = "channel")
    Page<Notification> findByIncidentId(Long incidentId, Pageable pageable);

    @Query(
            value = """
                    SELECT n.*
                    FROM notifications n
                    JOIN channels c ON c.id = n.channel_id
                    WHERE n.status = 'PENDING'
                      AND n.next_attempt_at <= now()
                      AND (n.delivery_claimed_until IS NULL OR n.delivery_claimed_until <= now())
                      AND c.enabled = true
                    ORDER BY n.next_attempt_at, n.id
                    LIMIT :limit
                    FOR UPDATE OF n SKIP LOCKED
                    """,
            nativeQuery = true)
    List<Notification> findReadyForClaim(@Param("limit") int limit);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select notification from Notification notification where notification.id = :id")
    Optional<Notification> findForUpdateById(@Param("id") Long id);
}
