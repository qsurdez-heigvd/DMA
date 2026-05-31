package ch.heigvd.iict.dma.labo2

import android.content.Context
import android.util.Log
import ch.heigvd.iict.dma.labo2.models.PersistentBeacon
import org.altbeacon.beacon.BeaconManager
import org.altbeacon.beacon.BeaconParser
import org.altbeacon.beacon.BeaconRegion
import org.altbeacon.beacon.Identifier
import org.altbeacon.beacon.Region
import java.util.UUID

/**
 * Handles BLE beacon scanning using the AltBeacon library.
 *
 * Maintains an internal cache of recently seen beacons and removes entries that
 * have not been detected for more than [STALE_THRESHOLD_MS] milliseconds.
 * The [onBeaconsDetected] callback is called on every ranging cycle with the cache.
 *
 * @param context Application context used to obtain the [BeaconManager] instance.
 * @param onBeaconsDetected Callback called with the up-to-date list of nearby beacons.
 *
 * @author 
 * @author 
 * @author Quentin Surdez
 */
class BeaconsRepository(
    context: Context,
    private val onBeaconsDetected: (List<PersistentBeacon>) -> Unit
) {

    private val beaconManager = BeaconManager.getInstanceForApplication(context)

    private val uuid = Identifier.parse("ebefd083-70a2-47c8-9837-e7b5634df670")
    private val major = Identifier.parse("1")

    private val region = Region("Beacon23", uuid, major, null)


    private val beaconCache = mutableMapOf<String, PersistentBeacon>()

    init {
        Log.d(TAG, "Init beacon")
        beaconManager.beaconParsers.add(
            BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24")
        )

        // BeaconManager.setDebug(true)

        beaconManager.addRangeNotifier { beacons, _ ->
            Log.d(TAG, "RangeNotifier fired, beacons: ${beacons.size}")
            val now = System.currentTimeMillis()

            beacons.forEach { beacon ->
                Log.d(TAG, beacon.toString())
                val key = "${beacon.id1}-${beacon.id2}-${beacon.id3}"
                val existing = beaconCache[key]
                if (existing != null) {
                    existing.rssi = beacon.rssi
                    existing.txPower = beacon.txPower
                    existing.distance = beacon.distance
                    // So that the user doesn't see
                    // the value constantly updating
                    if (existing.lastSeen < now - (STALE_THRESHOLD_MS/2)) {
                        existing.lastSeen = now
                    }
                } else {
                    beaconCache[key] = PersistentBeacon(
                        major = beacon.id2.toInt(),
                        minor = beacon.id3.toInt(),
                        uuid = beacon.id1.toUuid(),
                        rssi = beacon.rssi,
                        txPower = beacon.txPower,
                        distance = beacon.distance,
                        lastSeen = now
                    )
                }
            }
            // remove beacons not seen for more than STALE_THRESHOLD_MS
            beaconCache.entries.removeIf { it.value.lastSeen < now - STALE_THRESHOLD_MS }

            onBeaconsDetected(beaconCache.values.toList())
        }
    }


    fun startScanning() {
        Log.d(TAG, "Starting scanning for beacons")
        beaconManager.startRangingBeacons(region)
    }

    fun stopScanning() {
        Log.d(TAG, "Stoping scanning for beacons")
        beaconManager.stopRangingBeacons(region)
    }


    companion object {
        private const val TAG = "BeaconsRepository"
        private const val STALE_THRESHOLD_MS = 10000
    }
}