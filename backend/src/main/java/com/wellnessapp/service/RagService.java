/**
 * @author Tao Yuchen
 */
package com.wellnessapp.service;

import com.wellnessapp.dto.RagDTOs.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Service that proxies RAG requests to the Python agent service.
 * The Python agent hosts the DeepSeek + Doubao RAG pipeline at /rag/* endpoints.
 *
 * @author WellnessApp Team
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private final RestTemplate restTemplate;
    private final String agentBaseUrl;

    public RagService(
            @Value("${agent.python.base-url:http://localhost:5001}") String agentBaseUrl) {
        this.restTemplate = new RestTemplate();
        this.agentBaseUrl = agentBaseUrl;
    }

    /**
     * Get RAG index status from the Python agent.
     */
    @SuppressWarnings("unchecked")
    public RagStatusResponse getStatus() {
        String url = agentBaseUrl + "/rag/status";
        log.info("Fetching RAG status from: {}", url);

        ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            Map body = response.getBody();
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            if (data != null) {
                return RagStatusResponse.builder()
                        .indexPath((String) data.get("indexPath"))
                        .builtAt((String) data.get("builtAt"))
                        .documentCount(data.get("documentCount") != null
                                ? ((Number) data.get("documentCount")).intValue() : 0)
                        .sourceCount(data.get("sourceCount") != null
                                ? ((Number) data.get("sourceCount")).intValue() : 0)
                        .corpusGlob((String) data.get("corpusGlob"))
                        .deepseekModel((String) data.get("deepseekModel"))
                        .doubaoModel((String) data.get("doubaoModel"))
                        .build();
            }
        }
        throw new RuntimeException("Failed to fetch RAG status");
    }

    /**
     * Start an async reindex task.
     */
    @SuppressWarnings("unchecked")
    public RagReindexResponse startReindex(boolean force) {
        String url = agentBaseUrl + "/rag/reindex";
        log.info("Starting RAG reindex (force={}) at: {}", force, url);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(
                Map.of("force", force), headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            Map body = response.getBody();
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            if (data != null) {
                return RagReindexResponse.builder()
                        .taskId((String) data.get("taskId"))
                        .status((String) data.get("status"))
                        .build();
            }
        }
        throw new RuntimeException("Failed to start RAG reindex");
    }

    /**
     * Poll the status of a reindex task.
     */
    @SuppressWarnings("unchecked")
    public RagReindexStatusResponse getReindexStatus(String taskId) {
        String url = agentBaseUrl + "/rag/reindex-status/" + taskId;
        log.info("Polling RAG reindex status: {}", url);

        ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            Map body = response.getBody();
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            if (data != null) {
                return RagReindexStatusResponse.builder()
                        .taskId((String) data.get("taskId"))
                        .status((String) data.get("status"))
                        .progress(data.get("progress") != null
                                ? ((Number) data.get("progress")).intValue() : 0)
                        .phase((String) data.get("phase"))
                        .message((String) data.get("message"))
                        .error((String) data.get("error"))
                        .result((Map<String, Object>) data.get("result"))
                        .startedAt((String) data.get("startedAt"))
                        .completedAt((String) data.get("completedAt"))
                        .build();
            }
        }
        throw new RuntimeException("Reindex task " + taskId + " not found");
    }

    /**
     * Ask a question against the RAG index.
     */
    @SuppressWarnings("unchecked")
    public RagAskResponse ask(String question, Integer topK) {
        String url = agentBaseUrl + "/rag/ask";
        log.info("Asking RAG question (topK={}) at: {}", topK, url);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBody;
        if (topK != null) {
            requestBody = Map.of("question", question, "topK", topK);
        } else {
            requestBody = Map.of("question", question);
        }

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            Map body = response.getBody();
            boolean success = Boolean.TRUE.equals(body.get("success"));
            String answer = (String) body.get("answer");
            java.util.List<Map<String, Object>> sourcesRaw =
                    (java.util.List<Map<String, Object>>) body.get("sources");

            java.util.List<RagSource> sources = sourcesRaw != null
                    ? sourcesRaw.stream().map(s -> RagSource.builder()
                        .rank(((Number) s.get("rank")).intValue())
                        .score(((Number) s.get("score")).doubleValue())
                        .title((String) s.get("title"))
                        .sectionTitle((String) s.get("section_title"))
                        .chunkId(((Number) s.get("chunk_id")).intValue())
                        .sourceUrl((String) s.get("source_url"))
                        .snippet((String) s.get("snippet"))
                        .build()).toList()
                    : java.util.List.of();

            return RagAskResponse.builder()
                    .success(success)
                    .answer(answer)
                    .sources(sources)
                    .build();
        }
        throw new RuntimeException("RAG ask request failed");
    }
}
