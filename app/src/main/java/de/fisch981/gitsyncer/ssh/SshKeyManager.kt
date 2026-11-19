package de.fisch981.gitsyncer.ssh

import android.util.Base64
import de.fisch981.gitsyncer.model.SshKeyEntry
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Security
import java.security.interfaces.RSAPublicKey
import java.util.UUID

object SshKeyManager {

    init {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    fun generateRsaKey(name: String, bits: Int = 2048, comment: String = "gitsyncer@android"): SshKeyEntry {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(bits)
        val keyPair = generator.generateKeyPair()

        val pubKey = keyPair.public as RSAPublicKey
        val pubKeyOpenSsh = encodeRsaPublicKey(pubKey, comment)
        val privKeyPem = encodePrivateKeyToPem(keyPair, "RSA")

        return SshKeyEntry(
            id = UUID.randomUUID().toString(),
            name = name,
            publicKey = pubKeyOpenSsh,
            privateKey = privKeyPem,
            keyType = "RSA ($bits bit)",
            createdTimestamp = System.currentTimeMillis()
        )
    }

    fun generateEd25519Key(name: String, comment: String = "gitsyncer@android"): SshKeyEntry {
        return try {
            val generator = KeyPairGenerator.getInstance("Ed25519", BouncyCastleProvider.PROVIDER_NAME)
            val keyPair = generator.generateKeyPair()
            val privKeyPem = encodePrivateKeyToPem(keyPair, "PRIVATE")
            val pubEncoded = keyPair.public.encoded
            // Fallback to standard base64 if needed
            val pubKeyOpenSsh = "ssh-ed25519 " + Base64.encodeToString(pubEncoded, Base64.NO_WRAP) + " " + comment
            SshKeyEntry(
                id = UUID.randomUUID().toString(),
                name = name,
                publicKey = pubKeyOpenSsh,
                privateKey = privKeyPem,
                keyType = "Ed25519",
                createdTimestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            // Fallback to 2048 bit RSA if Ed25519 not supported on provider
            generateRsaKey(name, 2048, comment)
        }
    }

    private fun encodeRsaPublicKey(publicKey: RSAPublicKey, comment: String): String {
        val byteOs = ByteArrayOutputStream()
        val dos = DataOutputStream(byteOs)

        // Key type
        val typeBytes = "ssh-rsa".toByteArray(Charsets.US_ASCII)
        dos.writeInt(typeBytes.size)
        dos.write(typeBytes)

        // Exponent
        val expBytes = publicKey.publicExponent.toByteArray()
        dos.writeInt(expBytes.size)
        dos.write(expBytes)

        // Modulus
        val modBytes = publicKey.modulus.toByteArray()
        dos.writeInt(modBytes.size)
        dos.write(modBytes)

        dos.flush()
        val base64Key = Base64.encodeToString(byteOs.toByteArray(), Base64.NO_WRAP)
        return "ssh-rsa $base64Key $comment"
    }

    private fun encodePrivateKeyToPem(keyPair: KeyPair, keyLabel: String): String {
        val base64 = Base64.encodeToString(keyPair.private.encoded, Base64.NO_WRAP)
        val sb = StringBuilder()
        sb.append("-----BEGIN ").append(keyLabel).append(" KEY-----\n")
        var i = 0
        while (i < base64.length) {
            val end = (i + 64).coerceAtMost(base64.length)
            sb.append(base64.substring(i, end)).append("\n")
            i += 64
        }
        sb.append("-----END ").append(keyLabel).append(" KEY-----\n")
        return sb.toString()
    }
}
