package com.example.caloriecounter

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryScreen(entries:List<Entry>,profile:Profile,date:LocalDate,history:Boolean,onDate:(LocalDate)->Unit,onAdd:()->Unit,onEdit:(Entry)->Unit,onDelete:(Entry)->Unit) {
 val context=LocalContext.current
 var expanded by remember(date) { mutableStateOf<Set<Long>>(emptySet()) }
 var delete by remember { mutableStateOf<Entry?>(null) }
 val totals=entries.map { it.total() }
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
  Text(if(history)"History" else "Today",style=MaterialTheme.typography.headlineMedium)
  Text(date.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy")))
  if(history) Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
   IconButton(onClick={onDate(date.minusDays(1))}){Icon(Icons.Default.ChevronLeft,"Previous day")}
   OutlinedButton(onClick={DatePickerDialog(context,{_,y,m,d->onDate(LocalDate.of(y,m+1,d))},date.year,date.monthValue-1,date.dayOfMonth).apply{datePicker.maxDate=System.currentTimeMillis()}.show()}){Text("Calendar")}
   IconButton(enabled=date.isBefore(LocalDate.now()),onClick={onDate(date.plusDays(1))}){Icon(Icons.Default.ChevronRight,"Next day")}
   TextButton(onClick={onDate(LocalDate.now())}){Text("Today")}
  }
  Card(Modifier.fillMaxWidth()) {
   Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
    val used=partialTotal(totals,0);val remaining=profile.targets[0]-used
    Text(if(remaining>=0)"${numberText(remaining,0)} kcal remaining" else "${numberText(-remaining,0)} kcal over target",style=MaterialTheme.typography.titleLarge)
    nutrientNames.forEachIndexed { i,name ->
     val known=partialTotal(totals,i);val unknown=totals.count{it[i]==null};val target=profile.targets.getOrElse(i){referenceDefaults[i]}
     val pct=known/target*100
     val kind=when(i){0->"target";3,4,7->"limit";6->"goal";else->"reference"}
     Column {
      Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
       Text(name,style=MaterialTheme.typography.labelLarge)
       Text("${numberText(pct,0)}%"+if(unknown>0)"*" else "",style=MaterialTheme.typography.labelSmall)
      }
      LinearProgressIndicator(progress={ (known/target).toFloat().coerceIn(0f,1f) },modifier=Modifier.fillMaxWidth(),color=if(i in listOf(3,4,7)&&pct>100)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
      Text("${numberText(known,if(i==0)0 else if(i==7)2 else 1)} / ${numberText(target,if(i==0)0 else 1)} ${nutrientUnits[i]} $kind"+if(unknown>0)" · partial: $unknown missing" else "",style=MaterialTheme.typography.bodySmall)
     }
    }
    Text("Reference values are guides, not personalised RDAs. Fat, saturates and salt are limits, not goals to fill. Total sugars is a label reference, not a free-sugars allowance.",style=MaterialTheme.typography.bodySmall)
    if(history)Text("Uses your current targets, not historic targets.",style=MaterialTheme.typography.bodySmall)
   }
  }
  if(!history)Button(onClick=onAdd,modifier=Modifier.fillMaxWidth()){Text("Add food or recipe")}
  if(entries.isEmpty())Text("No items logged.")
  entries.forEach { e ->
   val isOpen=e.id in expanded
   Card(onClick={expanded=if(isOpen)expanded-e.id else expanded+e.id},modifier=Modifier.fillMaxWidth()) {
    Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
     Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
      Text(e.food.name,modifier=Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
      Text("${numberText(e.total()[0],0)} kcal")
     }
     if(isOpen) {
      Text("${numberText(e.amount)} ${e.unit}")
      NutritionText(e.total())
      Row {
       IconButton(onClick={onEdit(e)}){Icon(Icons.Default.Edit,"Edit entry")}
       IconButton(onClick={delete=e}){Icon(Icons.Default.Delete,"Delete entry")}
      }
     }
    }
   }
  }
 }
 delete?.let { e -> AlertDialog(onDismissRequest={delete=null},title={Text("Delete ${e.food.name}?")},text={Text("This removes this diary entry only.")},confirmButton={TextButton(onClick={onDelete(e);delete=null}){Text("Delete")}},dismissButton={TextButton(onClick={delete=null}){Text("Cancel")}}) }
}

