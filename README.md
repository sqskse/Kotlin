🍳 Кухня Parallel

    Учебный проект на Kotlin Coroutines, моделирующий работу ресторанной кухни. Каждый сценарий демонстрирует отдельную концепцию асинхронного программирования — от параллельной подготовки ингредиентов до отказоустойчивой обработки заказов через каналы.

📑 Содержание

    Требования

    Запуск

    Структура проекта

    Демонстрируемые концепции

    Пример вывода

    Шпаргалка по корутинам

🛠 Требования
Компонент	Версия
Kotlin	1.8+
JDK	17+
Зависимость	kotlinx-coroutines-core

build.gradle.kts
kotlin

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}

🚀 Запуск

Проект состоит из одного файла KitchenParallel.kt.
bash

# через Gradle
./gradlew run

# или через kotlinc
kotlinc KitchenParallel.kt -cp kotlinx-coroutines-core.jar -include-runtime -d kitchen.jar
java -jar kitchen.jar

Точка входа — fun main() = runBlocking { ... }. Сценарии выполняются последовательно, каждый со своим выводом в консоль.
📁 Структура проекта
text

KitchenParallel.kt
├── Order, DishResult                 // модель данных
├── prepareIngredient()               // withContext(Dispatchers.Default)
├── CoroutineScope.checkDishQuality   // async → Deferred
├── cookOrder()                       // coroutineScope + 3 × launch
├── cancelOrder()                     // Job.cancel()
├── runChannelDemo()                  // Channel<DishResult>
└── main()                            // runBlocking — сценарий смены

🎯 Демонстрируемые концепции
1. Модель данных
kotlin

data class Order(
    val id: String,
    val dishName: String,
    var cookingProgress: Int = 0,
    var status: String = "PENDING"
)

data class DishResult(
    val orderId: String,
    val dishName: String,
    val qualityScore: Double
)

    Order содержит изменяемое состояние (cookingProgress, status), которое корутины обновляют параллельно.

    DishResult — неизменяемый результат дегустации.

2. Подготовка ингредиентов — Dispatchers.Default
kotlin

suspend fun prepareIngredient(ingredientName: String): String =
    withContext(Dispatchers.Default) { /* ... */ }

    withContext переключает корутину на пул вычислительных потоков — подходит для CPU-bound задач.

    Ингредиенты готовятся параллельно:

kotlin

val prepared = ingredients.map { async { prepareIngredient(it) } }.awaitAll()

    ✅ Все три ингредиента обрабатываются одновременно.

3. Параллельное приготовление — coroutineScope + launch
kotlin

suspend fun cookOrder(order: Order) = coroutineScope {
    launch { /* готовка */ }
    launch { /* температурный датчик */ }
    launch { /* мониторинг времени */ }
}

    coroutineScope создаёт дочернюю область и ждёт завершения всех запущенных в ней корутин.

    Три задачи работают параллельно:

        🍳 готовка с прогрессом,

        🌡 датчик температуры,

        ⏰ мониторинг времени.

4. Проверка качества — async / Deferred
kotlin

fun CoroutineScope.checkDishQuality(
    orderId: String,
    dishName: String
): Deferred<DishResult> = async {
    delay(3000)
    DishResult(orderId, dishName, Random.nextDouble(7.5, 10.0))
}

    Функция объявлена как extension на CoroutineScope — async наследует правильный scope.

    Возвращает Deferred<DishResult> — «обещание» результата:

kotlin

val deferred = checkDishQuality(order1.id, order1.dishName)
println("Шеф дегустирует...")   // выполняется, пока идёт async
val result = deferred.await()   // дожидаемся результата

    ✅ async не блокирует поток — можно выполнять другую работу.

5. Отмена заказа — Job.cancel()
kotlin

val job2 = launch { cookOrder(order2) }
launch { cancelOrder(order2, job2) }
job2.join()

    cancelOrder через delay(2000) вызывает job.cancel().

    Внутри cookOrder отмена обрабатывается корректно:

kotlin

