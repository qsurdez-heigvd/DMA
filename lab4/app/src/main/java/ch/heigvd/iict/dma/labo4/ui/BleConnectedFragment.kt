package ch.heigvd.iict.dma.labo4.ui

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import ch.heigvd.iict.dma.labo4.R
import ch.heigvd.iict.dma.labo4.databinding.FragmentConnectedBinding
import ch.heigvd.iict.dma.labo4.viewmodels.BleViewModel
import java.text.SimpleDateFormat
import java.util.Locale

class BleConnectedFragment : Fragment(), MenuProvider {

    private val bleViewModel: BleViewModel by activityViewModels()

    private var _binding: FragmentConnectedBinding? = null
    private val binding get() = _binding!!

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentConnectedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonReadTemperature.setOnClickListener {
            if (!bleViewModel.readTemperature())
                Toast.makeText(context, "Impossible de lire la température", Toast.LENGTH_SHORT).show()
        }

        binding.buttonSetTime.setOnClickListener {
            val ok = bleViewModel.setTime()
            Toast.makeText(
                context,
                if (ok) "Heure mise à jour" else "Impossible de mettre à jour l'heure",
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.buttonSendInteger.setOnClickListener {
            val value = binding.editTextInteger.text?.toString()?.toIntOrNull()
            if (value == null) {
                Toast.makeText(context, "Valeur invalide", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!bleViewModel.sendValue(value))
                Toast.makeText(context, "Impossible d'envoyer la valeur", Toast.LENGTH_SHORT).show()
        }

        bleViewModel.temperature.observe(viewLifecycleOwner) { temp ->
            binding.textViewTemperature.text = if (temp != null)
                String.format(Locale.getDefault(), "%.1f °C", temp)
            else "-"
        }

        bleViewModel.buttonClick.observe(viewLifecycleOwner) { count ->
            binding.textViewButtonClick.text = count?.toString() ?: "-"
        }

        bleViewModel.currentTime.observe(viewLifecycleOwner) { time ->
            binding.textViewCurrentTime.text = if (time != null)
                dateFormat.format(time.time)
            else "-"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as AppCompatActivity).addMenuProvider(this)
    }

    override fun onPause() {
        super.onPause()
        (requireActivity() as AppCompatActivity).removeMenuProvider(this)
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.connected_menu, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.menu_ble_connected_disconnect -> {
                bleViewModel.disconnect()
                true
            }
            else -> false
        }
    }

    companion object {
        @JvmStatic
        fun newInstance() = BleConnectedFragment()
    }
}
