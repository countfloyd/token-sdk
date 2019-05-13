package com.r3.corda.sdk.token.workflow.flows.issue

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.commands.IssueTokenCommand
import com.r3.corda.sdk.token.contracts.states.AbstractToken
import com.r3.corda.sdk.token.contracts.states.FungibleToken
import com.r3.corda.sdk.token.contracts.types.IssuedTokenType
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.contracts.utilities.heldBy
import com.r3.corda.sdk.token.contracts.utilities.issuedBy
import com.r3.corda.sdk.token.contracts.utilities.of
import com.r3.corda.sdk.token.workflow.flows.distribution.UpdateDistributionListFlow
import com.r3.corda.sdk.token.workflow.flows.finality.ObserverAwareFinalityFlow
import com.r3.corda.sdk.token.workflow.utilities.getPreferredNotary
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

// This intention is that this flow can be started as a subflow from another flow. Or invoked directly.

/**
 * Creates a [TransactionBuilder] with the preferred notary, the requested set of tokensToIssue as outputs and adds
 * [IssueTokenCommand]s for each group of states (grouped by [IssuedTokenType]. This flow can be called as an
 * [InitiatingFlow] or an inlined sub-flow.
 *
 * - No need to pass in any sessions when issuing to self but can pass in observer sessions if needed.
 * - There is an assumption that this flow can only be used by one issuer at a time.
 * - Tokens are issued to well known identities or confidential identities. This flow TODO this flow what
 * - Many tokensToIssue can be issued to a single party.
 * - Many tokensToIssue can be issued to many tokenHolders but usually only one.
 * - Observers can also be specified.
 * - can issue fungible and non fungible tokensToIssue at the same time.
 * - tokensToIssue can be issued to self or to another party
 * - The notary is selected from a config file or picked at random if no notary preference is available.
 * - This is not an initiating flow. There will also be an initiating version which is startable from the shell.
 * - This flow handles observers. Observers (via additional flow sessions) store the tx with ALL_VISIBLE.
 * - Can issue different types of token at the same time.
 *
 *
 * Issue a list of [AbstractToken]s using a list of supplied sessions. NOTE: The list of sessions may or may not
 * include transaction observers. The flow will automatically determine which sessions belong to participants versus
 * which belong to observers.
 */
open class IssueTokensFlow<T : TokenType>(
        val tokensToIssue: List<AbstractToken<T>>,
        val sessions: Set<FlowSession>
) : FlowLogic<SignedTransaction>() {

    /* Non-fungible token constructors. */

    /** Issue a fully specified [FungibleToken]. */
    constructor(token: FungibleToken<T>) : this(listOf(token), emptySet())

    constructor(tokenType: T, issuer: Party, holder: AbstractParty, sessions: Set<FlowSession>)
            : this(listOf(tokenType issuedBy issuer heldBy holder), sessions)

    constructor(issuedTokenType: IssuedTokenType<T>, holder: AbstractParty, sessions: Set<FlowSession>)
            : this(listOf(issuedTokenType heldBy holder), sessions)

    constructor(issuedTokenType: IssuedTokenType<T>, sessions: Set<FlowSession>)
            : this(listOf(issuedTokenType heldBy issuedTokenType.issuer), sessions)

    constructor(tokenType: T, issuer: Party, sessions: Set<FlowSession>)
            : this(listOf(tokenType issuedBy issuer heldBy issuer), sessions)

    /* Fungible token constructors. */

    constructor(tokenType: T, amount: Long, issuer: Party, holder: AbstractParty, sessions: Set<FlowSession>)
            : this(listOf(amount of tokenType issuedBy issuer heldBy holder), sessions)

    constructor(issuedTokenType: IssuedTokenType<T>, amount: Long, holder: AbstractParty, sessions: Set<FlowSession>)
            : this(listOf(amount of issuedTokenType heldBy holder), sessions)

    constructor(issuedTokenType: IssuedTokenType<T>, amount: Long, sessions: Set<FlowSession>)
            : this(listOf(amount of issuedTokenType heldBy issuedTokenType.issuer), sessions)

    constructor(tokenType: T, amount: Long, issuer: Party, sessions: Set<FlowSession>)
            : this(listOf(amount of tokenType issuedBy issuer heldBy issuer), sessions)

    /* Standard constructors. */

    /**
     * Issue an [AbstractToken]s using a list of supplied sessions. NOTE: The list of sessions may or may not
     * include transaction observers. The flow will automatically determine which sessions belong to participants versus
     * which belong to observers.
     */
    constructor(tokens: AbstractToken<T>, session: FlowSession) : this(listOf(tokens), setOf(session))

    constructor(tokens: AbstractToken<T>, sessions: Set<FlowSession>) : this(listOf(tokens), sessions)

    @Suspendable
    override fun call(): SignedTransaction {
        // Initialise the transaction builder with a preferred notary or choose a random notary.
        val transactionBuilder = TransactionBuilder(notary = getPreferredNotary(serviceHub))
        // Add all the specified tokensToIssue to the transaction. The correct commands and signing keys are also added.
        addIssueTokens(tokensToIssue, transactionBuilder)
        // Create new sessions if this is started as a top level flow.
        val signedTransaction = subFlow(ObserverAwareFinalityFlow(transactionBuilder, sessions))
        // Update the distribution list.
        subFlow(UpdateDistributionListFlow(signedTransaction, sessions))
        // Return the newly created transaction.
        return signedTransaction
    }
}
