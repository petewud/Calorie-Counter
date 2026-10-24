package com.example.caloriecounter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate

class MainActivity:ComponentActivity() {
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  setContent { MaterialTheme(colorScheme=if(isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) { Surface { CounterApp() } } }
 }
 override fun onResume() { super.onResume();CalorieWidget.refresh(this) }
}

@Composable
fun CounterApp() {
 val context=androidx.compose.ui.platform.LocalContext.current
 val store=remember { Store(context.applicationContext) }
 DisposableEffect(Unit) { onDispose { store.close() } }
 var profile by remember { mutableStateOf(store.profile()) }
 var screen by remember { mutableStateOf(if(profile.completed) "Home" else "Setup") }
 var version by remember { mutableIntStateOf(0) }
 var day by remember { mutableStateOf(LocalDate.now()) }
 var recipe by androidx.compose.runtime.saveable.rememberSaveable(
  stateSaver=androidx.compose.runtime.saveable.Saver<Recipe,String>(save={json.toJson(it)},restore={runCatching{json.fromJson(it,Recipe::class.java)}.getOrDefault(Recipe())})
 ) { mutableStateOf(Recipe()) }
 var form by remember { mutableStateOf(Ingredient(Food())) }
 var formOrigin by remember { mutableStateOf("Home") }
 var formEntryId by remember { mutableLongStateOf(0) }
 var formDate by remember { mutableStateOf(LocalDate.now().toString()) }
 var ingredientIndex by remember { mutableIntStateOf(-1) }
 var pendingReplacement by remember { mutableStateOf(false) }
 var pickOrigin by remember { mutableStateOf("Home") }
 var notice by remember { mutableStateOf("") }
 val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current
 DisposableEffect(lifecycle) {
  val observer=androidx.lifecycle.LifecycleEventObserver { _, event ->
   if(event==androidx.lifecycle.Lifecycle.Event.ON_RESUME) { version++;CalorieWidget.refresh(context) }
  };lifecycle.lifecycle.addObserver(observer)
  onDispose { lifecycle.lifecycle.removeObserver(observer) }
 }
 fun openFood(food:Food) {
  val f=store.portion(food)
  form=Ingredient(f,if(f.servingGrams!=null) 1.0 else 100.0,if(f.servingGrams!=null) f.servingLabel else f.basis,f.servingGrams ?: 1.0)
  if(!pendingReplacement) { ingredientIndex=-1;formEntryId=0;formDate=LocalDate.now().toString() }
  formOrigin=pickOrigin;pendingReplacement=false;screen="Food"
 }
 fun goBack() {
  screen=when(screen) { "Food" -> formOrigin;"Search","Scan" -> if(pickOrigin=="Recipe") "Recipe" else "Home";"Recipe" -> "Recipes";"Setup" -> "Setup";else -> "Home" }
 }
 BackHandler(enabled=screen!="Home" && screen!="Setup") { goBack() }
 Scaffold(bottomBar={ if(profile.completed && screen!="Scan") NavigationBar {
  val tabs=listOf("History","Home","Add","Recipes","Settings")
  val icons=listOf(Icons.Default.History,Icons.Default.Home,Icons.Default.Add,Icons.Default.Restaurant,Icons.Default.Settings)
  tabs.forEachIndexed { i, tab -> NavigationBarItem(selected=screen==tab,onClick={
   if(screen=="Recipe" || (screen=="Food" && formOrigin=="Recipe")) notice="Recipe draft retained until you start another recipe."
   if(tab=="Add") { pendingReplacement=false;pickOrigin="Home";screen="Search" } else { screen=tab;if(tab=="Home") day=LocalDate.now() }
  }, icon={ Icon(icons[i],contentDescription=tab) },label={ Text(tab) }) }
 } }) { padding ->
  Column(Modifier.fillMaxSize().padding(padding)) {
   if(notice.isNotBlank()) { Text(notice,Modifier.padding(8.dp));TextButton(onClick={notice=""}) { Text("Dismiss") } }
   when(screen) {
    "Setup","Settings" -> ProfileScreen(profile,screen=="Setup",store,onSave={ p -> profile=p;store.saveProfile(p);version++;screen="Home" })
    "Home","History" -> {
     val selected=if(screen=="Home") LocalDate.now() else day
     val entries=remember(version,selected) { store.entries(selected.toString()) }
     DiaryScreen(entries,profile,selected,screen=="History",onDate={day=it},onAdd={ pendingReplacement=false;pickOrigin="Home";screen="Search" },onEdit={ e ->
      form=Ingredient(e.food,e.amount,e.unit,e.unitGrams);formOrigin=screen;formEntryId=e.id;formDate=e.date;ingredientIndex=-1;screen="Food"
     },onDelete={store.deleteEntry(it.id);version++})
    }
    "Recipes" -> {
     val recipes=remember(version) { store.recipes() }
     RecipesScreen(recipes,onNew={recipe=Recipe();screen="Recipe"},onResume={screen="Recipe"},hasDraft=recipe.name.isNotBlank()||recipe.ingredients.isNotEmpty(),onEdit={recipe=it;screen="Recipe"},onDelete={store.deleteRecipe(it.id);version++},onLog={r ->
      pickOrigin="Home";form=Ingredient(r.servingFood(),1.0,"portion",100.0);formOrigin="Home";formEntryId=0;formDate=LocalDate.now().toString();ingredientIndex=-1;screen="Food"
     })
    }
    "Recipe" -> RecipeScreen(recipe,onChange={recipe=it},onAdd={pendingReplacement=false;pickOrigin="Recipe";screen="Search"},onEdit={index ->
     ingredientIndex=index;form=recipe.ingredients[index];formOrigin="Recipe";screen="Food"
    },onSave={store.saveRecipe(recipe);recipe=Recipe();version++;screen="Recipes"},onCancel={screen="Recipes"})
    "Search" -> SearchScreen(profile,onSelect={openFood(it)},onManual={openFood(Food())},onScan={screen="Scan"})
    "Scan" -> ScanLookup(onSelect={openFood(it)},onCancel={screen="Search"})
    "Food" -> FoodForm(form,onCancel={screen=formOrigin},onReplace={pendingReplacement=true;pickOrigin=formOrigin;screen="Search"},onSave={ i ->
     form=i
     if(i.food.servingGrams!=null) store.savePortion(i.food)
     if(formOrigin=="Recipe") {
      val items=recipe.ingredients.toMutableList()
      if(ingredientIndex in items.indices) items[ingredientIndex]=i else items.add(i)
      recipe=recipe.copy(ingredients=items);ingredientIndex=-1;screen="Recipe"
     } else {
      store.saveEntry(Entry(formEntryId,formDate,i.food,i.amount,i.unit,i.unitGrams));version++;screen=formOrigin
     }
    })
   }
  }
 }
}
