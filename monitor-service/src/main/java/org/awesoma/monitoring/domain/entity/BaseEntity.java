package org.awesoma.monitoring.domain.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
/** Identity based on a non-null identifier; transient objects equal only themselves. */

@Getter
@Setter
@NoArgsConstructor
public abstract class BaseEntity {

    private Long id;

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        Long otherId = ((BaseEntity) other).getId();
        return id != null && id.equals(otherId);
    }

    // Constant per type on purpose: the id is null until the entity is persisted, so a
        // hash derived from it would change while the entity is already inside a HashSet.
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
