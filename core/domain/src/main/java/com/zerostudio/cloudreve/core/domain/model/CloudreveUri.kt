package com.zerostudio.cloudreve.core.domain.model

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@JvmInline
value class CloudreveUri(val value: String) {
    val canonicalValue: String
        get() = normalize(value)

    fun child(name: String): CloudreveUri {
        val cleanBase = canonicalValue
        val cleanName = name.trim('/')
        require(cleanName.isNotBlank()) { "Cloudreve URI child name is blank" }
        val encodedName = cleanName.encodePathSegment()
        return if (cleanBase == Root.value) {
            CloudreveUri("${Root.value}$encodedName")
        } else {
            CloudreveUri("${cleanBase.trimEnd('/')}/$encodedName")
        }
    }

    fun parent(): CloudreveUri {
        val cleanValue = canonicalValue
        if (cleanValue == Root.value) return Root
        val parent = cleanValue
            .trimEnd('/')
            .substringBeforeLast('/', missingDelimiterValue = Root.value)
        return parse(parent)
    }

    companion object {
        val Root = CloudreveUri("cloudreve://my/")

        fun parse(value: String): CloudreveUri = CloudreveUri(normalize(value))

        private fun normalize(value: String): String {
            val trimmed = value.trim()
            if (
                trimmed.isBlank() ||
                trimmed == "cloudreve:" ||
                trimmed == "cloudreve:/" ||
                trimmed == "cloudreve://" ||
                trimmed == "cloudreve://my"
            ) {
                return Root.value
            }
            if (!trimmed.startsWith(Root.value)) {
                return trimmed.trimEnd('/')
            }
            val path = trimmed
                .removePrefix(Root.value)
                .trim('/')
                .replace(Regex("/{2,}"), "/")
            return if (path.isBlank()) Root.value else "${Root.value}$path"
        }
    }
}

private fun String.encodePathSegment(): String =
    URLEncoder.encode(this, StandardCharsets.UTF_8.name())
        .replace("+", "%20")
