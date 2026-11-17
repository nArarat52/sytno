@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package ru.sytno.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.*
import ru.sytno.core.*
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.time.LocalDate

@Composable fun FoodChooser(state:AppState,vm:AppViewModel,selected:String?,onChoose:(String)->Unit,enabled:Boolean=true) {
    var open by remember{mutableStateOf(false)};var create by remember{mutableStateOf(false)};var query by rememberSaveable{mutableStateOf("")}
    LinkRow("Продукт",selected?.let{id->state.foods.find{it.id==id}?.name}?:"Выбери из списка или добавь свой",{if(enabled)open=true})
    if(open){
        BackHandler{open=false}
        Panel(color=C.Soft){
            Text("Выбрать продукт",style=MaterialTheme.typography.titleLarge)
            Field("Поиск",query,{query=it})
            LazyColumn(Modifier.fillMaxWidth().height(240.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                items(state.foods.filter{it.name.contains(query,true)},key={it.id}){food->TextButton({onChoose(food.id);open=false},Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("pick-food-${food.id}")){Text(food.name)}}
                if(state.foods.none{it.name.contains(query,true)})item{Text("Продукт не найден. Можно добавить свой.",Modifier.padding(12.dp),color=C.Secondary)}
            }
            PrimaryButton("Добавить свой продукт",{open=false;create=true},secondary=true)
            TextButton({open=false},Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("Закрыть")}
        }
    }
    if(create)NewFoodDialog(state,vm,query,{create=false},{id->onChoose(id);create=false})
}
@Composable private fun NewFoodDialog(state:AppState,vm:AppViewModel,initial:String,onDismiss:()->Unit,onCreated:(String)->Unit) {
    var name by rememberSaveable{mutableStateOf(initial)};var unit by rememberSaveable{mutableStateOf(UnitOfFood.GRAM)}
    var calories by rememberSaveable{mutableStateOf("")};var protein by rememberSaveable{mutableStateOf("")};var known by rememberSaveable{mutableStateOf(false)};var allergens by rememberSaveable{mutableStateOf(emptySet<String>())}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Новый продукт")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Field("Название",name,{name=it});FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){UnitOfFood.entries.forEach{u->Choice(u.title,u==unit,{unit=u})}}
        Text(if(unit==UnitOfFood.PIECE)"Пищевая ценность на 1 штуку"else"Пищевая ценность на 100 ${unit.title}",style=MaterialTheme.typography.bodySmall)
        Field("Ккал · можно оставить пустым",calories,{calories=it},numeric=true);Field("Белок, г · можно оставить пустым",protein,{protein=it},numeric=true)
        Toggle("Состав проверен","Отметь аллергены по этикетке",known,{known=it});if(known)FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){Catalog.allergenNames.forEach{a->Choice(a,a in allergens,{allergens=if(a in allergens)allergens-a else allergens+a})}}
        Text("Без известного состава продукт нельзя включить в рацион при заданных аллергиях.",style=MaterialTheme.typography.bodySmall,color=C.Secondary)
    }},confirmButton={TextButton({runCatching{
        if(name.isBlank())throw DomainError("Укажи название продукта")
        fun number(text:String):Double=if(text.isBlank())0.0 else text.replace(',','.').toDoubleOrNull()?.takeIf{it.isFinite()&&it>=0}?:throw DomainError("Проверь пищевую ценность")
        val factor=if(unit==UnitOfFood.PIECE)1.0 else 100.0
        val existing=state.foods.find{it.name.equals(name.trim(),true)&&it.unit==unit}
        if(existing!=null)onCreated(existing.id)else{
            val food=Food(newId(),name.trim(),unit,number(calories)/factor,number(protein)/factor,"Мои продукты",allergens,known,true)
            vm.mutate(onSuccess={onCreated(food.id)}){it.copy(foods=it.foods+food)}
        }
    }.onFailure(vm::report)},enabled=!vm.busy){Text("Добавить")}},dismissButton={TextButton(onDismiss){Text("Отменить")}})
}

