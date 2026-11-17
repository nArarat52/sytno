package ru.sytno.app

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.sytno.core.*
import java.io.File

class AppViewModel(application:Application):AndroidViewModel(application) {
    private val store=StateStore(File(application.filesDir,"sytno-state.json"))
    private val mutex=Mutex()
    private var pendingOperations=0
    var state:AppState? by mutableStateOf(null); private set
    var busy by mutableStateOf(false); private set
    var error:String? by mutableStateOf(null)
    var message:String? by mutableStateOf(null)
    var preview:PlanResult? by mutableStateOf(null); private set
    private var previewBase:AppState?=null
    init { viewModelScope.launch { val loaded=withContext(Dispatchers.IO){store.load()}; state=loaded.state; loaded.warning?.let{error=it} } }
    fun mutate(success:String?=null,onSuccess:()->Unit={},block:(AppState)->AppState) {
        pendingOperations++;busy=true
        viewModelScope.launch { mutex.withLock { try {
            val current=state ?: return@withLock
            val updated=withContext(Dispatchers.Default){block(current)}
            withContext(Dispatchers.IO){store.save(updated)}
            state=updated; message=success; onSuccess()
        } catch(e:Exception) { error=if(e is DomainError)e.message else "Не удалось сохранить изменения. Данные не изменены. ${e.localizedMessage.orEmpty()}" } finally { pendingOperations--;busy=pendingOperations>0 } } }
    }
    fun generate(onSuccess:()->Unit,onInfeasible:()->Unit) {
        pendingOperations++;busy=true;preview=null
        viewModelScope.launch { mutex.withLock { try {
            val current=state ?: return@withLock
            val result=withContext(Dispatchers.Default){Planner.generate(current,System.nanoTime().toInt())}
            if(result.success) { val updated=current.copy(plan=result.plan,onboardingComplete=true); withContext(Dispatchers.IO){store.save(updated)}; state=updated; preview=null; onSuccess() }
            else { preview=result; previewBase=current; onInfeasible() }
        } catch(e:Exception){error=e.message ?: "Не удалось составить рацион"; onInfeasible()} finally {pendingOperations--;busy=pendingOperations>0} } }
    }
    fun replace(mealId:String?,recipeId:String,day:Int,type:MealType,quarters:Int?=null,onReady:()->Unit) { val current=state?:return; runCatching{Engine.replacement(current,mealId,recipeId,day,type,quarters)}.onSuccess{preview=it;previewBase=current;onReady()}.onFailure{error=it.message} }
    fun applyPreview(onSuccess:()->Unit) { val result=preview?:return; if(!result.success){error=result.issues.joinToString("\n");return}; mutate("Рацион обновлён",onSuccess){if(it!=previewBase)throw DomainError("Запасы или настройки изменились. Открой замену ещё раз, чтобы пересчитать рацион.");it.copy(plan=result.plan,onboardingComplete=true)} }
    fun restore(text:String,onSuccess:()->Unit) { val value=runCatching{StateCodec.decode(text)}.getOrElse{error=it.message;return}; mutate("Данные восстановлены",onSuccess){value} }
    fun report(e:Throwable) { error=e.message ?: "Не удалось выполнить действие" }
}
