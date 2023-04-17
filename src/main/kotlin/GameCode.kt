import java.util.*

// todo if n idle, and this turn is idle, try send trom all factories to highest enemy priority
// todo if enemy factory prod 0 but truns before prod > 0 add to priority!!!
// todo keep idle on factory

// DATA
data class Factory(
    val id: Int,
    val owner: FactoryOwner,
    var cyborgsCount: Int,
    val production: Int,
    val turnsBeforeProduction: Int,
    val distanceToOther: MutableMap<Int, Int> = mutableMapOf(),
    var priority: Float = 0f,
    var isMyBombTarget: Boolean = false
) {
    enum class FactoryOwner {
        ME,
        ENEMY,
        NEUTRAL
    }
}

data class Troop(
    val id: Int,
    val owner: TroopOwner,
    val sourceFactoryId: Int,
    val targetFactoryId: Int,
    val cyborgsCount: Int,
    val turnsBeforeArrival: Int
) {
    enum class TroopOwner {
        ME,
        ENEMY
    }
}

data class Bomb(
    val id: Int,
    val owner: BombOwner,
    val sourceFactoryId: Int,
    val targetFactoryId: Int,
    val turnsBeforeBoom: Int
) {
    enum class BombOwner {
        ME,
        ENEMY
    }

    data class ActiveEnemyBomb(
        val id: Int,
        val sourceFactoryId: Int,
        val detectedOnTurn: Int
    )
}

data class FactoryRelations(
    val factorySourceId: Int,
    val factoryTargetId: Int,
    val distance: Int
)

data class GameData(
    var currentTurn: Int = 0,
    val factories: MutableList<Factory>,
    val troops: MutableList<Troop>,
    val bombs: MutableList<Bomb>,
    var mySentBombsCount: Int = 0,
    var bombSentThisTurn: Boolean = false,
    var idleTurnsInARow: Int = 0,
    var activeEnemyBombs: List<Bomb.ActiveEnemyBomb> = listOf(),
    var gameStage: GameStage = GameStage.OPENING,
    var myExpandTarget: Factory? = null
) {

    enum class GameStage {
        OPENING,
        MID_GAME,
        LATE_GAME
    }

    fun clearData() {
        factories.clear()
        troops.clear()
        bombs.clear()
    }

    fun updateGameStage() {
        gameStage = if (factories.none { it.owner == Factory.FactoryOwner.NEUTRAL && it.production != 0 }) {
            GameStage.MID_GAME
        } else {
            GameStage.OPENING
        }
    }

    fun getTargetFactoriesSortedByPriority(
        myFactory: Factory
    ): List<Factory> {
        return factories.onEach { targetFactory ->
            targetFactory.priority = if (targetFactory.owner == Factory.FactoryOwner.NEUTRAL) {
                100f
            } else {
                50f
            }
        }.filter {
            it.id != myFactory.id
        }.onEach {
            it.priority = if (it.production == 0) {
                -100000f
            } else {
                it.priority * it.production * 3
            }
        }.onEach {
            if (it.owner != Factory.FactoryOwner.ME) {
                it.priority = it.priority * (1 - myFactory.distanceToOther[it.id]!! * 0.1f)
            }
        }.onEach {
            if (it.owner != Factory.FactoryOwner.ME) {
                it.priority = it.priority - it.cyborgsCount
            }
        }.sortedBy {
            it.priority
        }.let { sortedList ->
            // completely ignore 0 production factories unless n IDLE turns
            if (idleTurnsInARow < 20) {
                sortedList.filter { factory ->
                    factory.production > 0
                }
            } else {
                sortedList
            }
        }.let { list ->
            if (currentTurn > 10) {
                list.filter { target ->
                    myFactory.distanceToOther[target.id]!! < 10 + idleTurnsInARow
                }
            } else {
                list
            }
        }.reversed()
    }


    /*
        if bomb no longer active remove from active enemy mobs list
        if bomb missing add to active enemy bmbs list
     */
    fun updateEnemyBombsData() {
        val enemyBombsThisTurn = bombs.filter { it.owner == Bomb.BombOwner.ENEMY }
        val listCopy = activeEnemyBombs.toMutableList()
        activeEnemyBombs.forEach { lastTurnData ->
            enemyBombsThisTurn.firstOrNull { it.id == lastTurnData.id }.let { matchedBomb ->
                if (matchedBomb == null) {
                    listCopy.remove(lastTurnData)
                }
            }
        }
        enemyBombsThisTurn.forEach { currentTurnData ->
            activeEnemyBombs.firstOrNull { it.id == currentTurnData.id }.let { matchedBomb ->
                if (matchedBomb == null) {
                    listCopy.add(
                        Bomb.ActiveEnemyBomb(
                            id = currentTurnData.id,
                            sourceFactoryId = currentTurnData.sourceFactoryId,
                            detectedOnTurn = currentTurn
                        )
                    )
                }
            }
        }
        activeEnemyBombs = listCopy
    }

    fun updateMyBombTargets() {
        bombs.filter { it.owner == Bomb.BombOwner.ME }.forEach { bomb ->
            factories.firstOrNull { it.id == bomb.targetFactoryId }?.isMyBombTarget = true
        }
    }

    fun getTroopsOnTheWayTo(
        factory: Factory,
        owner: Troop.TroopOwner
    ): List<Troop> {
        return troops.filter { it.targetFactoryId == factory.id }.filter { it.owner == owner }
    }
}

