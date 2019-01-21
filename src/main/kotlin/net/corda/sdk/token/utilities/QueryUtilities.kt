package net.corda.sdk.token.utilities

import net.corda.core.contracts.Amount
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.Vault
import net.corda.core.node.services.VaultService
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.sdk.token.persistence.schemas.DistributionRecord
import net.corda.sdk.token.persistence.schemas.PersistentOwnedTokenAmount
import net.corda.sdk.token.states.OwnedTokenAmount
import net.corda.sdk.token.types.AbstractOwnedToken
import net.corda.sdk.token.types.EmbeddableToken
import java.util.*
import javax.persistence.criteria.CriteriaQuery

/** Miscellaneous helpers. */

// Grabs the latest version of a linear state for a specified linear ID.
inline fun <reified T : LinearState> getLinearStateById(linearId: UniqueIdentifier, services: ServiceHub): StateAndRef<T>? {
    val query = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId), status = Vault.StateStatus.UNCONSUMED)
    return services.vaultService.queryBy<T>(query).states.singleOrNull()
}

// Gets the distribution list for a particular token.
fun getDistributionList(services: ServiceHub, linearId: UniqueIdentifier): List<DistributionRecord> {
    return services.withEntityManager {
        val query: CriteriaQuery<DistributionRecord> = criteriaBuilder.createQuery(DistributionRecord::class.java)
        query.apply {
            val root = from(DistributionRecord::class.java)
            where(criteriaBuilder.equal(root.get<UUID>("linearId"), linearId.id))
            select(root)
        }
        createQuery(query).resultList
    }
}

/** Utilities for getting tokens from the vault and performing miscellaneous queries. */

// TODO: Add queries for getting the balance of all tokens, not just relevant ones.
// TODO: Allow discrimination by issuer or a set of issuers.

// Returns all owned token amounts of a specified token.
// We need to discriminate on the token type as well as the symbol as different tokens might use the same symbols.
private fun <T : EmbeddableToken> tokenCriteria(embeddableToken: T): QueryCriteria {
    val tokenClass = builder { PersistentOwnedTokenAmount::tokenClass.equal(AbstractOwnedToken.tokenClass(embeddableToken)) }
    val tokenClassCriteria = QueryCriteria.VaultCustomQueryCriteria(tokenClass)
    val tokenIdentifier = builder { PersistentOwnedTokenAmount::tokenIdentifier.equal(AbstractOwnedToken.tokenIdentifier(embeddableToken)) }
    val tokenIdentifierCriteria = QueryCriteria.VaultCustomQueryCriteria(tokenIdentifier)
    return tokenClassCriteria.and(tokenIdentifierCriteria)
}

// For summing tokens of a specified type.
// NOTE: Issuer is ignored with this query criteria.
// NOTE: It only returns relevant states.
private fun <T : EmbeddableToken> sumTokenCriteria(embeddableToken: T): QueryCriteria {
    val sum = builder {
        val groups = listOf(PersistentOwnedTokenAmount::tokenClass, PersistentOwnedTokenAmount::tokenIdentifier)
        PersistentOwnedTokenAmount::amount.sum(groupByColumns = groups)
    }
    return QueryCriteria.VaultCustomQueryCriteria(sum, relevancyStatus = Vault.RelevancyStatus.RELEVANT)
}

// Abstracts away the nasty 'otherResults' part of the vault query API.
private fun <T : EmbeddableToken> rowsToAmount(embeddableToken: T, rows: Vault.Page<OwnedTokenAmount<T>>): Amount<T> {
    return if (rows.otherResults.isEmpty()) {
        Amount(0L, embeddableToken)
    } else {
        require(rows.otherResults.size == 3) { "Invalid number of rows returned by query." }
        // The class and identifier are also returned in indexes 1 and 2 but we can discard them.
        val quantity = rows.otherResults[0] as Long
        Amount(quantity, embeddableToken)
    }
}

/** General queries. */

// Get all owned token amounts for a specific token, ignoring the issuer.
fun <T : EmbeddableToken> VaultService.ownedTokenAmountsByToken(embeddableToken: T): Vault.Page<OwnedTokenAmount<T>> {
    return queryBy(tokenCriteria(embeddableToken))
}

/** Token balances. */

// We need to group the sum by the token class and token identifier.
fun <T : EmbeddableToken> VaultService.tokenBalance(embeddableToken: T): Amount<T> {
    val query = tokenCriteria(embeddableToken).and(sumTokenCriteria(embeddableToken))
    val result = queryBy<OwnedTokenAmount<T>>(query)
    return rowsToAmount(embeddableToken, result)
}