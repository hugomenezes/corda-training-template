package net.corda.training.contract

import net.corda.contracts.asset.Cash
import net.corda.contracts.asset.sumCash
import net.corda.core.contracts.*
import net.corda.core.contracts.Requirements.using
import net.corda.core.crypto.SecureHash
import net.corda.training.state.IOUState

/**
 * This is where you'll add the contract code which defines how the [IOUState] behaves. Looks at the unit tests in
 * [IOUContractTests] for instructions on how to complete the [IOUContract] class.
 */
class IOUContract : Contract {
    override val legalContractReference: SecureHash = SecureHash.sha256("Prose contract.")

    /**
     * Add any commands required for this contract as classes within this interface.
     * It is useful to encapsulate your commands inside an interface, so you can use the [requireSingleCommand]
     * function to check for a range of commands which implement this interface.
     */
    interface Commands : CommandData {
        class Issue : TypeOnlyCommandData(), Commands
        class Transfer : TypeOnlyCommandData(), Commands
        class Settle : TypeOnlyCommandData(), Commands
    }

    /**
     * The contract code for the [IOUContract].
     * The constraints are self documenting so don't require any additional explanation.
     */
    override fun verify(tx: TransactionForContract) {
        val command = tx.commands.requireSingleCommand<IOUContract.Commands>()

        when (command.value) {
            is Commands.Issue -> requireThat {
                "No inputs should be consumed when issuing an IOU." using (tx.inputs.isEmpty())
                "Only one output state should be created when issuing an IOU." using (tx.outputs.size == 1)
                val iouFromOutput = tx.outputs.single() as IOUState
                "A newly issued IOU must have a positive amount." using (iouFromOutput.amount.quantity > 0)
                val commandSigners = command.signers.toSet()
                val iouSigners = iouFromOutput.participants.map {it.owningKey}.toSet()
                "The lender and borrower cannot be the same identity." using ( iouFromOutput.lender != iouFromOutput.borrower)
                "Both lender and borrower together only may sign IOU issue transaction." using ( commandSigners == iouSigners)

            }
            is Commands.Transfer -> requireThat {
                "An IOU transfer transaction should only consume one input state." using (tx.inputs.size == 1)
                "An IOU transfer transaction should only create one output state." using (tx.outputs.size == 1)
                val input = tx.inputs.single() as IOUState
                val output = tx.outputs.single() as IOUState
                "Only the lender property may change." using (input == output.withNewLender(input.lender))
                "The lender property must change in a transfer." using (input.lender != output.lender)
                "The borrower, old lender and new lender only must sign an IOU transfer transaction" using
                        (command.signers.toSet() == (input.participants.map { it.owningKey }.toSet() `union`
                                output.participants.map { it.owningKey }.toSet()))
            }
        }
    }
}
