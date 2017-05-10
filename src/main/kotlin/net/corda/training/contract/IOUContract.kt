package net.corda.training.contract

import net.corda.contracts.asset.Cash
import net.corda.contracts.asset.sumCash
import net.corda.core.contracts.*
import net.corda.core.contracts.Requirements.by
import net.corda.core.crypto.SecureHash
import net.corda.training.state.IOUState

/**
 * This is where you'll add the contract code which defines how the [IOUState] behaves. Looks at the unit tests in
 * [IOUContractTests] for instructions on how to complete the [IOUContract] class.
 */
class IOUContract : Contract {
    /**
     * Legal prose reference. This is just a dummy string for the time being.
     */
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
        val command = tx.commands.requireSingleCommand<Commands>()

        when (command.value) {
            is Commands.Issue -> requireThat {
                "No inputs should be consumed when issuing an IOU." by (tx.inputs.isEmpty())
                "Only one output state should be created when issuing an IOU." by (tx.outputs.size == 1)
                val output = tx.outputs.single() as IOUState
                "A newly issued IOU must have a positive amount." by (output.amount.quantity > 0)
                "The lender and borrower cannot be the same identity." by (output.borrower != output.lender)
                "Both lender and borrower together only may sign IOU issue transaction." by
                        (output.participants.toSet() == command.signers.toSet())
            }
            is Commands.Transfer -> requireThat {
                "An IOU transfer transaction should only consume one input state." by (tx.inputs.size == 1)
                "An IOU transfer transaction should only create one output state." by (tx.outputs.size == 1)
                val input = tx.inputs.single() as IOUState
                val output = tx.outputs.single() as IOUState
                "Only the lender property may change." by (input == output.withNewLender(input.lender))
                "The lender property must change in a transfer." by (input.lender != output.lender)
                "The borrower, old lender and new lender only must sign an IOU transfer transaction" by
                        (input.participants.toSet() `union` output.participants.toSet() == command.signers.toSet())
            }
            is Commands.Settle -> {
                val ious = tx.groupStates<IOUState, UniqueIdentifier> { it.linearId }.single()
                require(ious.inputs.size == 1) { "There must be one input IOU." }

                val cash = tx.outputs.filterIsInstance<Cash.State>()
                require(cash.isNotEmpty()) { "There must be output cash." }

                val iou = ious.inputs.single()
                val acceptableCash = cash.filter { state: Cash.State -> state.owner == iou.lender.owningKey }

                require(acceptableCash.isNotEmpty()) { "There must be output cash paid to the recipient." }

                val leftToPay = iou.amount - iou.paid
                val sumCash = acceptableCash.sumCash().withoutIssuer()

                require(sumCash <= leftToPay) { "The amount settled cannot be more than the amount outstanding." }

                if (sumCash == leftToPay) {
                    require(ious.outputs.isEmpty()) { "There must be no output IOU as it has been fully settled." }
                } else {
                    require(ious.outputs.size == 1) { "There must be one output IOU." }
                    val output = ious.outputs.single()
                    requireThat {
                        "The amount may not change when settling." by (iou.amount == output.amount)
                        "The borrower may not change when settling." by (iou.borrower == output.borrower)
                        "The lender may not change when settling." by (iou.lender == output.lender)
//                        "The linearId may not change when settling." by (iou.linearId == output.linearId)
//                        "The contract may not change when settling." by (iou.contract == output.contract)
                    }
                    // Checking that the output iou paid amount is being increased by the correct value.
                    require(output.paid == iou.paid + sumCash) { "The paid property was not updated to the correct value." }
                }

                "Both lender and borrower together only must sign IOU settle transaction." by
                        (command.signers.toSet() == iou.participants.toSet())
            }
            else -> throw IllegalArgumentException("Unrecognised command.")
        }
    }
}
