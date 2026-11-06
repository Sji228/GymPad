package com.gympad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gympad.data.FoodDraft
import com.gympad.data.NutritionGoals
import com.gympad.domain.FoodItem
import com.gympad.domain.Meal
import com.gympad.domain.looksLikeBarcode
import com.gympad.domain.parseAmount
import com.gympad.ui.strings.LocalStrings
import com.gympad.ui.strings.amountText
import com.gympad.ui.strings.foodName
import com.gympad.ui.strings.gramsText
import com.gympad.ui.strings.kcalText
import com.gympad.ui.strings.mealName
import com.gympad.ui.theme.DoneGreen
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private sealed interface AddStep {
    data object Search : AddStep
    data class Amount(val item: FoodItem) : AddStep
    data class Create(val barcode: String?) : AddStep
    data class Edit(val item: FoodItem) : AddStep
}

@Composable
fun AddFoodSheet(meal: Meal, viewModel: FoodViewModel, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf<AddStep>(AddStep.Search) }
    var lastAdded by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { viewModel.setQuery("") } }

    val title = when (val current = step) {
        AddStep.Search -> strings.addFoodTo(strings.mealName(meal))
        is AddStep.Amount -> strings.foodName(current.item)
        is AddStep.Create -> strings.createFood
        is AddStep.Edit -> strings.editFood
    }
    fun added(item: FoodItem, grams: Double) {
        viewModel.addEntry(meal, item, grams)
        lastAdded = "${strings.foodName(item)} · ${strings.gramsText(grams)}"
        viewModel.setQuery("")
        step = AddStep.Search
    }

    GymSheet(title = title, onDismiss = onDismiss) {
        when (val current = step) {
            AddStep.Search -> SearchStep(
                viewModel = viewModel,
                lastAdded = lastAdded,
                onPick = { step = AddStep.Amount(it) },
                onQuickAdd = { item -> item.lastGrams?.let { added(item, it) } },
                onScan = { scanning = true },
                onCreate = { step = AddStep.Create(it) },
                onEdit = { step = AddStep.Edit(it) },
            )
            is AddStep.Amount -> AmountStep(
                item = current.item,
                initialGrams = current.item.lastGrams ?: current.item.portionGrams ?: 100.0,
                confirmText = strings.add,
                onConfirm = { added(current.item, it) },
                onBack = { step = AddStep.Search },
            )
            is AddStep.Create -> FoodForm(
                initial = null,
                barcode = current.barcode,
                onSave = { draft -> scope.launch { step = AddStep.Amount(viewModel.createFood(draft)) } },
                onArchive = null,
                onBack = { step = AddStep.Search },
            )
            is AddStep.Edit -> FoodForm(
                initial = current.item,
                barcode = current.item.barcode,
                onSave = { draft ->
                    current.item.foodId?.let { viewModel.updateFood(it, draft) }
                    step = AddStep.Search
                },
                onArchive = current.item.foodId?.let { id ->
                    {
                        viewModel.archiveFood(id)
                        step = AddStep.Search
                    }
                },
                onBack = { step = AddStep.Search },
            )
        }
    }

    if (scanning) {
        BarcodeScanner(
            onScanned = { code ->
                scanning = false
                scope.launch {
                    step = when (val result = viewModel.lookUpBarcode(code)) {
                        is BarcodeResult.Found -> AddStep.Amount(result.item)
                        is BarcodeResult.Missing -> AddStep.Create(result.code)
                    }
                }
            },
            onDismiss = { scanning = false },
        )
    }
}

