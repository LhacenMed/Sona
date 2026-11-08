package com.lhacenmed.sona.feature.feedback

import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lhacenmed.sona.core.designsystem.component.LocalBottomContentPadding
import com.lhacenmed.sona.core.designsystem.component.SonaIconButton
import com.lhacenmed.sona.core.designsystem.component.SonaSettingsRow
import com.lhacenmed.sona.core.designsystem.component.fastscroll.FastScroller
import com.lhacenmed.sona.core.designsystem.component.toast
import com.lhacenmed.sona.core.designsystem.theme.buttonPressShapes
import com.lhacenmed.sona.core.navigation.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A picked screenshot or recording, as small as it is shown. */
private val ThumbnailSize = 88.dp
private val ThumbnailShape = RoundedCornerShape(12.dp)

/**
 * Feedback the way anyone would give it: say what is wrong, or what would be better, in a sentence of one's own -
 * nothing to sort it under and no form to work through - add a screenshot or a recording if it helps, and send. The
 * phone's details and the app's log go with it by themselves, to find the problem with, unless left out.
 *
 * Sent by email, which carries the pictures and needs no account. Posting it on GitHub instead opens the app's issue
 * form there, filled in, for those who would rather.
 *
 * [crash] is the trace of the crash Sona last closed on - see [CrashReports] - for a report about it, first in its log.
 */
data class FeedbackScreen(val crash: String? = null) : Screen {
    override val titleRes: Int get() = R.string.feedback_title

    @Composable
    override fun Content() {
        val viewModel: FeedbackViewModel = hiltViewModel()
        LaunchedEffect(viewModel) { viewModel.start(crash) }
        val context = LocalContext.current
        val uriHandler = LocalUriHandler.current
        val scrollState = rememberScrollState()
        val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_ATTACHMENTS)) {
            viewModel.attach(it)
        }

        // Above the keyboard, so the field it opens for is scrolled into view rather than the window slid.
        FastScroller(scrollState, Modifier.fillMaxSize().imePadding()) { overscrollEffect ->
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState, overscrollEffect = overscrollEffect)
                    .padding(top = 8.dp, bottom = LocalBottomContentPadding.current + 16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = stringResource(R.string.feedback_intro),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = viewModel.message,
                        onValueChange = { viewModel.message = it },
                        placeholder = { Text(stringResource(R.string.feedback_message_hint)) },
                        minLines = 6,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (viewModel.attachments.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            viewModel.attachments.forEach { uri ->
                                AttachmentThumbnail(uri = uri, onRemove = { viewModel.attachments.remove(uri) })
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                        shapes = buttonPressShapes(),
                        enabled = viewModel.attachments.size < MAX_ATTACHMENTS,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Text(stringResource(R.string.feedback_add_media), modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
                    }
                }

                SonaSettingsRow(
                    title = stringResource(R.string.feedback_include_diagnostics),
                    summary = viewModel.diagnostics?.let { stringResource(R.string.feedback_include_diagnostics_summary, it.summary) },
                    onClick = { viewModel.includesDiagnostics = !viewModel.includesDiagnostics },
                    trailing = { Switch(checked = viewModel.includesDiagnostics, onCheckedChange = { viewModel.includesDiagnostics = it }) },
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    Button(
                        onClick = {
                            viewModel.email { email, share ->
                                runCatching { context.startActivity(email) }
                                    .recoverCatching { context.startActivity(share) }
                                    .onFailure { context.toast(context.getString(R.string.feedback_no_email_app)) }
                            }
                        },
                        shapes = buttonPressShapes(),
                        enabled = viewModel.canSend,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Text(stringResource(R.string.feedback_send), modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
                    }
                    TextButton(
                        onClick = { runCatching { uriHandler.openUri(viewModel.issueUrl().toString()) } },
                        shapes = buttonPressShapes(),
                        enabled = viewModel.canSend,
                    ) { Text(stringResource(R.string.feedback_post_on_github)) }
                    Text(
                        text = stringResource(R.string.feedback_github_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** A picked screenshot or recording, small, with the way to take it back out. */
@Composable
private fun AttachmentThumbnail(uri: Uri, onRemove: () -> Unit) {
    val context = LocalContext.current
    // Android's own preview of it, a frame for a recording - before Android 10 there is none to ask for.
    val thumbnail by produceState<ImageBitmap?>(null, uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            value = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.loadThumbnail(uri, Size(256, 256), null).asImageBitmap() }.getOrNull()
            }
        }
    }
    Box(
        modifier = Modifier
            .size(ThumbnailSize)
            .clip(ThumbnailShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        thumbnail?.let { Image(bitmap = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            ?: Icon(Icons.Filled.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        SonaIconButton(
            onClick = onRemove,
            icon = Icons.Filled.Close,
            contentDescription = stringResource(R.string.feedback_remove_media),
            colors = IconButtonDefaults.iconButtonColors(
                // Light on dark whatever the picture under it, in either theme.
                containerColor = Color.Black.copy(alpha = 0.5f),
                contentColor = Color.White,
            ),
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(28.dp),
        )
    }
}
