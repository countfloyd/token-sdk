package com.r3.corda.sdk.token.workflow.flows.finality

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.workflow.utilities.ourSigningKeys
import com.r3.corda.sdk.token.workflow.utilities.participants
import com.r3.corda.sdk.token.workflow.utilities.requireSessionsForParticipants
import com.r3.corda.sdk.token.workflow.utilities.toWellKnownParties
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder


/**
 * Transaction observers - those which are not participants in any of the output states - must invoke
// FinalityFlow with StatesToRecord set to ALL_VISIBLE, otherwise they will not store any of the states. This is
// handled automatically inside the ObserverAwareFinalityFlow.
//
// This
// does mean that there is an "all or nothing" approach to storing outputs, so if there are privacy concerns,
// then it is best to split state issuance up for different tokenHolders in separate flow invocations.
 */
class ObserverAwareFinalityFlow(
        val transactionBuilder: TransactionBuilder,
        val sessions: Set<FlowSession>
) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        // Check there is a session for each participant, apart from the node itself.
        val ledgerTransaction = transactionBuilder.toLedgerTransaction(serviceHub)
        val participants = ledgerTransaction.participants
        val wellKnownParticipants = participants.toWellKnownParties(serviceHub).toSet() - ourIdentity
        requireSessionsForParticipants(wellKnownParticipants, sessions)
        val finalSessions = sessions.filter { it.counterparty != ourIdentity }
        // Notify all session counterparties of their role.
        finalSessions.forEach { session ->
            if (session.counterparty in wellKnownParticipants) session.send(TransactionRole.PARTICIPANT)
            else session.send(TransactionRole.OBSERVER)
        }
        // Sign and finalise the transaction, obtaining the signing keys required from the LedgerTransaction.
        val ourSigningKeys = ledgerTransaction.ourSigningKeys(serviceHub)
        val signedTransaction = serviceHub.signInitialTransaction(transactionBuilder, signingPubKeys = ourSigningKeys)

        return subFlow(FinalityFlow(transaction = signedTransaction, sessions = finalSessions))
    }
}