package com.example.trucaller.service

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class GoogleDriveManager(private val context: Context) {

    @PublishedApi
    internal val gson = Gson()

    fun getSignInClient(): GoogleSignInClient {
        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
            .build()
        return GoogleSignIn.getClient(context, signInOptions)
    }

    fun getSignInIntent(): Intent = getSignInClient().signInIntent

    fun isSignedIn(): Boolean = GoogleSignIn.getLastSignedInAccount(context) != null

    fun getAccount(): GoogleSignInAccount? = GoogleSignIn.getLastSignedInAccount(context)

    @PublishedApi
    internal fun getDriveService(): Drive? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        val credential = GoogleAccountCredential.usingOAuth2(
            context, listOf(DriveScopes.DRIVE_APPDATA)
        )
        credential.selectedAccount = account.account
        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("TruCaller")
            .build()
    }

    suspend fun <T> uploadData(fileName: String, data: List<T>): Boolean = withContext(Dispatchers.IO) {
        try {
            val driveService = getDriveService() ?: return@withContext false
            val jsonContent = gson.toJson(data)
            val contentStream = ByteArrayContent.fromString("application/json", jsonContent)

            // Check if file already exists
            val existingFileId = findFile(driveService, fileName)

            if (existingFileId != null) {
                // Update existing file
                driveService.files().update(existingFileId, null, contentStream).execute()
            } else {
                // Create new file in appDataFolder
                val fileMetadata = com.google.api.services.drive.model.File().apply {
                    name = fileName
                    parents = listOf("appDataFolder")
                }
                driveService.files().create(fileMetadata, contentStream)
                    .setFields("id")
                    .execute()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend inline fun <reified T> downloadData(fileName: String): List<T>? = withContext(Dispatchers.IO) {
        try {
            val driveService = getDriveService() ?: return@withContext null
            val fileId = findFile(driveService, fileName) ?: return@withContext null

            val outputStream = ByteArrayOutputStream()
            driveService.files().get(fileId).executeMediaAndDownloadTo(outputStream)
            val json = outputStream.toString("UTF-8")

            val type = object : TypeToken<List<T>>() {}.type
            gson.fromJson<List<T>>(json, type)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun downloadRawJson(fileName: String): String? = withContext(Dispatchers.IO) {
        try {
            val driveService = getDriveService() ?: return@withContext null
            val fileId = findFile(driveService, fileName) ?: return@withContext null

            val outputStream = ByteArrayOutputStream()
            driveService.files().get(fileId).executeMediaAndDownloadTo(outputStream)
            outputStream.toString("UTF-8")
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    @PublishedApi
    internal fun findFile(driveService: Drive, fileName: String): String? {
        val result = driveService.files().list()
            .setSpaces("appDataFolder")
            .setQ("name = '$fileName'")
            .setFields("files(id, name)")
            .setPageSize(1)
            .execute()
        return result.files?.firstOrNull()?.id
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        getSignInClient().signOut()
    }

    companion object {
        const val CONTACTS_FILE = "trucaller_contacts.json"
        const val CALLER_ID_FILE = "trucaller_callerid.json"
        const val USERS_FILE = "trucaller_users.json"
    }
}