fun main(args: Array<String>) {
    val input = Scanner(System.`in`)
    val factoryCount = input.nextInt() // the number of factories
    val linkCount = input.nextInt() // the number of links between factories
    val factoryRelations = mutableListOf<FactoryRelations>()
    for (i in 0 until linkCount) {
        val factory1 = input.nextInt()
        val factory2 = input.nextInt()
        val distance = input.nextInt()
        factoryRelations.add(
            FactoryRelations(
                factorySourceId = factory1,
                factoryTargetId = factory2,
                distance = distance
            )
        )
    }

    val gameData = GameData(
        factories = mutableListOf(),
        troops = mutableListOf(),
        bombs = mutableListOf()
    )

    // game loop
    while (true) {
        gameData.clearData()
        gameData.currentTurn++
        gameData.bombSentThisTurn = false
        val entityCount = input.nextInt() // the number of entities (e.g. factories and troops)
        for (i in 0 until entityCount) {
            val entityId = input.nextInt()
            val entityType = input.next()
            val arg1 = input.nextInt()
            val arg2 = input.nextInt()
            val arg3 = input.nextInt()
            val arg4 = input.nextInt()
            val arg5 = input.nextInt()
            when (entityType) {
                "FACTORY" -> {
                    gameData.factories.add(
                        Factory(
                            id = entityId,
                            owner = when (arg1) {
                                1 -> Factory.FactoryOwner.ME
                                -1 -> Factory.FactoryOwner.ENEMY
                                else -> Factory.FactoryOwner.NEUTRAL
                            },
                            cyborgsCount = arg2,
                            production = arg3,
                            turnsBeforeProduction = arg4
                        )
                    )
                }

                "TROOP" -> {
                    gameData.troops.add(
                        Troop(
                            id = entityId,
                            owner = if (arg1 == 1) {
                                Troop.TroopOwner.ME
                            } else {
                                Troop.TroopOwner.ENEMY
                            },
                            sourceFactoryId = arg2,
                            targetFactoryId = arg3,
                            cyborgsCount = arg4,
                            turnsBeforeArrival = arg5
                        )
                    )
                }

                "BOMB" -> {
                    gameData.bombs.add(
                        Bomb(
                            id = entityId,
                            owner = if (arg1 == 1) {
                                Bomb.BombOwner.ME
                            } else {
                                Bomb.BombOwner.ENEMY
                            },
                            sourceFactoryId = arg2,
                            targetFactoryId = arg3,
                            turnsBeforeBoom = arg4
                        )
                    )
                }
            }
        }
        gameData.factories.forEach { factory ->
            factoryRelations.forEach { relation ->
                if (relation.factorySourceId == factory.id) {
                    factory.distanceToOther.put(
                        relation.factoryTargetId, relation.distance
                    )
                }
                if (relation.factoryTargetId == factory.id) {
                    factory.distanceToOther.put(
                        relation.factorySourceId, relation.distance
                    )
                }
            }
        }
        gameData.updateEnemyBombsData()
        gameData.updateMyBombTargets()
        gameData.updateGameStage()
        debug("EXPAND ON TURN ${gameData.currentTurn} -> ${gameData.myExpandTarget?.id}")

        handleAll(gameData).joinToString(separator = "; ").let {
            if (it.isEmpty()) {
                gameData.idleTurnsInARow++
                doNothing()
            } else {
                gameData.idleTurnsInARow = 0
                println(it)
            }
        }
    }
}

