package org.awesoma.notification.repository;

import java.util.List;
import org.awesoma.notification.domain.Channel;
import org.awesoma.notification.domain.ChannelType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ChannelRepository extends JpaRepository<Channel, Long> {

    Page<Channel> findByProjectId(Long projectId, Pageable pageable);

    List<Channel> findByProjectIdAndEnabledTrue(Long projectId);

    boolean existsByProjectIdAndTypeAndTarget(Long projectId, ChannelType type, String target);

    /** Bulk delete: the database cascades to the notifications of these channels. */
    @Modifying
    @Query("delete from Channel c where c.projectId = :projectId")
    int deleteByProjectId(Long projectId);
}
