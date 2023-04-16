import java.util.*


// DATA
data class Factory(
    val id: Int,
    val owner: FactoryOwner,
    var cyborgsCount: Int,
    val production: Int,
    val turnsBeforeProduction: Int,
    val distanceToOther: MutableMap<Int, Int> = mutableMapOf(),
    var priority: Float = 0f
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
    var idleTurnsInARow: Int = 0,
    var activeEnemyBombs: List<Bomb.ActiveEnemyBomb> = listOf()
) {
    fun clearData() {
        factories.clear()
        troops.clear()
        bombs.clear()
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
        }.onEach {
            it.priority = if (it.production == 0) {
                -100000f
            } else {
                it.priority * it.production * 2
            }
        }.onEach {
            if (it.owner != Factory.FactoryOwner.ME) {
                it.priority = it.priority - myFactory.distanceToOther[it.id]!! * 15
            }
        }.onEach {
            if (it.owner != Factory.FactoryOwner.ME) {
                it.priority = it.priority - it.cyborgsCount * 10
            }
        }.sortedBy {
            it.priority
        }.let { sortedList ->
            // completely ignore 0 production factories unless n IDLE turns
            if (idleTurnsInARow < 5) {
                sortedList.filter { factory ->
                    factory.production > 0
                }
            } else {
                sortedList
            }
        }.reversed()
    }

    fun shouldIncrement(factory: Factory): Boolean {
        return if (factories.filter { it.owner == Factory.FactoryOwner.NEUTRAL }.isEmpty()) {
            if (factory.owner == Factory.FactoryOwner.ME) {
                if (factory.production < 3 && factory.cyborgsCount > 10) {
                    getTroopsOnTheWayTo(
                        factory = factory,
                        owner = Troop.TroopOwner.ENEMY
                    ).sumOf { it.cyborgsCount }.let { incomingEnemies ->
                        if (factory.cyborgsCount > incomingEnemies) {
                            true
                        } else {
                            false
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

    gameData.factories.filter { it.owner == Factory.FactoryOwner.ME }.forEach { myFactory ->
        if (gameData.shouldIncrement(myFactory)) {
            actions.add("INC ${myFactory.id}")
        } else if (bombTimeResult != null && bombTimeResult.first == myFactory.id) {
            gameData.mySentBombsCount++
            actions.add("BOMB ${bombTimeResult.first} ${bombTimeResult.second}")
        } else {
            gameData.getTargetFactoriesSortedByPriority(
                myFactory = myFactory
            ).let { priorityTargetsList ->
                priorityTargetsList.filter { it.id != myFactory.id }.forEach { targetFactory ->
                    val myCyborgsOnTheWayCount = gameData.getTroopsOnTheWayTo(
                        factory = targetFactory,
                        owner = Troop.TroopOwner.ME
                    ).sumOf { it.cyborgsCount }

                    val enemyCyborgsOnTheWayCount = gameData.getTroopsOnTheWayTo(
                        factory = targetFactory,
                        owner = Troop.TroopOwner.ENEMY
                    ).sumOf { it.cyborgsCount }

                    val requiredCyborgsCount = when (targetFactory.owner) {
                        Factory.FactoryOwner.ME -> {
                            enemyCyborgsOnTheWayCount - targetFactory.cyborgsCount
                        }

                        Factory.FactoryOwner.ENEMY -> {
                            val reallyRequired =
                                targetFactory.cyborgsCount + enemyCyborgsOnTheWayCount + 1 + (targetFactory.distanceToOther[myFactory.id]!! * targetFactory.production) - myCyborgsOnTheWayCount
                            if (gameData.idleTurnsInARow > 15) {
                                reallyRequired - gameData.idleTurnsInARow * 2
                            } else {
                                reallyRequired
                            }
                        }

                        Factory.FactoryOwner.NEUTRAL -> {
                            targetFactory.cyborgsCount + enemyCyborgsOnTheWayCount + 1 - myCyborgsOnTheWayCount
                        }
                    }

                    if (myFactory.cyborgsCount >= requiredCyborgsCount && requiredCyborgsCount > 0) {
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
    // check if bombs still available to send
    if (gameData.mySentBombsCount < 2) {
        // check if no neutral factories (basically midgame check, so no bombs are sent at the start)
        if (gameData.factories.none { it.owner == Factory.FactoryOwner.NEUTRAL }) {
            // search for a 3 production enemy factory
            gameData.factories.firstOrNull { it.owner == Factory.FactoryOwner.ENEMY && it.production == 3 }
                ?.let { target ->
                    // create a list of all my factories ID
                    val myFactoryIDs = gameData.factories.filter { it.owner == Factory.FactoryOwner.ME }.map { it.id }
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
    return null
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