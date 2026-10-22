package com.example.desayuno

import com.example.desayuno.model.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class SalesReportTest {
    private fun time(value: String)=Instant.parse(value).toEpochMilli()
    private val date=LocalDate.parse("2026-10-22")
    private fun order(id: String,paid: String?,amount: Double,status: OrderStatus=OrderStatus.READY,method: String="CASH") = Order(
        id=id,orderNumber=id.length,customerName="Cliente",customerPhone="999888777",items=listOf(OrderItem("product","Pan",amount,1)),
        totalAmount=amount,status=status,timestamp=time("2026-10-22T14:00:00Z"),updatedAt=time("2026-10-22T15:00:00Z"),
        paymentMethod=if(paid!=null) method else null,paidAt=paid?.let(::time),paidAmount=if(paid!=null) amount else null)

    @Test fun paymentDateUsesLimaAndDoesNotCountUnpaidOrdersAsRevenue() {
        val previous=order("previous","2026-10-22T04:59:59Z",7.0,OrderStatus.DELIVERED)
        val today=order("today","2026-10-22T05:00:00Z",3.5)
        val unpaid=order("unpaid",null,4.0)
        val cancelled=order("cancelled",null,8.0,OrderStatus.CANCELLED)
        val report=buildSalesReport(listOf(previous,today,unpaid,cancelled),date)
        assertEquals(BigDecimal("3.5"),report.revenue)
        assertEquals(1,report.payments.size)
        assertEquals(1,report.completedOrders)
        assertEquals(1,report.cancelledOrders)
        assertEquals(1,report.unpaidOrders)
        assertEquals(BigDecimal("4.0"),report.unpaidAmount)
    }
    @Test fun revenueUsesRecordedPaymentAndExactDecimalSums() {
        val a=order("a","2026-10-22T12:00:00Z",0.1)
        val b=order("b","2026-10-22T13:00:00Z",0.2,method="YAPE").copy(totalAmount=999.0)
        val report=buildSalesReport(listOf(a,b),date)
        assertEquals(0,report.revenue.compareTo(BigDecimal("0.30")))
        assertEquals(0,report.byMethod.getValue("YAPE").compareTo(BigDecimal("0.2")))
        assertEquals(2,report.paidUnits)
    }
    @Test fun productsWithSameNameKeepDistinctStableIdentifiers() {
        val a=order("a","2026-10-22T12:00:00Z",2.0)
        val b=order("b","2026-10-22T13:00:00Z",3.0).copy(items=listOf(OrderItem("another","Pan",3.0,1)))
        val report=buildSalesReport(listOf(a,b),date)
        assertEquals(2,report.products.map { it.id }.distinct().size)
    }
    @Test fun exportedCsvQuotesNamesAndBlocksSpreadsheetFormulas() {
        val a=order("a","2026-10-22T12:00:00Z",2.0).copy(customerName="=HYPERLINK(\"test\")")
        val csv=buildSalesReport(listOf(a),date).toCsv()
        assertTrue(csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("'=HYPERLINK("))
        assertTrue(csv.contains("\"\"test\"\""))
        assertTrue(csv.contains("2026-10-22"))
    }
}
