package com.freeup.textactions

object ActionRegistry {
    private val allActions = listOf(
        CallAction(),
        SmsAction(),
        EmailAction(),
        OpenLinkAction(),
        MapAction(),
        CopyAddressAction(),
        SearchAction(),
        SummarizeAction(),
        QuickShareAction(),
        TranslateAction(),
        CopyAction(),
        TtsAction(),
        SmartReadAloudAction(),
        CalendarAction(),
        ReminderAction(),
        ReaderModeAction(),
        ShortenLinkAction(),
        SaveContactAction(),
        SaveNoteAction(),
        FormatTextAction(),
        ZaloAction(),
        DirectionAction(),
        RideHailingAction(),
        GPSMapAction(),
        CreateQRAction()
    )

    fun getApplicableActions(context: android.content.Context, text: String): List<ActionInstance> {
        val detections = TextAnalyzer().analyze(text)
        val instances = mutableListOf<ActionInstance>()
        val bestAddressDetection = selectBestAddressDetection(detections)
        
        // Define global/utility actions
        val utilityActionIds = setOf(
            "copy",
            "quick_share",
            "search",
            "summarize",
            "translate",
            "tts",
            "smart_read_aloud",
            "save_note",
            "format_text",
            "create_qr"
        )
        
        val shouldShowCalendar = shouldShowCalendarAction(text, detections)
        val shouldShowReminder = shouldShowReminderAction(text, detections) && !shouldShowCalendar

        val settingsManager = SettingsManager(context)
        val recommendedOrder = settingsManager.getRecommendedActionOrder()
        val timeOrder = settingsManager.getTimeActionOrder()
        val utilityOrder = settingsManager.getUtilityActionOrder()
        val aiOrder = settingsManager.getAIActionOrder()
        // Gộp recommended + time để sort contextual actions (call, sms, map... + calendar, reminder)
        val contextualOrder = recommendedOrder + timeOrder

        // Helper to sort actions based on saved order
        fun sortActions(actions: List<TextAction>, orderList: List<String>): List<TextAction> {
            if (orderList.isEmpty()) return actions.sortedByDescending { it.priority }
            
            return actions.sortedWith(
                Comparator { a, b ->
                    val indexA = orderList.indexOf(a.id)
                    val indexB = orderList.indexOf(b.id)
                    
                    when {
                        indexA != -1 && indexB != -1 -> indexA.compareTo(indexB)
                        indexA != -1 -> -1 // A is in list -> A first
                        indexB != -1 -> 1  // B is in list -> B first
                        else -> b.priority.compareTo(a.priority) // Both not in list -> Fallback to priority
                    }
                }
            )
        }

        // Phone detection: có số điện thoại hợp lệ là đủ để hiển thị Call/SMS
        val hasPhonePrimary = detections.any { d ->
            d.type == TextType.PHONE_NUMBER && PhoneClassifier.detectPhone(d.value).isPhoneNumber
        }

        // 1. Recommended (Contextual per entity)
        for (detection in detections) {
            if (detection.type == TextType.ADDRESS && detection != bestAddressDetection) {
                continue
            }
            val phoneResult = if (detection.type == TextType.PHONE_NUMBER) {
                PhoneClassifier.detectPhone(detection.value)
            } else null

            val contextualActions = allActions
                .filter { action ->
                    if (!action.isEnabled(context) || detection.type !in action.supportedTextTypes || action.id in utilityActionIds) {
                        return@filter false
                    }
                    if (action.id == "calendar" && !shouldShowCalendar) return@filter false
                    if (action.id == "reminder" && !shouldShowReminder) return@filter false
                    // Call, SMS: hiển thị khi có số điện thoại hợp lệ (kể cả landline)
                    true
                }
                .let { sortActions(it, contextualOrder) }
            
            // For phone numbers, show more actions (Call, SMS, Save Contact, etc.)
            val maxActions = if (detection.type == TextType.PHONE_NUMBER) 5 else 3
            var selectedActions = contextualActions.take(maxActions)
            
            // Ensure SMS is included for phone numbers if enabled
            if (detection.type == TextType.PHONE_NUMBER && phoneResult != null && phoneResult.isPhoneNumber) {
                val smsAction = contextualActions.firstOrNull { it.id == "sms" }
                if (smsAction != null && !selectedActions.contains(smsAction)) {
                    selectedActions = selectedActions.dropLast(1) + smsAction
                }
            }
            
            for (action in selectedActions) {
                instances.add(ActionInstance(action, detection.value))
            }
        }
        
        // 2. Utilities (Global)
        val hasDirectActionMatch = detections.any {
            it.type == TextType.PHONE_NUMBER ||
                it.type == TextType.EMAIL ||
                it.type == TextType.URL ||
                it.type == TextType.ADDRESS
        }

        val utilities = allActions
            .filter { action ->
                if (!action.isEnabled(context) || action.id !in utilityActionIds) return@filter false
                if (action.id == "search" && !shouldShowSearchAction(text, hasDirectActionMatch)) {
                    return@filter false
                }
                if (action.id == "summarize" && !shouldShowSummarizeAction(text, detections)) {
                    return@filter false
                }
                // Temporarily hidden: Read Aloud in bottom sheet
                if (action.id == "tts") return@filter false
                // Temporarily hidden: Smart Read Aloud in bottom sheet
                if (action.id == "smart_read_aloud") return@filter false
                if (action.id == "create_qr" && !shouldShowCreateQRAction(text, detections)) {
                    return@filter false
                }
                if (action.id == "format_text") {
                    val trimmed = text.trim()
                    if (trimmed.length < 3) return@filter false
                    val isSingleEntity = detections.size == 1 && detections.first().value.trim() == trimmed
                    val type = detections.firstOrNull()?.type
                    if (isSingleEntity && (type == TextType.URL || type == TextType.EMAIL || type == TextType.PHONE_NUMBER)) {
                        return@filter false
                    }
                }
                true
            }
            .let { 
                // Utilities can be mixed with AI actions in the UI, but here we separate by ID set.
                // However, SettingsManager has separate orders. We need to check which list to use.
                // Ideally, we combine utility + AI orders if we can't distinguish easily here.
                // But wait, ActionRegistry defines utilityActionIds. Let's see if any are AI.
                // "summarize", "translate", "smart_read_aloud" -> These might be in AI category in ViewModel.
                
                it.sortedWith(
                    Comparator { a, b ->
                        val combinedOrder = utilityOrder + aiOrder
                        
                        val indexA = combinedOrder.indexOf(a.id)
                        val indexB = combinedOrder.indexOf(b.id)
                        
                        when {
                            indexA != -1 && indexB != -1 -> indexA.compareTo(indexB)
                            indexA != -1 -> -1
                            indexB != -1 -> 1
                            else -> b.priority.compareTo(a.priority)
                        }
                    }
                )
            }
            
        for (action in utilities) {
            instances.add(ActionInstance(action, text))
        }

        // Final deduplication (by Action ID and Value) while preserving order
        val seen = mutableSetOf<String>()
        return instances.filter { seen.add("${it.action.id}:${it.boundText}") }
    }