@Composable fun PantryScreen(state:AppState,onSettings:()->Unit,onAdd:()->Unit,onEdit:(String)->Unit,onRecipes:()->Unit) {
    var query by rememberSaveable{mutableStateOf("")};var soon by rememberSaveable{mutableStateOf(false)}
    val items=state.pantry.filter{state.food(it.foodId).name.contains(query,true)&&(!soon||it.useSoon||it.expiry?.let{d->LocalDate.parse(d)<=LocalDate.now().plusDays(3)}==true)}.sortedWith(compareBy<PantryItem>{!it.useSoon}.thenBy{it.expiry?:"9999"}.thenBy{state.food(it.foodId).name})
    val required=remember(state){Shopping.required(state)}
    SytnoScreen("Мои запасы","Что уже есть дома",settings=onSettings){
        item{Field("Поиск по запасам",query,{query=it})};item{FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){Choice("Все",!soon,{soon=false});Choice("Использовать скорее",soon,{soon=true})}}
        item{PrimaryButton("Добавить продукт",onAdd,secondary=true)}
        if(items.isEmpty())item{EmptyState(if(state.pantry.isEmpty())"Начнём с того, что уже есть"else"Ничего не найдено",if(state.pantry.isEmpty())"Добавь крупы, яйца, молоко или масло. Отмеченные покупки также будут появляться здесь."else"Измени поиск или фильтр.")}
        items(items,key={it.foodId}){p->val food=state.food(p.foodId);val expired=p.expiry?.let{LocalDate.parse(it)<LocalDate.now()}==true
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Surface).clickable{onEdit(p.foodId)}.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                Glyph("package",tint=if(expired||p.useSoon)C.Warning else C.Brand)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)){Text(food.name,style=MaterialTheme.typography.titleMedium);Text("${p.amount} ${food.unit.title}"+(required[p.foodId]?.let{" · $it нужны для меню"}.orEmpty()),style=MaterialTheme.typography.bodyMedium,color=C.Secondary);p.expiry?.let{Text(if(expired)"Срок истёк: ${dateLabel(it)}"else"Использовать до ${dateLabel(it)}",style=MaterialTheme.typography.bodySmall,color=if(expired)C.Danger else C.Warning)};if(p.useSoon)Text("Использовать скорее",style=MaterialTheme.typography.bodySmall,color=C.Warning)};Glyph("chevron-right",tint=C.Secondary,size=20.dp)
            }
        }
        item{PrimaryButton("Подобрать блюда из запасов",onRecipes,secondary=true)}
        item{Text("Планирование не уменьшает остатки. Списание происходит после отметки о приготовлении.",style=MaterialTheme.typography.bodySmall,color=C.Secondary)}
    }
}
@Composable fun StockEditor(state:AppState,vm:AppViewModel,initialId:String?,onBack:()->Unit) {
    val old=state.pantry.find{it.foodId==initialId}
    var foodId by rememberSaveable{mutableStateOf(initialId)};var amount by rememberSaveable{mutableStateOf(old?.amount?.toString().orEmpty())};var expiry by rememberSaveable{mutableStateOf(old?.expiry.orEmpty())}
    var soon by rememberSaveable{mutableStateOf(old?.useSoon?:false)};var location by rememberSaveable{mutableStateOf(old?.location?:"Дома")};var kg by rememberSaveable{mutableStateOf(false)};var delete by remember{mutableStateOf(false)}
    val food=state.foods.find{it.id==foodId}
    fun setKg(value:Boolean){if(value==kg)return;if(amount.isBlank()){kg=value;return};runCatching{val grams=Numbers.parseAmount(amount,if(kg)1000 else 1);amount=if(value)java.math.BigDecimal.valueOf(grams,3).stripTrailingZeros().toPlainString()else grams.toString();kg=value}.onFailure(vm::report)}
    fun save(){runCatching{val id=foodId?:throw DomainError("Выбери продукт");val qty=Numbers.parseAmount(amount,if(kg)1000 else 1);val date=expiry.takeIf{it.isNotBlank()}?.let{LocalDate.parse(it).toString()};vm.mutate("Запасы обновлены",onBack){Engine.setStock(it,PantryItem(id,qty,date,soon,location.trim()))}}.onFailure(vm::report)}
    SytnoScreen(if(old==null)"Добавить в запасы"else"Изменить запас","То, что уже есть дома",back=onBack,footer={PrimaryButton("Сохранить продукт",::save,enabled=!vm.busy)}){
        item{FoodChooser(state,vm,foodId,{id->if(id!=foodId){amount="";kg=false};foodId=id},enabled=old==null)};item{Field("Количество, ${if(kg)"кг"else food?.unit?.title?:"г"}",amount,{amount=it},numeric=true)}
        if(food?.unit==UnitOfFood.GRAM)item{FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){Choice("Граммы",!kg,{setKg(false)});Choice("Килограммы",kg,{setKg(true)})}}
        item{Field("Где хранится",location,{location=it})};item{Field("Срок · ГГГГ-ММ-ДД · необязательно",expiry,{expiry=it})};item{Toggle("Использовать скорее","Предлагать в ближайших блюдах",soon,{soon=it})}
        if(old!=null){item{Summary("Нужно для меню","${Shopping.required(state)[old.foodId]?:0} ${food?.unit?.title.orEmpty()}")};item{PrimaryButton("Убрать из запасов",{delete=true},danger=true)}}
        item{Notice("Покупки пересчитаются","Новый остаток учтётся только в ещё не купленных продуктах. История расходов сохранится.")}
    }
    if(delete)AlertDialog(onDismissRequest={delete=false},title={Text("Убрать продукт из запасов?")},text={Text("Расходы на покупку сохранятся. Если продукт нужен для меню, он снова появится в покупках.")},confirmButton={TextButton({vm.mutate("Продукт убран",onBack){Engine.setStock(it,PantryItem(initialId!!,0))}}){Text("Убрать")}},dismissButton={TextButton({delete=false}){Text("Оставить")}})
}
@Composable fun ShoppingScreen(state:AppState,vm:AppViewModel,onSettings:()->Unit,onAdd:()->Unit,onEdit:(String)->Unit,onPrices:()->Unit) {
    val quote=remember(state){Shopping.quote(state)};val bought=quote.rows.filter{it.purchase!=null};val pending=quote.rows.filter{it.purchase==null}
    SytnoScreen("Покупки","${weekLabel(state)} · ${quote.rows.size} ${wordForm(quote.rows.size,"позиция","позиции","позиций")}",settings=onSettings){
        item{AmountCard(if(bought.isEmpty())"К покупке"else"Осталось купить на",Numbers.money(quote.pending),"Куплено на ${Numbers.money(quote.spent)} · лимит ${Numbers.money(state.preferences.budget)}")}
        if(quote.total>state.preferences.budget)item{Notice("Бюджет превышен","На ${Numbers.money(quote.total-state.preferences.budget)}. Уточни цены или пересчитай рацион.",warning=true)}
        if(quote.estimated)item{Notice("Есть ориентировочные цены","Уточни стоимость в магазине или подключи свой каталог. Изменить цену можно при отметке покупки.",warning=true)}
        if(quote.missingFoods.isNotEmpty())item{Notice("Нет цены для части продуктов",quote.missingFoods.joinToString{state.food(it).name}+". Полный итог пока неизвестен.",error=true)}
        item{PrimaryButton("Добавить позицию",onAdd,secondary=true)}
        if(bought.isNotEmpty()){item{Section("Куплено · ${Numbers.money(quote.spent)}")};items(bought,key={"bought-${it.purchase!!.id}"}){row->ShoppingItem(state,vm,row,onEdit)}}
        pending.groupBy{it.store}.toSortedMap(compareBy{it.ordinal}).forEach{(store,rows)->item(key="store-${store.name}"){Section(store.title)};items(rows,key={it.id}){row->ShoppingItem(state,vm,row,onEdit)}}
        if(quote.rows.isEmpty()&&quote.missingFoods.isEmpty())item{EmptyState("Покупки не нужны",if(state.plan==null)"Сначала составь рацион или добавь покупку вручную."else"Все необходимые продукты уже есть дома.","shopping-basket")}
        item{PrimaryButton("Цены и каталоги",onPrices,secondary=true)}
    }
}
@Composable private fun ShoppingItem(state:AppState,vm:AppViewModel,row:ShoppingRow,onEdit:(String)->Unit) {
    val food=state.food(row.foodId);val bought=row.purchase!=null;val offer=row.offer
    val largeText=LocalDensity.current.fontScale>1.3f
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Surface).clickable{onEdit(row.id)}.padding(8.dp).testTag("shopping-${food.id}-${if(bought)"bought"else"needed"}"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)){
        Checkbox(bought,{checked->if(checked)vm.mutate("${food.name}: добавлено в запасы"){Engine.buy(it,row.id)}else vm.mutate("Покупка отменена"){Engine.undoPurchase(it,row.purchase!!.id)}},enabled=!vm.busy,modifier=Modifier.testTag("check-${row.id}"))
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)){Text(food.name,style=MaterialTheme.typography.titleMedium,textDecoration=if(bought)TextDecoration.LineThrough else null,color=if(bought)C.Secondary else C.Text);Text(if(bought)"${row.amount} ${food.unit.title} · дома ${state.stock(food.id)}"else if(offer!=null)"Нужно ${row.needed} ${food.unit.title} · купить ${row.amount/offer.packAmount} × ${offer.packAmount} ${food.unit.title}"else"${row.amount} ${food.unit.title} · вручную",style=MaterialTheme.typography.bodySmall,color=C.Secondary);if(largeText)Text(Numbers.money(row.price),style=MaterialTheme.typography.titleMedium,color=C.Brand)}
        if(!largeText)Text(Numbers.money(row.price),Modifier.padding(8.dp),style=MaterialTheme.typography.titleMedium)
    }
}
@Composable fun PurchaseScreen(state:AppState,vm:AppViewModel,row:ShoppingRow,onBack:()->Unit,onSaved:()->Unit) {
    var qty by rememberSaveable(row.id){mutableStateOf(row.amount.toString())};var cost by rememberSaveable(row.id){mutableStateOf(Numbers.inputMoney(row.price))};var undo by remember{mutableStateOf(false)}
    val food=state.food(row.foodId);val offer=row.offer;val purchase=row.purchase
    fun save(){runCatching{val q=Numbers.parseAmount(qty);val p=Numbers.parseMoney(cost);vm.mutate("Покупка сохранена",onSaved){if(purchase!=null)Engine.editPurchase(it,purchase.id,q,p)else Engine.buy(it,row.id,q,p)}}.onFailure(vm::report)}
    SytnoScreen(if(row.purchase==null)"Отметить покупку"else"Изменить покупку","${food.name} · ${row.store.title}",back=onBack,footer={PrimaryButton(if(row.purchase==null)"Куплено · добавить в запасы"else"Сохранить изменения",::save,enabled=!vm.busy)}){
        item{Panel{Glyph("package",size=40.dp);Text(food.name,style=MaterialTheme.typography.headlineMedium);Text("Нужно ${row.needed} ${food.unit.title}. ${row.offer?.let{"Упаковка ${it.packAmount} ${food.unit.title}."}.orEmpty()}",color=C.Secondary)}}
        item{Field("Купленное количество, ${food.unit.title}",qty,{qty=it},numeric=true)};item{Field("Фактическая стоимость, ₽",cost,{cost=it},numeric=true)}
        item{Summary("Сейчас дома","${state.stock(food.id)} ${food.unit.title}")};item{Summary("После сохранения",runCatching{val delta=Numbers.parseAmount(qty)-(row.purchase?.amount?:0);"${state.stock(food.id)+delta} ${food.unit.title}"}.getOrDefault("—"),C.Brand)}
        if(offer!=null)item{Notice(offer.source.title,if(offer.source==PriceSource.ESTIMATE)"Это приблизительная цена. Введи фактическую стоимость с полки или чека."else"Указана для ${offer.city.ifEmpty{"твоего города"}}. Цена на кассе может измениться.",warning=offer.source==PriceSource.ESTIMATE)}
        if(row.purchase!=null)item{PrimaryButton("Отменить покупку",{undo=true},danger=true)}
        if(row.id.startsWith("manual:")&&row.purchase==null)item{PrimaryButton("Удалить из покупок",{vm.mutate("Позиция удалена",onSaved){Engine.removeManual(it,row.id.removePrefix("manual:"))}},danger=true)}
        item{Notice("Без повторного зачисления","Изменение покупки корректирует количество разницей. Отмена невозможна, если продукты уже использованы.")}
    }
    if(undo)AlertDialog(onDismissRequest={undo=false},title={Text("Отменить покупку?")},text={Text("Уберём её сумму из расходов и купленное количество из запасов.")},confirmButton={TextButton({vm.mutate("Покупка отменена",onSaved){Engine.undoPurchase(it,row.purchase!!.id)}}){Text("Отменить покупку")}},dismissButton={TextButton({undo=false}){Text("Оставить")}})
}
@Composable fun ShoppingAddScreen(state:AppState,vm:AppViewModel,onBack:()->Unit,onSaved:()->Unit) {
    var foodId by rememberSaveable{mutableStateOf<String?>(null)};var amount by rememberSaveable{mutableStateOf("")};var price by rememberSaveable{mutableStateOf("")};var store by rememberSaveable{mutableStateOf(state.preferences.stores.first())};var include by rememberSaveable{mutableStateOf(true)}
    SytnoScreen("Добавить в покупки","Любой продукт, даже без рецепта.",back=onBack,footer={PrimaryButton("Добавить в список",{runCatching{val id=foodId?:throw DomainError("Выбери продукт");val qty=Numbers.parseAmount(amount);val cost=if(price.isNotBlank())Numbers.parseMoney(price)else null;vm.mutate("Добавлено в покупки",onSaved){Engine.addManual(it,Engine.manualItem(it,id,qty,store,cost,include))}}.onFailure(vm::report)},enabled=!vm.busy)}){
        item{FoodChooser(state,vm,foodId,{foodId=it})};item{Field("Количество, ${state.foods.find{it.id==foodId}?.unit?.title?:"г"}",amount,{amount=it},numeric=true)};item{Field("Стоимость, ₽ · пусто для оценки",price,{price=it},numeric=true)}
        item{Section("Магазин")};item{FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){Store.entries.forEach{s->Choice(s.title,s==store,{store=s})}}};item{Toggle("Включить в бюджет недели","Показывать в общем итоге",include,{include=it})}
        item{Notice("Меню не изменится","После отметки продукт появится в запасах. Без указанной цены округлим количество до целых упаковок. Ручные позиции учтём в потребностях меню.")}
    }
}

