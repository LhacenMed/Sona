package com.lhacenmed.sona

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.datastore.UpdateSettings
import com.lhacenmed.sona.core.navigation.AppPrompts
import com.lhacenmed.sona.feature.feedback.CrashReportPrompt
import com.lhacenmed.sona.feature.feedback.CrashReports
import com.lhacenmed.sona.feature.update.ui.UpdateGate
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/** The prompts every activity raises: an update found, while its prompt is turned on, and the offer to report a crash. */
class SonaAppPrompts @Inject constructor(
    private val updateSettings: UpdateSettings,
    private val crashReports: CrashReports,
) : AppPrompts {

    @Composable
    override fun Content() {
        val isUpdateAutoPromptEnabled by remember { updateSettings.autoPrompt.flow }
            .collectAsStateWithLifecycle(updateSettings.autoPrompt.value)
        UpdateGate(isAutoPromptEnabled = isUpdateAutoPromptEnabled)
        CrashReportPrompt(crashReports)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppPromptsModule {
    @Binds
    abstract fun bindAppPrompts(prompts: SonaAppPrompts): AppPrompts
}