private fun handleAll(gameData: GameData): List<String> {
    val actions = mutableListOf<String>()

    val bombTimeResult = bombTime(gameData)
    expandOnNeutrals(gameData).let {
        if (it.isNotEmpty()) {
            actions.addAll(it)
        }
    }

    gameData.factories.filter { it.owner == Factory.FactoryOwner.ME }.forEach { myFactory ->
        if (myFactory.shouldIncrement(gameData)) {
            actions.add("INC ${myFactory.id}")
        } else if (bombTimeResult != null && bombTimeResult.first == myFactory.id) {
            gameData.mySentBombsCount++
            gameData.bombSentThisTurn = true
            actions.add("BOMB ${bombTimeResult.first} ${bombTimeResult.second}")
        } else if (myFactory.id != gameData.myExpandTarget?.id) {
            gameData.getTargetFactoriesSortedByPriority(
                myFactory = myFactory
            ).let { priorityTargetsList ->
                priorityTargetsList.filter { it.id != myFactory.id }.forEach { targetFactory ->
                    if (targetFactory.isMyBombTarget.not()) {
                        val myCyborgsOnTheWayCount = gameData.getTroopsOnTheWayTo(
                            factory = targetFactory,
                            owner = Troop.TroopOwner.ME
                        ).sumOf { it.cyborgsCount }

                        val enemyCyborgsOnTheWayCount = gameData.getTroopsOnTheWayTo(
                            factory = targetFactory,
                            owner = Troop.TroopOwner.ENEMY
                        ).sumOf { it.cyborgsCount }

                        val enemyCyborgsOnTheWayToMyFactoryCount = gameData.getTroopsOnTheWayTo(
                            factory = myFactory,
                            owner = Troop.TroopOwner.ENEMY
                        ).sumOf { it.cyborgsCount }

                        val requiredCyborgsCount = when (targetFactory.owner) {
                            Factory.FactoryOwner.ME -> {
                                enemyCyborgsOnTheWayCount - targetFactory.cyborgsCount
                            }

                            Factory.FactoryOwner.ENEMY -> {
                                val reallyRequired =
                                    targetFactory.cyborgsCount + enemyCyborgsOnTheWayCount + 1 + (targetFactory.distanceToOther[myFactory.id]!! * targetFactory.production + targetFactory.production) - myCyborgsOnTheWayCount
                                if (gameData.idleTurnsInARow > 15) {
                                    (reallyRequired - gameData.idleTurnsInARow * 2).coerceAtLeast(1)
                                } else {
                                    reallyRequired
                                }
                            }

                            Factory.FactoryOwner.NEUTRAL -> {
                                targetFactory.cyborgsCount + enemyCyborgsOnTheWayCount + 1 - myCyborgsOnTheWayCount
                            }
                        }

                        if (myFactory.cyborgsCount - enemyCyborgsOnTheWayToMyFactoryCount >= requiredCyborgsCount && requiredCyborgsCount > 0) {
                            myFactory.cyborgsCount -= requiredCyborgsCount
                            moveTroops(
                                sourceFactoryId = myFactory.id,
                                destinationFactory = targetFactory.id,
                                cyborgsCount = requiredCyborgsCount
                            ).apply {
                                actions.add(this)
                            }
                        }
                    }
                }
                if (myFactory.shouldExecuteRunBitchRunProtocol(gameData)) {
                    priorityTargetsList.firstOrNull { it.id != myFactory.id }?.let { firstPriorityTarget ->
                        actions.add(
                            moveTroops(
                                sourceFactoryId = myFactory.id,
                                destinationFactory = firstPriorityTarget.id,
                                cyborgsCount = myFactory.cyborgsCount
                            )
                        )
                    }
                }
            }
        }
    }
    return actions
}

/*
    Checks should send bomb
    If Yes, returns my factory id to enemy factory id Pair
    Otherwise returns null
 */
private fun bombTime(gameData: GameData): Pair<Int, Int>? {
    if (gameData.bombSentThisTurn.not()) {
        // check if bombs still available to send
        if (gameData.mySentBombsCount < 2) {
            // check if midgame
            if (gameData.gameStage == GameData.GameStage.MID_GAME) {
                // search for a 3 production enemy factory
                gameData.factories.firstOrNull { targetFactory ->
                    targetFactory.owner == Factory.FactoryOwner.ENEMY &&
                            targetFactory.production == 3 &&
                            targetFactory.turnsBeforeProduction == 0 &&
                            gameData.troops.filter { it.owner == Troop.TroopOwner.ME && it.targetFactoryId == targetFactory.id }
                                .sumOf { it.cyborgsCount } < targetFactory.cyborgsCount
                }
                    ?.let { target ->
                        // create a list of all my factories ID
                        val myFactoryIDs =
                            gameData.factories.filter { it.owner == Factory.FactoryOwner.ME }.map { it.id }
                        // sort distance to other factories
                        target.distanceToOther.toList().sortedBy { it.second }.toMap().keys.forEach {
                            if (myFactoryIDs.contains(it)) {
                                // check if no bomb on the way to target factory
                                if (gameData.bombs.none { bomb -> bomb.targetFactoryId == target.id }) {
                                    // on my closest factory found bomb time!
                                    return Pair(it, target.id)
                                }
                            }
                        }
                    }
            }
        }
    }
    return null
}

