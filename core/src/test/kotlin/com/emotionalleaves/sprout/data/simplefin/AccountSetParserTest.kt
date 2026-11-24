package com.emotionalleaves.sprout.data.simplefin

import com.emotionalleaves.sprout.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountSetParserTest {

    private fun parse(name: String, mode: ProtocolMode = ProtocolMode.V2) =
        AccountSetParser.parse(Fixtures.simplefin(name), mode)

    @Test fun v2BasicParsesAccountsBalancesAndTransactions() {
        val set = parse("v2_basic.json")
        assertEquals(1, set.connections.size)
        assertEquals("Prairie Federal Credit Union", set.connections[0].name)
        assertEquals(2, set.accounts.size)
        val chk = set.accounts[0]
        assertEquals("CON-111", chk.connId)
        assertEquals(152345L, chk.balanceMinor)
        assertEquals(150000L, chk.availableBalanceMinor)
        assertEquals(4, chk.transactions.size)
        assertEquals(-5421L, chk.transactions[0].amountMinor)
        assertEquals(1763490000L, chk.transactions[0].transactedAt)
        assertFalse(chk.transactions[0].pending)
        assertNull(set.accounts[1].availableBalanceMinor)
        assertTrue(set.errors.isEmpty())
    }

    @Test fun postedZeroIsPending() {
        val tx = parse("v2_basic.json").accounts[0].transactions[3]
        assertTrue(tx.pending)
        assertEquals(0L, tx.postedAt)
    }

    @Test fun multiConnectionKeepsSameAccountIdsApartByConnection() {
        val set = parse("v2_multi_connection.json")
        assertEquals(listOf("CON-A", "CON-B"), set.accounts.map { it.connId })
        assertEquals(listOf("1", "1"), set.accounts.map { it.id })
    }

    @Test fun partialErrorsKeepsAllDataAndBothErrorArrays() {
        val set = parse("v2_partial_errors.json")
        assertEquals(2, set.accounts.size)
        assertEquals(2, set.accounts[0].transactions.size)
        assertEquals(listOf("act.missingdata", "gen."), set.errors.map { it.code })
        assertEquals("ACT-SAV", set.errors[0].accountId)
        // The legacy string duplicates an errlist msg, so it isn't shown twice.
        assertEquals(2, set.errors.size)
    }

    @Test fun legacyErrorsNotInErrlistAreKept() {
        val body = """{"errlist":[{"code":"gen.","msg":"A"}],"errors":["B"],"connections":[],"accounts":[]}"""
        val set = AccountSetParser.parse(body, ProtocolMode.V2)
        assertEquals(listOf("gen.", SfError.LEGACY), set.errors.map { it.code })
    }

    @Test fun conAuthErrorCarriesConnectionId() {
        val e = parse("v2_con_auth.json").errors.single()
        assertEquals("con.auth", e.code)
        assertEquals("con", e.prefix)
        assertEquals("CON-A", e.connId)
    }

    @Test fun v1DerivesConnectionsFromOrgDomainOrName() {
        val set = parse("v1_legacy_org_and_errors.json", ProtocolMode.V1)
        assertEquals(ProtocolMode.V1, set.mode)
        assertEquals(listOf("oldtownbank.example.test", "no domain credit union"), set.accounts.map { it.connId })
        assertEquals(2, set.connections.size)
        assertEquals("Old Town Bank", set.connections[0].name)
        assertEquals(SfError.LEGACY, set.errors.single().code)
        assertTrue(set.accounts[1].transactions.isEmpty())
    }

    @Test fun emptyAccounts() {
        val set = parse("empty_accounts.json")
        assertTrue(set.accounts.isEmpty())
        assertTrue(set.errors.isEmpty())
    }

    @Test fun customCurrencyIsKeptAsUrl() {
        assertEquals("https://points.example.test/currency", parse("custom_currency.json").accounts[0].currency)
    }

    @Test(expected = AccountSetParseException::class)
    fun malformedThrowsParseException() {
        parse("malformed.json")
    }

    @Test fun parseExceptionNeverContainsBody() {
        try {
            AccountSetParser.parse("{\"secret\": \"https://a:b@x\"", ProtocolMode.V2)
        } catch (e: AccountSetParseException) {
            assertFalse(e.message!!.contains("https://"))
            return
        }
        throw AssertionError("expected exception")
    }

    @Test fun badRowsAreSkippedNotFatal() {
        val body = """{"errlist":[],"connections":[],"accounts":[
            {"id":"a","name":"A","conn_id":"c","currency":"USD","balance":"1.00","balance-date":1,"transactions":[
              {"id":"t1","posted":5,"amount":"oops","description":"x"},
              {"id":"t2","posted":5,"amount":"-1.00","description":"y"}]},
            {"name":"no id","balance":"1"}]}"""
        val set = AccountSetParser.parse(body, ProtocolMode.V2)
        assertEquals(1, set.accounts.size)
        assertEquals(listOf("t2"), set.accounts[0].transactions.map { it.id })
        assertEquals(2, set.skippedRows)
    }

    @Test fun numericAmountsAndStringTimestampsAreTolerated() {
        val body = """{"errlist":[],"connections":[],"accounts":[
            {"id":"a","name":"A","conn_id":"c","currency":"USD","balance":12.5,"balance-date":"100","transactions":[
              {"id":"t","posted":"200","amount":-3.25,"description":"z"}]}]}"""
        val acct = AccountSetParser.parse(body, ProtocolMode.V2).accounts.single()
        assertEquals(1250L, acct.balanceMinor)
        assertEquals(100L, acct.balanceDate)
        assertEquals(-325L, acct.transactions.single().amountMinor)
        assertEquals(200L, acct.transactions.single().postedAt)
    }

    @Test fun messagesAreSanitizedAndTruncated() {
        val long = "x".repeat(1000)
        val body = """{"errlist":[{"code":"gen.","msg":"bad\u0007‮text $long"}],"connections":[],"accounts":[]}"""
        val msg = AccountSetParser.parse(body, ProtocolMode.V2).errors.single().msg
        assertTrue(msg.startsWith("badtext "))
        assertEquals(300, msg.length)
    }
}
