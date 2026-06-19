package com.incalr26.botgram.util

import android.text.SpannableStringBuilder
import android.text.style.*
import org.json.JSONArray

object MessageFormatter {

    fun format(text: String, entitiesJson: String?): SpannableStringBuilder {
        val spannable = SpannableStringBuilder(text)
        if (entitiesJson.isNullOrEmpty()) return spannable

        try {
            val entities = JSONArray(entitiesJson)
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val offset = entity.getInt("offset")
                val length = entity.getInt("length")
                val type = entity.getString("type")
                val end = (offset + length).coerceAtMost(text.length)
                if (offset >= text.length || end <= offset) continue

                when (type) {
                    "bold" -> spannable.setSpan(StyleSpan(android.graphics.Typeface.BOLD), offset, end, 0)
                    "italic" -> spannable.setSpan(StyleSpan(android.graphics.Typeface.ITALIC), offset, end, 0)
                    "underline" -> spannable.setSpan(UnderlineSpan(), offset, end, 0)
                    "strikethrough" -> spannable.setSpan(StrikethroughSpan(), offset, end, 0)
                    "spoiler" -> spannable.setSpan(BackgroundColorSpan(0x88000000.toInt()), offset, end, 0)
                    "code" -> spannable.setSpan(TypefaceSpan("monospace"), offset, end, 0)
                    "pre" -> {
                        spannable.setSpan(TypefaceSpan("monospace"), offset, end, 0)
                        spannable.setSpan(BackgroundColorSpan(0x11000000), offset, end, 0)
                    }
                    "text_link" -> {
                        val url = entity.optString("url", null)
                        if (url != null) spannable.setSpan(URLSpan(url), offset, end, 0)
                    }
                    "url" -> {
                        val url = text.substring(offset, end)
                        spannable.setSpan(URLSpan(url), offset, end, 0)
                    }
                    "mention" -> spannable.setSpan(UnderlineSpan(), offset, end, 0)
                    "text_mention" -> spannable.setSpan(StyleSpan(android.graphics.Typeface.BOLD), offset, end, 0)
                    "blockquote" -> {
                        spannable.setSpan(QuoteSpan(0x33666666), offset, end, 0)
                        spannable.setSpan(StyleSpan(android.graphics.Typeface.ITALIC), offset, end, 0)
                    }
                    "expandable_blockquote" -> {
                        spannable.setSpan(QuoteSpan(0x33666666), offset, end, 0)
                    }
                }
            }
        } catch (_: Exception) {}
        return spannable
    }
}
