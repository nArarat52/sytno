package ru.sytno.core

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.Strictness
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.LocalDate

object StateCodec {
    const val MAX_BYTES = 5 * 1024 * 1024
    private val gson = GsonBuilder().setStrictness(Strictness.STRICT).disableHtmlEscaping().setPrettyPrinting().create()
    fun encode(state: AppState): String { validate(state); return gson.toJson(state) }
    fun decode(text: String): AppState {
        if (text.toByteArray(Charsets.UTF_8).size > MAX_BYTES) throw DomainError("Файл слишком большой (не больше 5 МБ)")
        return try {
            val root = JsonParser.parseString(text)
            val required = listOf("version", "weekId", "weekStart", "preferences", "foods", "recipes", "offers", "pantry", "purchases", "cooking", "manualShopping", "history")
            if (!root.isJsonObject || required.any { !root.asJsonObject.has(it) || root.asJsonObject[it].isJsonNull }) throw DomainError("Это не резервная копия Сытно")
            val state = gson.fromJson(text, AppState::class.java) ?: throw DomainError("Файл пуст")
            validate(state); state
        } catch (e: DomainError) { throw e } catch (_: Exception) { throw DomainError("Файл повреждён или имеет неподдерживаемый формат") }
    }
    fun validate(s: AppState) {
        try {
            fun check(value: Boolean, message: String) { if (!value) throw DomainError(message) }
            fun ids(values: List<String>) = values.size == values.toSet().size && values.all { it.isNotBlank() && it.length <= 200 }
            check(s.version == 1, "Неподдерживаемая версия резервной копии")
            check(s.weekId.isNotBlank() && s.weekId.length <= 200, "Некорректная неделя")
            LocalDate.parse(s.weekStart)
            val p = s.preferences
            check(p.budget in 100..100_000_000, "Бюджет должен быть от 1 до 1 000 000 ₽")
            check(p.stores.isNotEmpty() && p.stores.size <= 4 && p.stores.all { it in Store.entries }, "Выбери хотя бы один магазин")
            check(p.maxStores in 1..2, "Можно выбрать один или два магазина для закупки")
            check(p.calories == null || p.calories in 500..10_000, "Калории должны быть в диапазоне 500–10 000")
            check(p.protein == null || p.protein in 1..500, "Белок должен быть в диапазоне 1–500 г")
            check(p.maxMinutes in 1..180, "Время готовки должно быть от 1 до 180 минут")
            check(p.mealTypes.isNotEmpty() && p.mealTypes.all { it in MealType.entries }, "Выбери приёмы пищи")
            check(p.equipment.size <= Equipment.entries.size && p.equipment.all { it in Equipment.entries }, "Некорректная техника")
            check(p.city.length <= 100 && p.addresses.size <= 4 && p.addresses.values.all { it.length <= 300 }, "Слишком длинный адрес")
            check(p.allergies.size <= 50 && p.allergies.all { it.length in 1..100 }, "Некорректные аллергии")
            check(p.priceFeedUrl.length <= 2000, "Слишком длинная ссылка каталога")
            check(s.foods.size in 1..3000 && ids(s.foods.map { it.id }), "Некорректный список продуктов")
            val foodIds = s.foods.map { it.id }.toSet()
            s.foods.forEach { f -> check(f.name.length in 1..120 && f.unit in UnitOfFood.entries && f.category.length <= 100 &&
                f.caloriesPerUnit.isFinite() && f.caloriesPerUnit in 0.0..1000.0 && f.proteinPerUnit.isFinite() && f.proteinPerUnit in 0.0..100.0 && f.allergens.size <= 50, "Некорректные данные продукта") }
            check(p.excludedFoods.size <= 3000 && foodIds.containsAll(p.excludedFoods), "Неизвестные исключения")
            check(s.recipes.size in 1..2000 && ids(s.recipes.map { it.id }), "Некорректный список рецептов")
            s.recipes.forEach { r -> check(r.name.length in 1..120 && r.ingredients.size in 1..50 && r.ingredients.all { it.foodId in foodIds && it.amount in 1..1_000_000 } &&
                r.activeMinutes in 0..600 && r.totalMinutes in 0..1440 && r.mealTypes.isNotEmpty() && r.mealTypes.all { it in MealType.entries } &&
                r.steps.size <= 100 && r.steps.all { it.text.length in 1..3000 && it.timerSeconds in 0..86400 } &&
                (r.photo == null || r.photo in 0..5) && r.tools.size <= 10 && r.tools.all { tools -> tools.all { it in Equipment.entries } }, "Некорректный рецепт") }
            check(s.offers.size <= 30000 && ids(s.offers.map { it.id }), "Некорректный каталог цен")
            s.offers.forEach { o -> check(o.foodId in foodIds && o.packAmount in 1..1_000_000 && o.price in 0..100_000_000 && o.store in Store.entries && o.source in PriceSource.entries &&
                o.updatedAt >= 0 && o.city.length <= 100 && o.address.length <= 300 && o.url.length <= 2000, "Некорректная цена товара") }
            check(s.pantry.size <= 3000 && ids(s.pantry.map { it.foodId }), "Повторяющиеся продукты в запасах")
            s.pantry.forEach { i -> check(i.foodId in foodIds && i.amount in 1..1_000_000 && i.location.length <= 100, "Некорректный остаток"); i.expiry?.let { LocalDate.parse(it) } }
            check(s.purchases.size <= 20000 && ids(s.purchases.map { it.id }), "Некорректная история покупок")
            s.purchases.forEach { i -> check(i.foodId in foodIds && i.amount in 1..1_000_000 && i.price in 0..100_000_000 && i.store in Store.entries && i.weekId.length in 1..200 && i.itemId.length in 1..500, "Некорректная покупка") }
            val activeKeys = s.purchases.filterNot { it.reversed }.map { it.weekId + ":" + it.itemId }
            check(activeKeys.size == activeKeys.toSet().size, "Покупка зачислена дважды")
            check(s.manualShopping.size <= 1000 && ids(s.manualShopping.map { it.id }), "Некорректные ручные покупки")
            s.manualShopping.forEach { i -> check(i.foodId in foodIds && i.amount in 1..1_000_000 && i.price in 0..100_000_000 && i.store in Store.entries, "Некорректная позиция покупок") }
            val recipeIds = s.recipes.map { it.id }.toSet()
            s.plan?.let { plan -> check(plan.meals.size <= 28 && ids(plan.meals.map { it.id }), "Некорректный план");
                check(plan.meals.map { it.day to it.type }.toSet().size == plan.meals.size, "Повтор приёма пищи")
                plan.meals.forEach { m -> check(m.day in 0..6 && m.type in MealType.entries && m.recipeId in recipeIds && m.quarters in 1..16, "Некорректное блюдо в плане") } }
            check(s.cooking.size <= 10000 && ids(s.cooking.map { it.mealId }), "Блюдо списано дважды")
            s.cooking.forEach { c -> check(c.ingredients.size <= 50 && c.ingredients.all { it.key in foodIds && it.value in 1..4_000_000 }, "Некорректное списание")
                c.pantrySnapshot?.let { snapshot -> check(snapshot.size<=50 && snapshot.all { (id,p)->id in c.ingredients && p.foodId==id && p.amount in 1..1_000_000 && p.location.length<=100 },"Некорректная копия запасов");snapshot.values.forEach{it.expiry?.let{date->LocalDate.parse(date)}} }
            }
            check(s.history.size <= 104 && s.history.all { it.budget >= 0 && it.spent >= 0 }, "Некорректная история недель")
            s.timer?.let { check(it.deadline>=0&&it.pausedMillis in 0..86400000&&it.durationMillis in 1000..86400000,"Некорректный таймер") }
        } catch (e: DomainError) { throw e } catch (_: Exception) { throw DomainError("Некорректные или неполные данные") }
    }
}
data class LoadedState(val state: AppState, val warning: String? = null)
class StateStore(private val file: File) {
    private val backup = File(file.parentFile, file.name + ".bak")
    @Synchronized fun load(): LoadedState {
        if (!file.exists()) return if (backup.exists()) recover() else LoadedState(AppState())
        return runCatching { LoadedState(read(file)) }.getOrElse { recover() }
    }
    private fun recover(): LoadedState {
        val state = runCatching { read(backup) }.getOrNull()
        return if (state != null) LoadedState(state, "Основной файл повреждён. Восстановлена последняя рабочая копия.")
        else LoadedState(AppState(), "Не удалось прочитать сохранение. Повреждённые файлы сохранены; их можно восстановить из резервной копии.")
    }
    private fun read(source: File): AppState {
        if (source.length() > StateCodec.MAX_BYTES) throw DomainError("Сохранение слишком большое")
        return StateCodec.decode(source.readText(Charsets.UTF_8))
    }
    @Synchronized fun save(state: AppState) {
        val bytes = StateCodec.encode(state).toByteArray(Charsets.UTF_8)
        if (bytes.size > StateCodec.MAX_BYTES) throw DomainError("Слишком большой объём данных")
        file.parentFile?.mkdirs()
        val pending = File(file.parentFile, file.name + ".pending")
        FileChannel.open(pending.toPath(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE).use { channel ->
            val buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) channel.write(buffer); channel.force(true)
        }
        if (file.exists()) {
            if (runCatching { read(file) }.isSuccess) Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING)
            else Files.copy(file.toPath(), File(file.parentFile, "corrupt-${System.currentTimeMillis()}.json").toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        try { Files.move(pending.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
        catch (_: AtomicMoveNotSupportedException) { Files.move(pending.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING) }
    }
}
