package ru.sytno.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

object Numbers {
    fun money(kopecks: Long): String {
        val formatter = NumberFormat.getNumberInstance(Locale.forLanguageTag("ru-RU"))
        formatter.minimumFractionDigits = if (kopecks % 100 == 0L) 0 else 2
        formatter.maximumFractionDigits = 2
        return formatter.format(BigDecimal.valueOf(kopecks, 2)) + " ₽"
    }
    fun parseMoney(value: String): Long {
        val text = value.trim().replace(" ", "").replace('\u00a0'.toString(), "").replace(',', '.')
        if (!text.matches(Regex("[0-9]{1,7}(\\.[0-9]{1,2})?"))) throw DomainError("Введи сумму в рублях, например 4000 или 89,90")
        val result = BigDecimal(text).multiply(BigDecimal(100)).longValueExact()
        if (result !in 0..100_000_000) throw DomainError("Сумма должна быть не больше 1 000 000 ₽")
        return result
    }
    fun inputMoney(value: Long): String = BigDecimal.valueOf(value, 2).stripTrailingZeros().toPlainString()
    fun parseAmount(value: String, multiplier: Int = 1): Long {
        val text = value.trim().replace(',', '.')
        if (!text.matches(Regex("[0-9]{1,7}(\\.[0-9]{1,3})?"))) throw DomainError("Введи корректное количество")
        val amount = try { BigDecimal(text).multiply(BigDecimal(multiplier)).setScale(0, RoundingMode.UNNECESSARY).longValueExact() }
            catch (_: ArithmeticException) { throw DomainError("Укажи целое количество в граммах, миллилитрах или штуках") }
        if (amount !in 1..1_000_000) throw DomainError("Количество должно быть от 1 до 1 000 000")
        return amount
    }
}
