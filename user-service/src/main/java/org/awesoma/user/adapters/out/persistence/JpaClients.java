package org.awesoma.user.adapters.out.persistence;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.awesoma.user.application.port.ClientStore;
import org.awesoma.user.domain.ServiceClient;
import org.awesoma.user.adapters.out.persistence.repository.ServiceClientRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JpaClients implements ClientStore {
    private final ServiceClientRepository clients;

    @Override
    public Optional<ServiceClient> findById(String id) {
        return clients.findById(id).map(client ->
                new ServiceClient(client.getId(), client.getSecretHash(), client.isEnabled()));
    }

    @Override
    public ServiceClient save(ServiceClient client) {
        var row = clients.findById(client.id()).orElseGet(org.awesoma.user.adapters.out.persistence.ServiceClient::new);
        row.setId(client.id());
        row.setSecretHash(client.secretHash());
        row.setEnabled(client.enabled());
        var saved = clients.save(row);
        return new ServiceClient(saved.getId(), saved.getSecretHash(), saved.isEnabled());
    }
}
