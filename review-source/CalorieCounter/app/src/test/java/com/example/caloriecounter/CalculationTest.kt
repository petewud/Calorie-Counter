package com.example.caloriecounter

import org.junit.Assert.*
import org.junit.Test

class CalculationTest {
 @Test fun partialTinScalesAllNutrients() {
  val food=Food("Beans",nutrients=Nutrients(listOf(80.0,5.0,12.0,1.0,0.2,4.0,3.0,0.5)))
  val item=Ingredient(food,200.0)
  assertEquals(160.0,item.total()[0]!!,0.001)
  assertEquals(0.4,item.total()[4]!!,0.001)
 }
 @Test fun twoBreadSlicesUseLabelWeight() {
  val bread=Food("Bread",nutrients=Nutrients(listOf(244.0,9.1,46.4,2.0,0.5,3.0,2.3,0.98)),servingLabel="slice",servingGrams=57.5)
  val two=Ingredient(bread,2.0,"slice",57.5)
  assertEquals(280.6,two.total()[0]!!,0.001)
 }
 @Test fun threeIngredientRecipeAndPortions() {
  val oats=Ingredient(Food("Oats",nutrients=Nutrients(List(8){100.0})),50.0)
  val milk=Ingredient(Food("Milk",nutrients=Nutrients(List(8){50.0})),200.0,"ml")
  val honey=Ingredient(Food("Honey",nutrients=Nutrients(List(8){300.0})),10.0)
  val r=Recipe(name="Porridge",servings=2,ingredients=listOf(oats,milk,honey))
  assertEquals(3,r.ingredients.size)
  assertEquals(180.0,r.total()[0]!!,0.001)
  assertEquals(90.0,r.servingFood().nutrients[0]!!,0.001)
 }
 @Test fun unknownNutrientsAreNotZero() {
  val a=Nutrients(listOf(100.0,null,0.0,0.0,null,null,null,null))
  assertNull(sumNutrients(listOf(a,a))[1])
  assertEquals(200.0,sumNutrients(listOf(a,a))[0]!!,0.001)
 }
 @Test fun editingFourthIngredientDoesNotReplaceOthers() {
  val items=(1..10).map { Ingredient(Food("Ingredient $it")) }.toMutableList()
  items[3]=items[3].copy(amount=200.0)
  assertEquals(10,items.size)
  assertEquals(200.0,items[3].amount,0.001)
  assertEquals(100.0,items[4].amount,0.001)
 }
 @Test fun maintenanceEquation() {
  assertEquals(1979.0,estimateCalories("Male",30,175.0,70.0,1.2),0.001)
 }
}
