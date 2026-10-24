package com.example.caloriecounter

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject
import org.json.JSONArray

private val legacyFields = listOf("kcal", "protein", "carbs", "fat", "saturatedFat", "sugars", "fibre", "salt")
private fun Cursor.text(key: String, default: String = ""): String {
 val i = getColumnIndex(key); return if (i < 0 || isNull(i)) default else getString(i)
}
private fun Cursor.num(key: String): Double? {
 val i = getColumnIndex(key); return if (i < 0 || isNull(i)) null else getDouble(i)
}
private fun ContentValues.numbers(n: Nutrients, suffix: String = "") {
 legacyFields.forEachIndexed { i, field -> if (n[i] == null) putNull(field + suffix) else put(field + suffix, n[i]) }
}

class Store(private val context: Context) : SQLiteOpenHelper(context, "calorie_counter.db", null, 3) {
 private val prefs = context.getSharedPreferences("profile_v2", Context.MODE_PRIVATE)
 override fun onCreate(db: SQLiteDatabase) = schema(db)
 override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) = schema(db)
 override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
 private fun schema(db: SQLiteDatabase) {
  db.execSQL("CREATE TABLE IF NOT EXISTS diary_entries (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, date TEXT NOT NULL, mealType TEXT NOT NULL, barcode TEXT, name TEXT NOT NULL, quantityGrams REAL NOT NULL, kcal REAL NOT NULL, protein REAL NOT NULL, carbs REAL NOT NULL, fat REAL NOT NULL)")
  db.execSQL("CREATE TABLE IF NOT EXISTS recipes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, servings INTEGER NOT NULL)")
  db.execSQL("CREATE TABLE IF NOT EXISTS recipe_ingredients (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, recipeId INTEGER NOT NULL, name TEXT NOT NULL, quantityGrams REAL NOT NULL, kcalPer100 REAL NOT NULL, proteinPer100 REAL NOT NULL, carbsPer100 REAL NOT NULL, fatPer100 REAL NOT NULL)")
  db.execSQL("CREATE TABLE IF NOT EXISTS settings (id INTEGER PRIMARY KEY NOT NULL, calorieTarget INTEGER NOT NULL)")
  for (table in listOf("diary_entries", "recipe_ingredients")) {
   val fields = mutableSetOf<String>()
   db.rawQuery("PRAGMA table_info($table)", null).use { c -> while(c.moveToNext()) fields.add(c.getString(1)) }
   for (n in legacyFields.drop(4)) {
    val column = n + if (table == "recipe_ingredients") "Per100" else ""
    if (column !in fields) db.execSQL("ALTER TABLE $table ADD COLUMN $column REAL")
   }
   val snapshot = if (table == "diary_entries") "entryJson" else "ingredientJson"
   if (snapshot !in fields) db.execSQL("ALTER TABLE $table ADD COLUMN $snapshot TEXT")
  }
 }
 fun profile(): Profile {
  prefs.getString("profile", null)?.let { raw -> runCatching { json.fromJson(raw, Profile::class.java) }.getOrNull()?.let { return it } }
  val oldTarget = runCatching { readableDatabase.rawQuery("SELECT calorieTarget FROM settings WHERE id=1",null).use { if(it.moveToFirst()) it.getDouble(0) else 2000.0 } }.getOrDefault(2000.0)
  return Profile(targets = referenceDefaults.toMutableList().also { it[0] = oldTarget })
 }
 fun saveProfile(p: Profile) { prefs.edit().putString("profile", json.toJson(p)).commit(); CalorieWidget.refresh(context) }
 fun entries(date: String): List<Entry> {
  val result = mutableListOf<Entry>()
  readableDatabase.rawQuery("SELECT * FROM diary_entries WHERE date=? ORDER BY id DESC", arrayOf(date)).use { c ->
   while(c.moveToNext()) {
    val id = c.num("id")!!.toLong()
    val snapshot = c.text("entryJson")
    val saved = if(snapshot.isNotBlank()) runCatching { json.fromJson(snapshot, Entry::class.java) }.getOrNull() else null
    if(saved != null) result.add(saved.copy(id=id, date=date)) else {
     val q = c.num("quantityGrams") ?: 0.0
     val amount = if(q > 0) q else 1.0
     val weight = if(q > 0) 1.0 else 100.0
     val total = Nutrients(legacyFields.map { c.num(it) })
     result.add(Entry(id,date,Food(c.text("name"),c.text("barcode"),nutrients=total * (100.0/(amount*weight))),amount,if(q > 0) "g" else "portion",weight))
    }
   }
  }
  return result
 }
 fun saveEntry(e: Entry) {
  val v = ContentValues()
  v.put("date",e.date); v.put("mealType",""); v.put("barcode",e.food.barcode);v.put("name",e.food.name)
  v.put("quantityGrams",e.amount*e.unitGrams)
  val total=e.total()
  // Legacy non-null columns retain zero for SQL compatibility; JSON snapshot preserves unknown values.
  legacyFields.forEachIndexed { i, key -> if(i < 4) v.put(key,total[i] ?: 0.0) else if(total[i]==null) v.putNull(key) else v.put(key,total[i]) }
  v.put("entryJson",json.toJson(e))
  if(e.id == 0L) writableDatabase.insertOrThrow("diary_entries",null,v) else writableDatabase.update("diary_entries",v,"id=?",arrayOf(e.id.toString()))
  CalorieWidget.refresh(context)
 }
 fun deleteEntry(id: Long) { writableDatabase.delete("diary_entries","id=?",arrayOf(id.toString()));CalorieWidget.refresh(context) }
 fun recipes(): List<Recipe> {
  val result=mutableListOf<Recipe>()
  readableDatabase.rawQuery("SELECT * FROM recipes ORDER BY name",null).use { c ->
   while(c.moveToNext()) {
    val id=c.num("id")!!.toLong()
    result.add(Recipe(id,c.text("name"),(c.num("servings") ?: 1.0).toInt().coerceAtLeast(1),ingredients(id)))
   }
  }; return result
 }
 private fun ingredients(id: Long): List<Ingredient> {
  val result=mutableListOf<Ingredient>()
  readableDatabase.rawQuery("SELECT * FROM recipe_ingredients WHERE recipeId=? ORDER BY id",arrayOf(id.toString())).use { c ->
   while(c.moveToNext()) {
    val raw=c.text("ingredientJson")
    val stored=if(raw.isBlank()) null else runCatching { json.fromJson(raw,Ingredient::class.java) }.getOrNull()
    result.add(stored ?: Ingredient(Food(c.text("name"),nutrients=Nutrients(legacyFields.map { c.num(it+"Per100") })),c.num("quantityGrams") ?: 100.0))
   }
  };return result
 }
 fun saveRecipe(r: Recipe) {
  val db=writableDatabase;db.beginTransaction()
  try {
   val v=ContentValues();v.put("name",r.name);v.put("servings",r.servings)
   val id=if(r.id == 0L) db.insertOrThrow("recipes",null,v) else { db.update("recipes",v,"id=?",arrayOf(r.id.toString()));r.id }
   db.delete("recipe_ingredients","recipeId=?",arrayOf(id.toString()))
   r.ingredients.forEach { i ->
    val iv=ContentValues();iv.put("recipeId",id);iv.put("name",i.food.name);iv.put("quantityGrams",i.amount*i.unitGrams)
    legacyFields.forEachIndexed { n,key -> if(n < 4) iv.put(key+"Per100",i.food.nutrients[n] ?: 0.0) else if(i.food.nutrients[n]==null) iv.putNull(key+"Per100") else iv.put(key+"Per100",i.food.nutrients[n]) }
    iv.put("ingredientJson",json.toJson(i));db.insertOrThrow("recipe_ingredients",null,iv)
   };db.setTransactionSuccessful()
  } finally { db.endTransaction() }
 }
 fun deleteRecipe(id: Long) {
  writableDatabase.beginTransaction()
  try { writableDatabase.delete("recipe_ingredients","recipeId=?",arrayOf(id.toString()));writableDatabase.delete("recipes","id=?",arrayOf(id.toString()));writableDatabase.setTransactionSuccessful() }
  finally { writableDatabase.endTransaction() }
 }
 fun savePortion(food: Food) { prefs.edit().putString("portion:"+food.barcode.ifBlank { food.name }, json.toJson(food)).apply() }
 fun portion(food: Food): Food = prefs.getString("portion:"+food.barcode.ifBlank { food.name },null)?.let { runCatching { json.fromJson(it,Food::class.java) }.getOrNull() }?.let { food.copy(servingLabel=it.servingLabel,servingGrams=it.servingGrams) } ?: food
 fun exportBackup(): String {
  val dates=mutableListOf<String>()
  readableDatabase.rawQuery("SELECT DISTINCT date FROM diary_entries",null).use { while(it.moveToNext()) dates.add(it.getString(0)) }
  return JSONObject().put("format",2).put("profile",JSONObject(json.toJson(profile())))
   .put("entries",JSONArray(json.toJson(dates.flatMap { entries(it) }))).put("recipes",JSONArray(json.toJson(recipes()))).toString(2)
 }
 fun importBackup(raw: String) {
  val obj=JSONObject(raw)
  require(obj.optInt("format")==2) { "Unsupported backup format" }
  val p=json.fromJson(obj.getJSONObject("profile").toString(),Profile::class.java)
  val es=json.fromJson(obj.getJSONArray("entries").toString(),Array<Entry>::class.java)
  val rs=json.fromJson(obj.getJSONArray("recipes").toString(),Array<Recipe>::class.java)
  require(p.targets.size==8 && p.targets.all { it.isFinite() && it>0 })
  require(es.all { it.amount.isFinite() && it.amount>0 && it.food.nutrients.values.size==8 })
  require(rs.all { it.servings>0 })
  // Merge as new records; never delete existing user data on import.
  es.forEach { saveEntry(it.copy(id=0)) };rs.forEach { saveRecipe(it.copy(id=0)) };saveProfile(p)
 }
}
