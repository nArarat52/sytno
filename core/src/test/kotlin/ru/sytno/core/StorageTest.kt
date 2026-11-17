package ru.sytno.core

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class StorageTest {
    private inline fun rejected(block: () -> Unit) { try { block(); fail("Must reject invalid input") } catch (_: DomainError) {} }
    @Test fun roundTripCompleteState() { val s=AppState(preferences=Preferences(city="Москва",allergies=setOf("Арахис")),pantry=listOf(PantryItem("rice",400,"2027-01-01",true))); assertEquals(s,StateCodec.decode(StateCodec.encode(s))) }
    @Test fun invalidJsonIsRejected() { for (text in listOf("", "null", "{}", "[]", "{bad", "{\"version\":999}", StateCodec.encode(AppState()).replace("\"preferences\": {","\"preferences\": null, \"ignored\": {"))) rejected { StateCodec.decode(text) } }
    @Test fun unknownVersionRejected() { rejected { StateCodec.encode(AppState(version=2)) } }
    @Test fun duplicateIdsRejected() { val s=AppState(); rejected { StateCodec.encode(s.copy(foods=s.foods+s.foods.first())) } }
    @Test fun nanNutrientsRejected() { val s=AppState(); rejected { StateCodec.encode(s.copy(foods=s.foods.mapIndexed { i,f -> if(i==0)f.copy(caloriesPerUnit=Double.NaN) else f })) } }
    @Test fun invalidSettingsRejected() { val s=AppState(); for(p in listOf(Preferences(stores=emptySet()),Preferences(budget=-1),Preferences(maxStores=0),Preferences(calories=0),Preferences(protein=0),Preferences(mealTypes=emptySet()))) rejected { StateCodec.encode(s.copy(preferences=p)) } }
    @Test fun oversizedFileRejected() { rejected { StateCodec.decode(" ".repeat(StateCodec.MAX_BYTES+1)) } }
    @Test fun atomicSaveLoadAndFallback() { val dir=Files.createTempDirectory("sytno-test").toFile(); val file=dir.resolve("state.json"); val store=StateStore(file); val a=AppState(); val b=a.copy(pantry=listOf(PantryItem("rice",900))); store.save(a); store.save(b); assertEquals(b,store.load().state); file.writeText("broken"); val recovered=store.load(); assertEquals(a,recovered.state); assertNotNull(recovered.warning); store.save(recovered.state); assertEquals(a,store.load().state); assertTrue(dir.listFiles()!!.any { it.name.startsWith("corrupt-") }); dir.deleteRecursively() }
    @Test fun preservesMalformedDataAndWarns() { val dir=Files.createTempDirectory("sytno-test").toFile(); val file=dir.resolve("state.json"); file.writeText("broken"); val loaded=StateStore(file).load(); assertNotNull(loaded.warning); assertEquals("broken",file.readText()); dir.deleteRecursively() }
    @Test fun moneyParsesCommaExactly() { assertEquals(8990L,Numbers.parseMoney("89,90")); assertEquals(400000L,Numbers.parseMoney("4 000")); assertEquals("89.9",Numbers.inputMoney(8990)) }
    @Test fun malformedAndOverflowNumbersRejected() { for(s in listOf("-1","NaN","Infinity","1e9","12.999","99999999","")) rejected { Numbers.parseMoney(s) }; for(s in listOf("0","-1","1e4","0.2","999999999999999")) rejected { Numbers.parseAmount(s) } }
    @Test fun kgConvertsWithoutRoundingLoss() { assertEquals(500L,Numbers.parseAmount("0,5",1000)); assertEquals(1250L,Numbers.parseAmount("1.25",1000)) }
}
