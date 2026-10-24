package com.example.caloriecounter

import kotlin.math.roundToInt
import com.google.gson.Gson

val nutrientNames = listOf("Calories", "Protein", "Carbohydrate", "Fat", "Saturated fat", "Total sugars", "Fibre", "Salt")
val nutrientUnits = listOf("kcal", "g", "g", "g", "g", "g", "g", "g")
val referenceDefaults = listOf(2000.0, 50.0, 260.0, 70.0, 20.0, 90.0, 30.0, 6.0)

data class Nutrients(val values: List<Double?> = List(8) { null }) {
 operator fun times(f: Double) = Nutrients(values.map { it?.times(f) })
 operator fun get(i: Int): Double? = values.getOrNull(i)
}
fun sumNutrients(items: List<Nutrients>): Nutrients = Nutrients((0..7).map { i ->
 if (items.any { it[i] == null }) null else items.sumOf { it[i] ?: 0.0 }
})
fun partialTotal(items: List<Nutrients>, i: Int) = items.sumOf { it[i] ?: 0.0 }
fun numberText(n: Double?, decimals: Int = 1): String = n?.let { ("%.${decimals}f").format(java.util.Locale.UK,it) } ?: "not recorded"

data class Food(val name: String = "", val barcode: String = "", val brand: String = "",
 val nutrients: Nutrients = Nutrients(), val servingLabel: String = "serving", val servingGrams: Double? = null,
 val basis: String = "g")
data class Ingredient(val food: Food, val amount: Double = 100.0, val unit: String = "g", val unitGrams: Double = 1.0) {
 fun total() = food.nutrients * (amount * unitGrams / 100.0)
}
data class Recipe(val id: Long = 0, val name: String = "", val servings: Int = 1, val ingredients: List<Ingredient> = emptyList()) {
 fun total() = sumNutrients(ingredients.map { it.total() })
 fun servingFood() = Food(name, nutrients = total() * (1.0 / servings.coerceAtLeast(1)), servingLabel = "portion", servingGrams = 100.0)
}
data class Entry(val id: Long = 0, val date: String, val food: Food, val amount: Double = 100.0, val unit: String = "g", val unitGrams: Double = 1.0) {
 fun total() = food.nutrients * (amount * unitGrams / 100.0)
}
data class Profile(val completed: Boolean = false, val sex: String = "Female", val age: Int = 30,
 val height: Double = 170.0, val weight: Double = 70.0, val activity: Double = 1.2,
 val targets: List<Double> = referenceDefaults, val ukFirst: Boolean = true) {
 fun estimate() = estimateCalories(sex, age, height, weight, activity)
}
fun estimateCalories(sex: String, age: Int, height: Double, weight: Double, activity: Double): Double =
 ((10 * weight + 6.25 * height - 5 * age + if (sex == "Male") 5 else -161) * activity).roundToInt().toDouble()
val json = Gson()
