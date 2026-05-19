package com.example.scenic_avatar_guide_app.data.repository

import com.example.scenic_avatar_guide_app.domain.model.ChatFeedbackData
import com.example.scenic_avatar_guide_app.domain.model.ChatFeedbackRequest
import com.example.scenic_avatar_guide_app.domain.model.ChatFeedbackResponse
import org.junit.Assert.*
import org.junit.Test

class FeedbackModelTest {

    @Test
    fun `ChatFeedbackRequest has correct default values`() {
        val request = ChatFeedbackRequest(
            scenicId = "1911museum",
            rating = 5
        )

        assertEquals("1911museum", request.scenicId)
        assertEquals(5, request.rating)
        assertNull(request.sessionId)
        assertNull(request.userId)
        assertNull(request.messageId)
        assertFalse(request.isComplaint)
        assertNull(request.comment)
    }

    @Test
    fun `ChatFeedbackRequest with all fields populated`() {
        val request = ChatFeedbackRequest(
            scenicId = "1911museum",
            rating = 3,
            sessionId = "s_001",
            userId = "u_001",
            messageId = "m_001",
            isComplaint = true,
            comment = "服务态度一般"
        )

        assertEquals("1911museum", request.scenicId)
        assertEquals(3, request.rating)
        assertEquals("s_001", request.sessionId)
        assertEquals("u_001", request.userId)
        assertEquals("m_001", request.messageId)
        assertTrue(request.isComplaint)
        assertEquals("服务态度一般", request.comment)
    }

    @Test
    fun `ChatFeedbackResponse parses correctly`() {
        val response = ChatFeedbackResponse(
            code = 0,
            message = "ok",
            data = ChatFeedbackData(
                feedbackId = "fb_001",
                scenicId = "1911museum",
                sessionId = "s_001",
                userId = "u_001",
                messageId = "m_001",
                rating = 5,
                isComplaint = false,
                comment = "非常好",
                createdAt = "2026-05-04T12:00:00Z"
            )
        )

        assertEquals(0, response.code)
        assertEquals("ok", response.message)
        assertNotNull(response.data)
        assertEquals("fb_001", response.data?.feedbackId)
        assertEquals(5, response.data?.rating)
        assertFalse(response.data?.isComplaint!!)
    }

    @Test
    fun `ChatFeedbackResponse error case`() {
        val response = ChatFeedbackResponse(
            code = 1002,
            message = "参数格式错误",
            data = null
        )

        assertEquals(1002, response.code)
        assertEquals("参数格式错误", response.message)
        assertNull(response.data)
    }

    @Test
    fun `ChatFeedbackData with complaint`() {
        val data = ChatFeedbackData(
            feedbackId = "fb_002",
            scenicId = "1911museum",
            rating = 1,
            isComplaint = true,
            comment = "服务态度差"
        )

        assertEquals("fb_002", data.feedbackId)
        assertEquals(1, data.rating)
        assertTrue(data.isComplaint)
        assertEquals("服务态度差", data.comment)
        assertNull(data.sessionId)
        assertNull(data.createdAt)
    }
}
