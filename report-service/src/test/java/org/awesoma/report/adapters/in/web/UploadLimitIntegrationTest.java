package org.awesoma.report.adapters.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import org.awesoma.report.support.AbstractIntegrationTest;
import org.awesoma.security.jwt.JwtTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** The limit is read in binary units: 1KB is 1024 bytes, and one byte more is refused. */
@AutoConfigureMockMvc
@TestPropertySource(properties = "report.max-file-size=1KB")
class UploadLimitIntegrationTest extends AbstractIntegrationTest {
    private static final long PROJECT = 77;
    private static final long OPERATOR = 78;

    @Autowired private MockMvc http;
    @Autowired private JwtTokens tokens;

    @Test
    void acceptsAFileOfExactlyTheLimitAndRefusesOneByteMore() throws Exception {
        grantWrite(PROJECT, OPERATOR);

        upload(csvOfSize(1024)).andExpect(status().isCreated());
        upload(csvOfSize(1025))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.detail").value("File is larger than 1024 bytes"));
    }

    private ResultActions upload(MockMultipartFile file) throws Exception {
        return http.perform(multipart("/api/v1/projects/" + PROJECT + "/reports").file(file)
                .header("Authorization", "Bearer " + tokens.issue(Long.toString(OPERATOR), "OPERATOR")
                        .getTokenValue()));
    }

    private static MockMultipartFile csvOfSize(int size) {
        byte[] content = new byte[size];
        Arrays.fill(content, (byte) 'a');
        return new MockMultipartFile("file", "big.csv", "text/csv", content);
    }
}
