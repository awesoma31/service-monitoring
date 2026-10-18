package org.awesoma.monitoring.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.awesoma.monitoring.domain.entity.Tag;
import org.awesoma.monitoring.domain.entity.Project;
import org.junit.jupiter.api.Test;

class BaseEntityEqualityTest {

    @Test
    void entitiesOfDifferentTypesWithTheSameIdAreNotEqual() {
        Project user = new Project();
        user.setId(1L);
        Tag tag = new Tag();
        tag.setId(1L);

        assertThat(user).isNotEqualTo(tag);
    }

    @Test
    void separateInstancesOfTheSameRowAreEqual() {
        Project one = new Project();
        one.setId(42L);
        Project another = new Project();
        another.setId(42L);

        assertThat(one).isEqualTo(another).hasSameHashCodeAs(another);
    }

    @Test
    void unsavedEntitiesAreEqualOnlyToThemselves() {
        Project one = new Project();
        Project another = new Project();

        assertThat(one).isEqualTo(one).isNotEqualTo(another);
    }

    @Test
    void staysFindableInASetAfterTheIdIsAssigned() {
        Project user = new Project();
        Set<Project> set = new HashSet<>();
        set.add(user);

        user.setId(7L);

        assertThat(set).contains(user);
    }
}
