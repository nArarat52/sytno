@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package ru.sytno.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.sytno.core.*
import java.time.LocalDate

fun savePreferences(vm:AppViewModel,prefs:Preferences,onSaved:()->Unit) {
    vm.mutate("Настройки сохранены",onSaved){current->
        var next=current.copy(preferences=prefs)
        StateCodec.validate(next)
        next.plan?.let{plan->next=next.copy(plan=plan.copy(meals=plan.meals.filter{next.isCooked(it.id)||next.allowed(next.recipe(it.recipeId))}))}
        next
    }
}
@Composable fun BudgetScreen(state:AppState,vm:AppViewModel,onBack:()->Unit,onSaved:()->Unit,onGenerate:()->Unit) {
    var budget by rememberSaveable{mutableStateOf(Numbers.inputMoney(state.preferences.budget))}
    var start by rememberSaveable{mutableStateOf(state.weekStart)}
    var types by rememberSaveable{mutableStateOf(state.preferences.mealTypes)}
    fun save(){runCatching{val value=Numbers.parseMoney(budget);val date=LocalDate.parse(start).toString();if(value<100)throw DomainError("Бюджет должен быть больше нуля");val prefs=state.preferences.copy(budget=value,mealTypes=types);vm.mutate(onSuccess={if(state.onboardingComplete)onGenerate()else onSaved()}){current->val updated=current.copy(preferences=prefs,weekStart=date);StateCodec.validate(updated);updated}}.onFailure(vm::report)}
    SytnoScreen("Начнём с бюджета","На какую сумму планируем покупки?",back=onBack,footer={PrimaryButton(if(state.onboardingComplete)"Сохранить и пересчитать"else"Продолжить",::save,enabled=!vm.busy)}){
        item{AmountCard("Бюджет на 7 дней",runCatching{Numbers.money(Numbers.parseMoney(budget))}.getOrDefault("—"))}
        item{Field("Бюджет недели, ₽",budget,{budget=it},numeric=true)}
        item{FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("3000","4000","5000").forEach{value->Choice("$value ₽",budget==value,{budget=value})}}}
        item{Field("Начало недели · ГГГГ-ММ-ДД",start,{start=it})}
        item{Section("Какие приёмы пищи")}
        item{FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){MealType.entries.forEach{type->Choice(type.title,type in types,{types=if(type in types)types-type else types+type})}}}
        item{Notice("Считаем сумму на кассе","Учитываем целые упаковки, уже купленное и продукты дома. Бюджет — на одного человека.")}
        if(state.spent()>0)item{Summary("Уже куплено",Numbers.money(state.spent()))}
    }
}
@Composable fun StoresScreen(state:AppState,vm:AppViewModel,onBack:()->Unit,onSaved:()->Unit) {
    var city by rememberSaveable{mutableStateOf(state.preferences.city)}
    var stores by rememberSaveable{mutableStateOf(state.preferences.stores)}
    var count by rememberSaveable{mutableIntStateOf(state.preferences.maxStores)}
    var addresses by rememberSaveable{mutableStateOf(state.preferences.addresses)}
    var card by rememberSaveable{mutableStateOf(state.preferences.loyaltyCard)}
    SytnoScreen("Где покупаешь?","Выбери доступные магазины.",back=onBack,footer={PrimaryButton(if(state.onboardingComplete)"Сохранить магазины"else"Продолжить",{savePreferences(vm,state.preferences.copy(city=city.trim(),stores=stores,maxStores=count,addresses=addresses,loyaltyCard=card),onSaved)},enabled=!vm.busy)}){
        item{Field("Город",city,{city=it})}
        items(Store.entries,key={it.name}){store->Toggle(store.title,store.website,store in stores,{selected->stores=if(selected)stores+store else stores-store})}
        item{Section("Как удобнее покупать")};item{FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){Choice("Один магазин",count==1,{count=1});Choice("До двух",count==2,{count=2})}}
        item{Section("Конкретные магазины")}
        items(stores.sortedBy{it.ordinal},key={"address-${it.name}"}){store->Field("Адрес · ${store.title}",addresses[store].orEmpty(),{addresses=addresses+(store to it)})}
        item{Toggle("Есть карта X5 Клуба","Условия скидки проверяй в цене товара",card,{card=it})}
        item{Notice("Цены привязаны к месту покупки","При импорте каталога учитываются город и адрес. Встроенные цены — ориентировочные.",warning=true)}
    }
}
@Composable fun NutritionScreen(state:AppState,vm:AppViewModel,onBack:()->Unit,onSaved:()->Unit,onEquipment:()->Unit) {
    var enabled by rememberSaveable{mutableStateOf(state.preferences.calories!=null||state.preferences.protein!=null)}
    var calories by rememberSaveable{mutableStateOf(state.preferences.calories?.toString()?:"2100")}
    var protein by rememberSaveable{mutableStateOf(state.preferences.protein?.toString()?:"120")}
    var minutes by rememberSaveable{mutableStateOf(state.preferences.maxMinutes.toString())}
    var repeats by rememberSaveable{mutableStateOf(state.preferences.allowRepeats)}
    fun save(){runCatching{
        val c=if(enabled&&calories.isNotBlank())calories.toIntOrNull()?:throw DomainError("Проверь количество калорий")else null
        val p=if(enabled&&protein.isNotBlank())protein.toIntOrNull()?:throw DomainError("Проверь количество белка")else null
        val time=minutes.toIntOrNull()?:throw DomainError("Проверь время готовки")
        savePreferences(vm,state.preferences.copy(calories=c,protein=p,maxMinutes=time,allowRepeats=repeats),onSaved)
    }.onFailure(vm::report)}
    SytnoScreen("Питание под тебя","Цели необязательны — можешь просто планировать еду.",back=onBack,footer={PrimaryButton(if(state.onboardingComplete)"Сохранить настройки"else"Продолжить",::save,enabled=!vm.busy)}){
        item{Toggle("Учитывать калории и белок","Ориентиры задаёшь самостоятельно",enabled,{enabled=it})}
        if(enabled){item{Field("Калории в день · можно оставить пустым",calories,{calories=it},numeric=true)};item{Field("Белок в день, г · можно оставить пустым",protein,{protein=it},numeric=true)};item{Text("Калории подбираются с допуском ±10%, белок — не ниже указанного значения. Для части дня цели учитываются пропорционально выбранным приёмам пищи.",style=MaterialTheme.typography.bodySmall,color=C.Secondary)}}
        item{Field("Активная готовка на блюдо, минут",minutes,{minutes=it},numeric=true)}
        item{FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(15,30,45).forEach{n->Choice("$n минут",minutes==n.toString(),{minutes=n.toString()})}}}
        item{Toggle("Разрешить повторы","Проще готовить и меньше разных упаковок",repeats,{repeats=it})}
        item{LinkRow("Что есть на кухне",state.preferences.equipment.joinToString{it.title}.ifEmpty{"Только блюда без готовки"},onEquipment)}
        if(state.onboardingComplete)item{Notice("Неподходящие блюда уберём","После изменения ограничений пересчитай неделю, чтобы заполнить недостающее меню.")}
    }
}
@Composable fun EquipmentScreen(state:AppState,vm:AppViewModel,onBack:()->Unit) {
    var selected by rememberSaveable{mutableStateOf(state.preferences.equipment)}
    SytnoScreen("Что есть на кухне?","Предложим блюда для твоей техники.",back=onBack,footer={PrimaryButton("Сохранить технику",{savePreferences(vm,state.preferences.copy(equipment=selected),onBack)},enabled=!vm.busy)}){
        items(Equipment.entries,key={it.name}){equipment->Toggle(equipment.title,checked=equipment in selected,onChecked={yes->selected=if(yes)selected+equipment else selected-equipment})}
        item{Notice(if(selected.isEmpty())"Можно и без техники"else"Техника учитывается в подборе",if(selected.isEmpty())"Останутся блюда без термической обработки: салаты, творог, йогурт и другие подходящие варианты."else"Не будем предлагать запекание без духовки или аэрогриля.")}
    }
}
@Composable fun RestrictionsScreen(state:AppState,vm:AppViewModel,onBack:()->Unit,onSaved:()->Unit) {
    var allergies by rememberSaveable{mutableStateOf(state.preferences.allergies)}
    var excluded by rememberSaveable{mutableStateOf(state.preferences.excludedFoods)}
    var query by rememberSaveable{mutableStateOf("")}
    SytnoScreen("Что исключаем?","Аллергии и нелюбимые продукты хранятся отдельно.",back=onBack,footer={PrimaryButton(if(state.onboardingComplete)"Сохранить исключения"else"Продолжить",{savePreferences(vm,state.preferences.copy(allergies=allergies,excludedFoods=excluded),onSaved)},enabled=!vm.busy)}){
        item{Section("Аллергии")};item{FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){Catalog.allergenNames.forEach{name->Choice(name,name in allergies,{allergies=if(name in allergies)allergies-name else allergies+name})}}}
        item{Notice("Строгое исключение","Подбор использует состав ингредиентов. На упаковке отдельно проверяй состав и отметки о следах аллергенов.",warning=true)}
        item{Section("Не ем и не люблю")};item{Field("Найти продукт",query,{query=it})}
        items(state.foods.filter{it.name.contains(query,true)},key={it.id}){food->Toggle(food.name,checked=food.id in excluded,onChecked={yes->excluded=if(yes)excluded+food.id else excluded-food.id})}
    }
}
@Composable fun SettingsScreen(state:AppState,onBack:()->Unit,go:(String)->Unit,export:()->Unit,restore:()->Unit) {
    SytnoScreen("Мои настройки","Всё для твоего рациона",back=onBack){
        item{LinkRow("Бюджет недели","${Numbers.money(state.preferences.budget)} · ${weekLabel(state)}",{go("budget")})}
        item{LinkRow("Магазины и адреса",state.preferences.stores.joinToString{it.title},{go("stores")})}
        item{LinkRow("Питание и готовка",if(state.preferences.calories==null&&state.preferences.protein==null)"Без целей по калориям и белку"else"${state.preferences.calories?.let{"$it ккал"}.orEmpty()} · ${state.preferences.protein?.let{"$it г белка"}.orEmpty()}",{go("nutrition")})}
        item{LinkRow("Техника на кухне",state.preferences.equipment.joinToString{it.title}.ifEmpty{"Без техники"},{go("equipment")})}
        item{LinkRow("Исключения и аллергии","${state.preferences.allergies.size} аллергенов · ${state.preferences.excludedFoods.size} продуктов",{go("restrictions")})}
        item{LinkRow("Цены и каталоги","Оценки, ручные цены и импорт",{go("prices")})}
        item{Section("Данные")};item{PrimaryButton("Сохранить резервную копию",export,secondary=true)};item{PrimaryButton("Восстановить из файла",restore,secondary=true)}
        item{Notice("Личное приложение","Все настройки, меню, покупки и запасы сохраняются на телефоне. Сохранённый рацион доступен без интернета.")}
        item{Text("Сытно · версия ${BuildConfig.VERSION_NAME}",style=MaterialTheme.typography.bodySmall,color=C.Secondary)}
    }
}
