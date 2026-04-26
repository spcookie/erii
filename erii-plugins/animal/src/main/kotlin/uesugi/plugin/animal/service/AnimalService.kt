package uesugi.plugin.animal.service

import io.github.oshai.kotlinlogging.KotlinLogging
import uesugi.plugin.animal.core.FieldType
import uesugi.plugin.animal.core.Mode
import uesugi.plugin.animal.core.PersonaType
import uesugi.plugin.animal.domain.Persona
import uesugi.plugin.animal.domain.User
import uesugi.plugin.animal.domain.request.VisibleChangeType
import uesugi.plugin.animal.store.AnimalStore
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.max

class AnimalService(private val store: AnimalStore) {

    private val log = KotlinLogging.logger {}

    companion object {
        private const val COINS_PER_DRAW = 100
        private const val COINS_PER_10_DRAW = 1000
        private const val CONTRIBUTION_PER_CHECK_IN = 10
        private const val CONTRIBUTION_PER_10_MESSAGES = 10
        private const val MESSAGES_PER_CONTRIBUTION = 10
        private const val FIELD_PENALTY_ON_INACTIVE = 50
    }

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    suspend fun registerUser(groupId: String, userId: Long, name: String): User {
        val existingUser = store.getUser(groupId, userId)
        if (existingUser != null) {
            return existingUser
        }

        val user = User.newUser(
            id = userId,
            name = name,
            contributions = emptyMap()
        )

        store.saveUser(groupId, user)
        store.addUserId(groupId, userId)

        log.info { "User $userId registered with pet in group $groupId" }
        return user
    }

    suspend fun getUserPets(groupId: String, userId: Long): List<Persona> {
        val user = store.getUser(groupId, userId) ?: return emptyList()
        return user.personas.toList()
    }

    suspend fun viewPet(groupId: String, userId: Long, petId: Long): Persona? {
        val user = store.getUser(groupId, userId) ?: return null
        return user.personas.find { it.id == petId }
    }

    suspend fun onUserMessage(groupId: String, userId: Long): String {
        val user = store.getUser(groupId, userId) ?: return "请先注册宠物，使用 /animal register"
        val today = LocalDate.now().format(dateFormatter)

        user.todayMessageCount++

        // 打卡：当天首次发言
        if (user.lastCheckInDate != today) {
            user.lastCheckInDate = today
            user.todayMessageCount = 1
            user.updateContribution(CONTRIBUTION_PER_CHECK_IN)
            return "打卡成功！+${CONTRIBUTION_PER_CHECK_IN}贡献度"
        }

        // 每10条消息加贡献度
        if (user.todayMessageCount > 0 && user.todayMessageCount % MESSAGES_PER_CONTRIBUTION == 0) {
            user.updateContribution(CONTRIBUTION_PER_10_MESSAGES)
            return "发言奖励！+${CONTRIBUTION_PER_10_MESSAGES}贡献度"
        }

        return ""
    }

    suspend fun drawPet(groupId: String, userId: Long, count: Int): Result<String> {
        val user = store.getUser(groupId, userId) ?: return Result.failure(Exception("用户不存在"))

        val drawCount = if (count == 10) 10 else 1
        val cost = if (count == 10) COINS_PER_10_DRAW else COINS_PER_DRAW

        if (user.coins < cost) {
            return Result.failure(Exception("金币不足！需要 $cost 金币，当前 ${user.coins} 金币"))
        }

        user.coins -= cost

        val drawnPets = mutableListOf<Persona>()
        repeat(drawCount) {
            val personaType = PersonaType.random()
            val persona = user.addPersona(personaType)
            drawnPets.add(persona)
        }

        store.saveUser(groupId, user)

        val petInfo = drawnPets.joinToString(", ") { "${it.getType()} (Lv.${it.level()})" }
        return Result.success("抽宠成功！花费 $cost 金币，获得：$petInfo")
    }

    suspend fun sellPet(groupId: String, userId: Long, petId: Long): Result<String> {
        val user = store.getUser(groupId, userId) ?: return Result.failure(Exception("用户不存在"))

        if (user.personas.size <= 1) {
            return Result.failure(Exception("至少需要保留一只宠物"))
        }

        val pet = user.personas.find { it.id == petId }
            ?: return Result.failure(Exception("找不到宠物 $petId"))

        val price = calculatePetPrice(pet)
        user.coins += price
        user.deletePersona(petId)

        store.saveUser(groupId, user)
        return Result.success("售卖成功！获得 $price 金币")
    }

    fun calculatePetPrice(pet: Persona): Int {
        val rarityWeight = (pet.getType().weight * 100).toInt()
        return (rarityWeight * 10 + pet.level() * 5).toInt()
    }

    suspend fun getCoins(groupId: String, userId: Long): Int {
        val user = store.getUser(groupId, userId) ?: return 0
        return user.coins
    }

    suspend fun setFarmPet(groupId: String, userId: Long, petId: Long, visible: Boolean): Result<String> {
        val user = store.getUser(groupId, userId) ?: return Result.failure(Exception("用户不存在"))

        return try {
            user.changePersonaVisible(petId, visible, VisibleChangeType.DEFAULT)
            store.saveUser(groupId, user)
            Result.success("设置成功！宠物 ${if (visible) "已显示" else "已隐藏"}在农场中")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setField(groupId: String, userId: Long, fieldType: FieldType): Result<String> {
        val user = store.getUser(groupId, userId) ?: return Result.failure(Exception("用户不存在"))

        return try {
            user.changeField(fieldType)
            store.saveUser(groupId, user)
            Result.success("背景设置成功！")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resetDailyTasks() {
        val groupIds = store.getAllGroupIds()
        val today = LocalDate.now().format(dateFormatter)

        for (groupId in groupIds) {
            val userIds = store.getAllUserIds(groupId)
            for (userId in userIds) {
                val user = store.getUser(groupId, userId) ?: continue

                // 如果昨天没发言（lastCheckInDate 不是昨天），惩罚
                val yesterday = LocalDate.now().minusDays(1).format(dateFormatter)
                if (user.lastCheckInDate != today && user.lastCheckInDate != yesterday) {
                    // 随机一半的宠物各扣除50贡献度
                    val petsToPenalize = user.personas.shuffled().take(max(1, user.personas.size / 2))
                    for (pet in petsToPenalize) {
                        user.deductContribution(FIELD_PENALTY_ON_INACTIVE)
                    }
                    log.info { "User $userId in group $groupId penalized for inactivity" }
                }

                // 重置今日消息计数
                user.todayMessageCount = 0

                store.saveUser(groupId, user)
            }
        }
    }

    suspend fun getAllFieldTypes(groupId: String, userId: Long): List<FieldType> {
        val user = store.getUser(groupId, userId) ?: return emptyList()
        return user.fields.map { it.fieldType }
    }

    suspend fun getSelectedField(groupId: String, userId: Long): FieldType? {
        val user = store.getUser(groupId, userId) ?: return null
        return user.getSelectedField()
    }

    fun buildPersonaSvg(user: User, pet: Persona, mode: Mode): String {
        return pet.toSvgForce(mode)
    }

    fun buildFarmSvg(user: User): String {
        return user.createFarmAnimation()
    }

    fun buildLineSvg(user: User, pet: Persona): String {
        return pet.toSvgForce(Mode.LINE)
    }
}
