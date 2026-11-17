const page=await figma.getNodeByIdAsync('4:2');await figma.setCurrentPageAsync(page);
{
const s=start('welcome','Сытно','Еда на неделю. В твоём бюджете.');
photo(s.body,1,364,258);t(s.body,'Меньше думать.\nВкуснее есть.','Title','text',364);
t(s.body,'Простой рацион, продукты из твоих магазинов и покупки без лишнего.','Body','secondary',364);
for(const [icon,title,sub]of [['shopping-basket','Бюджет под контролем','Считаем целые упаковки'],['cooking-pot','Без сложной готовки','Подбираем под твоё время'],['refrigerator','Сначала то, что есть','Используем домашние запасы']]){const r=row(s.body,title,364,12);ico(r,icon,'brand');const c=a(r,'Copy','VERTICAL',328,0,2);t(c,title,'BodyStrong','text',328);t(c,sub,'Small','secondary',328);}
end(s,'Составить мою неделю','budget');}
{
const s=start('budget','Начнём с бюджета','На какую сумму планируем покупки?',{back:'welcome'});progress(s.body,1);amount(s.body,'4 000 ₽','Бюджет на 7 дней');
const r=row(s.body,'Budget presets',364,8);for(const [label,w]of [['3 000 ₽',112],['4 000 ₽',112],['5 000 ₽',124]])chip(r,label,label==='4 000 ₽',w);
field(s.body,'Период','26 октября — 1 ноября');field(s.body,'Для кого готовим','Для меня · 1 человек');
section(s.body,'Какие приёмы пищи');const meals=row(s.body,'Meal selection',364,8);chip(meals,'Завтрак',true,112);chip(meals,'Обед',true,112);chip(meals,'Ужин',true,124);
toggle(s.body,'Перекусы','Добавлять, если нужны по плану',true);notice(s.body,'Считаем сумму на кассе','Домашние запасы уменьшают покупки. Остатки упаковок сохраняются.');end(s,'Продолжить','stores');}
{
const s=start('stores','Где покупаешь?','Выбери доступные магазины.',{back:'budget'});progress(s.body,2);field(s.body,'Город','Москва');
for(const [name,sub,sel]of [['Пятёрочка','Выбран · у дома',true],['Чижик','Выбран · рядом с работой',true],['Магнит','Можно добавить',false],['Перекрёсток','Можно добавить',false]]){const r=row(s.body,'Store / '+name,364,12,16,'surface',16);const letter=row(r,'Store monogram',44,0,0,sel?'brandSoft':'disabled',12,44);letter.primaryAxisAlignItems='CENTER';t(letter,name.slice(0,1),'Subheading','brand',null);const c=a(r,'Store copy','VERTICAL',228,0,2);t(c,name,'BodyStrong','text',228);t(c,sub,'Caption','secondary',228);ico(r,sel?'circle-check':'plus',sel?'brand':'secondary');}
section(s.body,'Как удобнее покупать');const mode=row(s.body,'Shopping mode',364,8);chip(mode,'Один магазин',false,174);chip(mode,'До двух',true,182);
field(s.body,'Способ покупки','В магазине · выбрать адреса');notice(s.body,'Цены зависят от адреса','Для точного сравнения выбери конкретные магазины.','Warning');end(s,'Продолжить','nutrition');}
{
const s=start('nutrition','Питание под тебя','Цели можно задать сейчас или позже.',{back:'stores'});progress(s.body,3);toggle(s.body,'Учитывать калории и белок','Показывать ориентиры на каждый день',true);field(s.body,'Калории в день','2 100 ккал');field(s.body,'Белок в день · минимум','120 г');
t(s.body,'Это твои настройки, а не рассчитанная рекомендация.','Caption','secondary',364);section(s.body,'Время на готовку');const r=row(s.body,'Time chips',364,8);chip(r,'15 минут',false,112);chip(r,'30 минут',true,112);chip(r,'45 минут',false,124);toggle(s.body,'Готовить на два раза','Меньше времени у плиты',true);field(s.body,'Что есть на кухне','Плита, духовка, микроволновка');end(s,'Продолжить','restrictions',{label:'Пропустить цели питания',target:'restrictions'});}
{
const s=start('restrictions','Что исключаем?','Учтём ограничения в каждом блюде.',{back:'nutrition'});progress(s.body,4);field(s.body,'Найти продукт','Например, арахис или грибы');
section(s.body,'Аллергии');const r=row(s.body,'Allergy chips',364,8);chip(r,'Арахис ×',true,116);chip(r,'Добавить +',false,140);notice(s.body,'Строгое исключение','Для готовых продуктов важен состав и отметки о следах аллергенов.','Warning');
section(s.body,'Не ем и не люблю');const d=row(s.body,'Dislikes',364,8);chip(d,'Грибы ×',true,112);chip(d,'Печень ×',true,112);chip(d,'Ещё +',false,124);t(s.body,'Аллергии и нелюбимые продукты хранятся отдельно.','Small','secondary',364);field(s.body,'Предпочтения','Обычное домашнее питание');end(s,'Продолжить','pantrySetup');}
{
const s=start('pantrySetup','Что уже есть дома?','Это не придётся покупать повторно.',{back:'restrictions'});progress(s.body,5);pantry(s.body,'Рис','400 г · бакалея');pantry(s.body,'Яйца','6 шт. · холодильник');pantry(s.body,'Растительное масло','Есть · точный вес не нужен');b(s.body,'Добавить продукт','pantryAdd','Secondary');notice(s.body,'Запасы уже учтены','План предложит блюда с этими продуктами и докупит недостающее.','Success');toggle(s.body,'Использовать запасы в первую очередь','Особенно продукты с близким сроком',true);end(s,'Составить рацион','generation',{label:'Добавлю продукты позже',target:'generation'});}
return {createdNodeIds:[...new Set(createdNodeIds)],mutatedNodeIds:KEYS.map(k=>SCREENIDS[k]),actions:ACTIONS,screens:KEYS.map(k=>({key:k,id:W[k].root.id,contentHeight:W[k].body.height,viewportHeight:W[k].viewport.height})),font:'Manrope'};
