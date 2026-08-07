package dev.ashwake.data.repository.tasks

import dev.ashwake.core.time.AppClock
import dev.ashwake.data.db.dao.tasks.ProjectDao
import dev.ashwake.data.db.dao.tasks.TagDao
import dev.ashwake.data.db.entity.tasks.TagEntity
import dev.ashwake.data.db.mapper.tasks.toDomain
import dev.ashwake.data.db.mapper.tasks.toEntity
import dev.ashwake.domain.model.tasks.Project
import dev.ashwake.domain.model.tasks.Tag
import dev.ashwake.domain.repository.tasks.ProjectRepository
import dev.ashwake.domain.repository.tasks.TagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue

@Singleton
class ProjectRepositoryImpl @Inject constructor(
    private val dao: ProjectDao,
    private val clock: AppClock
) : ProjectRepository {

    override fun observeProjects(includeArchived: Boolean): Flow<List<Project>> =
        dao.observeProjects(if (includeArchived) 1 else 0)
            .map { list -> list.map { it.toDomain() } }

    override suspend fun upsert(project: Project): Long {
        val prepared =
            if (project.createdAt.toEpochMilli() == 0L) project.copy(createdAt = clock.now())
            else project
        return dao.upsert(prepared.toEntity())
    }

    override suspend fun archive(id: Long) = dao.archive(id)

    override suspend fun delete(id: Long) = dao.deleteById(id)
}

@Singleton
class TagRepositoryImpl @Inject constructor(
    private val dao: TagDao
) : TagRepository {

    override fun observeTags(): Flow<List<Tag>> =
        dao.observeTags().map { list -> list.map { it.toDomain() } }

    /**
     * Быстрый ввод пишет `#дом` — тега может ещё не быть.
     * Цвет выбирается детерминированно по имени, чтобы один и тот же тег
     * не менял цвет между устройствами и после импорта.
     */
    override suspend fun ensureTag(name: String): Long {
        val clean = name.trim().removePrefix("#")
        require(clean.isNotEmpty()) { "Имя тега не может быть пустым" }
        dao.findByName(clean)?.let { return it.id }
        val color = TAG_PALETTE[clean.hashCode().absoluteValue % TAG_PALETTE.size]
        val inserted = dao.insert(TagEntity(name = clean, color = color))
        // OnConflict.IGNORE вернёт -1, если тег успели создать параллельно
        return if (inserted != -1L) inserted else dao.findByName(clean)!!.id
    }

    override suspend fun delete(id: Long) = dao.deleteById(id)

    private companion object {
        /** Приглушённые цвета из общей палитры проекта, без чистых тонов. */
        val TAG_PALETTE = intArrayOf(
            0xFF6E7BA6.toInt(), 0xFF7F6A9E.toInt(), 0xFF5E8C7A.toInt(),
            0xFFA8845C.toInt(), 0xFF9E6A6A.toInt(), 0xFF6A8C9E.toInt(),
            0xFF8C7F6A.toInt(), 0xFF7A9E6A.toInt()
        )
    }
}
