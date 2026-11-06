package com.gympad.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gympad.data.AiError
import com.gympad.data.EstimatedFood
import com.gympad.domain.Macros
import com.gympad.domain.parseAmount
import com.gympad.ui.strings.LocalStrings
import com.gympad.ui.strings.aiErrorText
import com.gympad.ui.strings.amountText
import com.gympad.ui.strings.mealName
import com.gympad.ui.theme.DoneGreen
import com.gympad.ui.theme.OnStatus
import org.jetbrains.compose.resources.decodeToImageBitmap

private const val AI_STUDIO_URL = "https://aistudio.google.com/apikey"

@Composable
fun AiSetupSheet(viewModel: MealPhotoViewModel) {
    val strings = LocalStrings.current
    val colors = MaterialTheme.colorScheme
    val state by viewModel.keySetup.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    var key by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(viewModel.maskedKey()) }

    GymSheet(title = strings.aiSetupTitle, onDismiss = viewModel::closeSetup) {
        Text(strings.aiSetupIntro, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            strings.aiSetupSteps.forEachIndexed { i, step ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier.size(24.dp).clip(CircleShape).background(colors.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${i + 1}", style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(step, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        QuietButton(
            text = strings.openAiStudio,
            icon = GymIcons.Export,
            onClick = { uriHandler.openUri(AI_STUDIO_URL) },
            modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(colors.surfaceContainerHigh),
        )
        Spacer(Modifier.height(16.dp))
        saved?.let { masked ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(strings.savedKey(masked), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                QuietButton(
                    text = strings.removeKey,
                    icon = null,
                    onClick = {
                        viewModel.removeKey()
                        saved = null
                    },
                    height = 44.dp,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        GymTextField(
            value = key,
            onValueChange = { key = it },
            placeholder = strings.pasteKey,
            description = strings.apiKey,
            isError = state.error != null,
            capitalization = KeyboardCapitalization.None,
            onDone = { viewModel.saveKey(key) },
        )
        state.error?.let { error ->
            Text(
                strings.aiErrorText(error),
                style = MaterialTheme.typography.bodySmall,
                color = colors.error,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            if (state.checking) strings.checkingKey else strings.saveKey,
            onClick = { viewModel.saveKey(key) },
            enabled = key.isNotBlank() && !state.checking,
        )
        Text(
            strings.aiSetupFinePrint,
            style = MaterialTheme.typography.bodySmall,
            color = colors.outline,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
        )
    }
}

/** Note, waiting, review or error for one meal photo; one sheet, so the steps flow into each other. */
@Composable
fun MealPhotoSheet(step: PhotoStep, viewModel: MealPhotoViewModel, onAdd: (List<Pair<EstimatedFood, Double>>) -> Unit) {
    val strings = LocalStrings.current
    val language = strings.language
    val title = when (step) {
        is PhotoStep.Note -> strings.describeMeal
        is PhotoStep.Analyzing -> strings.mealName(step.meal)
        is PhotoStep.Review -> strings.addFoodTo(strings.mealName(step.meal))
        is PhotoStep.Failed -> strings.estimateFailed
    }
    val photo = remember(step.photo) { runCatching { step.photo.decodeToImageBitmap() }.getOrNull() }

    GymSheet(title = title, onDismiss = viewModel::close) {
        when (step) {
            is PhotoStep.Note -> NoteStep(step, photo, onEstimate = { viewModel.analyze(it, language) }, onRetake = viewModel::retake)
            is PhotoStep.Analyzing -> {
                PhotoPreview(photo)
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.5.dp)
                    Spacer(Modifier.width(14.dp))
                    Text(strings.analyzing, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(16.dp))
                QuietButton(strings.editNote, icon = GymIcons.Pencil, onClick = viewModel::editNote, modifier = Modifier.fillMaxWidth())
            }
            is PhotoStep.Review -> ReviewStep(step, photo, onAdd = onAdd, onEditNote = viewModel::editNote, onRetake = viewModel::retake)
            is PhotoStep.Failed -> {
                PhotoPreview(photo)
                Spacer(Modifier.height(16.dp))
                Text(strings.aiErrorText(step.error), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                if (step.error == AiError.InvalidKey || step.error == AiError.NoKey) {
                    PrimaryButton(strings.changeKey, onClick = viewModel::changeKey)
                } else {
                    PrimaryButton(strings.tryAgain, onClick = { viewModel.analyze(step.text, language) })
                }
                Row(Modifier.fillMaxWidth()) {
                    QuietButton(strings.editNote, icon = GymIcons.Pencil, onClick = viewModel::editNote, modifier = Modifier.weight(1f))
                    QuietButton(strings.retake, icon = GymIcons.Camera, onClick = viewModel::retake, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PhotoPreview(photo: ImageBitmap?) {
    if (photo == null) return
    Image(
        bitmap = photo,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxWidth().height(160.dp).clip(MaterialTheme.shapes.large),
    )
}

@Composable
private fun NoteStep(step: PhotoStep.Note, photo: ImageBitmap?, onEstimate: (String) -> Unit, onRetake: () -> Unit) {
    val strings = LocalStrings.current
    var text by remember { mutableStateOf(step.text) }
    val focus = remember { FocusRequester() }
    // The keyboard comes up with the sheet, so a note is one tap away and skipping is too.
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    PhotoPreview(photo)
    Spacer(Modifier.height(14.dp))
    GymTextField(
        value = text,
        onValueChange = { text = it },
        placeholder = strings.notePlaceholder,
        description = strings.noteDescription,
        capitalization = KeyboardCapitalization.Sentences,
        onDone = { onEstimate(text) },
        modifier = Modifier.focusRequester(focus),
    )
    Spacer(Modifier.height(12.dp))
    PrimaryButton(strings.estimate, onClick = { onEstimate(text) })
    Row(Modifier.fillMaxWidth()) {
        QuietButton(strings.skipNote, icon = null, onClick = { onEstimate("") }, modifier = Modifier.weight(1f))
        QuietButton(strings.retake, icon = GymIcons.Camera, onClick = onRetake, modifier = Modifier.weight(1f))
    }
}

private class ReviewRow(val food: EstimatedFood) {
    var grams by mutableStateOf("")
    var included by mutableStateOf(true)

    /** Gemini's macros scale with the amount, so changing the grams keeps the estimate consistent. */
    fun macros(): Macros? {
        val amount = parseAmount(grams) ?: return null
        val factor = amount / food.grams
        return Macros(food.kcal * factor, food.protein * factor, food.carbs * factor, food.fat * factor)
    }
}

@Composable
private fun ReviewStep(
    step: PhotoStep.Review,
    photo: ImageBitmap?,
    onAdd: (List<Pair<EstimatedFood, Double>>) -> Unit,
    onEditNote: () -> Unit,
    onRetake: () -> Unit,
) {
    val strings = LocalStrings.current
    val colors = MaterialTheme.colorScheme
    val rows = remember(step) {
        mutableStateListOf<ReviewRow>().apply {
            step.foods.forEach { food -> add(ReviewRow(food).also { it.grams = strings.amountText(food.grams) }) }
        }
    }

    PhotoPreview(photo)
    Spacer(Modifier.height(12.dp))
    step.note?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        Spacer(Modifier.height(8.dp))
    }
    if (rows.isEmpty()) {
        Text(strings.noFoodFound, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 12.dp))
    }
    rows.forEach { row -> ReviewFoodRow(row) }
    val chosen = rows.filter { it.included }
    val valid = chosen.isNotEmpty() && chosen.all { it.macros() != null }
    if (rows.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Text(
            macroLine(strings, chosen.mapNotNull { it.macros() }.fold(Macros.ZERO, Macros::plus)),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            strings.addFoodTo(strings.mealName(step.meal)),
            enabled = valid,
            onClick = { if (valid) onAdd(chosen.map { it.food to parseAmount(it.grams)!! }) },
        )
    }
    Row(Modifier.fillMaxWidth()) {
        QuietButton(strings.editNote, icon = GymIcons.Pencil, onClick = onEditNote, modifier = Modifier.weight(1f))
        QuietButton(strings.retake, icon = GymIcons.Camera, onClick = onRetake, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ReviewFoodRow(row: ReviewRow) {
    val strings = LocalStrings.current
    val colors = MaterialTheme.colorScheme
    val macros = row.macros()
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .toggleable(value = row.included, role = Role.Checkbox, onValueChange = { row.included = it })
                .semantics { contentDescription = strings.includeFood(row.food.name) },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(if (row.included) DoneGreen else colors.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                if (row.included) Icon(GymIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = OnStatus)
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                row.food.name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (row.included) colors.onSurface else colors.outline,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                macros?.let { macroLine(strings, it) } ?: "–",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        GymTextField(
            value = row.grams,
            onValueChange = { row.grams = it },
            placeholder = "",
            description = strings.gramsOf(row.food.name),
            isError = macros == null,
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.width(96.dp),
            trailing = { Text("g", style = MaterialTheme.typography.bodyMedium, color = colors.outline, modifier = Modifier.padding(end = 10.dp)) },
        )
    }
}
