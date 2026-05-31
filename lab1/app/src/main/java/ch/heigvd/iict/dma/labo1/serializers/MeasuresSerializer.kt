package ch.heigvd.iict.dma.labo1.serializers

import ch.heigvd.iict.dma.labo1.models.Measure
import ch.heigvd.iict.dma.labo1.models.MeasureServerResponse
import java.io.InputStream

/**
 * Interface for a serializer and deserializer of measures
 * @author Quentin Surdez
 */
interface MeasuresSerializer {
    /**
     * Serializes a list of measures in bytes
     * @param measures the list of measures to serialize
     * @return an array of bytes
     */
    fun serializeData(measures: List<Measure>): ByteArray

    /**
     * Deserializes bytes in a list of measures
     * @param inputStream the stream of bytes to deserialize
     * @return a list of measures
     */
    fun deserializeData(inputStream: InputStream): List<MeasureServerResponse>
}