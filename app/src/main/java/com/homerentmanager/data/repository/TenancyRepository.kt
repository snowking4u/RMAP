package com.homerentmanager.data.repository

import com.homerentmanager.data.dao.TenancyDao
import com.homerentmanager.data.local.DatabaseProvider
import com.homerentmanager.data.model.TenancyWithDetails
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class TenancyRepository(private val provider: DatabaseProvider) {
    private val dao: TenancyDao get() = provider.database.tenancyDao()

    fun observeAll(): Flow<List<TenancyWithDetails>> = dao.observeAllWithDetails()

    fun observeActive(): Flow<List<TenancyWithDetails>> = dao.observeActiveWithDetails()

    fun observeForRoom(roomId: UUID): Flow<List<TenancyWithDetails>> = dao.observeForRoom(roomId)

    fun observeActiveCount(): Flow<Int> = dao.observeActiveCount()

    suspend fun getAll(): List<com.homerentmanager.data.entity.TenancyEntity> = dao.getAll()
}
