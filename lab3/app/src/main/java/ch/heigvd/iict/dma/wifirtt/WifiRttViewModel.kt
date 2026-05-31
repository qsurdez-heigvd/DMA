package ch.heigvd.iict.dma.wifirtt

import android.net.wifi.rtt.RangingResult
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import ch.heigvd.iict.dma.wifirtt.config.MapConfig
import ch.heigvd.iict.dma.wifirtt.config.MapConfigs
import ch.heigvd.iict.dma.wifirtt.models.RangedAccessPoint
import com.lemmingapex.trilateration.LinearLeastSquaresSolver
import com.lemmingapex.trilateration.NonLinearLeastSquaresSolver
import com.lemmingapex.trilateration.TrilaterationFunction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer
import kotlin.math.min

class WifiRttViewModel : ViewModel() {

    // PERMISSIONS MANAGEMENT
    private val _wifiRttPermissionsGranted = MutableLiveData<Boolean>(null)
    val wifiRttPermissionsGranted: LiveData<Boolean> get() = _wifiRttPermissionsGranted

    fun wifiRttPermissionsGrantedUpdate(granted: Boolean) {
        _wifiRttPermissionsGranted.postValue(granted)
    }

    // WIFI RTT AVAILABILITY MANAGEMENT
    private val _wifiRttEnabled = MutableLiveData<Boolean>(null)
    val wifiRttEnabled: LiveData<Boolean> get() = _wifiRttEnabled

    fun wifiRttEnabledUpdate(enabled: Boolean) {
        _wifiRttEnabled.postValue(enabled)
    }

    // WIFI RTT MEASURES MANAGEMENT
    private val _rangedAccessPoints = MutableLiveData(emptyList<RangedAccessPoint>())
    val rangedAccessPoints: LiveData<List<RangedAccessPoint>> =
        _rangedAccessPoints.map { l -> l.toList().map { el -> el.copy() } }

    // CONFIGURATION MANAGEMENT
    private val _mapConfig = MutableLiveData(MapConfigs.b30)
    val mapConfig: LiveData<MapConfig> get() = _mapConfig

    fun onNewRangingResults(newResults: List<RangingResult>) {
        val currentMapConfig = mapConfig.value ?: return
        val updatedList = _rangedAccessPoints.value?.toMutableList() ?: mutableListOf()

        for (result in newResults) {
            if (result.status != RangingResult.STATUS_SUCCESS) continue
            val existing = updatedList.find { it.bssid == result.macAddress.toString() }
            if (existing != null) existing.update(result)
            else updatedList.add(RangedAccessPoint.newInstance(result))
        }

        _rangedAccessPoints.postValue(updatedList)

        // when the list is updated, we also want to update estimated location
        viewModelScope.launch(Dispatchers.Default) {
            Log.d(TAG, "Launching the estimateLocation in coroutine")
            estimateLocation(updatedList, currentMapConfig)
        }
    }

    // WIFI RTT ACCESS POINT LOCATIONS

    private val _estimatedPosition = MutableLiveData<DoubleArray>(null)
    val estimatedPosition: LiveData<DoubleArray> get() = _estimatedPosition

    private val _estimatedDistances = MutableLiveData<MutableMap<String, Double>>(mutableMapOf())
    val estimatedDistances: LiveData<Map<String, Double>> =
        _estimatedDistances.map { m -> m.toMap() }

    private val _debug = MutableLiveData(false)
    val debug: LiveData<Boolean> get() = _debug

    fun debugMode(debug: Boolean) {
        _debug.postValue(debug)
    }

    /**
     * Estimates the device's 2D (or optionally 3D) position via trilateration, using the known
     * locations from [config] and the measured distances in [apList].
     *
     * Requires at least 3 APs in 2D mode or 4 in 3D mode. Results are posted to
     * [_estimatedPosition] and [_estimatedDistances]. Trilateration failures are caught and logged.
     *
     * @param apList Access points with current distance measurements.
     * @param config Map configuration containing each AP's known physical location, keyed by BSSID.
     * @param use3D If `true`, includes AP height in the computation and requires 4+ APs.
     *
     * @author 
     * @author 
     * @author Quentin Surdez
     */
    private fun estimateLocation(
        apList: List<RangedAccessPoint>,
        config: MapConfig,
        use3D: Boolean = false
    ) {
        Log.d(TAG, "Entering estimateLocation func")
        // Check that the values we wanna work on are accessible
        val apLocations =
            if (use3D) config.accessPointKnownLocations.filter { it.value.heightMm != 0 }
            else config.accessPointKnownLocations

        // Minimum number of APs is 3
        val minApPresent = if (use3D) 4 else 3
        if (apList.size < minApPresent) return

        val positions = mutableListOf<DoubleArray>()
        val distances = mutableListOf<Double>()

        // We build the lists that we will give to our solvers
        for (ap in apList) {
            val loc = apLocations[ap.bssid] ?: continue
            positions.add(
                if (use3D) doubleArrayOf(
                    loc.xMm.toDouble(),
                    loc.yMm.toDouble(),
                    loc.heightMm.toDouble()
                )
                else doubleArrayOf(loc.xMm.toDouble(), loc.yMm.toDouble())
            )
            distances.add(ap.distanceMm)
        }

        try {
            val solver = LinearLeastSquaresSolver(
                TrilaterationFunction(positions.toTypedArray(), distances.toDoubleArray())
            )
            val point = solver.solve().toArray()

            _estimatedPosition.postValue(
                doubleArrayOf(
                    point[0],
                    point[1],
                    if (use3D) point[2] else 0.0
                )
            )

            _estimatedDistances.postValue(
                apList.associate { it.bssid to it.distanceMm }.toMutableMap()
            )

        } catch (e: Exception) {
            Log.e(TAG, "Trilateration failed", e)
        }
    }

    companion object {
        private val TAG = WifiRttViewModel::class.simpleName
    }

}