package org.awesoma.report.adapters.out.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.awesoma.report.application.port.AccessDecision;
import org.awesoma.report.application.port.AccessLevel;
import org.awesoma.report.application.port.AccessUnavailableException;
import org.awesoma.security.access.ResourceAccessClient;
import org.junit.jupiter.api.Test;

class MonitorServiceProjectAccessTest {
    private final ProjectLookupClient projects = mock(ProjectLookupClient.class);
    private final ResourceAccessClient access = mock(ResourceAccessClient.class);
    private final MonitorServiceProjectAccess adapter = new MonitorServiceProjectAccess(projects, access);

    @Test
    void asksForWriteAccessOnlyForWrites() {
        when(projects.projectExists(1L)).thenReturn(true);
        when(access.allowed("project", 1L, 7L, false)).thenReturn(true);
        when(access.allowed("project", 1L, 7L, true)).thenReturn(false);

        assertThat(adapter.check(1, 7, AccessLevel.READ)).isEqualTo(AccessDecision.ALLOWED);
        assertThat(adapter.check(1, 7, AccessLevel.WRITE)).isEqualTo(AccessDecision.DENIED);
    }

    @Test
    void aMissingProjectIsNotFoundEvenForASupervisor() {
        when(projects.projectExists(1L)).thenReturn(false);
        when(access.allowed(anyString(), anyLong(), anyLong(), anyBoolean())).thenReturn(true);

        assertThat(adapter.check(1, 7, AccessLevel.READ)).isEqualTo(AccessDecision.PROJECT_NOT_FOUND);
        verify(access, never()).allowed(anyString(), anyLong(), anyLong(), anyBoolean());
    }

    @Test
    void anyFailureClosesAccess() {
        when(projects.projectExists(1L)).thenReturn(true);
        when(access.allowed(anyString(), anyLong(), anyLong(), anyBoolean()))
                .thenThrow(new IllegalStateException("circuit open"));

        assertThatThrownBy(() -> adapter.check(1, 7, AccessLevel.READ))
                .isInstanceOf(AccessUnavailableException.class);
    }
}
