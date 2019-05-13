package com.r3.corda.sdk.token.workflow.flows.confidential

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.workflow.flows.confidential.internal.RequestConfidentialIdentityFlowHandler
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.utilities.unwrap

/**
 * Use of this flow should be paied with [ConfidentialTokensFlow]. If asked to do so, this flow begins the generation of
 * a new key pair by calling [RequestConfidentialIdentityFlowHandler].
 */
class ConfidentialTokensFlowHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val action = otherSession.receive<ActionRequest>().unwrap { it }
        if (action == ActionRequest.CREATE_NEW_KEY) {
            subFlow(RequestConfidentialIdentityFlowHandler(otherSession))
        }
    }
}