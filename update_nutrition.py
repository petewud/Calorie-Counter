from pathlib import Path
root = Path("CalorieCounter")
pkg = root / "app/src/main/java/com/example/caloriecounter"
ui = pkg / "ui"
def edit(path, fn):
    if not path.exists(): raise SystemExit("Missing " + str(path))
    path.write_text(fn(path.read_text()), encoding="utf-8")
if not (ui / "FoodPicker.kt").exists():
    raise SystemExit("Run update_app.py BEFORE update_nutrition.py")

# Schema changes retain existing rows; missing historic fields remain unknown.
extras = ['saturatedFat', 'sugars', 'fibre', 'salt']
def entities(s):
    s = s.replace('val fat: Double\n)', 'val fat: Double,\n' + ',\n'.join('    val '+n+': Double? = null' for n in extras) + '\n)')
    needle = '    val fatPer100: Double\n)'
    s = s.replace(needle, '    val fatPer100: Double,\n' + ',\n'.join('    val '+n+'Per100: Double? = null' for n in extras) + '\n)')
    return s
edit(pkg/'data/Entities.kt', entities)
def database(s):
    s = s.replace('version = 1,','version = 2,')
    stmts = '\n'.join('                    db.execSQL("ALTER TABLE '+table+' ADD COLUMN '+n+suffix+' REAL")' for table,suffix in [('diary_entries',''),('recipe_ingredients','Per100')] for n in extras)
    s = s.replace(').build().also { instance = it }', ''').addMigrations(object : androidx.room.migration.Migration(1, 2) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
''' + stmts + '''
                }
            }).build().also { instance = it }''')
    return s
edit(pkg/'data/AppDatabase.kt', database)

# Use dedicated search service, not legacy Perl search.
def picker(s):
    start=s.index('    val url = "https://world.openfoodfacts.org/cgi/search.pl?search_terms="')
    end=s.index('    val client =',start)
    s=s[:start]+'''    val url = "https://search.openfoodfacts.org/search?q=" +
        URLEncoder.encode(query, "UTF-8") +
        "&langs=en&page=1&page_size=20&fields=code,product_name,brands,nutriments"
'''+s[end:]
    old='Gson().fromJson(body, SearchResponse::class.java).products.orEmpty().mapNotNull { p ->'
    new='''val tree = com.google.gson.JsonParser.parseString(body).asJsonObject
        val array = tree.getAsJsonArray("hits") ?: tree.getAsJsonArray("products")
            get_ipython().run_line_magic('pinfo', ': error("Unexpected search response. Please try again later.")')
        array.mapNotNull { element ->
            val obj = element.asJsonObject
            val source = obj.getAsJsonObject("_source") ?: obj
            val p = Gson().fromJson(source, SearchProduct::class.java)'''
    if old not in s: raise SystemExit("Unexpected FoodPicker version")
    s=s.replace(old,new)
    s=s.replace('                carbs = food.carbsPer100 * factor, fat = food.fatPer100 * factor))', '''                carbs = food.carbsPer100 * factor, fat = food.fatPer100 * factor,
                saturatedFat = food.saturatedFatPer100 * factor, sugars = food.sugarsPer100 * factor,
                fibre = food.fibrePer100 * factor, salt = food.saltPer100 * factor))''')
    s=s.replace('Search unavailable: ${e.message}. Try again later.', 'Search unavailable: ${e.message}. Your diary is safe; barcode scanning or manual entry may still work.')
    return s
edit(ui/'FoodPicker.kt', picker)

# Manual and barcode diary entries.
def addfood(s):
    s=s.replace('    var fat by remember { mutableStateOf("") }', '    var fat by remember { mutableStateOf("") }\n' + '\n'.join('    var '+n+' by remember { mutableStateOf("") }' for n in extras))
    s=s.replace('        OutlinedTextField(\n            value = quantity,', '''        if (product == null) {
'''+ '\n'.join('            OutlinedTextField(value = '+n+', onValueChange = { '+n+' = it }, label = { Text("'+n+' g per 100 g/ml (optional)") }, modifier = Modifier.fillMaxWidth())' for n in extras)+'''
        }
        val nutrientFactor = quantityValue / 100.0
        Text("Saturated fat: " + ((product?.saturatedFatPer100 ?: saturatedFat.toDoubleOrNull())?.let { "%.1f g".format(it * nutrientFactor) } ?: "not supplied"))
        OutlinedTextField(
            value = quantity,''')
    s=s.replace('                            fat = scale(fatPer100, quantityValue)', '                            fat = scale(fatPer100, quantityValue),\n'+',\n'.join('                            '+n+' = (product?.'+n+'Per100 ?: '+n+'.toDoubleOrNull())?.times(quantityValue / 100.0)' for n in extras))
    return s
