package com.sakurain.gpuscheduler.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebSocketSecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void sockJsInfoEndpointShouldBePubliclyAccessible() throws Exception {
        mockMvc.perform(get("/ws/info"))
                .andExpect(status().isOk());
    }

    @Test
    void sockJsTransportEndpointShouldNotBeRejectedBySecurityFilters() throws Exception {
        MvcResult result = mockMvc.perform(get("/ws/123/test-session/websocket"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
    }
}
