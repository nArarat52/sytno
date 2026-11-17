package ru.sytno.core

import java.time.LocalDate

object Engine {
    private fun amount(value: Long) { if (value !in 1..1_000_000) throw DomainError("Укажи количество от 1 до 1 000 000") }
    private fun price(value: Long) { if (value !in 0..100_000_000) throw DomainError("Некорректная стоимость") }
    fun setStock(state: AppState, item: PantryItem): AppState {
        state.food(item.foodId)
        if (item.amount !in 0..1_000_000) throw DomainError("Некорректный остаток")
        item.expiry?.let { runCatching { LocalDate.parse(it) }.getOrElse { throw DomainError("Проверь срок годности") } }
        return state.copy(pantry = state.pantry.filterNot { it.foodId == item.foodId } + if (item.amount > 0) listOf(item) else emptyList())
    }
    private fun changeStock(state: AppState, id: String, delta: Long): AppState {
        val old = state.pantry.firstOrNull { it.foodId == id } ?: PantryItem(id, 0)
        val value = old.amount + delta
        if (value < 0) throw DomainError("Недостаточно продукта «${state.food(id).name}». Сначала уточни запасы или отмени готовку.")
        return setStock(state, old.copy(amount = value))
    }
    fun buy(state: AppState, rowId: String, quantity: Long? = null, actualPrice: Long? = null, now: Long = System.currentTimeMillis()): AppState {
        if (state.activePurchases().any { it.itemId == rowId }) return state
        val row = Shopping.quote(state).rows.firstOrNull { it.id == rowId && it.purchase == null } ?: throw DomainError("Список изменился. Обнови покупку.")
        val qty = quantity ?: row.amount; val cost = actualPrice ?: row.price
        amount(qty); price(cost)
        if (state.stock(row.foodId) > state.usableStock(row.foodId)) throw DomainError("Сначала убери просроченный остаток «${state.food(row.foodId).name}» из запасов или уточни срок годности.")
        val purchase = Purchase(newId(), state.weekId, row.id, row.foodId, qty, cost, row.store, row.includeBudget, now)
        val withStock = changeStock(state, row.foodId, qty)
        return withStock.copy(purchases = state.purchases + purchase)
    }
    fun editPurchase(state: AppState, id: String, quantity: Long, cost: Long): AppState {
        amount(quantity); price(cost)
        val old = state.activePurchases().firstOrNull { it.id == id } ?: throw DomainError("Покупка уже отменена")
        val updated = changeStock(state, old.foodId, quantity - old.amount)
        return updated.copy(purchases = updated.purchases.map { if (it.id == id) it.copy(amount = quantity, price = cost) else it })
    }
    fun undoPurchase(state: AppState, id: String): AppState {
        val old = state.activePurchases().firstOrNull { it.id == id } ?: return state
        val updated = changeStock(state, old.foodId, -old.amount)
        return updated.copy(purchases = updated.purchases.map { if (it.id == id) it.copy(reversed = true) else it })
    }
    fun cook(state: AppState, mealId: String, now: Long = System.currentTimeMillis()): AppState {
        if (state.isCooked(mealId)) return state
        val meal = state.plan?.meals?.firstOrNull { it.id == mealId } ?: throw DomainError("Блюда больше нет в плане")
        if (!state.allowed(state.recipe(meal.recipeId))) throw DomainError("Блюдо не подходит под текущие ограничения или технику")
        val use = state.ingredients(meal)
        val missing = use.filter { (id, qty) -> state.usableStock(id) < qty }
        if (missing.isNotEmpty()) throw DomainError("Не хватает свежих продуктов: " + missing.entries.joinToString { (id, qty) -> "${state.food(id).name} (${qty - state.usableStock(id)} ${state.food(id).unit.title})" })
        var result = state
        use.forEach { (id, qty) -> result = changeStock(result, id, -qty) }
        return result.copy(cooking = state.cooking + Cooking(mealId, use, now,state.pantry.filter{it.foodId in use}.associateBy{it.foodId}))
    }
    fun undoCooking(state: AppState, mealId: String): AppState {
        val record = state.cooking.firstOrNull { it.mealId == mealId } ?: return state
        var result = state
        record.ingredients.forEach { (id, qty) ->
            val old=result.pantry.firstOrNull{it.foodId==id};val snapshot=record.pantrySnapshot?.get(id)
            if(snapshot==null) result=changeStock(result,id,qty)
            else {
                val expiry=listOfNotNull(old?.expiry,snapshot.expiry).minOrNull()
                result=setStock(result,(old?:snapshot).copy(amount=(old?.amount?:0)+qty,expiry=expiry,useSoon=old?.useSoon==true||snapshot.useSoon))
            }
        }
        return result.copy(cooking = result.cooking.filterNot { it.mealId == mealId })
    }
    fun pin(state: AppState, mealId: String): AppState = state.copy(plan = state.plan?.copy(meals = state.plan.meals.map {
        if (it.id == mealId) it.copy(pinned = !it.pinned) else it
    }))
    fun saveRecipe(state: AppState, recipe: Recipe): AppState {
        if (recipe.name.isBlank() || recipe.name.length > 120 || recipe.ingredients.isEmpty()) throw DomainError("Укажи название и ингредиенты")
        if (recipe.ingredients.size > 50 || recipe.activeMinutes !in 0..600 || recipe.mealTypes.isEmpty()) throw DomainError("Проверь параметры рецепта")
        recipe.ingredients.forEach { amount(it.amount); state.food(it.foodId) }
        if (!state.allowed(recipe)) throw DomainError("В рецепте есть исключённый продукт, аллерген или требуется другая техника")
        return state.copy(recipes = state.recipes.filterNot { it.id == recipe.id } + recipe)
    }
    fun addManual(state: AppState, item: ManualShopping): AppState {
        amount(item.amount); price(item.price); state.food(item.foodId)
        return state.copy(manualShopping = state.manualShopping.filterNot { it.id == item.id } + item)
    }
    fun manualItem(state:AppState,foodId:String,quantity:Long,store:Store,actualPrice:Long?=null,includeBudget:Boolean=true):ManualShopping {
        state.food(foodId); amount(quantity)
        if(actualPrice!=null){price(actualPrice);return ManualShopping(newId(),foodId,quantity,store,actualPrice,includeBudget)}
        fun packs(offer:Offer)=(quantity+offer.packAmount-1)/offer.packAmount
        val offer=Shopping.availableOffers(state,foodId,setOf(store)).minByOrNull{packs(it)*it.price}?:throw DomainError("Укажи стоимость продукта")
        val count=packs(offer);val qty=count*offer.packAmount;val cost=count*offer.price
        amount(qty);price(cost)
        return ManualShopping(newId(),foodId,qty,store,cost,includeBudget,!offer.isFresh(System.currentTimeMillis()))
    }
    fun removeManual(state: AppState, id: String): AppState {
        if (state.activePurchases().any { it.itemId == "manual:$id" }) throw DomainError("Сначала отмени отметку покупки")
        return state.copy(manualShopping = state.manualShopping.filterNot { it.id == id })
    }
    fun newWeek(state: AppState, start: String): AppState {
        runCatching { LocalDate.parse(start) }.getOrElse { throw DomainError("Некорректная дата недели") }
        val record = WeekRecord(state.weekId, state.weekStart, state.spent(), state.preferences.budget, state.plan?.meals?.count { state.isCooked(it.id) } ?: 0)
        return state.copy(weekId = newId(), weekStart = start, plan = null, manualShopping = emptyList(), history = (state.history + record).takeLast(104))
    }
    fun replacement(state: AppState, mealId: String?, recipeId: String, day: Int = 0, type: MealType = MealType.DINNER, requestedQuarters:Int?=null): PlanResult {
        val recipe = state.recipe(recipeId)
        if (!state.allowed(recipe)) throw DomainError("Блюдо не подходит под ограничения или доступную технику")
        if(day !in 0..6 || requestedQuarters!=null&&requestedQuarters !in 1..16)throw DomainError("Проверь день и количество порций")
        val old = state.plan?.meals?.firstOrNull { it.id == mealId }
        val targetSlot=old?:state.plan?.meals?.firstOrNull{it.day==day&&it.type==type}
        if(targetSlot!=null&&state.isCooked(targetSlot.id))throw DomainError("Приготовленное блюдо нельзя заменить")
        if (old != null && state.isCooked(old.id)) throw DomainError("Приготовленное блюдо нельзя заменить")
        val targetType = old?.type ?: type
        if (targetType !in recipe.mealTypes) throw DomainError("Рецепт не предназначен для этого приёма пищи")
        val base = Meal(old?.id ?: newId(), old?.day ?: day, targetType, recipeId, 4, old?.pinned ?: true)
        val oldKcal = old?.let { state.nutrition(it).calories }
        val baseKcal = state.nutrition(base).calories
        val quarters = requestedQuarters ?: if (oldKcal != null && baseKcal > 0) kotlin.math.round(oldKcal / baseKcal * 4).toInt().coerceIn(1, 16) else 4
        val meal = base.copy(quarters = quarters)
        val meals = (state.plan?.meals.orEmpty().filterNot { it.id == old?.id || (old == null && it.day == day && it.type == type) } + meal).sortedWith(compareBy({ it.day }, { it.type.ordinal }))
        val plan = Plan(meals, System.currentTimeMillis()); val quote = Shopping.quote(state, plan)
        return PlanResult(plan, quote, Planner.issues(state, plan, quote, checkNutrition = state.plan != null))
    }
}
