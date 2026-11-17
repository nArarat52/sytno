package ru.sytno.core

import java.time.LocalDate
import java.util.UUID
import kotlin.math.ceil

fun newId(): String = UUID.randomUUID().toString()
enum class Store(val title: String, val website: String) {
    PYATEROCHKA("Пятёрочка", "https://5ka.ru/"), CHIZHIK("Чижик", "https://chizhik.club/"),
    MAGNIT("Магнит", "https://magnit.ru/"), PEREKRESTOK("Перекрёсток", "https://www.perekrestok.ru/")
}
enum class Equipment(val title: String) { STOVE("Плита"), OVEN("Духовка"), MICROWAVE("Микроволновка"), MULTICOOKER("Мультиварка"), AIR_FRYER("Аэрогриль"), BLENDER("Блендер"), KETTLE("Чайник") }
enum class UnitOfFood(val title: String) { GRAM("г"), ML("мл"), PIECE("шт.") }
enum class MealType(val title: String, val share: Double) { BREAKFAST("Завтрак", .25), LUNCH("Обед", .35), DINNER("Ужин", .30), SNACK("Перекус", .10) }
enum class PriceSource(val title: String) { ESTIMATE("Оценка"), MANUAL("Введено вручную"), CATALOG("Из каталога") }
data class Food(
    val id: String, val name: String, val unit: UnitOfFood, val caloriesPerUnit: Double,
    val proteinPerUnit: Double, val category: String, val allergens: Set<String> = emptySet(),
    val compositionKnown: Boolean = true, val custom: Boolean = false
)
data class Ingredient(val foodId: String, val amount: Long)
data class CookStep(val text: String, val timerSeconds: Int = 0)
data class Recipe(
    val id: String, val name: String, val mealTypes: Set<MealType>, val ingredients: List<Ingredient>,
    val steps: List<CookStep>, val activeMinutes: Int, val totalMinutes: Int,
    val tools: List<Set<Equipment>> = emptyList(), val photo: Int? = null, val custom: Boolean = false
)
data class Offer(
    val id: String, val foodId: String, val store: Store, val packAmount: Long, val price: Long,
    val source: PriceSource = PriceSource.ESTIMATE, val updatedAt: Long = 0,
    val city: String = "", val address: String = "", val url: String = "", val available: Boolean = true
) {
    fun isFresh(now: Long): Boolean = source != PriceSource.ESTIMATE && updatedAt > 0 && updatedAt <= now && now - updatedAt <= 86_400_000
}
data class PantryItem(val foodId: String, val amount: Long, val expiry: String? = null, val useSoon: Boolean = false, val location: String = "Дома")
data class Preferences(
    val budget: Long = 400_000, val city: String = "", val stores: Set<Store> = setOf(Store.PYATEROCHKA, Store.CHIZHIK),
    val maxStores: Int = 2, val calories: Int? = null, val protein: Int? = null, val maxMinutes: Int = 30,
    val equipment: Set<Equipment> = setOf(Equipment.STOVE), val allergies: Set<String> = emptySet(),
    val excludedFoods: Set<String> = emptySet(), val mealTypes: Set<MealType> = MealType.entries.toSet(),
    val allowRepeats: Boolean = true, val addresses: Map<Store, String> = emptyMap(), val loyaltyCard: Boolean = false,
    val priceFeedUrl: String = ""
)
data class Meal(val id: String, val day: Int, val type: MealType, val recipeId: String, val quarters: Int = 4, val pinned: Boolean = false)
data class Plan(val meals: List<Meal>, val generatedAt: Long)
data class ManualShopping(val id: String, val foodId: String, val amount: Long, val store: Store, val price: Long, val includeBudget: Boolean = true, val estimated:Boolean=false)
data class Purchase(
    val id: String, val weekId: String, val itemId: String, val foodId: String, val amount: Long, val price: Long,
    val store: Store, val includeBudget: Boolean, val createdAt: Long, val reversed: Boolean = false
)
data class Cooking(val mealId: String, val ingredients: Map<String, Long>, val createdAt: Long, val pantrySnapshot:Map<String,PantryItem>?=null)
data class WeekRecord(val weekId: String, val start: String, val spent: Long, val budget: Long, val cooked: Int)
data class TimerRecord(val deadline:Long=0,val pausedMillis:Long=0,val durationMillis:Long=600000)
data class AppState(
    val version: Int = 1, val onboardingComplete: Boolean = false, val weekId: String = newId(),
    val weekStart: String = LocalDate.now().toString(), val preferences: Preferences = Preferences(),
    val foods: List<Food> = Catalog.foods, val recipes: List<Recipe> = Catalog.recipes,
    val offers: List<Offer> = Catalog.offers, val pantry: List<PantryItem> = emptyList(), val plan: Plan? = null,
    val manualShopping: List<ManualShopping> = emptyList(), val purchases: List<Purchase> = emptyList(),
    val cooking: List<Cooking> = emptyList(), val history: List<WeekRecord> = emptyList(), val timer:TimerRecord?=null
) {
    fun food(id: String): Food = foods.firstOrNull { it.id == id } ?: throw DomainError("Продукт больше не существует")
    fun recipe(id: String): Recipe = recipes.firstOrNull { it.id == id } ?: throw DomainError("Рецепт больше не существует")
    fun stock(id: String): Long = pantry.firstOrNull { it.foodId == id }?.amount ?: 0
    fun usableStock(id: String, today: LocalDate = LocalDate.now()): Long = pantry.firstOrNull { it.foodId == id && (it.expiry == null || !LocalDate.parse(it.expiry).isBefore(today)) }?.amount ?: 0
    fun activePurchases(): List<Purchase> = purchases.filter { it.weekId == weekId && !it.reversed }
    fun spent(): Long = activePurchases().filter { it.includeBudget }.sumOf { it.price }
    fun isCooked(mealId: String): Boolean = cooking.any { it.mealId == mealId }
}
data class Nutrition(val calories: Double, val protein: Double) {
    operator fun plus(other: Nutrition) = Nutrition(calories + other.calories, protein + other.protein)
}
fun AppState.ingredients(meal: Meal): Map<String, Long> = recipe(meal.recipeId).ingredients.groupBy { it.foodId }.mapValues { (_, v) ->
    ceil(v.sumOf { it.amount }.toDouble() * meal.quarters / 4.0).toLong()
}
fun AppState.nutrition(meal: Meal): Nutrition = ingredients(meal).entries.fold(Nutrition(0.0, 0.0)) { n, (id, amount) ->
    val food = food(id); n + Nutrition(food.caloriesPerUnit * amount, food.proteinPerUnit * amount)
}
fun AppState.dailyNutrition(day: Int): Nutrition = plan?.meals?.filter { it.day == day }?.fold(Nutrition(0.0, 0.0)) { n, m -> n + nutrition(m) } ?: Nutrition(0.0, 0.0)
fun AppState.allowed(recipe: Recipe): Boolean = recipe.activeMinutes <= preferences.maxMinutes &&
    (recipe.tools.isEmpty() || recipe.tools.any { preferences.equipment.containsAll(it) }) && recipe.ingredients.all { ingredient ->
        val food = food(ingredient.foodId)
        food.id !in preferences.excludedFoods && food.allergens.none { it in preferences.allergies } &&
            (preferences.allergies.isEmpty() || food.compositionKnown)
    }
class DomainError(message: String) : IllegalArgumentException(message)
data class ShoppingRow(
    val id: String, val foodId: String, val needed: Long, val amount: Long, val price: Long, val store: Store,
    val offer: Offer? = null, val purchase: Purchase? = null, val includeBudget: Boolean = true, val estimated:Boolean=false
)
data class ShoppingQuote(val rows: List<ShoppingRow>, val missingFoods: Set<String>, val spent: Long) {
    val pending: Long get() = rows.filter { it.purchase == null && it.includeBudget }.sumOf { it.price }
    val total: Long get() = spent + pending
    val estimated: Boolean get() = rows.any { it.purchase == null && (it.estimated || it.offer?.isFresh(System.currentTimeMillis()) == false) }
}
data class PlanResult(val plan: Plan, val quote: ShoppingQuote, val issues: List<String>) { val success: Boolean get() = issues.isEmpty() }
