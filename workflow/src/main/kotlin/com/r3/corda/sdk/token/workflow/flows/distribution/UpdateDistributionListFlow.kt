package com.r3.corda.sdk.token.workflow.flows.distribution

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.commands.IssueTokenCommand
import com.r3.corda.sdk.token.contracts.commands.MoveTokenCommand
import com.r3.corda.sdk.token.contracts.states.AbstractToken
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.workflow.utilities.addToDistributionList
import com.r3.corda.sdk.token.workflow.utilities.updateDistributionList
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker

// Observer aware finality flow that also updates the distribution lists accordingly.
class UpdateDistributionListFlow(
        val signedTransaction: SignedTransaction,
        val existingSessions: Set<FlowSession>
) : FlowLogic<Unit>() {

    companion object {
        object ADD_DIST_LIST : ProgressTracker.Step("Adding to distribution list.")
        object UPDATE_DIST_LIST : ProgressTracker.Step("Updating distribution list.")
        object RECORDING : ProgressTracker.Step("Recording tokensToIssue transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(RECORDING, ADD_DIST_LIST, UPDATE_DIST_LIST)
    }

    override val progressTracker: ProgressTracker = tracker()

    @Suspendable
    override fun call() {
        // TODO: Only need to do this for evolvable tokens!!!!
        val tx = signedTransaction.tx
        val issueCmds = tx.commands.filterIsInstance<IssueTokenCommand<TokenType>>()
        val moveCmds = tx.commands.filterIsInstance<MoveTokenCommand<TokenType>>()
        val tokens: List<AbstractToken<TokenType>> = tx.outputs.map { it.data }.filterIsInstance<AbstractToken<TokenType>>()
        progressTracker.currentStep = RECORDING
        // Determine which tokenHolders are participants and observers.
        // Update the distribution list. This adds all proposed token holders to the distribution list for the token
        // type they are receiving. Observers are not currently added to the distribution list.
        if (issueCmds.isNotEmpty()) {
            val issueTypes = issueCmds.map { it.token.tokenType }
            progressTracker.currentStep = ADD_DIST_LIST
            val issueStates: List<AbstractToken<TokenType>> = tokens.filter { it.tokenType in issueTypes }
            addToDistributionList(issueStates)
        }
        if (moveCmds.isNotEmpty()) {
            val moveTypes = moveCmds.map { it.token.tokenType }
            progressTracker.currentStep = UPDATE_DIST_LIST
            val moveStates: List<AbstractToken<TokenType>> = tokens.filter { it.tokenType in moveTypes }
            updateDistributionList(moveStates)
        }
    }
}