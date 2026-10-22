package com.example.desayuno

import com.example.desayuno.model.StockInventory
import com.example.desayuno.repository.DesayunoRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DesayunoRepositoryTest {

    @Test
    fun testStockInventoryDefaults() {
        val stock = StockInventory(breadUnits = 50, quinuaLiters = 20, macaLiters = 15, soyaLiters = 15)
        assertEquals(50, stock.breadUnits)
        assertEquals(20, stock.quinuaLiters)
        assertEquals(15, stock.macaLiters)
        assertEquals(15, stock.soyaLiters)
    }

    @Test
    fun catalogIsNotFabricatedBeforeLoadingSupabase() {
        val menu = DesayunoRepository.menuState.value
        assertEquals(emptyList<Any>(), menu)
    }
}
