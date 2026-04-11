package com.sakurain.gpuscheduler.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WebSocketEndpointIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Test
    void nativeStompWebSocketEndpointShouldAcceptConnections() throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());

        StompSession session = client.connectAsync(
                "ws://127.0.0.1:" + port + "/ws",
                new StompSessionHandlerAdapter() {
                }
        ).get(5, TimeUnit.SECONDS);

        try {
            assertThat(session.isConnected()).isTrue();
        } finally {
            session.disconnect();
            client.stop();
        }
    }

    @Test
    void sockJsStompEndpointShouldAcceptConnections() throws Exception {
        List<Transport> transports = List.of(new WebSocketTransport(new StandardWebSocketClient()));
        SockJsClient sockJsClient = new SockJsClient(transports);
        WebSocketStompClient client = new WebSocketStompClient(sockJsClient);
        client.setMessageConverter(new MappingJackson2MessageConverter());

        StompSession session = client.connectAsync(
                "http://127.0.0.1:" + port + "/ws",
                new StompSessionHandlerAdapter() {
                }
        ).get(5, TimeUnit.SECONDS);

        try {
            assertThat(session.isConnected()).isTrue();
        } finally {
            session.disconnect();
            client.stop();
            sockJsClient.stop();
        }
    }
}
