package ru.sytno.core

object Catalog {
    private fun f(id: String, name: String, kcal: Double, protein: Double, category: String, vararg allergens: String, unit: UnitOfFood = UnitOfFood.GRAM) =
        Food(id, name, unit, kcal / 100, protein / 100, category, allergens.toSet())
    val foods = listOf(
        f("oats", "Овсяные хлопья", 366.0, 12.3, "Бакалея", "Глютен"),
        f("rice", "Рис", 344.0, 6.7, "Бакалея"), f("buckwheat", "Гречка", 308.0, 12.6, "Бакалея"),
        f("pasta", "Макароны", 350.0, 12.0, "Бакалея", "Глютен"), f("lentils", "Чечевица", 310.0, 24.0, "Бакалея"),
        f("beans", "Фасоль консервированная", 99.0, 6.7, "Бакалея"), f("bread", "Хлеб цельнозерновой", 240.0, 8.0, "Хлеб", "Глютен"),
        f("chicken", "Куриное филе", 113.0, 23.6, "Мясо"), f("turkey", "Филе индейки", 114.0, 23.7, "Мясо"),
        f("tuna", "Тунец консервированный", 110.0, 23.0, "Рыба", "Рыба"), f("fish", "Минтай", 72.0, 15.9, "Рыба", "Рыба"),
        Food("eggs", "Яйца", UnitOfFood.PIECE, 72.0, 6.3, "Молочные и яйца", setOf("Яйца")),
        f("cottage", "Творог 5%", 121.0, 17.0, "Молочные и яйца", "Молоко"),
        f("milk", "Молоко 2,5%", 52.0, 2.8, "Молочные и яйца", "Молоко", unit = UnitOfFood.ML),
        f("yogurt", "Йогурт натуральный", 66.0, 5.0, "Молочные и яйца", "Молоко"),
        f("cheese", "Сыр", 350.0, 25.0, "Молочные и яйца", "Молоко"),
        f("potato", "Картофель", 77.0, 2.0, "Овощи"), f("carrot", "Морковь", 35.0, 1.3, "Овощи"),
        f("onion", "Лук", 41.0, 1.4, "Овощи"), f("cabbage", "Капуста", 28.0, 1.8, "Овощи"),
        f("tomato", "Помидоры", 20.0, 1.1, "Овощи"), f("cucumber", "Огурцы", 15.0, .8, "Овощи"),
        f("vegetables", "Овощная смесь замороженная", 45.0, 2.5, "Овощи"),
        f("tomatoSauce", "Томаты протёртые", 30.0, 1.2, "Бакалея"),
        f("banana", "Бананы", 96.0, 1.5, "Фрукты"), f("apple", "Яблоки", 47.0, .4, "Фрукты"),
        f("berries", "Ягоды замороженные", 45.0, .8, "Фрукты"),
        f("oil", "Масло растительное", 899.0, 0.0, "Бакалея", unit = UnitOfFood.ML),
        f("sugar", "Сахар", 399.0, 0.0, "Бакалея"), f("salt", "Соль", 0.0, 0.0, "Бакалея"),
        f("peanut", "Арахис", 552.0, 26.3, "Бакалея", "Арахис"),
        f("mushroom", "Шампиньоны", 27.0, 4.3, "Овощи"), f("tofu", "Тофу", 73.0, 8.1, "Бакалея", "Соя")
    )
    private val packs = mapOf(
        "oats" to (500L to 7900L), "rice" to (500L to 8900L), "buckwheat" to (800L to 9900L), "pasta" to (450L to 7900L),
        "lentils" to (450L to 14900L), "beans" to (400L to 10900L), "bread" to (400L to 7900L),
        "chicken" to (500L to 24900L), "turkey" to (500L to 32900L), "fish" to (800L to 26900L), "tuna" to (185L to 15900L),
        "eggs" to (10L to 11900L), "cottage" to (400L to 15900L), "milk" to (1000L to 8900L), "yogurt" to (500L to 9900L), "cheese" to (200L to 17900L),
        "potato" to (1000L to 5900L), "carrot" to (1000L to 4900L), "onion" to (1000L to 4900L), "cabbage" to (1000L to 4900L),
        "tomato" to (500L to 13900L), "cucumber" to (500L to 10900L), "vegetables" to (400L to 9900L), "tomatoSauce" to (500L to 9900L),
        "banana" to (1000L to 13900L), "apple" to (1000L to 11900L), "berries" to (300L to 15900L), "oil" to (1000L to 13900L),
        "sugar" to (1000L to 7900L), "salt" to (1000L to 2900L), "peanut" to (200L to 9900L), "mushroom" to (400L to 14900L), "tofu" to (250L to 13900L)
    )
    val offers: List<Offer> = Store.entries.flatMap { store -> foods.map { food ->
        val (amount, price) = packs.getValue(food.id)
        val factor = when (store) { Store.CHIZHIK -> .96; Store.PYATEROCHKA -> 1.0; Store.MAGNIT -> 1.04; Store.PEREKRESTOK -> 1.19 }
        Offer("estimate:${store.name}:${food.id}", food.id, store, amount, (price * factor).toLong())
    } }
    private val stove = listOf(setOf(Equipment.STOVE), setOf(Equipment.MULTICOOKER))
    private val directions = mapOf(
        "oatApple" to listOf("Вымой яблоко, удали сердцевину и нарежь кубиками.", "Залей хлопья молоком, добавь 100–150 мл воды на порцию. Вари по инструкции на упаковке, помешивая.", "Добавь яблоко в готовую кашу."),
        "oatWater" to listOf("Залей хлопья водой по инструкции на упаковке и свари кашу, помешивая.", "Очисти и нарежь банан, добавь в готовую кашу."),
        "eggsToast" to listOf("Вымой и нарежь помидор. Хлеб подсуши на сухой сковороде или подай свежим.", "Разогрей масло на сковороде, разбей яйца и готовь под крышкой до полного схватывания белка и желтка.", "Подай яйца с хлебом и помидором."),
        "cottageBerry" to listOf("Разморозь ягоды в холодильнике. Если упаковка требует термообработки, прогрей ягоды согласно инструкции и остуди.", "Соедини творог, ягоды и сахар в миске."),
        "yogurtOats" to listOf("Выбери хлопья, которые по инструкции можно есть без варки; остальные заранее свари и остуди.", "Смешай хлопья с йогуртом, добавь нарезанный банан. Для мягкой текстуры оставь в холодильнике на 15 минут."),
        "cheeseToast" to listOf("Вымой огурец и нарежь ломтиками.", "Положи сыр и огурец на хлеб. Подай свежим; поджаривание не обязательно."),
        "chickenRice" to listOf("Промой рис и свари по инструкции на упаковке.", "Нарежь курицу небольшими кусочками, обжарь на масле 5 минут, помешивая.", "Добавь замороженные овощи и немного воды. Туши под крышкой до полной готовности курицы и овощей, примерно 12–15 минут.", "Подай с рисом. Если есть термометр, проверь курицу: внутри не ниже 74 °C."),
        "turkeyRice" to listOf("Промой и свари рис по инструкции на упаковке. Вымой, очисти и натри морковь.", "Нарежь индейку небольшими кусочками и обжарь на масле с морковью.", "Добавь немного воды, накрой и туши до полной готовности индейки. Если есть термометр, проверь: внутри не ниже 74 °C. Подай с рисом."),
        "beansRice" to listOf("Промой и свари рис по инструкции на упаковке.", "Очисти и мелко нарежь лук, обжарь на масле 3–4 минуты.", "Слей жидкость с фасоли, добавь фасоль и протёртые томаты к луку. Прогрей 5–7 минут и подай с рисом."),
        "lentilBuckwheat" to listOf("Промой гречку и чечевицу. Отвари раздельно по инструкциям на упаковках.", "Очисти и нарежь лук, натри морковь. Обжарь овощи на масле 5–7 минут.", "Смешай гречку, чечевицу и овощи, прогрей 2 минуты."),
        "tunaPasta" to listOf("Отвари макароны по инструкции на упаковке.", "Слей жидкость из тунца. Прогрей тунца с протёртыми томатами и маслом 3–5 минут.", "Соедини пасту с соусом и подавай."),
        "fishPotato" to listOf("Разморозь рыбу в холодильнике и проверь на кости. Вымой, очисти и тонко нарежь картофель и морковь.", "Смажь овощи маслом. Готовь в духовке при 200 °C около 15 минут; в аэрогриле следуй инструкции прибора.", "Добавь рыбу и готовь ещё 15–20 минут до готовности овощей и рыбы. Рыба должна легко разделяться вилкой; при наличии термометра температура внутри не ниже 63 °C."),
        "chickenOven" to listOf("Вымой, очисти и нарежь картофель и морковь небольшими кусочками. Нарежь курицу на отдельной доске.", "Смешай ингредиенты с маслом и разложи в форме одним слоем.", "Запекай в духовке при 200 °C около 30–35 минут до готовности картофеля и курицы. Для аэрогриля используй режим и время из инструкции. При наличии термометра проверь курицу: внутри не ниже 74 °C."),
        "cabbageChicken" to listOf("Вымой овощи, нашинкуй капусту и натри морковь. Нарежь курицу на отдельной доске.", "Обжарь курицу на масле 5 минут. Добавь овощи и немного воды.", "Туши под крышкой 20–25 минут до мягкости капусты и полной готовности курицы, помешивая. Подай с хлебом."),
        "riceEgg" to listOf("Свари рис по инструкции на упаковке.", "Разогрей масло, добавь замороженные овощи и готовь по инструкции на упаковке.", "Сдвинь овощи в сторону, добавь яйца и перемешивай до полного схватывания. Добавь рис и прогрей всё вместе."),
        "tofuRice" to listOf("Промой и свари рис по инструкции на упаковке.", "Нарежь тофу кубиками, обжарь на масле до лёгкой корочки.", "Добавь замороженные овощи и немного воды. Готовь по инструкции на упаковке овощей. Подай с рисом."),
        "beanSalad" to listOf("Слей жидкость с фасоли. Вымой и нарежь помидор и огурец.", "Смешай фасоль с овощами и маслом. Подай с хлебом; поджаривание не обязательно."),
        "tunaSalad" to listOf("Слей жидкость из тунца. Вымой и нарежь огурцы и помидоры.", "Соедини овощи, тунца и масло. Подай с хлебом."),
        "fruitYogurt" to listOf("Вымой яблоко, удали сердцевину и нарежь.", "Смешай яблоко с йогуртом."),
        "bananaCottage" to listOf("Отмерь творог и подай. Храни оставшийся творог в холодильнике согласно упаковке."),
        "fruit" to listOf("Вымой яблоко и банан. Очисти банан, удали сердцевину яблока и нарежь фрукты."),
        "smoothie" to listOf("Очисти банан и нарежь.", "Добавь в чашу блендера молоко, творог и банан. Взбей до однородности и подавай сразу.")
    )
    private fun r(id: String, name: String, types: Set<MealType>, items: List<Pair<String, Long>>, minutes: Int, photo: Int? = null, tools: List<Set<Equipment>> = stove, instructions: List<String> = emptyList()) =
        Recipe(id, name, types, items.map { Ingredient(it.first, it.second) }, (if (instructions.isNotEmpty()) instructions else directions.getValue(id)).map { CookStep(it) }, minutes, when(id){"chickenOven"->45;"fishPotato"->40;else->minutes + if (tools.isEmpty()) 0 else 10}, tools, photo)
    private val breakfast = setOf(MealType.BREAKFAST)
    private val main = setOf(MealType.LUNCH, MealType.DINNER)
    private val light = setOf(MealType.BREAKFAST, MealType.SNACK)
    val recipes = listOf(
        r("oatBanana", "Овсянка с бананом", breakfast, listOf("oats" to 65L, "milk" to 200L, "banana" to 100L), 10, 0,
            instructions = listOf("Отмерь овсяные хлопья и молоко.", "Приготовь кашу по инструкции на упаковке хлопьев.", "Нарежь банан и добавь в готовую кашу.")),
        r("oatApple", "Овсянка с яблоком", breakfast, listOf("oats" to 70L, "apple" to 150L, "milk" to 150L), 10, 0),
        r("oatWater", "Овсянка с фруктами на воде", breakfast, listOf("oats" to 80L, "banana" to 150L), 10, 0),
        r("eggsToast", "Яйца с тостами и овощами", breakfast, listOf("eggs" to 2L, "bread" to 80L, "tomato" to 100L, "oil" to 5L), 12, 3),
        r("cottageFruit", "Творог с бананом", light, listOf("cottage" to 200L, "banana" to 100L), 2, 4, emptyList(), listOf("Выложи творог в миску.", "Добавь нарезанный банан. Можно размять всё вилкой.")),
        r("cottageBerry", "Творог с ягодами", light, listOf("cottage" to 200L, "berries" to 100L, "sugar" to 10L), 3, 4, emptyList()),
        r("yogurtOats", "Йогурт с хлопьями и фруктами", light, listOf("yogurt" to 200L, "oats" to 50L, "banana" to 100L), 3, 0, emptyList()),
        r("cheeseToast", "Тосты с сыром и огурцом", breakfast, listOf("bread" to 100L, "cheese" to 50L, "cucumber" to 100L), 5, null, emptyList()),
        r("chickenBuckwheat", "Курица с гречкой и салатом", main, listOf("chicken" to 170L, "buckwheat" to 85L, "tomato" to 100L, "cucumber" to 100L, "oil" to 10L), 25, 1,
            instructions = listOf("Промой гречку и приготовь по инструкции на упаковке.", "Нарежь курицу. Разогрей сковороду с частью масла и обжарь филе до полной готовности.", "Вымой и нарежь огурец и помидор, заправь оставшимся маслом.", "Подай готовую курицу с гречкой и свежим салатом.")),
        r("chickenRice", "Курица с рисом и овощами", main, listOf("chicken" to 180L, "rice" to 85L, "vegetables" to 180L, "oil" to 10L), 25, 1),
        r("chickenPasta", "Паста с курицей", main, listOf("pasta" to 90L, "chicken" to 160L, "tomatoSauce" to 120L, "oil" to 8L), 20, 2,
            instructions = listOf("Отвари макароны по инструкции на упаковке.", "Нарежь филе и обжарь на масле до полной готовности.", "Добавь протёртые томаты и прогрей соус.", "Соедини пасту и соус с курицей, перемешай и подавай.")),
        r("turkeyRice", "Индейка с рисом", main, listOf("turkey" to 180L, "rice" to 85L, "carrot" to 100L, "oil" to 10L), 25, 1),
        r("lentilSoup", "Чечевичный суп", main, listOf("lentils" to 100L, "potato" to 150L, "carrot" to 70L, "onion" to 50L, "oil" to 10L, "bread" to 50L), 20, 5,
            instructions = listOf("Промой чечевицу. Очисти и нарежь овощи.", "Залей чечевицу и овощи водой. Свари до готовности чечевицы по инструкции на упаковке.", "Добавь масло, перемешай и подавай с хлебом.")),
        r("beansRice", "Рис с фасолью и томатами", main, listOf("rice" to 90L, "beans" to 200L, "tomatoSauce" to 100L, "onion" to 50L, "oil" to 10L), 20),
        r("lentilBuckwheat", "Гречка с чечевицей", main, listOf("buckwheat" to 70L, "lentils" to 80L, "carrot" to 100L, "onion" to 50L, "oil" to 10L), 25),
        r("omelet", "Омлет с овощами", setOf(MealType.BREAKFAST, MealType.DINNER), listOf("eggs" to 3L, "milk" to 50L, "tomato" to 150L, "bread" to 70L, "oil" to 8L), 15, 3,
            tools = listOf(setOf(Equipment.STOVE), setOf(Equipment.MICROWAVE)), instructions = listOf("Вымой и нарежь помидоры.", "Размешай яйца с молоком. Приготовь омлет с овощами до полного схватывания яиц.", "Подавай с хлебом.")),
        r("tunaPasta", "Паста с тунцом", main, listOf("pasta" to 90L, "tuna" to 150L, "tomatoSauce" to 100L, "oil" to 5L), 15, 2),
        r("fishPotato", "Минтай с картофелем", main, listOf("fish" to 220L, "potato" to 250L, "carrot" to 100L, "oil" to 12L), 20, null,
            tools = listOf(setOf(Equipment.OVEN), setOf(Equipment.AIR_FRYER))),
        r("chickenOven", "Курица с овощами в духовке", main, listOf("chicken" to 200L, "potato" to 230L, "carrot" to 100L, "oil" to 10L), 15, 1,
            tools = listOf(setOf(Equipment.OVEN), setOf(Equipment.AIR_FRYER))),
        r("cabbageChicken", "Тушёная капуста с курицей", main, listOf("chicken" to 200L, "cabbage" to 250L, "carrot" to 80L, "oil" to 10L, "bread" to 80L), 25),
        r("riceEgg", "Рис с яйцом и овощами", main, listOf("rice" to 90L, "eggs" to 2L, "vegetables" to 200L, "oil" to 10L), 20, 3),
        r("tofuRice", "Тофу с рисом и овощами", main, listOf("tofu" to 200L, "rice" to 85L, "vegetables" to 180L, "oil" to 10L), 20),
        r("beanSalad", "Салат с фасолью и тостами", main, listOf("beans" to 250L, "tomato" to 150L, "cucumber" to 100L, "bread" to 100L, "oil" to 12L), 10, null, emptyList()),
        r("tunaSalad", "Салат с тунцом", main, listOf("tuna" to 150L, "tomato" to 150L, "cucumber" to 150L, "bread" to 100L, "oil" to 12L), 10, null, emptyList()),
        r("fruitYogurt", "Йогурт с яблоком", setOf(MealType.SNACK), listOf("yogurt" to 200L, "apple" to 150L), 2, 4, emptyList()),
        r("bananaCottage", "Творог без добавок", setOf(MealType.SNACK), listOf("cottage" to 200L), 1, 4, emptyList()),
        r("fruit", "Банан и яблоко", setOf(MealType.SNACK), listOf("banana" to 130L, "apple" to 150L), 1, null, emptyList()),
        r("smoothie", "Банановый смузи с творогом", light, listOf("milk" to 200L, "banana" to 120L, "cottage" to 150L), 5, 4, listOf(setOf(Equipment.BLENDER)))
    )
    val allergenNames = listOf("Арахис", "Орехи", "Молоко", "Яйца", "Рыба", "Морепродукты", "Глютен", "Соя", "Кунжут")
}
