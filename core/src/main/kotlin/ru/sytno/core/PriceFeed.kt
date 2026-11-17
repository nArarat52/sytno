package ru.sytno.core

import com.google.gson.JsonParser
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness
import java.net.URI
import java.math.BigDecimal

object PriceFeed {
    fun validateUrl(value:String): URI {
        val uri=runCatching{URI(value.trim())}.getOrElse{throw DomainError("Проверь ссылку каталога")}
        if(uri.scheme!="https" || uri.host.isNullOrBlank() || uri.userInfo!=null || uri.fragment!=null) throw DomainError("Для каталога нужна ссылка HTTPS без логина и пароля")
        return uri
    }
    fun parse(text:String,state:AppState,now:Long=System.currentTimeMillis()): List<Offer> {
        if(text.toByteArray().size>StateCodec.MAX_BYTES)throw DomainError("Каталог слишком большой")
        return try {
            val root=GsonBuilder().setStrictness(Strictness.STRICT).create().fromJson(text,JsonObject::class.java)
            if(root["feedVersion"].asBigDecimal.intValueExact()!=1)throw DomainError("Неподдерживаемая версия каталога")
            val city=root["city"].asString
            if(city.isBlank()||!city.equals(state.preferences.city,true))throw DomainError("Каталог предназначен для другого города")
            val updated=root["updatedAt"].asBigDecimal.longValueExact()
            if(updated<=0||updated>now+300000)throw DomainError("Некорректная дата обновления каталога")
            val array=root["offers"].asJsonArray
            if(array.size() !in 1..5000)throw DomainError("Каталог должен содержать от 1 до 5000 цен")
            val offers=array.map { element ->
                val item=element.asJsonObject; val foodId=item["foodId"].asString; state.food(foodId)
                val store=Store.valueOf(item["store"].asString)
                val pack=item["packAmount"].asBigDecimal.longValueExact()
                val price=BigDecimal(item["priceRoubles"].asString).multiply(BigDecimal(100)).longValueExact()
                val address=item["address"]?.asString.orEmpty()
                val url=item["url"]?.asString.orEmpty(); if(url.isNotEmpty())validateUrl(url)
                if(pack !in 1..1_000_000||price !in 0..100_000_000||address.length>300)throw DomainError("Некорректная упаковка или цена")
                Offer("feed:${store.name}:$foodId:$pack:${address.hashCode()}",foodId,store,pack,price,PriceSource.CATALOG,updated,city,address,url,item["available"]?.asBoolean?:true)
            }
            if(offers.map{it.id}.toSet().size!=offers.size)throw DomainError("В каталоге повторяются товары")
            offers
        }catch(e:DomainError){throw e}catch(_:Exception){throw DomainError("Не удалось прочитать каталог. Проверь формат JSON и цены.")}
    }
    fun merge(state:AppState,offers:List<Offer>):AppState {
        val ids=offers.map{it.id}.toSet()
        val result=state.copy(offers=state.offers.filterNot{it.id in ids}+offers)
        StateCodec.validate(result);return result
    }
}
