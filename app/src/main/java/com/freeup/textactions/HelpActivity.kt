package com.freeup.textactions

import android.graphics.Color
import android.os.Bundle
import com.google.android.material.color.DynamicColors
import android.view.View
import android.widget.ImageView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.transition.TransitionManager
import com.freeup.textactions.databinding.ActivityHelpBinding

/**
 * Help screen explaining how Text Actions works.
 * Accessed from Settings. Uses Material 3 with collapsible sections.
 */
class HelpActivity : BaseActivity() {

    private lateinit var binding: ActivityHelpBinding

    /** Expand/collapse animation duration in ms */
    private val expandDuration = 250L

    /** Pairs of (header, content) for each section */
    private val sections: List<Pair<View, View>> by lazy {
        listOf(
            binding.headerWhatIs to binding.contentWhatIs,
            binding.headerQuickStart to binding.contentQuickStart,
            binding.headerActions to binding.contentActions,
            binding.headerNotes to binding.contentNotes,
            binding.headerTips to binding.contentTips,
        )
    }

    /** Chevrons for rotation animation */
    private val chevrons: List<ImageView> by lazy {
        listOf(
            binding.chevronWhatIs,
            binding.chevronQuickStart,
            binding.chevronActions,
            binding.chevronNotes,
            binding.chevronTips,
        )
    }

    /** Track expanded state per section */
    private val expanded = BooleanArray(5) { false }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apply dynamic colors from wallpaper when enabled (matches Compose TextActionsTheme)
        if (SettingsManager(this).isDynamicColorEnabled()) {
            DynamicColors.applyToActivityIfAvailable(this)
        }

        // Enable edge-to-edge with transparent status bar
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        window.statusBarColor = Color.TRANSPARENT

        // Set window background to match other screens using dynamic colors
        setWindowBackgroundFromTheme()

        binding = ActivityHelpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Handle window insets for proper padding (status bar + navigation bar)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }

        setupToolbar()
        setupSections()
    }

    private fun setWindowBackgroundFromTheme() {
        // Get background color from Material3 theme
        val typedValue = android.util.TypedValue()
        val theme = theme

        // Try to get surfaceContainerHigh for light mode or background for dark mode
        // This matches what Theme.kt does in Compose screens
        if (theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainerHigh, typedValue, true)) {
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(typedValue.data))
        } else if (theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)) {
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(typedValue.data))
        }
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener { finish() }
    }

    private fun setupSections() {
        sections.forEachIndexed { index, (header, content) ->
            header.setOnClickListener {
                toggleSection(index, content, chevrons[index])
            }
        }
    }

    /**
     * Toggle section visibility with smooth expand/collapse animation.
     */
    private fun toggleSection(index: Int, content: View, chevron: ImageView) {
        val isExpanding = !expanded[index]
        expanded[index] = isExpanding

        // Animate chevron rotation (0° when collapsed, 90° when expanded)
        val targetRotation = if (isExpanding) 90f else 0f
        chevron.animate()
            .rotation(targetRotation)
            .setDuration(expandDuration)
            .start()

        // Use TransitionManager for smooth layout change
        val transition = androidx.transition.AutoTransition().apply { duration = expandDuration }
        TransitionManager.beginDelayedTransition(binding.root as android.view.ViewGroup, transition)
        content.visibility = if (isExpanding) View.VISIBLE else View.GONE
    }
}
