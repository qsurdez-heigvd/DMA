package ch.heigvd.iict.dma.labo1.serializers

import ch.heigvd.iict.dma.labo1.models.Measure
import ch.heigvd.iict.dma.labo1.models.MeasureServerResponse
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStream


/**
 * Class for a serializer and deserializer of measures in JSON
 * @author Quentin Surdez
 */
class JsonSerializer : MeasuresSerializer {

    /**
     * Serializes a list of measures into JSON, encoded in bytes
     * @param measures the list of measures to serialize
     * @return an array of bytes
     */
    override fun serializeData(measures: List<Measure>): ByteArray {
        return gson.toJson(measures).toByteArray(Charsets.UTF_8)
    }

    /**
     * Deserializes JSON encoded bytes into a list of measures
     * @param inputStream the stream of bytes to deserialize
     * @return a list of measures
     */
    override fun deserializeData(inputStream: InputStream): List<MeasureServerResponse> {
        return inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            gson.fromJson(reader, RESPONSE_LIST_TYPE)
        }
    }

    companion object {
        // Gson object for conversions from and to JSON
        private val gson = Gson()
        // The type of measure server response for deserialization
        private val RESPONSE_LIST_TYPE = object : TypeToken<List<MeasureServerResponse>>() {}.type
    }

}