@Composable fun CustomRecipeScreen(state:AppState,vm:AppViewModel,onBack:()->Unit,onSaved:(String)->Unit) {
    var name by rememberSaveable{mutableStateOf("")};var minutes by rememberSaveable{mutableStateOf("20")};var portions by rememberSaveable{mutableStateOf("1")};var steps by rememberSaveable{mutableStateOf("")}
    var types by rememberSaveable{mutableStateOf(setOf(MealType.LUNCH,MealType.DINNER))};var equipment by rememberSaveable{mutableStateOf(setOf(Equipment.STOVE))}
    var ingredients by rememberSaveable{mutableStateOf<List<String>>(emptyList())};var selected by rememberSaveable{mutableStateOf<String?>(null)};var qty by rememberSaveable{mutableStateOf("")}
    val parsed=ingredients.map{value->val parts=value.split('|');Ingredient(parts[0],parts[1].toLong())}
    fun add(){runCatching{val id=selected?:throw DomainError("Выбери ингредиент");val amount=Numbers.parseAmount(qty);ingredients=ArrayList(ingredients+"$id|$amount");selected=null;qty=""}.onFailure(vm::report)}
    fun save(){runCatching{
        val count=portions.toIntOrNull()?.takeIf{it in 1..20}?:throw DomainError("Порций должно быть от 1 до 20")
        val time=minutes.toIntOrNull()?:throw DomainError("Проверь время готовки")
        if(types.isEmpty())throw DomainError("Выбери приёмы пищи")
        val items=parsed.groupBy{it.foodId}.map{(id,list)->Ingredient(id,kotlin.math.ceil(list.sumOf{it.amount}.toDouble()/count).toLong())}
        val instructions=steps.lines().filter{it.isNotBlank()}.map{CookStep(it.trim())}.ifEmpty{listOf(CookStep("Приготовь блюдо своим способом из указанных ингредиентов."))}
        val recipe=Recipe(newId(),name.trim(),types,items,instructions,time,time,if(equipment.isEmpty())emptyList()else listOf(equipment),custom=true)
        vm.mutate("Рецепт сохранён",{onSaved(recipe.id)}){Engine.saveRecipe(it,recipe)}
    }.onFailure(vm::report)}
    SytnoScreen("Мой рецепт","Ингредиенты укажи на все выбранные порции.",back=onBack,footer={PrimaryButton("Сохранить рецепт",::save,enabled=!vm.busy)}){
        item{Field("Название блюда",name,{name=it})};item{Field("Количество порций",portions,{portions=it},numeric=true)};item{Field("Время готовки, минут",minutes,{minutes=it},numeric=true)}
        item{Section("Приёмы пищи")};item{FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){MealType.entries.forEach{t->Choice(t.title,t in types,{types=if(t in types)types-t else types+t})}}}
        item{Section("Ингредиенты")};itemsIndexed(parsed){index,i->Panel{Summary(state.food(i.foodId).name,"${i.amount} ${state.food(i.foodId).unit.title}");TextButton({ingredients=ArrayList(ingredients.filterIndexed{ix,_->ix!=index})}){Text("Убрать")}}}
        item{FoodChooser(state,vm,selected,{selected=it})};item{Field("Количество, ${state.foods.find{it.id==selected}?.unit?.title?:"г"}",qty,{qty=it},numeric=true)};item{PrimaryButton("Добавить ингредиент",::add,secondary=true)}
        item{Section("Нужная техника · все выбранные")};item{FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){Equipment.entries.forEach{e->Choice(e.title,e in equipment,{equipment=if(e in equipment)equipment-e else equipment+e})}}}
        item{Field("Шаги приготовления · каждый с новой строки",steps,{steps=it},singleLine=false)};item{Notice("Проверим перед сохранением","Запрещённые продукты и аллергены не попадут в рецепт. Для пищевой ценности используются данные выбранных ингредиентов.")}
    }
}

