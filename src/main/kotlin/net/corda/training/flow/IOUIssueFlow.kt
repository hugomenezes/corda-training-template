package net.corda.training.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Command
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.flows.SignTransactionFlow
import net.corda.training.contract.IOUContract
import net.corda.training.contract.IOUContract.Commands.Issue
import net.corda.training.state.IOUState
import java.util.*


@InitiatingFlow
@StartableByRPC
class IOUIssueFlow(val iouState: IOUState, val otherParty: Party): FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        //1. Setup
        val me: Party = serviceHub.myInfo.legalIdentity
        val notary = serviceHub.networkMapCache.getAnyNotary()
        //2. Build IOUState object
        val requiredSigners = listOf(me.owningKey, otherParty.owningKey)
        //3. command
        val command = Command(Issue(), requiredSigners)
        //4. create tx
        val txBuilder = TransactionBuilder(notary = notary)
        txBuilder.addOutputState(iouState)
        txBuilder.addCommand(command)
        //5. sign the transaction using our private key
        val signedTx: SignedTransaction = serviceHub.signInitialTransaction(txBuilder)

        return signedTx
    }

}

@InitiatedBy(IOUIssueFlow::class)
class IOUIssueFlowResponder(val otherParty: Party): FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val signTransactionFlow = object : SignTransactionFlow(otherParty) {
            override fun checkTransaction(stx: SignedTransaction) {
                // Define checking logic.
            }
        }

        subFlow(signTransactionFlow)
    }
}