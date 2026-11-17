const page=await figma.getNodeByIdAsync('4:2');await figma.setCurrentPageAsync(page);
{
const s=start('today','Хорошего дня','Понедельник, 26 октября',{nav:'Today',setting:true});
const top=row(s.body,'Week budget',364,12,16,'brandDeep',20);const txt=a(top,'Budget','VERTICAL',244,0,2);t(txt,'Неделя в бюджете','Small','onBrand',244);t(txt,'3 420 ₽ из 4 000 ₽','Subheading','onBrand',244);action(ico(top,'arrow-right','onBrand'),'shopping');
const stats=row(s.body,'Daily nutrition',364,20);metric(stats,'2 100','ккал по плану');metric(stats,'121 г','белок');metric(stats,'35 мин','готовки за день');
section(s.body,'Сегодня в меню','Вся неделя','week');
mini(s.body,'Овсянка с бананом','Завтрак · 10 мин','420 ккал · 18 г белка',0);
meal(s.body,'Курица с гречкой','Обед · 25 мин · 42 г белка','580 ккал','≈ 148 ₽ / порция',1,'Pinned');
const quick=row(s.body,'Meal actions',364,8);b(quick,'Заменить блюдо','replace','Secondary').resize(178,56);b(quick,'Добавить своё','addDish','Ghost').resize(178,56);
mini(s.body,'Омлет с овощами','Ужин · 15 мин','510 ккал · 31 г белка',3);mini(s.body,'Творог и ягоды','Перекус · 2 мин','590 ккал · 30 г белка',4);
notice(s.body,'Сначала используем дома','Для завтрака уже есть овсянка. Яйца пригодятся к ужину.');end(s);}
{
const s=start('week','Твоя неделя','26 октября — 1 ноября',{nav:'Week',setting:true});
amount(s.body,'3 420 ₽','К покупке · бюджет 4 000 ₽');pair(s.body,'Остаётся в бюджете','580 ₽','brand');
const actions=row(s.body,'Week actions',364,8);b(actions,'Добавить блюдо','addDish','Secondary').resize(178,56);b(actions,'Параметры','editBudget','Ghost').resize(178,56);
notice(s.body,'Домашние запасы учтены','Рис, масло и часть яиц уже есть. Покупаем только недостающее.','Success');
const days=[['Пн, 26 · 2 100 ккал · 121 г белка','Овсянка · курица с гречкой · омлет'],['Вт, 27 · 2 060 ккал · 123 г белка','Творог · паста с курицей · чечевичный суп'],['Ср, 28 · 2 130 ккал · 122 г белка','Омлет · рис с овощами · курица с салатом'],['Чт, 29 · 2 090 ккал · 120 г белка','Овсянка · чечевичный суп · паста'],['Пт, 30 · 2 110 ккал · 125 г белка','Творог · курица с рисом · омлет'],['Сб, 31 · 2 080 ккал · 121 г белка','Омлет · паста · гречка с овощами'],['Вс, 1 · 2 100 ккал · 124 г белка','Овсянка · суп · курица с картофелем']];
for(const [title,sub]of days)list(s.body,title,sub,'today');t(s.body,'Перекусы включены в дневные итоги. Точные порции — внутри дня.','Caption','secondary',364);end(s,'Открыть список покупок','shopping');}
{
const s=start('recipe','Рецепт','Обед · понедельник',{back:'today'});photo(s.body,1,364,216);t(s.body,'Курица с гречкой\nи свежим салатом','Heading','text',364);
const chips=row(s.body,'Recipe metadata',364,8);chip(chips,'25 минут',true,112);chip(chips,'1 порция',false,112);action(chip(chips,'Закреплено',true,124),'pin');
const stats=row(s.body,'Nutrition',364,20);metric(stats,'580','ккал');metric(stats,'42 г','белок');metric(stats,'≈ 148 ₽','за порцию');
section(s.body,'Ингредиенты','Изменить','missingIngredients');
for(const [name,qty,status]of [['Куриное филе','150 г','Купить'],['Гречка сухая','70 г','Купить'],['Огурцы и помидоры','200 г','Купить'],['Масло растительное','10 г','Дома'],['Соль и специи','По вкусу','Дома']]){const r=row(s.body,name,364,8,12,'surface',12);const c=a(r,'Ingredient','VERTICAL',224,0,2);t(c,name,'BodyStrong','text',224);t(c,qty,'Caption','secondary',224);t(r,status,'Small',status==='Дома'?'brand':'secondary',108);}
notice(s.body,'Из запасов: масло и специи','Спишем их после отметки «Блюдо готово».');section(s.body,'Как приготовить');for(const [i,txt]of [['1','Промой гречку и поставь вариться.'],['2','Нарежь курицу, добавь специи и обжарь до готовности.'],['3','Нарежь овощи и заправь салат.'],['4','Подавай курицу с гречкой и салатом.']]){const r=row(s.body,'Step '+i,364,12);t(r,i,'BodyStrong','brand',24);t(r,txt,'Body','text',328);}end(s,'Начать готовить','cooking',{label:'Заменить блюдо',target:'replace'});}
{
const s=start('cooking','Готовим обед','Курица с гречкой и салатом',{back:'recipe'});photo(s.body,1,364,200);progress(s.body,2,4);t(s.body,'Обжарь курицу','Title','text',364);t(s.body,'Нарежь филе небольшими кусочками. Добавь масло и любимые специи. Обжарь до полной готовности, перемешивая.','Body','text',364);
const timer=a(s.body,'Timer','VERTICAL',364,24,8,'brandSoft',24);const r=row(timer,'Timer label',316,12);ico(r,'clock-3','brand');t(r,'Таймер шага','Small','brand',280);t(timer,'10:00','Amount','brand',316);b(timer,'Запустить таймер','cooking','Secondary').resize(316,56);
notice(s.body,'Гречка готовится параллельно','Пока курица на плите, можно нарезать овощи.');pair(s.body,'Порций готовим','1');end(s,'Блюдо готово','cooked',{label:'Вернуться к рецепту',target:'recipe'});}
{
const s=start('cooked','Обед готов','Приятного аппетита!',{back:'today'});const hero=a(s.body,'Success','VERTICAL',364,24,16,'brandSoft',24);ico(hero,'circle-check','brand',48);t(hero,'Один шаг —\nи запасы обновлены','Heading','brand',316);t(hero,'Отметили блюдо приготовленным и списали продукты за одну порцию.','Body','brand',316);
section(s.body,'Списано из запасов');for(const [name,qty]of [['Куриное филе','150 г'],['Гречка','70 г'],['Овощи','200 г'],['Масло','10 г']])pair(s.body,name,qty);notice(s.body,'Повторно не спишется','Это блюдо уже отмечено приготовленным.','Success');b(s.body,'Отменить отметку и вернуть продукты','recipe','Ghost');end(s,'Вернуться к сегодняшнему меню','today',{label:'Посмотреть запасы',target:'pantry'});}
{
const s=start('replace','Заменить обед','Понедельник · остальные блюда сохранятся',{back:'today'});const r=row(s.body,'Replacement filters',364,8);chip(r,'Дешевле',true,112);chip(r,'Быстрее',false,112);chip(r,'Больше белка',false,124);notice(s.body,'Сохраним бюджет и ограничения','Аллергии исключены. Закреплённые блюда остаются.');
meal(s.body,'Паста с курицей','20 мин · 40 г белка','560 ккал','На 35 ₽ дешевле',2,'Default','today');b(s.body,'Выбрать пасту · −35 ₽','today');mini(s.body,'Омлет с овощами','15 мин · 31 г белка','Покупки дешевле на 61 ₽',3,'today');mini(s.body,'Чечевичный суп','25 мин · 24 г белка','Покупки дешевле на 48 ₽',5,'today');end(s);}
return {createdNodeIds:[...new Set(createdNodeIds)],mutatedNodeIds:KEYS.map(k=>SCREENIDS[k]),actions:ACTIONS,screens:KEYS.map(k=>({key:k,id:W[k].root.id,contentHeight:W[k].body.height,viewportHeight:W[k].viewport.height}))};
