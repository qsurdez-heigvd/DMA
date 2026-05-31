package ch.heigvd.iict.dma.labo1.ui.push.services

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import ch.heigvd.iict.dma.labo1.database.MessagesDatabase
import ch.heigvd.iict.dma.labo1.models.Message
import ch.heigvd.iict.dma.labo1.repositories.MessagesRepository
import java.util.Calendar

class MessagingService : FirebaseMessagingService() {

    private val TAG = this.javaClass.simpleName

    private val repository: MessagesRepository by lazy {
        MessagesRepository(MessagesDatabase.getDatabase(applicationContext).messagesDao())
    }

    /**
     * Handles the reception of a new token
     * @author Quentin Surdez
     * @param token the new token
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New token: $token")
    }

    /**
     * Handles the reception of a message by inserting its content in the repository
     * @author Quentin Surdez
     * @param remoteMessage the message received
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "Message received from: ${remoteMessage.from}")

        if (remoteMessage.data.isEmpty()) return

        Log.d(TAG, "Payload data: ${remoteMessage.data}")

        val messageText = remoteMessage.data["message"]
        val command = remoteMessage.data["command"]

        if (command?.equals("clear", ignoreCase = true) == true) {
            repository.deleteAllMessage()
            return
        }

        repository.insert(Message(
            sentDate = Calendar.getInstance().apply { timeInMillis = remoteMessage.sentTime },
            receptionDate = Calendar.getInstance(),
            message = messageText,
            command = command
        ))
    }
}
