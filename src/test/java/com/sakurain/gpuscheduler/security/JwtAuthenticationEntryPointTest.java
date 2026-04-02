package com.sakurain.gpuscheduler.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JwtAuthenticationEntryPointTest {

    @Test
    void commence_shouldReturnUnifiedResultBody() throws Exception {
        JwtAuthenticationEntryPoint entryPoint = new JwtAuthenticationEntryPoint();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("bad credentials"));

        assertEquals(401, response.getStatus());
        JsonNode body = new ObjectMapper().readTree(response.getContentAsString());
        assertEquals(401, body.get("code").asInt());
        assertEquals("AUTH_UNAUTHORIZED", body.get("errorCode").asText());
        assertEquals("未授权访问，请先登录", body.get("message").asText());
    }
}
