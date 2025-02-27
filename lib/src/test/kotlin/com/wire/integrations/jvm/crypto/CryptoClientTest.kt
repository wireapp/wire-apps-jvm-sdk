package com.wire.integrations.jvm.crypto

import com.wire.integrations.jvm.config.IsolatedKoinContext
import com.wire.integrations.jvm.model.ClientId
import com.wire.integrations.jvm.model.QualifiedId
import com.wire.integrations.jvm.model.Team
import com.wire.integrations.jvm.model.TeamId
import org.junit.jupiter.api.Test
import org.koin.core.Koin
import org.koin.test.KoinTest
import java.util.UUID
import kotlin.test.assertNotNull

class CryptoClientTest : KoinTest {
    override fun getKoin(): Koin = IsolatedKoinContext.koinApp.koin

    @Test
    fun whenCryptoStoragePasswordIsSet_thenClientWorks() {
        IsolatedKoinContext.setCryptographyStoragePassword("banana🍌")

        val cryptoClient = CryptoClient(
            Team(
                id = TeamId(value = UUID.randomUUID()),
                userId = QualifiedId(UUID.randomUUID(), UUID.randomUUID().toString()),
                clientId = ClientId(UUID.randomUUID().toString())
            )
        )
        assertNotNull(cryptoClient.mlsGetPublicKey())
        val keyPackages = cryptoClient.mlsGenerateKeyPackages()
        assertNotNull(keyPackages)
    }
}
