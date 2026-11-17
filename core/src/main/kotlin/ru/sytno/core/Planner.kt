package ru.sytno.core

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

object Planner {
    fun generate(state: AppState, seed: Int = 1, attempts: Int = 24): PlanResult {
        StateCodec.validate(state)
        val types = state.preferences.mealTypes.sortedBy { it.ordinal }
        val pools = types.associateWith { type -> state.recipes.filter { type in it.mealTypes && state.allowed(it) } }
        pools.entries.firstOrNull { it.value.isEmpty() }?.let { throw DomainError("Нет подходящих блюд для «${it.key.title}». Проверь исключения, время и технику.") }
        val fixed = state.plan?.meals.orEmpty().filter { state.isCooked(it.id) || it.pinned }
        fixed.filterNot { state.isCooked(it.id) }.firstOrNull { !state.allowed(state.recipe(it.recipeId)) }?.let {
            throw DomainError("Закреплённое блюдо «${state.recipe(it.recipeId).name}» конфликтует с настройками. Сними закрепление или измени ограничения.")
        }
        var best: PlanResult? = null; var bestScore = Double.POSITIVE_INFINITY
        repeat(attempts.coerceIn(1, 64)) { attempt ->
            val random = Random(seed + attempt * 7919)
            val used = fixed.groupingBy { it.recipeId }.eachCount().toMutableMap(); val meals = mutableListOf<Meal>()
            for (day in 0..6) {
                val dayMeals = types.map { type ->
                    val locked = fixed.firstOrNull { it.day == day && it.type == type }
                    if (locked != null) locked else {
                        val candidates = pools.getValue(type).filter { state.preferences.allowRepeats || (used[it.id] ?: 0) == 0 }
                        if (candidates.isEmpty()) throw DomainError("Для разнообразной недели не хватает рецептов. Разреши повторы.")
                        val ranked = candidates.sortedBy { recipe ->
                            val m = Meal("candidate", day, type, recipe.id)
                            val n = state.nutrition(m)
                            val stockBonus = recipe.ingredients.sumOf { ingredient ->
                                if (state.usableStock(ingredient.foodId) == 0L) 0.0 else {
                                    val pantry = state.pantry.first { it.foodId == ingredient.foodId }
                                    .08 + if (pantry.useSoon || pantry.expiry?.let { java.time.LocalDate.parse(it) <= java.time.LocalDate.now().plusDays(3) } == true) .5 / (day + 1) else 0.0
                                }
                            }
                            Shopping.mealCost(state, m).toDouble() / n.calories.coerceAtLeast(100.0) + (used[recipe.id] ?: 0) * .12 - stockBonus
                        }
                        val recipe = ranked.take(if (attempt % 4 == 0) 3 else 9).random(random)
                        used[recipe.id] = (used[recipe.id] ?: 0) + 1
                        val base = Meal("${state.weekId}:$day:${type.name}", day, type, recipe.id)
                        val calories = state.nutrition(base).calories
                        val q = state.preferences.calories?.let { (it * type.share / calories.coerceAtLeast(1.0) * 4).roundToInt().coerceIn(1, 16) } ?: 4
                        base.copy(quarters = q)
                    }
                }.toMutableList()
                val forbidden = (fixed + meals).filter { m -> dayMeals.none { it.id == m.id } }.map { it.recipeId }.toSet()
                if (state.preferences.calories != null || state.preferences.protein != null) improveDay(state, dayMeals, fixed.map { it.id }.toSet(), pools, random, forbidden)
                meals += dayMeals
                used.clear(); (fixed + meals).distinctBy { it.id }.forEach { used[it.recipeId] = (used[it.recipeId] ?: 0) + 1 }
            }
            // Historical prepared meals remain available even after a meal type was disabled.
            meals += fixed.filter { old -> meals.none { it.id == old.id } }
            val plan = Plan(meals.sortedWith(compareBy({ it.day }, { it.type.ordinal })), System.currentTimeMillis())
            val quote = Shopping.quote(state, plan)
            val issues = issues(state, plan, quote)
            val nutritionPenalty = (0..6).sumOf { day -> dayScore(state, meals.filter { it.day == day }) }
            val over = (quote.total - state.preferences.budget).coerceAtLeast(0).toDouble() / state.preferences.budget.coerceAtLeast(1)
            val score = issues.size * 1000 + over * 10000 + nutritionPenalty * 100 + quote.total / 100000.0
            if (score < bestScore) { bestScore = score; best = PlanResult(plan, quote, issues) }
        }
        return best ?: throw DomainError("Не удалось составить рацион")
    }
    private fun improveDay(state: AppState, meals: MutableList<Meal>, fixed: Set<String>, pools: Map<MealType, List<Recipe>>, random: Random, forbidden:Set<String>) {
        repeat(36) {
            val current = dayScore(state, meals)
            var best = current; var change: Pair<Int, Meal>? = null
            meals.forEachIndexed { index, meal ->
                if (meal.id !in fixed) {
                    val variants = mutableListOf<Meal>()
                    for (delta in listOf(-1, 1)) if (meal.quarters + delta in 1..16) variants += meal.copy(quarters = meal.quarters + delta)
                    pools.getValue(meal.type).shuffled(random).take(3).forEach { recipe ->
                        val base = meal.copy(recipeId = recipe.id, quarters = 4)
                        val desired = state.preferences.calories?.times(meal.type.share) ?: state.nutrition(meal).calories
                        val q = (desired / state.nutrition(base).calories.coerceAtLeast(1.0) * 4).roundToInt().coerceIn(1, 16)
                        if (state.preferences.allowRepeats || (recipe.id !in forbidden && meals.none { it.id != meal.id && it.recipeId == recipe.id })) variants += base.copy(quarters = q)
                    }
                    variants.forEach { variant ->
                        val list = meals.toMutableList(); list[index] = variant
                        val score = dayScore(state, list)
                        if (score + .00001 < best) { best = score; change = index to variant }
                    }
                }
            }
            if (change == null) return
            meals[change!!.first] = change!!.second
        }
    }
    private fun dayScore(state: AppState, meals: List<Meal>): Double {
        val nutrition = meals.fold(Nutrition(0.0, 0.0)) { a, b -> a + state.nutrition(b) }
        val share = state.preferences.mealTypes.sumOf { it.share }
        val cal = state.preferences.calories?.times(share)
        val protein = state.preferences.protein?.times(share)
        val calError = cal?.let { ((nutrition.calories - it) / it).pow(2) * 5 } ?: 0.0
        val proteinError = protein?.let { ((it - nutrition.protein).coerceAtLeast(0.0) / it).pow(2) * 9 } ?: 0.0
        return calError + proteinError
    }
    fun issues(state: AppState, plan: Plan, quote: ShoppingQuote, checkNutrition: Boolean = true): List<String> {
        val result = mutableListOf<String>()
        if (!state.preferences.allowRepeats && plan.meals.map { it.recipeId }.toSet().size != plan.meals.size) result += "В закреплённых блюдах есть повторы. Сними закрепление или разреши повторы."
        if (quote.missingFoods.isNotEmpty()) result += "Нет цены или наличия: " + quote.missingFoods.joinToString { state.food(it).name }
        if (quote.total > state.preferences.budget) result += "Бюджет превышен на ${Numbers.money(quote.total - state.preferences.budget)}"
        if (checkNutrition) {
            val share = state.preferences.mealTypes.sumOf { it.share }
            val calories = state.preferences.calories?.times(share); val protein = state.preferences.protein?.times(share)
            val badCalories = mutableListOf<Int>(); val badProtein = mutableListOf<Int>()
            for (day in 0..6) {
                val total = plan.meals.filter { it.day == day }.fold(Nutrition(0.0, 0.0)) { a, m -> a + state.nutrition(m) }
                if (calories != null && abs(total.calories - calories) > calories * .10) badCalories += day + 1
                if (protein != null && total.protein + .01 < protein) badProtein += day + 1
            }
            if (badCalories.isNotEmpty()) result += "Не получилось попасть в калории (±10%) в дни: ${badCalories.joinToString()}"
            if (badProtein.isNotEmpty()) result += "Недостаточно белка в дни: ${badProtein.joinToString()}"
        }
        return result
    }
}
