package net.corda.training.state

import net.corda.core.contracts.Amount
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.utilities.ALICE
import net.corda.core.utilities.BOB
import net.corda.training.contract.IOUContract
import java.security.PublicKey
import java.util.*

/**
 * This is where you'll add the definition of your state object. Look at the unit tests in [IOUStateTests] for
 * instructions on how to complete the [IOUState] class.
 *
 * Remove the "val data: String = "data" property before starting the [IOUState] tasks.
 */
data class IOUState(
        val amount : Amount<Currency>,
        val lender : Party,
        val borrower : Party,
        val paid: Amount<Currency> = Amount(0, amount.token),
        override val linearId: UniqueIdentifier = UniqueIdentifier()
): LinearState {

    override fun isRelevant(ourKeys: Set<PublicKey>): Boolean {
        val stateKeys: List<PublicKey> = participants.map {party -> party.owningKey}
        return ourKeys.intersect(stateKeys).isNotEmpty()
    }

    override val participants: List<AbstractParty> get() = listOf(lender, borrower)

    override fun toString(): String {
        return "IOU($linearId): $borrower owes $lender $amount and has paid $paid so far."
    }

    /**
     * A Contract code reference to the IOUContract. Make sure this is not part of the [IOUState] constructor.
     * **Don't change this definition!**
     */
    override val contract get() = IOUContract()

    fun pay(amount: Amount<Currency>): IOUState = copy(paid = paid + amount)
}