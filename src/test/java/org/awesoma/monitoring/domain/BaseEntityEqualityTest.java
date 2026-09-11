package org.awesoma.monitoring.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.awesoma.monitoring.domain.entity.Tag;
import org.awesoma.monitoring.domain.entity.User;
import org.junit.jupiter.api.Test;

class BaseEntityEqualityTest {

    @Test
    void entitiesOfDifferentTypesWithTheSameIdAreNotEqual() {
        User user = new User();
        user.setId(1L);
        Tag tag = new Tag();
        tag.setId(1L);

        assertThat(user).isNotEqualTo(tag);
    }

    @Test
    void separateInstancesOfTheSameRowAreEqual() {
        User one = new User();
        one.setId(42L);
        User another = new User();
        another.setId(42L);

        assertThat(one).isEqualTo(another).hasSameHashCodeAs(another);
    }

    @Test
    void unsavedEntitiesAreEqualOnlyToThemselves() {
        User one = new User();
        User another = new User();

        assertThat(one).isEqualTo(one).isNotEqualTo(another);
    }

    @Test
    void staysFindableInASetAfterTheIdIsAssigned() {
        User user = new User();
        Set<User> set = new HashSet<>();
        set.add(user);

        user.setId(7L);

        assertThat(set).contains(user);
    }
}
