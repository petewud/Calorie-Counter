package com.example.caloriecounter

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun ProfileScreen(initial:Profile,onboarding:Boolean,store:Store,onSave:(Profile)->Unit) {
 val context=LocalContext.current
 var sex by remember { mutableStateOf(if(onboarding) "" else initial.sex) };var age by remember { mutableStateOf(if(onboarding) "" else initial.age.toString()) }
 var height by remember { mutableStateOf(if(onboarding) "" else initial.height.toString()) };var weight by remember { mutableStateOf(if(onboarding) "" else initial.weight.toString()) }
 var activity by remember { mutableDoubleStateOf(initial.activity) };var uk by remember { mutableStateOf(initial.ukFirst) }
 var estimate by remember { mutableStateOf(onboarding) }
 val targets=remember { mutableStateListOf<String>().apply{addAll(initial.targets.map{it.toString()})} }
 var message by remember { mutableStateOf("") }
 val a=age.toIntOrNull();val h=height.toDoubleOrNull();val w=weight.toDoubleOrNull()
 val valid=sex in listOf("Female","Male")&&a!=null&&a in 18..120&&h!=null&&h.isFinite()&&h in 100.0..250.0&&w!=null&&w.isFinite()&&w in 25.0..400.0
 val kcal=if(valid)estimateCalories(sex,a!!,h!!,w!!,activity) else null
 val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
  if(uri!=null)runCatching { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use{it.write(store.exportBackup())} ?: error("Cannot open backup") }.onSuccess{message="Backup saved."}.onFailure{message="Backup failed: ${it.message}"}
 }
 val importLauncher=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
  if(uri!=null)runCatching {val raw=context.contentResolver.openInputStream(uri)?.bufferedReader()?.use{it.readText()} ?: error("Cannot read backup");store.importBackup(raw)}.onSuccess{onSave(store.profile())}.onFailure{message="Import failed: ${it.message}"}
 }
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
  Text(if(onboarding)"Set up your profile" else "Settings",style=MaterialTheme.typography.headlineMedium)
  Text("For adults 18+. Calorie estimates use Mifflin–St Jeor with an activity multiplier. They estimate maintenance, not a prescribed weight-loss intake. Not suitable for pregnancy, breastfeeding, under-18s or personalised medical nutrition.")
  Text("Sex coefficient used by the energy equation")
  Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
   FilterChip(selected=sex=="Female",onClick={sex="Female"},label={Text("Female")})
   FilterChip(selected=sex=="Male",onClick={sex="Male"},label={Text("Male")})
  }
  NumericField(age,{age=it},"Age (years, 18+)")
  NumericField(height,{height=it},"Height (cm)")
  NumericField(weight,{weight=it},"Weight (kg)")
  Text("Activity")
  listOf("Sedentary" to 1.2,"Lightly active" to 1.375,"Moderately active" to 1.55,"Very active" to 1.725).forEach { (label,value) ->
   Row { RadioButton(selected=activity==value,onClick={activity=value});TextButton(onClick={activity=value}){Text(label)} }
  }
  Text("Estimated maintenance: ${numberText(kcal,0)} kcal/day")
  Row { Switch(checked=estimate,onCheckedChange={estimate=it});Text("Use estimate as calorie target",Modifier.padding(8.dp)) }
  Text("Daily targets and references",style=MaterialTheme.typography.titleLarge)
  Text("Only calories are estimated from your profile. Other starting values are general adult label reference values (plus a 30 g fibre goal), not personalised RDAs. Edit them if you have appropriate individual guidance. Saturated fat, fat and salt are limits; total sugars includes milk and fruit sugars and is not the free-sugars limit.")
  nutrientNames.forEachIndexed { i,label ->
   if(i==0&&estimate)Text("Calorie target: ${numberText(kcal,0)} kcal")
   else NumericField(targets[i],{targets[i]=it},"$label (${nutrientUnits[i]}/day)")
  }
  Row { Switch(checked=uk,onCheckedChange={uk=it});Text("Prioritise UK-labelled search results",Modifier.padding(8.dp)) }
  Text("Reorders the loaded results only; imported foods are not excluded.",style=MaterialTheme.typography.bodySmall)
  val targetNumbers=targets.mapIndexed{i,t->if(i==0&&estimate)kcal else t.toDoubleOrNull()}
  val targetsValid=targetNumbers.all{it!=null&&it.isFinite()&&it>0}
  if(!valid)Text("Enter valid adult profile details to continue.",color=MaterialTheme.colorScheme.error)
  Button(enabled=valid&&targetsValid,onClick={onSave(Profile(true,sex,a!!,h!!,w!!,activity,targetNumbers.map{it!!},uk))},modifier=Modifier.fillMaxWidth()){Text("Save settings")}
  if(!onboarding) {
   HorizontalDivider();Text("Backups",style=MaterialTheme.typography.titleLarge)
   Button(onClick={export.launch("calorie-counter-backup.json")}){Text("Export diary, recipes and profile")}
   OutlinedButton(onClick={importLauncher.launch(arrayOf("application/json","text/plain","application/octet-stream"))}){Text("Import backup (merge)")}
   Text("Import adds records; importing twice creates duplicates. Portion conversions are retained in saved entries/recipes. Standalone remembered conversions are not included.",style=MaterialTheme.typography.bodySmall)
  }
  Text(message)
  Text("Widget: long-press your phone's home screen, choose Widgets, then Calorie Counter → Daily calories. It updates after diary changes and approximately every 30 minutes; Android may delay background refresh.")
  Text("Sources: Mifflin–St Jeor equation (Medscape); adult label reference intakes; NHS / SACN fibre guidance. Food database: Open Food Facts, ODbL. All data is stored locally; searches and scanned barcodes are sent to Open Food Facts. No account or analytics.",style=MaterialTheme.typography.bodySmall)
 }
}
