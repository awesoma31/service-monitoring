package org.awesoma.notification.application.port;

import java.util.List;
import java.util.Optional;
import org.awesoma.notification.application.model.PageResult;
import org.awesoma.notification.domain.Notification;
public interface NotificationStore {
    PageResult<Notification> pageByIncident(Long incidentId, int page, int size);
    List<Notification> findReadyForClaim(int limit);
    Optional<Notification> findForUpdateById(Long id);
    Notification save(Notification value);
    List<Notification> saveAll(List<Notification> values);
}
