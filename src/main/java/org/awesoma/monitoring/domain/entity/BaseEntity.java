package org.awesoma.monitoring.domain.entity;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.Hibernate;

/**
 * Shared identity for entities with a generated primary key.
 *
 * <p>Equality is based on the identifier alone: two loaded copies of the same row must be
 * equal, and a lazy proxy must equal the entity it stands for — hence {@link Hibernate#getClass}
 * rather than {@code getClass()}, which would see the proxy subclass and break symmetry.
 * An entity that has not been persisted yet is equal only to itself.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) {
            return false;
        }
        Long otherId = ((BaseEntity) other).getId();
        return id != null && id.equals(otherId);
    }

    @Override
    public int hashCode() {
        // Constant per type on purpose: the id is null until the entity is persisted, so a
        // hash derived from it would change while the entity is already inside a HashSet.
        return Hibernate.getClass(this).hashCode();
    }
}
