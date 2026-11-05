package org.awesoma.monitoring.application.port;

import java.util.List;
import java.util.Map;
import org.awesoma.monitoring.application.model.UserSecurity;
import org.awesoma.monitoring.application.model.UserProfile;
public interface UserDirectoryPort {
    UserSecurity security(Long id);
    void requireActive(Long id);
    UserProfile profile(Long id);
    Map<Long, UserProfile> profiles(List<Long> ids);
}
