package com.doduohor.repository.postgres

import com.doduohor.domain.model.Booking
import com.doduohor.domain.model.BookingStatus
import com.doduohor.domain.model.BookingTimeInterval
import com.doduohor.infrastructure.database.BookingTable
import com.doduohor.repository.BookingRepository
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Instant
import java.time.temporal.ChronoUnit

class PostgresBookingRepository(private val database: Database): BookingRepository{
    override fun create(
        facilityId: Long,
        customerId: Int,
        timeInterval: BookingTimeInterval
    ): Booking = transaction (database) {

        val createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS)
        val insertedRow = BookingTable.insert {
            it[BookingTable.facilityId] = facilityId
            it[BookingTable.customerId] = customerId
            it[BookingTable.startTime] = timeInterval.startTime
            it[BookingTable.endTime] = timeInterval.endTime
            it[BookingTable.status] = Booking.DEFAULT_STATUS.name
            it[BookingTable.createdAt] = createdAt
        }

        val generatedId = insertedRow[BookingTable.id]

        Booking.createNew(
            id = generatedId,
            facilityId = facilityId,
            customerId = customerId,
            timeInterval = timeInterval,
            createdAt = createdAt
        )
    }

    override fun findByBookingId(id: Long): Booking? = transaction (database) {
        val foundRow = BookingTable.selectAll()
            .where { BookingTable.id eq id}
            .singleOrNull()
            ?: return@transaction null
        toBooking(foundRow)
    }

    override fun findByFacilityId(id: Long): List<Booking> = transaction(database) {
        BookingTable.selectAll()
            .where { BookingTable.facilityId eq id }
            .map{ row -> toBooking(row)}
    }

    override fun findAll(): List<Booking> = transaction(database){
        BookingTable.selectAll().map{ row -> toBooking(row) }
    }

    override fun findOverlappingByFacilityId(
        facilityId: Long,
        timeInterval: BookingTimeInterval
    ): Boolean = transaction (database){
        BookingTable.selectAll()
            .where { (BookingTable.facilityId eq facilityId) and
                    (BookingTable.startTime less timeInterval.endTime) and
                    (BookingTable.endTime greater timeInterval.startTime)
            }.any()
    }

    private fun toBooking(row: ResultRow): Booking {
        return Booking(
            id = row[BookingTable.id],
            facilityId = row[BookingTable.facilityId],
            customerId = row[BookingTable.customerId],
            timeInterval = BookingTimeInterval(
                startTime = row[BookingTable.startTime],
                endTime = row[BookingTable.endTime]
            ),
            status = BookingStatus.valueOf(row[BookingTable.status]),
            createdAt = row[BookingTable.createdAt]
        )
    }

}
