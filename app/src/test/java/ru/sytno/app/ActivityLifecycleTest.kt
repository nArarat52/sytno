package ru.sytno.app

import android.app.Application
import android.os.Looper
import android.os.Handler
import android.graphics.Bitmap
import android.graphics.Canvas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*
import ru.sytno.core.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w412dp-h892dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ActivityLifecycleTest {
    @get:Rule val compose=createEmptyComposeRule()
    private lateinit var scenario:ActivityScenario<MainActivity>
    private lateinit var store:StateStore
    @Before fun start(){
        Dispatchers.setMain(Handler(Looper.getMainLooper()).asCoroutineDispatcher())
        val app=ApplicationProvider.getApplicationContext<Application>()
        store=StateStore(File(app.filesDir,"sytno-state.json"))
        store.save(AppState(onboardingComplete=true))
        scenario=ActivityScenario.launch(MainActivity::class.java)
        waitFor("Хорошего дня")
    }
    @After fun stop(){scenario.close();Dispatchers.resetMain()}
    private fun waitFor(text:String){compose.waitUntil(15000){shadowOf(Looper.getMainLooper()).idle();compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
    private fun persisted(condition:(AppState)->Boolean){compose.waitUntil(15000){shadowOf(Looper.getMainLooper()).idle();compose.waitForIdle();condition(store.load().state)}}
    private fun shot(name:String){compose.waitForIdle();scenario.onActivity{a->val view=a.window.decorView;val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap));val file=File("build/reports/screenshots/$name.png");file.parentFile?.mkdirs();file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}}
    @Test fun coldStartAndRecreationPreserveScreenAndUnsavedForm(){
        compose.onNodeWithContentDescription("Настройки").performClick()
        compose.onNodeWithText("Магазины и адреса").performClick()
        compose.onNodeWithText("Город").performTextReplacement("Казань")
        scenario.recreate();waitFor("Где покупаешь?")
        compose.onNodeWithText("Казань").assertExists()
        compose.onNodeWithText("Сохранить магазины").performClick()
        persisted{it.preferences.city=="Казань"}
        waitFor("Мои настройки")
        assertEquals("Казань",store.load().state.preferences.city)
    }
    @Test fun recreateKeepsEquipmentSelectionAndPersistsIt(){
        compose.onNodeWithContentDescription("Настройки").performClick()
        compose.onNodeWithText("Техника на кухне").performScrollTo().performClick()
        compose.onNodeWithText("Духовка").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Духовка").assertIsOn()
        scenario.recreate();waitFor("Что есть на кухне?")
        compose.waitForIdle()
        compose.onNodeWithText("Духовка").assertIsOn()
        shot("lifecycle-equipment-before-save")
        compose.onNodeWithText("Сохранить технику").assertIsDisplayed().performClick();compose.waitForIdle();shot("lifecycle-equipment-after-click")
        persisted{Equipment.OVEN in it.preferences.equipment};waitFor("Мои настройки")
        assertTrue(Equipment.OVEN in store.load().state.preferences.equipment)
    }
}
