package ru.sytno.core

object Shopping {
    fun availableOffers(state:AppState,foodId:String,stores:Set<Store> = state.preferences.stores):List<Offer> {
        val scoped=state.offers.filter { it.foodId==foodId && it.store in stores &&
            (it.source==PriceSource.ESTIMATE || ((it.city.isBlank() || it.city.equals(state.preferences.city,true)) && (it.address.isBlank() || it.address==state.preferences.addresses[it.store].orEmpty()))) }
        return scoped.groupBy { it.store }.values.flatMap { offers ->
            val manual=offers.filter { it.source==PriceSource.MANUAL }
            val confirmed=offers.filter { it.source==PriceSource.CATALOG }
            when { manual.isNotEmpty()->manual.filter { it.updatedAt==manual.maxOf { o->o.updatedAt } }; confirmed.isNotEmpty()->confirmed.filter { it.updatedAt==confirmed.maxOf { o->o.updatedAt } }; else->offers }
        }.filter { it.available }
    }
    fun required(state: AppState, plan: Plan? = state.plan): Map<String, Long> {
        val amounts = mutableMapOf<String, Long>()
        plan?.meals?.filterNot { state.isCooked(it.id) }?.forEach { m -> state.ingredients(m).forEach { (id, qty) -> amounts[id] = (amounts[id] ?: 0) + qty } }
        return amounts
    }
    fun quote(state: AppState, plan: Plan? = state.plan): ShoppingQuote {
        val purchases = state.activePurchases()
        val manualPending=state.manualShopping.filter { m -> purchases.none { it.itemId == "manual:${m.id}" } }
        val manualAmounts=manualPending.groupBy { it.foodId }.mapValues { (_,items)->items.sumOf { it.amount } }
        val needed = required(state, plan).mapValues { (id, qty) -> (qty - state.usableStock(id) - (manualAmounts[id]?:0)).coerceAtLeast(0) }.filterValues { it > 0 }
        val stores = state.preferences.stores.sortedBy { it.ordinal }
        val combinations = stores.map { setOf(it) }.toMutableList()
        if (state.preferences.maxStores > 1) stores.forEachIndexed { i, s -> stores.drop(i + 1).forEach { combinations.add(setOf(s, it)) } }
        var bestRows = emptyList<ShoppingRow>(); var bestMissing = needed.keys; var bestCost = Long.MAX_VALUE
        for (combination in combinations) {
            val missing = mutableSetOf<String>()
            val rows = needed.mapNotNull { (id, qty) ->
                val offer = availableOffers(state,id,combination)
                    .minWithOrNull(compareBy<Offer> { packs(qty, it.packAmount) * it.price }.thenBy { it.source == PriceSource.ESTIMATE }.thenBy { it.store.ordinal })
                if (offer == null) { missing.add(id); null } else {
                    val count = packs(qty, offer.packAmount)
                    ShoppingRow("auto:${state.weekId}:$id:${purchases.count { it.foodId == id }}", id, qty, count * offer.packAmount, count * offer.price, offer.store, offer)
                }
            }
            val cost = rows.sumOf { it.price }
            if (missing.size < bestMissing.size || (missing.size == bestMissing.size && cost < bestCost)) { bestRows = rows; bestMissing = missing; bestCost = cost }
        }
        val manual = manualPending.map {
            ShoppingRow("manual:${it.id}", it.foodId, it.amount, it.amount, it.price, it.store, includeBudget = it.includeBudget, estimated=it.estimated)
        }
        val bought = purchases.map { ShoppingRow(it.itemId, it.foodId, it.amount, it.amount, it.price, it.store, purchase = it, includeBudget = it.includeBudget) }
        return ShoppingQuote(bestRows + manual + bought, bestMissing, state.spent())
    }
    private fun packs(quantity: Long, pack: Long): Long = (quantity + pack - 1) / pack
    fun mealCost(state: AppState, meal: Meal): Long = state.ingredients(meal).entries.sumOf { (id, qty) ->
        val offer = availableOffers(state,id).minByOrNull { it.price.toDouble() / it.packAmount }
        if (offer == null) 0L else (offer.price.toDouble() * qty / offer.packAmount).toLong()
    }
}
