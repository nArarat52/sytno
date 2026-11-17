package ru.sytno.core

import org.junit.Assert.*
import org.junit.Test

class EngineTest {
    private fun riceState(stock: Long = 400): AppState {
        val base = AppState(preferences = Preferences(budget = 1_000_000))
        val recipe = Recipe("testRice", "Рис", setOf(MealType.DINNER), listOf(Ingredient("rice", 700)), listOf(CookStep("Приготовить")), 10, 20)
        return base.copy(recipes = base.recipes + recipe, pantry = if (stock > 0) listOf(PantryItem("rice", stock)) else emptyList(), plan = Plan(listOf(Meal("m", 0, MealType.DINNER, recipe.id)), 1))
    }
    private inline fun error(block: () -> Unit) { try { block(); fail("Expected DomainError") } catch (_: DomainError) { } }
    @Test fun wholePacksAndPantryExample() {
        val state = riceState(); val row = Shopping.quote(state).rows.single()
        assertEquals(300L, row.needed); assertEquals(500L, row.amount)
        val bought = Engine.buy(state, row.id)
        assertEquals(900L, bought.stock("rice")); assertEquals(row.price, bought.spent())
        val cooked = Engine.cook(bought, "m"); assertEquals(200L, cooked.stock("rice"))
        assertEquals(cooked, Engine.cook(cooked, "m"))
        assertEquals(bought.pantry, Engine.undoCooking(cooked, "m").pantry)
    }
    @Test fun repeatPurchaseIsIdempotent() { val s = riceState(); val row = Shopping.quote(s).rows.single(); val b = Engine.buy(s, row.id); assertEquals(b, Engine.buy(b, row.id)); assertEquals(1, b.purchases.size) }
    @Test fun undoAndRebuyDoesNotDoubleCredit() {
        val s = riceState(); val row = Shopping.quote(s).rows.single(); val b = Engine.buy(s, row.id)
        val undone = Engine.undoPurchase(b, b.purchases.single().id); assertEquals(400L, undone.stock("rice")); assertEquals(0L, undone.spent())
        val rebought = Engine.buy(undone, Shopping.quote(undone).rows.first { it.purchase == null }.id)
        assertEquals(900L, rebought.stock("rice")); assertEquals(row.price, rebought.spent())
    }
    @Test fun cannotUndoConsumedPurchase() { val s = riceState(); val b = Engine.buy(s, Shopping.quote(s).rows.single().id); val cooked = Engine.cook(b, "m"); error { Engine.undoPurchase(cooked, b.purchases.single().id) }; assertEquals(200L, cooked.stock("rice")) }
    @Test fun editPurchaseOnlyAppliesDelta() { val s = riceState(); val b = Engine.buy(s, Shopping.quote(s).rows.single().id); val e = Engine.editPurchase(b, b.purchases.single().id, 900, 14900); assertEquals(1300L, e.stock("rice")); assertEquals(14900L, e.spent()); assertEquals(e, Engine.editPurchase(e, e.purchases.single().id, 900, 14900)) }
    @Test fun insufficientCookingIsAtomic() { val s = riceState(); error { Engine.cook(s, "m") }; assertEquals(400L, s.stock("rice")); assertTrue(s.cooking.isEmpty()) }
    @Test fun rejectNegativeAndZeroAmounts() { val s = riceState(); val row = Shopping.quote(s).rows.single(); for (q in listOf(-1L,0L,1_000_001L)) error { Engine.buy(s,row.id,q) }; error { Engine.setStock(s,PantryItem("rice",-1)) } }
    @Test fun shoppingOmitsSufficientStock() { val s = riceState(1000); assertTrue(Shopping.quote(s).rows.isEmpty()); assertEquals(300L, Engine.cook(s,"m").stock("rice")) }
    @Test fun noDoubleCountingBoughtCosts() { val s = riceState(); val before = Shopping.quote(s); val b = Engine.buy(s,before.rows.single().id); val after = Shopping.quote(b); assertEquals(before.total,after.total); assertEquals(0L,after.pending) }
    @Test fun unavailablePricesReportedNotFree() { val s=riceState().copy(offers=emptyList()); val q=Shopping.quote(s); assertEquals(setOf("rice"),q.missingFoods); assertFalse(Planner.issues(s,s.plan!!,q,false).isEmpty()) }
    @Test fun oneStoreLimitAndCheapestWholePack() { val s=riceState(0).copy(preferences=Preferences(maxStores=1),offers=listOf(Offer("a","rice",Store.PYATEROCHKA,500,10000),Offer("b","rice",Store.CHIZHIK,1000,15000))); val q=Shopping.quote(s); assertEquals(15000L,q.total); assertEquals(1000L,q.rows.single().amount) }
    @Test fun manualItemsHaveIndependentCostAndUndo() { val s=Engine.addManual(riceState(1000),ManualShopping("coffee","rice",500,Store.MAGNIT,10000)); val q=Shopping.quote(s); assertEquals(10000L,q.total); val b=Engine.buy(s,q.rows.single().id); assertEquals(1500L,b.stock("rice")); assertEquals(10000L,Shopping.quote(b).total); assertEquals(0L,Shopping.quote(b).pending); error { Engine.removeManual(b,"coffee") } }
    @Test fun nonBudgetManualIsNotIncluded() { val s=Engine.addManual(riceState(1000),ManualShopping("extra","rice",500,Store.MAGNIT,10000,false)); val b=Engine.buy(s,Shopping.quote(s).rows.single().id); assertEquals(0L,b.spent()); assertEquals(1500L,b.stock("rice")) }
    @Test fun allergiesAreStrict() { val s=AppState(preferences=Preferences(allergies=setOf("Молоко"))); assertFalse(s.allowed(s.recipe("cottageFruit"))); assertTrue(s.allowed(s.recipe("oatWater"))) }
    @Test fun noEquipmentOnlyColdMeals() { val s=AppState(preferences=Preferences(equipment=emptySet())); assertTrue(s.allowed(s.recipe("cottageFruit"))); assertFalse(s.allowed(s.recipe("chickenBuckwheat"))) }
    @Test fun alternativeEquipmentWorks() { val s=AppState(preferences=Preferences(equipment=setOf(Equipment.MULTICOOKER))); assertTrue(s.allowed(s.recipe("oatBanana"))); assertFalse(s.allowed(s.recipe("fishPotato"))) }
    @Test fun customAllergenCannotBeSaved() { val s=AppState(preferences=Preferences(allergies=setOf("Арахис"))); val r=s.recipe("cottageFruit").copy(id="custom",ingredients=listOf(Ingredient("peanut",50))); error { Engine.saveRecipe(s,r) } }
    @Test fun unknownCompositionBlockedWithAllergy() { val s=AppState(preferences=Preferences(allergies=setOf("Арахис"))); val f=Food("unknown","Соус",UnitOfFood.GRAM,1.0,.01,"Бакалея",compositionKnown=false); val r=s.recipe("cottageFruit").copy(ingredients=listOf(Ingredient(f.id,20))); assertFalse(s.copy(foods=s.foods+f).allowed(r)) }
    @Test fun cookingUndoUsesSnapshot() { var s=riceState(1000); s=Engine.cook(s,"m"); val changed=s.copy(recipes=s.recipes.map { if(it.id=="testRice") it.copy(ingredients=listOf(Ingredient("rice",100))) else it }); assertEquals(1000L,Engine.undoCooking(changed,"m").stock("rice")) }
    @Test fun newWeekKeepsStockAndPurchaseHistory() { val s=riceState(); val b=Engine.buy(s,Shopping.quote(s).rows.single().id); val n=Engine.newWeek(b,"2026-11-02"); assertEquals(900L,n.stock("rice")); assertEquals(0L,n.spent()); assertEquals(1,n.purchases.size); assertNull(n.plan); assertEquals(b.spent(),n.history.single().spent) }
    @Test fun missingOrStaleItemCannotBeBought() { error { Engine.buy(riceState(),"missing") } }
    @Test fun cookingProhibitedAfterAllergyChanged() { val s=AppState(plan=Plan(listOf(Meal("a",0,MealType.BREAKFAST,"cottageFruit")),1),pantry=listOf(PantryItem("cottage",1000),PantryItem("banana",1000))); error { Engine.cook(s.copy(preferences=s.preferences.copy(allergies=setOf("Молоко"))),"a") } }
    @Test fun randomizedAccountingConservation() {
        repeat(100) { seed -> val stock=(seed*7+100).toLong(); var s=riceState(stock); val row=Shopping.quote(s).rows.firstOrNull() ?: return@repeat; s=Engine.buy(s,row.id); val purchased=s.stock("rice"); s=Engine.editPurchase(s,s.purchases.last().id,row.amount+100,row.price+500); assertEquals(purchased+100,s.stock("rice")); s=Engine.undoPurchase(s,s.purchases.last().id); assertEquals(stock,s.stock("rice")); assertEquals(0L,s.spent()); StateCodec.validate(s) }
    }
}
