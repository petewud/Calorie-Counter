package com.example.caloriecounter

import com.google.gson.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.TimeUnit

object FoodApi {
 data class Hit(val code:String,val name:String,val brand:String,val size:String,val uk:Boolean)
 data class Page(val hits:List<Hit>,val more:Boolean)
 private val client=OkHttpClient.Builder().callTimeout(40,TimeUnit.SECONDS).build()
 private fun text(e:JsonElement?):String {
  if(e==null || e.isJsonNull) return ""
  return when {
   e.isJsonPrimitive -> e.asString
   e.isJsonArray -> e.asJsonArray.map { text(it) }.filter { it.isNotBlank() }.joinToString(", ")
   e.isJsonObject -> text(e.asJsonObject.get("en")).ifBlank { e.asJsonObject.entrySet().firstNotNullOfOrNull { text(it.value).takeIf(String::isNotBlank) }.orEmpty() }
   else -> ""
  }
 }
 private fun num(o:JsonObject,k:String):Double?=runCatching { o.get(k)?.asDouble?.takeIf { it.isFinite() && it>=0 } }.getOrNull()
 private suspend fun get(url:String):JsonObject=withContext(Dispatchers.IO) {
  client.newCall(Request.Builder().url(url).header("User-Agent","CalorieCounter/2.0 (Android personal food diary)").build()).execute().use { r ->
   check(r.isSuccessful) { "Food service returned HTTP ${r.code}. Please try later." }
   JsonParser.parseString(r.body?.string() ?: error("Empty service response")).asJsonObject
  }
 }
 suspend fun search(query:String,brand:String,page:Int):Page {
  fun clean(s:String)=Regex("[\\p{L}\\p{N}]+").findAll(s).joinToString(" ") { it.value }.take(160)
  val q=listOf(clean(query),clean(brand).let { if(it.isBlank()) "" else "brands:\"$it\"" }).filter(String::isNotBlank).joinToString(" ")
  val url="https://search.openfoodfacts.org/search".toHttpUrl().newBuilder().addQueryParameter("q",q).addQueryParameter("langs","en").addQueryParameter("page",page.toString()).addQueryParameter("page_size","50").build().toString()
  val root=get(url);val hits=root.get("hits")
  val arr=when { hits?.isJsonArray==true -> hits.asJsonArray; hits?.isJsonObject==true -> hits.asJsonObject.getAsJsonArray("hits");else -> root.getAsJsonArray("products") } ?: error("Unknown search format")
  return Page(arr.mapNotNull { e ->
   if(!e.isJsonObject) return@mapNotNull null
   val o=e.asJsonObject;val s=o.get("_source")?.takeIf { it.isJsonObject }?.asJsonObject ?: o
   val code=text(s.get("code")).ifBlank { text(o.get("_id")) }
   if(code.isBlank() || !code.all(Char::isDigit)) return@mapNotNull null
   val countries=text(s.get("countries_tags"))+text(s.get("countries"))
   Hit(code,text(s.get("product_name")).ifBlank { text(s.get("product_name_en")) }.ifBlank { "Product $code" },text(s.get("brands")),text(s.get("quantity")),countries.contains("united-kingdom",true)||countries.contains("united kingdom",true))
  }.distinctBy { it.code },arr.size()>=50)
 }
 suspend fun product(code:String):Food {
  require(code.length in 8..14 && code.all(Char::isDigit)) { "Enter an 8–14 digit food barcode" }
  val root=get("https://world.openfoodfacts.org/api/v2/product/$code.json")
  val p=root.get("product")?.takeIf { it.isJsonObject }?.asJsonObject ?: error("Product not found. Search or enter manually.")
  val n=p.get("nutriments")?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
  val kcal=num(n,"energy-kcal_100g") ?: num(n,"energy-kj_100g")?.div(4.184) ?: num(n,"energy_100g")?.div(4.184)
  val values=listOf(kcal,num(n,"proteins_100g"),num(n,"carbohydrates_100g"),num(n,"fat_100g"),num(n,"saturated-fat_100g"),num(n,"sugars_100g"),num(n,"fiber_100g"),num(n,"salt_100g"))
  val serving=text(p.get("serving_size"))
  val matched=Regex("([0-9]+(?:[.,][0-9]+)?)\\s*(g|ml)\\b",RegexOption.IGNORE_CASE).find(serving)
  val grams=matched?.groupValues?.get(1)?.replace(',','.')?.toDoubleOrNull() ?: num(p,"serving_quantity")
  val label=when { serving.contains("slice",true) -> "slice"; serving.contains("piece",true) -> "piece";serving.contains("biscuit",true) -> "biscuit"; else -> "serving" }
  return Food(text(p.get("product_name")).ifBlank { "Product $code" },code,text(p.get("brands")),Nutrients(values),label,grams?.takeIf { it>0 }, if(matched?.groupValues?.get(2)?.lowercase()=="ml") "ml" else "g")
 }
}