edit(ui/'AddFoodScreen.kt', addfood)

# Recipes: ingredient nutrients, manual fields, per-recipe and per-serving totals.
def recipe(s):
    s=s.replace('    var fat by rememberSaveable { mutableStateOf("") }', '    var fat by rememberSaveable { mutableStateOf("") }\n'+'\n'.join('    var '+n+' by rememberSaveable { mutableStateOf("") }' for n in extras))
    s=s.replace('proteinPer100 = p.proteinPer100, carbsPer100 = p.carbsPer100, fatPer100 = p.fatPer100))', 'proteinPer100 = p.proteinPer100, carbsPer100 = p.carbsPer100, fatPer100 = p.fatPer100,\n'+',\n'.join('                        '+n+'Per100 = p.'+n+'Per100' for n in extras)+'))')
    s=s.replace('        val manualValid =', '\n'.join('        OutlinedTextField('+n+', { '+n+' = it }, label = { Text("'+n+' g per 100 g/ml (optional)") })' for n in extras)+'\n        val manualValid =')
    s=s.replace('carbsPer100 = carbs.toDoubleOrNull() ?: 0.0, fatPer100 = fat.toDoubleOrNull() ?: 0.0))', 'carbsPer100 = carbs.toDoubleOrNull() ?: 0.0, fatPer100 = fat.toDoubleOrNull() ?: 0.0,\n'+',\n'.join('                '+n+'Per100 = '+n+'.toDoubleOrNull()' for n in extras)+'))')
    marker='        Button(enabled = !saving'
    lines='''        Text("Nutrition: whole recipe / per serving", style = MaterialTheme.typography.titleMedium)
'''
    for field,label in [('protein','Protein'),('carbs','Carbohydrate'),('fat','Fat')]+[(n,n.capitalize()) for n in extras]:
        isextra=field in extras
        expr='ingredients[i].'+field+'Per100'
        value=('('+expr+' ?: 0.0)') if isextra else expr
        missing=('ingredients.any { it.'+field+'Per100 == null }') if isextra else 'false'
        lines+='        run {\n            val value = ingredients.indices.sumOf { i -> '+value+' * (quantities[i].toDoubleOrNull() ?: 0.0) / 100 }\n'
        lines+='            val incomplete = '+missing+'\n'
        lines+='            Text("'+label+': " + "%.1f g".format(value) + (if (count > 0) " / %.1f g".format(value / count) else "") + (if (incomplete) " (partial: ingredient data missing)" else ""))\n        }\n'
    s=s.replace(marker,lines+marker)
    return s
edit(ui/'RecipeEditorScreen.kt', recipe)

def vm(s):
    s=s.replace('                    fat = totalFat / recipe.servings', '                    fat = totalFat / recipe.servings,\n'+',\n'.join('                    '+n+' = if (ingredients.any { it.'+n+'Per100 == null }) null else ingredients.sumOf { (it.'+n+'Per100 ?: 0.0) * it.quantityGrams / 100.0 } / recipe.servings' for n in extras))
    return s
edit(pkg/'AppViewModel.kt', vm)

