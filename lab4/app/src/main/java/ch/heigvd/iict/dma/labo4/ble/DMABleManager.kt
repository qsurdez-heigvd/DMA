package ch.heigvd.iict.dma.labo4.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.util.Log
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.data.Data
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

class DMABleManager(
    applicationContext: Context,
    private val dmaServiceListener: DMAServiceListener? = null
) : BleManager(applicationContext) {

    private var timeService: BluetoothGattService? = null
    private var symService: BluetoothGattService? = null
    private var currentTimeChar: BluetoothGattCharacteristic? = null
    private var integerChar: BluetoothGattCharacteristic? = null
    private var temperatureChar: BluetoothGattCharacteristic? = null
    private var buttonClickChar: BluetoothGattCharacteristic? = null

    fun requestDisconnection() {
        this.disconnect().enqueue()
    }

    override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
        Log.d(TAG, "isRequiredServiceSupported - discovered services:")
        for (service in gatt.services) Log.d(TAG, service.uuid.toString())

        timeService = gatt.getService(UUID_TIME_SERVICE)
        symService = gatt.getService(UUID_SYM_SERVICE)
        if (timeService == null || symService == null) return false

        currentTimeChar = timeService!!.getCharacteristic(UUID_CURRENT_TIME)
        integerChar = symService!!.getCharacteristic(UUID_INTEGER)
        temperatureChar = symService!!.getCharacteristic(UUID_TEMPERATURE)
        buttonClickChar = symService!!.getCharacteristic(UUID_BUTTON_CLICK)

        val readWriteNotify = BluetoothGattCharacteristic.PROPERTY_READ or
                BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_NOTIFY
        if (currentTimeChar == null || (currentTimeChar!!.properties and readWriteNotify) != readWriteNotify) return false
        if (integerChar == null || (integerChar!!.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) == 0) return false
        if (temperatureChar == null || (temperatureChar!!.properties and BluetoothGattCharacteristic.PROPERTY_READ) == 0) return false
        if (buttonClickChar == null || (buttonClickChar!!.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0) return false

        return true
    }

    override fun initialize() {
        super.initialize()

        setNotificationCallback(currentTimeChar).with { _, data ->
            Calendar.getInstance().also { cal ->
                cal.set(
                    data.getIntValue(Data.FORMAT_UINT16_LE, 0)!!,
                    data.getIntValue(
                        Data.FORMAT_UINT8,
                        2
                    )!! - 1, // BLE months are 1-12, Calendar 0-11
                    data.getIntValue(Data.FORMAT_UINT8, 3)!!,
                    data.getIntValue(Data.FORMAT_UINT8, 4)!!,
                    data.getIntValue(Data.FORMAT_UINT8, 5)!!,
                    data.getIntValue(Data.FORMAT_UINT8, 6)!!
                )
                dmaServiceListener?.dateUpdate(cal)
            }
        }
        enableNotifications(currentTimeChar).enqueue()

        setNotificationCallback(buttonClickChar).with { _, data ->
            dmaServiceListener?.clickCountUpdate(data.getIntValue(Data.FORMAT_UINT8, 0)!!)
        }
        enableNotifications(buttonClickChar).enqueue()
    }

    override fun onServicesInvalidated() {
        super.onServicesInvalidated()
        timeService = null
        symService = null
        currentTimeChar = null
        integerChar = null
        temperatureChar = null
        buttonClickChar = null
    }

    fun readTemperature(): Boolean {
        if (temperatureChar == null) return false
        readCharacteristic(temperatureChar).with { _, data ->
            data.getIntValue(Data.FORMAT_UINT16_LE, 0)?.let {
                dmaServiceListener?.temperatureUpdate(it / 10.0f)
            }
        }.enqueue()
        return true
    }

    fun sendValue(integer: Int): Boolean {
        if (integerChar == null) return false
        val bytes =
            ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(integer)
                .array()
        writeCharacteristic(
            integerChar,
            bytes,
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        ).enqueue()
        return true
    }

    fun setTime(): Boolean {
        if (currentTimeChar == null) return false
        val cal = Calendar.getInstance()
        val bytes = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(cal.get(Calendar.YEAR).toShort())
            .put((cal.get(Calendar.MONTH) + 1).toByte()) // Calendar 0-11 → BLE 1-12
            .put(cal.get(Calendar.DAY_OF_MONTH).toByte())
            .put(cal.get(Calendar.HOUR_OF_DAY).toByte())
            .put(cal.get(Calendar.MINUTE).toByte())
            .put(cal.get(Calendar.SECOND).toByte())
            .put(cal.get(Calendar.DAY_OF_WEEK).toByte())
            .put(0) // Fractions256
            .put(0) // Adjust Reason: manual update
            .array()
        writeCharacteristic(
            currentTimeChar,
            bytes,
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        ).enqueue()
        return true
    }

    companion object {
        private val TAG = DMABleManager::class.java.simpleName

        val UUID_TIME_SERVICE: UUID = UUID.fromString("00001805-0000-1000-8000-00805f9b34fb")
        val UUID_SYM_SERVICE: UUID = UUID.fromString("3c0a1000-281d-4b48-b2a7-f15579a1c38f")

        val UUID_CURRENT_TIME: UUID = UUID.fromString("00002A2B-0000-1000-8000-00805f9b34fb")
        val UUID_INTEGER: UUID = UUID.fromString("3c0a1001-281d-4b48-b2a7-f15579a1c38f")
        val UUID_TEMPERATURE: UUID = UUID.fromString("3c0a1002-281d-4b48-b2a7-f15579a1c38f")
        val UUID_BUTTON_CLICK: UUID = UUID.fromString("3c0a1003-281d-4b48-b2a7-f15579a1c38f")
    }
}

interface DMAServiceListener {
    fun dateUpdate(date: Calendar)
    fun temperatureUpdate(temperature: Float)
    fun clickCountUpdate(clickCount: Int)
}
