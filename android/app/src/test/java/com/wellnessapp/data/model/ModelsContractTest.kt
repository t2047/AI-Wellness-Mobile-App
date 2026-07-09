/**
 * @author Zhao Lei
 */
package com.wellnessapp.data.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Verifies the Android models against the Spring backend's camelCase JSON API.
 *
 * @author ZHAO LEI
 */
class ModelsContractTest {

    private val gson = Gson()

    @Test
    fun ragSourceUsesSpringCamelCaseFields() {
        val payload = """
            {
              "success": true,
              "answer": "Answer",
              "sources": [{
                "rank": 1,
                "score": 0.92,
                "title": "Sleep",
                "sectionTitle": "Overview",
                "chunkId": 7,
                "sourceUrl": "https://example.test/sleep",
                "snippet": "Evidence"
              }]
            }
        """.trimIndent()

        val response = gson.fromJson(payload, RagAskResponse::class.java)
        val source = response.sources?.single()

        assertNotNull(source)
        assertEquals("Overview", source!!.sectionTitle)
        assertEquals(7, source.chunkId)
        assertEquals("https://example.test/sleep", source.sourceUrl)
    }

    @Test
    fun chatResponsePreservesSourcesAndToolCalls() {
        val payload = """
            {
              "reply": "Grounded answer [1]",
              "timestamp": "2026-07-04T11:00:00",
              "sources": [{
                "rank": 1,
                "title": "Sleep",
                "section": "Overview",
                "sourceUrl": "https://example.test/sleep",
                "score": 0.91
              }],
              "toolCalls": [{
                "tool": "rag_search"
              }]
            }
        """.trimIndent()

        val response = gson.fromJson(payload, ChatResponse::class.java)

        assertEquals("Overview", response.sources.single().section)
        assertEquals("rag_search", response.toolCalls.single()["tool"])
    }
}
