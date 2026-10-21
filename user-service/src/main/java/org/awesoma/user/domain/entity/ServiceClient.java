package org.awesoma.user.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "service_clients")
@Getter @Setter @NoArgsConstructor
public class ServiceClient {
    @Id @Column(length = 100) private String id;
    @JsonIgnore @Column(name = "secret_hash", nullable = false, length = 255) private String secretHash;
    @Column(nullable = false) private boolean enabled = true;
}