    /** Re-run action from history: tìm action tương ứng và execute; trả về true nếu chạy được, false nếu không (show toast). */
    fun runActionFromHistory(context: android.content.Context, action: com.freeup.textactions.domain.model.ActionHistory): Boolean {
        val text = action.inputText
        if (text.isBlank()) return false
        val actionId = when (action.actionType.uppercase()) {
            "URL" -> "open_link"
            else -> action.actionType.lowercase()
        }
        val instances = getApplicableActions(context, text)
        val match = instances.find { it.action.id == actionId }
        return if (match != null) {
            try {
                match.action.execute(context, match.boundText)
                true
            } catch (_: Exception) {
                false
            }
        } else {
            false
        }
    }
}

private fun selectBestAddressDetection(detections: List<Detection>): Detection? {
    val addressDetections = detections.filter { it.type == TextType.ADDRESS }
    if (addressDetections.isEmpty()) return null

    val components = listOf(
        "đường", "phố", "ngõ", "ngách", "thôn", "xóm", "phường", "xã",
        "quận", "huyện", "tỉnh", "thành phố", "số", "tòa", "tầng"
    )

    return addressDetections.maxByOrNull { detection ->
        val lower = detection.value.lowercase()
        val componentCount = components.count { lower.contains(it) }
        componentCount * 10 + detection.value.length
    }
}
