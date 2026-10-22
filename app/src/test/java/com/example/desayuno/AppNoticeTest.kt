package com.example.desayuno

import com.example.desayuno.model.*
import com.example.desayuno.notification.*
import org.junit.Assert.*
import org.junit.Test

class AppNoticeTest {
    private val client=User("client-a","Ana","999888777",UserRole.CLIENT,"ana@test.invalid")
    private val cook=client.copy(id="cook",role=UserRole.COOK)
    private fun order(id: String="order-a",owner: String=client.id,status: OrderStatus=OrderStatus.PENDING,time: Long=1000L)=
        Order(id,123,"Ana","999888777",emptyList(),15.0,status,customerId=owner,timestamp=time,updatedAt=time)

    @Test fun clientsCannotSeeAnotherAccountsOrdersEvenWithSameName() {
        val notices=buildNotices(client,listOf(order(),order("other","client-b")),emptyList())
        assertEquals(listOf("order-a"),notices.map { it.orderId })
    }
    @Test fun readyOnInitialLoadIsRelevantButRepeatedSnapshotIsQuiet() {
        val ready=order(status=OrderStatus.READY)
        assertEquals(1,orderAlerts(client,emptyList(),listOf(ready),false).size)
        assertTrue(orderAlerts(client,listOf(ready),listOf(ready),true).isEmpty())
        assertTrue(orderAlerts(client,emptyList(),listOf(ready.copy(customerId="other")),false).isEmpty())
    }
    @Test fun staffInitialHistoryIsQuietButNewPendingOrdersAlert() {
        assertTrue(orderAlerts(cook,emptyList(),listOf(order()),false).isEmpty())
        assertEquals(1,orderAlerts(cook,emptyList(),listOf(order()),true).size)
        assertTrue(orderAlerts(cook,listOf(order()),listOf(order()),true).isEmpty())
    }
    @Test fun statusChangesHaveDistinctReadIdentifiers() {
        val preparing=orderNotice(client,order(status=OrderStatus.PREPARING))
        val ready=orderNotice(client,order(status=OrderStatus.READY))
        assertNotEquals(preparing.id,ready.id)
        assertEquals("order-a",ready.orderId)
        assertTrue(ready.ready)
    }
    @Test fun announcementsHaveNoOrderDestinationAndFeedIsLimitedAndSorted() {
        val rows=(1..110).map { order("order-$it",time=it.toLong()) }
        val feed=buildNotices(client,rows,listOf(Announcement("news","Aviso","Nuevo horario",1000)))
        assertEquals(100,feed.size)
        assertEquals("Aviso",feed.first().title)
        assertNull(feed.first().orderId)
        assertEquals(110L,feed[1].timestamp)
    }
    @Test fun deliveredAndCancelledChangesAlertOnlyWhenObserved() {
        val pending=order()
        for(status in listOf(OrderStatus.DELIVERED,OrderStatus.CANCELLED,OrderStatus.PREPARING)) {
            assertEquals(1,orderAlerts(client,listOf(pending),listOf(order(status=status)),true).size)
            assertTrue(orderAlerts(client,emptyList(),listOf(order(status=status)),false).isEmpty())
        }
    }
}
