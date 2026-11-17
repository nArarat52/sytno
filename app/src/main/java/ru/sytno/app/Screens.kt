package ru.sytno.app

import android.content.Intent
import androidx.core.net.toUri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import ru.sytno.core.*
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

val ruLocale:Locale=Locale.forLanguageTag("ru-RU")
fun wordForm(number:Int,one:String,few:String,many:String):String=when{number%100 in 11..14->many;number%10==1->one;number%10 in 2..4->few;else->many}
private val LocalFooterHeight=staticCompositionLocalOf<(Int)->Unit>{{}}
fun dateLabel(value:String):String=LocalDate.parse(value).format(DateTimeFormatter.ofPattern("d MMMM",ruLocale))
fun weekLabel(state:AppState):String="${dateLabel(state.weekStart)} — ${dateLabel(LocalDate.parse(state.weekStart).plusDays(6).toString())}"

@Composable fun SytnoScreen(title:String,subtitle:String="",back:(()->Unit)?=null,settings:(()->Unit)?=null,footer:(@Composable ColumnScope.()->Unit)?=null,content:LazyListScope.()->Unit) {
    val reportFooter=LocalFooterHeight.current
    if(footer==null)SideEffect{reportFooter(0)}
    Column(Modifier.widthIn(max=680.dp).fillMaxSize().testTag("screen-$title").background(C.Bg)) {
        Column(Modifier.fillMaxWidth().padding(start=24.dp,end=24.dp,top=12.dp,bottom=16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(back!=null)Row(verticalAlignment=Alignment.CenterVertically){IconButton(back){Glyph("arrow-left",description="Назад",tint=C.Text)};Text("Сытно",style=MaterialTheme.typography.bodyMedium,color=C.Secondary)}
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){Text(title,Modifier.weight(1f),style=MaterialTheme.typography.headlineLarge);if(settings!=null)IconButton(settings,Modifier.clip(RoundedCornerShape(100.dp)).background(C.Surface)){Glyph("settings-2",description="Настройки",tint=C.Text)}}
            if(subtitle.isNotBlank())Text(subtitle,style=MaterialTheme.typography.bodyMedium,color=C.Secondary)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("screen-list"),contentPadding=PaddingValues(start=24.dp,end=24.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp),content=content)
        if(footer!=null)Column(Modifier.fillMaxWidth().onGloballyPositioned{reportFooter(it.size.height)}.background(C.Bg).imePadding().padding(horizontal=24.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=footer)
    }
}

@Composable fun SytnoApp(vm:AppViewModel,state:AppState) {
    var route by rememberSaveable { mutableStateOf(if(state.onboardingComplete)"today" else "welcome") }
    var stack by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var day by rememberSaveable { mutableIntStateOf((java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(state.weekStart),LocalDate.now()).toInt()).coerceIn(0,6)) }
    var mealId by rememberSaveable { mutableStateOf<String?>(null) }
    var recipeId by rememberSaveable { mutableStateOf<String?>(null) }
    var stockId by rememberSaveable { mutableStateOf<String?>(null) }
    var shoppingId by rememberSaveable { mutableStateOf<String?>(null) }
    var cookingStep by rememberSaveable { mutableIntStateOf(0) }
    var importText by remember { mutableStateOf<String?>(null) }
    var footerHeight by remember { mutableIntStateOf(0) }
    val snackbarBottom=with(LocalDensity.current){footerHeight.toDp()}
    val context=LocalContext.current;val scope=rememberCoroutineScope();val snack=remember{SnackbarHostState()}
    fun go(value:String){stack=ArrayList(stack+route);route=value}
    fun home(value:String){stack=arrayListOf();route=value}
    fun back(){if(stack.isNotEmpty()){route=stack.last();stack=ArrayList(stack.dropLast(1))}else home(if(state.onboardingComplete)"today" else "welcome")}
    fun recipe(meal:Meal){mealId=meal.id;recipeId=meal.recipeId;go("recipe")}
    fun browse(){mealId=null;recipeId=null;go("recipes")}
    fun editStock(id:String?){stockId=id;go("stockEdit")}
    fun generate(){go("generation");vm.generate({home("week")},{route="infeasible"})}
    fun openSite(url:String){runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,url.toUri()))}.onFailure(vm::report)}
    BackHandler(enabled=route !in listOf("today","welcome")){back()}
    LaunchedEffect(vm.message){vm.message?.let{snack.showSnackbar(it);vm.message=null}}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->if(uri!=null)scope.launch{runCatching{withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use{it.write(StateCodec.encode(vm.state?:state))}?:error("Файл недоступен")}}.onSuccess{vm.message="Резервная копия сохранена"}.onFailure(vm::report)}}
    val restore=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)scope.launch{runCatching{withContext(Dispatchers.IO){context.contentResolver.openInputStream(uri)?.use{input->val out=ByteArrayOutputStream();val buf=ByteArray(8192);while(true){val n=input.read(buf);if(n<0)break;out.write(buf,0,n);if(out.size()>StateCodec.MAX_BYTES)throw DomainError("Файл больше 5 МБ")};out.toString("UTF-8")}?:throw DomainError("Не удалось открыть файл")}}.onSuccess{runCatching{StateCodec.decode(it)}.onSuccess{_->importText=it}.onFailure(vm::report)}.onFailure(vm::report)}}
    val main=route in listOf("today","week","shopping","pantry")
    val largeText=LocalDensity.current.fontScale>1.3f
    Scaffold(containerColor=C.Bg,snackbarHost={SnackbarHost(snack,Modifier.padding(bottom=snackbarBottom))},contentWindowInsets=WindowInsets.safeDrawing,bottomBar={if(main)NavigationBar(containerColor=C.Surface,tonalElevation=0.dp){listOf(Triple("today","Сегодня","house"),Triple("week","Неделя","calendar-days"),Triple("shopping","Покупки","shopping-basket"),Triple("pantry","Запасы","refrigerator")).forEach{(key,title,icon)->NavigationBarItem(selected=route==key,onClick={home(key)},icon={Glyph(icon,description=title,tint=if(route==key)C.Brand else C.Secondary)},label=if(largeText)null else{{Text(title,style=MaterialTheme.typography.bodySmall)}},colors=NavigationBarItemDefaults.colors(indicatorColor=C.Soft))}}}){padding->
        Box(Modifier.fillMaxSize().padding(padding),contentAlignment=Alignment.TopCenter){
            CompositionLocalProvider(LocalFooterHeight provides {footerHeight=it}){
            when(route){
                "welcome"->SytnoScreen("Сытно","Еда на неделю. В твоём бюджете.",footer={PrimaryButton("Составить мою неделю",{go("budget")})}){
                    item{FoodPhoto(1,Modifier.fillMaxWidth().height(250.dp))};item{Text("Меньше думать.\nВкуснее есть.",style=MaterialTheme.typography.headlineLarge)};item{Text("Простой рацион, продукты из твоих магазинов и покупки без лишнего.",color=C.Secondary)};item{Notice("Бюджет под контролем","Считаем целые упаковки и используем запасы.")};item{Notice("Без сложной готовки","Учитываем время, вкусы и твою технику.")}
                }
                "budget"->BudgetScreen(state,vm,{back()},{if(state.onboardingComplete)home("week")else go("stores")},onGenerate={generate()})
                "stores"->StoresScreen(state,vm,{back()},{if(state.onboardingComplete)back()else go("nutrition")})
                "nutrition"->NutritionScreen(state,vm,{back()},{if(state.onboardingComplete)back()else go("restrictions")},{go("equipment")})
                "equipment"->EquipmentScreen(state,vm,{back()})
                "restrictions"->RestrictionsScreen(state,vm,{back()},{if(state.onboardingComplete)back()else go("pantrySetup")})
                "pantrySetup"->SytnoScreen("Что уже есть дома?","Это не придётся покупать повторно.",back={back()},footer={PrimaryButton("Составить рацион",{generate()},enabled=!vm.busy)}){
                    items(state.pantry,key={it.foodId}){p->LinkRow(state.food(p.foodId).name,"${p.amount} ${state.food(p.foodId).unit.title}",{editStock(p.foodId)})};item{PrimaryButton("Добавить продукт",{editStock(null)},secondary=true)};item{Notice("Запасы учтём в покупках","В список попадёт только недостающее количество.")}
                }
                "today"->TodayScreen(state,vm,day,{day=it},{recipe(it)},{go("settings")},{home("week")},{home("shopping")},{browse()},{mealId=it;go("replace")},{generate()})
                "week"->WeekScreen(state,vm,{day=it;home("today")},{home("shopping")},{go("settings")},{browse()},{generate()},{go("summary")})
                "recipe"->{val meal=state.plan?.meals?.find{it.id==mealId};val r=state.recipes.find{it.id==(meal?.recipeId?:recipeId)};if(r==null)MissingScreen{back()}else RecipeScreen(state,vm,r,meal,{back()},{cookingStep=0;go("cooking")},{go("schedule")},{go("replace")},{home("pantry")})}
                "cooking"->{val m=state.plan?.meals?.find{it.id==mealId};if(m==null)MissingScreen{back()}else CookingScreen(state,vm,m,cookingStep,{cookingStep=it},{back()},{go("timer")},{vm.mutate("Блюдо приготовлено",{home("today")}){Engine.cook(it,m.id)}})}
                "timer"->TimerScreen(state,vm,{back()})
                "recipes","replace"->RecipesScreen(state,vm,route=="replace",{back()},{r->recipeId=r.id;if(route=="replace")vm.replace(mealId,r.id,day,MealType.DINNER){go("preview")}else go("schedule")},{go("customRecipe")},state.plan?.meals?.find{it.id==mealId}?.type)
                "customRecipe"->CustomRecipeScreen(state,vm,{back()},{id->recipeId=id;go("schedule")})
                "schedule"->{val r=state.recipes.find{it.id==recipeId};if(r==null)MissingScreen{back()}else ScheduleScreen(state,r,day,{back()},{d,t,q->vm.replace(null,r.id,d,t,q){go("preview")}})}
                "preview"->PlanPreviewScreen(state,vm,{back()},{vm.applyPreview{home("week")}})
                "shopping"->ShoppingScreen(state,vm,{go("settings")},{go("shoppingAdd")},{id->shoppingId=id;go("purchase")},{go("prices")})
                "purchase"->{val row=Shopping.quote(state).rows.find{it.id==shoppingId};if(row==null)MissingScreen{back()}else PurchaseScreen(state,vm,row,{back()},{home("shopping")})}
                "shoppingAdd"->ShoppingAddScreen(state,vm,{back()},{home("shopping")})
                "pantry"->PantryScreen(state,{go("settings")},{editStock(null)},{editStock(it)},{browse()})
                "stockEdit"->StockEditor(state,vm,stockId,{back()})
                "settings"->SettingsScreen(state,{back()},{go(it)},{export.launch("sytno-${LocalDate.now()}.json")},{restore.launch(arrayOf("application/json","text/plain","application/octet-stream"))})
                "prices"->PricesScreen(state,vm,{back()},::openSite)
                "generation"->SytnoScreen("Собираем твою неделю","Учитываем твои настройки."){
                    item{EmptyState("Чтобы было вкусно, быстро и по бюджету","Проверяем ограничения, домашние продукты, упаковки и цели питания.","cooking-pot")};item{LinearProgressIndicator(Modifier.fillMaxWidth(),color=C.Brand)};item{Text("Подбор выполняется на телефоне. Интернет для него не нужен.",color=C.Secondary)}
                }
                "infeasible"->SytnoScreen("Не получилось собрать рацион","Изменим условия подбора.",back={back()},footer={PrimaryButton("Изменить настройки",{go("nutrition")});PrimaryButton("Изменить бюджет",{go("budget")},secondary=true)}){
                    item{Notice("Условия не выполнены",vm.preview?.issues?.joinToString("\n")?:"Проверь доступные продукты, технику и ограничения.",warning=true)};vm.preview?.let{result->item{AmountCard("Стоимость найденного варианта",Numbers.money(result.quote.total),"Бюджет ${Numbers.money(state.preferences.budget)}")}};item{Notice("Ничего не изменили","Прежний план и домашние запасы сохранены. Аллергии и цели не ослаблялись.")}
                }
                "summary"->SummaryScreen(state,vm,{back()},{go("budget")})
            }
            }
        }
    }
    vm.error?.let{message->AlertDialog(onDismissRequest={vm.error=null},title={Text("Проверь данные")},text={Text(message)},confirmButton={TextButton({vm.error=null}){Text("Понятно")}})}
    importText?.let{text->val imported=remember(text){StateCodec.decode(text)};AlertDialog(onDismissRequest={importText=null},title={Text("Восстановить данные?")},text={Text("В копии: ${imported.pantry.size} продуктов и ${imported.recipes.count{it.custom}} своих рецептов. Текущие меню, покупки, запасы и настройки будут заменены.")},confirmButton={TextButton({vm.restore(text){importText=null;home("today")}}){Text("Восстановить")}},dismissButton={TextButton({importText=null}){Text("Отменить")}})}
}
@Composable fun MissingScreen(back:()->Unit){SytnoScreen("Запись изменилась",back=back){item{EmptyState("Вернись к списку","Возможно, меню или покупки уже пересчитаны.")}}}
