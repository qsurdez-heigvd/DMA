package ch.heigvd.iict.dma.labo1.repositories

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import ch.heigvd.iict.dma.labo1.models.*
import ch.heigvd.iict.dma.labo1.serializers.JsonSerializer
import ch.heigvd.iict.dma.labo1.serializers.ProtobufSerializer
import ch.heigvd.iict.dma.labo1.serializers.XmlSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.Deflater
import java.util.zip.DeflaterInputStream
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import kotlin.system.measureTimeMillis

class MeasuresRepository(
    private val scope: CoroutineScope,
    private val dtd: String = "https://mobile.iict.ch/measures.dtd",
    private val httpUrl: String = "http://mobile.iict.ch/api",
    private val httpsUrl: String = "https://mobile.iict.ch/api"
) {

    private val TAG = this.javaClass.simpleName.toString()

    private val _measures = MutableLiveData(mutableListOf<Measure>())
    val measures = _measures.map { mList -> mList.toList().map { el -> el.copy() } }

    private val _requestDuration = MutableLiveData(-1L)
    val requestDuration: LiveData<Long> get() = _requestDuration

    fun generateRandomMeasures(nbr: Int = 3) {
        addMeasures(Measure.getRandomMeasures(nbr))
    }

    fun resetRequestDuration() {
        _requestDuration.postValue(-1L)
    }

    fun addMeasure(measure: Measure) {
        addMeasures(listOf(measure))
    }

    fun addMeasures(measures: List<Measure>) {
        val l = _measures.value!!
        l.addAll(measures)
        _measures.postValue(l)
    }

    fun clearAllMeasures() {
        _measures.postValue(mutableListOf())
    }

    /**
     *
     */
    fun sendMeasureToServer(
        encryption: Encryption,
        compression: Compression,
        networkType: NetworkType,
        serialisation: Serialisation
    ) {
        scope.launch(Dispatchers.Default) {
            var measuresServerResponse: List<MeasureServerResponse> = emptyList()

            val url = when (encryption) {
                Encryption.DISABLED -> httpUrl
                Encryption.SSL -> httpsUrl
            }

            val elapsed = measureTimeMillis {
                withContext(Dispatchers.IO) {
                    // Getting the connection ready
                    val connection = createConnection(url, serialisation, compression, networkType)
                    Log.d(TAG, "Connection has been established")


                    // Serializing data
                    try {
                        measures.value?.let { measuresList ->

                            val serialisedData = serializeData(measuresList, serialisation)
                            Log.d(TAG, "Data has been serialised")

                            val outputStream = when (compression) {
                                Compression.DEFLATE -> DeflaterOutputStream(
                                    connection.getOutputStream(),
                                    Deflater(Deflater.DEFAULT_COMPRESSION, true)
                                )

                                Compression.DISABLED -> connection.getOutputStream()
                            }

                            outputStream.use { os ->
                                os.write(serialisedData)
                                Log.d(TAG, "Data has been sent")
                            }
                        } ?: Log.w(TAG, "No measures to send!")

                        Log.d(TAG, "Server response code: ${connection.responseCode}")
                        Log.d(TAG, "Server response message: ${connection.responseMessage}")

                        if (connection.responseCode == 200) {
                            Log.d(TAG, "Response received")
                            val inputStream = connection.getInputStream()

                            measuresServerResponse = when (compression) {
                                Compression.DISABLED -> deserializeData(inputStream, serialisation)
                                Compression.DEFLATE -> deserializeData(
                                    InflaterInputStream(
                                        inputStream,
                                        Inflater(true)
                                    ), serialisation
                                )
                            }
                            Log.d(TAG, "Deserialized response: $measuresServerResponse")

                        } else {
                            val errorMessage =
                                connection.getHeaderField("X-Error") ?: "Unknown error"
                            Log.e(
                                TAG,
                                "Server error: $errorMessage (code: ${connection.responseCode})"
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception rasied: ", e)
                    }
                }
            }
            _requestDuration.postValue(elapsed)

            if (!measuresServerResponse.isEmpty()) {
                Log.d(TAG, "Status updates of the measures folowing the server response")
                val list = mutableListOf<Measure>().apply {
                    _measures.value?.forEach { element ->
                        val matchingResponse = measuresServerResponse.filter { it.id == element.id }
                        if (matchingResponse.isEmpty()) {
                            Log.d(TAG, "No status returned for measure id: ${element.id}")
                            add(element)
                        } else {
                            val newStatus = matchingResponse.first().status
                            Log.d(
                                TAG,
                                "Updating status for measure id: ${element.id}, new status: $newStatus"
                            )
                            add(element.copy(status = newStatus))
                        }
                    }
                }
                _measures.postValue(list)
            }
        }
    }

    /**
     * Creates a URL connection to POST data
     * @author Quentin Surdez
     * @param url the URL to connect to
     * @param serialisation the format of the data that will go through the connection
     * @param compression the type of compression for the data, if any
     * @param networkType the network type, indicating the connection rate
     * @return the URL connection
     */
    private fun createConnection(
        url: String,
        serialisation: Serialisation,
        compression: Compression,
        networkType: NetworkType,
    ): HttpURLConnection {

        val contentType = when (serialisation) {
            Serialisation.JSON -> "application/json"
            Serialisation.XML -> "application/xml"
            Serialisation.PROTOBUF -> "application/protobuf"
        }

        val contentEncoding = when (compression) {
            Compression.DEFLATE -> "DEFLATE"
            Compression.DISABLED -> "NONE"
        }

        val net = networkType.toString()

        Log.d(
            TAG,
            "HTTP Headers: Content-Type=$contentType, Content-Encoding=$contentEncoding, Network=$net"
        )

        return (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("User-Agent", "Surdez")
            setRequestProperty("Content-Type", contentType)
            setRequestProperty("X-Content-Encoding", contentEncoding)
            setRequestProperty("X-Network", net)
        }

    }

    /**
     * Serializes a list of measures into an array of bytes
     * @author Quentin Surdez
     * @param measures the list of measures to serialize
     * @param serialisation the format the measures will be serialized into
     * @return an array of bytes
     */
    private fun serializeData(measures: List<Measure>, serialisation: Serialisation): ByteArray {
        val result = when (serialisation) {
            Serialisation.JSON -> JsonSerializer().serializeData(measures)
            Serialisation.XML -> XmlSerializer().serializeData(measures)
            Serialisation.PROTOBUF -> ProtobufSerializer().serializeData(measures)
        }
        return result
    }

    /**
     * Deserializes a stream of bytes into a list of measures
     * @author Quentin Surdez
     * @param inputStream the stream of bytes to deserialize
     * @param serialisation the format of the bytes that will be deserialized
     * @return a list of measures
     */
    private fun deserializeData(
        inputStream: InputStream,
        serialisation: Serialisation
    ): List<MeasureServerResponse> {
        val result = when (serialisation) {
            Serialisation.JSON -> JsonSerializer().deserializeData(inputStream)
            Serialisation.XML -> XmlSerializer().deserializeData(inputStream)
            Serialisation.PROTOBUF -> ProtobufSerializer().deserializeData(inputStream)
        }
        return result
    }

}