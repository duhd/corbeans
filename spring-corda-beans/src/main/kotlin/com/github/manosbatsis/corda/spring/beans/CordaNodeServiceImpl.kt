/**
 *     Corda-Spring: integration and other utilities for developers working with Spring-Boot and Corda.
 *     Copyright (C) 2018 Manos Batsis
 *
 *     This library is free software; you can redistribute it and/or
 *     modify it under the terms of the GNU Lesser General Public
 *     License as published by the Free Software Foundation; either
 *     version 2.1 of the License, or (at your option) any later version.
 *
 *     This library is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *     Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public
 *     License along with this library; if not, write to the Free Software
 *     Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 *     USA
 */
package com.github.manosbatsis.corda.spring.beans

//import org.springframework.messaging.simp.SimpMessagingTemplate
import com.github.manosbatsis.corda.spring.beans.util.NodeRpcConnection
import net.corda.core.contracts.ContractState
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.node.services.vault.*
import org.apache.commons.io.IOUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InitializingBean
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.zip.ZipInputStream


/**
 *  Basic RPC-based node service implementation
 */
open class CordaNodeServiceImpl(open val nodeRpcConnection: NodeRpcConnection) : InitializingBean, CordaNodeService {



    companion object {
        private val logger = LoggerFactory.getLogger(CordaNodeServiceImpl::class.java)
    }

    protected lateinit var _myLegalName: CordaX500Name
    protected lateinit var _myIdentity: Party
    protected lateinit var _defaultIssuingAdvisorIdentity: Party
    protected lateinit var _myIdCriteria: QueryCriteria.LinearStateQueryCriteria

    /**
     * Initialise some node-dependent and other constants
     */
    override fun afterPropertiesSet() {
        _myIdentity = nodeRpcConnection.proxy.nodeInfo().legalIdentities.first()
        _myLegalName = _myIdentity.name
        _myIdCriteria = QueryCriteria.LinearStateQueryCriteria(participants = listOf(_myIdentity))
        _defaultIssuingAdvisorIdentity = nodeRpcConnection.proxy.partiesFromName("PartyC", exactMatch = false).singleOrNull()!!
    }

    val defaultPageSpecification = PageSpecification(pageSize = DEFAULT_PAGE_SIZE, pageNumber = -1)
    var sortByUid = Sort.SortColumn(SortAttribute.Standard(Sort.LinearStateAttribute.UUID), Sort.Direction.DESC)
    var defaultSort = Sort(listOf(sortByUid))

    /** Get the node identity */
    override fun getMyIdentity() = this._myIdentity

    /** Returns a list of the node's network peers. */
    override fun peers() = mapOf("peers" to nodeRpcConnection.proxy.networkMapSnapshot()
            .filter { nodeInfo -> nodeInfo.legalIdentities.first() != _myIdentity }
            .map { it.legalIdentities.first().name.organisation })

    /** Returns a list of the node's network peer names. */
    override fun peerNames(): Map<String, List<String>> {
        val nodes = nodeRpcConnection.proxy.networkMapSnapshot()
        val nodeNames = nodes.map { it.legalIdentities.first().name }
        val filteredNodeNames = nodeNames.filter { it.organisation !==_myIdentity.name.organisation }
        val filteredNodeNamesToStr = filteredNodeNames.map { it.toString() }
        return mapOf("peers" to filteredNodeNamesToStr)
    }

    override fun serverTime(): LocalDateTime {
        return LocalDateTime.ofInstant(nodeRpcConnection.proxy.currentNodeTime(), ZoneId.of("UTC"))
    }

    override fun addresses() = nodeRpcConnection.proxy.nodeInfo().addresses

    override fun identities() = nodeRpcConnection.proxy.nodeInfo().legalIdentities

    override fun platformVersion() = nodeRpcConnection.proxy.nodeInfo().platformVersion

    override fun notaries() = nodeRpcConnection.proxy.notaryIdentities()

    override fun flows() = nodeRpcConnection.proxy.registeredFlows()

    override fun states() = nodeRpcConnection.proxy.vaultQueryBy<ContractState>().states


    override fun openArrachment(hash: String): InputStream = this.openArrachment(SecureHash.parse(hash))
    override fun openArrachment(hash: SecureHash): InputStream = nodeRpcConnection.proxy.openAttachment(hash)

    @Throws(IOException::class)
    private fun convertToInputStream(inputStreamIn: ZipInputStream): InputStream {
        val out = ByteArrayOutputStream()
        IOUtils.copy(inputStreamIn, out)
        return ByteArrayInputStream(out.toByteArray())
    }

}