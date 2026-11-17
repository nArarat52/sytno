@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package ru.sytno.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ru.sytno.core.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun portionLabel(q:Int):String=String.format(ruLocale,"%.2f",q/4.0).trimEnd('0').trimEnd(',')
@Composable fun TodayScreen(state:AppState,vm:AppViewModel,day:Int,onDay:(Int)->Unit,onRecipe:(Meal)->Unit,onSettings:()->Unit,onWeek:()->Unit,onShopping:()->Unit,onAdd:()->Unit,onReplace:(String)->Unit,onGenerate:()->Unit) {
    val quote=remember(state){Shopping.quote(state)};val meals=state.plan?.meals.orEmpty().filter{it.day==day}.sortedBy{it.type.ordinal}
    val date=LocalDate.parse(state.weekStart).plusDays(day.toLong());val n=state.dailyNutrition(day)
    SytnoScreen("Хорошего дня",date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM",ruLocale)).replaceFirstChar{it.uppercase()},settings=onSettings){
        item{AmountCard(if(quote.missingFoods.isNotEmpty())"Часть цен неизвестна"else if(quote.total<=state.preferences.budget)"Неделя в бюджете"else"Проверь бюджет", Numbers.money(quote.total),"из ${Numbers.money(state.preferences.budget)} · "+if(quote.missingFoods.isNotEmpty())"итог неполный"else if(quote.estimated)"ориентировочно"else"с учётом купленного",onShopping)}
        item{LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){items((0..6).toList()){d->Choice(LocalDate.parse(state.weekStart).plusDays(d.toLong()).format(DateTimeFormatter.ofPattern("EEE d",ruLocale)),d==day,{onDay(d)})}}}
        if(meals.isNotEmpty())item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
            if(state.preferences.calories!=null||state.preferences.protein!=null){Metric("${n.calories.toInt()}","ккал по плану",Modifier.weight(1f));Metric("${n.protein.toInt()} г","белок",Modifier.weight(1f))}else{Metric("${meals.size}",wordForm(meals.size,"приём пищи","приёма пищи","приёмов пищи"),Modifier.weight(1f))}
            Metric("${meals.sumOf{state.recipe(it.recipeId).activeMinutes}} мин","активной готовки",Modifier.weight(1f))}}
        item{Section("Сегодня в меню","Вся неделя",onWeek)}
        if(meals.isEmpty())item{EmptyState("Начнём с твоего рациона","Укажи бюджет, магазины, ограничения и продукты дома — составим меню на неделю.","cooking-pot")}
        items(meals,key={it.id}){meal->Column(verticalArrangement=Arrangement.spacedBy(8.dp)){RecipeCard(state,meal,meal.type==MealType.LUNCH,{onRecipe(meal)},{vm.mutate{Engine.pin(it,meal.id)}});if(meal.type==MealType.LUNCH&&!state.isCooked(meal.id))Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){PrimaryButton("Заменить",{onReplace(meal.id)},Modifier.weight(1f),secondary=true);PrimaryButton("Добавить своё",onAdd,Modifier.weight(1f),secondary=true)}}}
        if(meals.size<state.preferences.mealTypes.size)item{PrimaryButton("Составить рацион",onGenerate,enabled=!vm.busy)}
        item{PrimaryButton("Добавить блюдо",onAdd,secondary=true)}
        if(state.pantry.isNotEmpty())item{Notice("Сначала используем дома","Домашние продукты уменьшают закупку. Запасы спишутся после готовки.")}
    }
}
@Composable fun WeekScreen(state:AppState,vm:AppViewModel,onDay:(Int)->Unit,onShopping:()->Unit,onSettings:()->Unit,onAdd:()->Unit,onGenerate:()->Unit,onSummary:()->Unit) {
    val quote=remember(state){Shopping.quote(state)}
    SytnoScreen("Твоя неделя",weekLabel(state),settings=onSettings,footer={PrimaryButton("Открыть список покупок",onShopping)}){
        item{AmountCard("К покупке · бюджет ${Numbers.money(state.preferences.budget)}",Numbers.money(quote.total),if(quote.estimated)"В расчёте есть приблизительные цены"else"С учётом покупок")}
        if(quote.missingFoods.isNotEmpty())item{Notice("Итог неполный", "Нет цены или наличия: "+quote.missingFoods.joinToString{state.food(it).name},warning=true)}
        item{Summary(if(quote.total<=state.preferences.budget)"Остаётся в бюджете"else"Превышение бюджета",Numbers.money(kotlin.math.abs(state.preferences.budget-quote.total)),if(quote.total<=state.preferences.budget)C.Brand else C.Danger)}
        item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){PrimaryButton("Добавить блюдо",onAdd,Modifier.weight(1f),secondary=true);PrimaryButton("Пересчитать",onGenerate,Modifier.weight(1f),enabled=!vm.busy,secondary=true)}}
        if(state.plan==null)item{EmptyState("Пока нет плана","Нажми «Пересчитать», чтобы составить семь дней питания.","calendar-days")}
        else items((0..6).toList()){day->val meals=state.plan?.meals.orEmpty().filter{it.day==day};val n=state.dailyNutrition(day);LinkRow("${LocalDate.parse(state.weekStart).plusDays(day.toLong()).format(DateTimeFormatter.ofPattern("EEE, d",ruLocale))} · ${n.calories.toInt()} ккал · ${n.protein.toInt()} г белка",meals.sortedBy{it.type.ordinal}.joinToString(" · "){state.recipe(it.recipeId).name}.ifEmpty{"Нет блюд — пересчитай рацион"},{onDay(day)})}
        item{LinkRow("Итоги недели","Расходы, приготовленные блюда и остатки",onSummary)}
        item{Text("Пересчёт сохраняет приготовленные и закреплённые блюда. При невозможных условиях сначала покажем причину.",style=MaterialTheme.typography.bodySmall,color=C.Secondary)}
    }
}
@Composable fun RecipeScreen(state:AppState,vm:AppViewModel,recipe:Recipe,meal:Meal?,onBack:()->Unit,onStart:()->Unit,onAdd:()->Unit,onReplace:()->Unit,onStocks:()->Unit={}) {
    val display=meal?:Meal("preview",0,recipe.mealTypes.first(),recipe.id);val n=state.nutrition(display)
    val ingredients=state.ingredients(display);val missing=ingredients.filter{(id,q)->state.usableStock(id)<q}
    SytnoScreen("Рецепт",meal?.type?.title?:"Мой выбор",back=onBack,footer={
        if(meal==null)PrimaryButton("Добавить в меню",onAdd)
        else if(state.isCooked(meal.id)){PrimaryButton("Отменить готовку и вернуть продукты",{vm.mutate("Продукты возвращены",onBack){Engine.undoCooking(it,meal.id)}},enabled=!vm.busy,secondary=true)}
        else{PrimaryButton(if(missing.isNotEmpty())"Проверить домашние запасы"else"Начать готовить",if(missing.isNotEmpty())onStocks else onStart);PrimaryButton("Заменить блюдо",onReplace,secondary=true)}
    }){
        item{FoodPhoto(recipe.photo,Modifier.fillMaxWidth().height(216.dp))};item{Text(recipe.name,style=MaterialTheme.typography.headlineMedium)}
        item{FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){Choice("${recipe.activeMinutes} мин активно",true,{});Choice("${recipe.totalMinutes} мин всего",false,{});Choice("${portionLabel(display.quarters)} ${if(display.quarters%4!=0)"порции"else wordForm(display.quarters/4,"порция","порции","порций")}",false,{})}}
        item{Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Metric("${n.calories.toInt()}","ккал",Modifier.weight(1f));Metric("${n.protein.toInt()} г","белок",Modifier.weight(1f));Metric("≈ ${Numbers.money(Shopping.mealCost(state,display))}","съеденная порция",Modifier.weight(1f))}}
        if(meal!=null&&missing.isNotEmpty()&&!state.isCooked(meal.id))item{Notice("Нужно уточнить запасы",missing.entries.joinToString{"${state.food(it.key).name}: не хватает ${it.value-state.usableStock(it.key)} ${state.food(it.key).unit.title}"},warning=true)}
        item{Section("Ингредиенты")}
        items(ingredients.entries.toList(),key={it.key}){(id,qty)->Panel{Summary(state.food(id).name,"$qty ${state.food(id).unit.title}");Text(if(state.usableStock(id)>=qty)"Есть дома"else"Дома ${state.usableStock(id)} ${state.food(id).unit.title}",style=MaterialTheme.typography.bodySmall,color=if(state.usableStock(id)>=qty)C.Brand else C.Secondary)}}
        item{Section("Как приготовить")};items(recipe.steps.mapIndexed{i,step->i to step}){(i,step)->Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Text("${i+1}",color=C.Brand,style=MaterialTheme.typography.titleMedium);Text(step.text,Modifier.weight(1f))}}
        item{Notice("Пищевая ценность приблизительная","Расчёт основан на количестве сырых ингредиентов. Для упакованных продуктов сверяй этикетку.")}
    }
}
@Composable fun CookingScreen(state:AppState,vm:AppViewModel,meal:Meal,step:Int,onStep:(Int)->Unit,onBack:()->Unit,onTimer:()->Unit,onFinish:()->Unit) {
    val recipe=state.recipe(meal.recipeId);val steps=recipe.steps.ifEmpty{listOf(CookStep("Приготовь блюдо своим способом."))};val index=step.coerceIn(0,steps.lastIndex)
    SytnoScreen("Готовим ${meal.type.title.lowercase()}",recipe.name,back=onBack,footer={PrimaryButton(if(index==steps.lastIndex)"Блюдо готово · списать продукты"else"Дальше",{if(index==steps.lastIndex)onFinish()else onStep(index+1)},enabled=!vm.busy);if(index>0)PrimaryButton("Предыдущий шаг",{onStep(index-1)},secondary=true)}){
        item{FoodPhoto(recipe.photo,Modifier.fillMaxWidth().height(200.dp))};item{LinearProgressIndicator(progress={(index+1).toFloat()/steps.size},modifier=Modifier.fillMaxWidth(),color=C.Brand)};item{Text("Шаг ${index+1} из ${steps.size}",style=MaterialTheme.typography.bodySmall,color=C.Secondary)}
        item{Text(steps[index].text,style=MaterialTheme.typography.headlineMedium)};item{PrimaryButton("Открыть таймер",onTimer,secondary=true)}
        item{Summary("Готовим порций",portionLabel(meal.quarters))};item{Notice("Запасы пока не списаны","Обновим их после кнопки «Блюдо готово». Повторная отметка не спишет продукты второй раз.")}
    }
}
@Composable fun TimerScreen(state:AppState,vm:AppViewModel,onBack:()->Unit) {
    var minutes by rememberSaveable{mutableStateOf("10")};var now by remember{mutableLongStateOf(System.currentTimeMillis())}
    LaunchedEffect(state.timer?.deadline){while(true){now=System.currentTimeMillis();delay(500)}}
    val timer=state.timer;val remaining=if(timer==null)0L else if(timer.deadline>0)(timer.deadline-now).coerceAtLeast(0)else timer.pausedMillis
    val seconds=(remaining+999)/1000;val running=timer!=null&&timer.deadline>0&&remaining>0
    SytnoScreen("Таймер",if(timer==null)"Для удобной готовки"else if(remaining==0L)"Время вышло"else if(running)"Отсчёт продолжается"else"На паузе",back=onBack){
        item{AmountCard(if(running)"Осталось"else"Таймер",String.format(ruLocale,"%02d:%02d",seconds/60,seconds%60))}
        if(timer==null||remaining==0L){item{Field("Продолжительность, минут",minutes,{minutes=it},numeric=true)};item{PrimaryButton("Запустить таймер",{runCatching{val m=minutes.toIntOrNull()?:throw DomainError("Укажи минуты");if(m !in 1..1440)throw DomainError("Можно от 1 до 1440 минут");vm.mutate{it.copy(timer=TimerRecord(System.currentTimeMillis()+m*60000L,0,m*60000L))}}.onFailure(vm::report)},enabled=!vm.busy)}}
        else item{PrimaryButton(if(running)"Приостановить"else"Продолжить",{vm.mutate{current->current.copy(timer=if(running)timer!!.copy(deadline=0,pausedMillis=remaining)else timer!!.copy(deadline=System.currentTimeMillis()+remaining,pausedMillis=0))}},enabled=!vm.busy)}
        if(timer!=null)item{PrimaryButton("Сбросить",{vm.mutate{it.copy(timer=null)}},secondary=true)}
        item{Notice("Можно вернуться к рецепту","Отсчёт сохраняется при повороте экрана и продолжается после сворачивания приложения. Фонового звукового сигнала пока нет.")}
    }
}
@Composable fun RecipesScreen(state:AppState,vm:AppViewModel,replacing:Boolean,onBack:()->Unit,onRecipe:(Recipe)->Unit,onCustom:()->Unit,targetType:MealType?=null) {
    var query by rememberSaveable{mutableStateOf("")};var own by rememberSaveable{mutableStateOf(false)};var inStock by rememberSaveable{mutableStateOf(false)}
    val recipes=state.recipes.filter{state.allowed(it)&&it.name.contains(query,true)&&(!own||it.custom)&&(!replacing||targetType==null||targetType in it.mealTypes)&&(!inStock||it.ingredients.all{ingredient->state.usableStock(ingredient.foodId)>=ingredient.amount})}
    SytnoScreen(if(replacing)"Заменить блюдо"else"Что хочется съесть?","Ограничения и техника уже учтены.",back=onBack){
        item{Field("Найти рецепт",query,{query=it})};item{FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){Choice("Мои блюда",own,{own=!own});Choice("Из того, что есть",inStock,{inStock=!inStock})}};item{PrimaryButton("Добавить свой рецепт",onCustom,secondary=true)}
        if(recipes.isEmpty())item{EmptyState("Подходящих блюд нет","Попробуй изменить поиск или добавь собственный рецепт.","search")}
        items(recipes,key={it.id}){r->val meal=Meal("browse",0,r.mealTypes.first(),r.id);RecipeCard(state,meal,false,{onRecipe(r)})}
    }
}
@Composable fun ScheduleScreen(state:AppState,recipe:Recipe,initialDay:Int,onBack:()->Unit,onApply:(Int,MealType,Int)->Unit) {
    var day by rememberSaveable{mutableIntStateOf(initialDay)};var type by rememberSaveable{mutableStateOf(if(MealType.DINNER in recipe.mealTypes)MealType.DINNER else recipe.mealTypes.first())};var quarters by rememberSaveable{mutableIntStateOf(4)}
    SytnoScreen("Добавить в меню",recipe.name,back=onBack,footer={PrimaryButton("Проверить изменения",{onApply(day,type,quarters)})}){
        item{FoodPhoto(recipe.photo,Modifier.fillMaxWidth().height(200.dp))};item{Section("День недели")};item{FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){(0..6).forEach{d->Choice(LocalDate.parse(state.weekStart).plusDays(d.toLong()).format(DateTimeFormatter.ofPattern("EEE d",ruLocale)),day==d,{day=d})}}}
        item{Section("Приём пищи")};item{FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){recipe.mealTypes.forEach{t->Choice(t.title,type==t,{type=t})}}}
        item{Section("Количество порций")};item{FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(2,4,6,8).forEach{q->Choice(portionLabel(q),quarters==q,{quarters=q})}}}
        item{Notice("Заменит выбранный приём пищи","Перед применением покажем стоимость и проверим цели питания. Блюдо будет закреплено.")}
    }
}
@Composable fun PlanPreviewScreen(state:AppState,vm:AppViewModel,onBack:()->Unit,onApply:()->Unit) {
    val result=vm.preview
    SytnoScreen("Проверим изменения","Перед обновлением меню",back=onBack,footer={PrimaryButton("Применить изменения",onApply,enabled=result?.success==true&&!vm.busy);PrimaryButton("Вернуться без изменений",onBack,secondary=true)}){
        if(result==null)item{Notice("Предпросмотр недоступен","Вернись и выбери блюдо заново.",warning=true)}
        else{
            item{AmountCard("Новый итог недели",Numbers.money(result.quote.total),"Бюджет ${Numbers.money(state.preferences.budget)}")}
            item{Summary("Покупки до изменения",Numbers.money(Shopping.quote(state).total))};item{Summary("Изменение",Numbers.money(result.quote.total-Shopping.quote(state).total))}
            if(result.issues.isNotEmpty())item{Notice("Условия не выполнены",result.issues.joinToString("\n"),warning=true)}else item{Notice("Можно применить","Бюджет, цели и ограничения проверены. Уже купленное учтено.")}
        }
    }
}
@Composable fun SummaryScreen(state:AppState,vm:AppViewModel,onBack:()->Unit,onNext:()->Unit) {
    var confirm by remember{mutableStateOf(false)}
    SytnoScreen("Итоги недели",weekLabel(state),back=onBack,footer={PrimaryButton("Составить следующую неделю",{confirm=true})}){
        item{AmountCard("Потрачено на покупки",Numbers.money(state.spent()),"Бюджет ${Numbers.money(state.preferences.budget)}")};item{Summary("Остаток бюджета",Numbers.money(state.preferences.budget-state.spent()),if(state.spent()<=state.preferences.budget)C.Brand else C.Danger)};item{Summary("Приготовлено блюд","${state.plan?.meals?.count{state.isCooked(it.id)}?:0}")};item{Section("Запасы на следующую неделю")};items(state.pantry,key={it.foodId}){p->Summary(state.food(p.foodId).name,"${p.amount} ${state.food(p.foodId).unit.title}")}
        if(state.history.isNotEmpty()){item{Section("Предыдущие недели")};items(state.history.takeLast(6).reversed()){w->Summary(dateLabel(w.start),Numbers.money(w.spent))}}
    }
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("Начать новую неделю?")},text={Text("Сохраним расходы в истории и перенесём запасы. Текущее меню и ещё не купленные ручные позиции будут заменены новым планом.")},confirmButton={TextButton({vm.mutate(onSuccess={confirm=false;onNext()}){Engine.newWeek(it,LocalDate.parse(it.weekStart).plusDays(7).toString())}}){Text("Начать")}},dismissButton={TextButton({confirm=false}){Text("Отменить")}})
}
