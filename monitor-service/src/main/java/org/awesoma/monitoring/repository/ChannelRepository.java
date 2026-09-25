package org.awesoma.monitoring.repository;

import java.util.List;
import org.awesoma.monitoring.domain.entity.Channel;
import org.awesoma.monitoring.domain.enums.ChannelType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelRepository extends JpaRepository<Channel, Long> {

    List<Channel> findByProjectIdAndEnabledTrue(Long projectId);

    Page<Channel> findByProjectId(Long projectId, Pageable pageable);

    boolean existsByProjectIdAndTypeAndTarget(
            Long projectId, ChannelType type, String target);
}