private fun expandOnNeutrals(gameData: GameData): List<String> {
    if (gameData.gameStage == GameData.GameStage.MID_GAME) {
        if (gameData.myExpandTarget != null) {
            return handleExpand(
                gameData = gameData,
                expandTarget = gameData.myExpandTarget!!
            )
        } else {
            gameData.factories.filter { it.owner == Factory.FactoryOwner.NEUTRAL && it.production == 0 }.forEach { newTarget ->
                handleExpand(
                    gameData = gameData,
                    expandTarget = newTarget
                )
            }
        }
    }
    return listOf()
}

private fun handleExpand(gameData: GameData, expandTarget: Factory): List<String> {
    if (gameData.troops.filter { it.targetFactoryId == expandTarget.id }.isEmpty()) {
        debug("D4")
        val myFactoriesIds = gameData.factories.filter { it.owner == Factory.FactoryOwner.ME }.map { it.id }
        val enemyFactoriesIds = gameData.factories.filter { it.owner == Factory.FactoryOwner.ENEMY }.map { it.id }

        var myAbsoluteDistance = 0
        var enemyAbsoluteDistance = 0
        expandTarget.distanceToOther.forEach { (t, u) ->
            if (myFactoriesIds.contains(t)) {
                myAbsoluteDistance += u
            }
            if (enemyFactoriesIds.contains(t)) {
                enemyAbsoluteDistance += u
            }
        }
        if (myAbsoluteDistance < enemyAbsoluteDistance) {
            var requiredCyborgs = if (expandTarget.owner == Factory.FactoryOwner.ME) {
                10 - expandTarget.cyborgsCount
            } else {
                expandTarget.cyborgsCount + 10
            }
            val tmpActions = mutableListOf<String>()
            expandTarget.distanceToOther.toList().sortedBy { it.second }.toMap().keys.forEach { id ->
                if (myFactoriesIds.contains(id)) {
                    gameData.factories.first { it.id == id }.let { potentialDonnor ->
                        if (potentialDonnor.cyborgsCount > gameData.troops.filter {
                                it.targetFactoryId == potentialDonnor.id && it.owner == Troop.TroopOwner.ENEMY
                            }.sumOf { it.cyborgsCount }) {
                            if (potentialDonnor.cyborgsCount < requiredCyborgs) {
                                tmpActions.add(
                                    moveTroops(
                                        sourceFactoryId = potentialDonnor.id,
                                        destinationFactory = expandTarget.id,
                                        cyborgsCount = potentialDonnor.cyborgsCount
                                    )
                                )
                                requiredCyborgs -= potentialDonnor.cyborgsCount
                                potentialDonnor.cyborgsCount = 0
                            } else {
                                tmpActions.add(
                                    moveTroops(
                                        sourceFactoryId = potentialDonnor.id,
                                        destinationFactory = expandTarget.id,
                                        cyborgsCount = potentialDonnor.cyborgsCount - requiredCyborgs
                                    )
                                )
                                potentialDonnor.cyborgsCount -= requiredCyborgs
                                gameData.myExpandTarget = expandTarget
                                return tmpActions
                            }
                        }
                    }
                }
            }
        }
    }
    return listOf()
}

fun Factory.shouldIncrement(gameData: GameData): Boolean {
    return if (gameData.gameStage == GameData.GameStage.MID_GAME) {
        if (owner == Factory.FactoryOwner.ME) {
            if (production < 3 && cyborgsCount > 10) {
                gameData.getTroopsOnTheWayTo(
                    factory = this,
                    owner = Troop.TroopOwner.ENEMY
                ).sumOf { it.cyborgsCount }.let { incomingEnemies ->
                    if (cyborgsCount > incomingEnemies) {
                        if (this.id == gameData.myExpandTarget?.id) {
                            gameData.myExpandTarget = null
                        }
                        return true
                    } else {
                        return false
                    }
                }
            } else {
                false
            }
        } else {
            false
        }
    } else {
        false
    }
}

/*
    Check if given factory could be the target of an enemy bomb explosion next turn
 */
private fun Factory.shouldExecuteRunBitchRunProtocol(
    gameData: GameData
): Boolean {
    gameData.activeEnemyBombs.forEach { bomb ->
        val distanceToLaunch = distanceToOther[bomb.sourceFactoryId]
        val runningTurns = gameData.currentTurn - bomb.detectedOnTurn
        if (distanceToLaunch == runningTurns + 1) {
            return true
        }
    }
    return false
}

private fun moveTroops(
    sourceFactoryId: Int,
    destinationFactory: Int,
    cyborgsCount: Int,
): String {
    return "MOVE $sourceFactoryId $destinationFactory $cyborgsCount"
}

private fun doNothing() {
    println("WAIT")
}

private fun debug(message: String) {
    System.err.println(message)
}