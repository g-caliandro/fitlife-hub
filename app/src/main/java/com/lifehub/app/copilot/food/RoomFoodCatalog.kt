package com.lifehub.app.copilot.food

import com.lifehub.app.data.db.LifeHubDatabase
import com.lifehub.app.data.repo.BaseFoodRepository

class RoomFoodCatalog(private val db: LifeHubDatabase, private val base: BaseFoodRepository) : FoodCatalog {
    private val foods = db.foodDao()
    private val portionDao = db.portionDao()
    override suspend fun historyFoods() = foods.history()
    override suspend fun food(id: String) = foods.get(id)
    override suspend fun foodByBarcode(code: String) = foods.getByBarcode(code)
    override suspend fun foodByBaseId(baseId: String) = foods.byBaseId(baseId)
    override suspend fun portions(foodId: String) = portionDao.portions(foodId)
    override suspend fun baseFoods() = base.all()
    override suspend fun baseFood(id: String) = base.get(id)
    override suspend fun measures() = portionDao.measures()
}