catch (e: CancellationException) {
    order.status = "CANCELLED"
    throw e   // ОБЯЗАТЕЛЬНО пробрасываем дальше
}

    ⚠️ Проброс CancellationException — идиоматический паттерн: отмена не является обычной ошибкой, её нельзя «проглатывать».

6. Канал кухня → метрдотель
kotlin

val channel = Channel<DishResult>()

launch { /* producer: channel.send(...) ; channel.close() */ }
launch { for (result in channel) { /* consumer */ } }

    Channel — потокобезопасная очередь для обмена данными между корутинами.

    Паттерн producer → consumer:

        Кухня отправляет блюда через send.

        Метрдотель принимает через for (r in channel).

    channel.close() сигнализирует потребителю, что данных больше не будет — цикл корректно завершается.

7. Устойчивость к исключениям

Демонстрация через заказ с id = "burn-demo", который «подгорает» на 30 % прогресса:
kotlin

if (order.id == "burn-demo" && order.cookingProgress == 30) {
    throw IllegalStateException("🔥 Блюдо подгорело!")
}

Что происходит:
Шаг	Результат
Исключение ловится локально в launch	coroutineScope не падает
order.status = "BURNED"	заказ помечен как испорченный
Соседние заказы (ok-1, ok-2)	продолжают готовиться без сбоев

    💡 Правило: ловите исключения внутри конкретной launch-корутины, если не хотите каскадного падения всей области.

🖨 Пример вывода
text

════════ Кухня Parallel: старт смены ════════

─── Подготовка ингредиентов (Dispatchers.Default) ───
  ✓ Нарезанный лук
  ✓ Растопленное масло
  ✓ Взбитые сливки

─── Приготовление: заказ 1 ───
[1] 🍳 Ризотто с грибами: 10%
[1] 🍳 Ризотто с грибами: 20%
...
[1] ✅ Ризотто с грибами готово к подаче!
Итог: статус=READY, прогресс=100%

─── Дегустация шефа (async / await) ───
Шеф дегустирует...
Результат: Ризотто с грибами — оценка 8.7/10

─── Отмена заказа 2 через 2 сек ───
[2] ⛔ Приготовление отменено
Итог заказа 2: статус=CANCELLED, прогресс=40%

─── Канал: кухня → метрдотель ───
[Кухня] → отправлено: Суп
[Метрдотель] ← получено: Суп (заказ ch-0, качество 8.5)
...

─── Устойчивость: заказ burn-demo подгорает ───
[burn-demo] ⚠ Поймано исключение: 🔥 Блюдо подгорело!
[burn-demo] Соседние задачи и заказы продолжают работу.
[ok-1] ✅ Суп готово к подаче!
[ok-2] ✅ Салат готово к подаче!

─── Все заказы обработаны независимо ───
════════ Кухня Parallel: смена завершена ════════

    ⏱ Порядок строк может варьироваться — корутины выполняются конкурентно.

🧠 Шпаргалка по корутинам
Концепция	Где используется	Что даёт
runBlocking	main	мост между sync-миром и корутинами
coroutineScope	cookOrder, runChannelDemo	структурированная конкурентность
launch	готовка, датчик, мониторинг	fire-and-forget задачи
async / Deferred	checkDishQuality	отложенное значение
awaitAll	подготовка ингредиентов	параллельное ожидание результатов
withContext(Dispatchers.Default)	prepareIngredient	CPU-bound работа на отдельном пуле
Channel	кухня → метрдотель	producer-consumer
Job.cancel()	cancelOrder	отмена задачи
CancellationException	cookOrder	корректная обработка отмены (re-throw)
isActive	циклы готовки и мониторинга	cooperative cancellation
try/catch в launch	cookOrder	локальная устойчивость к ошибкам
📌 Итог

Проект показывает, как построить отказоустойчивую параллельную систему на корутинах:

    ✅ Задачи готовятся независимо.

    ✅ Отмена одного заказа не ломает остальные.

    ✅ Ошибка в одной корутине не роняет соседей (если её ловить).

    ✅ Результаты корректно передаются между компонентами через Channel.

    ✅ CPU-bound и IO-bound части разделены по диспетчерам.

Отличная песочница, чтобы «пощупать» структурированную конкурентность на знакомом примере. 🍽️
