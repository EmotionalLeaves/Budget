package com.emotionalleaves.sprout.domain.accounts

import org.junit.Assert.assertEquals
import org.junit.Test

class AccountTypesTest {
    @Test fun checking() = assertEquals(AccountType.CHECKING, AccountTypes.infer("Everyday Checking", 150000))
    @Test fun savings() = assertEquals(AccountType.SAVINGS, AccountTypes.infer("Share Savings", 1000))
    @Test fun creditByName() = assertEquals(AccountType.CREDIT, AccountTypes.infer("Rewards Visa", 0))
    @Test fun creditByNegativeBalance() = assertEquals(AccountType.CREDIT, AccountTypes.infer("Account 1234", -31288))
    @Test fun loan() = assertEquals(AccountType.LOAN, AccountTypes.infer("Auto Loan", -1200000))
    @Test fun investment() = assertEquals(AccountType.INVESTMENT, AccountTypes.infer("Roth IRA", 500000))
    @Test fun unknownPositiveIsChecking() = assertEquals(AccountType.CHECKING, AccountTypes.infer("Primary", 100))
    @Test fun customCurrencyLabel() = assertEquals("?", AccountTypes.currencyLabel("https://points.example.test/c"))
    @Test fun usdLabel() = assertEquals("USD", AccountTypes.currencyLabel("usd"))
}
