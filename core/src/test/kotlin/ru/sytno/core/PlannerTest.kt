package ru.sytno.core

import org.junit.Assert.*
import org.junit.Test

class PlannerTest {
    @Test fun buildsSevenDaysAndRealTotals() { val s=AppState(preferences=Preferences(budget=2_000_000)); val r=Planner.generate(s,3,3); assertEquals(28,r.plan.meals.size); assertTrue(r.issues.toString(),r.success); assertEquals(r.quote.total,Shopping.quote(s,r.plan).total); r.plan.meals.forEach { assertTrue(s.allowed(s.recipe(it.recipeId))) } }
    @Test fun defaultBudgetFindsFeasibleWeek() { val s=AppState(); val r=Planner.generate(s,1,24); assertTrue(r.issues.toString()+" cost="+r.quote.total,r.success); assertTrue(r.quote.total<=s.preferences.budget) }
    @Test fun impossiblyLowBudgetIsExplicit() { val s=AppState(preferences=Preferences(budget=100)); val r=Planner.generate(s,1,2); assertFalse(r.success); assertTrue(r.issues.any { it.startsWith("Бюджет") }) }
    @Test fun filtersAllergiesAndDislikesAcrossSeeds() { val s=AppState(preferences=Preferences(budget=2_000_000,allergies=setOf("Молоко","Яйца","Рыба","Арахис"),excludedFoods=setOf("chicken"))); repeat(8) { seed -> val r=Planner.generate(s,seed,2); r.plan.meals.forEach { assertTrue(s.allowed(s.recipe(it.recipeId))) } } }
    @Test fun pinnedAndCookedMealsPreserved() { val s=AppState(preferences=Preferences(budget=2_000_000)); val initial=Planner.generate(s,1,1).plan; val pinned=initial.meals.first().copy(pinned=true); val state=s.copy(plan=initial.copy(meals=initial.meals.map { if(it.id==pinned.id)pinned else it })); val next=Planner.generate(state,99,2); assertEquals(pinned,next.plan.meals.first { it.id==pinned.id }) }
    @Test fun allTargetsValidatedRatherThanSilentlyIgnored() { val s=AppState(preferences=Preferences(budget=2_000_000,calories=2100,protein=120)); val r=Planner.generate(s,2,12); assertTrue(r.issues.toString(),r.success); for(day in 0..6) { val n=s.copy(plan=r.plan).dailyNutrition(day); assertTrue(n.calories in 1890.0..2310.0); assertTrue(n.protein>=119.99) } }
    @Test fun missingEquipmentDoesNotGetSuggested() { val s=AppState(preferences=Preferences(budget=2_000_000,equipment=emptySet())); val r=Planner.generate(s,4,2); r.plan.meals.forEach { assertTrue(s.recipe(it.recipeId).tools.isEmpty()) } }
    @Test fun selectedMealTypesOnly() { val s=AppState(preferences=Preferences(budget=2_000_000,mealTypes=setOf(MealType.DINNER))); val r=Planner.generate(s,1,2); assertEquals(7,r.plan.meals.size); assertTrue(r.plan.meals.all { it.type==MealType.DINNER }) }
    @Test fun stockedFoodsReduceWholeBasket() { val s=AppState(preferences=Preferences(budget=2_000_000)); val r=Planner.generate(s,3,1); val stock=Shopping.required(s,r.plan).map { PantryItem(it.key,it.value) }; val q=Shopping.quote(s.copy(pantry=stock),r.plan); assertEquals(0L,q.total); assertTrue(q.rows.isEmpty()) }
    @Test fun defaultBudgetReliableAcrossTwentySeeds(){val s=AppState();repeat(20){seed->val r=Planner.generate(s,seed);assertTrue("seed=$seed ${r.issues}",r.success)}}
}
