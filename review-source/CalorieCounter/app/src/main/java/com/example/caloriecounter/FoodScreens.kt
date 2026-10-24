package com.example.caloriecounter

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

@Composable
fun NumericField(value:String,onChange:(String)->Unit,label:String,modifier:Modifier=Modifier) {
 OutlinedTextField(value,onChange,label={Text(label)},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=modifier.fillMaxWidth())
}
@Composable
fun NutritionText(n:Nutrients) {
 nutrientNames.forEachIndexed { i,name -> Text("$name: ${numberText(n[i],if(i==0) 0 else if(i==7) 2 else 1)} ${nutrientUnits[i]}") }
}
@Composable
fun FoodForm(initial:Ingredient,onCancel:()->Unit,onReplace:()->Unit,onSave:(Ingredient)->Unit) {
 var name by remember { mutableStateOf(initial.food.name) }
 var amount by remember { mutableStateOf(initial.amount.toString()) }
 var unit by remember { mutableStateOf(initial.unit) }
 var portionName by remember { mutableStateOf(initial.food.servingLabel) }
 var portionWeight by remember { mutableStateOf((initial.food.servingGrams ?: if(initial.unitGrams!=1.0) initial.unitGrams else null)?.toString().orEmpty()) }
 var basis by remember { mutableStateOf(initial.food.basis) }
 val nutrients=remember { mutableStateListOf<String>().apply { addAll(initial.food.nutrients.values.map { it?.toString().orEmpty() }) } }
 var showNutrients by remember { mutableStateOf(initial.food.name.isBlank()) }
 val q=amount.toDoubleOrNull()?.takeIf { it.isFinite() && it>0 }
 val weight=if(unit=="g" || unit=="ml") 1.0 else portionWeight.toDoubleOrNull()?.takeIf { it.isFinite() && it>0 }
 val valid=nutrients.all { it.isBlank() || it.toDoubleOrNull()?.let { n -> n.isFinite() && n>=0 }==true }
 val per100=Nutrients(nutrients.map { it.toDoubleOrNull() })
 val total=per100 * ((q ?: 0.0)*(weight ?: 0.0)/100.0)
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
  Text("Food / ingredient",style=MaterialTheme.typography.headlineSmall)
  OutlinedTextField(name,{name=it},label={Text("Name")},modifier=Modifier.fillMaxWidth())
  if(initial.food.brand.isNotBlank()) Text(initial.food.brand)
  OutlinedButton(onClick=onReplace){Text("Replace with a scanned / searched food")}
  Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
   FilterChip(selected=unit=="g",onClick={unit="g";basis="g"},label={Text("Grams")})
   FilterChip(selected=unit=="ml",onClick={unit="ml";basis="ml"},label={Text("Millilitres")})
   FilterChip(selected=unit!="g"&&unit!="ml",onClick={unit=portionName.ifBlank { "serving" }},label={Text("Slices / servings")})
  }
  if(unit!="g" && unit!="ml") {
   OutlinedTextField(portionName,{portionName=it;unit=it.ifBlank { "serving" }},label={Text("Unit name, e.g. slice, biscuit, portion")},modifier=Modifier.fillMaxWidth())
   NumericField(portionWeight,{portionWeight=it},"${basis} per ${portionName.ifBlank { "serving" }} (check pack)")
   Text("Use the packaging's average slice/serving weight, or enter a measured value. This conversion is remembered for this food.",style=MaterialTheme.typography.bodySmall)
  }
  NumericField(amount,{amount=it},"Amount ($unit)")
  Text("Values must match the label's per-100 g or per-100 ml basis. Grams and ml are not interchangeable.",style=MaterialTheme.typography.bodySmall)
  TextButton(onClick={showNutrients=!showNutrients}) { Text(if(showNutrients) "Hide nutrition editor" else "Review / edit nutrition per 100 $basis") }
  if(showNutrients) {
   Text("Per 100 $basis. Blank means unknown, not zero.")
   nutrientNames.forEachIndexed { i,label -> NumericField(nutrients[i],{nutrients[i]=it},"$label (${nutrientUnits[i]})") }
  }
  Text("For this amount",style=MaterialTheme.typography.titleMedium);NutritionText(total)
  if(per100[0]==null) Text("Calories are missing. Enter the calorie value from the label to save.",color=MaterialTheme.colorScheme.error)
  Button(enabled=name.isNotBlank()&&q!=null&&weight!=null&&valid&&per100[0]!=null,onClick={
   val f=initial.food.copy(name=name.trim(),nutrients=per100,basis=basis,servingLabel=portionName.ifBlank { "serving" },servingGrams=portionWeight.toDoubleOrNull()?.takeIf { it.isFinite()&&it>0 })
   onSave(Ingredient(f,q!!,unit,weight!!))
  },modifier=Modifier.fillMaxWidth()) { Text("Save") }
  OutlinedButton(onClick=onCancel,modifier=Modifier.fillMaxWidth()) { Text("Cancel") }
 }
}

