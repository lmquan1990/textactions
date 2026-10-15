package com.freeup.textactions.domain

data class SmartReadAloudResult(val sentences: List<String>, val text: String)

class SmartReadAloudUseCase {

    fun execute(text: String): SmartReadAloudResult {
        val cleaned = text.trim()
        val baseSentences = splitSentences(cleaned)
        val fallbackSentence = baseSentences.firstOrNull()?.trim().orEmpty()

        val filteredText = filterContent(cleaned)
        val sentences = splitSentences(filteredText)
        val filteredSentences = filterSentences(sentences)

        val candidates = if (filteredSentences.isNotEmpty()) filteredSentences else {
            if (fallbackSentence.isNotBlank()) listOf(fallbackSentence) else emptyList()
        }

        if (candidates.isEmpty()) {
            return SmartReadAloudResult(emptyList(), "")
        }

        // Giữ toàn bộ nội dung chính (đã lọc tag/metadata/râu ria), không giới hạn số câu
        val resultSentences = candidates.map { it.trim() }
        val resultText = resultSentences.joinToString(" ").trim()
        return SmartReadAloudResult(resultSentences, resultText)
    }

    private fun filterContent(text: String): String {
        val lines = text.split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val filteredLines = lines.filterNot { line ->
            isMetadataLine(line) || containsContactInfo(line)
        }

        return filteredLines.joinToString(" ")
    }

    private fun filterSentences(sentences: List<String>): List<String> {
        val seen = mutableSetOf<String>()
        return sentences.map { it.trim() }
            .filter { it.isNotBlank() }
            .filter { wordCount(it) >= 6 }
            .filterNot { containsContactInfo(it) }
            .filterNot { isMetadataLine(it) }
            .filter { hasVerb(it) }
            .filter { sentence ->
                val normalized = normalize(sentence)
                if (normalized.isBlank() || normalized in seen) {
                    false
                } else {
                    seen.add(normalized)
                    true
                }
            }
    }

    private fun isMetadataLine(line: String): Boolean {
        val lower = line.lowercase()
        val tokens = listOf(
            "©", "copyright", "all rights reserved", "privacy",
            "hotline", "fax", "email", "website", "web", "www",
            "liên hệ", "bản quyền", "điều khoản", "chính sách",
            "ý kiến", "chia sẻ ý kiến", "chuyên mục", "quảng cáo",
            "xem thêm", "phản hồi tới", "đăng nhập để", "tải ứng dụng",
            "đường dây nóng", "gửi tòa soạn", "báo điện tử", "giấy phép",
            "tổng biên tập", "toàn bộ bản quyền", "trang chủ",
            "quan tâm nhất", "mới nhất", "tags:", "theo dõi", "cookies"
        )
        return tokens.any { lower.contains(it) }
    }

    private fun containsContactInfo(text: String): Boolean {
        return phoneRegex.containsMatchIn(text) ||
            emailRegex.containsMatchIn(text) ||
            urlRegex.containsMatchIn(text) ||
            addressRegex.containsMatchIn(text)
    }

    private fun hasVerb(text: String): Boolean {
        val lower = text.lowercase()
        return verbRegex.containsMatchIn(lower)
    }

    private fun wordCount(text: String): Int {
        return tokenizeAllWords(text).size
    }

    private fun normalize(text: String): String {
        return text.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun tokenizeAllWords(text: String): List<String> {
        return wordRegex.findAll(text.lowercase())
            .map { it.value }
            .toList()
    }

    private companion object {
        private val wordRegex = Regex("[\\p{L}\\p{N}]+")
        private val phoneRegex = Regex("(?:\\(?\\+?84\\)?|0)\\s?(?:\\d[\\s.\\-]?){8,10}\\d(?![\\d])")
        private val emailRegex = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
        private val urlRegex = Regex("https?://|www\\.", RegexOption.IGNORE_CASE)
        private val addressRegex = Regex(
            "(đường|phố|ngõ|ngách|phường|quận|huyện|tỉnh|thành phố|street|st\\.|ave|road)",
            RegexOption.IGNORE_CASE
        )
        private val verbRegex = Regex(
            "\\b(is|are|was|were|be|been|being|have|has|had|do|does|did|will|can|could|should|would|may|might|must|need|needs|needed|make|made|go|goes|went|say|says|said|work|works|worked|là|có|đang|đã|sẽ|được|bị|phải|cần|nên|muốn|thích|gồm|bao\\s+gồm|thông\\s+báo|yêu\\s+cầu|tin|giữ|từng|đạt|gửi|đánh\\s+giá|cho\\s+biết|công\\s+bố|thiết\\s+lập|thể\\s+hiện|phát\\s+triển|trở\\s+thành|mong\\s+muốn|tin\\s+rằng|giữ\\s+chức|từng\\s+là|bao\\s+trùm)\\b",
            RegexOption.IGNORE_CASE
        )
    }
}
