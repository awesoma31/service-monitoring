package org.awesoma.user.application.port;
import java.util.Optional;
import org.awesoma.user.domain.ServiceClient;
public interface ClientStore {
    Optional<ServiceClient> findById(String id);
    ServiceClient save(ServiceClient client);
}
