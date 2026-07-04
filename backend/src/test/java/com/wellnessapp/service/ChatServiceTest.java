package com.wellnessapp.service;

import com.sun.net.httpserver.HttpServer;
import com.wellnessapp.dto.ChatDTOs.ChatRequest;
import com.wellnessapp.dto.ChatDTOs.ChatResponse;
import com.wellnessapp.entity.ChatMessage;
import com.wellnessapp.entity.User;
import com.wellnessapp.repository.ChatMessageRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Contract tests for the Python-agent-to-Spring chatbot response mapping.
 *
 * @author ZHAO LEI
 */
class ChatServiceTest {

    private HttpServer agentServer;
    private ChatMessageRepository chatMessageRepository;
    private WellnessInsightsService wellnessInsightsService;
    private AIClientService aiClientService;

    @BeforeEach
    void setUp() throws IOException {
        chatMessageRepository = mock(ChatMessageRepository.class);
        wellnessInsightsService = mock(WellnessInsightsService.class);
        aiClientService = mock(AIClientService.class);

        when(chatMessageRepository.findByUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(new ArrayList<>());
        when(chatMessageRepository.save(any(ChatMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(wellnessInsightsService.buildChatContext(any(User.class), anyInt()))
                .thenReturn("Personal wellness context for test.");

        agentServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        agentServer.createContext("/chat", exchange -> {
            byte[] body = """
                    {
                      "success": true,
                      "answer": "Grounded answer [1]",
                      "sources": [{
                        "rank": 1,
                        "title": "Sleep",
                        "section": "Overview",
                        "source_url": "https://example.test/sleep",
                        "score": 0.91
                      }],
                      "tool_calls": [{
                        "tool": "rag_search",
                        "result_summary": "one source"
                      }]
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        agentServer.start();
    }

    @AfterEach
    void tearDown() {
        if (agentServer != null) {
            agentServer.stop(0);
        }
    }

    @Test
    void processMessagePreservesSourcesAndToolCalls() {
        String agentUrl = "http://127.0.0.1:" + agentServer.getAddress().getPort();
        ChatService service = new ChatService(
                chatMessageRepository,
                aiClientService,
                wellnessInsightsService,
                agentUrl);
        User user = User.builder().id(1L).username("test-user").build();

        ChatResponse response = service.processMessage(
                user,
                ChatRequest.builder().message("How much sleep?").build());

        assertEquals("Grounded answer [1]", response.getReply());
        assertEquals(1, response.getSources().size());
        assertEquals("Overview", response.getSources().get(0).getSection());
        assertEquals("https://example.test/sleep", response.getSources().get(0).getSourceUrl());
        assertEquals(1, response.getToolCalls().size());
        assertEquals("rag_search", response.getToolCalls().get(0).get("tool"));
        assertNotNull(response.getTimestamp());
    }
}