# Home and History share one nutrient dashboard and diary rendering.
(ui/'TodayScreen.kt').write_text('''package com.example.caloriecounter.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.caloriecounter.AppViewModel
import com.example.caloriecounter.data.DiaryEntryEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun TodayScreen(viewModel: AppViewModel, onScan: () -> Unit, onAddManually: () -> Unit, onSearch: () -> Unit) {
    DayDiary(viewModel, LocalDate.now(), false, onScan, onAddManually, onSearch)
}

@Composable
fun HistoryScreen(viewModel: AppViewModel) {
    DayDiary(viewModel, LocalDate.now(), true, {}, {}, {})
}

@Composable
private fun DayDiary(viewModel: AppViewModel, initial: LocalDate, history: Boolean,
    onScan: () -> Unit, onManual: () -> Unit, onSearch: () -> Unit) {
    var dateText by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(initial.toString()) }
    val date = LocalDate.parse(dateText)
    val flow = remember(dateText) { viewModel.entriesForDate(dateText) }
    val entries by flow.collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val calories = entries.sumOf { it.kcal }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (history) "History" else "Today", style = MaterialTheme.typography.headlineMedium)
        Text(date.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy")))
        if (history) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { dateText = date.minusDays(1).toString() }) { Text("Previous") }
                OutlinedButton(enabled = date.isBefore(LocalDate.now()), onClick = { dateText = date.plusDays(1).toString() }) { Text("Next") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    DatePickerDialog(context, { _, y, m, d -> dateText = LocalDate.of(y, m + 1, d).toString() },
                        date.year, date.monthValue - 1, date.dayOfMonth).apply {
                        datePicker.maxDate = System.currentTimeMillis()
                    }.show()
                }) { Text("Calendar") }
                TextButton(onClick = { dateText = LocalDate.now().toString() }) { Text("Today") }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("%.0f kcal / %d target".format(calories, settings.calorieTarget), style = MaterialTheme.typography.titleLarge)
                Text("%.0f kcal remaining".format(settings.calorieTarget - calories))
                Text("Protein: %.1f g".format(entries.sumOf { it.protein }))
                Text("Carbohydrate: %.1f g".format(entries.sumOf { it.carbs }))
                Text("Fat: %.1f g".format(entries.sumOf { it.fat }))
                ExtraTotal("Saturated fat", entries) { it.saturatedFat }
                ExtraTotal("Sugars", entries) { it.sugars }
                ExtraTotal("Fibre", entries) { it.fibre }
                ExtraTotal("Salt", entries) { it.salt }
                if (history) Text("Target shown is your current target, not a historical target.", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (!history) {
            Button(onClick = onScan) { Text("Scan barcode") }
            OutlinedButton(onClick = onSearch) { Text("Search food") }
            OutlinedButton(onClick = onManual) { Text("Add manually") }
        }
        if (entries.isEmpty()) Text("No food logged for this day.")
        entries.forEach { entry ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(entry.name, style = MaterialTheme.typography.titleMedium)
                    Text(entry.mealType)
                    if (entry.quantityGrams > 0) Text("%.1f g/ml (as entered)".format(entry.quantityGrams))
                    Text("%.0f kcal; protein %.1f g; carbs %.1f g; fat %.1f g".format(entry.kcal, entry.protein, entry.carbs, entry.fat))
                    Text("Saturated fat: " + (entry.saturatedFat?.let { "%.1f g".format(it) } ?: "not recorded"))
                    TextButton(onClick = { viewModel.deleteEntry(entry) }) { Text("Delete entry") }
                }
            }
        }
        Text("Nutrition data: Open Food Facts (ODbL). Community data may be incomplete; check packaging.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ExtraTotal(label: String, entries: List<DiaryEntryEntity>, read: (DiaryEntryEntity) -> Double?) {
    val missing = entries.count { read(it) == null }
    val known = entries.mapNotNull(read).sum()
    Text(label + ": " + (if (entries.isNotEmpty() && missing == entries.size) "not recorded" else "%.1f g".format(known)) +
        (if (missing > 0 && missing < entries.size) " (partial; $missing entries missing data)" else ""))
}
''', encoding="utf-8")

def navigation(s):
    needle='            NavigationBar {\n'
    item='''                NavigationBarItem(
                    selected = currentRoute == "history",
                    onClick = { navController.navigate("history") { launchSingleTop = true } },
                    icon = { Icon(Icons.Default.List, contentDescription = null) },
                    label = { Text("History") }
                )
'''
    s=s.replace(needle,needle+item)
    s=s.replace('            composable("settings") {','            composable("history") { HistoryScreen(viewModel) }\n            composable("settings") {')
    return s
edit(ui/'App.kt', navigation)
edit(root/'app/build.gradle.kts', lambda s:s.replace('versionCode = 2','versionCode = 3').replace('versionName = "1.1"','versionName = "1.2"'))
print("Applied search, nutrient tracking, migration and history update (1.2)")
