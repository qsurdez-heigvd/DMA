package ch.heigvd.iict.dma.labo1.serializers

import android.util.Log
import ch.heigvd.iict.dma.labo1.models.Measure
import ch.heigvd.iict.dma.labo1.models.MeasureServerResponse
import org.jdom2.DocType
import org.jdom2.Document
import org.jdom2.Element
import org.jdom2.input.SAXBuilder
import org.jdom2.output.Format
import org.jdom2.output.XMLOutputter
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.StringReader

/**
 * Class for a serializer and deserializer of measures in XML
 * @author Quentin Surdez
 */
class XmlSerializer : MeasuresSerializer {
    /**
     * Serializes a list of measures into XML, encoded in bytes
     * @param measures the list of measures to serialize
     * @return an array of bytes
     */
    override fun serializeData(measures: List<Measure>): ByteArray {
        val root = Element("measures").apply {
            measures.forEach { measure ->
                addContent(Element("measure").apply {
                    setAttribute("id", measure.id.toString())
                    setAttribute("status", measure.status.name)
                    addContent(Element("type").setText(measure.type.name))
                    addContent(Element("value").setText(measure.value.toString()))
                    addContent(Element("date").setText(measure.date.timeInMillis.toString()))
                })
            }
        }

        val document = Document(root, DocType("measures", DTD_URL))
        Log.d("XmlSerializer", "Document created: $document")

        val stream = ByteArrayOutputStream()
        xmlOutputter.output(document, stream)
        return stream.toByteArray()

    }

    /**
     * Deserializes XML encoded bytes into a list of measures
     * @param inputStream the stream of bytes to deserialize
     * @return a list of measures
     */
    override fun deserializeData(inputStream: InputStream): List<MeasureServerResponse> {
        val saxBuilder = SAXBuilder().apply { setExpandEntities(false) }
        val xmlString = inputStream.bufferedReader(Charsets.UTF_8)
            .use { bufferedReader -> bufferedReader.readText() }

        val root = saxBuilder.build(StringReader(xmlString)).rootElement

        return root.getChildren("measure").map { element ->
            MeasureServerResponse(
                id = element.getAttributeValue("id").toInt(),
                status = runCatching {
                    enumValueOf<Measure.Status>(element.getAttributeValue("status"))
                }.getOrDefault(Measure.Status.ERROR)
            )
        }
    }


    companion object {
        // URL of the DTD the serialization has to follow
        private val DTD_URL = "https://mobile.iict.ch/measures.dtd"
        // Outputter to print the XML document on the OutputStream
        private val xmlOutputter = XMLOutputter(Format.getPrettyFormat())
    }

}