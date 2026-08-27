package com.doduohor.repository.postgres

import com.doduohor.domain.model.Booking
import com.doduohor.domain.model.BookingCreationResult
import com.doduohor.domain.model.BookingStatus
import com.doduohor.domain.model.BookingTimeInterval
import com.doduohor.infrastructure.database.postgres.BookingTable
import com.doduohor.repository.BookingRepository
import com.doduohor.domain.shared.BookingId
import com.doduohor.domain.shared.Clock
import com.doduohor.domain.shared.CustomerId
import com.doduohor.domain.shared.FacilityId
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.sql.SQLException

class PostgresBookingRepository(
    private val database: Database,
    private val clock: Clock
    ): BookingRepository{
    override fun createIfAvailable(
        facilityId: FacilityId,
        customerId: CustomerId,
        timeInterval: BookingTimeInterval
    ): BookingCreationResult<Booking> = try {
        transaction(database) {

            val createdAt = clock.now()
            val insertedRow = BookingTable.insert {
                it[BookingTable.facilityId] = facilityId.value
                it[BookingTable.customerId] = customerId.value
                it[BookingTable.startTime] = timeInterval.startTime
                it[BookingTable.endTime] = timeInterval.endTime
                it[BookingTable.status] = Booking.DEFAULT_STATUS.name
                it[BookingTable.createdAt] = createdAt
            }

            BookingCreationResult.Success(
                Booking.createNew(
                    id = BookingId(insertedRow[BookingTable.id]),
                    facilityId = facilityId,
                    customerId = customerId,
                    timeInterval = timeInterval,
                    createdAt = createdAt
                )
            )
        }

    }catch (exception: ExposedSQLException) {
        val sqlException = exception.cause as? SQLException

        if (sqlException?.sqlState == "23P01") {
            BookingCreationResult.UnavailableRange
        } else {
            throw exception
        }
    }

    override fun findByBookingId(id: BookingId): Booking? = transaction (database) {
        val foundRow = BookingTable.selectAll()
            .where { BookingTable.id eq id.value}
            .singleOrNull()
            ?: return@transaction null
        toBooking(foundRow)
    }

    override fun findByFacilityId(id: FacilityId): List<Booking> = transaction(database) {
        BookingTable.selectAll()
            .where { BookingTable.facilityId eq id.value }
            .map{ row -> toBooking(row)}
    }

    override fun findAll(): List<Booking> = transaction(database){
        BookingTable.selectAll().map{ row -> toBooking(row) }
    }


    private fun toBooking(row: ResultRow): Booking {
        return Booking(
            id = BookingId(row[BookingTable.id]),
            facilityId = FacilityId(row[BookingTable.facilityId]),
            customerId = CustomerId(row[BookingTable.customerId]),
            timeInterval = BookingTimeInterval(
                startTime = row[BookingTable.startTime],
                endTime = row[BookingTable.endTime]
            ),
            status = BookingStatus.valueOf(row[BookingTable.status]),
            createdAt = row[BookingTable.createdAt]
        )
    }
}
