package org.awesoma.user.adapters.out.persistence.repository;

import org.awesoma.user.adapters.out.persistence.ServiceClient;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceClientRepository extends JpaRepository<ServiceClient, String> {}
