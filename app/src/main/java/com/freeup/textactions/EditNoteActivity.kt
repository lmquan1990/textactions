package com.freeup.textactions

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.freeup.textactions.data.local.db.AppDatabase
import com.freeup.textactions.data.repository.HomeRepository
import com.freeup.textactions.ui.theme.TextActionsTheme

class EditNoteActivity : BaseActivity() {

    companion object {
        const val EXTRA_NOTE_ID = "EXTRA_NOTE_ID"
    }

    private val viewModel: EditNoteViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val db = AppDatabase.getDatabase(applicationContext)
                val repository = HomeRepository(
                    applicationContext,
                    db.actionHistoryDao(),
                    db.noteDao(),
                    com.freeup.textactions.data.local.datastore.AiStatusDataStore(applicationContext)
                )
                return EditNoteViewModel(repository) as T
            }
        }
    }

    private val _isDeviceSecure = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val noteId = intent.getStringExtra(EXTRA_NOTE_ID)
        if (noteId.isNullOrBlank()) {
            finish()
            return
        }
        viewModel.load(noteId)
        _isDeviceSecure.value = isDeviceSecure()

        setContent {
            TextActionsTheme(transparentSystemBars = true) {
                val note by viewModel.note.collectAsState()
                var showConfirmDelete by remember { mutableStateOf(false) }
                if (note == null) {
                    return@TextActionsTheme
                }
                EditNoteScreen(
                    note = note!!,
                    isDeviceSecure = _isDeviceSecure.value,
                    onBack = { finish() },
                    onSave = { updated ->
                        viewModel.update(updated)
                        Toast.makeText(this, getString(R.string.saved_to_snippet), Toast.LENGTH_SHORT).show()
                        finish()
                    },
                    onArchive = {
                        if (note!!.isArchived) {
                            viewModel.unarchive(noteId)
                            Toast.makeText(this, getString(R.string.msg_note_unarchived), Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.archive(noteId)
                            Toast.makeText(this, getString(R.string.msg_note_archived), Toast.LENGTH_SHORT).show()
                        }
                        finish()
                    },
                    onDelete = {
                        if (note!!.isLocked) {
                            authenticateUser(
                                onSuccess = { showConfirmDelete = true },
                                onFailure = { /* no-op */ }
                            )
                        } else {
                            showConfirmDelete = true
                        }
                    },
                    onTogglePin = {
                        if (note!!.isPinned) {
                            viewModel.unpin(noteId)
                            Toast.makeText(this, getString(R.string.msg_note_unpinned), Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.pin(noteId)
                            Toast.makeText(this, getString(R.string.msg_note_pinned), Toast.LENGTH_SHORT).show()
                        }
                        finish()
                    },
                    onRequestLockAuth = { onSuccess ->
                        authenticateForLock(
                            onSuccess = onSuccess,
                            onFailure = { /* no-op, toggle stays off */ }
                        )
                    },
                    onOpenSecuritySettings = {
                        startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                    },
                    showConfirmDelete = showConfirmDelete,
                    onConfirmDelete = {
                        showConfirmDelete = false
                        viewModel.delete(noteId)
                        Toast.makeText(this, getString(R.string.msg_note_deleted), Toast.LENGTH_SHORT).show()
                        finish()
                    },
                    onDismissDelete = { showConfirmDelete = false }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        _isDeviceSecure.value = isDeviceSecure()
    }

    private fun isDeviceSecure(): Boolean {
        val keyguardManager = getSystemService(android.content.Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        return keyguardManager.isDeviceSecure
    }

    private fun authenticateUser(onSuccess: () -> Unit, onFailure: () -> Unit) {
        val executor = androidx.core.content.ContextCompat.getMainExecutor(this)
        val prompt = androidx.biometric.BiometricPrompt(
            this,
            executor,
            object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onFailure()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                }
            }
        )
        val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.unlock_note_title))
            .setSubtitle(getString(R.string.unlock_note_subtitle))
            .setAllowedAuthenticators(
                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or
                androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        prompt.authenticate(promptInfo)
    }

    private fun authenticateForLock(onSuccess: () -> Unit, onFailure: () -> Unit) {
        val executor = androidx.core.content.ContextCompat.getMainExecutor(this)
        val prompt = androidx.biometric.BiometricPrompt(
            this,
            executor,
            object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onFailure()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                }
            }
        )
        val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.lock_auth_title))
            .setSubtitle(getString(R.string.lock_auth_subtitle))
            .setAllowedAuthenticators(
                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or
                androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        prompt.authenticate(promptInfo)
    }
}
