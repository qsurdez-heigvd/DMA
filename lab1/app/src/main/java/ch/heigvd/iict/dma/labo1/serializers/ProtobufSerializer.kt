package ch.heigvd.iict.dma.labo1.serializers

import ch.heigvd.iict.dma.labo1.models.Measure
import ch.heigvd.iict.dma.labo1.models.MeasureServerResponse
import ch.heigvd.iict.dma.protobuf.MeasuresOuterClass
import com.google.protobuf.InvalidProtocolBufferException
import kotlinx.serialization.SerializationException
import java.io.InputStream
import kotlin.io.use

/**
 * Class for a serializer and deserializer of measures in Protobuf
 * @author Quentin Surdez
 */
class ProtobufSerializer : MeasuresSerializer {
    /**
     * Serializes a list of measures into Protobuf, encoded in bytes
     * @param measures the list of measures to serialize
     * @return an array of bytes
     */
    override fun serializeData(measures: List<Measure>): ByteArray {
        return MeasuresOuterClass.Measures.newBuilder().apply {
            measures.forEach { measure ->
                addMeasures(
                    MeasuresOuterClass.Measure.newBuilder().apply {
                        id = measure.id
                        status = measure.status.toProto()
                        type = measure.type.name
                        value = measure.value
                        date = measure.date.timeInMillis
                    }.build()
                )
            }
        }.build().toByteArray()
    }

    /**
     * Deserializes Protobuf encoded bytes into a list of measures
     * @param inputStream the stream of bytes to deserialize
     * @return a list of measures
     */
    override fun deserializeData(inputStream: InputStream): List<MeasureServerResponse> {
        try {
            return inputStream.use { stream ->
                MeasuresOuterClass.MeasuresAck.parseFrom(stream)
                    .measuresList
                    .map { ack -> ack.toMeasureServerResponse() }
            }
        } catch (e: InvalidProtocolBufferException) {
            throw SerializationException("Failed to parse MeasuresAck from server response", e)
        }
    }

    /**
     * Converts the status of the measure into a status for a MeasureOuterClass object
     * @return the status of the MeasureOuterClass
     */
    private fun Measure.Status.toProto(): MeasuresOuterClass.Status = when (this) {
        Measure.Status.NEW -> MeasuresOuterClass.Status.NEW
        Measure.Status.OK -> MeasuresOuterClass.Status.OK
        Measure.Status.ERROR -> MeasuresOuterClass.Status.ERROR
    }

    /**
     * Creates a new MeasureServerResponse object from the information (id and status) of a MeasureOuterClass object
     * @return a MeasureServerResponse
     */
    private fun MeasuresOuterClass.MeasureAck.toMeasureServerResponse(): MeasureServerResponse {
        val domainStatus = when (status) {
            MeasuresOuterClass.Status.NEW -> Measure.Status.NEW
            MeasuresOuterClass.Status.OK -> Measure.Status.OK
            MeasuresOuterClass.Status.ERROR -> Measure.Status.ERROR
            MeasuresOuterClass.Status.UNRECOGNIZED, null -> throw SerializationException(
                "Unrecognized or null status '${status}' in ack for measure id='${id}'"
            )
        }
        return MeasureServerResponse(id = id, status = domainStatus)
    }
}