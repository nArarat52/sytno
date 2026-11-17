package ru.sytno.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import org.robolectric.Shadows.shadowOf
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import ru.sytno.core.*
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w412dp-h892dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class AppUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private lateinit var vm:AppViewModel
    private lateinit var seed:AppState
    private var fontScale by mutableFloatStateOf(1f)
    private var width by mutableIntStateOf(412)
    private var height by mutableIntStateOf(892)
    private var sessionKey by mutableIntStateOf(0)
    @Before fun setup() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val base=AppState(onboardingComplete=true,weekStart=LocalDate.now().toString(),preferences=Preferences(budget=400000,city="Москва",allergies=setOf("Арахис")))
        val recipe=Recipe("testRice","Рис с овощами",setOf(MealType.DINNER),listOf(Ingredient("rice",700)),listOf(CookStep("Приготовь рис.")),10,20,photo=null)
        seed=base.copy(recipes=base.recipes+recipe,pantry=listOf(PantryItem("rice",400)),plan=Plan(listOf(Meal("test-meal",0,MealType.DINNER,recipe.id)),1))
        File(app.filesDir,"sytno-state.json").delete();File(app.filesDir,"sytno-state.json.bak").delete()
        StateStore(File(app.filesDir,"sytno-state.json")).save(seed)
        vm=AppViewModel(app)
        compose.setContent{val density=LocalDensity.current;CompositionLocalProvider(LocalDensity provides Density(density.density,fontScale)){SytnoTheme{Box(Modifier.width(width.dp).height(height.dp)){key(sessionKey){vm.state?.let{SytnoApp(vm,it)}}}}}}
        await{vm.state!=null}
        compose.onNodeWithText("Хорошего дня").assertExists()
    }
    private fun await(condition:()->Boolean){compose.waitUntil(15000){shadowOf(Looper.getMainLooper()).idle();condition()}}
    private fun idle(){compose.waitForIdle();shadowOf(Looper.getMainLooper()).idle();await{!vm.busy};compose.waitForIdle()}
    private fun scrollTo(text:String){compose.onNodeWithTag("screen-list").performScrollToNode(hasText(text));compose.onNodeWithText(text).assertIsDisplayed()}
    private fun nav(text:String){compose.onNodeWithContentDescription(text,useUnmergedTree=true).performClick();compose.waitForIdle()}
    private fun shot(name:String){compose.waitForIdle();compose.runOnIdle{val view=compose.activity.window.decorView;val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap));val file=File("build/reports/screenshots/$name.png");file.parentFile?.mkdirs();file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}}
    @Test fun allMainTabsAndRealAssetsRender() {
        val meals=listOf(Meal("b",0,MealType.BREAKFAST,"oatBanana"),Meal("l",0,MealType.LUNCH,"chickenBuckwheat"),Meal("d",0,MealType.DINNER,"chickenPasta"),Meal("s",0,MealType.SNACK,"cottageBerry"))
        vm.mutate{seed.copy(plan=Plan(meals,1))};idle()
        shot("today");nav("Неделя");compose.onNodeWithText("Твоя неделя").assertExists();shot("week")
        nav("Покупки");compose.onNodeWithText("Добавить позицию").assertExists();shot("shopping")
        nav("Запасы");compose.onNodeWithText("Мои запасы").assertExists();shot("pantry")
    }
    @Test fun checkingPurchaseCreditsAndUncheckingRestoresExactly() {
        nav("Покупки")
        compose.onNodeWithTag("shopping-rice-needed").assertExists()
        val id=Shopping.quote(seed).rows.first{it.foodId=="rice"}.id
        compose.onNodeWithTag("check-$id").performClick();idle()
        assertEquals(900L,vm.state!!.stock("rice"));assertEquals(1,vm.state!!.activePurchases().size)
        compose.onNodeWithTag("shopping-rice-bought").assertExists();shot("shopping-bought")
        compose.onNodeWithTag("check-$id").performClick();idle()
        assertEquals(400L,vm.state!!.stock("rice"));assertEquals(0L,vm.state!!.spent())
    }
    @Test fun purchasePersistsBeforeSuccessIsVisible() {
        nav("Покупки");val id=Shopping.quote(seed).rows.first{it.foodId=="rice"}.id
        compose.onNodeWithTag("check-$id").performClick();idle()
        val app=ApplicationProvider.getApplicationContext<Application>();val loaded=StateStore(File(app.filesDir,"sytno-state.json")).load()
        assertEquals(900L,loaded.state.stock("rice"));assertNull(loaded.warning)
    }
    @Test fun equipmentSelectionIsFunctionalAndSaved() {
        compose.onNodeWithContentDescription("Настройки").performClick()
        compose.onNodeWithText("Техника на кухне").performScrollTo().performClick()
        compose.onNodeWithText("Плита").performClick()
        compose.onNodeWithText("Духовка").performClick()
        shot("equipment")
        compose.onNodeWithText("Сохранить технику").performClick();idle()
        assertTrue(Equipment.OVEN in vm.state!!.preferences.equipment);assertFalse(Equipment.STOVE in vm.state!!.preferences.equipment)
    }
    @Test fun invalidStockAmountDoesNotMutateData() {
        nav("Запасы");compose.onNodeWithText("Рис").performClick()
        compose.onNodeWithText("Количество, г").performTextReplacement("-10")
        compose.onNodeWithText("Сохранить продукт").performClick();idle()
        compose.onNodeWithText("Проверь данные").assertExists();assertEquals(400L,vm.state!!.stock("rice"))
    }
    @Test fun cookingDeductionAndUndoAreReal() {
        val row=Shopping.quote(seed).rows.first{it.foodId=="rice"}
        vm.mutate{Engine.buy(it,row.id)};idle()
        compose.onNodeWithText("Рис с овощами").performScrollTo().performClick()
        compose.onNodeWithText("Начать готовить").performClick()
        compose.onNodeWithText("Блюдо готово · списать продукты").performClick();idle()
        assertEquals(200L,vm.state!!.stock("rice"));assertTrue(vm.state!!.isCooked("test-meal"))
        compose.onNodeWithText("Рис с овощами").performScrollTo().performClick()
        compose.onNodeWithText("Отменить готовку и вернуть продукты").performClick();idle()
        assertEquals(900L,vm.state!!.stock("rice"));assertFalse(vm.state!!.isCooked("test-meal"))
    }
    @Test @Config(qualifiers="w320dp-h640dp") fun largeTextAndSmallDisplayStayUsable() {
        compose.runOnIdle{fontScale=1.6f;width=320;height=640}
        nav("Покупки");scrollTo("Добавить позицию");shot("shopping-large-text-320")
        nav("Запасы");scrollTo("Добавить продукт")
    }
    @Test @Config(qualifiers="w800dp-h480dp") fun landscapeLayoutNavigationWorks() {
        compose.runOnIdle{width=800;height=480}
        nav("Покупки");compose.onNodeWithText("Добавить позицию").performScrollTo().assertIsDisplayed();shot("shopping-landscape")
        nav("Неделя");compose.onNodeWithText("Открыть список покупок").assertIsDisplayed()
    }
    @Test fun firstLaunchWizardGeneratesAnActualWeek() {
        vm.mutate{AppState()};idle();compose.runOnIdle{sessionKey++}
        compose.onNodeWithText("Составить мою неделю").performClick()
        scrollTo("Бюджет недели, ₽")
        compose.onNodeWithText("Бюджет недели, ₽").performTextReplacement("4000")
        compose.onNodeWithText("Продолжить").performClick();idle()
        compose.onNodeWithText("Город").performTextReplacement("Москва")
        compose.onNodeWithText("Продолжить").performClick();idle()
        compose.onNodeWithText("Продолжить").performClick();idle()
        compose.onNodeWithText("Продолжить").performClick();idle()
        compose.onNodeWithText("Составить рацион").performClick();idle()
        compose.onNodeWithText("Твоя неделя").assertExists()
        assertTrue(vm.state!!.onboardingComplete);assertEquals(28,vm.state!!.plan!!.meals.size)
        assertTrue(Shopping.quote(vm.state!!).total<=400000);shot("generated-week")
    }
    @Test fun stalePreviewCannotOverwriteChangedStock() {
        vm.replace("test-meal","beansRice",0,MealType.DINNER){}
        vm.mutate{Engine.setStock(it,PantryItem("rice",123))};idle()
        vm.applyPreview{};idle()
        assertNotNull(vm.error);assertEquals("testRice",vm.state!!.plan!!.meals.single().recipeId);assertEquals(123L,vm.state!!.stock("rice"))
    }
    @Test fun manualProductCanBeAddedBoughtAndCredited() {
        nav("Покупки");scrollTo("Добавить позицию");compose.onNodeWithText("Добавить позицию").performClick()
        compose.onNodeWithText("Продукт").performClick();compose.onNodeWithText("Поиск").performTextReplacement("Рис");compose.onNodeWithTag("pick-food-rice").performClick()
        compose.onNodeWithText("Количество, г").performTextReplacement("100")
        compose.onNodeWithText("Стоимость, ₽ · пусто для оценки").performTextReplacement("20")
        compose.onNodeWithText("Добавить в список").performClick();idle()
        assertEquals(1,vm.state!!.manualShopping.size)
        val row=Shopping.quote(vm.state!!).rows.first{it.id.startsWith("manual:")}
        compose.onNodeWithTag("screen-list").performScrollToNode(hasTestTag("check-${row.id}"))
        compose.onNodeWithTag("check-${row.id}").performClick();idle()
        assertEquals(500L,vm.state!!.stock("rice"));assertEquals(2000L,vm.state!!.spent())
    }
    @Test fun ownRecipeCanBeSavedScheduledAndApplied() {
        scrollTo("Добавить блюдо");compose.onNodeWithText("Добавить блюдо").performClick()
        compose.onNodeWithText("Добавить свой рецепт").performClick()
        compose.onNodeWithText("Название блюда").performTextReplacement("Мой рис")
        compose.onNodeWithText("Количество порций").performTextReplacement("2")
        scrollTo("Продукт");compose.onNodeWithText("Продукт").performClick()
        compose.onNodeWithText("Поиск").performTextReplacement("Рис");compose.onNodeWithTag("pick-food-rice").performClick()
        scrollTo("Количество, г");compose.onNodeWithText("Количество, г").performTextReplacement("200")
        scrollTo("Добавить ингредиент");compose.onNodeWithText("Добавить ингредиент").performClick()
        compose.onNodeWithText("Сохранить рецепт").performClick();idle()
        assertEquals(100L,vm.state!!.recipes.single{it.name=="Мой рис"}.ingredients.single().amount)
        compose.onNodeWithText("Проверить изменения").performClick()
        compose.onNodeWithText("Применить изменения").performClick();idle()
        val meal=vm.state!!.plan!!.meals.single();assertEquals("Мой рис",vm.state!!.recipe(meal.recipeId).name);assertTrue(meal.pinned)
    }
    @Test fun switchingGramsAndKilogramsPreservesStock(){
        nav("Запасы");compose.onNodeWithText("Рис").performClick()
        compose.onNodeWithText("Килограммы").performClick();compose.onNodeWithText("0.4").assertExists()
        compose.onNodeWithText("Граммы").performClick();compose.onNodeWithText("400").assertExists()
        compose.onNodeWithText("Килограммы").performClick();compose.onNodeWithText("Сохранить продукт").performClick();idle()
        assertEquals(400L,vm.state!!.stock("rice"))
    }
}
