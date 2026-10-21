package org.awesoma.user.repository;

import org.awesoma.user.domain.entity.ServiceClient;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceClientRepository extends JpaRepository<ServiceClient, String> {}