@Composable
private fun SearchStep(
    viewModel: FoodViewModel,
    lastAdded: String?,
    onPick: (FoodItem) -> Unit,
    onQuickAdd: (FoodItem) -> Unit,
    onScan: () -> Unit,
    onCreate: (barcode: String?) -> Unit,
    onEdit: (FoodItem) -> Unit,
) {
    val strings = LocalStrings.current
    val colors = MaterialTheme.colorScheme
    val search by viewModel.search.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val ownFoods by viewModel.ownFoods.collectAsStateWithLifecycle()
    val query = search.query

    Column(Modifier.heightIn(min = 420.dp)) {
        GymTextField(
            value = query,
            onValueChange = viewModel::setQuery,
            placeholder = strings.searchFood,
            description = strings.searchFood,
            capitalization = KeyboardCapitalization.None,
            leading = {
                Icon(GymIcons.Search, contentDescription = null, modifier = Modifier.padding(end = 10.dp).size(18.dp), tint = colors.outline)
            },
            trailing = {
                if (query.isNotEmpty()) {
                    IconAction(GymIcons.Close, strings.cancel, { viewModel.setQuery("") }, size = 44.dp, iconSize = 18.dp)
                }
                if (canScanBarcodes) {
                    IconAction(GymIcons.Barcode, strings.scanBarcode, onScan, size = 44.dp, iconSize = 20.dp)
                }
            },
        )
        lastAdded?.let {
            Row(Modifier.padding(top = 12.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(GymIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = DoneGreen)
                Spacer(Modifier.width(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (query.isBlank()) {
            if (recent.isNotEmpty()) {
                SectionLabel(strings.recent, Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp))
                recent.forEach { item ->
                    val grams = item.lastGrams ?: 100.0
                    FoodRow(
                        item = item,
                        detail = "${strings.gramsText(grams)} · ${strings.kcalText(item.macrosFor(grams).kcal)}",
                        onClick = { onPick(item) },
                        action = { IconAction(GymIcons.Plus, strings.add + " " + strings.foodName(item), { onQuickAdd(item) }, size = 44.dp, iconSize = 20.dp) },
                    )
                }
            }
            if (ownFoods.isNotEmpty()) {
                SectionLabel(strings.myFoods, Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp))
                ownFoods.forEach { item ->
                    FoodRow(
                        item = item,
                        detail = per100Text(item),
                        onClick = { onPick(item) },
                        action = { IconAction(GymIcons.Pencil, strings.editFood, { onEdit(item) }, size = 44.dp, iconSize = 18.dp, tint = colors.onSurfaceVariant) },
                    )
                }
            }
            if (recent.isEmpty() && ownFoods.isEmpty()) {
                Hint(strings.emptyFoodList)
            }
        } else {
            search.results.forEach { item ->
                FoodRow(item = item, detail = per100Text(item), onClick = { onPick(item) })
            }
            if (search.results.isEmpty() && !search.searching) {
                Hint(if (looksLikeBarcode(query)) strings.notInDatabase(query.trim()) else strings.noResults)
            }
        }
        Spacer(Modifier.height(8.dp))
        QuietButton(
            text = strings.createFood,
            icon = GymIcons.Plus,
            onClick = { onCreate(query.trim().takeIf(::looksLikeBarcode)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            strings.catalogCredit,
            style = MaterialTheme.typography.bodySmall,
            color = colors.outline,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun per100Text(item: FoodItem): String {
    val strings = LocalStrings.current
    return listOfNotNull(item.brand, "${strings.kcalText(item.per100.kcal)} / 100 g").joinToString(" · ")
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
    )
}

@Composable
private fun FoodRow(
    item: FoodItem,
    detail: String,
    onClick: () -> Unit,
    action: (@Composable () -> Unit)? = null,
) {
    val strings = LocalStrings.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(strings.foodName(item), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        action?.invoke()
    }
}

/** The amount field with one-tap amounts, a live total and the confirm button. */
@Composable
private fun AmountStep(
    item: FoodItem,
    initialGrams: Double,
    confirmText: String,
    onConfirm: (Double) -> Unit,
    onBack: (() -> Unit)?,
    extra: @Composable () -> Unit = {},
) {
    val strings = LocalStrings.current
    var text by remember(item.key) { mutableStateOf(strings.amountText(initialGrams)) }
    val grams = parseAmount(text)

    Text(per100Text(item), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(14.dp))
    GymTextField(
        value = text,
        onValueChange = { text = it },
        placeholder = "100",
        description = strings.amount,
        isError = text.isNotEmpty() && grams == null,
        keyboardType = KeyboardType.Decimal,
        onDone = { grams?.let(onConfirm) },
        trailing = { Text("g", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(end = 12.dp)) },
    )
    val quick = buildList {
        item.lastGrams?.let { add(strings.lastTime to it) }
        item.portionGrams?.let { add(strings.portion to it) }
        item.packageGrams?.let { add(strings.wholePackage to it) }
        add(null to 100.0)
    }.distinctBy { it.second }
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        quick.forEach { (label, value) ->
            val valueText = strings.gramsText(value)
            Pill(
                text = label?.let { "$it · $valueText" } ?: valueText,
                selected = grams == value,
                onClick = { text = strings.amountText(value) },
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    MacroTotals(item.macrosFor(grams ?: 0.0))
    extra()
    Spacer(Modifier.height(16.dp))
    PrimaryButton(confirmText, onClick = { grams?.let(onConfirm) }, enabled = grams != null)
    if (onBack != null) {
        QuietButton(strings.back, icon = GymIcons.Back, onClick = onBack, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun MacroTotals(macros: com.gympad.domain.Macros) {
    val strings = LocalStrings.current
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surfaceContainerHigh)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(strings.kcalText(macros.kcal), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        MacroValue(strings.proteinShort, macros.protein)
        MacroValue(strings.carbsShort, macros.carbs)
        MacroValue(strings.fatShort, macros.fat)
    }
}

@Composable
private fun MacroValue(label: String, grams: Double) {
    Column(Modifier.padding(start = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Text("${LocalStrings.current.amountText(grams.roundOne())} g", style = MaterialTheme.typography.labelMedium)
    }
}

private fun Double.roundOne(): Double = (this * 10).roundToInt() / 10.0

@Composable
private fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .height(36.dp)
            .clip(CircleShape)
            .background(if (selected) colors.primary else colors.surfaceContainerHigh)
            .selectable(selected = selected, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) colors.onPrimary else colors.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Decimal,
    isError: Boolean = false,
) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
        GymTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            description = label,
            keyboardType = keyboardType,
            capitalization = KeyboardCapitalization.Sentences,
            isError = isError,
        )
    }
}

/** Zero is a real value on a label ("0 g fat"), unlike an amount eaten. */
private fun parseNutrient(text: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.0..1000.0 }

@Composable
private fun FoodForm(
    initial: FoodItem?,
    barcode: String?,
    onSave: (FoodDraft) -> Unit,
    onArchive: (() -> Unit)?,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    fun text(value: Double?) = value?.let(strings::amountText).orEmpty()
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var brand by remember { mutableStateOf(initial?.brand.orEmpty()) }
    var code by remember { mutableStateOf(barcode.orEmpty()) }
    var kcal by remember { mutableStateOf(text(initial?.per100?.kcal)) }
    var protein by remember { mutableStateOf(text(initial?.per100?.protein)) }
    var carbs by remember { mutableStateOf(text(initial?.per100?.carbs)) }
    var fat by remember { mutableStateOf(text(initial?.per100?.fat)) }
    var portion by remember { mutableStateOf(text(initial?.portionGrams)) }

    val p = parseNutrient(protein)
    val c = parseNutrient(carbs)
    val f = parseNutrient(fat)
    // Labels sometimes only list macros; the calories then follow from them.
    val computedKcal = if (p != null && c != null && f != null) 4 * p + 4 * c + 9 * f else null
    val k = if (kcal.isBlank()) computedKcal else parseNutrient(kcal)
    val portionGrams = if (portion.isBlank()) null else parseAmount(portion)
    val valid = name.isNotBlank() && k != null && p != null && c != null && f != null &&
        (portion.isBlank() || portionGrams != null)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LabeledField(strings.foodNameLabel, name, { name = it }, keyboardType = KeyboardType.Text)
        LabeledField("${strings.brandLabel} (${strings.optional})", brand, { brand = it }, keyboardType = KeyboardType.Text)
        LabeledField("${strings.barcodeLabel} (${strings.optional})", code, { code = it }, keyboardType = KeyboardType.Number)
        SectionLabel(strings.per100g, Modifier.padding(start = 4.dp, top = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LabeledField(
                strings.caloriesGoal, kcal, { kcal = it }, Modifier.weight(1f),
                placeholder = computedKcal?.let { strings.amountText(it.roundToInt().toDouble()) }.orEmpty(),
                isError = kcal.isNotBlank() && parseNutrient(kcal) == null,
            )
            LabeledField(strings.gramsGoal(strings.protein), protein, { protein = it }, Modifier.weight(1f), isError = protein.isNotBlank() && p == null)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LabeledField(strings.gramsGoal(strings.carbs), carbs, { carbs = it }, Modifier.weight(1f), isError = carbs.isNotBlank() && c == null)
            LabeledField(strings.gramsGoal(strings.fat), fat, { fat = it }, Modifier.weight(1f), isError = fat.isNotBlank() && f == null)
        }
        LabeledField("${strings.portionGramsLabel} (${strings.optional})", portion, { portion = it }, isError = portion.isNotBlank() && portionGrams == null)
        if (initial != null) {
            Text(strings.editFoodNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PrimaryButton(
            strings.save,
            enabled = valid,
            onClick = {
                if (valid) {
                    onSave(FoodDraft(name, brand, code, k, p, c, f, portionGrams))
                }
            },
        )
        if (onArchive != null && initial?.custom == true) {
            QuietButton(strings.archiveFood, icon = GymIcons.Close, onClick = onArchive, modifier = Modifier.fillMaxWidth())
        }
        QuietButton(strings.back, icon = GymIcons.Back, onClick = onBack, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun EditEntrySheet(entry: FoodEntryUi, viewModel: FoodViewModel, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    var meal by remember { mutableStateOf(entry.meal) }
    var editingFood by remember { mutableStateOf(false) }

    GymSheet(title = if (editingFood) strings.editFood else strings.foodName(entry.item), onDismiss = onDismiss) {
        if (editingFood) {
            FoodForm(
                initial = entry.item,
                barcode = entry.item.barcode,
                onSave = { draft ->
                    entry.item.foodId?.let { viewModel.updateFood(it, draft) }
                    onDismiss()
                },
                onArchive = null,
                onBack = { editingFood = false },
            )
        } else {
            AmountStep(
                item = entry.item,
                initialGrams = entry.grams,
                confirmText = strings.save,
                onConfirm = { grams ->
                    viewModel.updateEntry(entry.entryId, grams, meal)
                    onDismiss()
                },
                onBack = null,
                extra = {
                    SectionLabel(strings.mealLabel, Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp))
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Meal.entries.forEach { option ->
                            Pill(strings.mealName(option), selected = option == meal, onClick = { meal = option })
                        }
                    }
                },
            )
            Row(Modifier.fillMaxWidth()) {
                QuietButton(strings.editFood, icon = GymIcons.Pencil, onClick = { editingFood = true }, modifier = Modifier.weight(1f))
                QuietButton(
                    strings.removeEntry,
                    icon = GymIcons.Close,
                    onClick = {
                        viewModel.deleteEntry(entry)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
fun GoalsSheet(goals: NutritionGoals, onSave: (NutritionGoals) -> Unit, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    fun text(value: Int?) = value?.toString().orEmpty()
    var kcal by remember { mutableStateOf(text(goals.kcal)) }
    var protein by remember { mutableStateOf(text(goals.protein)) }
    var carbs by remember { mutableStateOf(text(goals.carbs)) }
    var fat by remember { mutableStateOf(text(goals.fat)) }
    fun parse(value: String): Int? = value.trim().toIntOrNull()?.takeIf { it in 1..20_000 }
    fun bad(value: String) = value.isNotBlank() && parse(value) == null
    val valid = listOf(kcal, protein, carbs, fat).none(::bad)

    GymSheet(title = strings.goalsTitle, onDismiss = onDismiss) {
        Text(strings.goalsDetail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LabeledField(strings.caloriesGoal, kcal, { kcal = it }, keyboardType = KeyboardType.Number, isError = bad(kcal))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LabeledField(strings.gramsGoal(strings.protein), protein, { protein = it }, Modifier.weight(1f), keyboardType = KeyboardType.Number, isError = bad(protein))
                LabeledField(strings.gramsGoal(strings.carbs), carbs, { carbs = it }, Modifier.weight(1f), keyboardType = KeyboardType.Number, isError = bad(carbs))
                LabeledField(strings.gramsGoal(strings.fat), fat, { fat = it }, Modifier.weight(1f), keyboardType = KeyboardType.Number, isError = bad(fat))
            }
            PrimaryButton(
                strings.save,
                enabled = valid,
                onClick = {
                    onSave(NutritionGoals(kcal = parse(kcal), protein = parse(protein), carbs = parse(carbs), fat = parse(fat)))
                    onDismiss()
                },
            )
        }
    }
}
