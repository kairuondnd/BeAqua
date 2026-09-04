package com.example.beaqua

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FirebaseFirestore
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.Executors

data class DownloadedKycDocument(
    val uri: Uri,
    val mimeType: String
)

object FirestoreKycDocumentStore {
    private const val COLLECTION = "kyc_document_files"
    private const val REFERENCE_PREFIX = "firestore-kyc://"
    private const val MAX_FILE_BYTES = 15 * 1024 * 1024
    private const val CHUNK_BYTES = 600 * 1024

    private val database = FirebaseFirestore.getInstance()
    private val executor = Executors.newCachedThreadPool()

    fun upload(
        context: Context,
        username: String,
        documentType: String,
        documentUri: Uri
    ): Task<String> {
        val source = TaskCompletionSource<String>()
        executor.execute {
            try {
                val bytes = readDocument(context, documentUri)
                val fileId = UUID.randomUUID().toString()
                val fileReference = database.collection(COLLECTION).document(fileId)
                val details = documentDetails(context, documentUri)
                val chunkWrites = bytes.toListOfChunks().mapIndexed { index, chunk ->
                    fileReference.collection("chunks")
                        .document(index.toString().padStart(4, '0'))
                        .set(mapOf("index" to index, "data" to Blob.fromBytes(chunk)))
                }

                Tasks.whenAll(chunkWrites)
                    .addOnSuccessListener {
                        fileReference.set(
                            mapOf(
                                "username" to username,
                                "documentType" to documentType,
                                "displayName" to details.first,
                                "mimeType" to details.second,
                                "size" to bytes.size,
                                "chunkCount" to chunkWrites.size,
                                "uploadedAt" to System.currentTimeMillis()
                            )
                        ).addOnSuccessListener {
                            source.trySetResult("$REFERENCE_PREFIX$fileId")
                        }.addOnFailureListener(source::trySetException)
                    }
                    .addOnFailureListener(source::trySetException)
            } catch (error: Exception) {
                source.trySetException(error)
            }
        }
        return source.task
    }

    fun download(context: Context, reference: String): Task<DownloadedKycDocument> {
        val source = TaskCompletionSource<DownloadedKycDocument>()
        val fileId = reference.removePrefix(REFERENCE_PREFIX)
        if (fileId.isBlank() || fileId == reference) {
            source.setException(IllegalArgumentException("Invalid KYC document reference"))
            return source.task
        }

        val fileReference = database.collection(COLLECTION).document(fileId)
        fileReference.get()
            .addOnSuccessListener { metadata ->
                if (!metadata.exists()) {
                    source.trySetException(IllegalStateException("KYC document metadata was not found"))
                    return@addOnSuccessListener
                }
                fileReference.collection("chunks")
                    .orderBy("index")
                    .get()
                    .addOnSuccessListener { chunks ->
                        executor.execute {
                            try {
                                val directory = File(context.cacheDir, "kyc_documents/$fileId")
                                if (!directory.exists() && !directory.mkdirs()) {
                                    throw IllegalStateException("Could not prepare document cache")
                                }
                                val displayName = metadata.getString("displayName")
                                    .orEmpty()
                                    .ifBlank { "kyc-document" }
                                    .replace(Regex("[^A-Za-z0-9._-]"), "_")
                                val outputFile = File(directory, displayName)
                                FileOutputStream(outputFile).use { output ->
                                    chunks.documents.forEach { chunk ->
                                        val data = chunk.getBlob("data")
                                            ?: throw IllegalStateException("A document chunk is missing")
                                        output.write(data.toBytes())
                                    }
                                }
                                val expectedSize = metadata.getLong("size") ?: -1L
                                if (expectedSize >= 0L && outputFile.length() != expectedSize) {
                                    throw IllegalStateException("The reconstructed document is incomplete")
                                }
                                val contentUri = FileProvider.getUriForFile(
                                    context,
                                    "${BuildConfig.APPLICATION_ID}.fileprovider",
                                    outputFile
                                )
                                source.trySetResult(
                                    DownloadedKycDocument(
                                        contentUri,
                                        metadata.getString("mimeType") ?: "application/octet-stream"
                                    )
                                )
                            } catch (error: Exception) {
                                source.trySetException(error)
                            }
                        }
                    }
                    .addOnFailureListener(source::trySetException)
            }
            .addOnFailureListener(source::trySetException)
        return source.task
    }

    fun isFirestoreReference(reference: String): Boolean = reference.startsWith(REFERENCE_PREFIX)

    private fun readDocument(context: Context, uri: Uri): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("The selected document could not be read")
        input.use {
            while (true) {
                val count = it.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
                if (output.size() > MAX_FILE_BYTES) {
                    throw IllegalArgumentException("Each KYC document must be 15 MB or smaller")
                }
            }
        }
        return output.toByteArray()
    }

    private fun documentDetails(context: Context, uri: Uri): Pair<String, String> {
        val displayName = context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }.orEmpty().ifBlank { "kyc-document" }
        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
        return displayName to mimeType
    }

    private fun ByteArray.toListOfChunks(): List<ByteArray> {
        if (isEmpty()) return listOf(ByteArray(0))
        return (indices step CHUNK_BYTES).map { start ->
            copyOfRange(start, minOf(start + CHUNK_BYTES, size))
        }
    }
}