@Composable fun PricesScreen(state:AppState,vm:AppViewModel,onBack:()->Unit,openSite:(String)->Unit) {
    var url by rememberSaveable{mutableStateOf(state.preferences.priceFeedUrl)};var edit by remember{mutableStateOf(false)};var fetching by remember{mutableStateOf(false)};val scope=rememberCoroutineScope()
    SytnoScreen("Цены и каталоги","Источники и актуальность цен",back=onBack){
        item{Notice("Встроенные цены — оценки","Автоматические каталоги четырёх сетей пока не подключены. Можно уточнить цены вручную или загрузить свой JSON-каталог.",warning=true)}
        item{PrimaryButton("Указать цену товара",{edit=true},secondary=true)}
        item{Section("Каталоги магазинов")};items(Store.entries){store->LinkRow(store.title,"Открыть официальный сайт",{openSite(store.website)})}
        item{Section("Подключить JSON-каталог")};item{Field("Прямая HTTPS-ссылка на каталог",url,{url=it})}
        item{PrimaryButton(if(fetching)"Обновляем…"else"Загрузить цены",{
            scope.launch{fetching=true;try{
                val uri=PriceFeed.validateUrl(url)
                val text=withContext(Dispatchers.IO){val connection=uri.toURL().openConnection() as HttpURLConnection;try{connection.connectTimeout=8000;connection.readTimeout=8000;connection.instanceFollowRedirects=false;if(connection.responseCode!=200)throw DomainError("Каталог ответил HTTP ${connection.responseCode}. Укажи прямую ссылку на JSON.");connection.inputStream.use{input->val out=ByteArrayOutputStream();val buf=ByteArray(8192);while(true){val n=input.read(buf);if(n<0)break;out.write(buf,0,n);if(out.size()>StateCodec.MAX_BYTES)throw DomainError("Каталог больше 5 МБ")};out.toString("UTF-8")}}finally{connection.disconnect()}}
                val current=vm.state?:state;val offers=withContext(Dispatchers.Default){PriceFeed.parse(text,current)}
                vm.mutate("Обновлено ${offers.size} цен"){PriceFeed.merge(it,offers).copy(preferences=it.preferences.copy(priceFeedUrl=url.trim()))}
            }catch(e:Exception){vm.report(e)}finally{fetching=false}}
        },enabled=!fetching&&!vm.busy)}
        item{Text("Формат каталога и пример находятся в документации проекта. Город и адрес должны совпадать с настройками магазинов.",style=MaterialTheme.typography.bodySmall,color=C.Secondary)}
        val confirmed=state.offers.filter{it.source!=PriceSource.ESTIMATE}.sortedByDescending{it.updatedAt}
        if(confirmed.isNotEmpty()){item{Section("Уточнённые цены")};items(confirmed.take(100),key={it.id}){offer->Panel{Summary("${state.food(offer.foodId).name} · ${offer.store.title}",Numbers.money(offer.price));Text("${offer.packAmount} ${state.food(offer.foodId).unit.title} · ${offer.source.title}"+if(!offer.isFresh(System.currentTimeMillis()))" · требует проверки"else"",style=MaterialTheme.typography.bodySmall,color=C.Secondary)}}}
    }
    if(edit)ManualPriceDialog(state,vm,{edit=false})
}
@Composable private fun ManualPriceDialog(state:AppState,vm:AppViewModel,onDismiss:()->Unit) {
    var foodId by rememberSaveable{mutableStateOf<String?>(null)};var store by rememberSaveable{mutableStateOf(state.preferences.stores.first())};var amount by rememberSaveable{mutableStateOf("")};var price by rememberSaveable{mutableStateOf("")};var available by rememberSaveable{mutableStateOf(true)}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Цена товара")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
        FoodChooser(state,vm,foodId,{foodId=it});FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){Store.entries.forEach{s->Choice(s.title,s==store,{store=s})}};Field("Размер упаковки, ${state.foods.find{it.id==foodId}?.unit?.title?:"г"}",amount,{amount=it},numeric=true);Field("Цена упаковки, ₽",price,{price=it},numeric=true);Toggle("Есть в наличии",checked=available,onChecked={available=it})
    }},confirmButton={TextButton({runCatching{val id=foodId?:throw DomainError("Выбери продукт");val qty=Numbers.parseAmount(amount);val cost=Numbers.parseMoney(price);val offer=Offer("manual:${store.name}:$id",id,store,qty,cost,PriceSource.MANUAL,System.currentTimeMillis(),state.preferences.city,state.preferences.addresses[store].orEmpty(),available=available);vm.mutate("Цена сохранена",onDismiss){it.copy(offers=it.offers.filterNot{o->o.id==offer.id}+offer)}}.onFailure(vm::report)},enabled=!vm.busy){Text("Сохранить")}},dismissButton={TextButton(onDismiss){Text("Отменить")}})
}
