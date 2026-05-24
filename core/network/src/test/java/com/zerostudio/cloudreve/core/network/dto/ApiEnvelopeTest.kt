package com.zerostudio.cloudreve.core.network.dto

import com.zerostudio.cloudreve.core.common.CloudreveException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ApiEnvelopeTest {
    @Test
    fun unwrapReturnsDataWhenCodeIsZero() {
        val value = ApiEnvelope(code = 0, data = "ok").unwrap()

        assertEquals("ok", value)
    }

    @Test
    fun unwrapTurnsApiCodeIntoDomainException() {
        val exception = assertThrows(CloudreveException.Api::class.java) {
            ApiEnvelope<String>(code = -1, msg = "invalid file uri").unwrap()
        }

        assertEquals(-1, exception.code)
        assertEquals("invalid file uri", exception.message)
    }

    @Test
    fun unwrapUsesMessageFieldWhenMsgIsEmpty() {
        val exception = assertThrows(CloudreveException.Unauthorized::class.java) {
            ApiEnvelope<String>(code = -1, message = "authentication required").unwrap()
        }

        assertEquals("authentication required", exception.message)
    }

    @Test
    fun unwrapTurnsCloudreveAuthCodeIntoUnauthorizedException() {
        val exception = assertThrows(CloudreveException.Unauthorized::class.java) {
            ApiEnvelope<String>(code = 401, msg = "login required").unwrap()
        }

        assertEquals("login required", exception.message)
    }

    @Test
    fun unwrapRejectsEmptyDataForNonUnitResponses() {
        val exception = assertThrows(CloudreveException.Api::class.java) {
            ApiEnvelope<String>(code = 0, data = null).unwrap()
        }

        assertEquals("Cloudreve response data is empty", exception.message)
    }

    @Test
    fun unwrapUnitAllowsEmptySuccessData() {
        ApiEnvelope<Unit?>(code = 0, data = null).unwrapUnit()
    }
}
