package net.corda.training.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.*
import net.corda.core.identity.Party
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.node.services.linearHeadsOfType
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.flows.CollectSignaturesFlow
import net.corda.flows.FinalityFlow
import net.corda.flows.SignTransactionFlow
import net.corda.training.contract.IOUContract
import net.corda.training.state.IOUState
import java.security.PublicKey

/**
 * This is the flow which handles transfers of existing IOUs on the ledger.
 * Gathering the counterparty's signature is handled by the [CollectSignaturesFlow].
 * Notarisation (if required) and commitment to the ledger is handled vy the [FinalityFlow].
 * The flow returns the [SignedTransaction] that was committed to the ledger.
 */
@InitiatingFlow
@StartableByRPC
class IOUTransferFlow(val linearId: UniqueIdentifier, val newLender: Party): FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        //1.setup
        val me = serviceHub.myInfo.legalIdentity
        //2. get the IOU we want to transfer
        val stateAndRefs = serviceHub.vaultService.linearHeadsOfType<IOUState>()
        val stateAndRef:  StateAndRef<IOUState> = stateAndRefs[linearId] ?: throw IllegalArgumentException("could not find the IOU")
        val inputIOU = stateAndRef.state.data
        val outputIOU =  inputIOU.copy(lender = newLender)
        val  signers = listOf(inputIOU.borrower.owningKey, inputIOU.lender.owningKey, outputIOU.lender.owningKey)
        val transferCommand = Command(IOUContract.Commands.Transfer(), signers)
        val notary = stateAndRef.state.notary
        val txBuilder = TransactionBuilder(notary = notary)
        txBuilder.addInputState(stateAndRef)
        txBuilder.addOutputState(outputIOU)
        txBuilder.addCommand(transferCommand)
        val signedTx = serviceHub.signInitialTransaction(txBuilder)
        val fullySignedTx = subFlow(CollectSignaturesFlow(signedTx))
        val finalTx = subFlow(FinalityFlow(fullySignedTx)).single()
        return finalTx
    }
}

/**
 * This is the flow which signs IOU transfers.
 * The signing is handled by the [CollectSignaturesFlow].
 */
@InitiatedBy(IOUTransferFlow::class)
class IOUTransferFlowResponder(val otherParty: Party): FlowLogic<Unit>() {

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