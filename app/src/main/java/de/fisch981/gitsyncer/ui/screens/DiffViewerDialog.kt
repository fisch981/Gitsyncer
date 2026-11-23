package de.fisch981.gitsyncer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.fisch981.gitsyncer.ui.theme.GitGreen
import de.fisch981.gitsyncer.ui.theme.GitRed
import de.fisch981.gitsyncer.ui.theme.LocalAppStrings

@Composable
fun DiffViewerDialog(
    filePath: String,
    diffText: String,
    isConflict: Boolean,
    onDismiss: () -> Unit,
    onResolveConflict: ((useLocal: Boolean) -> Unit)? = null
) {
    val strings = LocalAppStrings.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = String.format(strings.diffTitle, filePath),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (isConflict) {
                    Text(
                        text = strings.conflictDetected,
                        color = GitRed,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onResolveConflict?.invoke(true) },
                            colors = ButtonDefaults.buttonColors(containerColor = GitGreen),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(strings.keepLocal)
                        }
                        Button(
                            onClick = { onResolveConflict?.invoke(false) },
                            colors = ButtonDefaults.buttonColors(containerColor = GitRed),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(strings.keepRemote)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Diff viewer scrollable content
                val vScroll = rememberScrollState()
                val hScroll = rememberScrollState()

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = MaterialTheme.shapes.small
                        )
                        .padding(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(vScroll)
                            .horizontalScroll(hScroll)
                    ) {
                        val lines = diffText.lines()
                        for (line in lines) {
                            val bgColor = when {
                                line.startsWith("+") && !line.startsWith("+++") -> GitGreen.copy(alpha = 0.2f)
                                line.startsWith("-") && !line.startsWith("---") -> GitRed.copy(alpha = 0.2f)
                                line.startsWith("@@") -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else -> Color.Transparent
                            }
                            val textColor = when {
                                line.startsWith("+") && !line.startsWith("+++") -> GitGreen
                                line.startsWith("-") && !line.startsWith("---") -> GitRed
                                line.startsWith("@@") -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurface
                            }

                            Text(
                                text = line,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = textColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(bgColor)
                                    .padding(vertical = 1.dp, horizontal = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(strings.close)
                }
            }
        }
    }
}
