import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.random.Random

// ============================================================
// 1. Модель данных
// ============================================================
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

// ============================================================
// 2. Подготовка ингредиентов (Dispatchers.Default)
// ============================================================
suspend fun prepareIngredient(ingredientName: String): String = withContext(Dispatchers.Default) {
    // Имитация вычислительной операции (нарезка, взбивание и т.п.)
    delay(1500)
    when (ingredientName.lowercase()) {
        "onion", "лук"     -> "Нарезанный лук"
        "butter", "масло"  -> "Растопленное масло"
        "cream", "сливки"  -> "Взбитые сливки"
        else               -> "Подготовлено: $ingredientName"
    }
}

// ============================================================
// 4. Проверка качества (async / Deferred)
//    Extension на CoroutineScope — так async получает правильный scope.
//    Возвращает Deferred, ожидается через .await() в main().
// ============================================================
fun CoroutineScope.checkDishQuality(orderId: String, dishName: String): Deferred<DishResult> = async {
    // Шеф дегустирует 3 секунды
    delay(3000)
    DishResult(
        orderId    = orderId,
        dishName   = dishName,
        qualityScore = Random.nextDouble(7.5, 10.0)
    )
}

// ============================================================
// 3. Приготовление заказа: три параллельные задачи
//  + 7. Устойчивость к исключениям (try-catch внутри launch)
// ============================================================
suspend fun cookOrder(order: Order) = coroutineScope {

    // ---- Задача 1: готовка ----
    launch {
        try {
            while (isActive && order.cookingProgress < 100) {
                delay(500)
                if (!isActive) break
                order.cookingProgress = (order.cookingProgress + 10).coerceAtMost(100)
                order.status = "COOKING"
                println("[${order.id}] 🍳 ${order.dishName}: ${order.cookingProgress}%")

                // Демонстрация «негативного сценария»:
                // заказ с id = "burn-demo" подгорает на 30% прогресса
                if (order.id == "burn-demo" && order.cookingProgress == 30) {
                    throw IllegalStateException("🔥 Блюдо подгорело!")
                }
            }
            if (order.cookingProgress >= 100) {
                order.status = "READY"
                println("[${order.id}] ✅ ${order.dishName} готово к подаче!")
            }
        } catch (e: CancellationException) {
            order.status = "CANCELLED"
            println("[${order.id}] ⛔ Приготовление отменено")
            throw e // важно: пробрасываем дальше, чтобы отмена сработала корректно
        } catch (e: Exception) {
            // Ловим локально — coroutineScope НЕ падает, другие задачи/заказы живы
            order.status = "BURNED"
            println("[${order.id}] ⚠ Поймано исключение: ${e.message}")
            println("[${order.id}] Соседние задачи и заказы продолжают работу.")
        }
    }

    // ---- Задача 2: контроль температуры ----
    launch {
        delay(2000)
        if (isActive) {
            println("[${order.id}] 🌡 Температурный датчик: показания в норме")
        }
    }

    // ---- Задача 3: мониторинг времени ----
    launch {
        while (isActive) {
            if (order.status in listOf("BURNED", "READY", "CANCELLED")) break
            if (order.cookingProgress >= 80) {
                println("[${order.id}] ⏰ ВНИМАНИЕ: блюдо почти готово, пора заканчивать!")
                break
            }
            delay(100)
        }
    }
}

// ============================================================
// 5. Отмена заказа через 2 секунды
// ============================================================
suspend fun cancelOrder(order: Order, job: Job) {
    delay(2000)
    job.cancel()
    println("Order ${order.id} canceled, dish ${order.dishName} удалено из приготовления")
}

// ============================================================
// 6. Канал кухня → метрдотель
// ============================================================
suspend fun runChannelDemo() = coroutineScope {
    val channel = Channel<DishResult>()

    // Производитель (кухня)
    launch {
        val dishes = listOf("Суп", "Ризотто", "Стейк", "Салат", "Десерт")
        dishes.forEachIndexed { i, dish ->
            delay(300)
            val result = DishResult(
                orderId      = "ch-$i",
                dishName     = dish,
                qualityScore = Random.nextDouble(7.0, 10.0)
            )
            channel.send(result)
            println("[Кухня] → отправлено: $dish")
        }
        channel.close()
        println("[Кухня] Канал закрыт")
    }

    // Потребитель (метрдотель)
    launch {
        for (result in channel) {
            println(
                "[Метрдотель] ← получено: ${result.dishName} " +
                        "(заказ ${result.orderId}, качество ${"%.1f".format(result.qualityScore)})"
            )
        }
        println("[Метрдотель] Все блюда приняты")
    }
}

// ============================================================
// main — прогоняем ВСЕ функции
// ============================================================
fun main() = runBlocking {
    println("════════ Кухня Parallel: старт смены ════════\n")

    // ---------- 2. Подготовка ингредиентов ----------
    println("─── Подготовка ингредиентов (Dispatchers.Default) ───")
    val ingredients = listOf("onion", "butter", "cream")
    val prepared = ingredients.map { async { prepareIngredient(it) } }.awaitAll()
    prepared.forEach { println("  ✓ $it") }

    // ---------- 3. Параллельное приготовление ----------
    println("\n─── Приготовление: заказ 1 ───")
    val order1 = Order("1", "Ризотто с грибами")
    cookOrder(order1)
    println("Итог: статус=${order1.status}, прогресс=${order1.cookingProgress}%")

    // ---------- 4. Проверка качества через Deferred ----------
    println("\n─── Дегустация шефа (async / await) ───")
    val deferred = checkDishQuality(order1.id, order1.dishName)
    println("Шеф дегустирует...")
    val result = deferred.await()
    println("Результат: ${result.dishName} — оценка ${"%.1f".format(result.qualityScore)}/10")

    // ---------- 5. Отмена заказа ----------
    println("\n─── Отмена заказа 2 через 2 сек ───")
    val order2 = Order("2", "Стейк")
    val job2 = launch { cookOrder(order2) }
    launch { cancelOrder(order2, job2) }
    job2.join()
    println("Итог заказа 2: статус=${order2.status}, прогресс=${order2.cookingProgress}%")

    // ---------- 6. Канал ----------
    println("\n─── Канал: кухня → метрдотель ───")
    runChannelDemo()

    // ---------- 7. Один горит — остальные продолжают ----------
    println("\n─── Устойчивость: заказ burn-demo подгорает ───")
    coroutineScope {
        listOf(
            Order("ok-1",      "Суп"),
            Order("burn-demo", "Пицца"),
            Order("ok-2",      "Салат")
        ).forEach { order ->
            launch { cookOrder(order) }
        }
    }
    println("\n─── Все заказы обработаны независимо ───")

    println("\n════════ Кухня Parallel: смена завершена ════════")
}