package org.awesoma.report.adapters.out.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.awesoma.report.domain.ReportFormat;
import org.junit.jupiter.api.Test;

class FilesystemReportWorkspaceTest {
    @Test
    void writesReadsAndRemovesTheTemporaryArtifact() throws Exception {
        var workspace = new FilesystemReportWorkspace();
        var artifact = workspace.create(UUID.randomUUID(), ReportFormat.CSV);
        try (var output = artifact.write()) {
            output.write("данные".getBytes(StandardCharsets.UTF_8));
        }
        assertThat(artifact.size()).isEqualTo("данные".getBytes(StandardCharsets.UTF_8).length);
        try (var input = artifact.read()) {
            assertThat(input.readAllBytes()).isEqualTo("данные".getBytes(StandardCharsets.UTF_8));
        }
        artifact.close();
        assertThatThrownBy(artifact::read).isInstanceOf(java.io.IOException.class);
    }
}
