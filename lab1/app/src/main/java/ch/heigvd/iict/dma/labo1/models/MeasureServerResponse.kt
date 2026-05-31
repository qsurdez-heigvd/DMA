package ch.heigvd.iict.dma.labo1.models

/**
 * Class for a server response after receiving a measure
 * @author Quentin Surdez
 */
data class MeasureServerResponse(val id: Int, var status: Measure.Status)