@Composable
fun RecipesScreen(recipes:List<Recipe>,onNew:()->Unit,onResume:()->Unit,hasDraft:Boolean,onEdit:(Recipe)->Unit,onDelete:(Recipe)->Unit,onLog:(Recipe)->Unit) {
 var delete by remember { mutableStateOf<Recipe?>(null) }
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
  Text("Recipes",style=MaterialTheme.typography.headlineMedium)
  Button(onClick=onNew){Icon(Icons.Default.Add,null);Text("New recipe")}
  if(hasDraft)OutlinedButton(onClick=onResume){Text("Continue current draft")}
  recipes.forEach { r -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
   Text(r.name,style=MaterialTheme.typography.titleLarge)
   Text("${r.ingredients.size} ingredients · ${r.servings} portions")
   Text("${numberText(r.total()[0]?.div(r.servings),0)} kcal per portion")
   Row {
    Button(onClick={onLog(r)}){Text("Log portions")}
    IconButton(onClick={onEdit(r)}){Icon(Icons.Default.Edit,"Edit recipe")}
    IconButton(onClick={delete=r}){Icon(Icons.Default.Delete,"Delete recipe")}
   }
  } } }
 }
 delete?.let { r -> AlertDialog(onDismissRequest={delete=null},title={Text("Delete recipe?")},text={Text("Past diary entries are retained.")},confirmButton={TextButton(onClick={onDelete(r);delete=null}){Text("Delete")}},dismissButton={TextButton(onClick={delete=null}){Text("Cancel")}}) }
}

@Composable
fun RecipeScreen(recipe:Recipe,onChange:(Recipe)->Unit,onAdd:()->Unit,onEdit:(Int)->Unit,onSave:()->Unit,onCancel:()->Unit) {
 var servings by remember(recipe.id) { mutableStateOf(recipe.servings.toString()) }
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
  Text(if(recipe.id==0L)"New recipe" else "Edit recipe",style=MaterialTheme.typography.headlineSmall)
  OutlinedTextField(recipe.name,{onChange(recipe.copy(name=it))},label={Text("Recipe name")},modifier=Modifier.fillMaxWidth())
  NumericField(servings,{servings=it;it.toIntOrNull()?.takeIf{n->n>0}?.let{n->onChange(recipe.copy(servings=n))}},"Number of portions")
  Button(onClick=onAdd,modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Add,null);Text("Add another ingredient")}
  Text("${recipe.ingredients.size} ingredients. Tap a pencil to correct any ingredient, its amount or its nutrition.")
  recipe.ingredients.forEachIndexed { index,i -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
   Text("${index+1}. ${i.food.name}",style=MaterialTheme.typography.titleMedium)
   Text("${numberText(i.amount)} ${i.unit} · ${numberText(i.total()[0],0)} kcal")
   Row {
    IconButton(onClick={onEdit(index)}){Icon(Icons.Default.Edit,"Edit ingredient ${index+1}")}
    IconButton(onClick={onChange(recipe.copy(ingredients=recipe.ingredients.filterIndexed{n,_->n!=index}))}){Icon(Icons.Default.Delete,"Remove ingredient ${index+1}")}
   }
  } } }
  Text("Whole recipe / per portion",style=MaterialTheme.typography.titleLarge)
  val total=recipe.total()
  nutrientNames.forEachIndexed { i,label -> Text("$label: ${numberText(total[i],if(i==0)0 else 1)} / ${numberText(total[i]?.div(recipe.servings),if(i==0)0 else 1)} ${nutrientUnits[i]}") }
  Text("If an ingredient's nutrient is unknown, the recipe total for that nutrient is also unknown. Saved diary entries are snapshots: editing a recipe does not rewrite past meals.",style=MaterialTheme.typography.bodySmall)
  Button(enabled=recipe.name.isNotBlank()&&recipe.ingredients.isNotEmpty()&&(servings.toIntOrNull() ?: 0)>0,onClick=onSave,modifier=Modifier.fillMaxWidth()){Text("Save recipe")}
  TextButton(onClick=onCancel){Text("Back to recipes (keep draft)")}
 }
}
