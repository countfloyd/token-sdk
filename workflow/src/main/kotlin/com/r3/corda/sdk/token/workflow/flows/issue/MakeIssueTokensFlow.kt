package com.r3.corda.sdk.token.workflow.flows.issue

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.states.AbstractToken
import com.r3.corda.sdk.token.contracts.states.NonFungibleToken
import com.r3.corda.sdk.token.contracts.types.IssuedTokenType
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.contracts.utilities.heldBy
import com.r3.corda.sdk.token.contracts.utilities.issuedBy
import com.r3.corda.sdk.token.contracts.utilities.of
import com.r3.corda.sdk.token.workflow.utilities.sessionsForParticipantsAndObservers
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction

/**
 * A flow for issuing fungible or non-fungible tokens which initiates its own sessions. This is the case when called
 * from the node shell or in a unit test. However, in the case where you already have a session with another [Party] and
 * you wish to issue tokens as part of a wider workflow, then use [IssueTokensFlow].
 *
 * @property tokensToIssue a list of [AbstractToken]s to issue
 * @property observers aset of observing [Party]s
 */
@StartableByService
@StartableByRPC
@InitiatingFlow
class MakeIssueTokensFlow<T : TokenType>(
        val tokensToIssue: List<AbstractToken<T>>,
        val observers: Set<Party>
) : FlowLogic<SignedTransaction>() {

    /* Fungible token constructors. */

    constructor(tokenType: T, amount: Long, issuer: Party, holder: Party, observers: Set<Party>)
            : this(listOf(amount of tokenType issuedBy issuer heldBy holder), observers)

    constructor(issuedTokenType: IssuedTokenType<T>, amount: Long, holder: AbstractParty, observers: Set<Party>)
            : this(listOf(amount of issuedTokenType heldBy holder), observers)

    constructor(issuedTokenType: IssuedTokenType<T>, amount: Long, observers: Set<Party>)
            : this(listOf(amount of issuedTokenType heldBy issuedTokenType.issuer), observers)

    constructor(tokenType: T, amount: Long, issuer: Party, observers: Set<Party>)
            : this(listOf(amount of tokenType issuedBy issuer heldBy issuer), observers)

    /* Non-fungible token constructors. */

    constructor(token: NonFungibleToken<T>, observers: Set<Party> = emptySet())
            : this(listOf(token), observers)

    constructor(tokenType: T, issuer: Party, holder: AbstractParty, observers: Set<Party> = emptySet())
            : this(listOf(tokenType issuedBy issuer heldBy holder), observers)

    constructor(issuedTokenType: IssuedTokenType<T>, holder: AbstractParty, observers: Set<Party> = emptySet())
            : this(listOf(issuedTokenType heldBy holder), observers.toSet())

    constructor(issuedTokenType: IssuedTokenType<T>, observers: Set<Party> = emptySet())
            : this(listOf(issuedTokenType heldBy issuedTokenType.issuer), observers)

    constructor(tokenType: T, issuer: Party, observers: Set<Party> = emptySet())
            : this(listOf(tokenType issuedBy issuer heldBy issuer), observers)

    @Suspendable
    override fun call(): SignedTransaction {
        val observerAndHolderSessions = sessionsForParticipantsAndObservers(tokensToIssue, observers)
        return subFlow(IssueTokensFlow(tokensToIssue, observerAndHolderSessions))
    }
}

// TODO: Move to new file.
@InitiatedBy(IssueTokensFlow::class)
class MakeIssueTokensFlowHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() = subFlow(IssueTokensFlowHandler(otherSession))
}