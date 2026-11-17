package ru.sytno.core

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class EdgeCasesTest {
    private fun rice():AppState { val s=AppState(); val r=Recipe("test","Рис",setOf(MealType.DINNER),listOf(Ingredient("rice",700)),listOf(CookStep("Свари рис")),10,20); return s.copy(recipes=s.recipes+r,plan=Plan(listOf(Meal("m",0,MealType.DINNER,r.id)),1)) }
    private inline fun rejects(block:()->Unit){try{block();fail("Expected DomainError")}catch(_:DomainError){}}
    private fun feed(overrides:String="",city:String="Москва",updated:String="1000000",pack:String="500",price:String="89.90",version:String="1")="""{"feedVersion":$version,"city":"$city","updatedAt":$updated,"offers":[{"foodId":"rice","store":"PYATEROCHKA","packAmount":$pack,"priceRoubles":"$price"$overrides}]}"""
    private val feedState=AppState(preferences=Preferences(city="Москва"))
    @Test fun expiredStockNotUsedForShoppingOrCooking(){val s=rice().copy(pantry=listOf(PantryItem("rice",1000,LocalDate.now().minusDays(1).toString())));assertEquals(1000L,s.stock("rice"));assertEquals(0L,s.usableStock("rice"));assertEquals(700L,Shopping.quote(s).rows.single().needed);rejects{Engine.cook(s,"m")}}
    @Test fun expiresTodayStillUsable(){val s=rice().copy(pantry=listOf(PantryItem("rice",1000,LocalDate.now().toString())));assertEquals(1000L,s.usableStock("rice"));assertTrue(Shopping.quote(s).rows.isEmpty())}
    @Test fun purchaseDoesNotMergeIntoExpiredStock(){val s=rice().copy(pantry=listOf(PantryItem("rice",1000,"2000-01-01")));rejects{Engine.buy(s,Shopping.quote(s).rows.single().id)};assertEquals(1000L,s.stock("rice"))}
    @Test fun unavailableManualSuppressesEstimate(){val s=rice().copy(preferences=Preferences(stores=setOf(Store.PYATEROCHKA)));val o=Offer("manual","rice",Store.PYATEROCHKA,500,10000,PriceSource.MANUAL,1,available=false);val q=Shopping.quote(s.copy(offers=s.offers+o));assertTrue(q.rows.isEmpty());assertEquals(setOf("rice"),q.missingFoods)}
    @Test fun confirmedPriceOverridesCheaperEstimate(){val s=rice().copy(preferences=Preferences(stores=setOf(Store.PYATEROCHKA)));val o=Offer("manual","rice",Store.PYATEROCHKA,500,50000,PriceSource.MANUAL,1);assertEquals(100000L,Shopping.quote(s.copy(offers=s.offers+o)).total)}
    @Test fun otherCityAndAddressNotUsed(){val s=rice().copy(preferences=Preferences(city="Москва",stores=setOf(Store.PYATEROCHKA),addresses=mapOf(Store.PYATEROCHKA to "ул. 1")));val o=Offer("wrong","rice",Store.PYATEROCHKA,1000,1,PriceSource.CATALOG,1,"Москва","ул. 2");assertNotEquals(1L,Shopping.quote(s.copy(offers=s.offers+o)).total);assertNotEquals(1L,Shopping.quote(s.copy(offers=s.offers+o.copy(city="Омск",address=""))).total)}
    @Test fun staleConfirmedPriceFlagged(){val s=rice().copy(offers=listOf(Offer("old","rice",Store.PYATEROCHKA,1000,100,PriceSource.CATALOG,1)));assertTrue(Shopping.quote(s).estimated)}
    @Test fun validFeedExactMoneyAndSource(){val o=PriceFeed.parse(feed(),feedState,1000000).single();assertEquals(8990L,o.price);assertEquals(500L,o.packAmount);assertEquals(PriceSource.CATALOG,o.source);assertEquals(1,PriceFeed.merge(feedState,listOf(o)).offers.count{it.id==o.id})}
    @Test fun feedWrongCityRejected(){rejects{PriceFeed.parse(feed(city="Омск"),feedState,1000000)}}
    @Test fun feedFutureRejected(){rejects{PriceFeed.parse(feed(updated="1300001"),feedState,1000000)}}
    @Test fun feedFractionalPackRejected(){rejects{PriceFeed.parse(feed(pack="1.5"),feedState,1000000)}}
    @Test fun feedFractionalTimestampRejected(){rejects{PriceFeed.parse(feed(updated="1.5"),feedState,1000000)}}
    @Test fun feedFractionalVersionRejected(){rejects{PriceFeed.parse(feed(version="1.5"),feedState,1000000)}}
    @Test fun feedNegativeAndOverflowPricesRejected(){for(p in listOf("-1","0.001","100000000000000000000","NaN"))rejects{PriceFeed.parse(feed(price=p),feedState,1000000)}}
    @Test fun feedUnknownProductAndStoreRejected(){rejects{PriceFeed.parse(feed().replace("rice","unknown"),feedState,1000000)};rejects{PriceFeed.parse(feed().replace("PYATEROCHKA","UNKNOWN"),feedState,1000000)}}
    @Test fun duplicateFeedRejected(){val f=feed();val item=f.substringAfter("\"offers\":[").substringBeforeLast("]}");rejects{PriceFeed.parse(f.replace(item,"$item,$item"),feedState,1000000)}}
    @Test fun invalidFeedIsAtomic(){val s=feedState;rejects{PriceFeed.parse("{}",s,1000000)};assertEquals(Catalog.offers,s.offers)}
    @Test fun insecureAndCredentialUrlsRejected(){for(url in listOf("http://example.com/feed","https://a:b@example.com/feed","file:///tmp/x","https://example.com/#x","bad"))rejects{PriceFeed.validateUrl(url)};assertEquals("https",PriceFeed.validateUrl("https://example.com/feed.json").scheme)}
    @Test fun feedUnavailablePreserved(){assertFalse(PriceFeed.parse(feed(",\"available\":false"),feedState,1000000).single().available)}
    @Test fun cookedSlotCannotBeReplacedBySchedule(){val s=Engine.cook(rice().copy(pantry=listOf(PantryItem("rice",1000))),"m");rejects{Engine.replacement(s,null,"beansRice",0,MealType.DINNER)}}
    @Test fun portionRoundingForEggsUsesActualNutrition(){val s=AppState();val m=Meal("m",0,MealType.BREAKFAST,"eggsToast",1);assertEquals(1L,s.ingredients(m)["eggs"]);val n=s.nutrition(m);assertTrue(n.calories>72)}
    @Test fun noRepeatsDuringNutritionOptimization(){val s=AppState(preferences=Preferences(budget=2_000_000,mealTypes=setOf(MealType.DINNER),allowRepeats=false,calories=2100,protein=80));repeat(12){val r=Planner.generate(s,it,2);assertEquals(r.plan.meals.size,r.plan.meals.map{it.recipeId}.toSet().size)}}
    @Test fun allBuiltInRecipesHaveConcreteInstructions(){assertEquals(28,Catalog.recipes.size);Catalog.recipes.forEach{assertTrue(it.id,it.steps.isNotEmpty());assertFalse(it.id,it.steps.any{it.text.contains("Приготовь основные ингредиенты")})}}
    @Test fun timerSurvivesBackup(){val s=AppState(timer=TimerRecord(2000000,0,60000));assertEquals(s.timer,StateCodec.decode(StateCodec.encode(s)).timer)}
    @Test fun invalidTimerRejected(){rejects{StateCodec.encode(AppState(timer=TimerRecord(-1,0,60000)))}}
    @Test fun pendingManualQuantityCoversPlanWithoutDoublePurchase(){val s=Engine.addManual(rice().copy(pantry=listOf(PantryItem("rice",400))),ManualShopping("extra","rice",500,Store.PYATEROCHKA,9000));val q=Shopping.quote(s);assertEquals(1,q.rows.size);assertEquals(9000L,q.total);val b=Engine.buy(s,q.rows.single().id);assertEquals(q.total,Shopping.quote(b).total);assertEquals(900L,b.stock("rice"))}
    @Test fun manualProductWithNoCatalogPriceCanCoverPlan(){val s=Engine.addManual(rice().copy(offers=emptyList()),ManualShopping("extra","rice",700,Store.MAGNIT,10000));val q=Shopping.quote(s);assertTrue(q.missingFoods.isEmpty());assertEquals(10000L,q.total)}
    @Test fun manualEstimateRoundsQuantityAndLabelsPrice(){val s=AppState();val item=Engine.manualItem(s,"rice",100,Store.PYATEROCHKA);assertEquals(500L,item.amount);assertEquals(8900L,item.price);assertTrue(Shopping.quote(Engine.addManual(s,item)).estimated)}
    @Test fun explicitManualPriceKeepsExactQuantity(){val item=Engine.manualItem(AppState(),"rice",100,Store.PYATEROCHKA,2000);assertEquals(100L,item.amount);assertFalse(item.estimated)}
    @Test fun cookingUndoRestoresExpiryLocationAndUseSoon(){val item=PantryItem("rice",700,LocalDate.now().plusDays(5).toString(),true,"Шкаф");val s=rice().copy(pantry=listOf(item));val cooked=Engine.cook(s,"m");assertEquals(0L,cooked.stock("rice"));val restored=Engine.undoCooking(StateCodec.decode(StateCodec.encode(cooked)),"m");assertEquals(item,restored.pantry.single())}
}