@Composable
fun ScanLookup(onSelect:(Food)->Unit,onCancel:()->Unit) {
 var code by remember { mutableStateOf<String?>(null) }
 var message by remember { mutableStateOf("") }
 LaunchedEffect(code) {
  code?.let { try {message="Looking up barcode…";onSelect(FoodApi.product(it))} catch(e:CancellationException){throw e} catch(e:Exception){message=e.message ?: "Lookup failed"} }
 }
 if(code==null) Scanner({code=it},onCancel) else Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
  Text(message);Button(onClick={code=null}){Text("Scan again")};OutlinedButton(onClick=onCancel){Text("Search / manual entry")}
 }
}

@Composable
fun SearchScreen(profile:Profile,onSelect:(Food)->Unit,onManual:()->Unit,onScan:()->Unit) {
 var query by remember { mutableStateOf("") };var brand by remember { mutableStateOf("") }
 var activeQ by remember { mutableStateOf("") };var activeB by remember { mutableStateOf("") }
 var page by remember { mutableIntStateOf(0) };var more by remember { mutableStateOf(false) }
 var results by remember { mutableStateOf<List<FoodApi.Hit>>(emptyList()) }
 var busy by remember { mutableStateOf(false) };var message by remember { mutableStateOf("") }
 val scope=rememberCoroutineScope()
 fun choose(code:String) {scope.launch {busy=true;message="Retrieving nutrition…";try{onSelect(FoodApi.product(code))}catch(e:CancellationException){throw e}catch(e:Exception){message=e.message ?: "Lookup failed"}finally{busy=false}}}
 fun search(load:Boolean) {scope.launch {
  busy=true;message="Searching…"
  val q=if(load) activeQ else query.trim();val b=if(load) activeB else brand.trim()
  if(!load){results=emptyList();more=false;page=0}
  try {
   if(!load&&q.length in 8..14&&q.all(Char::isDigit)&&b.isBlank()) onSelect(FoodApi.product(q))
   else {val next=if(load)page+1 else 1;val r=FoodApi.search(q,b,next);results=((if(load)results else emptyList())+r.hits).distinctBy{it.code};page=next;more=r.more;activeQ=q;activeB=b;message=if(results.isEmpty())"No matches. Try a brand-only search." else "${results.size} matches. Select one to load nutrition."}
  }catch(e:CancellationException){throw e}catch(e:Exception){message=e.message ?: "Search unavailable"}finally{busy=false}
 }}
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
  Text("Find food",style=MaterialTheme.typography.headlineSmall)
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(enabled=!busy,onClick=onScan){Text("Scan")};OutlinedButton(onClick=onManual){Text("Manual entry")}}
  OutlinedTextField(query,{query=it},label={Text("Food name or barcode")},modifier=Modifier.fillMaxWidth())
  OutlinedTextField(brand,{brand=it},label={Text("Brand (optional)")},modifier=Modifier.fillMaxWidth())
  Button(enabled=!busy&&(query.trim().length>=2||brand.trim().length>=2),onClick={search(false)}){Text("Search")}
  if(busy) LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
  Text(message)
  val shown=if(profile.ukFirst)results.sortedByDescending{it.uk} else results
  shown.forEach { h -> OutlinedButton(enabled=!busy,onClick={choose(h.code)},modifier=Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth()){Text(h.name);Text(h.brand);if(h.size.isNotBlank())Text(h.size);Text(h.code)} } }
  if(more) OutlinedButton(enabled=!busy,onClick={search(true)}){Text("Load more")}
  Text("Open Food Facts (ODbL). Community data: verify the packaging. Missing values stay unknown. UK preference is in Settings and reorders loaded results only.",style=MaterialTheme.typography.bodySmall)
 }
}
