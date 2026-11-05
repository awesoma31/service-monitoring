package org.awesoma.user.application.port;
import java.util.List;
import java.util.Optional;
import org.awesoma.user.application.model.UserPage;
import org.awesoma.user.domain.User;
public interface UserStore {
    Optional<User> findById(long id);
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByRole(org.awesoma.user.domain.enums.UserRole role);
    User save(User user);
    UserPage list(int page, int size);
    List<User> findAllById(List<Long> ids);
}
